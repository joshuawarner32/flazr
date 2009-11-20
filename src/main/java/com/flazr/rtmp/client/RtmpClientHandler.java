/*
 * Flazr <http://flazr.com> Copyright (C) 2009  Peter Thomas.
 *
 * This file is part of Flazr.
 *
 * Flazr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flazr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Flazr.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.flazr.rtmp.client;

import com.flazr.io.flv.FlvWriter;
import static com.flazr.rtmp.message.Control.Type.*;

import com.flazr.rtmp.message.Control;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.message.BytesRead;
import com.flazr.rtmp.message.WindowAckSize;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.SetPeerBw;
import com.flazr.util.Utils;
import java.util.HashMap;
import java.util.Map;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelPipelineCoverage("one")
public class RtmpClientHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(RtmpClientHandler.class);

    private int transactionId = 1;
    private Map<Integer, String> transactionToCommandMap;
    private RtmpClientSession session;
    private byte[] swfvBytes;
    private FlvWriter writer;
    private int bytesReadWindow = 2500000;
    private long bytesRead;
    private long bytesReadLastSent;    
    private int bytesWrittenWindow = 2500000;

    public void setSwfvBytes(byte[] swfvBytes) {
        this.swfvBytes = swfvBytes;        
        logger.info("set swf verification bytes: {}", Utils.toHex(swfvBytes));        
    }

    public RtmpClientHandler(RtmpClientSession session) {
        this.session = session;
        transactionToCommandMap = new HashMap<Integer, String>();
        writer = new FlvWriter(session.getPlayStart(), session.getSaveAs());
    }

    private void writeCommandExpectingResult(Channel channel, Command command) {
        final int id = transactionId++;
        command.setTransactionId(id);
        transactionToCommandMap.put(id, command.getName());
        logger.info("sending command (expecting result): {}", command);
        channel.write(command);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        logger.info("handshake complete, sending 'connect'");
        writeCommandExpectingResult(e.getChannel(), Command.connect(session));
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        final Object o = e.getMessage();    
        final Channel channel = e.getChannel();
        final RtmpMessage message = (RtmpMessage) o;
        switch(message.getHeader().getMessageType()) {
            case CONTROL:
                Control control = (Control) message;
                logger.debug("server control: {}", control);
                switch(control.getType()) {
                    case PING_REQUEST:
                        final int time = control.getTime();
                        logger.debug("server ping: {}", time);
                        Control pong = Control.pingResponse(time);
                        logger.debug("sending ping response: {}", pong);
                        channel.write(pong);
                        break;
                    case SWFV_REQUEST:
                        if(swfvBytes == null) {
                            logger.warn("swf verification not initialized!" 
                                + " not sending response, server likely to stop responding / disconnect");
                        } else {
                            Control swfv = Control.swfvResponse(swfvBytes);
                            logger.info("sending swf verification response: {}", swfv);
                            channel.write(swfv);
                        }
                        break;
                    default:
                        logger.debug("ignoring control message: {}", control);
                }
                break;
            case METADATA_AMF0:
            case METADATA_AMF3:
                Metadata metadata = (Metadata) message;
                if(metadata.getName().equals("onMetaData")) {
                    logger.info("writing server 'onMetaData': {}", metadata);
                    writer.write(message);
                } else {
                    logger.info("ignoring server metadata: {}", metadata);
                }
                break;
            case AUDIO:
            case VIDEO:
            case AGGREGATE:                
                writer.write(message);
                bytesRead += message.getHeader().getSize();
                if((bytesRead - bytesReadLastSent) > bytesReadWindow) {
                    logger.info("sending bytes read ack {}", bytesRead);
                    bytesReadLastSent = bytesRead;
                    channel.write(new BytesRead(bytesRead));
                }
                break;
            case COMMAND_AMF0:
            case COMMAND_AMF3:
                Command command = (Command) message;                
                String name = command.getName();
                logger.info("server command: {}", name);
                if(name.equals("_result")) {
                    String resultFor = transactionToCommandMap.get(command.getTransactionId());
                    logger.info("result for method call: {}", resultFor);
                    if(resultFor.equals("connect")) {
                        writeCommandExpectingResult(channel, Command.createStream());
                    } else if(resultFor.equals("createStream")) {
                        final int streamId = ((Double) command.getArg(0)).intValue();
                        logger.info("streamId to play: {}", streamId);
                        channel.write(Command.play(streamId, session));
                    } else {
                        logger.warn("un-handled server result for: {}", resultFor);
                    }
                } else if(name.equals("onStatus")) {
                    Map<String, Object> temp = (Map) command.getArg(0);
                    String code = (String) temp.get("code");
                    logger.info("onStatus code: {}", code);
                    if (code.equals("NetStream.Failed") || code.equals("NetStream.Play.Failed") || code.equals("NetStream.Play.Stop")) {
                        logger.info("disconnecting, bytes read: {}", bytesRead);
                        writer.close();
                        channel.close();
                    }
                } else {
                    logger.warn("ignoring server command: {}", command);
                }
                break;
            case BYTES_READ:
                logger.info("server bytes read: {}", message);
                break;
            case WINDOW_ACK_SIZE:
                WindowAckSize was = (WindowAckSize) message;                
                if(was.getValue() != bytesReadWindow) {
                    channel.write(SetPeerBw.dynamic(bytesReadWindow));
                }                
                break;
            case SET_PEER_BW:
                SetPeerBw spb = (SetPeerBw) message;                
                if(spb.getValue() != bytesWrittenWindow) {
                    channel.write(new WindowAckSize(bytesWrittenWindow));
                }
                break;
            default:
            logger.info("ignoring rtmp message: {}", message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.error("exception: {}", e.getCause().getMessage());
        writer.close();
        e.getChannel().close();        
    }    

}
