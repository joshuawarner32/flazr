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

public enum RtmpProtocol {

    RTMP,
    RTMPE,
    RTMPT,
    RTMPS_NATIVE,
    RTMPS_OVER_HTTPS;

    private static final int FLAG_RTMP = 0;
    private static final int FLAG_RTMPE = 1;
    private static final int FLAG_HTTP = 2;
    private static final int FLAG_SSL = 4;

    static {
        RTMP.flags = FLAG_RTMP;
        RTMPE.flags = FLAG_RTMPE;
        RTMPT.flags = FLAG_HTTP;
        RTMPS_NATIVE.flags = FLAG_SSL;
        RTMPS_OVER_HTTPS.flags = FLAG_HTTP | FLAG_SSL;
    }

    private int flags;

    public boolean useHttp() {
        return (flags & FLAG_HTTP) != 0;
    }

    public boolean useSsl() {
        return (flags & FLAG_SSL) != 0;
    }

    public boolean useRtmpe() {
        return (flags & FLAG_RTMPE) != 0;
    }

}