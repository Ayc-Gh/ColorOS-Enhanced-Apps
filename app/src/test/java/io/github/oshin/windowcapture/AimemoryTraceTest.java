package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
public class AimemoryTraceTest {
    @Test public void countOnlyPresenceOfKnownNonemptyFields(){
        assertEquals(0,AimemoryTrace.fieldMask(Map.of()));
        assertEquals(1,AimemoryTrace.fieldMask(Map.of("price","1.00","paymentStatus"," ","unknown","secret")));
        assertEquals(3,AimemoryTrace.fieldMask(Map.of("price","1.00","paymentStatus","支付成功")));
        assertEquals(31,AimemoryTrace.fieldMask(Map.of("price","1.00","paymentStatus","支付成功","recipient","sample","orderNumber","example","paymentTime","sample")));
    }
    @Test public void unknownPagesAreNotLabeledPaymentResults(){
        assertEquals("other",AimemoryPolicy.pageKind("com.tencent.mm.ui.chatting.ChattingUI"));
        assertEquals("detail",AimemoryPolicy.pageKind("com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI"));
    }
}
