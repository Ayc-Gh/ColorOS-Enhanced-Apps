package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class NetworkPolicyTest {
    @Test public void disabledKeepsNative(){assertFalse(NetworkPolicy.eligible(false,10085,true));}
    @Test public void internetSystemAppCanBeManaged(){assertTrue(NetworkPolicy.eligible(true,10085,true));}
    @Test public void noInternetIsNotForged(){assertFalse(NetworkPolicy.eligible(true,10123,false));}
    @Test public void coreUidIsNotMisrepresentedAsOneApp(){assertFalse(NetworkPolicy.eligible(true,1000,true));}
    @Test public void isolatedUidIsNotAnApp(){assertFalse(NetworkPolicy.eligible(true,99000,true));}
    @Test public void secondaryUserAppIdentityPreserved(){assertTrue(NetworkPolicy.eligible(true,1010085,true));}
    @Test public void classifiersAreLimitedToTheCurrentScreenCall()throws Throwable{
        var screen=new NetworkPolicy.ScreenScope();
        assertFalse(screen.options());
        screen.during(false,()->{
            assertTrue(screen.options());assertFalse(screen.dataApps());
            screen.during(true,()->{assertTrue(screen.options());assertTrue(screen.dataApps());return null;});
            assertFalse(screen.dataApps());
            var other=new java.util.concurrent.FutureTask<Boolean>(screen::options);
            new Thread(other).start();assertFalse(other.get());
            return null;
        });
        assertFalse(screen.options());assertFalse(screen.dataApps());
    }
    @Test public void failedScreenCallLeavesRestoreAndBackgroundCallersNative()throws Throwable{
        var screen=new NetworkPolicy.ScreenScope();
        try{screen.during(true,()->{throw new IllegalStateException();});fail();}
        catch(IllegalStateException expected){assertFalse(screen.options());assertFalse(screen.dataApps());}
    }
}
