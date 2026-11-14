// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.os.SystemClock;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Root companion: transient reload, bounded host lease, and native restoration. */
public final class SoundPolicyRoot {
    static final String PACKAGE="io.github.oshin.windowcapture",ROOT_CLASS=PACKAGE+".SoundPolicyRoot";
    private static final String POLICY="/system_ext/etc/Multimedia_Daemon_List.xml";
    private SoundPolicyRoot(){}
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    static void atomic(File file,String data)throws IOException{
        android.util.AtomicFile a=new android.util.AtomicFile(file);FileOutputStream out=a.startWrite();try{out.write(data.getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}catch(Throwable t){a.failWrite(out);throw t;}
    }
    private static String run(String... args)throws Exception{
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();Thread reader=new Thread(()->{try{p.getInputStream().transferTo(out);}catch(IOException ignored){}});reader.start();
        if(!p.waitFor(12,TimeUnit.SECONDS)){p.destroy();if(!p.waitFor(2,TimeUnit.SECONDS))p.destroyForcibly();throw new IOException("Audio command timed out");}reader.join(1000);if(p.exitValue()!=0)throw new IOException("Audio command failed ("+p.exitValue()+")");return out.toString(StandardCharsets.UTF_8);
    }
    private static int listPid()throws Exception{
        String[] pids=run("/system/bin/pidof","mmlistservice").trim().split("\\s+");if(pids.length!=1)throw new IOException("Audio policy service unavailable");int pid=Integer.parseInt(pids[0]);
        if(!new File("/proc/"+pid+"/exe").getCanonicalPath().equals("/system_ext/bin/mmlistservice"))throw new IOException("Audio policy service changed");return pid;
    }
    private static void status(File dir,String message){try{atomic(new File(dir,"status"),SystemClock.elapsedRealtime()+"\n"+message);}catch(IOException ignored){}}
    private static boolean lease(File dir,String apk){try{
        List<String> s=Files.readAllLines(new File(dir,"lease").toPath());if(s.size()!=4||!apk.equals(s.get(3))||!new File(apk).isFile())return false;
        int pid=Integer.parseInt(s.get(2));String process=new String(Files.readAllBytes(new File("/proc/"+pid+"/cmdline").toPath()),StandardCharsets.UTF_8).split("\0",2)[0];
        return Integer.parseInt(s.get(0))==BuildConfig.VERSION_CODE&&SoundPolicy.leaseValid(SystemClock.elapsedRealtime(),Long.parseLong(s.get(1)),pid,SoundPolicy.PROCESS.equals(process));
    }catch(Exception e){return false;}}
    private static Set<String> installed()throws Exception{Set<String> a=new TreeSet<>();for(String line:run("/system/bin/cmd","package","list","packages","--user","0").split("\\R"))if(line.startsWith("package:"))a.add(line.substring(8).trim());if(a.isEmpty())throw new IOException("Application list unavailable");return a;}
    private static void apply(File dir,String apk,int pid,SoundPolicy.Patch patch)throws Exception{
        File source=new File(dir,"runtime-policy.xml");atomic(source,patch.xml());
        try{
            run("/system/bin/chmod","644",source.getPath());run("/system/bin/chcon","u:object_r:system_file:s0",source.getPath());
            String mounts=new String(Files.readAllBytes(new File("/proc/"+pid+"/mountinfo").toPath()),StandardCharsets.UTF_8);for(String line:mounts.split("\\R"))if(line.contains(" "+POLICY+" "))throw new IOException("An existing audio overlay must be retained");
            // The shell owns unmount cleanup, including if its Java caller exits.
            String script="set -e\ntrap "+quote("umount "+quote(POLICY))+" EXIT\nmount --bind "+quote(source.getPath())+" "+quote(POLICY)+"\nCLASSPATH="+quote(apk)+" app_process /system/bin "+ROOT_CLASS+" reload\n";
            run("/system/bin/nsenter","-t",String.valueOf(pid),"-m","--","/system/bin/sh","-c",script);
        }finally{Files.deleteIfExists(source.toPath());}
    }
    private static void restore(File dir)throws Exception{
        File record=new File(dir,"expanded");if(!record.isFile())return;List<String> expanded=Files.readAllLines(record.toPath());File baselineFile=new File(dir,"native-active-baseline");Set<String> baseline=baselineFile.isFile()?new HashSet<>(Files.readAllLines(baselineFile.toPath())):Set.of();Exception failure=null;
        for(String pkg:expanded)if(SoundPolicy.packageName(pkg)&&!baseline.contains(pkg))try{if(SoundNative.enabled(pkg)&&SoundNative.enable(pkg,false)!=0)throw new IOException("MSS stop rejected");}catch(Exception e){failure=e;}
        SoundNative.reload();if(!expanded.isEmpty()){String pkg=expanded.get(0),attribute=SoundNative.attribute(pkg);Map<String,Integer> original=SoundPolicy.entries(new String(Files.readAllBytes(new File(POLICY).toPath()),StandardCharsets.UTF_8));if(!SoundPolicy.restoredAttribute(attribute,original.get(pkg)))throw new IOException("Native application policy was not restored");}
        if(failure!=null)throw failure;Files.deleteIfExists(record.toPath());Files.deleteIfExists(baselineFile.toPath());status(dir,"已恢复系统原生应用范围");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("reload")){SoundNative.reload();return;}
        if(args.length!=3||!(args[0].equals("daemon")||args[0].equals("restore")))throw new IllegalArgumentException("Invalid audio companion arguments");
        File dir=new File(args[1]);String apk=args[2];if(!dir.getCanonicalPath().equals("/data/user_de/0/"+PACKAGE+"/files/sound-policy")||!new File(apk).getCanonicalPath().startsWith("/data/app/")||!apk.endsWith("/base.apk"))throw new IOException("Invalid audio companion path");
        try(RandomAccessFile file=new RandomAccessFile(new File(dir,"lock"),"rw");FileLock lock=file.getChannel().tryLock()){
            if(lock==null)return;
            if(args[0].equals("restore")){restore(dir);return;}
            if(!lease(dir,apk))return;
            Exception activationError=null;
            try{
                restore(dir);int pid=listPid();String xml=new String(Files.readAllBytes(new File(POLICY).toPath()),StandardCharsets.UTF_8);Map<String,Integer> original=SoundPolicy.entries(xml);
                String prop=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"ro.oplus.audio.support.mss","0");if((Integer.parseInt(prop)&1)==0)throw new IOException("Native MSS is unavailable");
                if(new File("/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml").exists())throw new IOException("Online audio policy requires a separate adaptation");
                var sample=original.entrySet().iterator().next();if(!String.valueOf(sample.getValue()).equals(SoundNative.attribute(sample.getKey())))throw new IOException("Native policy schema differs");
                Set<String> packages=Set.of();Set<String> baseline=new HashSet<>();SoundPolicy.Patch patch=null;long refresh=0,check=0;
                while(lease(dir,apk)){
                    long now=SystemClock.elapsedRealtime();
                    if(now>=refresh){Set<String> current=installed();if(!current.equals(packages)){
                        patch=SoundPolicy.expand(xml,current);Set<String> tracked=new LinkedHashSet<>();File record=new File(dir,"expanded");if(record.isFile())tracked.addAll(Files.readAllLines(record.toPath()));for(String pkg:patch.expanded())if(!tracked.contains(pkg)&&original.containsKey(pkg)&&SoundNative.enabled(pkg))baseline.add(pkg);tracked.addAll(patch.expanded());atomic(new File(dir,"native-active-baseline"),String.join("\n",baseline));atomic(record,String.join("\n",tracked));
                        apply(dir,apk,pid,patch);if(!patch.expanded().isEmpty()){String test=patch.expanded().iterator().next();String attr=SoundNative.attribute(test);if(attr==null||(Integer.parseInt(attr)&1)==0)throw new IOException("Native policy activation failed");}packages=current;
                    }refresh=now+60_000;}
                    // Native audio rules can be refreshed independently of package
                    // installation. Recover a lost runtime policy without a toggle.
                    if(now>=check&&patch!=null&&patch.expanded().contains(PACKAGE)){String expected=String.valueOf(SoundPolicy.entries(patch.xml()).get(PACKAGE));if(!expected.equals(SoundNative.attribute(PACKAGE))){pid=listPid();apply(dir,apk,pid,patch);if(!expected.equals(SoundNative.attribute(PACKAGE)))throw new IOException("Native policy refresh failed");}check=now+10_000;}
                    status(dir,"已生效 · 原生声音分轨应用限制已解除");Thread.sleep(1000);
                }
            }catch(Exception error){activationError=error;}
            finally{try{restore(dir);if(activationError!=null)status(dir,"未生效，原生范围已恢复："+activationError.getMessage());}catch(Exception error){status(dir,"恢复失败，请重新启动手机后保持开关关闭");throw error;}}
        }
    }
}
