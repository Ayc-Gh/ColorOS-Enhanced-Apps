// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SoundPolicyMountTest {
    private static final String BOOT="12345678-1234-1234-1234-123456789abc",NS="mnt:[40001]",INO="123:456",HASH="a".repeat(64);
    private static String mount(long id,String target){return id+" 3 1:8 / "+target+" rw shared:10 - ext4 /dev/block/dm-2 ro";}
    @Test public void onlyExactMountpointsCountAndSharedParentsAreRetained()throws Exception{
        String parent=mount(3,"/system_ext");
        assertEquals(0,SoundPolicyMount.mountId(parent));
        assertEquals(0,SoundPolicyMount.mountId(parent+"\n"+mount(21,SoundPolicyMount.TARGET+".other")));
        assertEquals(21,SoundPolicyMount.mountId(parent+"\n"+mount(21,SoundPolicyMount.TARGET)));
    }
    @Test public void refusesStackedAndInvalidMountIds(){
        assertThrows(IOException.class,()->SoundPolicyMount.mountId(mount(19,SoundPolicyMount.TARGET)+"\n"+mount(20,SoundPolicyMount.TARGET)));
        assertThrows(IOException.class,()->SoundPolicyMount.mountId(mount(0,SoundPolicyMount.TARGET)));
    }
    @Test public void validatesBootNamespaceStagedInodeAndRecordedMount()throws Exception{
        var journal=new SoundPolicyMount.Journal(BOOT,NS,INO,HASH,11);
        assertEquals(journal,SoundPolicyMount.Journal.parse(journal.encode().lines().toList()));
        assertTrue(journal.owns(BOOT,NS,11,INO));
        assertFalse(journal.owns(BOOT+"x",NS,11,INO));
        assertFalse(journal.owns(BOOT,"mnt:[40002]",11,INO));
        assertFalse(journal.owns(BOOT,NS,12,INO));
        assertFalse(journal.owns(BOOT,NS,11,"123:777"));
    }
    @Test public void interruptedBindNeedsPositiveMountIdAndOriginalStagingInode(){
        var pending=new SoundPolicyMount.Journal(BOOT,NS,INO,HASH,0);
        assertTrue(pending.owns(BOOT,NS,12,INO));
        assertFalse(pending.owns(BOOT,NS,0,INO));
        assertFalse(pending.owns(BOOT,NS,12,"123:999"));
        assertThrows(IOException.class,()->SoundPolicyMount.Journal.parse(List.of(NS,INO,HASH)));
    }
}
