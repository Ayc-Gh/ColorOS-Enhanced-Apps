package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CastSessionTest {
    @Test public void oldConfigureCannotOverwriteReconnect(){var s=new CastSession<String>();long old=s.begin(true);long current=s.begin(true);assertFalse(s.configured(old,new Object(),"old"));assertTrue(s.configured(current,new Object(),"new"));assertEquals("new",s.snapshot().encoder());}
    @Test public void unrelatedCodecStopPreservesEncoder(){var s=new CastSession<String>();Object encoder=new Object();s.configured(s.begin(true),encoder,"video");s.ended(new Object());assertTrue(s.snapshot().active());assertEquals("video",s.snapshot().encoder());}
    @Test public void encoderStopClearsReportedSession(){var s=new CastSession<String>();Object encoder=new Object();s.configured(s.begin(true),encoder,"video");s.ended(encoder);assertFalse(s.snapshot().active());assertNull(s.snapshot().encoder());}
    @Test public void oldStartFailureDoesNotCancelNewSession(){var s=new CastSession<String>();long old=s.begin(true);s.begin(true);s.failed(old);assertTrue(s.snapshot().active());}
    @Test public void disabledOrFailedStartRejectsConfigure(){var s=new CastSession<String>();assertFalse(s.configured(s.begin(false),new Object(),"video"));long token=s.begin(true);s.failed(token);assertFalse(s.configured(token,new Object(),"video"));}
}
