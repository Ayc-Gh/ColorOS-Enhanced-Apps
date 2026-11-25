// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SoundRecoveryPlanTest {
    private static final String HASH="a".repeat(64);
    @Test public void dirtyAndAlreadyActiveAppsRoundTripAsOneRecord()throws Exception{
        var plan=new SoundRecoveryPlan(HASH,Set.of("org.video","org.nativeapp"),Set.of("org.nativeapp"));
        assertEquals(plan,SoundRecoveryPlan.parse(plan.encode().lines().toList()));
        assertEquals(new SoundRecoveryPlan(HASH,Set.of(),Set.of()),SoundRecoveryPlan.parse(List.of("v1 "+HASH)));
    }
    @Test public void rejectsCorruptOrContradictoryBaseline(){
        assertThrows(IOException.class,()->SoundRecoveryPlan.parse(List.of("v1 "+HASH,"0 org.video","1 org.video")));
        assertThrows(IOException.class,()->SoundRecoveryPlan.parse(List.of("v1 "+HASH,"2 org.video")));
        assertThrows(IllegalArgumentException.class,()->new SoundRecoveryPlan(HASH,Set.of("org.video"),Set.of("org.other")));
    }
    @Test public void checksEveryNativeAndExpandedPackageOnce()throws Exception{
        var original=Map.of("org.unchanged",3,"org.nativeapp",17);
        var plan=new SoundRecoveryPlan(HASH,Set.of("org.added1","org.added2","org.nativeapp"),Set.of());
        Set<String> queried=new HashSet<>();
        plan.verifyRestored(original,pkg->{assertTrue(queried.add(pkg));return original.containsKey(pkg)?original.get(pkg).toString():null;});
        assertEquals(Set.of("org.unchanged","org.nativeapp","org.added1","org.added2"),queried);
        assertThrows(IOException.class,()->plan.verifyRestored(original,pkg->pkg.equals("org.nativeapp")?null:
            original.containsKey(pkg)?original.get(pkg).toString():null));
        assertThrows(IOException.class,()->plan.verifyRestored(original,pkg->pkg.equals("org.added2")?"3":
            original.containsKey(pkg)?original.get(pkg).toString():null));
    }
}
