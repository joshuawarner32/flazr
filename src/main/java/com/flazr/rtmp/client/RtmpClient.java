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

import com.flazr.io.flv.FlvReader;
import com.flazr.util.Utils;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public class RtmpClient {

    public static void main(String[] args) {        
        ClientSession session = new ClientSession("localhost", "live", "cameraFeed", null);
        session.setReader(new FlvReader("home/apps/vod/sample.flv"));
        session.setType(ClientSession.Type.PUBLISH_LIVE);
        connect(session);
    }

    public static void connect(ClientSession session) {
        Utils.outputCopyrightNotice();
        ChannelFactory factory = new NioClientSocketChannelFactory (
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        bootstrap.setPipelineFactory(new ClientPipelineFactory(session));
        bootstrap.setOption("tcpNoDelay" , true);
        bootstrap.setOption("keepAlive", true);
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(session.getHost(), session.getPort()));
        future.awaitUninterruptibly();
        if(!future.isSuccess()) {
            future.getCause().printStackTrace();
        }
        future.getChannel().getCloseFuture().awaitUninterruptibly();
        factory.releaseExternalResources();
    }

}
