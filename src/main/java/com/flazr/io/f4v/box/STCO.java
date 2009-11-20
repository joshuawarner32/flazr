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

package com.flazr.io.f4v.box;

import com.flazr.io.f4v.*;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class STCO implements Payload {

    private static final Logger logger = LoggerFactory.getLogger(STCO.class);
    private List<Integer> offsets;

    public STCO(ChannelBuffer in) {
        read(in);
    }

    public void setOffsets(List<Integer> offsets) {
        this.offsets = offsets;
    }

    public List<Integer> getOffsets() {
        return offsets;
    }

    @Override
    public void read(ChannelBuffer in) {
        in.readInt(); // UI8 version + UI24 flags
        final int count = in.readInt();
        logger.debug("no of chunk offsets: {}", count);
        offsets = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) {
            final Integer offset = in.readInt();
            // logger.debug("#{} offset: {}", new Object[]{i, offset});
            offsets.add(offset);
        }
    }

    @Override
    public ChannelBuffer write() {
        ChannelBuffer out = ChannelBuffers.dynamicBuffer();
        out.writeInt(0); // UI8 version + UI24 flags        
        out.writeInt(offsets.size());
        for (Integer offset : offsets) {
            out.writeInt(offset);
        }
        return out;
    }
    
}
