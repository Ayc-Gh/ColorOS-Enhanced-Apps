// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Keeps OEM eviction valid while an expanded list returns to a lower limit. */
final class EdgeCountPolicy {
    private boolean applied;

    static boolean configured(int value) {
        return value>=1&&value<=Config.MAX_EDGE_COUNT;
    }

    synchronized boolean needsSize(int value) {
        return configured(value)||applied;
    }

    synchronized int limit(int value,int nativeLimit,int currentSize) {
        if(!configured(value)&&!applied)return nativeLimit;
        if(currentSize<0)return nativeLimit;
        if(configured(value)) {
            applied=true;
            return Math.max(value,currentSize);
        }
        // The OEM add path handles size == limit and size < limit only. Do not
        // return a smaller limit until existing extra handles have been closed.
        if(currentSize<=nativeLimit)applied=false;
        return Math.max(nativeLimit,currentSize);
    }
}
