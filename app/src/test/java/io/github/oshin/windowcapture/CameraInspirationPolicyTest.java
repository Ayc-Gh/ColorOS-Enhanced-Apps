package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CameraInspirationPolicyTest {
 @Test public void recommendationIsIndependentFromLocalComposition(){assertEquals("com.oplus.camera",CameraInspirationPolicy.HOST);assertNotEquals(CameraInspirationPolicy.KEY,"feature_is_support_ai_composition");}
}
