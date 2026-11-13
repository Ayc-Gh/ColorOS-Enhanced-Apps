package com.op.aod.enhance.hook

import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method

internal object PanoramicHook {
    // Basic panoramic/panel support is native on PHY110 and is never rewritten.
    private val state=NativeBooleanOverride(listOf("isSupportPanoramicAllDay","isSupportPanoramicAllDayByPanelFeature"))
    private const val CONTROLLER="com.oplus.systemui.aod.display.SmoothTransitionController"
    private val handler=Handler(Looper.getMainLooper())
    private var publisher:Method?=null
    private val refresh=Runnable { state.instances().forEach { instance -> reconcile(instance) } }

    private fun supportsPanoramic(instance:Any)=runCatching {
        instance.javaClass.getDeclaredField("isSupportPanoramic").apply{isAccessible=true}.getBoolean(instance)
    }.getOrDefault(false)
    private fun enabled(instance:Any)=AodConfigReader.read(MainHook.hostAppContext).enablePanoramic&&supportsPanoramic(instance)
    private fun reconcile(instance:Any){
        runCatching {
            val effective=state.apply(instance,enabled(instance))?:return
            publisher?.invoke(instance,effective)
            val native=state.nativeValue(instance)==true
            HostDiagnostics.panoramic=HostDiagnostics.PanoramicSample(native,effective,!native&&effective)
            AodLog.i("PANORAMIC_APPLY","native=$native effective=$effective enhanced=${!native&&effective}")
        }.onFailure { AodLog.e("PANORAMIC_APPLY","Capability refresh failed",it) }
    }

    fun HookRuntime.hookPanoramicAllDaySupport(){
        val clazz=runCatching{findClass(CONTROLLER)}.getOrElse{failure("全景控制器不可用");return}
        publisher=runCatching{findMethod(clazz,"setPanoramicSupportAllDayForApplication",Boolean::class.javaPrimitiveType!!)}
            .onFailure{failure("全天能力发布入口不可用")}.getOrNull()
        publisher?.let { publish ->
            intercept("aod.panoramic.publish-all-day",publish){chain->
                val instance=chain.getThisObject()
                if(instance==null||!enabled(instance))return@intercept chain.proceed()
                val args=chain.getArgs().toTypedArray();args[0]=true;chain.proceed(args)
            }
        }
        for(name in arrayOf("initSmoothTransitionState","setPanoramicSupportedByRemote")){
            runCatching {
                val method=findMethod(clazz,name)
                intercept("aod.panoramic.$name",method){chain->
                    val instance=chain.getThisObject()?:return@intercept chain.proceed()
                    state.beforeNative(instance)
                    try{chain.proceed()}finally{
                        val count=state.afterNative(instance,enabled(instance))
                        if(count==0)HostDiagnostics.registry.failed("Panoramic.AllDay","未找到适用的全天能力字段")
                        reconcile(instance)
                    }
                }
            }.onFailure{failure("$name 入口不可用");AodLog.e("HOOK_REGISTER_DETAIL","Panoramic $name unavailable",it)}
        }
        AodConfigReader.observe {
            handler.removeCallbacks(refresh);handler.postDelayed(refresh,150)
        }
    }
}
