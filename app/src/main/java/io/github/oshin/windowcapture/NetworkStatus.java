// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import android.content.*;
import android.app.BroadcastOptions;
import android.os.Build;
import org.json.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class NetworkStatus {
    static void attach(Context context,BooleanSupplier enabled,IntSupplier expanded,android.content.SharedPreferences status){
        context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context ctx,Intent intent){
            String app=BuildConfig.APPLICATION_ID,token=intent.getStringExtra("request_nonce");
            if(!(app+".QUERY_RUNTIME").equals(intent.getAction())||token==null||token.length()<32||token.length()>64)return;
            try{
                if(Build.VERSION.SDK_INT>=34){if(getSentFromUid()!=ctx.getPackageManager().getPackageUid(app,0))return;}
                else if(!token.equals(status.getString("request_nonce",null)))return;
                JSONObject data=new JSONObject().put("host",NetworkPolicy.HOST).put("version",BuildConfig.VERSION_NAME).put("code",BuildConfig.VERSION_CODE)
                    .put("network",new JSONObject().put("enabled",enabled.getAsBoolean()).put("expanded",expanded.getAsInt()).put("nativePolicy",true));
                Intent reply=new Intent(app+".REPLY_RUNTIME").setPackage(app).putExtra("host",NetworkPolicy.HOST).putExtra("request_nonce",token).putExtra("payload",data.toString());
                if(Build.VERSION.SDK_INT>=34)ctx.sendBroadcast(reply,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());else ctx.sendBroadcast(reply);
            }catch(Exception ignored){}
        }},new IntentFilter(BuildConfig.APPLICATION_ID+".QUERY_RUNTIME"),Context.RECEIVER_EXPORTED);
    }
    private NetworkStatus(){}
}
