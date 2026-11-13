package com.op.aod.enhance.data

import org.junit.Assert.*
import org.junit.Test

class AodDefaultsTest {
    private fun assertNative(config:AodConfig) {
        assertTrue(config.useSystemInitDark)
        assertTrue(config.useSystemInitBright)
        assertTrue(config.useSystemRunningMultiplier)
        assertFalse(config.enablePanoramic)
        assertFalse(config.enableSettingsSupport)
        assertFalse(config.blockSingleClick)
        assertFalse(config.blockLowLightHide)
        assertEquals(AodConfigContract.DURATION_MODE_SYSTEM,config.aodDurationMode)
    }
    @Test fun freshInstallAndRuntimeFallbackUseNativeBehavior(){assertNative(AodConfig());assertNative(AodConfigCodec.decode(emptyMap<String,Any>()))}
    @Test fun malformedSwitchesDoNotAccidentallyEnableEnhancements(){assertNative(AodConfigCodec.decode(mapOf(AodConfigContract.KEY_ENABLE_PANORAMIC to "true",AodConfigContract.KEY_USE_SYSTEM_INIT_DARK to 0)))}
    @Test fun upgradePreservesExplicitExistingChoices(){
        val chosen=AodConfig(useSystemInitDark=false,initDark=95,enablePanoramic=true,enableSettingsSupport=true,aodDurationMode=2)
        assertEquals(chosen,AodConfigCodec.decode(AodConfigCodec.encode(chosen)))
    }
    @Test fun resetDoesNotReuseThePreviousBrightnessOverride(){val previous=AodConfig(useSystemInitDark=false,initDark=120,enablePanoramic=true);assertNotEquals(previous,AodConfig());assertNative(AodConfigCodec.decode(AodConfigCodec.encode(AodConfig())))}
}
