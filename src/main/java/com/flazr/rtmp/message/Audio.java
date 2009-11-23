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

public class Audio extends DataMessage {

    public Audio(final RtmpHeader header, final ChannelBuffer in) {
        super(header, in);
    }

    public Audio(final byte[] ... bytes) {
        data = ChannelBuffers.wrappedBuffer(bytes);
    }

    public Audio(final int time, final byte[] prefix, final ByteBuffer bb) {
        header.setTime(time);
        data = ChannelBuffers.wrappedBuffer(ByteBuffer.wrap(prefix), bb);
        header.setSize(data.readableBytes());
    }
    
    public static Audio empty() {
        Audio empty = new Audio();
        empty.data = ChannelBuffers.EMPTY_BUFFER;
        return empty;
    }

    @Override
    MessageType getMessageType() {
        return MessageType.AUDIO;
    }

}
