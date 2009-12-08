package com.flazr.io.flv;

import static org.junit.Assert.*;
import org.junit.Test;

public class AudioTagTest {

    @Test
    public void testParseByte() {
        byte byteValue = 0x2a;
        AudioTag tag = new AudioTag(byteValue);
        assertEquals(AudioTag.Format.MP3, tag.getFormat());
        assertEquals(AudioTag.SampleRate.KHZ_22, tag.getSampleRate());
        assertTrue(tag.isSampleSize16Bit());
        assertFalse(tag.isStereo());
    }

}
