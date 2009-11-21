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


import com.flazr.util.ValueToEnum;
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

    public static enum Type implements ValueToEnum.IntValue {

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

        private final int value;

        private Type(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }

        private static final ValueToEnum<Type> converter = new ValueToEnum<Type>(Type.values());

        public static Type valueToEnum(final int value) {
            return converter.valueToEnum(value);
        }

    }

    private static final byte[] OBJECT_END_MARKER = new byte[]{0x00, 0x00, 0x09};
    
    private final Type type;
    private final Object value;

    public Object getValue() {
        return value;
    }

    public Amf0Value(final Object o) {        
        if (o == null) {
            type = NULL;
        } else if (o instanceof Number) {
            type = NUMBER;
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
        if(type == NUMBER && !(o instanceof Double)) {
            value = Double.parseDouble(o.toString()); // converts int also
        } else {
            value = o;
        }
    }

    private static String decodeString(final ChannelBuffer in) {
        final short size = in.readShort();
        final byte[] bytes = new byte[size];
        in.readBytes(bytes);
        return new String(bytes); // TODO UTF-8 ?
    }

    private static void encodeString(final ChannelBuffer out, final String value) {
        final byte[] bytes = value.getBytes(); // TODO UTF-8 ?
        out.writeShort((short) bytes.length);
        out.writeBytes(bytes);
    }

    public static void encode(final ChannelBuffer out, final Object... values) {
        for (final Object o : values) {
            new Amf0Value(o).encode(out);
        }
    }

    public static Object decodeValue(final ChannelBuffer in) {
        return new Amf0Value(in).value;
    }

    private Amf0Value(final ChannelBuffer in) {
        type = Type.valueToEnum(in.readByte());
        value = decode(in, type);
        if(logger.isDebugEnabled()) {
            logger.debug("<< " + toString());
        }
    }

    private Object decode(final ChannelBuffer in, final Type type) {
        switch (type) {
            case NUMBER: return Double.longBitsToDouble(in.readLong());
            case BOOLEAN: return (in.readByte() == 0x01) ? true : false;
            case STRING: return decodeString(in);
            case ARRAY:
                final int arraySize = in.readInt();
                final Object[] array = new Object[arraySize];
                for (int i = 0; i < arraySize; i++) {
                    array[i] = decodeValue(in);
                }
                return array;
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
                final byte[] endMarker = new byte[3];
                while (true) {
                    in.getBytes(in.readerIndex(), endMarker);
                    if (Arrays.equals(endMarker, OBJECT_END_MARKER)) {
                        in.skipBytes(3);
                        break;
                    }
                    map.put(decodeString(in), decodeValue(in));
                }
                return map;
            case DATE:
                final long dateValue = in.readLong();
                in.readShort(); // consume the timezone
                return new Date((long) Double.longBitsToDouble(dateValue));
            case LONG_STRING:
                final int stringSize = in.readInt();
                final byte[] bytes = new byte[stringSize];
                in.readBytes(bytes);
                return new String(bytes); // TODO UTF-8 ?
            case NULL:
            case UNDEFINED:
            case UNSUPPORTED:
                return null;
            default:
                throw new RuntimeException("unexpected type: " + type);
        }
    }

    public void encode(final ChannelBuffer out) {
        if(logger.isDebugEnabled()) {
            logger.debug(">> " + toString());
        }
        out.writeByte((byte) type.value);
        switch (type) {
            case NUMBER:
                out.writeLong(Double.doubleToLongBits((Double) value));
                return;
            case BOOLEAN:
                final int bool = (Boolean) value ? 0x01 : 0x00;
                out.writeByte((byte) bool);
                return;
            case STRING:
                encodeString(out, (String) value);
                return;
            case NULL:
                return;
            case MAP:                
                out.writeInt(0);
                // no break; remaining processing same as OBJECT
            case OBJECT:                
                final Map<String, Object> map = (Map) value;
                for(final Map.Entry<String, Object> entry : map.entrySet()) {
                    encodeString(out, entry.getKey());
                    encode(out, entry.getValue());
                }
                out.writeBytes(OBJECT_END_MARKER);
                return;
            case ARRAY:                
                final Object[] array = (Object[]) value;
                out.writeInt(array.length);
                for(Object o : array) {
                    encode(out, o);
                }
                return;
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
