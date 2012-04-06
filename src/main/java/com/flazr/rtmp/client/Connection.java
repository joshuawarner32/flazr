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

package com.flazr.rtmp.client;

import java.util.Map;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.RtmpWriter;

import com.flazr.rtmp.PublishType;

public interface Connection {

    // TODO: either / or:
    // * break this method out into a higher-level class, so we guarantee that it's called exactly once per connection.
    // * make sure connecting twice has sensical, defined semantics.
    public void connect(String scopeName, String tcUrl, Map<String, Object> params, Object[] args, ResultHandler handler);

    // TODO: return an object that can be used to manipulate the publish stream
    public void publish(String streamName, RtmpReader reader, PublishType publishType, StreamHandler handler);

    // TODO: return an object that can be used to manipulate the play stream
    public void play(String streamName, RtmpWriter writer, long start, long length, StreamHandler handler);

}