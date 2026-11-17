package io.github.oshin.windowcapture;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RetiredSettingsTest {
    @Test public void upgradeRemovesOldControlsAndPreservesCaptureAndOtherFeatures(){
        for(boolean capture:new boolean[]{false,true}){
            var saved=new HashMap<String,Object>(Map.of("capture",capture,"cast_enabled",true,
                    "cast_fps",120,"cast_bitrate_mbps",18,"free_ratio",true,"sound_mss_apps",true,
                    "window_count",5,"cast_future_unknown",7));
            RetiredSettings.presentKeys(saved.keySet()).forEach(saved::remove);
            assertEquals(Map.of("capture",capture,"sound_mss_apps",true,"window_count",5,"cast_future_unknown",7),saved);
            assertTrue(RetiredSettings.presentKeys(saved.keySet()).isEmpty());
        }
    }
    @Test public void cleanInstallDoesNotCreateSettings(){assertTrue(RetiredSettings.presentKeys(Set.of()).isEmpty());}
}
