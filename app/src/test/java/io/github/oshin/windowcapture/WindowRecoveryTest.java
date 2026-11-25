package io.github.oshin.windowcapture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class WindowRecoveryTest {
    @Test public void rebindAfterFailureInstallsOnce(){
        var config=new WindowConfigConnection();
        var installations=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->config.connect(()->{throw new IllegalStateException();},installations::incrementAndGet));
        assertEquals(0,installations.get());
        assertEquals(1000,config.retryDelayMs());
        config.connect(()->{},installations::incrementAndGet);
        config.connect(()->fail("already connected"),installations::incrementAndGet);
        assertEquals(1,installations.get());
    }
    @Test public void temporarilyInvalidSurfaceRetainsOnlyOneRetry(){
        var parked=new WindowParkedTasks();
        var callbacks=new ArrayList<Runnable>();
        var delays=new ArrayList<Long>();
        parked.park(7,new Object());
        var entry=parked.get(7);
        Runnable retry=()->parked.retry(entry,(callback,delay)->{callbacks.add(callback);delays.add(delay);},()->parked.finish(entry));
        retry.run();
        retry.run();
        assertSame(entry,parked.get(7));
        assertEquals(List.of(200L),delays);
        callbacks.remove(0).run();
        assertNull(parked.get(7));
    }
    @Test public void destructionAndReparkingInvalidatePendingRestore(){
        var parked=new WindowParkedTasks();
        var callbacks=new ArrayList<Runnable>();
        Object task=new Object();
        for(boolean destroyed:new boolean[]{false,true}){
            parked.park(7,task);
            var original=parked.get(7);
            parked.retry(original,(callback,delay)->callbacks.add(callback),()->fail("stale restore was run"));
            if(destroyed)parked.finish(original);
            parked.park(7,destroyed?new Object():task);
            var replacement=parked.get(7);
            callbacks.remove(0).run();
            parked.finish(original);
            assertSame(replacement,parked.get(7));
        }
    }
    @Test public void splitSharedQueriesRequireTheEnclosingStandardTask()throws Throwable{
        var scope=new WindowSplitScope();
        assertFalse(scope.active());
        assertEquals(true,scope.query(true,"example.app",()->{
            assertTrue(scope.active());
            assertTrue(scope.matches("example.app"));
            assertFalse(scope.matches("example.other"));
            assertEquals(false,scope.query(false,"example.special",()->{assertFalse(scope.active());return false;}));
            assertTrue(scope.matches("example.app"));
            assertThrows(IllegalStateException.class,()->scope.query(false,null,()->{throw new IllegalStateException();}));
            assertTrue(scope.active());
            return false;
        }));
        assertFalse(scope.active());
        assertEquals(true,scope.query(true,null,()->{assertTrue(scope.active());assertFalse(scope.matches(""));return false;}));
        assertEquals(false,scope.query(false,null,()->false));
    }
    @Test public void canvasSizingRequiresTheExactLiveStandardActivityOnThePhone() {
        var target=new WindowSplitScope.CanvasActivity("sample.app","sample.app.Payment",10123);
        assertTrue(WindowSplitScope.isPhoneCanvasRequest(0,2));
        assertFalse(WindowSplitScope.isPhoneCanvasRequest(1,2));
        for(int scenario:new int[]{-1,0,1,3,4,5})assertFalse(WindowSplitScope.isPhoneCanvasRequest(0,scenario));
        assertTrue(WindowSplitScope.canvasTask(-1,42,1,target,target));
        assertTrue(WindowSplitScope.canvasTask(42,42,1,target,target));
        assertFalse(WindowSplitScope.canvasTask(41,42,1,target,target));
        assertFalse(WindowSplitScope.canvasTask(-1,-1,1,target,target));
        for(int type:new int[]{0,2,3,4,5})assertFalse(WindowSplitScope.canvasTask(42,42,type,target,target));
        for(var mismatch:java.util.List.of(new WindowSplitScope.CanvasActivity("sample.other",target.className(),target.uid()),
                new WindowSplitScope.CanvasActivity(target.packageName(),"sample.app.Settings",target.uid()),
                new WindowSplitScope.CanvasActivity(target.packageName(),target.className(),110123)))
            assertFalse(WindowSplitScope.canvasTask(42,42,1,target,mismatch));
        assertFalse(WindowSplitScope.canvasTask(42,42,1,target,null));
        assertFalse(WindowSplitScope.canvasTask(42,42,1,null,null));
        var missing=new WindowSplitScope.CanvasActivity(null,null,-1);
        assertFalse(WindowSplitScope.canvasTask(42,42,1,missing,missing));
    }
}
