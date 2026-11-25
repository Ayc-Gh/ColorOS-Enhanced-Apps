package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class SoundStatusPolicyTest {
    @Test public void stableMinuteNeedsSevenWritesIncludingInitial(){int writes=0;long last=-1;String previous=null;for(long now=0;now<=60_000;now+=1000)if(SoundStatusPolicy.write(now,last,previous,"active")){writes++;last=now;previous="active";}assertEquals(7,writes);}
    @Test public void changeAndClockResetWriteImmediately(){assertTrue(SoundStatusPolicy.write(1001,1000,"active","restored"));assertTrue(SoundStatusPolicy.write(0,1000,"active","active"));}
    @Test public void bothReadersShareHeartbeatFreshness(){assertTrue(SoundStatusPolicy.fresh(15_000,0,19,19));assertFalse(SoundStatusPolicy.fresh(15_001,0,19,19));assertTrue(SoundStatusPolicy.FRESH_MS>SoundStatusPolicy.HEARTBEAT_MS);assertTrue(SoundStatusPolicy.FRESH_MS<SoundPolicy.LEASE_MS);}
    @Test public void oldBuildAndFutureTimestampAreNeverHealthy(){assertFalse(SoundStatusPolicy.fresh(1000,0,18,19));assertFalse(SoundStatusPolicy.fresh(0,1000,19,19));assertFalse(SoundStatusPolicy.fresh(0,-1,19,19));}
    @Test public void staleOrLegacyRecordsDoNotDescribeTheCurrentBoot() {
        long sameUptime = 20_000, wall = 1_000_000;
        for(String message : new String[]{"已生效","已恢复系统原生应用范围","未生效","恢复失败"}) {
            var oldBoot = java.util.List.of("19500",message,"24","950000");
            assertNull(SoundStatusPolicy.current(oldBoot,sameUptime,wall,24));
            assertNull(SoundStatusPolicy.current(oldBoot.subList(0,3),sameUptime,wall,24));
            assertNull(SoundStatusPolicy.current(java.util.List.of("19500",message,"23","999500"),sameUptime,wall,24));
        }
    }
    @Test public void activeErrorsAndSuccessfulRestoreHaveDifferentLifetimes() {
        String restored="已恢复系统原生应用范围";
        assertEquals("已生效",SoundStatusPolicy.current(java.util.List.of("1000","已生效","24","201000"),16_000,216_000,24));
        assertNull(SoundStatusPolicy.current(java.util.List.of("1000","已生效","24","201000"),16_001,216_001,24));
        assertEquals("未生效",SoundStatusPolicy.current(java.util.List.of("1000","未生效","24","201000"),61_000,261_000,24));
        assertNull(SoundStatusPolicy.current(java.util.List.of("1000","未生效","24","201000"),61_001,261_001,24));
        assertEquals(restored,SoundStatusPolicy.current(java.util.List.of("1000",restored,"24","201000"),501_000,701_000,24));
    }
    @Test public void reconnectionStartsImmediatelyThenBacksOffToOneMinute() {
        var retry=new SoundStatusPolicy.Retry();
        long now=0;
        for(long delay:new long[]{10_000,20_000,40_000,60_000,60_000}) {
            assertTrue(retry.ready(now));
            retry.failed(now);
            assertFalse(retry.ready(now+delay-1));
            now+=delay;
        }
        retry.reset();
        assertTrue(retry.ready(1));
        retry.failed(1);
        assertTrue(retry.ready(10_001));
    }
    @Test public void switchAndPendingRestorationQualifyTheLastReport() {
        String waiting="开关已开启，正在等待声音组件连接", stopping="开关已关闭，等待原生状态确认";
        assertEquals(waiting,SoundStatusPolicy.describe(true,null,true));
        assertEquals(waiting,SoundStatusPolicy.describe(true,"已恢复系统原生应用范围",false));
        assertEquals("已生效",SoundStatusPolicy.describe(true,"已生效",true));
        assertEquals(waiting,SoundStatusPolicy.describe(true,"已生效",false));
        assertEquals(stopping,SoundStatusPolicy.describe(false,"已生效",true));
        assertEquals(stopping,SoundStatusPolicy.describe(false,null,true));
        assertEquals(stopping,SoundStatusPolicy.describe(false,"已恢复系统原生应用范围",true));
        assertEquals("声音增强已关闭",SoundStatusPolicy.describe(false,null,false));
        assertEquals("恢复失败",SoundStatusPolicy.describe(false,"恢复失败",true));
    }
}
