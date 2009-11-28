package com.flazr.io.flv;

import static org.junit.Assert.*;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.message.MessageType;
import org.junit.Test;

public class FlvReaderTest {

    @Test
    public void testRandomAccessOfMetadataAtom() {
        FlvReader reader = new FlvReader("home/apps/vod/sample.flv");
        RtmpMessage message = reader.getMetadata();
        assertEquals(message.getHeader().getMessageType(), MessageType.METADATA_AMF0);
        reader.close();
    }

    @Test
    public void testReadBackwards() {
        FlvReader reader = new FlvReader("home/apps/vod/sample.flv");
        RtmpMessage m1 = reader.next();        
        assertEquals(m1.encode(), reader.prev().encode());
        assertFalse(reader.hasPrev()); // we are at beginning again
        reader.next();
        RtmpMessage m2  = reader.next();
        assertEquals(m2.encode(), reader.prev().encode());
        assertTrue(reader.hasPrev());
        reader.close();
    }

}
