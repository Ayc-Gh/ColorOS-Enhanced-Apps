// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Repairs missing metadata in memory; native switches and extraction remain authoritative. */
public final class AimemoryEntry extends XposedModule {
    private static final String TAG="ColorOSEnhance-Memory";
    private final AtomicBoolean installed=new AtomicBoolean();
    private final Object lock=new Object();
    private final ThreadLocal<Boolean> converting=new ThreadLocal<>();
    private final Set<Object> owned=Collections.newSetFromMap(new IdentityHashMap<>());
    private SharedPreferences prefs;
    private String processName;
    private Boolean cacheMode;
    private volatile int repaired,available;
    private volatile String adapter="loading";
    private volatile boolean allowed;
    private final AdapterRuntime runtime=new AdapterRuntime(AimemoryPolicy.HOST);
    private WechatOcrAdapter ocrAdapter;
    private volatile Application application;
    private Method refresh;
    private java.lang.reflect.Field switchSingleton;
    private Object switchManager;
    private final AimemoryTrace trace=new AimemoryTrace();
    private final ThreadLocal<Boolean> ocrScope=new ThreadLocal<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if(k==null||Config.AIMEMORY_WECHAT_BILL.equals(k)){if(ocrAdapter!=null)ocrAdapter.invalidate();refreshMonitoring();}};
    private boolean requested(){try{return prefs!=null&&prefs.getBoolean(Config.AIMEMORY_WECHAT_BILL,false);}catch(Throwable ignored){return false;}}
    private boolean enabled(){return runtime.ready()&&requested();}
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param){processName=param.getProcessName();}
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){
        if(!AimemoryPolicy.HOST.equals(param.getPackageName())||!AimemoryPolicy.HOST.equals(processName)||!installed.compareAndSet(false,true))return;
        runtime.arm(this,this::requested,data->{JSONObject memory=trace.json();data.put("memory",memory.put("enabled",enabled()).put("adapter",adapter).put("rules",available).put("repaired",repaired).put("nativeGate",allowed).put("revision",AimemoryPolicy.REVISION));});
        if(!runtime.check(this))return;
        try{
            prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener(listener);
            ClassLoader loader=param.getClassLoader();
            Class<?> entity=Class.forName("com.oplus.gleanerservice.config.repository.db.SceneRuleBeanEntity",false,loader);
            Class<?> converter=Class.forName("com.oplus.gleanerservice.config.e",false,loader);
            Class<?> manager=Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.config.AutoNotesConfigManager",false,loader);
            Class<?> rule=Class.forName("com.oplus.gleanerservice.config.AutoFlashNotes",false,loader);
            rule.getMethod("getPackageName");rule.getMethod("getSceneName");
            Method convert=converter.getDeclaredMethod("h",entity,Context.class,boolean.class);
            Method copy=entity.getMethod("copy",int.class,String.class,String.class,String.class,String.class,String.class,int.class,String.class,String.class);
            Method config=manager.getDeclaredMethod("c",Context.class,boolean.class);
            Method nativeGate=Class.forName("w9.b",false,loader).getDeclaredMethod("b",Context.class,String.class,List.class);
            Method appCreate=Class.forName("com.oplus.gleanerservice.product.GleanerApplication",false,loader).getDeclaredMethod("onCreate");
            Class<?> switches=Class.forName("com.oplus.gleanerservice.appswitch.AppSwitchConfigManager",false,loader);
            refresh=switches.getDeclaredMethod("h",Context.class);
            switchSingleton=switches.getDeclaredField("b");switchSingleton.setAccessible(true);
            deoptimize(convert);deoptimize(config);deoptimize(nativeGate);
            hook(convert).intercept(chain->{
                Object original=chain.getArg(0);
                if(!Boolean.TRUE.equals(converting.get()))return chain.proceed();
                Context context=(Context)chain.getArg(1);
                Object replacement=original;
                try{
                    String scene=(String)read(original,"getScene"),app=(String)read(original,"getAppName"),page=(String)read(original,"getPageControlName"),ext=(String)read(original,"getExt");
                    if(AimemoryPolicy.repairable(scene,app,page,ext)&&AimemoryPolicy.supported(context.getPackageManager().getPackageInfo(AimemoryPolicy.HOST,0).getLongVersionCode(),context.getPackageManager().getPackageInfo(AimemoryPolicy.WECHAT,0).getLongVersionCode()))
                        replacement=copy.invoke(original,read(original,"getTouchType"),scene,app,read(original,"getPageName"),page,read(original,"getViewId"),read(original,"getType"),AimemoryPolicy.metadata(page),read(original,"getChannel"));
                }catch(ReflectiveOperationException|android.content.pm.PackageManager.NameNotFoundException error){
                    log(Log.WARN,TAG,"Rule metadata unavailable; using native rule",error);
                }
                if(replacement==original)return chain.proceed();
                Object result=chain.proceed(new Object[]{replacement,context,chain.getArg(2)});
                synchronized(lock){owned.add(result);repaired=owned.size();}
                return result;
            });
            hook(config).intercept(chain->{
                synchronized(lock){
                    boolean mode=enabled();Boolean previous=converting.get();converting.set(mode);
                    boolean clear=cacheMode!=null&&cacheMode!=mode;
                    try{
                        if(clear||Boolean.TRUE.equals(chain.getArg(1))){owned.clear();repaired=0;}
                        Object result=chain.proceed(new Object[]{chain.getArg(0),clear||Boolean.TRUE.equals(chain.getArg(1))});
                        cacheMode=mode;
                        if(!(result instanceof List<?> list))return result;
                        Set<Object> current=Collections.newSetFromMap(new IdentityHashMap<>());current.addAll(list);owned.retainAll(current);repaired=owned.size();
                        Set<String> nativePages=new java.util.HashSet<>();
                        for(Object item:list)if(isWeChat(item)&&!owned.contains(item))nativePages.add((String)read(item,"getActivityName"));
                        ArrayList<Object> filtered=new ArrayList<>();int count=0;
                        int patches=0;for(Object item:list){if(owned.contains(item)){if(!RuleMergePolicy.keepPatched(mode,nativePages,(String)read(item,"getActivityName")))continue;patches++;}filtered.add(item);if(isWeChat(item))count++;}
                        available=count;
                        repaired=patches;adapter=!nativePages.isEmpty()?(patches>0?"mixed":"native"):patches>0?"adapted":"no_rules";
                        return filtered.size()==list.size()?result:filtered;
                    }finally{if(previous==null)converting.remove();else converting.set(previous);}
                }
            });
            hook(nativeGate).intercept(chain->{Object result=chain.proceed();if(AimemoryPolicy.WECHAT.equals(chain.getArg(1)))allowed=Boolean.TRUE.equals(result);return result;});
            ocrAdapter=new WechatOcrAdapter(this,loader,this::enabled,item->{synchronized(lock){return owned.contains(item);}},trace);ocrAdapter.install();
            diagnostics(loader);
            hook(appCreate).intercept(chain->{Object result=chain.proceed();
                application=(Application)chain.getThisObject();
                try{switchManager=switchSingleton.get(null);}catch(ReflectiveOperationException error){log(Log.WARN,TAG,"Native registration manager unavailable",error);}
                if(enabled())refreshMonitoring();return result;
            });
            runtime.loaded();adapter="ready";log(Log.INFO,TAG,"Installed v"+BuildConfig.VERSION_NAME+" "+AimemoryPolicy.REVISION+"; native per-app switch retained");
        }catch(Throwable error){runtime.failed("记账接口不完整，已保留原生流程");prefs=null;adapter="unavailable";log(Log.ERROR,TAG,"Adapter unavailable; using native behavior",error);}
    }
    private static Object read(Object target,String method)throws ReflectiveOperationException{return target.getClass().getMethod(method).invoke(target);}
    private static boolean isWeChat(Object item)throws ReflectiveOperationException{return AimemoryPolicy.WECHAT.equals(read(item,"getPackageName"))&&AimemoryPolicy.SCENE.equals(read(item,"getSceneName"));}
    private void refreshMonitoring(){
        Application app=application;if(app==null||refresh==null||switchManager==null)return;
        try{refresh.invoke(switchManager,app);}catch(ReflectiveOperationException error){log(Log.WARN,TAG,"Native page registration refresh unavailable",error);}
    }
    private void diagnostics(ClassLoader loader){
        try{
            Method bind=Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.appswitch.FlashNotesAppSwitchService",false,loader).getDeclaredMethod("onBindSync",Intent.class);bind.setAccessible(true);deoptimize(bind);
            hook(bind).intercept(chain->{Intent intent=(Intent)chain.getArg(0);if(enabled()&&intent!=null&&AimemoryPolicy.WECHAT.equals(intent.getStringExtra("packageName"))&&AimemoryPolicy.paymentPage(intent.getStringExtra("targetName")))trace.page(intent.getStringExtra("targetName"));return chain.proceed();});
            Class<?> handler=Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.parse.payment.PaymentHandler",false,loader);
            Method start=handler.getDeclaredMethod("k",Context.class,Class.forName("com.oplus.gleanerservice.config.AutoFlashNotes",false,loader));deoptimize(start);
            hook(start).intercept(chain->{if(enabled()&&isWeChat(chain.getArg(1)))trace.start((String)read(chain.getArg(1),"getActivityName"));return chain.proceed();});
            for(Method method:Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.parse.payment.PaymentInfoHandler",false,loader).getDeclaredMethods())if(method.getName().equals("g")&&method.getParameterCount()==5&&method.getParameterTypes()[1]==ComponentName.class&&method.getParameterTypes()[3]==Map.class){
                deoptimize(method);hook(method).intercept(chain->{ComponentName component=(ComponentName)chain.getArg(1);if(enabled()&&AimemoryPolicy.WECHAT.equals(component.getPackageName()))trace.data((Map<?,?>)chain.getArg(3));return chain.proceed();});
            }
            for(Method method:handler.getDeclaredMethods())if(method.getName().equals("i")&&method.getParameterCount()==6&&method.getParameterTypes()[2]==ComponentName.class){
                deoptimize(method);hook(method).intercept(chain->{ComponentName component=(ComponentName)chain.getArg(2);if(enabled()&&AimemoryPolicy.WECHAT.equals(component.getPackageName()))trace.request();return chain.proceed();});
            }
            Class<?> collector=Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.parse.payment.PaymentPollingCollector",false,loader);
            java.lang.reflect.Field component=collector.getDeclaredField("b");component.setAccessible(true);
            for(Method method:collector.getDeclaredMethods())if(method.getName().equals("j")&&method.getParameterCount()==1){
                deoptimize(method);hook(method).intercept(chain->{if(enabled()&&AimemoryPolicy.WECHAT.equals(((ComponentName)component.get(chain.getThisObject())).getPackageName()))trace.nativeResult(String.valueOf(read(chain.getArg(0),"f")));return chain.proceed();});
            }else if(method.getName().equals("k")&&method.getParameterCount()==1){
                deoptimize(method);hook(method).intercept(chain->{Boolean previous=ocrScope.get();boolean target=enabled()&&AimemoryPolicy.WECHAT.equals(((ComponentName)component.get(chain.getThisObject())).getPackageName());ocrScope.set(target);if(target)trace.ocr();try{return chain.proceed();}finally{if(previous==null)ocrScope.remove();else ocrScope.set(previous);}});
            }
            Class<?> ocrResult=Class.forName("com.oplus.gleanerservice.feature.flashnotes.autonotes.parse.payment.c",false,loader);
            Method ocrStatus=ocrResult.getDeclaredMethod("b");deoptimize(ocrStatus);hook(ocrStatus).intercept(chain->{Object result=chain.proceed();if(Boolean.TRUE.equals(ocrScope.get()))trace.ocrResult(String.valueOf(result));return result;});
            Class<?> engine=Class.forName("com.oplus.gleanerservice.flashnotes.runtime.engine.FlashNotesEngine",false,loader);
            for(Method method:engine.getDeclaredMethods())if(method.getName().equals("R")&&method.getParameterCount()==3){deoptimize(method);hook(method).intercept(chain->{Object result=chain.proceed();if(enabled()&&wechatTrigger(chain.getArg(1)))trace.accepted(Boolean.TRUE.equals(result));return result;});}
            else if(method.getName().equals("v")&&method.getParameterCount()==5){deoptimize(method);hook(method).intercept(chain->{if(enabled()&&wechatTrigger(chain.getArg(2)))trace.rejected((String)chain.getArg(0));return chain.proceed();});}
        }catch(ReflectiveOperationException error){log(Log.WARN,TAG,"Optional extraction counters unavailable",error);}
    }
    private static boolean wechatTrigger(Object trigger)throws ReflectiveOperationException{return "AUTO_PAYMENT".equals(String.valueOf(read(trigger,"getType")))&&read(trigger,"getExtras") instanceof Map<?,?> extras&&AimemoryPolicy.WECHAT.equals(extras.get("packageName"));}
}
