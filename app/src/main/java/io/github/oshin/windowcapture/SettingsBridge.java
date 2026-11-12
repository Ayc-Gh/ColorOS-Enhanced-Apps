// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class SettingsBridge {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArraySet<Runnable> observers=new CopyOnWriteArraySet<>();
    static volatile SharedPreferences prefs;
    static volatile String status="正在连接 LSPosed…请先启用模块并勾选系统框架。";
    private static volatile XposedService service;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->main.post(SettingsBridge::notifyObservers);
    static void init(com.op.aod.enhance.AodApplication application) {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            public void onServiceBind(XposedService current) {
                service=current;
                worker.execute(()->{
                    try {
                        if(current.getApiVersion()<102) throw new IllegalStateException("需要支持 API 102 的框架");
                        application.onServiceBind(current);
                        SharedPreferences value=current.getRemotePreferences(Config.PREFS);
                        var scope=current.getScope();
                        String message="已连接 "+current.getFrameworkName()+" · API "+current.getApiVersion()+"\n系统框架"+(scope.contains("system")?"已勾选":"未勾选")+" · 屏幕投屏"+(scope.contains("com.oplus.cast")?"已勾选":"未勾选")+"\n系统界面"+(scope.contains("com.android.systemui")?"已勾选":"未勾选")+" · 息屏"+(scope.contains("com.oplus.aod")?"已勾选":"未勾选");
                        main.post(()->{
                            if(service!=current) return;
                            if(prefs!=null) prefs.unregisterOnSharedPreferenceChangeListener(listener);
                            prefs=value; prefs.registerOnSharedPreferenceChangeListener(listener);
                            status=message; notifyObservers();
                        });
                    } catch(Throwable t) { main.post(()->{status="连接失败："+t.getMessage(); notifyObservers();}); }
                });
            }
            public void onServiceDied(XposedService current) {
                application.onServiceDied(current);
                main.post(()->{if(service!=current)return; service=null; if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(listener); prefs=null;status="框架连接已断开，请重新打开。";notifyObservers();});
            }
        });
    }
    static void observe(Runnable r) { observers.add(r); }
    static void unobserve(Runnable r) { observers.remove(r); }
    private static void notifyObservers() { for(Runnable r:observers)r.run(); }
    static void save(java.util.function.Consumer<SharedPreferences.Editor> edit, java.util.function.Consumer<Boolean> done) {
        SharedPreferences p=prefs;
        worker.execute(()->{boolean ok=false;try {if(p!=null){SharedPreferences.Editor e=p.edit();edit.accept(e);ok=e.commit();}}catch(Throwable ignored){} final boolean result=ok;main.post(()->done.accept(result));});
    }
}
