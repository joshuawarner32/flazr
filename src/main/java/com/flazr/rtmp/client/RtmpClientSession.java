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

import com.flazr.rtmp.RtmpHandshake;
import com.flazr.rtmp.RtmpMessageReader;
import com.flazr.util.Utils;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpClientSession {

    private static final Logger logger = LoggerFactory.getLogger(RtmpClientSession.class);

    public static enum Type {
        
        PLAY,
        PUBLISH_LIVE,
        PUBLISH_APPEND, 
        PUBLISH_RECORD;

        public boolean isPublish() {
            return this != PLAY;
        }

        public String getPublishType() {
            switch(this) {
                case PUBLISH_LIVE: return "live";
                case PUBLISH_APPEND: return "append";
                case PUBLISH_RECORD: return "record";
                default: return null;
            }
        }
    }

    private Type type = Type.PLAY;
    private String host;
    private int port;
    private String appName;
    private String playName; // TODO cleanup
    private RtmpMessageReader reader;
    private String saveAs;    
    private boolean rtmpe;
    private Map<String, Object> params;
    private Object[] args;
    private byte[] clientVersionToUse;
    private int playStart;
    private int playDuration = -2;    
    private byte[] swfHash;
    private int swfSize;

    private RtmpClientSession() {}

    public static void main(String[] args) {
        final RtmpClientSession session;
        switch(args.length) {
            case 0:
                logger.error("at least 1 argument required"); return;
            case 1:
                session = new RtmpClientSession(args[0]); break;
            case 2:
                session = new RtmpClientSession(args[0], args[1]); break;
            case 3:
                session = new RtmpClientSession(args[0], args[1], args[2], null); break;
            case 4:
                session = new RtmpClientSession(args[0], args[1], args[2], args[3]); break;
            case 5:
                session = new RtmpClientSession(args[0], Integer.parseInt(args[1]), args[2], args[3],
                        args[4], false, null); break;
            case 6:
                session = new RtmpClientSession(args[0], Integer.parseInt(args[1]), args[2], args[3],
                        args[4], Boolean.parseBoolean(args[5]), null); break;
            default:
                session = new RtmpClientSession(args[0], Integer.parseInt(args[1]), args[2], args[3],
                        args[4], Boolean.parseBoolean(args[5]), args[6]); break;
        }
        RtmpClient.connect(session);
    }

    public RtmpClientSession(String host, String app, String playName, String saveAs) {
        this(host, 1935, app, playName, saveAs, false, null);
    }

    public RtmpClientSession(String host, int port, String app, String playName, String saveAs, 
            boolean rtmpe, String swfFile) {
        this.host = host;
        this.port = port;
        this.appName = app;
        this.playName = playName;
        this.saveAs = saveAs;
        this.rtmpe = rtmpe;        
        if(swfFile != null) {
            initSwfVerification(swfFile);
        }
    }

    private static final Pattern URL_PATTERN = Pattern.compile(
          "(rtmp.?)://" // 1) protocol
        + "([^/:]+)(:[0-9]+)?/" // 2) host 3) port
        + "([^/]+)/" // 4) app
        + "(.*)" // 5) play
    );

    public RtmpClientSession(String url, String saveAs) {
        this(url);
        this.saveAs = saveAs;
    }

    public RtmpClientSession(String url) {                      
        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            throw new RuntimeException("invalid url: " + url);
        }
        logger.debug("parsing url: {}", url);
        String protocol = matcher.group(1);
        logger.debug("protocol = '{}'",  protocol);
        host = matcher.group(2);
        logger.debug("host = '{}'", host);
        String portString = matcher.group(3);
        if (portString == null) {
            logger.debug("port is null in url, will use default 1935");
        } else {
            portString = portString.substring(1); // skip the ':'
            logger.debug("port = '{}'", portString);
        }
        port = portString == null ? 1935 : Integer.parseInt(portString);
        appName = matcher.group(4);
        logger.debug("app = '{}'",  appName);
        playName = matcher.group(5);
        logger.debug("playName = '{}'", playName);        
        rtmpe = protocol.equalsIgnoreCase("rtmpe");
        if(rtmpe) {
            logger.debug("rtmpe requested, will use encryption");
        }        
    }

    public String getAppName() {
        return appName;
    }

    public RtmpMessageReader getReader() {
        return reader;
    }

    public void setReader(RtmpMessageReader reader) {
        this.reader = reader;
    }

    public String getTcUrl() {
        return (rtmpe ? "rtmpe://" : "rtmp://") + host + ":" + port + "/" + appName;
    }

    public void setArgs(Object ... args) {
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setClientVersionToUse(byte[] clientVersionToUse) {
        this.clientVersionToUse = clientVersionToUse;
    }

    public byte[] getClientVersionToUse() {
        return clientVersionToUse;
    }

    public void initSwfVerification(String pathToLocalSwfFile) {
        initSwfVerification(new File(pathToLocalSwfFile));
    }

    public void initSwfVerification(File localSwfFile) {
        logger.info("initializing swf verification data for: " + localSwfFile.getAbsolutePath());
        byte[] bytes = Utils.readAsByteArray(localSwfFile);
        byte[] hash = Utils.sha256(bytes, RtmpHandshake.CLIENT_CONST);
        swfSize = bytes.length;
        swfHash = hash;
        logger.info("swf verification initialized - size: {}, hash: {}", swfSize, Utils.toHex(swfHash));
    }
    
    public void putParam(String key, Object value) {
        if(params == null) {
            params = new LinkedHashMap<String, Object>();
        }
        params.put(key, value);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getPlayName() {
        return playName;
    }

    public void setPlayName(String playName) {
        this.playName = playName;
    }

    public int getPlayStart() {
        return playStart;
    }

    public void setPlayStart(int playStart) {
        this.playStart = playStart;
    }

    public int getPlayDuration() {
        return playDuration;
    }

    public void setPlayDuration(int playDuration) {
        this.playDuration = playDuration;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSaveAs() {
        return saveAs;
    }

    public void setSaveAs(String saveAs) {
        this.saveAs = saveAs;
    }

    public boolean isRtmpe() {
        return rtmpe;
    }

    public byte[] getSwfHash() {
        return swfHash;
    }

    public void setSwfHash(byte[] swfHash) {
        this.swfHash = swfHash;
    }

    public int getSwfSize() {
        return swfSize;
    }

    public void setSwfSize(int swfSize) {
        this.swfSize = swfSize;
    }
    
}
