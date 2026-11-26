// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

/** Tracks generations of one substituted camera output under the routes lock. */
final class GpuSessionState {
    static final class Attempt {
        private boolean configured;
        private boolean finished;
    }

    private Attempt current;
    private int submissions;
    private boolean retired;

    void prepare() {
        current = null;
        retired = false;
    }

    Attempt submit() {
        if (retired) return null;
        current = new Attempt();
        submissions++;
        return current;
    }

    void configured(Attempt attempt) {
        if (current == attempt) attempt.configured = true;
    }

    Attempt ready() { return current != null && current.configured ? current : null; }

    void supersede(Attempt attempt) {
        if (current == attempt) current = null;
    }

    boolean finish(Attempt attempt) {
        if (attempt.finished) return false;
        attempt.finished = true;
        submissions--;
        boolean wasCurrent = current == attempt;
        supersede(attempt);
        return wasCurrent;
    }

    void retire() {
        retired = true;
        current = null;
    }

    boolean retired() { return retired; }
    boolean canDispose() { return retired && submissions == 0; }
}
