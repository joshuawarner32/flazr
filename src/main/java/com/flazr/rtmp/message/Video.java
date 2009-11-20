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

package com.flazr.rtmp.message;

import com.flazr.rtmp.RtmpHeader;
import java.nio.ByteBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class Video extends AbstractMessage {
    
    private ChannelBuffer data;

    public Video(RtmpHeader header, ChannelBuffer in) {
        super(header, in);
    }

    public Video(byte[] ... bytes) {
        data = ChannelBuffers.wrappedBuffer(bytes);
    }

    public Video(int time, byte[] prefix, int compositionOffset, ByteBuffer bb) {
        header.setTime(time);
        final ChannelBuffer cb = ChannelBuffers.buffer(prefix.length + 3);
        cb.writeBytes(prefix);
        cb.writeMedium(compositionOffset);
        data = ChannelBuffers.wrappedBuffer(cb.toByteBuffer(), bb);
        header.setSize(data.readableBytes());
    }

    private Video() {
        super();
    }

    public static Video empty() {
        Video empty = new Video();
        empty.data = ChannelBuffers.wrappedBuffer(new byte[2]);
        return empty;
    }

    @Override
    MessageType getMessageType() {
        return MessageType.VIDEO;
    }

    @Override
    public ChannelBuffer encode() {
        return data;
    }

    @Override
    public void decode(ChannelBuffer in) {
        data = in;
    }

    @Override
    public String toString() {
        return super.toString() + ChannelBuffers.hexDump(data);
    }

}
