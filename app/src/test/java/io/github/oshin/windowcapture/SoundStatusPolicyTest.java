package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class SoundStatusPolicyTest {
    @Test public void stableMinuteNeedsSevenWritesIncludingInitial(){int writes=0;long last=-1;String previous=null;for(long now=0;now<=60_000;now+=1000)if(SoundStatusPolicy.write(now,last,previous,"active")){writes++;last=now;previous="active";}assertEquals(7,writes);}
    @Test public void changeAndClockResetWriteImmediately(){assertTrue(SoundStatusPolicy.write(1001,1000,"active","restored"));assertTrue(SoundStatusPolicy.write(0,1000,"active","active"));}
    @Test public void bothReadersShareHeartbeatFreshness(){assertTrue(SoundStatusPolicy.fresh(15_000,0,19,19));assertFalse(SoundStatusPolicy.fresh(15_001,0,19,19));assertTrue(SoundStatusPolicy.FRESH_MS>SoundStatusPolicy.HEARTBEAT_MS);assertTrue(SoundStatusPolicy.FRESH_MS<SoundPolicy.LEASE_MS);}
    @Test public void oldBuildAndFutureTimestampAreNeverHealthy(){assertFalse(SoundStatusPolicy.fresh(1000,0,18,19));assertFalse(SoundStatusPolicy.fresh(0,1000,19,19));assertFalse(SoundStatusPolicy.fresh(0,-1,19,19));}
}
