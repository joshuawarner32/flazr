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

package com.flazr.rtmp.message;

import com.flazr.amf.Amf0Object;
import com.flazr.rtmp.RtmpHeader;
import com.flazr.rtmp.client.RtmpClientSession;
import java.util.Arrays;
import java.util.Map;
import org.jboss.netty.buffer.ChannelBuffer;

public abstract class Command extends AbstractMessage {
    
    protected String name;
    protected int transactionId;
    protected Amf0Object object;
    protected Object[] args;

    public Command(RtmpHeader header, ChannelBuffer in) {
        super(header, in);
    }
    
    public Command(int transactionId, String name, Amf0Object object, Object ... args) {
        this.transactionId = transactionId;
        this.name = name;
        this.object = object;
        this.args = args;
    }

    public Command(String name, Amf0Object object, Object ... args) {
        this(0, name, object, args);
    }

    public Amf0Object getObject() {
        return object;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getArgCount() {
        if(args == null) {
            return 0;
        }
        return args.length;
    }

    //==========================================================================

    public static Command connect(RtmpClientSession session) {
        Amf0Object object = object(
            pair("app", session.getAppName()),
            pair("flashVer", "WIN 9,0,124,2"),
            pair("tcUrl", session.getTcUrl()),
            pair("fpad", false),
            pair("audioCodecs", 1639.0),
            pair("videoCodecs", 252.0),
            pair("objectEncoding", 0.0),
            pair("capabilities", 15.0),
            pair("videoFunction", 1.0));
        if(session.getParams() != null) {
            object.putAll(session.getParams());
        }
        return new CommandAmf0("connect", object, session.getArgs());
    }

    public static Command connectSuccess(int transactionId) {
        Amf0Object object = object(
            pair("fmsVer", "FMS/3,5,1,516"),
            pair("capabilities", 31.0),
            pair("mode", 1.0));
        Map<String, Object> arg = status(
            "NetConnection.Connect.Success",
            "Connection succeeded.",
            pair("objectEncoding", 0.0),
            pair("data", map(
                pair("version", "3,5,1,516"))));
        return new CommandAmf0(transactionId, "_result", object, arg);
    }

    public static Command createStream() {
        return new CommandAmf0("createStream", null);
    }

    public static Command onBWDone() {
        return new CommandAmf0("onBWDone", null);
    }

    public static Command createStreamSuccess(int transactionId, int streamId) {
        return new CommandAmf0(transactionId, "_result", null, streamId);
    }

    public static Command play(int streamId, RtmpClientSession session) {
        Command command = new CommandAmf0("play", null,
            session.getPlayName(), session.getPlayStart(), session.getPlayDuration());
        command.header.setChannelId(8);
        command.header.setStreamId(streamId);        
        return command;
    }

    private static Command playStatus(String code, String description, String playName, String clientId, Pair ... pairs) {
        Amf0Object status = status("NetStream.Play." + code, description + " " + playName + ".",
                pair("details", playName),
                pair("clientid", clientId));
        object(status, pairs);
        Command command = new CommandAmf0("onStatus", null, status);
        command.header.setChannelId(5);
        return command;
    }

    public static Command playReset(String playName, String clientId) {
        Command command = playStatus("Reset", "Playing and resetting", playName, clientId);
        command.header.setChannelId(4); // ?
        return command;
    }

    public static Command playStart(String playName, String clientId) {
        Command play = playStatus("Start", "Started playing", playName, clientId);
        return play;
    }

    public static Command playStop(String playName, String clientId) {
        return playStatus("Stop", "Stopped playing", playName, clientId);
    }

    public static Command playFailed(String playName, String clientId) {
        return playStatus("Failed", "Stream not found:", playName, clientId);
    }

    public static Command seekNotify(int streamId, int seekTime, String playName, String clientId) {
        Amf0Object status = status(
                "NetStream.Seek.Notify",
                "Seeking " + seekTime + " (stream ID: " + streamId + ").",
                pair("details", playName),
                pair("clientid", clientId));        
        Command command = new CommandAmf0("onStatus", null, status);
        command.header.setChannelId(5);
        command.header.setStreamId(streamId);
        command.header.setTime(seekTime);
        return command;
    }

    public static Command pauseNotify(String playName, String clientId) {
        Amf0Object status = status(
                "NetStream.Pause.Notify",
                "Pausing " + playName,
                pair("details", playName),
                pair("clientid", clientId));
        Command command = new CommandAmf0("onStatus", null, status);
        command.header.setChannelId(5);
        return command;
    }

    public static Command unpauseNotify(String playName, String clientId) {
        Amf0Object status = status(
                "NetStream.Unpause.Notify",
                "Unpausing " + playName,
                pair("details", playName),
                pair("clientid", clientId));
        Command command = new CommandAmf0("onStatus", null, status);
        command.header.setChannelId(5);
        return command;
    }
    
    public static Command publish(int streamId, RtmpClientSession session) { // TODO
        Command command = new CommandAmf0("publish", null, session.getPlayName(), session.getType().getPublishType());
        command.header.setChannelId(8);
        command.header.setStreamId(streamId);
        return command;
    }

    public static Command closeStream() {
        return new CommandAmf0("closeStream", null);
    }

    //==========================================================================

    public String getName() {
        return name;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());        
        sb.append("name: ").append(name);
        sb.append(", transactionId: ").append(transactionId);
        sb.append(", object: ").append(object);
        sb.append(", args: ").append(Arrays.toString(args));
        return sb.toString();
    }

}
