// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.util.Log;
import io.github.oshin.windowcapture.CameraFilterResources;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

final class LogMonitorPresets {
    private static final String TAG = "X7UVideoLut";
    private static final String ASSETS = "assets/camera-log-monitor/";

    enum Preset {
        SDR(9, "log_video_sdr_3dlut.cube", 661_823,
                "6678b3cc7a1a44c705d8c5ce962cdc6fcc7ba64acd055ba02dcd475505fb890b"),
        HDR(10, "log_video_hdr_3dlut.cube", 926_896,
                "82b5309912522b9c17d2105ab074bf53023b352ae37acc0819b9fbcbf17615fa");

        final int id;
        final String fileName;
        final int byteCount;
        final String sha256;

        Preset(int id, String fileName, int byteCount, String sha256) {
            this.id = id;
            this.fileName = fileName;
            this.byteCount = byteCount;
            this.sha256 = sha256;
        }

        static Preset fromPosition(int position) {
            return switch (position) {
                case 1 -> SDR;
                case 2 -> HDR;
                default -> null;
            };
        }

        File nativeFile(String directory) {
            return new File(directory, fileName);
        }
    }

    private final CameraModule module;
    private final Map<Preset, VideoCube> cubes = new EnumMap<>(Preset.class);
    private final Set<Preset> failed = EnumSet.noneOf(Preset.class);
    private volatile boolean prepared;

    LogMonitorPresets(CameraModule module) {
        this.module = module;
    }

    static boolean readable(File file) {
        return file.isFile() && file.canRead();
    }

    boolean hasPreparedFallback() {
        return prepared;
    }

    synchronized VideoCube prepared(Preset preset) {
        return cubes.get(preset);
    }

    synchronized VideoCube prepare(Preset preset) {
        var cube = cubes.get(preset);
        if (cube == null && !failed.contains(preset)) {
            try (var archive = new ZipFile(module.getModuleApplicationInfo().sourceDir)) {
                var entry = archive.getEntry(ASSETS + preset.fileName);
                if (entry == null || entry.getSize() != preset.byteCount) {
                    throw new IOException("Invalid LUT asset size");
                }
                byte[] bytes;
                try (var input = archive.getInputStream(entry)) {
                    bytes = input.readNBytes(preset.byteCount + 1);
                }
                if (bytes.length != preset.byteCount || !CameraFilterResources.hasSha256(bytes, preset.sha256)) {
                    throw new IOException("LUT asset digest mismatch");
                }
                cube = VideoCube.open(new ByteArrayInputStream(bytes));
                cubes.put(preset, cube);
                prepared = true;
                module.log(Log.INFO, TAG, "Prepared bundled " + preset.name() + " monitor LUT, id=" + preset.id);
            } catch (Exception error) {
                failed.add(preset);
                module.log(Log.WARN, TAG, "Cannot load bundled " + preset.name() + " monitor LUT", error);
            }
        }
        return cube;
    }
}
