package com.flazr.rtmp.client;

public class TestRtmpUrlMain {
    
    public static void main(String[] args) {
        ClientOptions co = new ClientOptions();
        co.parseCli(new String[]{"rtmpt://localhost:5080/vod/red5.flv"});
        RtmpClient.connect(co);
    }    
    
}
