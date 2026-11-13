package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompatibilityPolicyTest {
    @Test public void unknownFirmwareAndApplicationsKeepNativeBehavior(){assertTrue(CompatibilityPolicy.rom("PHY110",37,CompatibilityPolicy.ROM));assertFalse(CompatibilityPolicy.rom("PHY110",38,CompatibilityPolicy.ROM));assertFalse(CompatibilityPolicy.rom("PHY110",37,"new-ota"));assertTrue(CompatibilityPolicy.host("com.oplus.cast",170017));assertFalse(CompatibilityPolicy.host("com.oplus.cast",170018));assertFalse(CompatibilityPolicy.host("unknown",-1));}
}
