// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CameraFilterPolicyTest {
    private static final String BASE="blackandwhite_HC_sdr.bin",LOW="blackandwhite_HC_sdr_0.bin",HIGH="blackandwhite_HC_sdr_1.bin";
    private static final Set<String> LUTS=Set.of(BASE,LOW,HIGH);
    @Test public void lowerHalfInterpolatesLowToCenter(){assertArrayEquals(new String[]{LOW,BASE},CameraFilterPolicy.contrastLuts(BASE,25,LUTS));assertEquals(.5f,CameraFilterPolicy.factor(25),0f);}
    @Test public void centerSelectsTheCenterLutWithoutColorBlending(){assertArrayEquals(new String[]{BASE,HIGH},CameraFilterPolicy.contrastLuts(BASE,50,LUTS));assertEquals(0f,CameraFilterPolicy.factor(50),0f);}
    @Test public void upperHalfInterpolatesCenterToHigh(){assertArrayEquals(new String[]{BASE,HIGH},CameraFilterPolicy.contrastLuts(BASE,75,LUTS));assertEquals(.5f,CameraFilterPolicy.factor(75),0f);}
    @Test public void endpointsClampMalformedHostStrengths(){assertEquals(0f,CameraFilterPolicy.factor(-1),0f);assertEquals(1f,CameraFilterPolicy.factor(101),0f);assertArrayEquals(new String[]{LOW,BASE},CameraFilterPolicy.contrastLuts(BASE,-1,LUTS));}
    @Test public void incompleteOrUnrelatedLutsKeepNativeBehavior(){assertNull(CameraFilterPolicy.contrastLuts(BASE,50,Set.of(BASE,LOW)));assertNull(CameraFilterPolicy.contrastLuts("mono.cube.rgb.bin",50,LUTS));assertNull(CameraFilterPolicy.contrastLuts(null,50,LUTS));assertNull(CameraFilterPolicy.contrastLuts(LOW,50,LUTS));}
    @Test public void contrastUsesBothSidesOfMidpointContinuously(){assertEquals(.98f,CameraFilterPolicy.factor(49),.00001f);assertEquals(.02f,CameraFilterPolicy.factor(51),.00001f);}
}
