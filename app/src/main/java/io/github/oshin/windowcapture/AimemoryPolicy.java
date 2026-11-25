// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

final class AimemoryPolicy {
    static final String HOST="com.oplus.gleanerservice", WECHAT="com.tencent.mm", SCENE="flashNotes_autoPayment";
    static final String REVISION="verified-ocr-2";
    private static final long WECHAT_8_0_79=3182L, WECHAT_8_0_80=3220L;
    private static final java.util.Set<String> PAGES=java.util.Set.of(
        "com.tencent.mm.framework.app.UIPageFragmentActivity",
        "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI",
        "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI",
        "com.tencent.mm.plugin.remittance.mobile.ui.MobileRemitResultUI",
        "com.tencent.mm.plugin.lite.ui.WxaLiteAppTransparentLiteUI",
        "com.tencent.mm.plugin.lite.ui.WxaLiteAppPayTransparentLiteUI",
        "com.tencent.mm.plugin.lite.ui.WxaLiteAppLiteUI");
    private AimemoryPolicy(){}
    static boolean supported(long host,long wechat){
        return CompatibilityPolicy.host(HOST,host)&&(wechat==WECHAT_8_0_79||wechat==WECHAT_8_0_80);
    }
    static boolean supportedPage(long host,long wechat,String page,java.util.function.Predicate<String> declared){
        if(!supported(host,wechat)||!paymentPage(page))return false;
        // Keep the existing 8.0.80 adapter. On 8.0.79 restore only activities available in this installation.
        return wechat==WECHAT_8_0_80||declared.test(page);
    }
    static boolean paymentPage(String page){return page!=null&&PAGES.contains(page);}
    static boolean usesOcr(String page){return paymentPage(page)&&(page.startsWith("com.tencent.mm.plugin.lite.ui.")||page.equals("com.tencent.mm.framework.app.UIPageFragmentActivity"));}
    static String pageKind(String page){return !paymentPage(page)?"other":page.contains("MMWebViewUI")?"detail":usesOcr(page)?"result":page.contains("luckymoney")?"redpacket":"transfer";}
    static boolean repairable(String scene,String app,String page,String ext){
        return SCENE.equals(scene)&&app!=null&&app.endsWith("-"+WECHAT)&&paymentPage(page)&&"-1".equals(ext);
    }
    static String metadata(String page){
        if(page==null||!PAGES.contains(page))throw new IllegalArgumentException("Unknown payment page");
        // A saved native per-app choice takes precedence over this disabled default.
        String rule=usesOcr(page)?",\"paymentPageRule\":{\"pageType\":\"complete\",\"subPageType\":\"paymentComplete\",\"viewType\":\"native\",\"isUseViewExtract\":0,\"isUseOcr\":1,\"maxRetryTimes\":20,\"retryDTMillis\":1000,\"extractMethod\":0}":"";
        return "{\"sceneName\":\""+SCENE+"\",\"hostVersion\":\"16.5.0\",\"ruleVersion\":1,\"switchOpen\":false,\"subType\":"+(page.contains("MMWebViewUI")?2:1)+",\"appSubTitleResName\":\"recognize_success_pages\""+rule+"}";
    }
}
