// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.os.SystemClock;
import android.system.Os;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Root companion for the stock PHY110 policy service. */
public final class SoundPolicyRoot {
    static final String PACKAGE="io.github.oshin.windowcapture",ROOT_CLASS=PACKAGE+".SoundPolicyRoot";
    private static final String DIRECTORY="/data/user_de/0/"+PACKAGE+"/files/sound-policy";
    private static final String ONLINE="/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml";
    private static final String CHANGED="expanded",ACTIVE="native-active-baseline",PLAN="native-recovery-plan";
    private static long lastStatusAt=-1;
    private static String lastStatus;
    private SoundPolicyRoot(){}

    static String readText(Path path)throws IOException{
        // procfs files can report a length of zero while providing data on read.
        try(InputStream input=new FileInputStream(path.toFile())){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];
            for(int count;(count=input.read(buffer))!=-1;){
                if(bytes.size()+count>8*1024*1024)throw new IOException("Audio policy input is too large");
                bytes.write(buffer,0,count);
            }
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    /** A failed fsync or rename is an error, including for recovery records. */
    static void atomic(File file,String data)throws IOException{
        Path target=file.toPath(),tmp=Files.createTempFile(target.getParent(),".sound-write-",".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(tmp.toFile())){
                out.write(data.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // Root publishes state to the module's private directory. The module must be able to read it.
            try{Os.chmod(tmp.toString(),0644);}catch(android.system.ErrnoException e){throw new IOException(e);}
            Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
    }
    static void requireRegular(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid audio state file: "+path.getFileName());
        try{
            var st=Os.lstat(path.toString());
            if(st.st_nlink!=1||st.st_uid!=0)throw new IOException("Unexpected audio state file owner");
        }catch(android.system.ErrnoException e){throw new IOException(e);}
    }
    static FileChannel lockChannel(File dir,String name)throws IOException{
        Path path=dir.toPath().resolve(name);
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))requireRegular(path);
        FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.CREATE,LinkOption.NOFOLLOW_LINKS);
        try{requireRegular(path);return channel;}catch(IOException e){channel.close();throw e;}
    }
    static String run(String... args)throws Exception{return run(new ProcessBuilder(args));}
    static String run(ProcessBuilder command)throws Exception{
        Process process=command.redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(var in=process.getInputStream()){in.transferTo(output);}catch(IOException ignored){}},"sound-command-output");
        reader.setDaemon(true);
        reader.start();
        if(!process.waitFor(15,TimeUnit.SECONDS)){
            process.destroy();
            if(!process.waitFor(1,TimeUnit.SECONDS))process.destroyForcibly();
            throw new IOException("Audio command timed out: "+command.command().get(0));
        }
        reader.join(1000);
        String result=output.toString(StandardCharsets.UTF_8);
        if(process.exitValue()!=0){
            String detail=result.lines().filter(s->s.startsWith("Audio policy error:")).findFirst().orElse("");
            throw new IOException("Audio command failed ("+process.exitValue()+") "+detail);
        }
        return result;
    }
    // Binder and kernel filesystem reads do not honour Thread.interrupt(). The outer
    // shell keeps the APK alive and invokes a separately bounded recovery process.
    private static final class Deadline {
        static final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{
            Thread t=new Thread(r,"audio-policy-deadline");t.setDaemon(true);return t;
        });
        static {timer.setRemoveOnCancelPolicy(true);}
    }
    private static ScheduledFuture<?> deadline(long seconds){
        return Deadline.timer.schedule(()->Runtime.getRuntime().halt(124),seconds,TimeUnit.SECONDS);
    }
    static void writeStatus(File dir,String message)throws IOException{
        atomic(new File(dir,"status"),SystemClock.elapsedRealtime()+"\n"+message+"\n"+BuildConfig.VERSION_CODE+"\n"+System.currentTimeMillis());
    }
    private static void status(File dir,String message){
        long now=SystemClock.elapsedRealtime();
        if(!SoundStatusPolicy.write(now,lastStatusAt,lastStatus,message))return;
        try{writeStatus(dir,message);lastStatus=message;lastStatusAt=now;}catch(IOException ignored){}
    }
    private static boolean lease(File dir,String apk){
        try{
            Path path=dir.toPath().resolve("lease");
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return false;
            List<String> lines=Files.readAllLines(path);
            if(lines.size()!=4||!apk.equals(lines.get(3))||!new File(apk).isFile())return false;
            int pid=Integer.parseInt(lines.get(2));
            String name=readText(Paths.get("/proc/"+pid+"/cmdline")).split("\0",2)[0];
            return Integer.parseInt(lines.get(0))==BuildConfig.VERSION_CODE&&SoundPolicy.leaseValid(
                    SystemClock.elapsedRealtime(),Long.parseLong(lines.get(1)),pid,SoundPolicy.PROCESS.equals(name));
        }catch(Exception e){return false;}
    }
    private static Set<String> installed()throws Exception{
        Set<String> packages=new TreeSet<>();
        for(String line:run("/system/bin/cmd","package","list","packages","--user","0").split("\\R"))
            if(line.startsWith("package:")&&SoundPolicy.packageName(line.substring(8).trim()))packages.add(line.substring(8).trim());
        if(packages.isEmpty())throw new IOException("Application list unavailable");
        return packages;
    }
    private static Set<String> readPackages(Path file)throws IOException{
        requireRegular(file);
        Set<String> packages=new LinkedHashSet<>();
        for(String pkg:Files.readAllLines(file)){
            if(!SoundPolicy.packageName(pkg))throw new IOException("Invalid audio recovery record");
            packages.add(pkg);
        }
        return packages;
    }
    private static SoundPolicyMount.Service service()throws Exception{
        return SoundPolicyMount.Service.current();
    }
    private static void cleanStage(File dir,String classpath)throws Exception{
        if(Files.exists(dir.toPath().resolve(SoundPolicyMount.JOURNAL),LinkOption.NOFOLLOW_LINKS)
                ||Files.exists(dir.toPath().resolve(SoundPolicyMount.SOURCE),LinkOption.NOFOLLOW_LINKS))
            service().worker("cleanup",dir,classpath,"-","-");
    }
    private static void apply(File dir,String classpath,SoundPolicy.Patch patch,String hash)throws Exception{
        var target=service();
        if(!hash.equals(SoundPolicyMount.digest(target.baseline())))throw new IOException("Native policy changed");
        Path source=dir.toPath().resolve(SoundPolicyMount.SOURCE);
        if(Files.exists(source,LinkOption.NOFOLLOW_LINKS)||new File(dir,SoundPolicyMount.JOURNAL).exists())
            throw new IOException("Previous policy staging is still pending");
        atomic(source.toFile(),patch.xml());
        requireRegular(source);
        Os.chmod(source.toString(),0444);
        run("/system/bin/chcon","u:object_r:system_file:s0",source.toString());
        target.worker("apply",dir,classpath,hash,SoundPolicyMount.inode(source));
    }
    private static void restore(File dir,String classpath)throws Exception{
        var alarm=deadline(45);
        try{
            cleanStage(dir,classpath);
            Path changed=dir.toPath().resolve(CHANGED),active=dir.toPath().resolve(ACTIVE),plan=dir.toPath().resolve(PLAN);
            boolean hasPlan=Files.exists(plan,LinkOption.NOFOLLOW_LINKS);
            if(!hasPlan&&!Files.exists(changed,LinkOption.NOFOLLOW_LINKS)){
                Files.deleteIfExists(active);
                return;
            }
            var target=service();
            String xml=target.baseline();
            String hash=SoundPolicyMount.digest(xml);
            SoundRecoveryPlan recovery;
            if(hasPlan){
                requireRegular(plan);
                recovery=SoundRecoveryPlan.parse(Files.readAllLines(plan));
                if(!recovery.hash().equals(hash))throw new IOException("Native recovery baseline changed");
            }else{
                // Older installed versions wrote the two lists separately, before applying.
                recovery=new SoundRecoveryPlan(hash,readPackages(changed),readPackages(active));
            }
            Map<String,Integer> original=SoundPolicy.entries(xml);
            Set<String> expanded=recovery.changed(),baseline=recovery.alreadyActive();
            Exception failure=null;
            for(String pkg:expanded){
                if(baseline.contains(pkg))continue;
                try{if(SoundNative.enabled(pkg)&&SoundNative.enable(pkg,false)!=0)throw new IOException("MSS stop rejected");}
                catch(Exception e){failure=e;}
            }
            target.verify();
            SoundNative.reload();
            recovery.verifyRestored(original,SoundNative::attribute);
            if(!hash.equals(SoundPolicyMount.digest(target.baseline())))throw new IOException("Native recovery view changed");
            if(failure!=null)throw failure;
            Files.deleteIfExists(changed);Files.deleteIfExists(active);Files.deleteIfExists(plan);
            status(dir,"已恢复系统原生应用范围");
        }finally{alarm.cancel(false);}
    }
    private static void daemon(File dir,String apk,String classpath)throws Exception{
        restore(dir,classpath);
        var initializing=deadline(25);
        String xml,hash;
        Map<String,Integer> original;
        try{
            String prop=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"ro.oplus.audio.support.mss","0");
            if((Integer.parseInt(prop)&1)==0)throw new IOException("Native MSS is unavailable");
            if(new File(ONLINE).exists())throw new IOException("Online audio policy requires separate adaptation");
            xml=service().baseline();hash=SoundPolicyMount.digest(xml);original=SoundPolicy.entries(xml);
            SoundPolicy.verify(original,original.keySet(),SoundNative::attribute);
        }finally{initializing.cancel(false);}
        Set<String> packages=Set.of(),tracked=new LinkedHashSet<>(),baseline=new LinkedHashSet<>();
        SoundPolicy.Patch patch=null;
        Map<String,Integer> expected=Map.of();
        long refresh=0,nextCheck=0;
        String sentinel=null;
        while(lease(dir,apk)){
            long now=SystemClock.elapsedRealtime();
            var alarm=deadline(28);
            try{
                if(now>=refresh){
                    if(new File(ONLINE).exists())throw new IOException("Online audio policy changed");
                    Set<String> current=installed();
                    if(!current.equals(packages)){
                        patch=SoundPolicy.expand(xml,current);
                        for(String pkg:patch.expanded())if(!tracked.contains(pkg)&&original.containsKey(pkg)&&SoundNative.enabled(pkg))baseline.add(pkg);
                        tracked.addAll(patch.expanded());
                        // Independently verify every affected package after transitions, not on each heartbeat.
                        atomic(new File(dir,PLAN),new SoundRecoveryPlan(hash,tracked,baseline).encode());
                        atomic(new File(dir,ACTIVE),String.join("\n",baseline));
                        atomic(new File(dir,CHANGED),String.join("\n",tracked));
                        apply(dir,classpath,patch,hash);
                        expected=SoundPolicy.entries(patch.xml());
                        SoundPolicy.verify(expected,tracked,SoundNative::attribute);
                        sentinel=patch.expanded().contains(PACKAGE)?PACKAGE:patch.expanded().stream().findFirst().orElse(null);
                        packages=current;
                    }
                    refresh=now+60_000;
                }
                if(now>=nextCheck&&sentinel!=null){
                    if(!SoundPolicy.restoredAttribute(SoundNative.attribute(sentinel),expected.get(sentinel))){
                        apply(dir,classpath,patch,hash);
                        SoundPolicy.verify(expected,tracked,SoundNative::attribute);
                    }
                    nextCheck=now+10_000;
                }
                status(dir,"已生效 · 原生声音分轨应用限制已解除");
            }finally{alarm.cancel(false);}
            Thread.sleep(1000);
        }
    }
    private static File directory(String input)throws IOException{
        File dir=new File(input);
        if(!DIRECTORY.equals(input)||!DIRECTORY.equals(dir.getCanonicalPath()))throw new IOException("Invalid audio state directory");
        return dir;
    }
    public static void main(String[] args)throws Exception{
        if(args.length==7&&args[0].equals("mount-worker")){
            var alarm=deadline(13);
            try{SoundPolicyMount.execute(args[1],directory(args[2]),new SoundPolicyMount.Service(Integer.parseInt(args[3]),args[4]),args[5],args[6]);}
            catch(Exception e){System.err.println("Audio policy error: "+e.getMessage());throw e;}
            finally{alarm.cancel(false);}
            return;
        }
        if((args.length!=3&&args.length!=4)||!(args[0].equals("daemon")||args[0].equals("restore")))throw new IllegalArgumentException("Invalid audio companion arguments");
        File dir=directory(args[1]);
        String apk=args[2],classpath=SoundCompanionCommand.classpath(System.getenv("CLASSPATH"),apk);
        if(!new File(apk).getCanonicalPath().startsWith("/data/app/")||!apk.endsWith("/base.apk")||!new File(classpath).isFile())
            throw new IOException("Invalid audio companion code");
        try(FileChannel channel=lockChannel(dir,"lock");FileLock lock=channel.tryLock()){
            if(lock==null)return;
            if(args[0].equals("restore")){
                try{restore(dir,classpath);}catch(Exception e){status(dir,"恢复失败，请检查声音策略状态："+e.getMessage());throw e;}
                if(args.length==4&&!args[3].equals("0")){
                    Path report=dir.toPath().resolve("status");
                    String prior=Files.isRegularFile(report,LinkOption.NOFOLLOW_LINKS)
                            ?SoundStatusPolicy.current(Files.readAllLines(report),SystemClock.elapsedRealtime(),System.currentTimeMillis(),BuildConfig.VERSION_CODE):null;
                    if(prior==null||prior.startsWith("已"))
                        status(dir,"未生效，声音守护异常退出（"+args[3]+"），已完成恢复检查");
                }
                return;
            }
            if(!lease(dir,apk))return;
            Exception activationError=null;
            try {daemon(dir,apk,classpath);}catch(Exception error){activationError=error;}
            try{restore(dir,classpath);}catch(Exception recovery){status(dir,"恢复失败，请检查声音策略状态："+recovery.getMessage());throw recovery;}
            if(activationError!=null){
                status(dir,"未生效，声音策略已退出："+activationError.getMessage());
                throw activationError;
            }
        }
    }
}
