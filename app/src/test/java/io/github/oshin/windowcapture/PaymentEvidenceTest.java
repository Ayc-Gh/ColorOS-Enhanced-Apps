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
        var a=PaymentEvidence.proof(Map.of("orderNumber","202611140001","price","1.00"));var b=PaymentEvidence.proof(Map.of("orderNumber","202611140002","price","1.00"));assertEquals(PaymentEvidence.Proof.Relation.DIFFERENT,a.compare(b));assertEquals(PaymentEvidence.Proof.Relation.SAME,a.compare(a));
        var t1=PaymentEvidence.proof(Map.of("paymentTime","2026-11-14 1:02:03","price","1.00","recipient","sample"));var t2=PaymentEvidence.proof(Map.of("paymentTime","2026年11月14日 1:02:04","price","1","recipient","sample"));assertEquals(PaymentEvidence.Proof.Relation.DIFFERENT,t1.compare(t2));
        assertEquals(PaymentEvidence.Proof.Relation.UNKNOWN,PaymentEvidence.proof(Map.of("price","1.00")).compare(PaymentEvidence.proof(Map.of("price","1.00"))));
    }
    @Test public void idsAreTakenOnlyFromVisibleLabeledText(){var r=PaymentEvidence.verify("支付成功 ￥12.34 交易单号202611140001 支付时间2026-11-14 01:02:03",Map.of());assertEquals("202611140001",r.values().get("orderNumber"));assertEquals("2026-11-14 01:02:03",r.values().get("paymentTime"));}
    @Test public void currencyLabelsAndEqualSecondWithoutAnOrderStayConservative(){
        for(String text:new String[]{"支付成功 实付金额12.00USD","支付成功 实付金额€12.00","支付成功 付款金额12.00港元"})assertFalse(PaymentEvidence.verify(text,Map.of()).accepted());
        var t=PaymentEvidence.proof(Map.of("paymentTime","2026-11-14 01:02:03","price","1","recipient","sample"));assertEquals(PaymentEvidence.Proof.Relation.UNKNOWN,t.compare(t));
    }
}
