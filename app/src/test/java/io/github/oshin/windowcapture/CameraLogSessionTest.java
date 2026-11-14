// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CameraLogSessionTest {
 @Test public void logRemainsUsableWithoutPersistingHiddenFormat(){var s=new CameraLogSession();assertEquals("sdr",s.write("rear-format",CameraLogSession.FORMAT,"log",true));assertEquals("log",s.read("rear-format",CameraLogSession.FORMAT,"sdr",true));assertEquals("off",s.write("rear-log",CameraLogSession.ENABLE,"on",true));assertEquals("on",s.read("rear-log",CameraLogSession.ENABLE,"off",true));}
 @Test public void disabledOrNewProcessReadsNativeFormat(){var s=new CameraLogSession();s.write("rear",CameraLogSession.FORMAT,"log",true);assertEquals("sdr",s.read("rear",CameraLogSession.FORMAT,"sdr",false));s.clear();assertEquals("sdr",s.read("rear",CameraLogSession.FORMAT,"sdr",true));assertEquals("sdr",new CameraLogSession().read("rear",CameraLogSession.FORMAT,"sdr",true));}
 @Test public void keepsCamerasAndNativeFormatsIndependent(){var s=new CameraLogSession();s.write("rear0",CameraLogSession.FORMAT,"log",true);assertEquals("sdr",s.read("rear1",CameraLogSession.FORMAT,"sdr",true));assertEquals("hdr",s.write("rear0",CameraLogSession.FORMAT,"hdr",true));assertEquals("hdr",s.read("rear0",CameraLogSession.FORMAT,"hdr",true));assertEquals("unchanged",CameraLogSession.nativeValue("unrelated","unchanged"));assertFalse(CameraLogSession.targets("pref_video_fps_key"));}
 @Test public void staleCandidateValueCannotLeakThroughDisabledRead(){var s=new CameraLogSession();assertEquals("sdr",s.read("rear",CameraLogSession.FORMAT,"log",false));assertEquals("off",s.read("rear",CameraLogSession.ENABLE,"on",false));}
}
