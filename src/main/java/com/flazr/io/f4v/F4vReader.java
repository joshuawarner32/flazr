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

package com.flazr.io.f4v;

import com.flazr.io.flv.FlvAtom;
import com.flazr.rtmp.RtmpHeader;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpMessageReader;
import com.flazr.rtmp.message.Aggregate;
import com.flazr.rtmp.message.Audio;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.Video;
import com.flazr.util.Utils;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class F4vReader implements RtmpMessageReader {

    private static final Logger logger = LoggerFactory.getLogger(F4vReader.class);

    private static final byte[] MP4A_BEGIN = Utils.fromHex("af0013100000");
    private static final byte[] MP4A_PREFIX = Utils.fromHex("af01");
    private static final byte[] AVC1_BEGIN_PREFIX = Utils.fromHex("1700000000");
    private static final byte[] AVC1_PREFIX_KEYFRAME = Utils.fromHex("1701");
    private static final byte[] AVC1_PREFIX = Utils.fromHex("2701");

    private byte[] AVC1_BEGIN;

    private final FileChannel in;
    private final String absolutePath; // just for log
    private final List<Sample> samples;
    private final Metadata metadata;

    private int cursor;
    private int aggregateDuration;
    private boolean closed;

    public F4vReader(String fileName) {
        this(new File(fileName));
    }

    public F4vReader(File file) {
        absolutePath = file.getAbsolutePath();
        try {
            in = new FileInputStream(file).getChannel();
            final MovieInfo movie = new MovieInfo(in);
            AVC1_BEGIN = movie.getVideoDecoderConfig();
            logger.debug("video decoder config inited: {}", Utils.toHex(AVC1_BEGIN));
            metadata = Metadata.onMetaData(movie);
            samples = movie.getSamples();
            cursor = 0;
            logger.info("opened file for reading: {}", absolutePath);
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
        return new RtmpMessage[] {
            Metadata.rtmpSampleAccess(),
            Audio.empty(),
            Metadata.dataStart(),
            // Video.empty(),
            getMetadata(),
            new Video(AVC1_BEGIN_PREFIX, AVC1_BEGIN),
            new Audio(MP4A_BEGIN)
        };
    }

    @Override
    public void setAggregateDuration(int targetDuration) {
        this.aggregateDuration = targetDuration;
    }

    @Override
    public long getTimePosition() {
        final int index;
        if(cursor == samples.size()) {
            index = cursor - 1;
        } else {
            index = cursor;
        }
        return samples.get(index).getTime();
    }

    @Override
    public long seek(long timePosition) {
        cursor = 0;
        while(cursor < samples.size()) {
            final Sample sample = samples.get(cursor);
            if(sample.getTime() >= timePosition) {
                break;
            }
            cursor++;
        }
        while(!samples.get(cursor).isSyncSample() && cursor > 0) {
            cursor--;
        }
        return samples.get(cursor).getTime();
    }

    @Override
    public boolean hasNext() {
        if(closed) {
            return false;
        }
        return cursor < samples.size();
    }

    private static final int AGGREGATE_SIZE_LIMIT = 65536;

    @Override
    public RtmpMessage next() {
        if(aggregateDuration <= 0) {
            return getMessage(samples.get(cursor++));
        }
        final ChannelBuffer out = ChannelBuffers.dynamicBuffer();
        int startSampleTime = -1;
        while(cursor < samples.size()) {
            final Sample sample = samples.get(cursor++);
            if(startSampleTime == -1) {
                startSampleTime = sample.getTime();
            }
            final RtmpMessage message = getMessage(sample);
            final RtmpHeader header = message.getHeader();
            final FlvAtom flvAtom = new FlvAtom(header.getMessageType(), header.getTime(), message.encode());
            final ChannelBuffer temp = flvAtom.write();
            if(out.readableBytes() + temp.readableBytes() > AGGREGATE_SIZE_LIMIT) {
                cursor--;
                break;
            }
            out.writeBytes(temp);
            if(sample.getTime() - startSampleTime > aggregateDuration) {
                break;
            }
        }
        return new Aggregate(startSampleTime, out);
    }

    private RtmpMessage getMessage(final Sample sample) {
        final ByteBuffer bb = ByteBuffer.allocate(sample.getSize());
        try {
            in.position(sample.getFileOffset());
            in.read(bb);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        final int time = sample.getTime();
        final byte[] prefix;
        bb.flip();
        if(sample.isVideo()) {
            if(sample.isSyncSample()) {
                prefix = AVC1_PREFIX_KEYFRAME;
            } else {
                prefix = AVC1_PREFIX;
            }
            return new Video(time, prefix, sample.getCompositionTimeOffset(), bb);
        } else {
            prefix = MP4A_PREFIX;
            return new Audio(time, prefix, bb);
        }
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

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported");
    }    

    public static void main(String[] args) {
        F4vReader reader = new F4vReader("home/apps/vod/sample1_150kbps.f4v");
        while(reader.hasNext()) {
            logger.debug("read: {}", reader.next());
        }
    }

}
