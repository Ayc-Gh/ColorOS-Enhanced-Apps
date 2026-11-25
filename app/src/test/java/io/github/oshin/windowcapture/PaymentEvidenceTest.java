package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
public class PaymentEvidenceTest {
    @Test public void merchantDigitsCannotBecomeAmount(){var r=PaymentEvidence.verify("支付成功 商户123 ￥12.34",Map.of("price","123","recipient","商户"));assertTrue(r.accepted());assertEquals("12.34",r.values().get("price"));assertEquals("商户123",r.values().get("recipient"));assertTrue(r.corrected());}
    @Test public void bareNumbersAndMissingStatusDoNotCreateBills(){assertFalse(PaymentEvidence.verify("支付成功订单号202611140001",Map.of("price","202611140001")).accepted());assertFalse(PaymentEvidence.verify("未支付成功 ￥12.34",Map.of()).accepted());assertFalse(PaymentEvidence.verify("支付成功 已退款 ￥12.34",Map.of()).accepted());}
    @Test public void ambiguityIsRejectedAndExplicitActualAmountWins(){assertFalse(PaymentEvidence.verify("支付成功 ￥15.00 ￥12.00",Map.of()).accepted());assertEquals("12.00",PaymentEvidence.verify("支付成功 原价￥15.00 实付金额￥12.00",Map.of()).values().get("price"));assertTrue(PaymentEvidence.verify("支付成功 ￥12.00 付款金额12.00",Map.of()).accepted());}
    @Test public void repeatedEqualAmountsAndCurrencyVariantsAreSupported(){assertEquals("1234.50",PaymentEvidence.verify("支付成功 ¥1,234.50 ￥1234.50",Map.of()).values().get("price"));assertFalse(PaymentEvidence.verify("支付成功 ￥1.234",Map.of()).accepted());assertFalse(PaymentEvidence.verify("支付成功 ￥0.00",Map.of()).accepted());assertFalse(PaymentEvidence.verify("支付成功 USD12.00",Map.of()).accepted());}
    @Test public void actualOrderAndFullPaymentTimeImproveDeduplication(){
        var a=verified("交易单号202611140001");var b=verified("交易单号202611140002");
        assertEquals(PaymentEvidence.Proof.Relation.DIFFERENT,a.compare(b));assertEquals(PaymentEvidence.Proof.Relation.SAME,a.compare(a));
        var t1=verified("支付时间2026-11-14 1:02:03");var t2=verified("支付时间2026年11月14日 1:02:04");
        assertEquals(PaymentEvidence.Proof.Relation.DIFFERENT,t1.compare(t2));
        assertNull(verified(""));
    }
    @Test public void idsAreTakenOnlyFromVisibleLabeledText(){var r=PaymentEvidence.verify("支付成功 ￥12.34 交易单号202611140001 支付时间2026-11-14 01:02:03",Map.of());assertEquals("202611140001",r.values().get("orderNumber"));assertEquals("2026-11-14 01:02:03",r.values().get("paymentTime"));}
    @Test public void currencyLabelsAndEqualSecondWithoutAnOrderStayConservative(){
        for(String text:new String[]{"支付成功 实付金额12.00USD","支付成功 实付金额€12.00","支付成功 付款金额12.00港元"})assertFalse(PaymentEvidence.verify(text,Map.of()).accepted());
        var t=verified("支付时间2026-11-14 01:02:03");assertEquals(PaymentEvidence.Proof.Relation.UNKNOWN,t.compare(t));
    }
    private static PaymentEvidence.Proof verified(String evidence){
        return PaymentEvidence.verify("支付成功 ￥1.00 "+evidence,Map.of("recipient","sample")).proof();
    }
    @Test public void unresolvedOrConflictingIdentifiersCannotOverrideNativeDedup(){
        String text="支付成功 实付金额6.00 交易单号ABC12345678 支付时间2026-11-14 01:02:03";
        var conflict=PaymentEvidence.verify(text,Map.of("orderNumber","OLD00000000"));
        assertTrue(conflict.accepted());assertNull(conflict.proof());assertFalse(conflict.values().containsKey("orderNumber"));
        assertNull(PaymentEvidence.verify(text,Map.of("paymentTime","2026-11-15 01:02:03")).proof());
        assertNull(PaymentEvidence.verify("支付成功 实付金额6.00",Map.of("orderNumber","OLD00000000")).proof());
        assertNull(verified("交易单号ABC12345678 交易单号ABC87654321"));
        assertEquals(PaymentEvidence.Proof.Relation.UNKNOWN,verified("交易单号ABC12345678").compare(verified("商户订单号ABC87654321")));
    }
    @Test public void proofAssociationRequiresTheVerifiedNativeIdentity(){
        var proof=verified("交易单号ABC12345678");
        assertTrue(proof.matches(Map.of("price","1","orderNumber","ABC12345678")));
        assertFalse(proof.matches(Map.of("price","2","orderNumber","ABC12345678")));
        assertFalse(proof.matches(Map.of("price","1","orderNumber","ABC87654321")));
        assertFalse(proof.matches(Map.of("price","1")));
    }
    @Test public void foreignAmountsAreRejectedAndLocalLabelsWithoutCurrencyStillWork(){
        for(String label:new String[]{"1000韩元","1000KRW","KRW1000","1000 泰铢","1000 THB","₩1000","1000新加坡元","1000₽"})
            assertFalse(label,PaymentEvidence.verify("支付成功 实付金额"+label,Map.of()).accepted());
        for(String label:new String[]{"1000","1000元","1000人民币","1000CNY","￥1000","RMB1000","1000 交易单号ABC12345678"})
            assertTrue(label,PaymentEvidence.verify("支付成功 实付金额"+label,Map.of()).accepted());
    }
}
