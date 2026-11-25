// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.zip.ZipFile;

final class SettingsScope {
    private static final Map<String,String> LABELS=Map.of(
        "system","系统框架","com.android.systemui","系统界面","com.oplus.aod","息屏",
        "com.oplus.gesture","手势体感","com.oplus.trafficmonitor","流量管理",
        "com.oplus.gleanerservice","小布记忆","com.coloros.gallery3d","相册",
        "com.oplus.camera","相机","com.oplus.audio.effectcenter","音效服务","com.oplus.atlas","Atlas 服务");
    static String summary(String apkPath,Collection<String> enabled){
        try(ZipFile apk=new ZipFile(apkPath)){
            var entry=apk.getEntry("META-INF/xposed/scope.list");
            if(entry!=null)try(var reader=new BufferedReader(new InputStreamReader(apk.getInputStream(entry),StandardCharsets.UTF_8))){
                return display(reader.lines().collect(java.util.stream.Collectors.toList()),enabled);
            }
        }catch(IOException | java.io.UncheckedIOException ignored){/* The other settings remain usable. */}
        return "无法读取安装包中的作用域清单";
    }
    static String display(Collection<String> declared,Collection<String> enabled){
        var lines=new StringJoiner("\n");
        String previous=null;
        for(String raw:declared){
            String pkg=raw.trim();
            if(pkg.isEmpty()||pkg.startsWith("#"))continue;
            String item=LABELS.getOrDefault(pkg,pkg)+(enabled.contains(pkg)?"已勾选":"未勾选");
            if(previous==null)previous=item;
            else{lines.add(previous+" · "+item);previous=null;}
        }
        if(previous!=null)lines.add(previous);
        return lines.toString();
    }
    private SettingsScope(){}
}
