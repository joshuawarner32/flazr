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
    
    private final int TIMER_TICK_SIZE;

    private final RtmpReader reader;
    private final Timer timer;

    private int streamId;
    private long startTime;
    private long seekTime;
    private long timePosition;
    private int currentConversationId;    
    private int playLength = -1;
    private boolean paused;
    private int bufferDuration = 5000;

    public static class Event {

        private final int conversationId;

        public Event(final int conversationId) {
            this.conversationId = conversationId;
        }

        public int getConversationId() {
            return conversationId;
        }

    }

    public RtmpPublisher(RtmpReader reader, Timer timer, int streamId, int bufferDuration) {
        TIMER_TICK_SIZE = RtmpConfig.TIMER_TICK_SIZE;
        this.reader = reader;
        this.timer = timer;
        this.streamId = streamId;
        this.bufferDuration = bufferDuration;
        logger.debug("publisher init, streamId: {}", streamId);
    }

    public static RtmpReader getReader(String path) {
        if(path.toLowerCase().startsWith("mp4:")) {
            return new F4vReader(path.substring(4));
        } else if (path.toLowerCase().endsWith(".f4v")) {
            return new F4vReader(path);
        } else {
            return new FlvReader(path);
        }
    }

    public RtmpReader getReader() {
        return reader;
    }

    public long getTimePosition() {
        return timePosition;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setBufferDuration(int bufferDuration) {
        this.bufferDuration = bufferDuration;
    }

    public boolean handle(final MessageEvent me) {
        if(me.getMessage() instanceof Event) {
            final Event pe = (Event) me.getMessage();
            if(pe.getConversationId() != currentConversationId) {
                logger.debug("stopping obsolete conversation id: {}, current: {}",
                        pe.getConversationId(), currentConversationId);
                return true;
            }
            write(me.getChannel());
            return true;
        }
        return false;
    }

    public void start(final Channel channel, final int seekTime, final int playLength, final RtmpMessage ... messages) {
        this.playLength = playLength;
        start(channel, seekTime, messages);
    }

    public void start(final Channel channel, final int seekTimeRequested, final RtmpMessage ... messages) {
        paused = false;
        currentConversationId++;
        startTime = System.currentTimeMillis();        
        if(seekTimeRequested >= 0) {
            seekTime = reader.seek(seekTimeRequested);
        } else {
            seekTime = 0;
        }
        timePosition = seekTime;
        logger.debug("play start, seek requested: {} actual seek: {}, play length: {}, conversation: {}",
                new Object[]{seekTimeRequested, seekTime, playLength, currentConversationId});
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
        if (!reader.hasNext() || playLength >= 0 && timePosition > (seekTime + playLength)) {
            stop(channel);
            return;
        }
        final long elapsedTime = System.currentTimeMillis() - startTime;
        final long elapsedTimePlusSeek = elapsedTime + seekTime;
        final double clientBuffer = timePosition - elapsedTimePlusSeek;
        if(logger.isDebugEnabled()) {
            logger.debug("elapsed: {}, streamed: {}, buffer: {}",
                    new Object[]{elapsedTimePlusSeek, timePosition, clientBuffer});
        }
        if(clientBuffer > TIMER_TICK_SIZE) { // TODO cleanup
            reader.setAggregateDuration((int) clientBuffer);
        } else {
            reader.setAggregateDuration(0);
        }
        final RtmpMessage message = reader.next();
        final RtmpHeader header = message.getHeader();
        final double compensationFactor = clientBuffer / bufferDuration;
        final long delay = (long) ((header.getTime() - timePosition) * compensationFactor);
        timePosition = header.getTime();
        header.setStreamId(streamId);
        final Event readyForNext = new Event(currentConversationId);
        final long writeTime = System.currentTimeMillis();
        final ChannelFuture future = channel.write(message);
        future.addListener(new ChannelFutureListener() {
            @Override public void operationComplete(final ChannelFuture cf) {
                final long completedIn = System.currentTimeMillis() - writeTime;
                if(completedIn > 2000) {
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

    private void stop(final Channel channel) {
        currentConversationId++;
        final long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("finished, start: {}, elapsed {}, streamed: {}",
                new Object[]{seekTime / 1000, elapsedTime / 1000, (timePosition - seekTime) / 1000});
        for(RtmpMessage message : getStopMessages(timePosition)) {
            writeToStream(channel, message);
        }
    }

    protected abstract RtmpMessage[] getStopMessages(long timePosition);

}
