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
import com.flazr.rtmp.server.ServerStream;
import com.flazr.rtmp.server.ServerStream.PublishType;
import com.flazr.util.Utils;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientOptions {

    private static final Logger logger = LoggerFactory.getLogger(ClientOptions.class);

    private ServerStream.PublishType publishType;
    private String host = "localhost";
    private int port = 1935;
    private String appName = "vod";
    private String streamName;
    private String fileToPublish;
    private String saveAs;    
    private boolean rtmpe;
    private Map<String, Object> params;
    private Object[] args;
    private byte[] clientVersionToUse;
    private int start = -1;
    private int length = -2;
    private byte[] swfHash;
    private int swfSize;

    protected ClientOptions() {}

    public ClientOptions(String host, String appName, String streamName, String saveAs) {
        this(host, 1935, appName, streamName, saveAs, false, null);
    }

    public ClientOptions(String host, int port, String appName, String streamName, String saveAs,
            boolean rtmpe, String swfFile) {
        this.host = host;
        this.port = port;
        this.appName = appName;
        this.streamName = streamName;
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

    public ClientOptions(String url, String saveAs) {
        this(url);
        this.saveAs = saveAs;
    }

    public ClientOptions(String url) {
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
        streamName = matcher.group(5);
        logger.debug("playName = '{}'", streamName);
        rtmpe = protocol.equalsIgnoreCase("rtmpe");
        if(rtmpe) {
            logger.debug("rtmpe requested, will use encryption");
        }        
    }

    public void setFileToPublish(String fileName) {
        this.fileToPublish = fileName;
    }
    
    public void publishLive() {
        publishType = ServerStream.PublishType.LIVE;        
    }
    
    public void publishRecord() {
        publishType = ServerStream.PublishType.RECORD;        
    }
    
    public void publishAppend() {
        publishType = ServerStream.PublishType.APPEND;        
    }

    //==========================================================================
    
    protected static Options getCliOptions() {
        final Options options = new Options();
        options.addOption(new Option("help", "print this message"));
        options.addOption(OptionBuilder.withArgName("host").hasArg()
                .withDescription("host name").create("host"));
        options.addOption(OptionBuilder.withArgName("port").hasArg().withType(Integer.class)
                .withDescription("port number").create("port"));
        options.addOption(OptionBuilder.withArgName("app").hasArg()
                .withDescription("app name").create("app"));
        options.addOption(OptionBuilder
                .withArgName("start").hasArg().withType(Integer.class)
                .withDescription("start position (milliseconds)").create("start"));
        options.addOption(OptionBuilder.withArgName("length").hasArg().withType(Integer.class)
                .withDescription("length (milliseconds)").create("length"));
        options.addOption(new Option("rtmpe", "use RTMPE (encryption)"));
        options.addOption(new Option("live", "publish local file to server in 'live' mode"));
        options.addOption(new Option("record", "publish local file to server in 'record' mode"));
        options.addOption(new Option("append", "publish local file to server in 'live' mode"));
        options.addOption(OptionBuilder.withArgName("property=value").hasArgs(2)
                .withValueSeparator().withDescription("add / override connection param").create("D"));
        options.addOption(OptionBuilder.withArgName("swf").hasArg()
                .withDescription("path to (decompressed) SWF for verification").create("swf"));
        return options;
    }

    public boolean parseCli(final String[] args) {
        CommandLineParser parser = new GnuParser();
        CommandLine line = null;
        final Options options = getCliOptions();
        try {
            line = parser.parse(options, args);
            if(line.hasOption("help") || line.getArgs().length == 0) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("client [options] streamName [saveAs | fileToPublish]", options);
                return false;
            }
            if(line.hasOption("host")) {
                host = line.getOptionValue("host");
            }
            if(line.hasOption("port")) {
                port = (Integer) line.getParsedOptionValue("port");
            }  
            if(line.hasOption("app")) {
                appName = line.getOptionValue("app");
            }
            if(line.hasOption("start")) {
                start = (Integer) line.getParsedOptionValue("start");
            }
            if(line.hasOption("length")) {
                length = (Integer) line.getParsedOptionValue("length");
            }
            if(line.hasOption("rtmpe")) {
                rtmpe = true;
            }
            if(line.hasOption("live")) {
                publishLive();
            }
            if(line.hasOption("record")) {
                publishRecord();
            }
            if(line.hasOption("append")) {
                publishAppend();
            }
        } catch(Exception e) {
            System.err.println("parsing failed: " + e.getMessage());
            return false;
        }
        String[] actualArgs = line.getArgs();
        streamName = actualArgs[0];
        if(publishType != null) {
            if(actualArgs.length < 2) {
                System.err.println("fileToPublish is required for publish mode");
                return false;
            }
            fileToPublish = actualArgs[1];
        } else if(actualArgs.length > 1) {
            saveAs = actualArgs[1];
        }
        logger.info("options: {}", this);
        return true;
    }

    //==========================================================================

    public String getFileToPublish() {
        return fileToPublish;
    }

    public String getAppName() {
        return appName;
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

    public PublishType getPublishType() {
        return publishType;
    }

    public void setPublishType(PublishType publishType) {
        this.publishType = publishType;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getDuration() {
        return length;
    }

    public void setDuration(int duration) {
        this.length = duration;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[host: ").append(host);
        sb.append(" port: ").append(port);
        sb.append(" appName: ").append(appName);
        sb.append(" streamName: ").append(streamName);
        sb.append(" saveAs: ").append(saveAs);
        sb.append(" rtmpe: ").append(rtmpe);
        sb.append(" publish: ").append(publishType);
        if(clientVersionToUse != null) {
            sb.append(" clientVersionToUse: ").append(Utils.toHex(clientVersionToUse));
        }
        sb.append(" start: ").append(start);
        sb.append(" length: ").append(length);
        sb.append(" params: ").append(params);
        sb.append(" args: ").append(Arrays.toString(args));
        if(swfHash != null) {
            sb.append(" swfHash: ").append(Utils.toHex(swfHash));
            sb.append(" swfSize: ").append(swfSize);
        }
        sb.append(']');
        return sb.toString();
    }
    
}
