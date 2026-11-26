// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.util.Set;

/** Enables the X7 Ultra camera paths verified against native output. */
final class NativeCapabilities {
    private static final Set<String> VERIFIED_GATES = Set.of(
            "com.oplus.feature.master.video.24fps.support",
            "com.oplus.feature.monitor.assist.support",
            "com.oplus.feature.fast.video.resolution.switch",
            "com.oplus.nightmode.support.more.lens.focal",
            "com.oplus.camera.video.bitrate.increase.support",
            "com.oplus.feature.assist.center.indicator.support",
            "com.oplus.video.global.ev.support",
            "com.oplus.portrait.global.ev.support",
            "com.oplus.high_pixel.global.ev.support");

    private NativeCapabilities() { }

    static void install(CameraModule module, ClassLoader loader) throws ReflectiveOperationException {
        var config = Class.forName("com.oplus.camera.configure.CameraConfig", false, loader);
        var professionalVideo = Class.forName("um.d", false, loader);
        var lookup = config.getDeclaredMethod("d", String.class, boolean.class, boolean.class);
        var booleanValue = config.getDeclaredMethod("getConfigBooleanValue", String.class);
        module.deoptimize(lookup);
        module.deoptimize(booleanValue);
        module.deoptimize(config.getDeclaredMethod("K"));
        module.deoptimize(professionalVideo.getDeclaredMethod("De", String.class));
        module.deoptimize(professionalVideo.getDeclaredMethod("getSupportFunction", String.class));

        XposedInterface.Hooker enabled = chain -> chain.getArg(0) instanceof String key
                && VERIFIED_GATES.contains(key) ? true : chain.proceed();
        var defaultLookup = module.hook(lookup).intercept(enabled);
        boolean installed = false;
        try {
            module.hook(booleanValue).intercept(enabled);
            installed = true;
        } finally {
            if (!installed) defaultLookup.unhook();
        }
        module.log(Log.INFO, "X7UNativeCapabilities", "Verified native camera gates installed");
    }
}
