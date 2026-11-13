// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.Map;
import org.json.JSONObject;

/** Process-local technical counters. No bill values, text, identifiers or screenshots are retained. */
final class AimemoryTrace {
    private int pages,attempts,validMaps,requests,accepted,ocr,duplicates,verified,blocked,corrected,preciseDedup;
    private int fields;
    private String page="none",state="idle",nativeResult="unknown",ocrResult="unknown",reason="none";
    private String validation="unknown";
    synchronized void page(String name){pages++;page=AimemoryPolicy.pageKind(name);state="page";fields=0;reason="none";nativeResult="unknown";ocrResult="unknown";}
    synchronized void start(String name){attempts++;page=AimemoryPolicy.pageKind(name);state="extracting";}
    synchronized void data(Map<?,?> map){fields=fieldMask(map);if((fields&3)==3){validMaps++;state="fields";}else state="missing_fields";}
    synchronized void request(){requests++;state="requested";}
    synchronized void accepted(boolean ok){if(ok){accepted++;state="accepted";}else state="rejected";}
    synchronized void duplicate(){duplicates++;state="duplicate";}
    synchronized void nativeResult(String value){nativeResult=code(value);}
    synchronized void ocr(){ocr++;}
    synchronized void ocrResult(String value){ocrResult=code(value);}
    synchronized void rejected(String value){reason=code(value);state="rejected";}
    synchronized void validation(String value,boolean changed){validation=code(value);if("VERIFIED".equals(value)){verified++;if(changed)corrected++;}else{blocked++;state="validation_blocked";}}
    synchronized void preciseDedup(){preciseDedup++;}
    synchronized JSONObject json()throws Exception{return new JSONObject().put("pages",pages).put("attempts",attempts).put("validMaps",validMaps).put("requests",requests).put("accepted",accepted).put("ocr",ocr).put("duplicates",duplicates).put("fields",fields).put("page",page).put("state",state).put("nativeResult",nativeResult).put("ocrResult",ocrResult).put("reason",reason).put("validation",validation).put("verified",verified).put("blocked",blocked).put("corrected",corrected).put("preciseDedup",preciseDedup);}
    static int fieldMask(Map<?,?> map){int mask=0;String[] keys={"price","paymentStatus","recipient","orderNumber","paymentTime"};for(int i=0;i<keys.length;i++)if(map.get(keys[i]) instanceof String value&&!value.isBlank())mask|=1<<i;return mask;}
    private static String code(String value){return value!=null&&value.matches("[A-Z_]{2,48}")?value:"unknown";}
}
