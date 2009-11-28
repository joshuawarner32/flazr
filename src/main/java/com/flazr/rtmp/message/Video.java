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
import com.flazr.util.Utils;
import com.flazr.util.ValueToEnum;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class Video extends DataMessage {

    public static enum CodecType implements ValueToEnum.IntValue {

        JPEG(1),
        H263(2),
        SCREEN(3),
        ON2VP6(4),
        ON2VP6_ALPHA(5),
        SCREEN_V2(6),
        AVC(7);

        private final int value;

        CodecType(final int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }

        private static final ValueToEnum<CodecType> converter = new ValueToEnum<CodecType>(CodecType.values());

        public static CodecType valueToEnum(final int value) {
            return converter.valueToEnum(value);
        }

    }

    @Override
    public boolean isConfig() { // TODO now hard coded for avc1
        return data.readableBytes() > 3 && data.getInt(0) == 0x17000000;
    }

    public Video(final RtmpHeader header, final ChannelBuffer in) {
        super(header, in);
    }

    public Video(final byte[] ... bytes) {
        data = ChannelBuffers.wrappedBuffer(bytes);
    }

    public Video(final int time, final byte[] prefix, final int compositionOffset, final byte[] videoData) {
        header.setTime(time);
        data = ChannelBuffers.wrappedBuffer(prefix, Utils.toInt24(compositionOffset), videoData);
        header.setSize(data.readableBytes());
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

}
