// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** PHY110 MMListService returns an aligned String8 followed by a 32-bit found flag. */
final class SoundAttributeReply {
    private SoundAttributeReply() {}

    static String decode(byte[] bytes) throws IOException {
        if (bytes.length < 12) throw new IOException("Truncated MSS attribute reply");
        ByteBuffer reply = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int length = reply.getInt();
        // XML attributes are non-negative Java ints. Validate size before doing offset arithmetic.
        if (length < 0 || length > 10) throw new IOException("Invalid MSS attribute length");
        int flagOffset = 4 + ((length + 1 + 3) & ~3);
        if (bytes.length != flagOffset + 4 || bytes[4 + length] != 0) {
            throw new IOException("Invalid MSS attribute frame");
        }
        int found = reply.getInt(flagOffset);
        if (found == 0 && length == 0) return null;
        if (found != 1 || length == 0) throw new IOException("Invalid MSS attribute presence");
        for (int i = 4; i < 4 + length; i++) {
            if (bytes[i] < '0' || bytes[i] > '9') throw new IOException("Invalid MSS attribute digit");
        }
        String value = new String(bytes, 4, length, StandardCharsets.US_ASCII);
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("MSS attribute out of range", e);
        }
        return value;
    }
}
