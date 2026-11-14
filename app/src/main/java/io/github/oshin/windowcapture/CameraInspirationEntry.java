// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

/** Restores the tested cloud inspiration recommendation entry in Camera 7.006.191. */
public final class CameraInspirationEntry extends XposedModule {
    private final AdapterRuntime runtime=new AdapterRuntime(CameraInspirationPolicy.HOST);
    private volatile SharedPreferences prefs;
    private String processName;
    private boolean installed;
    private volatile boolean expanded;
    private boolean requested(){try{return prefs!=null&&prefs.getBoolean(CameraInspirationPolicy.KEY,false);}catch(Throwable ignored){return false;}}
    private boolean enabled(){return runtime.ready()&&requested();}
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam p){processName=p.getProcessName();}
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam p){install(p.getPackageName(),p.getDefaultClassLoader());}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam p){install(p.getPackageName(),p.getClassLoader());}
    private synchronized void install(String pkg,ClassLoader loader){
        if(installed||!CameraInspirationPolicy.HOST.equals(pkg)||!CameraInspirationPolicy.HOST.equals(processName))return;
        try{
            Class<?> config=Class.forName("com.oplus.camera.configure.CameraConfig",false,loader),aps=Class.forName("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsUtils",false,loader),configure=Class.forName("com.oplus.ocs.camera.configure.CameraConfigure",false,loader),table=Class.forName("com.oplus.ocs.camera.configure.ProtobufFeatureConfig$FeatureTable",false,loader);
            Method gate=config.getDeclaredMethod("w",String.class,boolean.class),vendor=aps.getDeclaredMethod("getVendorTagConfig",String.class),registry=configure.getDeclaredMethod("initFeatureTable",table);
            // The OEM bridge is obfuscated and its vendor-tag return type changed
            // between Camera minor releases; the method signatures above are the
            // stable calls used by the already tested temporary adapter.
            installed=true;
            runtime.arm(this,this::requested,data->data.put("camera",new org.json.JSONObject().put("enabled",enabled()).put("expanded",enabled()&&expanded)));
            if(!runtime.check(this))return;
            prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener((p,key)->{if(key==null||CameraInspirationPolicy.KEY.equals(key))expanded=false;});
            hook(gate).intercept(c->{String key=(String)c.getArg(0);if(enabled()&&key.equals("com.oplus.feature.aicomposition.inspiration.support")){expanded=true;runtime.hit();return true;}if(enabled()&&key.equals("com.oplus.feature.aicomposition.realscene.support"))return false;return c.proceed();});runtime.path();
            hook(vendor).intercept(c->{String key=(String)c.getArg(0);if(enabled()&&key.equals("com.oplus.feature.aicomposition.inspiration.support")){expanded=true;runtime.hit();return "1";}if(enabled()&&key.equals("com.oplus.feature.aicomposition.realscene.support"))return "0";return c.proceed();});runtime.path();
            hook(registry).intercept(c->{if(!enabled())return c.proceed();try{Object original=c.getArg(0),patched=CameraInspirationRegistryPatch.apply(original,loader);expanded|=patched!=original;runtime.hit();return c.proceed(new Object[]{patched});}catch(Throwable error){log(Log.WARN,"ColorOSEnhance-Camera","Inspiration registry unavailable; preserving native table",error);return c.proceed();}});runtime.path();
            runtime.loaded();log(Log.INFO,"ColorOSEnhance-Camera","Cloud inspiration recommendation adapter loaded; local composition excluded");
        }catch(Throwable error){runtime.failed("相机灵感推荐接口不可用，已保留原生行为");log(Log.ERROR,"ColorOSEnhance-Camera","Camera inspiration adapter unavailable",error);}
    }
}
