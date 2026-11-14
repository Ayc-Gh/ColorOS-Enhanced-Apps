// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/** The module app owns Root authorization; injected system code never invokes su. */
final class SoundController {
    private static volatile Process companion;
    private static long lastAttempt;
    private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    static File directory(Context c){File dir=new File(c.createDeviceProtectedStorageContext().getFilesDir(),"sound-policy");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("无法建立声音配置目录");return dir;}
    static synchronized void pulse(Context c,int hostPid){
        try{
            File dir=directory(c);boolean enabled=SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(SoundPolicy.KEY,false);
            if(!enabled){stop(c);return;}
            String apk=c.getApplicationInfo().sourceDir;
            SoundPolicyRoot.atomic(new File(dir,"lease"),BuildConfig.VERSION_CODE+"\n"+SystemClock.elapsedRealtime()+"\n"+hostPid+"\n"+apk);
            if(companion!=null&&companion.isAlive())return;
            File state=new File(dir,"status");if(state.isFile()){var lines=Files.readAllLines(state.toPath());long age=lines.size()>1?SystemClock.elapsedRealtime()-Long.parseLong(lines.get(0)):Long.MAX_VALUE;if(age>=0&&age<4000&&lines.get(1).startsWith("已生效"))return;}
            long now=SystemClock.elapsedRealtime();if(now-lastAttempt<10_000)return;lastAttempt=now;
            worker.execute(()->{try{
                String command="CLASSPATH="+SoundPolicyRoot.quote(apk)+" app_process /system/bin "+SoundPolicyRoot.ROOT_CLASS+" daemon "+SoundPolicyRoot.quote(dir.getPath())+" "+SoundPolicyRoot.quote(apk)+"; CLASSPATH="+SoundPolicyRoot.quote(apk)+" app_process /system/bin "+SoundPolicyRoot.ROOT_CLASS+" restore "+SoundPolicyRoot.quote(dir.getPath())+" "+SoundPolicyRoot.quote(apk);
                Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();companion=process;
                try(InputStream in=process.getInputStream()){in.transferTo(OutputStream.nullOutputStream());}int code=process.waitFor();if(code!=0)SoundPolicyRoot.atomic(new File(dir,"status"),SystemClock.elapsedRealtime()+"\n未生效，请检查本应用的 Root 授权");
            }catch(Exception e){try{SoundPolicyRoot.atomic(new File(dir,"status"),SystemClock.elapsedRealtime()+"\n无法启动声音适配，请检查 Root 授权");}catch(IOException ignored){}}});
        }catch(Exception ignored){}
    }
    static void stop(Context c){try{SoundPolicyRoot.atomic(new File(directory(c),"lease"),"disabled\n");}catch(IOException ignored){}}
    static String state(Context c){try{File file=new File(directory(c),"status");if(!file.isFile())return "等待声音组件加载";var lines=Files.readAllLines(file.toPath());if(lines.size()<2)return "等待声音组件响应";String value=lines.get(1);long age=SystemClock.elapsedRealtime()-Long.parseLong(lines.get(0));if(value.startsWith("已生效")&&(age<0||age>5000))return "等待声音组件重新连接，原生范围将自动恢复";if(value.startsWith("已恢复")&&SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(SoundPolicy.KEY,false))return "开关已开启，正在等待声音组件连接";return value;}catch(Exception e){return "等待声音组件响应";}}
    private SoundController(){}
}
