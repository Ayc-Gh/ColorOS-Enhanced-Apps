// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Expose OEM gesture controls without changing any recognition-engine feature. */
public final class AirGesturePolicy {
    public static final String PACKAGE="com.oplus.gesture";
    private AirGesturePolicy() {}
    public static boolean expose(boolean enabled,String packageName,String feature) {
        if(!enabled||!PACKAGE.equals(packageName)||feature==null)return false;
        return switch(feature) {
            case "oplus.software.aon_gestureui_side_enable",
                 "oplus.software.aon_gestureui_enable",
                 "oplus.software.aon_gesture_press",
                 "oplus.software.aon_gesture_control_enable" -> true;
            default -> false;
        };
    }
}
