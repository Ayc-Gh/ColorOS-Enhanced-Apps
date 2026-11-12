// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Pinned against the actual HeyCast 17.0.017 APK. All hooks remain process-local. */
final class CastHooks {
    private static final String TAG="WindowCapture-Cast";
    private final XposedModule module;
    private final ClassLoader loader;
    private SharedPreferences prefs;
    // Register a strong listener so the framework keeps this process's preference
    // snapshot synchronized, even though hooks read values on demand.
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=(p,k)->{};
    private final Set<String> reported=ConcurrentHashMap.newKeySet();
    private volatile boolean game;
    private int count;
    private final Map<String,Integer> rateCache=new ConcurrentHashMap<>();
    private final Map<Object,int[]> hardwareCaps=Collections.synchronizedMap(new WeakHashMap<>());
    CastHooks(XposedModule module,ClassLoader loader){this.module=module;this.loader=loader;}
    private boolean enabled(){return bool(Config.CAST);}
    private boolean bool(String key){try{return prefs!=null&&prefs.getBoolean(key,false);}catch(Throwable t){return false;}}
    private int number(String key,int fallback){try{return prefs.getInt(key,fallback);}catch(Throwable t){return fallback;}}
    private void event(String value){if(bool(Config.LOG)&&reported.size()<48&&reported.add(value))module.log(Log.INFO,TAG,value);}
    private interface Installer {XposedInterface.Hooker make(Method method);}
    private Class<?> cls(String name){try{return Class.forName(name,false,loader);}catch(Throwable ignored){return null;}}
    private void hook(String owner,String name,Class<?> result,int params,Installer installer) {
        Class<?> c=cls(owner);boolean found=false;
        if(c!=null)for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==params&&(result==null||result==m.getReturnType())) {
            try{m.setAccessible(true);XposedInterface.Hooker delegate=installer.make(m);module.hook(m).intercept(chain->{
                event("Entered "+owner+"#"+name+" enabled="+enabled());
                return delegate.intercept(chain);
            });count++;found=true;}
            catch(Throwable t){module.log(Log.WARN,TAG,"Cannot hook "+m,t);}
        }
        if(!found)module.log(Log.WARN,TAG,"Interface unavailable: "+owner+"#"+name);
    }
    void install(){
        try{prefs=module.getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener(preferenceListener);}catch(Throwable t){module.log(Log.ERROR,TAG,"Preferences unavailable",t);return;}
        // Deoptimize callers before installing hooks, including constructor paths.
        for(String name:new String[]{"k2.g","k2.c0","k2.b0","a3.a",
                "com.oplus.cast.engine.impl.odmf.a",
                "com.oplus.cast.engine.impl.odmf.display.castdisplaymanager.CastDisplayManager",
                "com.oplus.cast.engine.impl.odmf.display.castdisplaymanager.o",
                "org.webrtc.HardwareVideoEncoder"}) {
            Class<?> c=cls(name);if(c==null)continue;
            for(Method method:c.getDeclaredMethods())try{module.deoptimize(method);}catch(Throwable ignored){}
            for(java.lang.reflect.Constructor<?> constructor:c.getDeclaredConstructors())try{module.deoptimize(constructor);}catch(Throwable ignored){}
        }
        // The settings are read at each call; reconnecting creates fresh negotiated codec state.
        hook("k2.g","Q",boolean.class,2,m->chain->{
            event("start_mirror policy="+enabled());
            if(enabled())game=chain.getArg(1) instanceof Bundle b&&b.getInt("cast_scenario",-1)==1;
            else game=false;return chain.proceed();
        });
        hook("k2.c0","e",Pair.class,2,m->chain->{
            Object nativeValue=chain.proceed();
            if(!enabled()||!(nativeValue instanceof Pair<?,?> p)||!(p.first instanceof Integer min)||!(p.second instanceof Integer max))return nativeValue;
            int[] value=CastPolicy.bitrateKbps(min,max,number(Config.CAST_QUALITY,0),number(Config.CAST_BITRATE,-1),bool(Config.CAST_LOW_BANDWIDTH),bool(Config.CAST_GAME)&&game);
            event("bitrate_kbps="+min+".."+max+" -> "+value[0]+".."+value[1]);
            if(value[0]==min&&value[1]==max)return nativeValue;
            return new Pair<>(value[0],value[1]);
        });
        hook("k2.c0","n",void.class,3,m->chain->{
            if(!enabled())return chain.proceed();Object[] args=chain.getArgs().toArray();
            if(!(args[0] instanceof Integer w)||!(args[1] instanceof Integer h)||!(args[2] instanceof Integer))return chain.proceed();
            int target=targetFps(w,h);
            if(target>0)args[2]=Math.max((Integer)args[2],target);
            event("codec_limit="+w+"x"+h+"@"+args[2]);return chain.proceed(args);
        });
        hook("k2.c0","j",void.class,1,m->chain->{
            if(!enabled())return chain.proceed();
            int[] dims=dimensions(chain.getThisObject());int target=targetFps(dims[0],dims[1]);
            if(target==0){event("fps kept native; hardware check="+dims[0]+"x"+dims[1]);return chain.proceed();}
            // Update this session's local ceiling when the option changes after negotiation.
            setInt(chain.getThisObject(),"n",Math.max(target,Reflect.integer(chain.getThisObject(),"n",0)));
            event("requested_fps="+target);return chain.proceed(new Object[]{target});
        });
        hook("k2.c0","i",null,0,m->chain->{
            Object result=chain.proceed();if(!enabled()||number(Config.CAST_RESOLUTION,0)==0||result==null)return result;
            try{
                Object w=Reflect.call(result,"c"),h=Reflect.call(result,"b");
                if(!(w instanceof Integer width)||!(h instanceof Integer height))return result;
                int[] physical=physicalSize();int[] limited=CastPolicy.fit(width,height,physical[0],physical[1],number(Config.CAST_RESOLUTION,0));
                if(width!=limited[0]||height!=limited[1]){
                    Reflect.call(result,"h",limited[0]);Reflect.call(result,"f",limited[1]);
                    Object capture=Reflect.call(chain.getThisObject(),"g");
                    if(capture!=null){Reflect.call(capture,"h",limited[0]);Reflect.call(capture,"f",limited[1]);}
                    event("resolution="+width+"x"+height+" -> "+limited[0]+"x"+limited[1]);
                }
            }catch(Throwable t){event("resolution skipped: "+t.getClass().getSimpleName());}
            return result;
        });
        hook("com.oplus.cast.engine.impl.odmf.display.castdisplaymanager.CastDisplayManager","r",int.class,1,m->chain->{
            Object nativeValue=chain.proceed();if(!enabled()||!bool(Config.CAST_DENSITY))return nativeValue;
            Object c=Reflect.field(chain.getThisObject(),"e");Context context=c instanceof Context ctx?ctx:context();
            if(context==null)return nativeValue;int dpi=context.getResources().getDisplayMetrics().densityDpi;
            if(dpi<120||dpi>1000)return nativeValue;event("density="+nativeValue+" -> "+dpi);return dpi;
        });
        // PC mirroring assigns density in PCVideoFormats rather than the base
        // manager's density helper. The native display input stores effective DPI.
        hook("k2.b0","B",int.class,1,m->chain->{
            Object original=chain.proceed();if(!enabled()||!bool(Config.CAST_DENSITY))return original;
            int dpi=Reflect.integer(chain.getThisObject(),"j",0);
            if(dpi<120||dpi>1000)return original;
            event("pc_density="+original+" -> "+dpi);return dpi;
        });
        hook("a3.a","f",void.class,1,m->chain->{
            if(enabled()&&(bool(Config.CAST_MODEL)||bool(Config.CAST_HARDWARE))) {
                if(bool(Config.CAST_HARDWARE)&&chain.getArg(0)!=null)hardwareCaps.put(chain.getArg(0),readCapability(chain.getArg(0)));
                event("preserved hardware capability before model policy");return null;
            }return chain.proceed();
        });
        hook("a3.a","c",null,0,m->chain->{
            Object result=chain.proceed();if(!enabled()||!bool(Config.CAST_HARDWARE)||result==null)return result;
            int[] cap=hardwareCaps.get(result);if(cap==null)return result;
            // Preserve the framework's actual enumeration; do not advertise arbitrary codec sizes.
            Reflect.call(result,"l",cap[0],cap[1],cap[2],cap[3]);Reflect.call(result,"n",cap[4],cap[5],cap[6],cap[7]);
            event("restored enumerated hardware capability");return result;
        });
        // Log the final encoder request independently of the app's policy-stage values.
        hook("android.media.MediaCodec","configure",void.class,4,m->chain->{
            if(enabled()&&chain.getArg(0) instanceof MediaFormat format&&chain.getArg(3) instanceof Integer flags&&(flags&MediaCodec.CONFIGURE_FLAG_ENCODE)!=0) {
                try{String mime=format.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/"))event("encoder="+mime+" "+integer(format,"width")+"x"+integer(format,"height")+" fps="+integer(format,"frame-rate")+" bitrate_bps="+integer(format,"bitrate"));}catch(Throwable ignored){}
            }return chain.proceed();
        });
        module.log(Log.INFO,TAG,"Installed "+count+" cast hooks (HeyCast 17.0.017); enabled="+enabled()+" log="+bool(Config.LOG)+" quality="+number(Config.CAST_QUALITY,0));
    }
    private int integer(MediaFormat f,String key){try{return f.getInteger(key);}catch(Throwable t){try{return Math.round(f.getFloat(key));}catch(Throwable ignored){return -1;}}}
    private int[] readCapability(Object cap){return new int[]{value(cap,"c"),value(cap,"e"),value(cap,"d"),value(cap,"b"),value(cap,"h"),value(cap,"j"),value(cap,"i"),value(cap,"g")};}
    private int value(Object o,String method){Object v=Reflect.call(o,method);return v instanceof Integer n?n:0;}
    private void setInt(Object target,String name,int value){for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(name);if(f.getType()!=int.class)return;f.setAccessible(true);f.setInt(target,value);return;}catch(ReflectiveOperationException ignored){}}
    private int[] dimensions(Object formats){try{Object size=Reflect.call(formats,"f");int w=value(size,"c"),h=value(size,"b");if(w>0&&h>0)return new int[]{w,h};}catch(Throwable ignored){}return physicalSize();}
    private int targetFps(int w,int h){int requested=number(Config.CAST_FPS,0);boolean boost=bool(Config.CAST_GAME)&&game;if(requested==0&&!boost)return 0;return CastPolicy.frameRate(requested,boost,hardwareRate(w,h));}
    private int hardwareRate(int w,int h){
        if(w<2||h<2)return 0;String key=w+"x"+h;
        return rateCache.computeIfAbsent(key,k->{int best=0;try{
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(codec.isEncoder()&&codec.isHardwareAccelerated())
                for(String type:codec.getSupportedTypes())if(type.equalsIgnoreCase("video/avc"))try{
                    MediaCodecInfo.VideoCapabilities caps=codec.getCapabilitiesForType(type).getVideoCapabilities();
                    for(int fps:new int[]{60,90,120})if(caps.areSizeAndRateSupported(w,h,fps)||caps.areSizeAndRateSupported(h,w,fps))best=Math.max(best,fps);
                }catch(Throwable ignored){}
        }catch(Throwable t){event("hardware query failed");}return best;});
    }
    private Context context(){try{Class<?> type=Class.forName("android.app.ActivityThread");Object app=type.getMethod("currentApplication").invoke(null);return app instanceof Application a?a:null;}catch(Throwable t){return null;}}
    private int[] physicalSize(){
        try{Context c=context();if(c!=null){DisplayManager dm=c.getSystemService(DisplayManager.class);Display display=dm==null?null:dm.getDisplay(Display.DEFAULT_DISPLAY);if(display!=null){Display.Mode mode=display.getMode();return new int[]{mode.getPhysicalWidth(),mode.getPhysicalHeight()};}}}catch(Throwable ignored){}
        // Unknown display: do not impose an assumed 3120 or density-specific size.
        return new int[]{0,0};
    }
}
