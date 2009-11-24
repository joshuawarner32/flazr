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

package com.flazr.io.flv;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpMessageReader;
import com.flazr.rtmp.message.Aggregate;
import com.flazr.rtmp.message.MessageType;
import com.flazr.rtmp.message.Metadata;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlvReader implements RtmpMessageReader {

    private static final Logger logger = LoggerFactory.getLogger(FlvReader.class);
    
    private final FileChannel in;      
    private final String absolutePath; // just for log
    private final long mediaStartPosition;
    private final Metadata metadata;
    private int aggregateDuration;
    private boolean closed;

    public FlvReader(String fileName) {
        this(new File(fileName));
    }

    public FlvReader(File file) {
        absolutePath = file.getAbsolutePath();
        try {            
            in = new FileInputStream(file).getChannel();
            in.position(13); // skip flv header
            RtmpMessage metadataAtom = next();
            metadata = (Metadata) MessageType.decode(metadataAtom.getHeader(), metadataAtom.encode());
            mediaStartPosition = in.position();
            logger.info("opened file for reading: {}", absolutePath);
            logger.debug("flv file metadata: {}", metadata);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public RtmpMessage[] getStartMessages() {
        return new RtmpMessage[] {getMetadata()};
    }

    @Override
    public void setAggregateDuration(int targetDuration) {
        this.aggregateDuration = targetDuration;
    }

    @Override
    public long getTimePosition() {
        final int time;
        if(hasNext()) {
            time = next().getHeader().getTime();
            prev();
        } else if(hasPrev()) {
            time = prev().getHeader().getTime();
            next();
        } else {
            throw new RuntimeException("not seekable");
        }
        return time;
    }

    private boolean isSyncFrame(final RtmpMessage message) {        
        final byte firstByte = message.encode().getByte(0);
        if((firstByte & 0xF0) == 0x10) {
            return true;
        }
        return false;
    }

    @Override
    public long seek(final long time) {
        logger.debug("trying to seek to: {}", time);
        final long start = getTimePosition();        
        if(time > start) {
            while(hasNext()) {
                final RtmpMessage cursor = next();
                if(cursor.getHeader().getTime() >= time) {                    
                    break;
                }
            }
        } else {
            while(hasPrev()) {
                final RtmpMessage cursor = prev();
                if(cursor.getHeader().getTime() <= time) {
                    next();
                    break;
                }
            }
        }
        // find the closest sync frame prior
        try {
            final long checkPoint = in.position();
            while(hasPrev()) {
                final RtmpMessage cursor = prev();
                if(cursor.getHeader().isVideo() && isSyncFrame(cursor)) {
                    logger.debug("returned seek frame / position: {}", cursor);
                    return cursor.getHeader().getTime();
                }
            }
            // could not find a sync frame !
            // TODO better handling, what if file is audio only
            in.position(checkPoint);
            return getTimePosition();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        if(closed) {
            return false;
        }
        try {
            return in.position() < in.size();
        } catch(Exception e) {
            logger.info("exception when calling hasNext(): {}", e.getMessage());
            return false;
        }
    }


    protected boolean hasPrev() {
        if(closed) {
            return false;
        }
        try {
            return in.position() > mediaStartPosition;
        } catch(Exception e) {
            logger.info("exception when calling hasPrev(): {}", e.getMessage());
            return false;
        }
    }

    protected RtmpMessage prev() {
        try {
            in.position(in.position() - 4);
            final ByteBuffer bb = ByteBuffer.allocate(4);
            in.read(bb);
            bb.flip();
            final long start = in.position() - 4 - bb.getInt();            
            in.position(start);
            final FlvAtom flvAtom = new FlvAtom(in);
            in.position(start);
            return flvAtom;

        } catch(Exception e) {
            throw new RuntimeException(e);
        }        
    }

    private static final int AGGREGATE_SIZE_LIMIT = 65536;

    @Override
    public RtmpMessage next() {
        if(aggregateDuration <= 0) {
            return new FlvAtom(in);
        }
        final ChannelBuffer out = ChannelBuffers.dynamicBuffer();
        int firstAtomTime = -1;
        while(hasNext()) {
            final FlvAtom flvAtom = new FlvAtom(in);
            final int currentAtomTime = flvAtom.getHeader().getTime();
            if(firstAtomTime == -1) {
                firstAtomTime = currentAtomTime;
            }
            final ChannelBuffer temp = flvAtom.write();
            if(out.readableBytes() + temp.readableBytes() > AGGREGATE_SIZE_LIMIT) {
                prev();
                break;
            }
            out.writeBytes(temp);
            if(currentAtomTime - firstAtomTime > aggregateDuration) {
                break;
            }
        }
        return new Aggregate(firstAtomTime, out);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported");
    }

    @Override
    public void close() {
        closed = true;
        try {
            if(in.isOpen()) {
                in.close();
            }
            logger.info("closed file: {}", absolutePath);
        } catch(Exception e) {
            logger.info("exception when closing file: {}", e.getMessage());
        }        
    }

    public static void main(String[] args) {
        FlvReader reader = new FlvReader("IronMan.flv");
        while(reader.hasNext()) {
            RtmpMessage message = reader.next();
            ChannelBuffer data = message.encode();
            byte first = data.getByte(0);
            boolean sync = (first & 0xF0) == 0x10;
            logger.debug("{} {} {}", new Object[]{message, sync, ChannelBuffers.hexDump(data)});
        }
        reader.close();
    }

}
