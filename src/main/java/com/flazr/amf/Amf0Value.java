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

package com.flazr.amf;


import com.flazr.util.ByteToEnum;
import java.util.Arrays;
import static com.flazr.amf.Amf0Value.Type.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Amf0Value {

    private static final Logger logger = LoggerFactory.getLogger(Amf0Value.class);

    public static enum Type implements ByteToEnum.Convert {

        NUMBER(0x00),
        BOOLEAN(0x01),
        STRING(0x02),
        OBJECT(0x03),
        NULL(0x05),
        UNDEFINED(0x06),
        MAP(0x08),
        ARRAY(0x0A),
        DATE(0x0B),
        LONG_STRING(0x0C),
        UNSUPPORTED(0x0D);

        private final byte value;

        private Type(int byteValue) {
            this.value = (byte) byteValue;
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

    private static final byte[] OBJECT_END_MARKER = new byte[]{0x00, 0x00, 0x09};
    
    private Type type;    
    private Object value;

    private Amf0Value() {}

    public Object getValue() {
        return value;
    }

    public Amf0Value(final Object o) {
        value = o;
        if (o == null) {
            type = NULL;
        } else if (o instanceof Number) {
            type = NUMBER;
            if(!(o instanceof Double)) {
                value = Double.parseDouble(o.toString()); // converts int also
            }
        } else if (o instanceof Boolean) {
            type = BOOLEAN;
        } else if (o instanceof String) {
            type = STRING;
        } else if (o instanceof Amf0Object) {
            type = OBJECT;
        } else if (o instanceof Map) {
            type = MAP;
        } else if (o instanceof Object[]) {
            type = ARRAY;
        } else {
            throw new RuntimeException("unexpected type: " + o.getClass());
        }
    }

    private static String decodeString(ChannelBuffer in) {
        short size = in.readShort();
        byte[] bytes = new byte[size];
        in.readBytes(bytes);
        return new String(bytes); // TODO UTF-8 ?
    }

    private static void encodeString(ChannelBuffer out, String value) {        
        byte[] bytes = value.getBytes(); // TODO UTF-8 ?
        out.writeShort((short) bytes.length);
        out.writeBytes(bytes);
    }

    public static void encode(ChannelBuffer out, Object... values) {
        for (Object o : values) {
            new Amf0Value(o).encode(out);
        }
    }

    public static Object decodeValue(ChannelBuffer in) {
        return new Amf0Value(in).value;
    }

    private Amf0Value(ChannelBuffer in) {
        decode(in);        
    }

    private void decode(ChannelBuffer in) {
        type = Type.parseByte(in.readByte());
        switch (type) {
            case NUMBER:
                value = Double.longBitsToDouble(in.readLong());
                break;
            case BOOLEAN:
                value = (in.readByte() == 0x01) ? true : false;
                break;
            case STRING:
                value = decodeString(in);
                break;
            case NULL:
                break;
            case ARRAY:
                int arraySize = in.readInt();                
                Object[] array = new Object[arraySize];
                for (int i = 0; i < arraySize; i++) {
                    array[i] = decodeValue(in);
                }
                value = array;
                break;
            case MAP:                
                in.readInt(); // will always be 0
                // no break; remaining processing same as OBJECT
            case OBJECT:                
                final Map<String, Object> map;
                if(type == MAP) {
                    map = new LinkedHashMap<String, Object>();
                } else {
                    map = new Amf0Object();
                }
                byte[] endMarker = new byte[3];
                while (true) {
                    in.getBytes(in.readerIndex(), endMarker);
                    if (Arrays.equals(endMarker, OBJECT_END_MARKER)) {                        
                        in.skipBytes(3);
                        break;
                    }
                    map.put(decodeString(in), decodeValue(in));
                }
                value = map;
                break;
            case DATE:
                value = new Date((long) Double.longBitsToDouble(in.readLong())); // TODO UTC offset
                in.readShort(); // consume the timezone
                break;
            case LONG_STRING:
                int stringSize = in.readInt();
                byte[] bytes = new byte[stringSize];
                in.readBytes(bytes);
                value = new String(bytes); // TODO UTF-8 ?
                break;
            case UNDEFINED:
            case UNSUPPORTED:
                break;
            default:
                throw new RuntimeException("unexpected type: " + type);
        }
        if(logger.isDebugEnabled()) {
            logger.debug("<< " + toString());
        }
    }

    public void encode(ChannelBuffer out) {
        if(logger.isDebugEnabled()) {
            logger.debug(">> " + toString());
        }
        out.writeByte(type.value);
        switch (type) {
            case NUMBER:
                out.writeLong(Double.doubleToLongBits((Double) value));
                break;
            case BOOLEAN:
                final int bool = (Boolean) value ? 0x01 : 0x00;
                out.writeByte((byte) bool);
                break;
            case STRING:
                encodeString(out, (String) value);
                break;
            case NULL:
                break;
            case MAP:                
                out.writeInt(0);
                // no break; remaining processing same as OBJECT
            case OBJECT:                
                final Map<String, Object> map = (Map) value;
                for(Map.Entry<String, Object> entry : map.entrySet()) {
                    encodeString(out, entry.getKey());
                    encode(out, entry.getValue());
                }
                out.writeBytes(OBJECT_END_MARKER);
                break;
            case ARRAY:                
                final Object[] array = (Object[]) value;
                out.writeInt(array.length);
                for(Object o : array) {
                    encode(out, o);
                }
                break;
            default:
                // ignoring other types client doesn't require for now
                throw new RuntimeException("unexpected type: " + type);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(type).append(" ");
        if(type == ARRAY) {
            sb.append(Arrays.toString((Object[]) value));
        } else {
            sb.append(value);
        }
        sb.append(']');
        return sb.toString();
    }
    
}
