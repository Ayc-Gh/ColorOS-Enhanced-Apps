// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.lang.ref.WeakReference;

/** Guards against a delayed configure/stop from a previous encoder or session. */
final class CastSession<T> {
    record Snapshot<T>(long generation,boolean active,T encoder) {}
    private long generation;
    private boolean active;
    private T encoder;
    private WeakReference<Object> owner=new WeakReference<>(null);
    synchronized long begin(boolean enabled){generation++;active=enabled;encoder=null;owner.clear();return generation;}
    synchronized void failed(long token){if(token==generation){active=false;encoder=null;owner.clear();}}
    synchronized boolean configured(long token,Object codec,T value){
        if(token!=generation||!active)return false;
        owner=new WeakReference<>(codec);encoder=value;return true;
    }
    synchronized void ended(Object codec){if(owner.get()==codec){active=false;encoder=null;owner.clear();}}
    synchronized Snapshot<T> snapshot(){return new Snapshot<>(generation,active,encoder);}
}
