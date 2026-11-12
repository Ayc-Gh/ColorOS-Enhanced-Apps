import io.github.oshin.windowcapture.CastPolicy;
import java.util.Arrays;
public final class CastPolicyChecks {
    private static int cases;
    private static void eq(int[] actual,int...expected){cases++;if(!Arrays.equals(actual,expected))throw new AssertionError(Arrays.toString(actual)+" != "+Arrays.toString(expected));}
    public static void main(String[] args){
        eq(CastPolicy.bitrateKbps(6000,12000,0,-1,false,false),6000,12000);
        eq(CastPolicy.bitrateKbps(6000,12000,1,-1,false,false),8000,16000);
        eq(CastPolicy.bitrateKbps(24000,40000,1,-1,false,false),24000,40000);
        eq(CastPolicy.bitrateKbps(6000,12000,2,-1,false,false),20000,32000);
        eq(CastPolicy.bitrateKbps(6000,12000,2,12,false,true),12000,12000);
        eq(CastPolicy.bitrateKbps(6000,12000,0,2,true,false),2000,2000);
        eq(CastPolicy.bitrateKbps(6000,12000,0,41,false,false),6000,12000);
        eq(CastPolicy.fit(1440,3168,1440,3168,1),1080,2376);
        eq(CastPolicy.fit(3168,1440,1440,3168,1),2376,1080);
        eq(CastPolicy.fit(1440,3168,1440,3168,2),1440,3168);
        eq(CastPolicy.fit(1440,3168,1440,3168,3),1440,3168);
        eq(CastPolicy.fit(720,1584,1440,3168,1),720,1584);
        eq(CastPolicy.fit(1440,3168,0,0,1),1440,3168);
        if(CastPolicy.frameRate(120,false,90)!=90||CastPolicy.frameRate(0,false,120)!=0||CastPolicy.frameRate(0,true,60)!=60||CastPolicy.frameRate(120,false,0)!=0)throw new AssertionError("fps caps");
        System.out.println("PASS "+(cases+4)+" policy checks");
    }
}
