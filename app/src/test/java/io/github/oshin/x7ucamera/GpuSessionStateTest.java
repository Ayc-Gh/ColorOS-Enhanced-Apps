// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import org.junit.Test;

import static org.junit.Assert.*;

public final class GpuSessionStateTest {
    @Test public void preparingWithoutSubmittingDoesNotRetainASurface() {
        var route = new GpuSessionState();
        route.prepare();
        assertNull(route.ready());
        route.retire();
        assertTrue(route.canDispose());
        assertNull(route.submit());
    }

    @Test public void synchronousRejectionReleasesItsSubmissionExactlyOnce() {
        var route = new GpuSessionState();
        var rejected = route.submit();
        assertNull(route.ready());
        assertTrue(route.finish(rejected));
        assertFalse(route.finish(rejected));
        route.configured(rejected);
        assertNull(route.ready());
        route.retire();
        assertTrue(route.canDispose());
    }

    @Test public void staleCallbacksCannotAuthorizeOrInvalidateANewerCapture() {
        var route = new GpuSessionState();
        var first = route.submit();
        route.configured(first);
        assertSame(first, route.ready());
        var second = route.submit();
        route.configured(first);
        assertNull(route.ready());
        route.configured(second);
        assertFalse(route.finish(first));
        assertSame(second, route.ready());
        assertTrue(route.finish(second));
        assertNull(route.ready());
    }

    @Test public void replacementAndRetirementWaitForTheActualClosingCallback() {
        var route = new GpuSessionState();
        var active = route.submit();
        route.configured(active);
        route.supersede(active);
        assertNull(route.ready());
        route.retire();
        assertFalse(route.canDispose());
        assertFalse(route.finish(active));
        assertTrue(route.canDispose());
    }
}
