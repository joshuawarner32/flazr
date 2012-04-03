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

import com.flazr.rtmp.message.Metadata;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimitedReader implements RtmpReader {
    private static final Logger logger = LoggerFactory.getLogger(LoopedReader.class);

    private RtmpReader reader;
    private long start;
    private long length;

    public LimitedReader(RtmpReader reader, long start, long length) {
      this.reader = reader;
      this.start = reader.seek(start);
      this.length = length;
    }

    public Metadata getMetadata() {
      return reader.getMetadata();
    }

    public RtmpMessage[] getStartMessages() {
      return reader.getStartMessages();
    }

    public void setAggregateDuration(int targetDuration) {
      reader.setAggregateDuration(targetDuration);
    }

    public long getTimePosition() {
      return reader.getTimePosition() - start;
    }

    public long seek(long timePosition) {
      if(timePosition <= 0) {
        reader.seek(start);
        return 0;
      } else if(timePosition >= length) {
        reader.seek(start + length);
        return length;
      } else {
        return reader.seek(timePosition + start) - start;
      }
    }

    public void close() {
      reader.close();
    }

    public boolean hasNext() {
      if(getTimePosition() >= length) {
        return false;
      } else {
        return reader.hasNext();
      }
    }

    public RtmpMessage next() {
      return reader.next();
    }
}