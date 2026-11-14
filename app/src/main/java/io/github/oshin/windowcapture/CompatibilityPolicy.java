// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
public final class CompatibilityPolicy {
    public static final String ROM="PHY110_17.0.0.100(CN01)";
    public static boolean rom(String model,int sdk,String display){return "PHY110".equals(model)&&sdk==37&&ROM.equals(display);}
    public static long expected(String host){return switch(host){case "com.oplus.cast"->170017L;case "com.oplus.gesture"->8600L;case "com.oplus.trafficmonitor"->17003005L;case "com.oplus.gleanerservice"->17008000L;case "com.coloros.gallery3d"->17010066L;case "com.oplus.camera"->60000L;case "com.android.systemui"->179902L;case "com.oplus.aod"->170010021L;default->-1L;};}
    public static boolean host(String host,long code){return expected(host)>0&&expected(host)==code;}
    private CompatibilityPolicy(){}
}
