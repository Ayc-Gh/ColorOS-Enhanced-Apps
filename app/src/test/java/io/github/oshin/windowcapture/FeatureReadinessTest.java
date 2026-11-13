package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FeatureReadinessTest {
    @Test public void missingAlternativeIsNotARequiredFailure(){assertTrue(FeatureReadiness.ready(Map.of("primary",true,"oldVersion",false),Set.of("primary")));}
    @Test public void partialInstallationCannotChangeBehavior(){assertFalse(FeatureReadiness.ready(Map.of("primary",false,"optional",true),Set.of("primary")));assertFalse(FeatureReadiness.ready(Map.of(),Set.of()));}
}
