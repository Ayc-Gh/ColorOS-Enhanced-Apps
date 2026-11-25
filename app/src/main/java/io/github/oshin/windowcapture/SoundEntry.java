// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.Context;
import android.content.ContentProviderClient;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.SystemClock;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.concurrent.*;

/** Renews a native policy lease only while this scoped audio process is loaded. */
public final class SoundEntry extends XposedModule {
    private String process;
    private boolean installed;
    private ScheduledExecutorService timer;
    private SharedPreferences prefs;
    private ContentProviderClient bridge;
    private final SoundStatusPolicy.Retry retry=new SoundStatusPolicy.Retry();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{
        if(k==null||SoundPolicy.KEY.equals(k))timer.execute(()->{retry.reset();pulse();});
    };
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam p){process=p.getProcessName();}
    @Override public synchronized void onPackageReady(XposedModuleInterface.PackageReadyParam p){
        if(installed||!SoundPolicy.PROCESS.equals(process)||!(SoundPolicy.HOST.equals(p.getPackageName())||"com.oplus.atlas".equals(p.getPackageName())))return;
        if(!CompatibilityPolicy.rom(android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,android.os.Build.DISPLAY))return;
        timer=Executors.newSingleThreadScheduledExecutor();
        timer.scheduleWithFixedDelay(this::pulse,1,10,TimeUnit.SECONDS);
        installed=true;
    }
    private void pulse() {
        long now=SystemClock.elapsedRealtime();
        if(!retry.ready(now))return;
        try {
            Context c=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if(c==null)return;
            // The module settings and its libxposed provider become available after first unlock.
            android.os.UserManager users = c.getSystemService(android.os.UserManager.class);
            if (users == null || !users.isUserUnlocked()) return;
            if(prefs==null) {
                long version=c.getPackageManager().getPackageInfo(SoundPolicy.HOST,0).getLongVersionCode();
                if(!CompatibilityPolicy.host(SoundPolicy.HOST,version)){timer.shutdown();return;}
                SharedPreferences value=getRemotePreferences(Config.PREFS);
                value.registerOnSharedPreferenceChangeListener(listener);
                prefs=value;
            }
            boolean enabled=prefs.getBoolean(SoundPolicy.KEY,false);
            if(!enabled&&bridge==null){retry.reset();return;}
            ApplicationInfo info=c.getPackageManager().getApplicationInfo(SoundStartupPolicy.APP,0);
            if(!SoundStartupPolicy.available(info.enabled,info.flags)) {
                disconnect();
                retry.reset();
                return;
            }
            // Keep a stable reference during a healthy lease. Only a normal, enabled
            // package can receive a new connection after a stop or suspension.
            if(bridge==null)bridge=c.getContentResolver().acquireContentProviderClient(Uri.parse("content://io.github.oshin.windowcapture.sound-policy"));
            if(bridge==null)throw new IllegalStateException("Module provider unavailable");
            bridge.call("lease",null,null);
            if(!enabled)disconnect();
            retry.reset();
        }catch(Throwable error){
            disconnect();
            retry.failed(now);
            log(android.util.Log.WARN,"ColorOSEnhance-Sound","Audio lease renewal failed: "+error.getClass().getSimpleName(),error);
        }
    }
    private void disconnect() {
        ContentProviderClient previous=bridge;
        bridge=null;
        if(previous!=null)try{previous.close();}catch(RuntimeException ignored){}
    }
}
