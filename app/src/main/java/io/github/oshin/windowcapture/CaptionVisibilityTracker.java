// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
/** Each control keeps its own native visibility and controlling option. */
final class CaptionVisibilityTracker<T> {
    private record Entry(String key,int visibility){}
    private final WeakHashMap<T,Entry> entries=new WeakHashMap<>();
    synchronized void track(T view,String key,int nativeVisibility){entries.putIfAbsent(view,new Entry(key,nativeVisibility));}
    synchronized List<T> views(){return new ArrayList<>(entries.keySet());}
    synchronized void reconcileOne(T view,Predicate<String> enabled,BiConsumer<T,Integer> setter,int invisible){
        Entry entry=entries.get(view);if(entry==null)return;boolean hide=enabled.test(entry.key());
        setter.accept(view,hide?invisible:entry.visibility());if(!hide)entries.remove(view);
    }
    synchronized void reconcile(Predicate<String> enabled,BiConsumer<T,Integer> setter,int invisible){
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){
            var item=iterator.next();boolean hide=enabled.test(item.getValue().key());
            setter.accept(item.getKey(),hide?invisible:item.getValue().visibility());
            if(!hide)iterator.remove();
        }
    }
}
