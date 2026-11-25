// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.List;

/** Status persistence cadence is independent of the one-second lease-expiry check. */
final class SoundStatusPolicy {
    static final long HEARTBEAT_MS=10_000, FRESH_MS=15_000;
    private static final long MAX_CLOCK_DRIFT_MS=2_000, ERROR_LIFETIME_MS=60_000;
    static boolean fresh(long now,long stamp,int code,int expected){return code==expected&&stamp>=0&&now>=stamp&&now-stamp<=FRESH_MS;}
    static boolean write(long now,long last,String previous,String current){return !current.equals(previous)||last<0||now<last||now-last>=HEARTBEAT_MS;}

    static String current(List<String> lines, long elapsed, long wall, int version) {
        // Elapsed time alone can match an old boot once the device reaches the same uptime.
        if (lines.size() != 4) return null;
        try {
            long stamp = Long.parseLong(lines.get(0));
            long wallStamp = Long.parseLong(lines.get(3));
            long age = elapsed - stamp;
            int code = Integer.parseInt(lines.get(2));
            if (code != version || stamp < 0 || stamp > elapsed
                    || wallStamp < 0 || wallStamp > wall || Math.abs((wall - wallStamp) - age) > MAX_CLOCK_DRIFT_MS) return null;
            String value = lines.get(1);
            if (value.isBlank() || (value.startsWith("已生效") && !fresh(elapsed, stamp, code, version))) return null;
            if (!value.startsWith("已恢复") && !value.startsWith("已生效") && age > ERROR_LIFETIME_MS) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static final class Retry {
        private long due, delay;
        boolean ready(long now) { return now >= due; }
        void failed(long now) {
            delay = delay == 0 ? HEARTBEAT_MS : Math.min(delay * 2, 60_000);
            due = now + delay;
        }
        void reset() { due = delay = 0; }
    }

    static String describe(boolean enabled, String current, boolean pendingRestore) {
        if (enabled && (current == null || current.startsWith("已恢复")
                || (current.startsWith("已生效") && !pendingRestore))) return "开关已开启，正在等待声音组件连接";
        if (current == null) return pendingRestore ? "开关已关闭，等待原生状态确认" : "声音增强已关闭";
        if (!enabled && (current.startsWith("已生效") || (current.startsWith("已恢复") && pendingRestore))) return "开关已关闭，等待原生状态确认";
        return current;
    }
    private SoundStatusPolicy(){}
}
