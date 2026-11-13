package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class CaptionVisibilityTrackerTest {
    @Test public void disablingOneControlDoesNotRevealTheOther(){
        Object top=new Object(),bottom=new Object();var tracker=new CaptionVisibilityTracker<Object>();
        Map<Object,Integer> current=new HashMap<>();tracker.track(top,"top",0);tracker.track(bottom,"bottom",0);
        tracker.reconcile("bottom"::equals,current::put,4);assertEquals(Integer.valueOf(0),current.get(top));assertEquals(Integer.valueOf(4),current.get(bottom));
        tracker.reconcile(key->false,current::put,4);assertEquals(Integer.valueOf(0),current.get(bottom));
    }
    @Test public void restoreNativeGoneAndDoNotOverwriteOriginalWhileHidden(){
        Object view=new Object();var tracker=new CaptionVisibilityTracker<Object>();Map<Object,Integer> current=new HashMap<>();
        tracker.track(view,"top",8);tracker.track(view,"top",4);tracker.reconcile(key->false,current::put,4);assertEquals(Integer.valueOf(8),current.get(view));
    }
    @Test public void delayedUiCallbackReadsLatestOption(){
        Object top=new Object(),bottom=new Object();var tracker=new CaptionVisibilityTracker<Object>();Map<Object,Integer> current=new HashMap<>();tracker.track(top,"top",0);tracker.track(bottom,"bottom",8);
        tracker.reconcileOne(top,key->true,current::put,4);assertEquals(Integer.valueOf(4),current.get(top));assertEquals(2,tracker.views().size());
        tracker.reconcileOne(bottom,key->false,current::put,4);assertEquals(Integer.valueOf(8),current.get(bottom));assertEquals(List.of(top),tracker.views());
    }
}
