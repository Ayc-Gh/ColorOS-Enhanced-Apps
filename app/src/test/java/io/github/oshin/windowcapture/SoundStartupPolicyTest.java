package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class SoundStartupPolicyTest {
 private boolean caller(boolean on,int uid,String process,String source,String target,String component,int targetUid,boolean stopped){
  return SoundStartupPolicy.caller(on,uid,uid,process,source,target,component,targetUid,stopped);
 }
 @Test public void acceptsOnlyTheNativeAudioCallerAndModuleProvider(){
  assertTrue(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertTrue(caller(true,1000,SoundPolicy.PROCESS,"com.oplus.atlas",SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,10001,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,1000,"system_server",SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,"com.android.settings",SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,"another.app",SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,"another.Provider",10356,false));
 }
 @Test public void disabledStoppedAndOtherUsersRetainNativeStartupRules(){
  assertFalse(caller(false,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,false));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,10356,true));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,110356,false));
  assertFalse(caller(true,1000,SoundPolicy.PROCESS,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,1000,false));
 }
 @Test public void associationRequiresTheSameProviderScopeAndIdentity(){
  assertTrue(allow(true,64,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,0,10356));
  assertFalse(allow(false,64,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,0,10356));
  assertFalse(allow(true,128,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,0,10356));
  assertFalse(allow(true,64,"com.oplus.atlas",SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,0,10356));
  assertFalse(allow(true,64,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,"another.process",0,10356));
  assertFalse(allow(true,64,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,10,10356));
  assertFalse(allow(true,64,SoundPolicy.HOST,SoundStartupPolicy.APP,SoundStartupPolicy.PROVIDER,SoundStartupPolicy.APP,0,10357));
 }
 private boolean allow(boolean enabled,int type,String caller,String target,String component,String process,int user,int uid){
  return SoundStartupPolicy.association(enabled,type,caller,target,component,process,user,uid,SoundPolicy.HOST,10356);
 }
}
