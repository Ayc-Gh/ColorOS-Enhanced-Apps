// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.*;
import android.content.*;
import android.os.Build;
import io.github.libxposed.api.XposedModule;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Shared compatibility state and authenticated on-demand diagnostics for each OEM host. */
final class AdapterRuntime {
    interface Details {void add(JSONObject report)throws Exception;}
    private final String host;
    private volatile String state="loading",reason="正在核对接口",hostVersion="unknown";
    private final AtomicBoolean attached=new AtomicBoolean();
    private volatile int paths,hits;
    AdapterRuntime(String host){this.host=host;}
    boolean ready(){return state.equals("ready");}
    void loaded(){state="ready";reason="接口校验完成";}
    void failed(String detail){state="failed";reason=detail;}
    void path(){paths++;}
    void hit(){hits=1;}
    boolean check(Context context){
        if(!CompatibilityPolicy.rom(Build.MODEL,Build.VERSION.SDK_INT,Build.DISPLAY)){state="unsupported";reason="系统版本尚未适配，已保留原生行为";return false;}
        try{var info=context.getPackageManager().getPackageInfo(host,0);hostVersion=info.versionName;
            if(!CompatibilityPolicy.host(host,info.getLongVersionCode())){state="unsupported";reason="宿主应用已更新，当前版本尚未适配";return false;}
            if(AimemoryPolicy.HOST.equals(host)&&!AimemoryPolicy.supported(info.getLongVersionCode(),context.getPackageManager().getPackageInfo(AimemoryPolicy.WECHAT,0).getLongVersionCode())){
                state="unsupported";reason="微信版本尚未适配，已保留原生记账流程";return false;
            }
            return true;
        }catch(Exception error){state="unsupported";reason="无法核对宿主版本，已保留原生行为";return false;}
    }
    boolean check(XposedModule module){
        try{var thread=Class.forName("android.app.ActivityThread");Object current=thread.getMethod("currentActivityThread").invoke(null);Context context=(Context)thread.getMethod("getSystemContext").invoke(current);return check(context);}
        catch(Exception error){state="unsupported";reason="版本查询不可用，已保留原生行为";return false;}
    }
    void arm(XposedModule module,BooleanSupplier enabled,Details details){
        try{
            var method=Instrumentation.class.getDeclaredMethod("callApplicationOnCreate",Application.class);
            module.hook(method).intercept(chain->{Object result=chain.proceed();Object app=chain.getArg(0);if(app instanceof Application application&&host.equals(application.getPackageName()))attach(application,enabled,details,module);return result;});
        }catch(Exception error){module.log(android.util.Log.WARN,"ColorOSEnhance-Status","Runtime receiver unavailable for "+host,error);}
    }
    private void attach(Application app,BooleanSupplier enabled,Details details,XposedModule module){
        if(!attached.compareAndSet(false,true))return;
        try{
            app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
                String name=BuildConfig.APPLICATION_ID,nonce=intent.getStringExtra("request_nonce");
                if(!(name+".QUERY_RUNTIME").equals(intent.getAction())||nonce==null||nonce.length()<32||nonce.length()>64)return;
                try{
                    if(Build.VERSION.SDK_INT>=34){if(getSentFromUid()!=context.getPackageManager().getPackageUid(name,0))return;}
                    else if(!nonce.equals(module.getRemotePreferences("aod_runtime_status").getString("request_nonce",null)))return;
                    JSONObject report=new JSONObject().put("host",host).put("version",BuildConfig.VERSION_NAME).put("code",BuildConfig.VERSION_CODE)
                        .put("adapter",new JSONObject().put("state",state).put("reason",reason).put("hostVersion",hostVersion).put("enabled",enabled.getAsBoolean()).put("paths",paths).put("hits",hits));
                    details.add(report);
                    Intent reply=new Intent(name+".REPLY_RUNTIME").setPackage(name).putExtra("host",host).putExtra("request_nonce",nonce).putExtra("payload",report.toString());
                    if(Build.VERSION.SDK_INT>=34)context.sendBroadcast(reply,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_NONE).toBundle());else context.sendBroadcast(reply);
                }catch(Exception ignored){}
            }},new IntentFilter(BuildConfig.APPLICATION_ID+".QUERY_RUNTIME"),Context.RECEIVER_EXPORTED);
        }catch(Exception error){attached.set(false);module.log(android.util.Log.WARN,"ColorOSEnhance-Status","Cannot attach runtime receiver",error);}
    }
}
