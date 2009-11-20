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

package com.flazr.rtmp.server;

import com.flazr.rtmp.RtmpConfig;
import com.flazr.io.f4v.F4vReader;
import com.flazr.io.flv.FlvReader;
import com.flazr.rtmp.RtmpHeader;
import com.flazr.rtmp.message.BytesRead;
import com.flazr.rtmp.message.ChunkSize;
import com.flazr.rtmp.message.Control;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpMessageReader;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.SetPeerBw;
import com.flazr.rtmp.message.WindowAckSize;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelPipelineCoverage("one")
public class RtmpServerHandler extends SimpleChannelHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RtmpServerHandler.class);    
    
    private final Timer timer = RtmpServer.GLOBAL_TIMER;    
    private final int TARGET_BUFFER_DURATION = 5000;    
        
    private int bytesReadWindow = 2500000;
    private long bytesRead;
    private long bytesReadLastSent;

    private long bytesWritten;
    private int bytesWrittenWindow = 2500000;
    private int bytesWrittenLastReceived;    
    
    private long startTime;
    private int streamId;    
    private boolean paused;    
    private long seekTime;
    private long timePosition;
    private int currentConversationId;

    private String appName;
    private String clientId;
    private String playName;
    private int playStart = -2;
    private int playLength = -1;
    private RtmpMessageReader reader;

    public static class RtmpServerEvent {}

    private static class WriteNext extends RtmpServerEvent {
        
        private final int conversationId;

        public WriteNext(int id) {
            this.conversationId = id;
        }

    }

    @Override
    public void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        RtmpServer.ALL_CHANNELS.add(e.getChannel());        
        logger.info("opened channel: {}", e);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        if (e.getCause() instanceof ClosedChannelException) {
            logger.info("exception: {}", e);
        } else if(e.getCause() instanceof IOException) {
            logger.info("exception: {}", e.getCause().getMessage());
        } else {
            logger.warn("exception: {}", e.getCause());
        }
        if(e.getChannel().isOpen()) {
            e.getChannel().close();
        }
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        logger.info("channel closed: {}", e);
        if(reader != null) {
            reader.close();
        }
    }

    @Override
    public void writeComplete(final ChannelHandlerContext ctx, final WriteCompletionEvent e) throws Exception {
        bytesWritten += e.getWrittenAmount();        
        super.writeComplete(ctx, e);
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final Channel channel = e.getChannel();
        if(e.getMessage() instanceof WriteNext) {
            final WriteNext mw = (WriteNext) e.getMessage();
            if(mw.conversationId != currentConversationId) {
                logger.info("stopping obsolete conversation id: {}", mw.conversationId);
                return;
            }
            writeNextAndWait(channel);
            return;
        }
        final RtmpMessage message = (RtmpMessage) e.getMessage();        
        bytesRead += message.getHeader().getSize();
        if((bytesRead - bytesReadLastSent) > bytesReadWindow) {
            logger.info("sending bytes read ack after: {}", bytesRead);
            BytesRead ack = new BytesRead(bytesRead);
            channel.write(ack);
            bytesReadLastSent = bytesRead;
        }
        switch(message.getHeader().getMessageType()) {
            case COMMAND_AMF0:
                final Command command = (Command) message;
                final String name = command.getName();
                if(name.equals("connect")) {
                    connectResponse(channel, command);
                } else if(name.equals("createStream")) {
                    streamId = 1;
                    channel.write(Command.createStreamSuccess(command.getTransactionId(), streamId));
                } else if(name.equals("play")) {
                    playResponse(channel, command);
                } else if(name.equals("deleteStream")) {
                    int deleteStreamId = ((Double) command.getArg(0)).intValue();
                    logger.info("deleting stream id: {}", deleteStreamId);
                    ChannelFuture future = channel.write(Control.streamEof(deleteStreamId));
                    future.addListener(ChannelFutureListener.CLOSE);
                } else if(name.equals("closeStream")) {
                    final int clientStreamId = command.getHeader().getStreamId();
                    logger.info("closing stream id: {}", clientStreamId);
                    streamId = 0; // TODO
                } else if(name.equals("pause")) {                    
                    pauseResponse(channel, command);
                } else if(name.equals("seek")) {                    
                    seekResponse(channel, command);
                } else {
                    logger.warn("ignoring client command: {}", command);
                }
                break;
            case BYTES_READ:
                final BytesRead bytesReadByClient = (BytesRead) message;                
                bytesWrittenLastReceived = bytesReadByClient.getValue();
                logger.info("client bytes read: {}, actual: {}", bytesReadByClient, bytesWritten);
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
            logger.warn("ignoring rtmp message: {}", message);
        }
    }

    //==========================================================================

    private void playStartSequence(final Channel channel, final RtmpMessage variation) {
        currentConversationId++;
        timePosition = seekTime;
        logger.info("play, seek: {}, length: {}, conversation: {}",
                new Object[]{seekTime, playLength, currentConversationId});
        startTime = System.currentTimeMillis();
        reader.setAggregateDuration(0);
        channel.write(new ChunkSize(4096));
        channel.write(Control.streamIsRecorded(streamId));
        channel.write(Control.streamBegin(streamId));
        if(variation != null) {
            writeToStream(channel, variation);
        }
        writeToStream(channel, Command.playStart(playName, clientId));
        for(RtmpMessage message : reader.getStartMessages()) {
            writeToStream(channel, message);
        }
        writeNextAndWait(channel);
    }

    private void writeToStream(final Channel channel, final RtmpMessage message) {
        message.getHeader().setStreamId(streamId);
        message.getHeader().setTime((int) seekTime);
        channel.write(message);
    }

    private void writeNextAndWait(final Channel channel) {
        final long elapsedTime = System.currentTimeMillis() - startTime;     
        final long elapsedTimePlusSeek = elapsedTime + seekTime;
        final double clientBuffer = timePosition - elapsedTimePlusSeek;
        if(logger.isDebugEnabled()) {
            logger.debug("bytes written, actual: {}, last client ack: {}, window: {}",
                 new Object[] {bytesWritten, bytesWrittenLastReceived, bytesWrittenWindow});
            logger.debug("elapsed: {}, streamed: {}, buffer: {}",
                    new Object[]{elapsedTimePlusSeek, timePosition, clientBuffer});
        }              
        if(clientBuffer > 100) {
            reader.setAggregateDuration((int) clientBuffer);
        } else {
            reader.setAggregateDuration(0);
        }
        if (!reader.hasNext() || playLength >= 0 && timePosition > (seekTime + playLength)) {
            playStopSequence(channel);
            return;
        }        
        final RtmpMessage message = reader.next();
        final RtmpHeader header = message.getHeader();
        final double compensationFactor = clientBuffer / TARGET_BUFFER_DURATION;
        final long delay = (long) ((header.getTime() - timePosition) * compensationFactor);
        timePosition = header.getTime();
        header.setStreamId(streamId);
        final WriteNext writeNext = new WriteNext(currentConversationId);
        final long writeTime = System.currentTimeMillis();
        final ChannelFuture future = channel.write(message);        
        future.addListener(new ChannelFutureListener() {
            @Override public void operationComplete(ChannelFuture cf) {
                final long completedIn = System.currentTimeMillis() - writeTime;
                if(completedIn > 1000) {
                    logger.warn("channel busy? time taken to write last message: {}", completedIn);
                }
                final long delayToUse = delay - completedIn;
                if(delayToUse > RtmpConfig.TIMER_TICK_SIZE) {
                    timer.newTimeout(new TimerTask() {
                        @Override public void run(Timeout timeout) {
                            Channels.fireMessageReceived(future.getChannel(), writeNext);
                        }
                    }, delayToUse, TimeUnit.MILLISECONDS);
                } else {
                    Channels.fireMessageReceived(future.getChannel(), writeNext);
                }
            }
        });
    }

    private void playStopSequence(Channel channel) {
        final long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("finished, start: {}, elapsed {}, streamed: {}",
                new Object[]{seekTime / 1000, elapsedTime / 1000, (timePosition - seekTime) / 1000});
        seekTime = timePosition; // for next 2 messages
        writeToStream(channel, Metadata.onPlayStatus(timePosition / 1000, bytesWritten));
        writeToStream(channel, Command.playStop(playName, clientId));
        channel.write(Control.streamEof(streamId));
    }

    //==========================================================================

    private void connectResponse(Channel channel, Command connect) {
        appName = (String) connect.getObject().get("app");
        clientId = channel.getId() + "";
        logger.info("connect app name: {}, client id: {}", appName, clientId);
        channel.write(new WindowAckSize(bytesWrittenWindow));
        channel.write(SetPeerBw.dynamic(bytesReadWindow));
        channel.write(Control.streamBegin(streamId));
        Command result = Command.connectSuccess(connect.getTransactionId());
        channel.write(result);
        channel.write(Command.onBWDone());
    }

    private void playResponse(Channel channel, Command play) {        
        seekTime = 0;        
        if(play.getArgCount() > 1) {
            playStart = ((Double) play.getArg(1)).intValue();
        }
        if(play.getArgCount() > 2) {
            playLength = ((Double) play.getArg(2)).intValue();
        }
        final boolean playReset;
        if(play.getArgCount() > 3) {
            playReset = ((Boolean) play.getArg(3));
        } else {
            playReset = true;
        }
        String clientPlayName = (String) play.getArg(0);
        if(!clientPlayName.equals(playName)) {
            playName = clientPlayName;
            final String path = RtmpConfig.SERVER_HOME_DIR + "/apps/" + appName + "/";
            try {
                if(playName.startsWith("mp4:")) {
                    playName = playName.substring(4);
                    reader = new F4vReader(path + playName);
                } else {
                    final String readerPlayName;
                    if(playName.lastIndexOf('.') < playName.length() - 4) {
                        readerPlayName = playName + ".flv";
                    } else {
                        readerPlayName = playName;
                    }
                    reader = new FlvReader(path + readerPlayName);
                }
            } catch(Exception e) {
                logger.info("play failed: {}", e.getMessage());
                channel.write(Command.playFailed(playName, clientId));
                return;
            }
        }
        if(playStart > 0) {
            seekTime = reader.seek(playStart);
        }
        logger.debug("play name {}, start {}, length {}, reset {}",
                new Object[]{playName, playStart, playLength, playReset});
        Command playResetCommand = playReset ? Command.playReset(playName, clientId) : null;
        playStartSequence(channel, playResetCommand);
    }

    private void pauseResponse(Channel channel, Command command) {
        paused = ((Boolean) command.getArg(0));
        int clientTimePosition = ((Double) command.getArg(1)).intValue();
        logger.debug("pause request: {}, client time position: {}", paused, clientTimePosition);
        if(!paused) {            
            logger.debug("doing unpause, seeking and playing");
            seekTime = reader.seek(clientTimePosition);
            playStartSequence(channel, Command.unpauseNotify(playName, clientId));
        } else {
            currentConversationId++; // effectively stops current loop
            logger.debug("doing pause, stopped streaming");
        }
    }

    private void seekResponse(Channel channel, Command command) {
       int clientTimePosition = ((Double) command.getArg(0)).intValue();
        if(!paused) {
            logger.debug("client seek greater than server time position, seeking");            
            seekTime = reader.seek(clientTimePosition);
            Command notify = Command.seekNotify(streamId, (int) seekTime, playName, clientId);
            playStartSequence(channel, notify);
        } else {            
            logger.debug("ignoring seek when paused {}", clientTimePosition);
        }
    }

}
