// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** A failed preference bind is retried; business hook installation runs once after it succeeds. */
final class WindowConfigConnection {
    private boolean connected;
    private long delayMs = 1000;
    synchronized void connect(Runnable bind, Runnable install) {
        if (connected) return;
        bind.run();
        connected = true;
        install.run();
    }
    synchronized long retryDelayMs() {
        long delay = delayMs;
        delayMs = Math.min(60_000, delay * 2);
        return delay;
    }
}
