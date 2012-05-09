package com.flazr.rtmp.client;

import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHttpTunnelHandler extends SimpleChannelUpstreamHandler implements ChannelDownstreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientHttpTunnelHandler.class);

    private String host;
    private boolean opened = false;
    private boolean polling = false;
    private String clientId;
    private int requestId;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    private ChannelHandlerContext ctx;

    public ClientHttpTunnelHandler(String host) {
        this.host = host;
    }

    private static final ChannelBuffer LINE_FEED = ChannelBuffers.wrappedBuffer(new byte[]{'\n'});
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private HttpRequest post(String uri, ChannelBuffer content) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
        request.setHeader(HttpHeaders.Names.USER_AGENT, "Shockwave Flash");
        request.setHeader(HttpHeaders.Names.HOST, host);
        request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        request.setHeader(HttpHeaders.Names.CACHE_CONTROL, HttpHeaders.Values.NO_CACHE);
        request.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/x-fcs");
        request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        request.setContent(content);
        return request;
    }

    @Override
    public void channelConnected(final ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        this.ctx = ctx;
        logger.info("http channel connected, sending tunnel open request");
        HttpRequest open = post("/open/1", LINE_FEED);
        Channels.write(ctx, e.getFuture(), open);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!(e.getMessage() instanceof HttpResponse)) { // TODO get rid of this
            logger.warn("this message should not be here: {}", e);
            super.messageReceived(ctx, e);
            return;
        }
        HttpResponse response = (HttpResponse) e.getMessage();
        if (!opened) {
            String content = response.getContent().toString(UTF_8);
            clientId = content.trim();
            logger.info("http tunnel opened successfully, client id: {}", clientId);
            opened = true;
            Channels.fireChannelConnected(ctx, ctx.getChannel().getRemoteAddress());
            return;
        }
        ChannelBuffer in = response.getContent();
        byte firstByte = in.readByte();
        logger.info("firstByte: {}", firstByte);
        if (in.readable()) {
            Channels.fireMessageReceived(ctx, in);
        }
        if (!polling) {
            logger.info("received first server message, starting http polling: {}", e);
            poller.scheduleWithFixedDelay(new PollingTask(), 500, 500, TimeUnit.MILLISECONDS);
            polling = true;
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("channel closed, shutting down http poller: {}", e);
        poller.shutdown();
        super.channelClosed(ctx, e);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent ce) {
        if (ce instanceof MessageEvent) {
            final ChannelBuffer in = (ChannelBuffer) ((MessageEvent) ce).getMessage();
            HttpRequest send = post("/send/" + clientId + "/" + requestId, in);
            requestId++;
            Channels.write(ctx, ce.getFuture(), send);
        } else {
            logger.info("sending downstream: {}", ce);
            ctx.sendDownstream(ce);
        }
    }

    class PollingTask implements Runnable {

        @Override
        public void run() {
            logger.info("polling task");
            HttpRequest send = post("/idle/" + clientId + "/" + requestId, LINE_FEED);
            requestId++;
            Channels.write(ctx, Channels.future(ctx.getChannel()), send);
        }

    }

}
