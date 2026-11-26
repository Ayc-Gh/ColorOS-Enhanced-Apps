// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import io.github.oshin.windowcapture.CameraFilterResources;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

final class LutFiles implements AutoCloseable {
    private static final String PREFIX = "assets/camera-filters/";
    private static final List<String> MASTER_FILES = List.of(
            "portra400_master.bin", "positive_master.bin", "blackandwhite_HC_master.bin",
            "portra400_sdr_gen_a_1.bin", "portra400_sdr_gen_d_1.bin",
            "positive_sdr_gen_a_1.bin", "positive_sdr_gen_d_1.bin",
            "blackandwhite_HC_sdr.bin", "blackandwhite_HC_sdr_0.bin", "blackandwhite_HC_sdr_1.bin",
            "portra400_hdr_master_a_1.bin", "portra400_hdr_master_d_1.bin",
            "positive_hdr_master_a_1.bin", "positive_hdr_master_d_1.bin",
            "blackandwhite_HC_hdr_master.bin", "blackandwhite_HC_hdr_master_0.bin",
            "blackandwhite_HC_hdr_master_1.bin");
    private static final int MFD_ALLOW_SEALING = 2;
    private static final int F_ADD_SEALS = 1033;
    private static final int ALL_SEALS = 15;
    private final Map<String, ParcelFileDescriptor> files = new HashMap<>();
    private final Map<String, String> namesByPath = new HashMap<>();

    void load(String moduleApk) throws Exception {
        try (ZipFile archive = new ZipFile(moduleApk)) {
            for (String name : MASTER_FILES) {
                var entry = archive.getEntry(PREFIX + name);
                if (entry == null || entry.getSize() < 1 || entry.getSize() > 900_000) {
                    throw new IOException("Invalid LUT " + name);
                }
                byte[] bytes;
                try (var input = archive.getInputStream(entry)) { bytes = input.readAllBytes(); }
                if (!CameraFilterResources.matches(name, bytes)) throw new IOException("LUT digest mismatch: " + name);
                var fd = Os.memfd_create("x7u-film-" + name, OsConstants.MFD_CLOEXEC | MFD_ALLOW_SEALING);
                try {
                    int offset = 0;
                    while (offset < bytes.length) {
                        int written = Os.write(fd, bytes, offset, bytes.length - offset);
                        if (written <= 0) throw new IOException("Short LUT write: " + name);
                        offset += written;
                    }
                    Os.lseek(fd, 0, OsConstants.SEEK_SET);
                    Os.fcntlInt(fd, F_ADD_SEALS, ALL_SEALS);
                    files.put(name, ParcelFileDescriptor.dup(fd));
                    namesByPath.put(path(name), name);
                } finally {
                    Os.close(fd);
                }
            }
        }
    }

    String path(String name) {
        var fd = files.get(name);
        return fd == null ? null : "/proc/self/fd/" + fd.getFd();
    }

    String name(String path) { return namesByPath.get(path); }

    String remap(String list, String fallbackPrefix, String extraPrefix) {
        String[] parts = list.split(",", -1);
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String path = path(parts[i]);
            if (path != null) {
                parts[i] = extraPrefix + path;
                changed = true;
            } else {
                parts[i] = fallbackPrefix + parts[i];
            }
        }
        return changed ? String.join(",", parts) : null;
    }

    String contrastPair(String name, int strength, String prefix) {
        if (!"blackandwhite_HC_sdr.bin".equals(name) && !"blackandwhite_HC_hdr_master.bin".equals(name)) {
            return null;
        }
        String stem = name.substring(0, name.length() - 4);
        String first = strength < 50 ? stem + "_0.bin" : name;
        String second = strength < 50 ? name : stem + "_1.bin";
        return prefix + path(first) + "," + prefix + path(second);
    }

    static float contrastFactor(int strength) {
        int clamped = Math.max(0, Math.min(100, strength));
        return (clamped < 50 ? clamped : clamped - 50) / 50f;
    }

    @Override public void close() {
        for (var descriptor : files.values()) {
            try { descriptor.close(); } catch (IOException ignored) { }
        }
        files.clear();
        namesByPath.clear();
    }
}
