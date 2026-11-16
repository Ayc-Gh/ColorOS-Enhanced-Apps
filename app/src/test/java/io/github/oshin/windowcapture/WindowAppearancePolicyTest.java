package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowAppearancePolicyTest {
    @Test public void onlyOuterCaptionedStandardFlexibleTasksQualify(){assertTrue(WindowAppearancePolicy.applies(1,1,false,true));assertFalse(WindowAppearancePolicy.applies(1,1,true,true));assertFalse(WindowAppearancePolicy.applies(1,2,false,true));assertFalse(WindowAppearancePolicy.applies(2,1,false,true));assertFalse(WindowAppearancePolicy.applies(1,1,false,false));}
    @Test public void offPreservesNativeAndZeroIsRealOverride(){assertEquals(46,WindowAppearancePolicy.radius(-1,46));assertEquals(0,WindowAppearancePolicy.radius(0,46));assertEquals(120,WindowAppearancePolicy.radius(120,46));assertEquals(300,WindowAppearancePolicy.radius(Integer.MAX_VALUE,46));}
}
