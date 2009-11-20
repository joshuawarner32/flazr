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

import com.flazr.rtmp.message.*;
import com.flazr.util.ByteToEnum;
import com.flazr.util.Utils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpHeader {
    
    private static final Logger logger = LoggerFactory.getLogger(RtmpHeader.class);

    public static enum Type implements ByteToEnum.Convert {

        LARGE(0x00), MEDIUM(0x01), SMALL(0x02), TINY(0x03);

        private final byte value;        

        private Type(int value) {
            this.value = (byte) value;            
        }

        @Override
        public byte byteValue() {
            return value;
        }

        private static final ByteToEnum<Type> converter = new ByteToEnum<Type>(Type.values());

        public static Type parseByte(byte b) {
            return converter.parseByte(b);
        }

    }

    public static final int MAX_CHANNEL_ID = 65600;
    public static final int MAX_NORMAL_HEADER_TIME = 0xFFFFFF;
    public static final int MAX_ENCODED_SIZE = 18;

    private Type headerType;
    private int channelId;
    private int deltaTime;
    private int time;    
    private int size;
    private MessageType messageType;
    private int streamId;

    public RtmpHeader(ChannelBuffer in, RtmpHeader[] incompleteHeaders) {
        //=================== TYPE AND CHANNEL (1 - 3 bytes) ===================
        final byte firstByte = in.readByte();
        final int typeAndChannel;
        final byte headerTypeByte;
        if ((firstByte & 0x3f) == 0) {
            typeAndChannel = ((int) firstByte & 0xff) << 8 | ((int) in.readByte() & 0xff);
            channelId = 64 + (typeAndChannel & 0xff);
            headerTypeByte = (byte) (typeAndChannel >> 14);
        } else if ((firstByte & 0x3f) == 1) {
            typeAndChannel = ((int) firstByte & 0xff) << 16 | ((int) in.readByte() & 0xff) << 8 | ((int) in.readByte() & 0xff);
            channelId = 64 + ((typeAndChannel >> 8) & 0xff) + ((typeAndChannel & 0xff) << 8);
            headerTypeByte = (byte) (typeAndChannel >> 22);
        } else {
            typeAndChannel = (int) firstByte & 0xff;
            channelId = (typeAndChannel & 0x3f);
            headerTypeByte = (byte) (typeAndChannel >> 6);
        }
        headerType = Type.parseByte(headerTypeByte);
        //========================= REMAINING HEADER ===========================
        final RtmpHeader prevHeader = incompleteHeaders[channelId];
        // logger.debug("so far: {}, prev {}", this, prevHeader);
        switch(headerType) {
            case LARGE:
                time = in.readMedium();
                size = in.readMedium();
                messageType = MessageType.parseByte(in.readByte());
                streamId = Utils.readInt32Reverse(in);
                if(time == MAX_NORMAL_HEADER_TIME) {
                    time = in.readInt();
                }
                break;
            case MEDIUM:
                deltaTime = in.readMedium();
                size = in.readMedium();
                messageType = MessageType.parseByte(in.readByte());
                streamId = prevHeader.streamId;
                if(deltaTime == MAX_NORMAL_HEADER_TIME) {
                    deltaTime = in.readInt();
                }
                break;
            case SMALL:
                deltaTime = in.readMedium();
                size = prevHeader.size;
                messageType = prevHeader.messageType;
                streamId = prevHeader.streamId;
                if(deltaTime == MAX_NORMAL_HEADER_TIME) {
                    deltaTime = in.readInt();
                }
                break;
            case TINY:
                headerType = prevHeader.headerType; // preserve original
                time = prevHeader.time;
                deltaTime = prevHeader.deltaTime;
                size = prevHeader.size;
                messageType = prevHeader.messageType;
                streamId = prevHeader.streamId;
                break;
        }        
    }

    public RtmpHeader(MessageType messageType, int time, int size) {
        this(messageType);
        this.time = time;
        this.size = size;
    }

    public RtmpHeader(MessageType messageType) {
        this.messageType = messageType;
        headerType = Type.LARGE;
        channelId = messageType.getDefaultChannelId();
    }

    public boolean isMedia() {
        switch(messageType) {
            case AUDIO:
            case VIDEO:
            case AGGREGATE:
                return true;
            default:
                return false;
        }
    }

    public boolean isAggregate() {
        return messageType == MessageType.AGGREGATE;
    }

    public boolean isAudio() {
        return messageType == MessageType.AUDIO;
    }

    public boolean isVideo() {
        return messageType == MessageType.VIDEO;
    }

    public boolean isLarge() {
        return headerType == Type.LARGE;
    }

    public boolean isControl() {
        return messageType == MessageType.CONTROL;
    }

    public boolean isChunkSize() {
        return messageType == MessageType.CHUNK_SIZE;
    }

    public Type getHeaderType() {
        return headerType;
    }

    public void setHeaderType(Type headerType) {
        this.headerType = headerType;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getDeltaTime() {
        return deltaTime;
    }

    public void setDeltaTime(int deltaTime) {
        this.deltaTime = deltaTime;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public void encode(ChannelBuffer out) {
        out.writeBytes(encodeHeaderTypeAndChannel(headerType.value, channelId));
        if(headerType == Type.TINY) {
            return;
        }     
        final boolean extendedTime;
        if(headerType == Type.LARGE) {
            extendedTime = time >= MAX_NORMAL_HEADER_TIME;             
        } else {
            extendedTime = deltaTime >= MAX_NORMAL_HEADER_TIME;
        }
        if(extendedTime) {
            out.writeMedium(MAX_NORMAL_HEADER_TIME); 
        } else {                                        // LARGE / MEDIUM / SMALL
            out.writeMedium(headerType == Type.LARGE ? time : deltaTime);
        }
        if(headerType != Type.SMALL) {
            out.writeMedium(size);                      // LARGE / MEDIUM
            out.writeByte(messageType.byteValue());     // LARGE / MEDIUM
            if(headerType == Type.LARGE) {
                Utils.writeInt32Reverse(out, streamId); // LARGE
            }
        }
        if(extendedTime) {
            out.writeInt(headerType == Type.LARGE ? time : deltaTime);
        }
    }

    public byte[] getTinyHeader() {
        return encodeHeaderTypeAndChannel(Type.TINY.byteValue(), channelId);
    }

    private static byte[] encodeHeaderTypeAndChannel(final byte headerTypeByte, final int channelId) {        
        if (channelId <= 63) {
            return new byte[] {(byte) ((headerTypeByte << 6) + channelId)};
        } else if (channelId <= 320) {
            return new byte[] {(byte) (headerTypeByte << 6), (byte) (channelId - 64)};
        } else {            
            return new byte[] {(byte) ((headerTypeByte << 6) | 1),
                (byte) ((channelId - 64) & 0xff), (byte) ((channelId - 64) >> 8)};
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(headerType.ordinal());
        sb.append(' ').append(messageType);
        sb.append(" c").append(channelId);        
        sb.append(" #").append(streamId);        
        sb.append(" t").append(time);
        sb.append(" (").append(deltaTime);
        sb.append(") s").append(size);
        sb.append(']');
        return sb.toString();
    }

}
