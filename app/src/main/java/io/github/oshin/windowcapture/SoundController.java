// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/** The module app owns Root authorization; injected system code never invokes su. */
final class SoundController {
    private static boolean launching;
    private static final SoundStatusPolicy.Retry retry = new SoundStatusPolicy.Retry();
    private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    static File directory(Context c){File dir=new File(c.createDeviceProtectedStorageContext().getFilesDir(),"sound-policy");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("无法建立声音配置目录");return dir;}
    static synchronized void pulse(Context c,int hostPid){
        try{
            SharedPreferences prefs=SettingsBridge.prefs;
            if(prefs==null)return; // LSPosed may still be binding after a normal launch.
            if(!prefs.getBoolean(SoundPolicy.KEY,false)){stop(c);return;}
            File dir=directory(c);
            String apk=c.getApplicationInfo().sourceDir;
            SoundPolicyRoot.atomic(new File(dir,"lease"),BuildConfig.VERSION_CODE+"\n"+SystemClock.elapsedRealtime()+"\n"+hostPid+"\n"+apk);
            String state=currentStatus(dir);
            if(state!=null&&state.startsWith("已生效")&&new File(dir,"expanded").isFile()){retry.reset();return;}
            if(launching||!retry.ready(SystemClock.elapsedRealtime()))return;
            launching=true;
            worker.execute(()->runCompanion(dir,apk));
        }catch(Exception ignored){}
    }
    private static void runCompanion(File dir,String apk) {
        try {
            String command=SoundCompanionCommand.script(dir.getPath(),apk,SoundPolicyRoot.ROOT_CLASS);
            Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            try(InputStream in=process.getInputStream()){in.transferTo(OutputStream.nullOutputStream());}
            if (process.waitFor() != 0) {
                String state = currentStatus(dir);
                if (state == null || state.startsWith("已恢复") || state.startsWith("已生效")) {
                    SoundPolicyRoot.writeStatus(dir, "声音辅助进程异常退出，请检查运行状态和 Root 授权");
                }
            }
        } catch(Exception error) {
            try{SoundPolicyRoot.writeStatus(dir,"无法启动声音适配，请检查 Root 授权");}catch(IOException ignored){}
        } finally {
            synchronized(SoundController.class) {
                launching=false;
                String state=currentStatus(dir);
                if(state==null||(!state.startsWith("已恢复")&&!state.startsWith("已生效")))retry.failed(SystemClock.elapsedRealtime());
            }
        }
    }
    static synchronized void stop(Context c){retry.reset();try{SoundPolicyRoot.atomic(new File(directory(c),"lease"),"disabled\n");}catch(IOException ignored){}}

    private static String currentStatus(File dir) {
        try {
            File file=new File(dir,"status");
            if(file.isFile())return SoundStatusPolicy.current(Files.readAllLines(file.toPath()),SystemClock.elapsedRealtime(),System.currentTimeMillis(),BuildConfig.VERSION_CODE);
        } catch(IOException ignored){}
        return null;
    }
    static String state(Context c) {
        try {
            SharedPreferences prefs=SettingsBridge.prefs;
            if(prefs==null)return "等待配置服务连接";
            boolean enabled=prefs.getBoolean(SoundPolicy.KEY,false);
            File dir=directory(c);
            return SoundStatusPolicy.describe(enabled,currentStatus(dir),new File(dir,"expanded").isFile());
        } catch(Exception e){return "等待声音组件响应";}
    }
    private SoundController(){}
}
