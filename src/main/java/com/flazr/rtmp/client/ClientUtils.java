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

import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.LimitedReader;
import com.flazr.rtmp.LoopedReader;
import com.flazr.rtmp.PublishType;
import com.flazr.rtmp.RtmpPublisher;

public class ClientUtils {

    public static void beginPublish(final Connection conn, final RtmpReader reader, final String streamName, final PublishType publishType, final int bufferSize) {
        conn.createStream(new ResultHandler() {
            public void handleResult(Object streamId) {
                int id = ((Double) streamId).intValue();
                conn.publish(id, streamName, publishType, bufferSize, reader,
                    new ResultHandler() {
                        public void handleResult(Object ignored) {
                            // TODO: tell the client that the publish completed successfully
                        }
                    });
            }
        });
    }

}