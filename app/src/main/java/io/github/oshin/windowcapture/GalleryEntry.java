// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Gallery 17.10.66: restore the AI retouch capability, preserving its native workflow. */
public final class GalleryEntry extends XposedModule {
    private final AdapterRuntime runtime=new AdapterRuntime(GalleryPolicy.HOST);
    private volatile SharedPreferences prefs;
    private String processName;
    private boolean installed;
    private volatile Boolean nativeSupport;
    private volatile boolean expanded;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if(key==null||GalleryPolicy.KEY.equals(key))expanded=false;};
    private boolean requested(){try{return prefs!=null&&prefs.getBoolean(GalleryPolicy.KEY,false);}catch(Throwable ignored){return false;}}
    private boolean enabled(){return runtime.ready()&&requested();}
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param){processName=param.getProcessName();}
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param){install(param.getPackageName(),param.getDefaultClassLoader());}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){install(param.getPackageName(),param.getClassLoader());}
    private synchronized void install(String pkg,ClassLoader loader){
        if(installed||!GalleryPolicy.HOST.equals(pkg)||!GalleryPolicy.HOST.equals(processName))return;
        Method getter,delegate,support,menu;
        try{
            Class<?> config=Class.forName("com.oplus.aiunit.vision.qab",false,loader);
            getter=config.getDeclaredMethod("b",String.class,boolean.class,boolean.class);
            delegate=config.getDeclaredMethod("c",int.class,String.class,boolean.class);
            support=Class.forName("com.oplus.aiunit.vision.sd2",false,loader).getDeclaredMethod("b");
            menu=Class.forName("com.oplus.gallery.photoeditor.editingvvm.menu.MenuVM",false,loader).getDeclaredMethod("u",List.class);
            if(getter.getReturnType()!=boolean.class||!Modifier.isStatic(getter.getModifiers())||
                    delegate.getReturnType()!=boolean.class||support.getReturnType()!=boolean.class||menu.getReturnType()!=void.class)
                throw new NoSuchMethodException("Unexpected Gallery interface");
        }catch(ReflectiveOperationException error){return;}
        installed=true;
        runtime.arm(this,this::requested,data->{
            var gallery=new org.json.JSONObject().put("enabled",enabled()).put("expanded",enabled()&&expanded);
            if(nativeSupport!=null)gallery.put("nativeSupport",nativeSupport.booleanValue());
            data.put("gallery",gallery);
        });
        if(!runtime.check(this))return;
        try{
            prefs=getRemotePreferences(Config.PREFS);
            prefs.registerOnSharedPreferenceChangeListener(listener);
            hook(getter).intercept(chain->{
                Object value=chain.proceed();
                String key=(String)chain.getArg(0);
                if(!GalleryPolicy.FEATURE.equals(key))return value;
                nativeSupport=Boolean.TRUE.equals(value);
                if(GalleryPolicy.unlock(enabled(),key,Boolean.TRUE.equals(value))){expanded=true;runtime.hit();return true;}
                return value;
            });
            runtime.path();
            // Compiled callers must read the same narrowly scoped gate.
            for(Method caller:new Method[]{delegate,support,menu}){deoptimize(caller);runtime.path();}
            runtime.loaded();
            log(Log.INFO,"ColorOSEnhance-Gallery","Installed v"+BuildConfig.VERSION_NAME+"; native retouch service and saving retained");
        }catch(Throwable error){prefs=null;runtime.failed("相册适配接口不可用，已保留原生行为");log(Log.ERROR,"ColorOSEnhance-Gallery","Gallery adapter unavailable",error);}
    }
}
