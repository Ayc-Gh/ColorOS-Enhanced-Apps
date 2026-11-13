package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.oshin.windowcapture.LaunchPolicy.Kind.*;

public final class LaunchPolicyTest {
    @Test public void inferredSameAppTaskRemainsInternal(){assertTrue(LaunchPolicy.sameAppNavigation("app","app","app",false));}
    @Test public void desktopCallerIsNotInternalEvenWhenReusingTask(){assertFalse(LaunchPolicy.sameAppNavigation("app","app","com.android.launcher",false));}
    @Test public void notificationCallerIsNotInternalEvenWhenReusingTask(){assertFalse(LaunchPolicy.sameAppNavigation("app","app","com.android.systemui",false));}
    private LaunchPolicy.Origin origin(String caller,boolean home,boolean standard,boolean source,boolean same,boolean edge){return new LaunchPolicy.Origin(caller,home,standard,source,same,edge);}
    @Test public void desktopWithoutSourceActivityStillQualifies(){assertEquals(LAUNCHER,LaunchPolicy.choose(false,false,true,false,origin("com.android.launcher",false,false,false,false,false)));}
    @Test public void desktopDoesNotBecomeAnAppJump(){assertEquals(NATIVE,LaunchPolicy.choose(true,false,false,false,origin("com.android.launcher",true,false,false,false,false)));}
    @Test public void nativeHomeSourceIsRecognized(){assertEquals(LAUNCHER,LaunchPolicy.choose(false,false,true,false,origin("caller",true,false,true,false,false)));}
    @Test public void ordinaryNullSourceIsNotMistakenForANotification(){assertEquals(NATIVE,LaunchPolicy.choose(false,true,false,false,origin("shell",false,false,false,false,false)));}
    @Test public void notificationRequiresItsOwnSwitch(){var source=origin("com.android.systemui",false,true,false,false,false);assertEquals(NATIVE,LaunchPolicy.choose(true,false,false,false,source));assertEquals(NOTIFICATION,LaunchPolicy.choose(false,true,false,false,source));}
    @Test public void sameAppNavigationKeepsNativeOptions(){assertEquals(NATIVE,LaunchPolicy.choose(true,true,true,true,origin("app",false,true,true,true,true)));}
    @Test public void edgeRestoreWorksIndependentlyOfDesktopPreference(){assertEquals(REMEMBER,LaunchPolicy.choose(false,false,false,true,origin("com.android.launcher",false,false,false,false,true)));}
    @Test public void rememberDoesNotForceAnUnrecordedApp(){assertEquals(NATIVE,LaunchPolicy.choose(false,false,false,true,origin("com.android.launcher",false,false,false,false,false)));}
    @Test public void rememberedPositionTakesPriorityOverNewDesktopWindow(){assertEquals(REMEMBER,LaunchPolicy.choose(false,false,true,true,origin("com.android.launcher",true,false,true,false,true)));}
    @Test public void backgroundCallerDoesNotRestoreAnEdgeTask(){assertEquals(NATIVE,LaunchPolicy.choose(false,false,false,true,origin("background",false,false,false,false,true)));}
    @Test public void ordinaryCrossAppStartUsesJumpOnlyWhenSelected(){var source=origin("app",false,true,true,false,false);assertEquals(JUMP,LaunchPolicy.choose(true,false,false,false,source));assertEquals(NATIVE,LaunchPolicy.choose(false,false,false,false,source));}
}
