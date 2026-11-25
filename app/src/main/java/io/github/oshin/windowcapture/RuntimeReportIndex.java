// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.*;

/** One request's authenticated replies, retained per process instead of arrival order. */
public final class RuntimeReportIndex<T> {
    public record Identity(String host,String process,int pid,long started,long sampled,int code) {}
    private record Entry<T>(Identity identity,T report) {}
    private final Map<String,Map<String,Entry<T>>> hosts=new HashMap<>();
    public void clear(){hosts.clear();}
    public T accept(Identity identity,T report,int expectedCode){
        return accept(identity,report,expectedCode,null);
    }
    public T accept(Identity identity,T report,int expectedCode,String preferredProcess){
        Map<String,Entry<T>> processes=hosts.computeIfAbsent(identity.host(),key->new HashMap<>());
        String key=identity.process()+":"+identity.pid()+":"+identity.started();
        Entry<T> previous=processes.get(key);
        if(previous==null||identity.sampled()>=previous.identity().sampled())processes.put(key,new Entry<>(identity,report));
        return processes.values().stream().max(Comparator
            .<Entry<T>>comparingInt(e->e.identity().code()==expectedCode?1:0)
            .thenComparingInt(e->e.identity().process().equals(preferredProcess)?1:0)
            .thenComparingLong(e->e.identity().sampled())
            .thenComparing(e->e.identity().process())
            .thenComparingInt(e->e.identity().pid())).orElseThrow().report();
    }
    public int processCount(String host){return hosts.getOrDefault(host,Map.of()).size();}
}
