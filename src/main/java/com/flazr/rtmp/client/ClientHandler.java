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

import com.flazr.rtmp.LoopedReader;
import com.flazr.rtmp.message.Control;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.RtmpPublisher;
import com.flazr.rtmp.RtmpWriter;
import com.flazr.rtmp.message.BytesRead;
import com.flazr.rtmp.message.ChunkSize;
import com.flazr.rtmp.message.WindowAckSize;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.DataMessage;
import com.flazr.rtmp.message.SetPeerBw;
import com.flazr.util.ChannelUtils;
import com.flazr.util.Utils;
import com.flazr.rtmp.PublishType;

import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelPipelineCoverage("one")
public class ClientHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private int transactionId = 1;
    private Map<Integer, ResultHandler> transactionToResultHandler = new HashMap<Integer, ResultHandler>();
    private ClientLogic logic;
    private byte[] swfvBytes;

    private int bytesReadWindow = 2500000;
    private long bytesRead;
    private long bytesReadLastSent;    
    private int bytesWrittenWindow = 2500000;
    
    private RtmpPublisher publisher;
    private RtmpWriter writer;

    private int streamId;

    private int bufferSize;

    private Channel channel;

    private Connection myConnection = new Connection() {

        public void connectToScope(String scopeName, String tcUrl, Map<String, Object> params, Object[] args, ResultHandler handler) {
            writeCommandExpectingResult(channel, 
                Command.connect(scopeName, tcUrl, params, args),
                handler);
        }

        public void createStream(ResultHandler handler) {
            writeCommandExpectingResult(channel,
                Command.createStream(),
                handler);
        }

        public void publish(final int streamId, String streamName, PublishType publishType, int bufferSize, RtmpReader reader, ResultHandler handler) {
            publisher = new RtmpPublisher(reader, streamId, bufferSize, false, false) {
                @Override protected RtmpMessage[] getStopMessages(long timePosition) {
                    return new RtmpMessage[]{Command.unpublish(streamId)};
                }
            };
            writeCommandExpectingResult(channel,
                Command.publish(streamId, streamName, publishType),
                handler);
        }

        public void play(int streamId, String streamName, int start, int length, ResultHandler handler) {
            writeCommandExpectingResult(channel,
                Command.play(streamId, streamName, start, length),
                handler);
        }

        public void message(RtmpMessage message) {
            channel.write(message);
        }
    };

    public void setSwfvBytes(byte[] swfvBytes) {
        this.swfvBytes = swfvBytes;        
        logger.info("set swf verification bytes: {}", Utils.toHex(swfvBytes));        
    }

    public ClientHandler(ClientLogic logic, int bufferSize) {
        this.logic = logic;
        this.bufferSize = bufferSize;
    }

    private void writeCommandExpectingResult(Channel channel, Command command, ResultHandler handler) {
        final int id = transactionId++;
        command.setTransactionId(id);
        transactionToResultHandler.put(id, handler);
        logger.info("sending command (expecting result): {}", command);
        channel.write(command);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("channel opened: {}", e);
        channel = e.getChannel();
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, final ChannelStateEvent e) {
        logger.info("handshake complete, sending 'connect'");
        logic.connected(myConnection);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("channel closed: {}", e);
        logic.closed(myConnection);
        super.channelClosed(ctx, e);
        if(publisher != null) {
            publisher.close();
        }
        if(writer != null) {
            writer.close();
        }
    }
    
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me) {
        if(publisher != null && publisher.handle(me)) {
            return;
        }
        final Channel channel = me.getChannel();
        final RtmpMessage message = (RtmpMessage) me.getMessage();
        switch(message.getHeader().getMessageType()) {
            case CHUNK_SIZE: // handled by decoder
                break;
            case CONTROL:
                Control control = (Control) message;
                logger.debug("control: {}", control);
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
                    case STREAM_BEGIN:
                        if(publisher != null && !publisher.isStarted()) {
                            publisher.start(channel, new ChunkSize(4096));
                            return;
                        }
                        if(streamId !=0) {
                            channel.write(Control.setBuffer(streamId, bufferSize));
                        }
                        break;
                    default:
                        logger.debug("ignoring control message: {}", control);
                }
                break;
            case METADATA_AMF0:
            case METADATA_AMF3:
                logic.onMetaData(myConnection, (Metadata) message);
                break;
            case AUDIO:
            case VIDEO:
            case AGGREGATE:
                logic.onData(myConnection, (DataMessage) message);
                bytesRead += message.getHeader().getSize();
                if((bytesRead - bytesReadLastSent) > bytesReadWindow) {
                    logger.debug("sending bytes read ack {}", bytesRead);
                    bytesReadLastSent = bytesRead;
                    channel.write(new BytesRead(bytesRead));
                }
                break;
            case COMMAND_AMF0:
            case COMMAND_AMF3:
                Command command = (Command) message;
                String name = command.getName();
                logger.debug("server command: {}", name);
                if(name.equals("_result")) {
                    ResultHandler handler = transactionToResultHandler.get(command.getTransactionId());
                    if(handler != null) {
                        handler.handleResult(command.getArg(0));
                        logger.info("result for method call: {}", command.getArg(0));
                    } else {
                        logger.warn("un-handled server result for: {}", command.getArg(0));
                    }
                } else if(name.equals("onStatus")) {
                    final Map<String, Object> temp = (Map) command.getArg(0);
                    final String code = (String) temp.get("code");
                    logger.info("onStatus code: {}", code);
                    if (code.equals("NetStream.Failed") // TODO cleanup
                            || code.equals("NetStream.Play.Failed")
                            || code.equals("NetStream.Play.Stop")
                            || code.equals("NetStream.Play.StreamNotFound"))
                    {
                        logger.info("disconnecting, code: {}, bytes read: {}", code, bytesRead);
                        channel.close();
                        return;
                    }
                    if(code.equals("NetStream.Publish.Start")
                            && publisher != null && !publisher.isStarted())
                    {
                        publisher.start(channel, new ChunkSize(4096));
                        return;
                    }
                    if (publisher != null && code.equals("NetStream.Unpublish.Success")) {
                        logger.info("unpublish success, closing channel");
                        ChannelFuture future = channel.write(Command.closeStream(streamId));
                        future.addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                } else if(name.equals("close")) {
                    logger.info("server called close, closing channel");
                    channel.close();
                    return;
                } else if(name.equals("_error")) {
                    logger.error("closing channel, server resonded with error: {}", command);
                    channel.close();
                    return;
                } else {
                    Object result = logic.onCommand(myConnection, command);
                    // TODO: send result back
                }
                break;
            case BYTES_READ:
                logger.info("ack from server: {}", message);
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
        if(publisher != null && publisher.isStarted()) { // TODO better state machine
            publisher.fireNext(channel, 0);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        ChannelUtils.exceptionCaught(e);
    }    

}
