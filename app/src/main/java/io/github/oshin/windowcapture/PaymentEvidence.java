// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;

/** Strict, local verification of actual OCR text. Never supplies missing financial values. */
final class PaymentEvidence {
    private static final String NUMBER="(?:[0-9]{1,3}(?:[,，][0-9]{3})+|[0-9]{1,9})(?:[.．][0-9]{1,2})?";
    private static final String END="(?![0-9.,．，])";
    private static final Pattern CURRENCY=Pattern.compile("(?:[￥¥]|人民币|CNY|RMB)\\s*("+NUMBER+")"+END,Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTUAL=Pattern.compile("(?:实付金额|实付款|实际支付)[：:\\s]*(?:[￥¥]|人民币|CNY|RMB)?\\s*("+NUMBER+")"+END);
    private static final Pattern LABELED=Pattern.compile("(?:付款金额|支付金额)[：:\\s]*(?:[￥¥]|人民币|CNY|RMB)?\\s*("+NUMBER+")"+END);
    private static final Pattern NEGATIVE=Pattern.compile("未支付成功|没有支付成功|支付失败|支付未完成|支付取消|交易关闭|等待支付|全额退款|已退款");
    private static final Pattern FOREIGN=foreignCurrency();
    private static final String OTHER_CODE="(?!(?:RMB|CNY)(?![A-Z]))[A-Z]{3}(?![A-Z])";
    private static final Pattern FOREIGN_AFTER=Pattern.compile("\\s*"+OTHER_CODE,Pattern.CASE_INSENSITIVE);
    private static final Pattern FOREIGN_BEFORE=Pattern.compile("(?:实付金额|实付款|实际支付|付款金额|支付金额)[：:\\s]*"+OTHER_CODE+"\\s*[0-9]",Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER=Pattern.compile("(商户订单号|商家订单号|交易单号|交易订单号|转账单号|订单号)[：:\\s]*([A-Za-z0-9]{8,64})(?![A-Za-z0-9])");
    private static final Pattern TIME=Pattern.compile("(?:支付时间|付款时间)[：:\\s]*([0-9]{4}[-/年][0-9]{1,2}[-/月][0-9]{1,2}日?\\s+[0-9]{1,2}:[0-9]{2}:[0-9]{2})");
    record Result(Map<String,String> values,String state,boolean corrected,Proof proof) { boolean accepted(){return state.equals("VERIFIED");} }
    record Proof(String order,String orderLabel,String time,String price,String recipient,String direction){
        enum Relation { SAME, DIFFERENT, UNKNOWN }
        Relation compare(Proof other){
            if(other==null||price.isEmpty()||other.price.isEmpty()||!direction.equals(other.direction))return Relation.UNKNOWN;
            if(!order.isEmpty()&&!other.order.isEmpty()){
                if(!orderLabel.equals(other.orderLabel))return Relation.UNKNOWN;
                return !order.equals(other.order)?Relation.DIFFERENT:price.equals(other.price)?Relation.SAME:Relation.UNKNOWN;
            }
            if(!time.isEmpty()&&!other.time.isEmpty()&&!price.isEmpty()&&price.equals(other.price)&&!recipient.isEmpty()&&recipient.equals(other.recipient)&&direction.equals(other.direction))
                return time.equals(other.time)?Relation.UNKNOWN:Relation.DIFFERENT;
            return Relation.UNKNOWN;
        }
        boolean matches(Map<?,?> fields){
            if(!price.equals(normalize(value(fields,"price"))))return false;
            if(!order.isEmpty())return order.equals(value(fields,"orderNumber"));
            return !time.isEmpty()&&!recipient.isEmpty()&&recipient.equals(value(fields,"recipient"))
                    &&time.equals(normalizeTime(value(fields,"paymentTime")));
        }
    }
    private static Pattern foreignCurrency(){
        // Include localized currency names and aliases used on payment pages.
        Set<String> names=new HashSet<>(List.of("韩币","韩圆","日币","日圆","港币","台币","泰币","美金","澳币","纽币"));
        for(Currency currency:Currency.getAvailableCurrencies()){
            if("CNY".equals(currency.getCurrencyCode()))continue;
            String name=currency.getDisplayName(Locale.SIMPLIFIED_CHINESE);
            if(!name.equals(currency.getCurrencyCode()))names.add(name);
        }
        StringJoiner localNames=new StringJoiner("|");for(String name:names)localNames.add(Pattern.quote(name));
        return Pattern.compile("[\\p{Sc}&&[^￥¥]]|"+localNames);
    }
    private static boolean foreignAmount(String text){
        if(FOREIGN.matcher(text).find()||FOREIGN_BEFORE.matcher(text).find())return true;
        Matcher suffix=FOREIGN_AFTER.matcher(text);
        for(Pattern pattern:List.of(ACTUAL,LABELED,CURRENCY)){
            Matcher amount=pattern.matcher(text);
            while(amount.find())if(suffix.region(amount.end(),text.length()).lookingAt())return true;
        }
        return false;
    }
    static Result verify(String text,Map<?,?> nativeValues){
        if(text==null||text.length()>24000||!text.contains("支付成功")||NEGATIVE.matcher(text).find())return reject("STATUS_NOT_CONFIRMED");
        if(foreignAmount(text))return reject("CURRENCY_NOT_SUPPORTED");
        Set<String> values=amounts(ACTUAL,text);
        if(values.isEmpty())values=amounts(LABELED,text);
        if(values.isEmpty())values=amounts(CURRENCY,text);
        if(values.size()!=1)return reject(values.isEmpty()?"AMOUNT_NOT_CONFIRMED":"AMOUNT_AMBIGUOUS");
        String price=values.iterator().next();BigDecimal amount=new BigDecimal(price);
        if(amount.signum()<=0||amount.compareTo(new BigDecimal("999999999.99"))>0)return reject("AMOUNT_NOT_CONFIRMED");
        Map<String,String> copy=new LinkedHashMap<>();nativeValues.forEach((k,v)->{if(k instanceof String key&&v instanceof String value)copy.put(key,value);});
        boolean corrected=!price.equals(normalize(copy.get("price")));copy.put("price",price);copy.put("paymentStatus","支付成功");
        // The native parser accepts merchant-name digits as an amount. Recover only
        // a directly observed merchant prefix when it demonstrably truncated that name.
        Matcher money=CURRENCY.matcher(text);int success=text.indexOf("支付成功")+4;
        if(money.find()&&money.start()>success&&money.start()-success<=80){
            String merchant=text.substring(success,money.start()).trim().replaceFirst("^微信支付\\s*","");String old=copy.get("recipient");
            if(merchant.matches("[\\p{L}\\p{N} .·()（）_\\-*&]{2,80}")&&!merchant.matches(".*(?:金额|付款方式|订单|时间|完成|返回).*")){
                if(old==null||old.isBlank()||(merchant.startsWith(old)&&merchant.length()>old.length()&&Character.isDigit(merchant.charAt(old.length()))))copy.put("recipient",merchant);
            }
        }
        Set<String> orders=new HashSet<>(),labels=new HashSet<>(),times=new HashSet<>();
        Matcher orderMatch=ORDER.matcher(text);
        while(orderMatch.find()){labels.add(orderMatch.group(1));orders.add(orderMatch.group(2));}
        Matcher timeMatch=TIME.matcher(text);
        while(timeMatch.find())times.add(normalizeTime(timeMatch.group(1)));
        String order=orders.size()==1?orders.iterator().next():"";
        String time=times.size()==1?times.iterator().next():"";
        String nativeOrder=copy.remove("orderNumber"),nativeTime=copy.remove("paymentTime");
        boolean conflict=orders.size()>1||labels.size()>1||times.size()>1
                ||(!order.isEmpty()&&nativeOrder!=null&&!nativeOrder.isBlank()&&!order.equals(nativeOrder.trim()))
                ||(!time.isEmpty()&&nativeTime!=null&&!nativeTime.isBlank()&&!time.equals(normalizeTime(nativeTime.trim())));
        Proof proof=null;
        if(!conflict){
            if(!order.isEmpty())copy.put("orderNumber",order);
            if(!time.isEmpty()){timeMatch.reset();timeMatch.find();copy.put("paymentTime",timeMatch.group(1));}
            if(!order.isEmpty()||!time.isEmpty())proof=new Proof(order,labels.size()==1?labels.iterator().next():"",
                    time,price,value(copy,"recipient"),value(copy,"transactionType"));
        }
        return new Result(Collections.unmodifiableMap(copy),"VERIFIED",corrected,proof);
    }
    private static String value(Map<?,?> data,String key){return data.get(key) instanceof String s?s.trim():"";}
    private static Result reject(String state){return new Result(Map.of(),state,false,null);}
    private static Set<String> amounts(Pattern pattern,String text){Set<String> values=new LinkedHashSet<>();Matcher m=pattern.matcher(text);while(m.find()){String n=normalize(m.group(1));if(n!=null)values.add(n);}return values;}
    private static String normalize(String value){try{return new BigDecimal(value.replace(",","").replace("，","").replace('．','.').trim()).setScale(2,java.math.RoundingMode.UNNECESSARY).toPlainString();}catch(Exception ignored){return null;}}
    private static String normalizeTime(String value){
        String text=value.replace('年','-').replace('月','-').replace("日","").replace('/','-').replaceAll("\\s+"," ");
        try{return LocalDateTime.parse(text,DateTimeFormatter.ofPattern("uuuu-M-d H:mm:ss").withResolverStyle(ResolverStyle.STRICT)).toString();}catch(Exception ignored){return "";}
    }
}
