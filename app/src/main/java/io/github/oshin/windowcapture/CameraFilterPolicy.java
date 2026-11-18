// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.Set;

/** Mirrors the OEM compound-LUT contrast interpolation for anonymous file handles. */
final class CameraFilterPolicy {
    private CameraFilterPolicy() {}
    static int clamp(int strength){return Math.max(0,Math.min(100,strength));}
    static float factor(int strength){int level=clamp(strength);return (level<50?level:level-50)/50f;}
    static String[] contrastLuts(String name,int strength,Set<String> available){
        if(name==null||!name.startsWith("blackandwhite_HC_")||!name.endsWith(".bin")||!available.contains(name))return null;
        String base=name.substring(0,name.length()-4),low=base+"_0.bin",high=base+"_1.bin";
        if(!available.contains(low)||!available.contains(high))return null;
        return clamp(strength)<50?new String[]{low,name}:new String[]{name,high};
    }
}
