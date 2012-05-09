package com.flazr.rtmp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RtmpPusher implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(RtmpPusher.class);
    
    private boolean stopped;     
    private boolean paused;
    private long startTime = -1;
    private final ExecutorService executor;  
    private final RtmpReader reader;
    private boolean started;    
    private long playDuration = -1;
    private int bufferDuration;
    private long currentPosition;
    private long startPosition;
    private int streamId;
    
    public RtmpPusher(RtmpReader reader) {
        this.reader = reader;
        executor = Executors.newSingleThreadExecutor();
    }
    
    public void setBufferDuration(int bufferDuration) {
        this.bufferDuration = bufferDuration;
    }    
    
    public void start(int streamId, long playPosition, long playDuration, RtmpMessage ... messages) {  
        this.streamId = streamId;
        this.playDuration = playDuration;
        if (playPosition > 0) { // could be -2, TODO why?
            currentPosition = reader.seek(playPosition);
        }
        startTime = -1;
        for (RtmpMessage message : messages) {
            onMessageInternal(message);
        }
        for (RtmpMessage message : reader.getStartMessages()) {
            onMessageInternal(message);
        }                
        started = true;
        paused = false;
        stopped = false;
        executor.execute(this);                
    }      
    
    private void onMessageInternal(RtmpMessage message) {
        if (message.getHeader().getChannelId() > 2) {
            message.getHeader().setStreamId(streamId);
            message.getHeader().setTime((int) currentPosition);
        } 
        onMessage(message);
    }
    
    public void pause() {
        paused = true;
    }

    public boolean isPaused() {
        return paused;
    }        
        
    public void stop() {
        stopped = true;
    }

    public boolean isStarted() {
        return started;
    }           

    @Override
    public void run() {
        logger.info("publish thread started");
        while (reader.hasNext() && !stopped && !paused) {
            final RtmpMessage message = reader.next();   
            if (message.getHeader().isVideo()) { // TODO if only audio stream                
                final long now = System.currentTimeMillis();
                currentPosition = message.getHeader().getTime();
                if (startTime == -1) {
                    startTime = now;
                    startPosition = currentPosition;
                }
                final long elapsedTime = now - startTime;                
                final long playedTime = currentPosition - startPosition;                
                if (playDuration > 0 && playedTime > playDuration) {
                    logger.info("stopping, completed playing requested duration");
                    stopped = true;
                    break;
                }
                final long delay = playedTime - elapsedTime - bufferDuration;                
                if (delay > 0) { // sleep
                    try {
                        Thread.sleep(delay);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            onMessageInternal(message);            
        }                  
        started = false;    
        if (paused) {
            logger.info("pause signal success, publish thread stopped");
        } else {
            if (stopped) {
                logger.info("stop signal success, publish thread stopped");
            } else {
                logger.info("stream ended, publish thread stopped");
            }
            onStop(currentPosition);
        }
    }   
    
    public void close() {
        stopped = true;        
        executor.shutdown();
        reader.close();
    }        
    
    public abstract void onMessage(RtmpMessage message);
        
    public abstract void onStop(long timePosition);  
    
}
