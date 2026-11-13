package io.github.oshin.windowcapture;

import org.junit.Test;
import static org.junit.Assert.*;

public class EdgeCountPolicyTest {
    @Test public void defaultLeavesNativeLimitAlone() {
        EdgeCountPolicy policy=new EdgeCountPolicy();
        assertFalse(policy.needsSize(-1));
        assertEquals(2,policy.limit(-1,2,0));
        assertEquals(5,policy.limit(-1,5,4));
        assertEquals(2,policy.limit(-1,2,4));
    }

    @Test public void expandedLimitAllowsThirdAndFifthHandles() {
        EdgeCountPolicy policy=new EdgeCountPolicy();
        assertTrue(policy.needsSize(5));
        assertEquals(5,policy.limit(5,2,2));
        assertEquals(5,policy.limit(5,2,4));
        assertEquals(5,policy.limit(5,2,5));
    }

    @Test public void lowerLimitPreservesExistingHandlesUntilTheyClose() {
        EdgeCountPolicy policy=new EdgeCountPolicy();
        assertEquals(5,policy.limit(5,2,4));
        assertEquals(4,policy.limit(3,2,4));
        assertEquals(3,policy.limit(3,2,3));
        assertEquals(3,policy.limit(3,2,2));
    }

    @Test public void restoringDefaultDrainsExtraHandlesThenStopsOverriding() {
        EdgeCountPolicy policy=new EdgeCountPolicy();
        assertEquals(5,policy.limit(5,2,4));
        assertEquals(4,policy.limit(-1,2,4));
        assertTrue(policy.needsSize(-1));
        assertEquals(3,policy.limit(-1,2,3));
        assertEquals(2,policy.limit(-1,2,2));
        assertFalse(policy.needsSize(-1));
        assertEquals(5,policy.limit(-1,5,0));
    }

    @Test public void invalidValuesAndUnavailableCountKeepNativeBehavior() {
        EdgeCountPolicy policy=new EdgeCountPolicy();
        for(int value:new int[]{Integer.MIN_VALUE,-1,0,6,Integer.MAX_VALUE}) {
            assertFalse(policy.needsSize(value));
            assertEquals(2,policy.limit(value,2,0));
        }
        assertEquals(2,policy.limit(5,2,-1));
        assertFalse(policy.needsSize(-1));
    }

    @Test public void resetIncludesIndependentEdgePreference() {
        assertNotEquals(Config.COUNT,Config.EDGE_COUNT);
        assertTrue(java.util.Arrays.asList(Config.WINDOW_KEYS).contains(Config.EDGE_COUNT));
    }
}
