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

package com.flazr.util;

import java.util.Arrays;

/**
 * a little bit of code reuse, would have been cleaner if enum types
 * could extend some other class - we implement an interface instead
 * and have to construct a static instance in each enum type we use
 */
public class ByteToEnum<T extends Enum<T> & ByteToEnum.Convert> {

    public static interface Convert {
        byte byteValue();
    }

    private final Enum[] lookupArray;
    private final int maxIndex;

    public ByteToEnum(T[] enumValues) {
        final int[] lookupIndexes = new int[enumValues.length];
        for(int i = 0; i < enumValues.length; i++) {
            lookupIndexes[i] = enumValues[i].byteValue();
        }
        Arrays.sort(lookupIndexes);
        maxIndex = lookupIndexes[lookupIndexes.length - 1];        
        lookupArray = new Enum[maxIndex + 1]; // use 1 based index
        for (final T t : enumValues) {
            lookupArray[t.byteValue()] = t;
        }        
    }

    public T parseByte(final byte b) {
        final T t;
        try {
            t = (T) lookupArray[b];
        } catch(Exception e) { // index out of bounds
            throw new RuntimeException("bad byte: " + Utils.toHex(b) + " " + e);
        }
        if (t == null) {
            throw new RuntimeException("bad byte: " + Utils.toHex(b));
        }
        return t;
    }

    public int getMaxIndex() {
        return maxIndex;
    }

}
