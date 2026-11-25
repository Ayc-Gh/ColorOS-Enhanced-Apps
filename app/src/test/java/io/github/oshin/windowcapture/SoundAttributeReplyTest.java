// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class SoundAttributeReplyTest {
    private static byte[] words(int... words) {
        ByteBuffer bytes = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : words) bytes.putInt(word);
        return bytes.array();
    }

    @Test public void decodesCapturedPhy110Replies() throws IOException {
        assertEquals("3", SoundAttributeReply.decode(words(1, 0x33, 1)));
        assertEquals("17", SoundAttributeReply.decode(words(2, 0x3731, 1)));
        assertNull(SoundAttributeReply.decode(words(0, 0, 0)));
        assertEquals("1234", SoundAttributeReply.decode(words(4, 0x34333231, 0, 1)));
    }

    @Test public void malformedRepliesAreNeverReportedAsAbsent() {
        for (byte[] invalid : new byte[][] {
            words(2, 0x3731), words(2, 0x3731, 0), words(0, 0, 1),
            words(1, 0x33, 2), words(2, 0x7831, 1), words(2, 0x013731, 1),
            words(1, 0x33, 1, 0), words(Integer.MAX_VALUE, 0, 0),
            words(10, 0x39393939, 0x39393939, 0x3939, 1)
        }) assertThrows(IOException.class, () -> SoundAttributeReply.decode(invalid));
    }
}
