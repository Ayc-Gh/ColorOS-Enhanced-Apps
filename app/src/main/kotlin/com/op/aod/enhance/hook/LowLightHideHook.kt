package com.op.aod.enhance.hook

internal object LowLightHideHook {
    private const val SENSOR_CALLBACK="com.oplus.systemui.aod.aodclock.off.AodUpdateManager\$2"
    fun HookRuntime.hookLowLightAodHide(){
        val method=findMethod(SENSOR_CALLBACK,"hideAodByDarkLight",Int::class.javaPrimitiveType!!)
        check(method.returnType==Void.TYPE){"Unexpected dark-light callback signature"}
        intercept("aod.lowlight.hide-dark",method){chain->
            if(AodConfigReader.read(MainHook.hostAppContext).blockLowLightHide)null else chain.proceed()
        }
        // General special-rule decisions also enforce the cumulative day quota.
        // Leave those methods and their mutable state entirely native.
    }
}
