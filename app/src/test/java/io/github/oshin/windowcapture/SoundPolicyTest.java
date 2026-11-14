// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SoundPolicyTest {
 private static final String XML="<config><other>keep</other><mss-whitelist>\n<name>com.native.music</name><attribute>3</attribute>\n<name>com.native.study</name><attribute>17</attribute>\n<name>com.foreign.music</name><attribute>2</attribute>\n</mss-whitelist><tail>same</tail></config>";
 @Test public void removesApplicationGateAndCategoryExclusion(){var p=SoundPolicy.expand(XML,List.of("com.native.music","com.native.study","com.foreign.music","org.new.video"));assertEquals(Map.of("com.native.music",3,"com.native.study",1,"com.foreign.music",3,"org.new.video",3),SoundPolicy.entries(p.xml()));assertEquals(Set.of("com.native.study","com.foreign.music","org.new.video"),p.expanded());assertTrue(p.xml().startsWith("<config><other>keep</other>"));assertTrue(p.xml().endsWith("<tail>same</tail></config>"));}
 @Test public void invalidPackageCannotInjectXml(){var p=SoundPolicy.expand(XML,List.of("evil</name>","bad;reboot","org.valid.player"));assertEquals(Set.of("org.valid.player"),p.expanded());assertEquals(4,p.total());}
 @Test(expected=IllegalArgumentException.class) public void refusesUnknownPolicyShape(){SoundPolicy.expand(XML.replace("<attribute>17</attribute>","<attr>17</attr>"),List.of("org.new.video"));}
 @Test(expected=IllegalArgumentException.class) public void refusesMultipleMssSections(){SoundPolicy.expand(XML+"<mss-whitelist></mss-whitelist>",List.of());}
 @Test public void leaseStopsAtHostDeathExpiryOrReboot(){assertTrue(SoundPolicy.leaseValid(40_000,20_000,123,true));assertFalse(SoundPolicy.leaseValid(50_001,20_000,123,true));assertFalse(SoundPolicy.leaseValid(1000,20_000,123,true));assertFalse(SoundPolicy.leaseValid(40_000,20_000,123,false));assertFalse(SoundPolicy.leaseValid(40_000,20_000,0,true));}
 @Test public void unrelatedAttributeBitsAndUninstalledApplicationsStayNative(){String xml=XML.replace("<attribute>17</attribute>","<attribute>81</attribute>");var p=SoundPolicy.expand(xml,List.of("com.native.study"));assertEquals(Integer.valueOf(65),SoundPolicy.entries(p.xml()).get("com.native.study"));assertEquals(Integer.valueOf(2),SoundPolicy.entries(p.xml()).get("com.foreign.music"));}
 @Test public void restorationAcceptsOriginalCategoryButRejectsExpandedValue(){assertTrue(SoundPolicy.restoredAttribute("17",17));assertTrue(SoundPolicy.restoredAttribute(null,17));assertFalse(SoundPolicy.restoredAttribute("1",17));assertFalse(SoundPolicy.restoredAttribute("3",null));assertTrue(SoundPolicy.restoredAttribute(null,null));assertFalse(SoundPolicy.restoredAttribute("invalid",17));}
}
