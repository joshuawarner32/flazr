package com.flazr.rtmp.client;

import com.flazr.util.Utils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientMain {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientMain.class);

    private static final ByteArrayEntity EMPTY_REQUEST = new ByteArrayEntity(new byte[] { 0 });

    public static void main(String[] args) throws Exception {
        HttpHost host = new HttpHost("openmeetings.de", 8088);
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        HttpPost openPost = new HttpPost("/open/1");
        addHeaders(openPost);
        openPost.setEntity(EMPTY_REQUEST);
        HttpResponse httpResponse = client.execute(host, openPost);
        HttpEntity responseEntity = httpResponse.getEntity();
        logger.info("content type: {}", responseEntity.getContentType());
        String response = Utils.streamToString(responseEntity.getContent());
        logger.info("response: {}", response);
    }

    private static void addHeaders(HttpPost post) {
        post.addHeader("Connection", "Keep-Alive");
        post.addHeader("Cache-Control", "no-cache");
    }

}
