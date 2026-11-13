// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;

public class AirGesturePolicyTest {
    private static final String[] UI={"oplus.software.aon_gestureui_side_enable","oplus.software.aon_gestureui_enable","oplus.software.aon_gesture_press","oplus.software.aon_gesture_control_enable"};
    @Test public void exposesOemControlsInGestureHost(){for(String name:UI)assertTrue(name,AirGesturePolicy.expose(true,"com.oplus.gesture",name));}
    @Test public void disabledRestoresEveryNativeDecision(){for(String name:UI)assertFalse(name,AirGesturePolicy.expose(false,"com.oplus.gesture",name));}
    @Test public void doesNotChangeAonServiceEvenForAllowedControl(){for(String name:UI)assertFalse(name,AirGesturePolicy.expose(true,"com.aiunit.aon",name));}
    @Test public void neverSelectsIncompatibleHardwareEngines(){for(String name:new String[]{"oplus.software.aon_sensorhub_enable","oplus.software.aon_explorer_enable"})assertFalse(name,AirGesturePolicy.expose(true,"com.oplus.gesture",name));}
    @Test public void preservesUnrelatedAndNativeCallFeatures(){for(String name:new String[]{"oplus.software.aon_phone_enable","oplus.software.aon_phone_mute","oplus.software.aon_enable","oplus.software.aon_gestureui_enable_extra","","unrelated"})assertFalse(name,AirGesturePolicy.expose(true,"com.oplus.gesture",name));}
    @Test public void rejectsUnknownHostAndNull(){assertFalse(AirGesturePolicy.expose(true,null,UI[0]));assertFalse(AirGesturePolicy.expose(true,"com.oplus.gesture.ui",UI[0]));assertFalse(AirGesturePolicy.expose(true,"com.oplus.gesture",null));}
}
