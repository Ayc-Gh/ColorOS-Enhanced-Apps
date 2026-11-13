// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** This entry deliberately never installs anything in com.aiunit.aon. */
public final class AirGestureEntry extends XposedModule {
    private static final String TAG="ColorOSEnhance-Gesture";
    private final AtomicBoolean installed=new AtomicBoolean();
    private final Set<String> reported=ConcurrentHashMap.newKeySet();
    private SharedPreferences prefs;
    private final AdapterRuntime runtime=new AdapterRuntime(AirGesturePolicy.PACKAGE);
    private boolean requested(){try{return prefs!=null&&prefs.getBoolean(Config.AIR_GESTURE,false);}catch(Throwable ignored){return false;}}
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{};
    private boolean enabled(){return runtime.ready()&&requested();}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if(!AirGesturePolicy.PACKAGE.equals(param.getPackageName())||!installed.compareAndSet(false,true))return;
        runtime.arm(this,this::requested,data->data.put("gesture",new org.json.JSONObject().put("enabled",enabled()).put("nativeEngine",true)));
        if(!runtime.check(this))return;
        try {
            prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener(listener);
            ClassLoader loader=param.getClassLoader();
            Class<?> manager=Class.forName("com.oplus.content.OplusFeatureConfigManager",false,loader);
            Method feature=manager.getDeclaredMethod("hasFeature",String.class);
            if(feature.getReturnType()!=boolean.class)throw new NoSuchMethodException("Unexpected feature signature");
            hook(feature).intercept(chain->{
                Object nativeValue=chain.proceed();
                String name=(String)chain.getArg(0);
                boolean expose=AirGesturePolicy.expose(enabled(),param.getPackageName(),name);
                if(name!=null&&name.startsWith("oplus.software.aon_")&&reported.size()<32&&reported.add(name+":"+expose))
                    log(Log.INFO,TAG,"feature="+name+" native="+nativeValue+" expose="+expose);
                if(expose)runtime.hit();return expose?Boolean.TRUE:nativeValue;
            });
            // Cached UI decisions are recomputed by restarting only the OEM components.
            // Deoptimize the verified call sites; do not replace their native implementation.
            for(String owner:new String[]{"lb.i","com.oplus.gesture.util.GestureUtil"}) {
                try {
                    Class<?> type=Class.forName(owner,false,loader);
                    for(Method method:type.getDeclaredMethods()) {
                        String name=method.getName();
                        if(owner.equals("lb.i")||Set.of("hasfeature","isSupportAonSpaceGesture").contains(name))deoptimize(method);
                    }
                } catch(Throwable t){log(Log.WARN,TAG,"Optional call-site optimization unavailable: "+owner,t);}
            }
            runtime.path();runtime.loaded();
            log(Log.INFO,TAG,"Installed UI feature hook v"+BuildConfig.VERSION_NAME+" enabled="+enabled()+"; AONService unchanged");
        } catch(Throwable t){runtime.failed("手势入口校验失败，已保留原生识别与设置");log(Log.ERROR,TAG,"Gesture controls unavailable; keep native behavior",t);}
    }
}
