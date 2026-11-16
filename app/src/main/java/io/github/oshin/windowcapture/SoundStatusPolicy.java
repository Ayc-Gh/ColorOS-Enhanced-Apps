// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Status persistence cadence is independent of the one-second lease-expiry check. */
final class SoundStatusPolicy {
    static final long HEARTBEAT_MS=10_000, FRESH_MS=15_000;
    static boolean fresh(long now,long stamp,int code,int expected){return code==expected&&stamp>=0&&now>=stamp&&now-stamp<=FRESH_MS;}
    static boolean write(long now,long last,String previous,String current){return !current.equals(previous)||last<0||now<last||now-last>=HEARTBEAT_MS;}
    private SoundStatusPolicy(){}
}
