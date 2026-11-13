// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

final class NetworkPolicy {
    static final String HOST="com.oplus.trafficmonitor", KEY="network_options";
    static boolean eligible(boolean enabled,int uid,boolean internet){int appId=uid%100000;return enabled&&uid>=0&&appId>=10000&&appId<=19999&&internet;}
    private NetworkPolicy(){}
}
