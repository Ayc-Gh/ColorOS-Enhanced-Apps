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

/** Answers only an authenticated, user-initiated status request; never lists apps. */
final class WindowDiagnostics {
    private static final String APP=BuildConfig.APPLICATION_ID,QUERY=APP+".QUERY_RUNTIME",REPLY=APP+".REPLY_RUNTIME";
    private static final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private static final Map<String,Observation> launches=new ConcurrentHashMap<>();
    private static final Set<String> LAUNCH_KEYS=Set.of(Config.LAUNCHER,Config.REMEMBER,Config.JUMP,Config.NOTIFICATION,Config.FILTER,Config.BLACKLIST,Config.APPS,Config.EXCLUDE_SYSTEM);
    private static long launchGeneration;
    private static boolean attached;
    private static final class Entry {
        final Map<String,Boolean> paths=new ConcurrentHashMap<>();volatile boolean triggered;
    }
    private record Observation(String result,long at) {}
    static void register(String feature,String path,boolean present){entries.computeIfAbsent(feature,k->new Entry()).paths.put(path,present);}
    static void trigger(String feature){Entry entry=entries.get(feature);if(entry!=null&&!entry.triggered)entry.triggered=true;}
    static synchronized long generation(){return launchGeneration;}
    static synchronized void configurationChanged(String key){if(key==null||LAUNCH_KEYS.contains(key)){launchGeneration++;launches.clear();}}
    static synchronized void launch(String policy,String result,long generation){if(generation==launchGeneration)launches.put(policy,new Observation(result,SystemClock.elapsedRealtime()));}
    static synchronized void attach(Context context,SharedPreferences config,SharedPreferences status){
        if(attached)return;
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context ctx,Intent intent){
                String token=intent.getStringExtra("request_nonce");if(!QUERY.equals(intent.getAction())||token==null||token.length()<32||token.length()>64)return;
                try{
                    if(Build.VERSION.SDK_INT>=34){if(getSentFromUid()!=ctx.getPackageManager().getPackageUid(APP,0))return;}
                    else if(!token.equals(status.getString("request_nonce",null)))return;
                    JSONObject data=new JSONObject().put("host","android").put("version",BuildConfig.VERSION_NAME).put("code",BuildConfig.VERSION_CODE);
                    JSONArray features=new JSONArray();
                    for(var item:new TreeMap<>(entries).entrySet()){
                        Entry entry=item.getValue();long valid=entry.paths.values().stream().filter(Boolean::booleanValue).count();
                        String state=valid==0?"failed":valid==entry.paths.size()?"loaded":"partial";
                        features.put(new JSONObject().put("name",item.getKey()).put("state",state).put("hits",entry.triggered?1:0));
                    }
                    data.put("features",features);JSONObject observations=new JSONObject();
                    for(String policy:List.of("LAUNCHER","REMEMBER","JUMP","NOTIFICATION")){
                        String key=switch(policy){case "LAUNCHER"->Config.LAUNCHER;case "REMEMBER"->Config.REMEMBER;case "JUMP"->Config.JUMP;default->Config.NOTIFICATION;};
                        Observation observation=launches.get(policy);
                        observations.put(policy,new JSONObject().put("enabled",config.getBoolean(key,false))
                            .put("result",observation==null?"unknown":observation.result())
                            .put("ageMs",observation==null?-1:SystemClock.elapsedRealtime()-observation.at()));
                    }
                    data.put("launches",observations);
                    Intent reply=new Intent(REPLY).setPackage(APP).putExtra("request_nonce",token).putExtra("host","android").putExtra("payload",data.toString());
                    if(Build.VERSION.SDK_INT>=34)ctx.sendBroadcast(reply,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());else ctx.sendBroadcast(reply);
                }catch(Exception ignored){/* Unknown stays unknown if the framework is unavailable. */}
            }
        };
        context.registerReceiver(receiver,new IntentFilter(QUERY),Context.RECEIVER_EXPORTED);attached=true;
    }
}
