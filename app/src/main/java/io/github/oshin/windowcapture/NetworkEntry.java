// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI-only adapter for TrafficMonitor 17.3.5. Native policy writes are untouched. */
public final class NetworkEntry extends XposedModule {
    private final AtomicBoolean installed=new AtomicBoolean();
    private final ThreadLocal<Integer> screenDepth=ThreadLocal.withInitial(()->0);
    private SharedPreferences prefs;
    private String processName;
    private int registered;
    private volatile int expanded;
    private final AdapterRuntime runtime=new AdapterRuntime(NetworkPolicy.HOST);
    private boolean requested(){try{return prefs!=null&&prefs.getBoolean(NetworkPolicy.KEY,false);}catch(Throwable ignored){return false;}}
    private boolean enabled(){return runtime.ready()&&requested();}
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if(k==null||NetworkPolicy.KEY.equals(k))expanded=0;};
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param){processName=param.getProcessName();}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){
        if(!NetworkPolicy.HOST.equals(param.getPackageName())||!NetworkPolicy.HOST.equals(processName)||!installed.compareAndSet(false,true))return;
        runtime.arm(this,this::requested,data->data.put("network",new org.json.JSONObject().put("enabled",enabled()).put("expanded",expanded).put("nativePolicy",true)));
        if(!runtime.check(this))return;
        try{
            prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener(listener);
            ClassLoader loader=param.getClassLoader();
            Class<?> details=Class.forName("com.oplus.trafficmonitor.view.datausagelist.appdatausage.OplusAppDataUsage",false,loader);
            Class<?> model=Class.forName("com.oplus.trafficmonitor.model.NetworkControlDataModel",false,loader);
            Class<?> utils=Class.forName("com.android.settings.datausage.DataUsageUtils",false,loader);
            Class<?> common=Class.forName("G3.k",false,loader);
            // Validate the two obfuscated methods against this ROM's constant data.
            boolean ota=false,gms=false;
            for(java.lang.reflect.Field field:common.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(field.getType()==String[].class||java.util.List.class.isAssignableFrom(field.getType()))){
                field.setAccessible(true);Object value=field.get(null);
                if(value instanceof java.util.List<?> list&&list.equals(java.util.List.of("com.oplus.ota")))ota=true;
                if(value instanceof String[] names&&java.util.Arrays.equals(names,new String[]{"com.google.android.gms","com.google.android.gsf","com.android.vending"}))gms=true;
            }
            if(!ota||!gms)throw new IllegalStateException("Unrecognized TrafficMonitor adapter");
            wrap(details.getDeclaredMethod("onCreate",Bundle.class));
            wrap(details.getDeclaredMethod("g0",int.class));
            for(Method m:model.getDeclaredMethods()){
                Class<?>[] p=m.getParameterTypes();
                if(java.util.Map.class.isAssignableFrom(m.getReturnType())&&(p.length==7||p.length==8)&&p[0]==android.content.pm.PackageInfo.class&&p[1]==Context.class&&p[2]==PackageManager.class)wrap(m);
            }
            if(registered!=4)throw new IllegalStateException("Incomplete TrafficMonitor callers");
            Method dataApp=utils.getDeclaredMethod("isDataApp",Context.class,int.class);
            hook(dataApp).intercept(chain->{
                Object nativeValue=chain.proceed();if(Boolean.TRUE.equals(nativeValue)||!enabled()||screenDepth.get()==0)return nativeValue;
                Context context=(Context)chain.getArg(0);int uid=(Integer)chain.getArg(1);boolean internet=false;
                String[] packages=context.getPackageManager().getPackagesForUid(uid);
                if(packages!=null)for(String pkg:packages)if(context.getPackageManager().checkPermission("android.permission.INTERNET",pkg)==PackageManager.PERMISSION_GRANTED){internet=true;break;}
                if(NetworkPolicy.eligible(true,uid,internet)){expanded=1;runtime.hit();return true;}return nativeValue;
            });
            for(String name:new String[]{"T","U"}){
                Method method=common.getDeclaredMethod(name,String.class);
                if(method.getReturnType()!=boolean.class)throw new IllegalStateException("Unexpected option classifier");
                hook(method).intercept(chain->enabled()?false:chain.proceed());
            }
            // No device-policy lists, INTERNET checks, UID identities or firewall setters are replaced.
            Class<?> adapter=Class.forName("com.oplus.trafficmonitor.view.networkcontrol.a",false,loader);
            for(Method method:adapter.getDeclaredMethods())deoptimize(method);
            for(Method method:common.getDeclaredMethods())if(method.getName().equals("V")||method.getName().equals("W"))deoptimize(method);
            runtime.loaded();
            log(Log.INFO,"ColorOSEnhance-Network","Installed v"+BuildConfig.VERSION_NAME+"; native networking policies retained");
        }catch(Throwable error){runtime.failed("联网选项接口不完整，已保留原生行为");prefs=null;log(Log.ERROR,"ColorOSEnhance-Network","Adapter unavailable; keep native behavior",error);}
    }
    private void wrap(Method method){
        method.setAccessible(true);deoptimize(method);
        hook(method).intercept(chain->{int depth=screenDepth.get();screenDepth.set(depth+1);try{return chain.proceed();}finally{if(depth==0)screenDepth.remove();else screenDepth.set(depth);}});registered++;runtime.path();
    }
}
