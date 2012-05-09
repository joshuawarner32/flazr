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

import com.flazr.rtmp.*;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientPipelineFactory implements ChannelPipelineFactory {

    private static final Logger logger = LoggerFactory.getLogger(ClientPipelineFactory.class);

    private final ClientLogic logic;
    private final String host;
    private final int bufferSize;
    private RtmpProtocol protocol;
    private SwfData swfData;
    private byte[] clientVersionToUse;

    public ClientPipelineFactory(ClientLogic logic,String host, int bufferSize, RtmpProtocol protocol, SwfData swfData, byte[] clientVersionToUse) {
        this.logic = logic;
        this.host = host;
        this.bufferSize = bufferSize;
        this.protocol = protocol;
        this.swfData = swfData;
        this.clientVersionToUse = clientVersionToUse;
    }

    @Override
    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        if(protocol.useSsl()) {
            // TODO: add SSL encoder / decoder, to support RTMPS (in it's two forms: http proxied and native)
            throw new UnsupportedOperationException("SSL is not currently supported");
//        SSLEngine engine = DummySslContextFactory.getClientContext().createSSLEngine();
//        engine.setUseClientMode(true);
//        pipeline.addLast("ssl", new SslHandler(engine));
        }
        if(protocol.useHttp()) {
            logger.info("{} requested, initializing http tunnel", protocol);
            pipeline.addLast("httpcodec", new HttpClientCodec());
            pipeline.addLast("httpchunk", new HttpChunkAggregator(1048576));
            pipeline.addLast("httptunnel", new ClientHttpTunnelHandler(host));
        }
        pipeline.addLast("handshaker", new ClientHandshakeHandler(protocol.useRtmpe(), swfData, clientVersionToUse));
        pipeline.addLast("decoder", new RtmpDecoder());
        pipeline.addLast("encoder", new RtmpEncoder());
//        if(options.getLoad() == 1) {
//            pipeline.addLast("executor", new ExecutionHandler(
//                    new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576)));
//        }
        pipeline.addLast("handler", new ClientHandler(logic, bufferSize));
        return pipeline;
    }

}
