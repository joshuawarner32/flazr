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

public class RtmpProtocol {

  private int flags;

  public static final int RTMP = 0;
  public static final int RTMPE = 1;
  public static final int HTTP = 2;
  public static final int SSL = 4;

  public static final int RTMPS_NON_NATIVE = RTMP | HTTP | SSL;
  public static final int RTMPS_NATIVE = RTMP | SSL;

  public RtmpProtocol(int flags) {
    // TODO: verify flags
    this.flags = flags;
  }

  public boolean useHttp() {
    return (flags & HTTP) != 0;
  }

  public boolean useSsl() {
    return (flags & SSL) != 0;
  }

  public boolean useRtmpe() {
    return (flags & RTMPE) != 0;
  }

}