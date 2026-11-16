// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

final class WindowAppearancePolicy {
    static boolean applies(int scenario,int activityType,boolean embedded,boolean caption){return scenario==1&&activityType==1&&!embedded&&caption;}
    static int radius(int configured,int original){return configured<0?original:Math.min(configured,300);}
    private WindowAppearancePolicy(){}
}
