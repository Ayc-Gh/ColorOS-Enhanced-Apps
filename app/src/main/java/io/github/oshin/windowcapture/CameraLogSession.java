// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.concurrent.ConcurrentHashMap;

/** The added format is usable in this process; native storage keeps a supported format. */
final class CameraLogSession {
    static final String FORMAT="pref_master_video_format_key",ENABLE="pref_log_video_key";
    private final ConcurrentHashMap<String,Object> current=new ConcurrentHashMap<>();
    static boolean targets(String key){return FORMAT.equals(key)||ENABLE.equals(key);}
    static Object nativeValue(String key,Object value){if(FORMAT.equals(key)&&"log".equals(value))return "sdr";if(ENABLE.equals(key)&&"on".equals(value))return "off";return value;}
    Object write(String uri,String key,Object value,boolean requested){if(requested&&value!=null)current.put(uri,value);else current.remove(uri);return nativeValue(key,value);}
    Object read(String uri,String key,Object stored,boolean requested){return requested?current.getOrDefault(uri,nativeValue(key,stored)):nativeValue(key,stored);}
    void clear(){current.clear();}
}
