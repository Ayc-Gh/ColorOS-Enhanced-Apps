package io.github.oshin.windowcapture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsScopeTest {
    @Test public void displayedHostsComeFromPackagedScopeIncludingFutureNames()throws Exception{
        var apk=Files.createTempFile("scope-test",".zip");
        try{
            try(var zip=new ZipOutputStream(Files.newOutputStream(apk))){
                zip.putNextEntry(new ZipEntry("META-INF/xposed/scope.list"));
                zip.write("system\ncom.oplus.atlas\n# ignored\n\nexample.future\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            String summary=SettingsScope.summary(apk.toString(),Set.of("com.oplus.atlas","example.future","com.oplus.aod"));
            assertEquals("系统框架未勾选 · Atlas 服务已勾选\nexample.future已勾选",summary);
        }finally{Files.delete(apk);}
    }
}
