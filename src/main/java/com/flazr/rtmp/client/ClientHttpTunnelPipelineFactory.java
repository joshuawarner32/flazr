package com.flazr.rtmp.client;

import com.flazr.rtmp.RtmpDecoder;
import com.flazr.rtmp.RtmpEncoder;
import javax.net.ssl.SSLEngine;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.ssl.SslHandler;

public class ClientHttpTunnelPipelineFactory implements ChannelPipelineFactory {
    
    private ClientOptions options;
    
    public ClientHttpTunnelPipelineFactory(ClientOptions options) {
        this.options = options;
    }
    
    @Override
    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();
        SSLEngine engine = DummySslContextFactory.getClientContext().createSSLEngine();
        engine.setUseClientMode(true);
        // pipeline.addLast("ssl", new SslHandler(engine));
        pipeline.addLast("httpcodec", new HttpClientCodec());
        pipeline.addLast("httpchunk", new HttpChunkAggregator(1048576));
        pipeline.addLast("httptunnel", new ClientHttpTunnelHandler(options)); 
        pipeline.addLast("handshaker", new ClientHandshakeHandler(options)); 
        pipeline.addLast("decoder", new RtmpDecoder());
        pipeline.addLast("encoder", new RtmpEncoder());
        pipeline.addLast("handler", new ClientHandler(options));
        return pipeline;
    }    
    
}
