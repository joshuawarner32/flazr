package org.redfire.screen;

import com.flazr.rtmp.*;
import com.flazr.rtmp.client.ClientHandshakeHandler;
import com.flazr.rtmp.client.ClientHttpTunnelHandler;
import com.flazr.rtmp.client.ClientOptions;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenClientPipelineFactory implements ChannelPipelineFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenClientPipelineFactory.class);

    private final ClientOptions options;
    private final ScreenShare screenShare;

    public ScreenClientPipelineFactory(final ClientOptions options, final ScreenShare screenShare) {
        this.options = options;
        this.screenShare = screenShare;
    }

    @Override
    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();
        ProtocolType protocol = options.getProtocol();
        if (protocol == ProtocolType.RTMPS) {
            logger.info("{} requested, initializing SSL", protocol);
//        SSLEngine engine = DummySslContextFactory.getClientContext().createSSLEngine();
//        engine.setUseClientMode(true);
//        pipeline.addLast("ssl", new SslHandler(engine));
        }
        if (protocol == ProtocolType.RTMPS || protocol == ProtocolType.RTMPT) {
            logger.info("{} requested, initializing http tunnel", protocol);
            pipeline.addLast("httpcodec", new HttpClientCodec());
            pipeline.addLast("httpchunk", new HttpChunkAggregator(1048576));
            pipeline.addLast("httptunnel", new ClientHttpTunnelHandler(options));             
        }
        pipeline.addLast("handshaker", new ClientHandshakeHandler(options));
        pipeline.addLast("decoder", new RtmpDecoder());
        pipeline.addLast("encoder", new RtmpEncoder());
        pipeline.addLast("handler", new ScreenClientHandler(options, screenShare));
        return pipeline;
    }

}
