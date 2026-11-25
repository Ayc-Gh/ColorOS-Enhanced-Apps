// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

final class WindowParkedTasks {
    static final class Entry {
        final int id;
        final Object task;
        private boolean queued;
        private long delayMs = 200;
        Entry(int id, Object task) { this.id = id; this.task = task; }
    }
    private final ConcurrentHashMap<Integer, Entry> entries = new ConcurrentHashMap<>();
    Entry get(int id) { return entries.get(id); }
    List<Entry> snapshot() { return List.copyOf(entries.values()); }
    boolean current(Entry entry) { return entry != null && entries.get(entry.id) == entry; }
    void park(int id, Object task) { entries.put(id, new Entry(id, task)); }
    void finish(Entry entry) { entries.remove(entry.id, entry); }
    void retry(Entry entry, BiConsumer<Runnable, Long> schedule, Runnable attempt) {
        synchronized (entry) {
            if (!current(entry) || entry.queued) return;
            entry.queued = true;
            long delay = entry.delayMs;
            entry.delayMs = Math.min(5000, delay * 2);
            schedule.accept(() -> {
                synchronized (entry) { entry.queued = false; }
                if (current(entry)) attempt.run();
            }, delay);
        }
    }
}
