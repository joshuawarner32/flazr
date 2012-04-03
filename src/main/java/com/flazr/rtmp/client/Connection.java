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

import com.flazr.rtmp.server.ServerStream.PublishType;

public interface Connection {

    public void connectToScope(String scopeName, String tcUrl, Map<String, Object> params, Object[] args, ResultHandler handler);

    public void createStream(ResultHandler handler);

    public void publish(int streamId, String streamName, PublishType publishType, int bufferSize, RtmpReader reader, ResultHandler handler);

    public void play(int streamId, String streamName, int start, int length, ResultHandler handler);

    public void message(RtmpMessage message);

}