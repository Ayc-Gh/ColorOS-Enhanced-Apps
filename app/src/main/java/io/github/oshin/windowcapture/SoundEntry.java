// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.Context;
import android.content.ContentProviderClient;
import android.content.SharedPreferences;
import android.net.Uri;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.concurrent.*;

/** Renews a native policy lease only while this scoped audio process is loaded. */
public final class SoundEntry extends XposedModule {
    private String process;
    private boolean installed;
    private ScheduledExecutorService timer;
    private SharedPreferences prefs;
    private boolean wasEnabled;
    private ContentProviderClient bridge;
    private long lastFailure;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if((k==null||SoundPolicy.KEY.equals(k))&&timer!=null)timer.execute(this::pulse);};
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam p){process=p.getProcessName();}
    @Override public synchronized void onPackageReady(XposedModuleInterface.PackageReadyParam p){
        if(installed||!SoundPolicy.PROCESS.equals(process)||!(SoundPolicy.HOST.equals(p.getPackageName())||"com.oplus.atlas".equals(p.getPackageName())))return;
        if(!CompatibilityPolicy.rom(android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,android.os.Build.DISPLAY))return;
        try{Class<?> at=Class.forName("android.app.ActivityThread");Context c=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("currentActivityThread").invoke(null));if(!CompatibilityPolicy.host(SoundPolicy.HOST,c.getPackageManager().getPackageInfo(SoundPolicy.HOST,0).getLongVersionCode()))return;prefs=getRemotePreferences(Config.PREFS);installed=true;prefs.registerOnSharedPreferenceChangeListener(listener);timer=Executors.newSingleThreadScheduledExecutor();timer.scheduleWithFixedDelay(this::pulse,1,10,TimeUnit.SECONDS);}catch(Throwable ignored){}
    }
    private void pulse(){try{
        boolean on=prefs!=null&&prefs.getBoolean(SoundPolicy.KEY,false);if(!on&&!wasEnabled)return;wasEnabled=on;
        // Calling while off also invalidates a previous lease immediately.
        Context c=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        if(c==null)return;
        // Hold a stable provider reference. Transient resolver calls allowed the
        // module process to become cached between renewals on this ColorOS build.
        if(bridge==null)bridge=c.getContentResolver().acquireContentProviderClient(Uri.parse("content://io.github.oshin.windowcapture.sound-policy"));
        if(bridge==null)throw new IllegalStateException("Module provider unavailable");
        bridge.call("lease",null,null);
        if(!on){bridge.close();bridge=null;}
    }catch(Throwable error){if(bridge!=null){bridge.close();bridge=null;}long now=android.os.SystemClock.elapsedRealtime();if(now-lastFailure>30_000){lastFailure=now;log(android.util.Log.WARN,"ColorOSEnhance-Sound","Audio lease renewal failed: "+error.getClass().getSimpleName(),error);}}}
}
