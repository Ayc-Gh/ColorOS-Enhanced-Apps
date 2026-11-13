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
}
