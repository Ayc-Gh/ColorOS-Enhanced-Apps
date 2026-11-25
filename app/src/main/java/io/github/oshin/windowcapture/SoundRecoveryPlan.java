// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One atomic record contains the original policy identity and every application to restore. */
record SoundRecoveryPlan(String hash,Set<String> changed,Set<String> alreadyActive) {
    SoundRecoveryPlan {
        changed=Set.copyOf(changed);
        alreadyActive=Set.copyOf(alreadyActive);
        if(!hash.matches("[0-9a-f]{64}")||!changed.containsAll(alreadyActive)||!changed.stream().allMatch(SoundPolicy::packageName))
            throw new IllegalArgumentException("Invalid audio recovery plan");
    }
    String encode(){
        StringBuilder out=new StringBuilder("v1 ").append(hash);
        changed.stream().sorted().forEach(pkg->out.append('\n').append(alreadyActive.contains(pkg)?"1 ":"0 ").append(pkg));
        return out.toString();
    }
    void verifyRestored(java.util.Map<String,Integer> original,SoundPolicy.Lookup lookup)throws Exception{
        Set<String> packages=new LinkedHashSet<>(original.keySet());
        packages.addAll(changed);
        SoundPolicy.verify(original,packages,lookup);
    }
    static SoundRecoveryPlan parse(List<String> lines)throws IOException{
        if(lines.isEmpty()||!lines.get(0).matches("v1 [0-9a-f]{64}"))throw new IOException("Invalid audio recovery header");
        Set<String> changed=new LinkedHashSet<>(),active=new LinkedHashSet<>();
        for(String row:lines.subList(1,lines.size())){
            if(!(row.startsWith("0 ")||row.startsWith("1 ")))throw new IOException("Invalid audio recovery row");
            String pkg=row.substring(2);
            if(!SoundPolicy.packageName(pkg)||!changed.add(pkg))throw new IOException("Invalid audio recovery package");
            if(row.startsWith("1 "))active.add(pkg);
        }
        return new SoundRecoveryPlan(lines.get(0).substring(3),changed,active);
    }
}
