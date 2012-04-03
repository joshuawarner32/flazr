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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.RtmpWriter;

public class ServerScope {

    private final ServerScope parent;

    private final String name;

    private final Map<String, ServerStream> streams = new ConcurrentHashMap<String, ServerStream>();

    private final Map<String, ServerScope> childScopes = new ConcurrentHashMap<String, ServerScope>();

    private final ServerScopeFactory childFactory;

    public ServerScope(ServerScope parent, String name) {
        this(parent, name, null);
    }

    public ServerScope(ServerScope parent, String name, ServerScopeFactory childFactory) {
        this.parent = parent;
        if(parent != null) {
            parent.childScopes.put(name, this);
        }
        this.name = name;
        this.childFactory = childFactory;
    }

    public String getName() {
        return name;
    }

    public ServerScope getChild(String name) {
        ServerScope ret = childScopes.get(name);
        if(ret == null) {
            if(childFactory != null) {
                childScopes.put(name, ret = childFactory.makeChild(this, name));
            } else {
                throw new ScopePermissionException("scope " + this + " doesn't allow child scopes");
            }
        }
        return ret;
    }

    public ServerScope getChildRecursive(String slashDelimitedName) {
        ServerScope scope = this;
        for(String name : slashDelimitedName.split("/")) {
            scope = scope.getChild(name);
            if(scope == null) {
                return null;
            }
        }
        return scope;
    }

    public ServerStream getStream(final String name) {        
        return getStream(name, null);
    }

    public ServerStream getStream(final String name, final String type) {
        ServerStream stream = streams.get(name);
        if(stream == null) {
            stream = new ServerStream(name, type);
            streams.put(name, stream);
        }
        return stream;
    }

    public RtmpReader getReader(final String name) {
        if(parent != null) {
            return parent.getReader(this.name + "/" + name);
        }
        return null;
    }

    public RtmpWriter getWriter(final String name) {
        if(parent != null) {
            return parent.getWriter(this.name + "/" + name);
        }
        return null;
    }

    @Override
    public String toString() {
        return "[name: '" + name + "' streams: " + streams + "]";
    }

}