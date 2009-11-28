package com.flazr.io;

import com.flazr.util.Utils;
import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferedFileChannel {

    private static final Logger logger = LoggerFactory.getLogger(BufferedFileChannel.class);

    private static final int BUFFER_SIZE = 1024;

    private final String absolutePath;
    private final FileChannel in;
    private final long fileSize;
    private long position;    
    private ChannelBuffer buffer;

    public BufferedFileChannel(final String path) {
        this(new File(path));
    }

    public BufferedFileChannel(final File file) {
        absolutePath = file.getAbsolutePath();
        try {
            in = new FileInputStream(file).getChannel();
            fileSize = in.size();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        preloadBuffer(0);
        logger.info("opened file: {}", absolutePath);
    }

    public long size() {
        return fileSize;
    }

    public long position() {
        return position;
    }

    public void position(final long newPosition) {
        if(newPosition == position) {
            return;
        }
        if(newPosition > position && newPosition < position + buffer.readableBytes()) {
            buffer.skipBytes((int) (newPosition - position));
            position = newPosition;
            return;
        }
        preloadBuffer(newPosition);
    }

    private void preloadBuffer(final long newPosition) {
        try {
            in.position(newPosition);
            position = newPosition;      
            final int size = (int) Math.min(BUFFER_SIZE, fileSize - position);
            buffer = Utils.read(in, size);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ChannelBuffer read(final int size) {
        if(size < buffer.readableBytes()) {
            position += size;
            return buffer.readBytes(size);
        }
        if(size == buffer.readableBytes()) {
            final ChannelBuffer temp = buffer;
            preloadBuffer(position + size);
            return temp;
        }
        try { // TODO improve
            in.position(position);
            final ChannelBuffer temp = Utils.read(in, size);
            preloadBuffer(position + size);
            return temp;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int readInt() {
        return read(4).readInt();
    }

    public byte[] readBytes(final int size) {
        if(size == 0) {
            return new byte[0];
        }
        final ChannelBuffer temp = read(size);
        final byte[] bytes = new byte[size];
        temp.readBytes(bytes);
        return bytes;
    }

    public void close() {
        try {
            in.close();
        } catch(Exception e) {
            logger.warn("error closing file {}: {}", absolutePath, e.getMessage());
        }
        logger.info("closed file: {}", absolutePath);
    }

}
