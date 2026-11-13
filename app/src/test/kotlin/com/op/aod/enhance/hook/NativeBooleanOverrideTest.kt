package com.op.aod.enhance.hook

import org.junit.Assert.*
import org.junit.Test

class NativeBooleanOverrideTest {
    private class Capabilities(@JvmField var allDay:Boolean=false,@JvmField var panelAllDay:Boolean=false,@JvmField var basic:Boolean=true)
    private fun store()=NativeBooleanOverride(listOf("allDay","panelAllDay"))
    @Test fun disablingRestoresBothNativeValues(){val target=Capabilities();val state=store();assertEquals(2,state.afterNative(target,true));assertTrue(target.allDay);assertTrue(target.panelAllDay);state.apply(target,false);assertFalse(target.allDay);assertFalse(target.panelAllDay);assertTrue(target.basic)}
    @Test fun nativeSupportSurvivesTurningEnhancementOff(){val target=Capabilities(true,true);val state=store();state.afterNative(target,true);state.apply(target,false);assertTrue(target.allDay);assertTrue(target.panelAllDay)}
    @Test fun repeatedTogglesDoNotReplaceTheNativeBaseline(){val target=Capabilities();val state=store();state.afterNative(target,true);repeat(5){state.apply(target,false);assertFalse(target.allDay);state.apply(target,true);assertTrue(target.allDay)};state.apply(target,false);assertFalse(target.allDay)}
    @Test fun nativeReinitialisationSeesNativeValues(){val target=Capabilities();val state=store();state.afterNative(target,true);state.beforeNative(target);assertFalse(target.allDay);target.allDay=true;state.afterNative(target,true);state.apply(target,false);assertTrue(target.allDay);assertFalse(target.panelAllDay)}
    @Test fun separateInstancesKeepSeparateNativeCapabilities(){val a=Capabilities();val b=Capabilities(true,true);val state=store();state.afterNative(a,true);state.afterNative(b,true);state.apply(a,false);state.apply(b,false);assertFalse(a.allDay);assertTrue(b.allDay)}
}
