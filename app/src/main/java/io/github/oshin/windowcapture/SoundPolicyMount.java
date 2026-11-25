// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.List;

/** A short-lived worker in the verified policy service's mount namespace. */
final class SoundPolicyMount {
    static final String TARGET="/system_ext/etc/Multimedia_Daemon_List.xml";
    static final String SOURCE="runtime-policy.xml", JOURNAL="runtime-mount";
    private static final String BOOT="/proc/sys/kernel/random/boot_id";
    private static final String SERVICE="/system_ext/bin/mmlistservice";
    private static final String MOUNTINFO="/proc/self/mountinfo";

    record Service(int pid,String namespace) {
        static Service current() throws Exception {
            String result=SoundPolicyRoot.run("/system/bin/pidof","mmlistservice").trim();
            if(!result.matches("[1-9][0-9]*"))throw new IOException("Audio policy service unavailable");
            int pid=Integer.parseInt(result);
            Service service=new Service(pid,Os.readlink("/proc/"+pid+"/ns/mnt"));
            service.verify();
            return service;
        }
        void verify() throws Exception {
            if(!SERVICE.equals(Os.readlink("/proc/"+pid+"/exe"))||
                    !namespace.equals(Os.readlink("/proc/"+pid+"/ns/mnt")))
                throw new IOException("Audio policy service changed");
        }
        String baseline() throws Exception {
            verify();
            if(mountId(SoundPolicyRoot.readText(Paths.get("/proc/"+pid+"/mountinfo")))!=0)
                throw new IOException("Audio policy already has a file mount");
            Path policy=Paths.get("/proc/"+pid+"/root"+TARGET);
            String identity=inode(policy),xml=SoundPolicyRoot.readText(policy);
            if(!identity.equals(inode(policy))||mountId(SoundPolicyRoot.readText(Paths.get("/proc/"+pid+"/mountinfo")))!=0)
                throw new IOException("Audio policy changed during baseline read");
            verify();
            return xml;
        }
        void worker(String operation,File dir,String classpath,String baselineHash,String sourceInode) throws Exception {
            verify();
            ProcessBuilder command=new ProcessBuilder("/system/bin/nsenter","-t",Integer.toString(pid),"-m","--",
                    "/system/bin/app_process","/system/bin",SoundPolicyRoot.ROOT_CLASS,"mount-worker",
                    operation,dir.toString(),Integer.toString(pid),namespace,baselineHash,sourceInode);
            command.environment().put("CLASSPATH",classpath);
            SoundPolicyRoot.run(command);
        }
    }

    /** Zero means uncovered. Multiple mounts must never be dismantled by this module. */
    static long mountId(String mountinfo) throws IOException {
        long found=0;
        for(String line:mountinfo.split("\n")){
            String[] fields=line.split(" ",7);
            if(fields.length<6||!TARGET.equals(fields[4]))continue;
            if(found!=0)throw new IOException("Audio policy has stacked mounts");
            try{found=Long.parseLong(fields[0]);}catch(NumberFormatException e){throw new IOException("Invalid mount ID",e);}
            if(found<=0)throw new IOException("Invalid mount ID");
        }
        return found;
    }

    record Journal(String boot,String namespace,String inode,String baseline,long mountId) {
        String encode(){return String.join("\n",boot,namespace,inode,baseline,Long.toString(mountId));}
        static Journal parse(List<String> lines) throws IOException {
            if(lines.size()!=5||!lines.get(0).matches("[0-9a-f-]{36}")||!lines.get(1).matches("mnt:\\[[0-9]+\\]")
                    ||!lines.get(2).matches("[0-9]+:[0-9]+")||!lines.get(3).matches("[0-9a-f]{64}")
                    ||!lines.get(4).matches("[0-9]{1,18}"))throw new IOException("Incomplete audio mount record");
            return new Journal(lines.get(0),lines.get(1),lines.get(2),lines.get(3),Long.parseLong(lines.get(4)));
        }
        boolean owns(String currentBoot,String currentNamespace,long currentId,String currentInode){
            return currentId>0&&boot.equals(currentBoot)&&namespace.equals(currentNamespace)&&inode.equals(currentInode)
                    &&(mountId==0||mountId==currentId);
        }
    }

    static String digest(String value) throws Exception {
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder(64);
        for(byte b:bytes)hex.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
        return hex.toString();
    }
    static String inode(Path path) throws Exception {
        var st=Os.lstat(path.toString());
        if(!OsConstants.S_ISREG(st.st_mode))throw new IOException("Unexpected audio policy file type");
        return st.st_dev+":"+st.st_ino;
    }
    private static long visibleId()throws IOException{return mountId(SoundPolicyRoot.readText(Paths.get(MOUNTINFO)));}
    private static FileLock acquire(FileChannel channel,boolean recovering)throws Exception{
        long end=android.os.SystemClock.elapsedRealtime()+(recovering?8_000:0);
        for(;;){
            FileLock lock=channel.tryLock();
            if(lock!=null)return lock;
            if(android.os.SystemClock.elapsedRealtime()>=end)throw new IOException("Previous audio mount worker is still running");
            Thread.sleep(100);
        }
    }

    static void execute(String operation,File dir,Service service,String baselineHash,String sourceInode) throws Exception {
        service.verify();
        if(!service.namespace.equals(Os.readlink("/proc/self/ns/mnt")))throw new IOException("Wrong audio mount namespace");
        Path source=dir.toPath().resolve(SOURCE),journal=dir.toPath().resolve(JOURNAL);
        try(FileChannel channel=SoundPolicyRoot.lockChannel(dir,"mount-lock");FileLock lock=acquire(channel,"cleanup".equals(operation))){
            String boot=SoundPolicyRoot.readText(Paths.get(BOOT)).trim();
            if("cleanup".equals(operation)){
                cleanup(source,journal,boot,service);
                return;
            }
            if(!"apply".equals(operation)||!baselineHash.matches("[0-9a-f]{64}"))throw new IOException("Invalid audio mount operation");
            if(Files.exists(journal,LinkOption.NOFOLLOW_LINKS)||visibleId()!=0)throw new IOException("Existing audio mount must be retained");
            if(!baselineHash.equals(digest(SoundPolicyRoot.readText(Paths.get(TARGET)))))throw new IOException("Audio baseline changed before reload");
            String identity=inode(source);
            var st=Os.lstat(source.toString());
            if(!identity.equals(sourceInode)||st.st_uid!=0||st.st_nlink!=1||(st.st_mode&0222)!=0)
                throw new IOException("Unsafe audio staging file");
            Journal pending=new Journal(boot,service.namespace,identity,baselineHash,0);
            SoundPolicyRoot.atomic(journal.toFile(),pending.encode());
            try {
                service.verify();
                if(visibleId()!=0)throw new IOException("Another audio overlay appeared");
                SoundPolicyRoot.run("/system/bin/mount","--bind",source.toString(),TARGET);
                long id=visibleId();
                if(!pending.owns(boot,service.namespace,id,inode(Paths.get(TARGET))))throw new IOException("Cannot identify new audio mount");
                SoundPolicyRoot.atomic(journal.toFile(),new Journal(boot,service.namespace,identity,baselineHash,id).encode());
                service.verify();
                SoundNative.reload();
            } finally {
                cleanup(source,journal,boot,service);
            }
        }
    }

    private static void cleanup(Path source,Path journal,String boot,Service service) throws Exception {
        long id=visibleId();
        if(!Files.exists(journal,LinkOption.NOFOLLOW_LINKS)){
            if(id!=0)throw new IOException("An external audio mount is active");
            // A daemon may have stopped after staging, before the worker recorded intent.
            if(Files.exists(source,LinkOption.NOFOLLOW_LINKS)){SoundPolicyRoot.requireRegular(source);Files.delete(source);}
            return;
        }
        SoundPolicyRoot.requireRegular(journal);
        Journal record=Journal.parse(Files.readAllLines(journal));
        if(id!=0){
            if(!record.owns(boot,service.namespace,id,inode(Paths.get(TARGET))))throw new IOException("External or ambiguous audio mount retained");
            // Linux umount takes a path and has no compare-by-mount-ID operation. This
            // rejects observed conflicts; another privileged writer can still race the final lookup.
            if(visibleId()!=id||!record.inode.equals(inode(Paths.get(TARGET))))throw new IOException("Audio mount changed during cleanup");
            SoundPolicyRoot.run("/system/bin/umount",TARGET);
            if(visibleId()!=0)throw new IOException("Audio mount cleanup not confirmed");
        } else if(record.boot.equals(boot)&&!record.namespace.equals(service.namespace)){
            throw new IOException("Audio mount recovery belongs to another namespace");
        }
        if(record.boot.equals(boot)&&!record.baseline.equals(digest(SoundPolicyRoot.readText(Paths.get(TARGET)))))
            throw new IOException("Audio baseline was not restored");
        if(Files.exists(source,LinkOption.NOFOLLOW_LINKS)){
            if(!record.inode.equals(inode(source)))throw new IOException("Audio staging file identity changed");
            Files.delete(source);
        }
        Files.delete(journal);
    }
    private SoundPolicyMount(){}
}
