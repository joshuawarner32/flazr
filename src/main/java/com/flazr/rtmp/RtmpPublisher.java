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

package com.flazr.rtmp;

import com.flazr.io.f4v.F4vReader;
import com.flazr.io.flv.FlvReader;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RtmpPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RtmpPublisher.class);
    
    private final static int TIMER_TICK_SIZE = 100;

    private final RtmpMessageReader reader;
    private final Timer timer;

    private int streamId;
    private long startTime;
    private long seekTime;
    private long timePosition;
    private int currentConversationId;    
    private int playLength;
    private boolean paused;
    private int targetBufferDuration = 5000;

    public static RtmpMessageReader getReader(String appName, String streamName) {
        final String path = RtmpConfig.SERVER_HOME_DIR + "/apps/" + appName + "/";
        try {
            if(streamName.startsWith("mp4:")) {
                streamName = streamName.substring(4);
                return new F4vReader(path + streamName);
            } else {
                final String readerPlayName;
                if(streamName.lastIndexOf('.') < streamName.length() - 4) {
                    readerPlayName = streamName + ".flv";
                } else {
                    readerPlayName = streamName;
                }
                return new FlvReader(path + readerPlayName);
            }
        } catch(Exception e) {
            logger.info("reader creation failed: {}", e.getMessage());
            return null;
        }
    }

    public RtmpPublisher(RtmpMessageReader reader, Timer timer, int streamId, int playLength) {
        this.reader = reader;
        this.timer = timer;
        this.streamId = streamId;
        this.playLength = playLength;
        logger.info("publisher init, streamId: {}, play length: {}", streamId, playLength);
    }

    public void setTargetBufferDuration(int targetBufferDuration) {
        this.targetBufferDuration = targetBufferDuration;
    }

    public RtmpMessageReader getReader() {
        return reader;
    }

    public long getTimePosition() {
        return timePosition;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean handle(final MessageEvent me) {
        if(me.getMessage() instanceof RtmpPublisherEvent) {
            final RtmpPublisherEvent rse = (RtmpPublisherEvent) me.getMessage();
            if(rse.getConversationId() != currentConversationId) {
                logger.info("stopping obsolete conversation id: {}, current: {}",
                        rse.getConversationId(), currentConversationId);
                return true;
            }
            write(me.getChannel());
            return true;
        }
        return false;
    }

    public void start(final Channel channel, final int seekTime, final RtmpMessage ... messages) {
        paused = false;
        currentConversationId++;
        startTime = System.currentTimeMillis();
        this.seekTime = seekTime;
        timePosition = seekTime;
        logger.info("play start, seek: {}, length: {}, conversation: {}",
                new Object[]{seekTime, playLength, currentConversationId});        
        reader.setAggregateDuration(0);
        for(final RtmpMessage message : messages) {
            writeToStream(channel, message);
        }
        for(final RtmpMessage message : reader.getStartMessages()) {
            writeToStream(channel, message);
        }
        write(channel);
    }

    private void writeToStream(final Channel channel, final RtmpMessage message) {
        if(message.getHeader().getChannelId() > 2) {
            message.getHeader().setStreamId(streamId);
            message.getHeader().setTime((int) timePosition);
        }
        channel.write(message);
    }

    public void write(final Channel channel) {
        final long elapsedTime = System.currentTimeMillis() - startTime;
        final long elapsedTimePlusSeek = elapsedTime + seekTime;
        final double clientBuffer = timePosition - elapsedTimePlusSeek;
        if(logger.isDebugEnabled()) {
            logger.debug("elapsed: {}, streamed: {}, buffer: {}",
                    new Object[]{elapsedTimePlusSeek, timePosition, clientBuffer});
        }
        if(clientBuffer > 100 && targetBufferDuration > 1000) { // TODO cleanup
            reader.setAggregateDuration((int) clientBuffer);
        } else {
            reader.setAggregateDuration(0);
        }
        if (!reader.hasNext() || playLength >= 0 && timePosition > (seekTime + playLength)) {
            stop(channel);
            return;
        }
        final RtmpMessage message = reader.next();
        final RtmpHeader header = message.getHeader();
        final double compensationFactor = clientBuffer / targetBufferDuration;
        final long delay = (long) ((header.getTime() - timePosition) * compensationFactor);
        timePosition = header.getTime();
        header.setStreamId(streamId);
        final RtmpPublisherEvent readyForNext = new RtmpPublisherEvent(currentConversationId);
        final long writeTime = System.currentTimeMillis();
        final ChannelFuture future = channel.write(message);
        future.addListener(new ChannelFutureListener() {
            @Override public void operationComplete(ChannelFuture cf) {
                final long completedIn = System.currentTimeMillis() - writeTime;
                if(completedIn > 1000) {
                    logger.warn("channel busy? time taken to write last message: {}", completedIn);
                }
                final long delayToUse = delay - completedIn;
                if(delayToUse > TIMER_TICK_SIZE) {
                    timer.newTimeout(new TimerTask() {
                        @Override public void run(Timeout timeout) {
                            Channels.fireMessageReceived(channel, readyForNext);
                        }
                    }, delayToUse, TimeUnit.MILLISECONDS);
                } else {
                    Channels.fireMessageReceived(channel, readyForNext);
                }
            }
        });
    }

    public void pause() {
        paused = true;
        currentConversationId++;
    }

    public void stop(final Channel channel) {
        currentConversationId++;
        final long elapsedTime = System.currentTimeMillis() - startTime;
        for(final RtmpMessage message : getStopMessages(timePosition)) {
            writeToStream(channel, message);
        }
        logger.info("finished, start: {}, elapsed {}, streamed: {}",
                new Object[]{seekTime / 1000, elapsedTime / 1000, (timePosition - seekTime) / 1000});
    }

    protected abstract RtmpMessage[] getStopMessages(long timePosition);

}
