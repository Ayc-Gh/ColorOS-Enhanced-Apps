package io.github.oshin.windowcapture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CastEncoderPolicyTest {
    @Test public void actualSelectedEncoderLimitsRequest(){assertEquals(60,CastPolicy.frameRate(120,false,CastEncoderPolicy.ceiling(fps->fps<=60)));assertEquals(0,CastPolicy.frameRate(120,false,CastEncoderPolicy.ceiling(fps->false)));}
    @Test public void neverAssumesSupportFromARequest(){assertEquals(90,CastEncoderPolicy.ceiling(fps->fps<=90));assertEquals(0,CastPolicy.frameRate(0,false,120));}
}
