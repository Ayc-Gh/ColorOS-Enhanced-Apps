package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;

public class AimemoryPolicyTest {
    private final String page="com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI";
    @Test public void onlyMissingKnownWeChatPaymentMetadataIsEligible(){
        assertTrue(AimemoryPolicy.repairable(AimemoryPolicy.SCENE,"微信-com.tencent.mm",page,"-1"));
        assertFalse(AimemoryPolicy.repairable("flashNotes_payment","微信-com.tencent.mm",page,"-1"));
        assertFalse(AimemoryPolicy.repairable(AimemoryPolicy.SCENE,"其他-com.tencent.mm.fake",page,"-1"));
        assertFalse(AimemoryPolicy.repairable(AimemoryPolicy.SCENE,"微信-com.tencent.mm","com.tencent.mm.ui.LauncherUI","-1"));
        assertFalse(AimemoryPolicy.repairable(AimemoryPolicy.SCENE,"微信-com.tencent.mm",page,"{\"switchOpen\":false}"));
        assertFalse(AimemoryPolicy.repairable(AimemoryPolicy.SCENE,null,null,null));
    }
    @Test public void unsupportedApplicationUpdatesKeepNativeBehavior(){
        assertTrue(AimemoryPolicy.supported(17008000,3220));
        assertFalse(AimemoryPolicy.supported(17008001,3220));
        assertFalse(AimemoryPolicy.supported(17008000,3221));
    }
    @Test public void nativePerAppChoiceStartsDisabledAndWebPagesUseDetailMode(){
        assertTrue(AimemoryPolicy.metadata(page).contains("\"switchOpen\":false"));
        assertTrue(AimemoryPolicy.metadata(page).contains("\"subType\":2"));
        assertFalse(AimemoryPolicy.metadata(page).contains("paymentStatus"));
        assertFalse(AimemoryPolicy.usesOcr(page));
        assertFalse(AimemoryPolicy.usesOcr("com.tencent.mm.ui.LauncherUI"));
        String result="com.tencent.mm.plugin.lite.ui.WxaLiteAppTransparentLiteUI";
        assertTrue(AimemoryPolicy.usesOcr(result));
        assertTrue(AimemoryPolicy.metadata(result).contains("\"isUseViewExtract\":0"));
        assertTrue(AimemoryPolicy.metadata(result).contains("\"isUseOcr\":1"));
        assertFalse(AimemoryPolicy.usesOcr("com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"));
    }
}
