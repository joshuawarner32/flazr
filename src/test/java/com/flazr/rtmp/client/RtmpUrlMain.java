package com.flazr.rtmp.client;

public class RtmpUrlMain {

    public static void main(String[] args) {
        ClientOptions co = new ClientOptions();
        co.parseCli(new String[]{"rtmp://localhost/vod/sample.flv", "sample.flv"});
        RtmpClient.connect(co);
    }

}
