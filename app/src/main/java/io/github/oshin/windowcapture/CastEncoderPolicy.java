// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.util.function.IntPredicate;
final class CastEncoderPolicy {
    static int ceiling(IntPredicate actualSizeSupported){for(int fps:new int[]{120,90,60})if(actualSizeSupported.test(fps))return fps;return 0;}
    private CastEncoderPolicy(){}
}
