// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Caller identity also covers starts for which Android supplies no source Activity. */
final class LaunchPolicy {
    enum Kind { NATIVE, JUMP, NOTIFICATION, LAUNCHER, REMEMBER }
    record Origin(String caller, boolean home, boolean standardSource, boolean hasSource,
                  boolean sameAppInternal, boolean savedEdge) {}
    static boolean isLauncher(String caller) {
        return "com.android.launcher".equals(caller)||"com.android.launcher3".equals(caller)||"com.oplus.launcher".equals(caller);
    }
    static boolean sameAppNavigation(String target,String source,String caller,boolean home) {
        return target!=null&&target.equals(source)&&!home&&!isLauncher(caller)&&!"com.android.systemui".equals(caller);
    }
    static Kind choose(boolean jump, boolean notification, boolean launcher, boolean remember, Origin origin) {
        if(origin.sameAppInternal())return Kind.NATIVE;
        boolean desktop=origin.home()||isLauncher(origin.caller());
        boolean notice=!origin.hasSource()&&"com.android.systemui".equals(origin.caller());
        if(remember&&origin.savedEdge()&&(desktop||notice||origin.standardSource()))return Kind.REMEMBER;
        if(desktop)return launcher?Kind.LAUNCHER:Kind.NATIVE;
        if(notice)return notification?Kind.NOTIFICATION:Kind.NATIVE;
        return jump&&origin.standardSource()?Kind.JUMP:Kind.NATIVE;
    }
}
