// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Unit-explicit policy shared by the injected hook and host-side checks. */
public final class CastPolicy {
    private CastPolicy(){}
    public static int[] bitrateKbps(int originalMin,int originalMax,int quality,int customMbps,boolean lowBandwidth,boolean game) {
        int min=originalMin,max=originalMax;
        if(customMbps>=2&&customMbps<=40)return new int[]{customMbps*1000,customMbps*1000};
        if(quality==2||game){min=Math.max(min,20000);max=Math.max(max,32000);}
        else if(quality==1){min=Math.max(min,8000);max=Math.max(max,16000);}
        if(lowBandwidth){min=Math.max(min,8000);max=Math.max(max,16000);}
        return new int[]{min,Math.max(min,max)};
    }
    /** A ceiling, never upscales or changes the current content aspect ratio. */
    public static int[] fit(int width,int height,int physicalWidth,int physicalHeight,int mode) {
        if(mode==0||width<2||height<2||physicalWidth<2||physicalHeight<2)return new int[]{width,height};
        int physicalShort=Math.min(physicalWidth,physicalHeight),physicalLong=Math.max(physicalWidth,physicalHeight);
        int shortLimit=mode==1?Math.min(1080,physicalShort):mode==2?Math.min(1440,physicalShort):physicalShort;
        double longLimit=(double)physicalLong*shortLimit/physicalShort;
        double ratio=Math.min(1,Math.min((double)shortLimit/Math.min(width,height),longLimit/Math.max(width,height)));
        return new int[]{Math.max(2,((int)Math.floor(width*ratio)/2)*2),Math.max(2,((int)Math.floor(height*ratio)/2)*2)};
    }
    public static int frameRate(int requested,boolean game,int maxHardware) {
        int target=game?Math.max(requested,120):requested;
        if(target!=60&&target!=90&&target!=120)return 0;
        return Math.max(0,Math.min(target,maxHardware));
    }
}
