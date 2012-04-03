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

package com.flazr.rtmp.server;

import com.flazr.io.f4v.F4vReader;
import com.flazr.io.flv.FlvReader;
import com.flazr.io.flv.FlvWriter;
import com.flazr.rtmp.RtmpConfig;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.RtmpWriter;
import com.flazr.util.Utils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerApplication extends ServerScope {

    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    public ServerApplication(ServerScope parent, String name) {
        this(parent, name, new ServerScopeFactory() {
            public ServerScope makeChild(ServerScope scope, String name) {
                return new ServerScope(scope, name);
            }
        });
    }

    public ServerApplication(ServerScope parent, String name, ServerScopeFactory childFactory) {
        super(parent, name, childFactory);
    }

    @Override
    public RtmpReader getReader(final String rawName) {
        final String streamName = Utils.trimSlashes(rawName);
        final String path = RtmpConfig.SERVER_HOME_DIR + "/apps/" + getName() + "/";
        final String readerPlayName;
        try {
            if(streamName.startsWith("mp4:")) {
                readerPlayName = streamName.substring(4);
                return new F4vReader(path + readerPlayName);
            } else {                
                if(streamName.lastIndexOf('.') < streamName.length() - 4) {
                    readerPlayName = streamName + ".flv";
                } else {
                    readerPlayName = streamName;
                }
                return new FlvReader(path + readerPlayName);
            }
        } catch(Exception e) {
            logger.info("reader creation failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public RtmpWriter getWriter(final String rawName) {
        final String streamName = Utils.trimSlashes(rawName);
        final String path = RtmpConfig.SERVER_HOME_DIR + "/apps/" + getName() + "/";
        return new FlvWriter(path + streamName + ".flv");
    }

    private static String cleanName(final String raw) {
        return Utils.trimSlashes(raw).toLowerCase();
    }

}
