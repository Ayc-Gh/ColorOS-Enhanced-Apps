// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import org.junit.Test;
import static org.junit.Assert.*;

public class SoundCompanionCommandTest {
    @Test public void reloadKeepsPinnedCodeIndependentOfExpiredLeaseApkPath() {
        assertEquals("/proc/3124/fd/3", SoundCompanionCommand.classpath("/proc/3124/fd/3", "/data/app/removed/base.apk"));
    }
    @Test public void directRootInvocationRemainsCompatible() {
        assertEquals("/data/app/module/base.apk", SoundCompanionCommand.classpath("/data/app/module/base.apk", "/data/app/module/base.apk"));
    }
    @Test public void rejectsUnrelatedFilesAndMultiEntryClasspaths() {
        for (String value : new String[]{"/data/local/tmp/other.apk", "/proc/0/fd/3", "/proc/self/fd/3", "/proc/12/fd/4", "/proc/12/fd/3:/other.apk", "", null}) {
            try { SoundCompanionCommand.classpath(value, "/data/app/module/base.apk"); fail("Accepted: " + value); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNulArgument() { SoundCompanionCommand.quote("bad\0path"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidMainClass() { SoundCompanionCommand.script("/dir", "/apk", "class; bad"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingApk() { SoundCompanionCommand.script("/dir", "", "org.example.Main"); }
}
