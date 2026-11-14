package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class GalleryPolicyTest {
    @Test public void onlyMissingRetouchCapabilityCanBeExpanded(){
        assertTrue(GalleryPolicy.unlock(true,"feature_is_support_ai_retouch",false));
        assertFalse(GalleryPolicy.unlock(false,"feature_is_support_ai_retouch",false));
        assertFalse(GalleryPolicy.unlock(true,"feature_is_support_ai_retouch",true));
        for(String key:new String[]{null,"feature_ai_retouch","feature_is_support_ai_retouch_quota_exhausted_hint","authorize_cloud_editor","feature_is_support_ai_composition"})
            assertFalse(GalleryPolicy.unlock(true,key,false));
    }
}
