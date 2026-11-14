// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.*;
import java.util.regex.*;

/** Changes only the native MSS application list, preserving every other policy byte. */
final class SoundPolicy {
    static final String KEY="sound_mss_apps", HOST="com.oplus.audio.effectcenter", PROCESS="com.oplus.persist.multimedia";
    static final long LEASE_MS=30_000;
    static final int APP_MUSIC_ONLY_EXCLUSION=16;
    record Patch(String xml,Set<String> expanded,int total){}
    private static final Pattern ITEM=Pattern.compile("\\s*<name>([^<]+)</name>\\s*<attribute>([0-9]+)</attribute>");
    static boolean packageName(String s){return s!=null&&s.length()<256&&s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    static Map<String,Integer> entries(String xml){
        String start="<mss-whitelist>",end="</mss-whitelist>";int a=xml.indexOf(start),b=xml.indexOf(end);
        if(a<0||b<a||xml.indexOf(start,a+1)>=0||xml.indexOf(end,b+1)>=0)throw new IllegalArgumentException("MSS list is not unique");
        String body=xml.substring(a+start.length(),b);Matcher m=ITEM.matcher(body);LinkedHashMap<String,Integer> entries=new LinkedHashMap<>();int p=0;
        while(m.find()){if(!body.substring(p,m.start()).isBlank()||!packageName(m.group(1)))throw new IllegalArgumentException("Unknown MSS list format");if(entries.put(m.group(1),Integer.parseInt(m.group(2)))!=null)throw new IllegalArgumentException("Duplicate MSS application");p=m.end();}
        if(entries.isEmpty()||!body.substring(p).isBlank())throw new IllegalArgumentException("Incomplete MSS list");return entries;
    }
    static Patch expand(String xml,Collection<String> installed){
        Map<String,Integer> original=entries(xml),values=new LinkedHashMap<>(original);Set<String> expanded=new LinkedHashSet<>();
        for(String pkg:installed)if(packageName(pkg)){int old=values.getOrDefault(pkg,2),allowed=(old|1)&~APP_MUSIC_ONLY_EXCLUSION;if(allowed!=old){values.put(pkg,allowed);expanded.add(pkg);}}
        StringBuilder body=new StringBuilder("\n");values.forEach((pkg,value)->body.append("        <name>").append(pkg).append("</name>\n        <attribute>").append(value).append("</attribute>\n"));body.append("    ");
        int a=xml.indexOf("<mss-whitelist>")+"<mss-whitelist>".length(),b=xml.indexOf("</mss-whitelist>");
        return new Patch(xml.substring(0,a)+body+xml.substring(b),Set.copyOf(expanded),values.size());
    }
    static boolean leaseValid(long now,long stamp,int pid,boolean hostAlive){return pid>0&&hostAlive&&stamp>=0&&now>=stamp&&now-stamp<=LEASE_MS;}
    static boolean restoredAttribute(String actual,Integer original){if(actual==null||actual.isBlank())return true;try{return original!=null&&Integer.parseInt(actual)==original;}catch(NumberFormatException e){return false;}}
    private SoundPolicy(){}
}
