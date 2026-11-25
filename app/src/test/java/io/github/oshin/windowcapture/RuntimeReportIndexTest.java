package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeReportIndexTest {
    @Test public void aodWorkerCannotReplaceAMatchingMainProcess(){
        var index=new RuntimeReportIndex<String>();
        assertEquals("worker",index.accept(report("host:worker",2,19,25),"worker",25,"host"));
        assertEquals("main",index.accept(report("host",1,11,25),"main",25,"host"));
        assertEquals("main",index.accept(report("host:worker",2,20,25),"fresh worker",25,"host"));
        assertEquals(2,index.processCount("host"));
        index.clear();
        index.accept(report("host",1,21,24),"old main",25,"host");
        assertEquals("current worker",index.accept(report("host:worker",2,20,25),"current worker",25,"host"));
    }
    private RuntimeReportIndex.Identity report(String process,int pid,long stamp,int code){return new RuntimeReportIndex.Identity("host",process,pid,1,stamp,code);}
    @Test public void newestSampleWinsRegardlessOfArrivalOrder(){
        for(boolean reversed:new boolean[]{false,true}){
            var index=new RuntimeReportIndex<String>();
            var older=report("host",1,10,23);var newer=report("host:worker",2,11,23);
            if(reversed){index.accept(newer,"new",23);assertEquals("new",index.accept(older,"old",23));}
            else{index.accept(older,"old",23);assertEquals("new",index.accept(newer,"new",23));}
            assertEquals(2,index.processCount("host"));
        }
    }
    @Test public void lateSampleCannotOverwriteNewerReportFromSameProcess(){var index=new RuntimeReportIndex<String>();index.accept(report("host",1,11,23),"new",23);assertEquals("new",index.accept(report("host",1,10,23),"old",23));}
    @Test public void currentCodeWinsOverLaterOldProcess(){var index=new RuntimeReportIndex<String>();index.accept(report("host",1,11,22),"old",23);assertEquals("current",index.accept(report("host:worker",2,10,23),"current",23));}
    @Test public void refreshDropsPreviousReplies(){var index=new RuntimeReportIndex<String>();index.accept(report("host",1,11,23),"old",23);index.clear();assertEquals("new",index.accept(report("host:worker",2,12,23),"new",23));assertEquals(1,index.processCount("host"));}
    @Test public void samePidWithNewStartIsDistinct(){var index=new RuntimeReportIndex<String>();index.accept(report("host",1,11,23),"old",23);index.accept(new RuntimeReportIndex.Identity("host","host",1,12,13,23),"new",23);assertEquals(2,index.processCount("host"));}
}
