// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.os.Build;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.oshin.windowcapture.CompatibilityPolicy;
import io.github.oshin.windowcapture.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class CameraModule extends XposedModule {
    static final String TAG = "X7UCameraUnlock";
    private static final String CAMERA = "com.oplus.camera";
    private static final String CAMERA_SHA256 = "9eed439a83d6f4e9e666619626cd5371cc31da7db31a05138f35745cc8c2aa97";
    private static volatile String startupState = "pending";
    private static volatile VideoLut videoLut;
    private String processName;
    private boolean started;

    public static String startupState() { return startupState; }
    public static boolean lutMonitorInstalled() { return videoLut != null; }
    public static String gpuState() {
        VideoLut current = videoLut;
        return current == null ? "unavailable" : current.gpuState();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public synchronized void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!CAMERA.equals(param.getPackageName()) || !CAMERA.equals(processName)
                || !param.isFirstPackage() || started) return;
        started = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || !CompatibilityPolicy.rom(Build.MODEL, Build.VERSION.SDK_INT, Build.DISPLAY)) {
            startupState = "unsupported_firmware";
            return;
        }
        try {
            // Camera menus and feature tables are initialized once per camera process.
            if (!getRemotePreferences(Config.PREFS).getBoolean(Config.CAMERA_X7U_ENHANCEMENTS, false)) {
                startupState = "off";
                return;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(Path.of(param.getApplicationInfo().sourceDir))) {
                byte[] buffer = new byte[1024 * 1024];
                int size;
                while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
            }
            if (!CAMERA_SHA256.equals(HexFormat.of().formatHex(digest.digest()))) {
                startupState = "unsupported_camera";
                log(Log.WARN, TAG, "X7 Ultra camera enhancements require camera APK 7.006.191 with the verified digest");
                return;
            }
            installFeatures(param.getClassLoader());
        } catch (Exception | LinkageError error) {
            startupState = "failed";
            log(Log.ERROR, TAG, "X7 Ultra camera enhancement initialization failed", error);
        }
    }

    private void installFeatures(ClassLoader loader) {
        int installed = 0;
        try {
            try {
                new MasterFilm(this, loader).install();
                installed++;
                info("Master film ready for PHY110 camera 7.006.191");
            } catch (Exception | LinkageError error) {
                log(Log.ERROR, TAG, "Master film initialization failed", error);
            }
            try {
                VideoLut adapter = new VideoLut(this, loader);
                adapter.install();
                videoLut = adapter;
                installed++;
            } catch (Exception | LinkageError error) {
                // Center indicator, EV and 4:3 use the settings isolation installed by VideoLut.
                log(Log.ERROR, TAG, "Video LUT initialization failed; skipping its dependent features", error);
                return;
            }
            try {
                NativeCapabilities.install(this, loader);
                installed++;
            } catch (Exception | LinkageError error) {
                log(Log.ERROR, TAG, "Native camera capability initialization failed", error);
            }
            try {
                OpenGate.install(this, loader);
                installed++;
            } catch (Exception | LinkageError error) {
                log(Log.ERROR, TAG, "4:3 recording initialization failed", error);
            }
        } finally {
            startupState = installed == 4 ? "ready" : installed == 0 ? "failed" : "partial";
        }
    }

    void info(String message) {
        Log.i(TAG, message);
    }
}
