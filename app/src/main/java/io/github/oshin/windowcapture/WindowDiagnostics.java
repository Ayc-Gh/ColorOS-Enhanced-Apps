// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.BroadcastOptions;
import android.content.*;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Answers only an authenticated, user-initiated status request; never lists apps. */
final class WindowDiagnostics {
    private static final String APP=BuildConfig.APPLICATION_ID,QUERY=APP+".QUERY_RUNTIME",REPLY=APP+".REPLY_RUNTIME";
    private static final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private static final Map<String,Observation> launches=new ConcurrentHashMap<>();
    private static final Set<String> LAUNCH_KEYS=Set.of(Config.LAUNCHER,Config.REMEMBER,Config.JUMP,Config.NOTIFICATION,Config.EXCLUDE_SYSTEM);
    private static long launchGeneration;
    private static boolean attached;
    private static volatile String compatibility="ready",compatibilityReason="本机接口校验";
    private static final class Entry {
        final Map<String,Boolean> paths=new ConcurrentHashMap<>();volatile boolean triggered,sealed;volatile String error;
        volatile Value observation;
    }
    private record Value(String path,int original,int applied,long at) {}
    private record Observation(String result,long at) {}
    static void register(String feature,String path,boolean present){entries.computeIfAbsent(feature,k->new Entry()).paths.put(path,present);}
    static void seal(String feature){entries.computeIfAbsent(feature,k->new Entry()).sealed=true;}
    static void fail(String feature){entries.computeIfAbsent(feature,k->new Entry()).error="必需接口安装失败";}
    static void compatibility(boolean supported){compatibility=supported?"ready":"unsupported";compatibilityReason=supported?"本机接口校验":"系统版本尚未适配，已保留原生行为";}
    static boolean ready(String feature){Entry e=entries.get(feature);return e!=null&&e.sealed&&e.error==null&&FeatureReadiness.ready(e.paths,required(feature));}
    private static Set<String> required(String feature){String wm="com.android.server.wm.";return switch(feature){
        case "Window.Capture"->Set.of(wm+"WindowState#isSecureLocked",wm+"WindowManagerService#addWindow",wm+"WindowManagerService#relayoutWindow");
        case "Window.Layout"->Set.of(wm+"FlexibleTaskInfo#getRatio",wm+"FlexibleWindowManagerService#getMaxWinNum");
        case "Window.Corner"->Set.of(wm+"TaskExtImpl#getCornerRadius");
        case "Window.ShadowFocused"->Set.of(wm+"TaskExtImpl#getShadowRadiusFocused");
        case "Window.ShadowUnfocused"->Set.of(wm+"TaskExtImpl#getShadowRadiusUnfocused");
        case "Window.Split"->Set.of(wm+"TaskDisplayArea#supportsNonResizableMultiWindow",wm+"TaskDisplayArea#supportsActivityMinWidthHeightMultiWindow");
        case "Window.Launch"->Set.of(wm+"FlexibleTaskController#adjustOptionsForSmartMultiWindow");
        case "Window.Caption"->Set.of(wm+"FlexibleTaskCaptionView#initCaptionView",wm+"FlexibleCaptionView#updateThreeButtonVisibility");
        case "Window.Edge"->Set.of(wm+"FloatHandleController#getLimitMaxFloatNum",wm+"FlexibleTaskController#exitFlexibleTask");
        case "Sound.Startup"->Set.of("com.android.server.am.OplusAppStartupManager#isAllowStartFromProvider","com.android.server.am.OplusAppStartupManager$OplusStartupStrategy#isAllowAssociateByList");
        default->Set.of();};}
    static void trigger(String feature){Entry entry=entries.get(feature);if(entry!=null&&!entry.triggered)entry.triggered=true;}
    static void value(String feature,String path,int original,int applied){Entry entry=entries.get(feature);if(entry!=null){entry.triggered=true;entry.observation=new Value(path,original,applied,SystemClock.elapsedRealtime());}}
    static synchronized long generation(){return launchGeneration;}
    static synchronized void configurationChanged(String key){if(key==null||LAUNCH_KEYS.contains(key)){launchGeneration++;launches.clear();}}
    static synchronized void launch(String policy,String result,long generation){if(generation==launchGeneration)launches.put(policy,new Observation(result,SystemClock.elapsedRealtime()));}
    static synchronized void attach(Context context,Supplier<SharedPreferences> config,Supplier<SharedPreferences> status){
        if(attached)return;
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context ctx,Intent intent){
                String token=intent.getStringExtra("request_nonce");if(!QUERY.equals(intent.getAction())||token==null||token.length()<32||token.length()>64)return;
                try{
                    if(Build.VERSION.SDK_INT>=34){if(getSentFromUid()!=ctx.getPackageManager().getPackageUid(APP,0))return;}
                    else if(!token.equals(status.get().getString("request_nonce",null)))return;
                    JSONObject data=new JSONObject().put("host","android").put("version",BuildConfig.VERSION_NAME).put("code",BuildConfig.VERSION_CODE);
                    data.put("adapter",new JSONObject().put("state",compatibility).put("reason",compatibilityReason));
                    SharedPreferences current=config.get();
                    Map<String,?> saved=null;
                    if(current!=null)try{saved=current.getAll();}catch(RuntimeException ignored){/* Still report installed paths. */}
                    if(current==null && "ready".equals(compatibility))
                        data.put("adapter",new JSONObject().put("state","loading").put("reason","等待框架配置连接，正在重试"));
                    else if(saved==null && "ready".equals(compatibility))
                        data.put("configurationRead","failed");
                    JSONArray features=new JSONArray();
                    for(var item:new TreeMap<>(entries).entrySet()){
                        Entry entry=item.getValue();long valid=entry.paths.values().stream().filter(Boolean::booleanValue).count();
                        String state=ready(item.getKey())?"loaded":"failed";
                        JSONObject feature=new JSONObject().put("name",item.getKey()).put("state",state).put("hits",entry.triggered?1:0).put("optionalMissing",entry.paths.entrySet().stream().filter(p->!p.getValue()&&!required(item.getKey()).contains(p.getKey())).count()).put("reason",entry.error==null?(ready(item.getKey())?"必需接口已校验；实际效果需行为验证":"必需接口缺失，保留原生行为"):entry.error);
                        JSONArray paths=new JSONArray();for(var path:new TreeMap<>(entry.paths).entrySet())paths.put(new JSONObject().put("name",path.getKey()).put("present",path.getValue()).put("required",required(item.getKey()).contains(path.getKey())));
                        feature.put("paths",paths);
                        Value value=entry.observation;if(value!=null)feature.put("lastValue",new JSONObject().put("path",value.path()).put("native",value.original()).put("applied",value.applied()).put("ageMs",SystemClock.elapsedRealtime()-value.at()));
                        features.put(feature);
                    }
                    data.put("features",features);JSONObject observations=new JSONObject();
                    if(saved!=null)for(String policy:List.of("LAUNCHER","REMEMBER","JUMP","NOTIFICATION")){
                        String key=switch(policy){case "LAUNCHER"->Config.LAUNCHER;case "REMEMBER"->Config.REMEMBER;case "JUMP"->Config.JUMP;default->Config.NOTIFICATION;};
                        Observation observation=launches.get(policy);
                        observations.put(policy,new JSONObject().put("enabled",Boolean.TRUE.equals(saved.get(key)))
                            .put("result",observation==null?"unknown":observation.result())
                            .put("ageMs",observation==null?-1:SystemClock.elapsedRealtime()-observation.at()));
                    }
                    if(saved!=null)data.put("launches",observations);
                    Intent reply=new Intent(REPLY).setPackage(APP).putExtra("request_nonce",token).putExtra("host","android").putExtra("payload",data.toString());
                    if(Build.VERSION.SDK_INT>=34)ctx.sendBroadcast(reply,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());else ctx.sendBroadcast(reply);
                }catch(Exception ignored){/* Unknown stays unknown if the framework is unavailable. */}
            }
        };
        context.registerReceiver(receiver,new IntentFilter(QUERY),Context.RECEIVER_EXPORTED);attached=true;
    }
}
