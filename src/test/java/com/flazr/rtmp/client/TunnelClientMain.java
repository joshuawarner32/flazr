package com.flazr.rtmp.client;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelClientMain {

    private static final Logger logger = LoggerFactory.getLogger(TunnelClientMain.class);

    public static void main(String[] args) {

        String host = "localhost";
        int port = 5080;

        ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        ClientOptions options = new ClientOptions(host, "vod", "red5", "red5.flv");
        options.setPort(port);
        bootstrap.setPipelineFactory(new ClientHttpTunnelPipelineFactory(options));
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        Channel channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            logger.error("connection failed", future.getCause());
            bootstrap.releaseExternalResources();
            return;
        }
        channel.getCloseFuture().awaitUninterruptibly();
        bootstrap.releaseExternalResources();

    }

}
