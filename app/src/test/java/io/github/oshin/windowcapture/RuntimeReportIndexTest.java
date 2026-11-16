package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuntimeReportIndexTest {
    private RuntimeReportIndex.Identity report(String process,int pid,long stamp,int code,int activity){return new RuntimeReportIndex.Identity("cast",process,pid,1,stamp,code,activity);}
    @Test public void activeEncoderWinsEitherArrivalOrder(){
        for(boolean reverse:new boolean[]{false,true}){
            var index=new RuntimeReportIndex<String>();
            var active=report("cast",1,10,19,2);var idle=report("cast:lelinkps",2,11,19,0);
            index.accept(reverse?idle:active,reverse?"idle":"active",19);
            assertEquals("active",index.accept(reverse?active:idle,reverse?"active":"idle",19));
            assertEquals(2,index.processCount("cast"));
        }
    }
    @Test public void endedEncoderReplacesActiveReportFromSameProcess(){var index=new RuntimeReportIndex<String>();index.accept(report("cast",1,10,19,2),"active",19);assertEquals("ended",index.accept(report("cast",1,11,19,0),"ended",19));}
    @Test public void lateOldSampleCannotResurrectEndedSession(){var index=new RuntimeReportIndex<String>();index.accept(report("cast",1,11,19,0),"ended",19);assertEquals("ended",index.accept(report("cast",1,10,19,2),"old",19));}
    @Test public void currentCodeWinsOverOldActiveProcess(){var index=new RuntimeReportIndex<String>();index.accept(report("cast",1,11,18,2),"old",19);assertEquals("current",index.accept(report("cast",2,10,19,0),"current",19));}
    @Test public void refreshDropsPreviousSession(){var index=new RuntimeReportIndex<String>();index.accept(report("cast",1,11,19,2),"old",19);index.clear();assertEquals("idle",index.accept(report("cast:lelinkps",2,12,19,0),"idle",19));assertEquals(1,index.processCount("cast"));}
    @Test public void samePidWithNewStartIsDistinct(){var index=new RuntimeReportIndex<String>();index.accept(report("cast",1,11,19,0),"old",19);index.accept(new RuntimeReportIndex.Identity("cast","cast",1,12,13,19,2),"new",19);assertEquals(2,index.processCount("cast"));}
}
