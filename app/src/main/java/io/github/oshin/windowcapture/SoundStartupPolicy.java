// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Only the audio host may bootstrap this module's leased sound provider. */
final class SoundStartupPolicy {
    static final String APP="io.github.oshin.windowcapture";
    static final String PROVIDER=APP+".SoundBridgeProvider";
    private SoundStartupPolicy(){}
    static boolean caller(boolean enabled,int callingUid,int processUid,String process,
                          String caller,String target,String component,int targetUid,boolean stopped){
        return enabled&&!stopped&&callingUid==1000&&processUid==1000&&SoundPolicy.PROCESS.equals(process)
                &&(SoundPolicy.HOST.equals(caller)||"com.oplus.atlas".equals(caller))
                &&APP.equals(target)&&PROVIDER.equals(component)&&targetUid>=10000&&targetUid<20000;
    }
    static boolean association(boolean enabled,int type,String caller,String target,String component,
                               String process,int user,int uid,String scopedCaller,int scopedUid){
        return enabled&&type==64&&user==0&&uid==scopedUid&&caller!=null&&caller.equals(scopedCaller)
                &&APP.equals(target)&&PROVIDER.equals(component)&&APP.equals(process);
    }
}
