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
import com.flazr.util.ValueToEnum;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class Audio extends DataMessage {

    public static enum Format implements ValueToEnum.IntValue {

        NONE(0),
        ADPCM(1),
        MP3(2),
        PCM(3),
        NELLY_16(4),
        NELLY_8(5),
        NELLY(6),
        G711_A(7),
        G711_U(8),
        RESERVED(9),
        AAC(10),
        SPEEX(11),
        MP3_8(14),
        DEVICE_SPECIFIC(15);


        private final int value;

        Format(final int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }

        private static final ValueToEnum<Format> converter = new ValueToEnum<Format>(Format.values());

        public static Format valueToEnum(final int value) {
            return converter.valueToEnum(value);
        }

    }

    @Override
    public boolean isConfig() { // TODO now hard coded for mp4a
        return data.readableBytes() > 3 && data.getInt(0) == 0xaf001310;
    }

    public Audio(final RtmpHeader header, final ChannelBuffer in) {
        super(header, in);
    }

    public Audio(final byte[] ... bytes) {
        data = ChannelBuffers.wrappedBuffer(bytes);
    }

    public Audio(final int time, final byte[] prefix, final byte[] audioData) {
        header.setTime(time);
        data = ChannelBuffers.wrappedBuffer(prefix, audioData);
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
