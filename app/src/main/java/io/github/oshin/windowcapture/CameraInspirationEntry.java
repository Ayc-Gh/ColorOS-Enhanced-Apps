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
    private volatile boolean logExpanded;
    private final CameraLogSession logSession=new CameraLogSession();
    private boolean value(String key){try{return prefs!=null&&prefs.getBoolean(key,false);}catch(Throwable ignored){return false;}}
    private boolean requested(){return value(CameraInspirationPolicy.KEY)||value(Config.CAMERA_LOG);}
    private boolean enabled(){return runtime.ready()&&value(CameraInspirationPolicy.KEY);}
    private boolean logEnabled(){return runtime.ready()&&value(Config.CAMERA_LOG);}
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam p){processName=p.getProcessName();}
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam p){install(p.getPackageName(),p.getDefaultClassLoader());}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam p){install(p.getPackageName(),p.getClassLoader());}
    private synchronized void install(String pkg,ClassLoader loader){
        if(installed||!CameraInspirationPolicy.HOST.equals(pkg)||!CameraInspirationPolicy.HOST.equals(processName))return;
        try{
            Class<?> config=Class.forName("com.oplus.camera.configure.CameraConfig",false,loader),aps=Class.forName("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsUtils",false,loader),configure=Class.forName("com.oplus.ocs.camera.configure.CameraConfigure",false,loader),table=Class.forName("com.oplus.ocs.camera.configure.ProtobufFeatureConfig$FeatureTable",false,loader);
            Method gate=config.getDeclaredMethod("w",String.class,boolean.class),vendor=aps.getDeclaredMethod("getVendorTagConfig",String.class),registry=configure.getDeclaredMethod("initFeatureTable",table);
            if(gate.getReturnType()!=String.class||vendor.getReturnType()!=String.class)throw new NoSuchMethodException("Camera configuration return type changed");
            // The OEM bridge is obfuscated and its vendor-tag return type changed
            // between Camera minor releases; the method signatures above are the
            // stable calls used by the already tested temporary adapter.
            installed=true;
            runtime.arm(this,this::requested,data->data.put("camera",new org.json.JSONObject().put("enabled",enabled()).put("expanded",enabled()&&expanded).put("logEnabled",logEnabled()).put("logExpanded",logExpanded)));
            if(!runtime.check(this))return;
            prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener((p,key)->{if(key==null||CameraInspirationPolicy.KEY.equals(key))expanded=false;if((key==null||Config.CAMERA_LOG.equals(key))&&!value(Config.CAMERA_LOG)){logSession.clear();new android.os.Handler(android.os.Looper.getMainLooper()).post(()->restoreLogSelection(loader));}});
            // A restart is already required for the feature table. Keep the hot
            // data repository completely native in processes started with Log off.
            if(value(Config.CAMERA_LOG)){
            Class<?> repository=Class.forName("xa.e",false,loader);
            Method read=repository.getDeclaredMethod("a",android.net.Uri.class,Class.class,Object.class),write=repository.getDeclaredMethod("d",android.net.Uri.class,Class.class,Object.class,boolean.class);
            hook(read).intercept(c->{Object original=c.proceed();android.net.Uri uri=(android.net.Uri)c.getArg(0);String key=uri.getFragment();return logExpanded&&CameraLogSession.targets(key)?logSession.read(uri.toString(),key,original,logEnabled()):original;});runtime.path();
            hook(write).intercept(c->{android.net.Uri uri=(android.net.Uri)c.getArg(0);String key=uri.getFragment();if(!logExpanded||!CameraLogSession.targets(key))return c.proceed();Object[] args=c.getArgs().toArray();args[2]=logSession.write(uri.toString(),key,args[2],logEnabled());return c.proceed(args);});runtime.path();
            }
            hook(gate).intercept(c->{String key=(String)c.getArg(0);if(enabled()&&key.equals("com.oplus.feature.aicomposition.inspiration.support")){expanded=true;runtime.hit();return "1";}if(enabled()&&key.equals("com.oplus.feature.aicomposition.realscene.support"))return "0";return c.proceed();});runtime.path();
            hook(vendor).intercept(c->{String key=(String)c.getArg(0);if(enabled()&&key.equals("com.oplus.feature.aicomposition.inspiration.support")){expanded=true;runtime.hit();return "1";}if(enabled()&&key.equals("com.oplus.feature.aicomposition.realscene.support"))return "0";return c.proceed();});runtime.path();
            hook(registry).intercept(c->{Object original=c.getArg(0),patched=original;if(enabled())try{Object next=CameraInspirationRegistryPatch.apply(patched,loader);expanded|=next!=patched;patched=next;runtime.hit();}catch(Throwable error){log(Log.WARN,"ColorOSEnhance-Camera","Inspiration registry unavailable; preserving native rows",error);}if(logEnabled())try{Object next=CameraLogRegistryPatch.apply(patched,loader);logExpanded|=next!=patched;patched=next;runtime.hit();}catch(Throwable error){log(Log.WARN,"ColorOSEnhance-Camera","Log registry unavailable; preserving native rows",error);}return patched==original?c.proceed():c.proceed(new Object[]{patched});});runtime.path();
            runtime.loaded();log(Log.INFO,"ColorOSEnhance-Camera","Cloud recommendation and native Log registry adapter loaded");
        }catch(Throwable error){runtime.failed("相机灵感推荐接口不可用，已保留原生行为");log(Log.ERROR,"ColorOSEnhance-Camera","Camera inspiration adapter unavailable",error);}
    }
    private void restoreLogSelection(ClassLoader loader){if(!logExpanded)return;try{
        Class<?> manager=Class.forName("com.oplus.camera.data.DataManager",true,loader),keyClass=Class.forName("com.oplus.camera.data.DataKey",false,loader),keys=Class.forName("za.a",true,loader);Object instance=manager.getMethod("getInstance").invoke(null);
        for(String[] item:new String[][]{{CameraLogSession.FORMAT,"sdr"},{CameraLogSession.ENABLE,"off"}}){Object key=keys.getDeclaredMethod("b",String.class).invoke(null,item[0]);if(key!=null)manager.getDeclaredMethod("g",keyClass,Object.class).invoke(instance,key,item[1]);}
    }catch(Throwable error){log(Log.WARN,"ColorOSEnhance-Camera","Camera selection reset deferred to process restart",error);}}
}
