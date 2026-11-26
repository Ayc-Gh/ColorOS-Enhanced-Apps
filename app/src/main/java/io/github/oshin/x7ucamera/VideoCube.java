// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class VideoCube implements AutoCloseable {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MFD_ALLOW_SEALING = 2;
    private static final int F_ADD_SEALS = 1033;
    private static final int ALL_SEALS = 15;
    private final ParcelFileDescriptor descriptor;
    final int size;
    final float[] points;
    final int retainedBytes;

    private VideoCube(ParcelFileDescriptor descriptor, int size, float[] points, int byteCount) {
        this.descriptor = descriptor;
        this.size = size;
        this.points = points;
        this.retainedBytes = byteCount + points.length * Float.BYTES;
    }

    static VideoCube open(InputStream input) throws Exception {
        if (input == null) throw new IOException("无法读取文件");
        byte[] raw = input.readNBytes(MAX_BYTES + 1);
        if (raw.length > MAX_BYTES) throw new IOException("文件超过 16 MB");
        var lines = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8));
        var normalized = new StringBuilder(raw.length);
        int size = 0;
        int count = 0;
        float[] points = null;
        for (String line; (line = lines.readLine()) != null;) {
            if (line.length() > 4096) throw new IOException("LUT 包含过长的数据行");
            if (line.startsWith("\ufeff")) line = line.substring(1);
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("TITLE ")) continue;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment).stripTrailing();
            String[] data = line.split("\\s+", 5);
            switch (data[0]) {
                case "DOMAIN_MIN", "DOMAIN_MAX" -> {
                    float expected = "DOMAIN_MIN".equals(data[0]) ? 0 : 1;
                    if (count != 0 || data.length != 4 || value(data[1]) != expected
                            || value(data[2]) != expected || value(data[3]) != expected) {
                        throw new IOException("LUT 输入范围须为 0–1");
                    }
                }
                case "LUT_3D_INPUT_RANGE" -> {
                    if (count != 0 || data.length != 3 || value(data[1]) != 0 || value(data[2]) != 1) {
                        throw new IOException("LUT 输入范围须为 0–1");
                    }
                }
                case "LUT_3D_SIZE" -> {
                    if (size != 0 || data.length != 2) throw new IOException("无效的 LUT_3D_SIZE");
                    try { size = Integer.parseInt(data[1]); }
                    catch (NumberFormatException error) { throw new IOException("无效的 LUT_3D_SIZE", error); }
                    if (size < 2 || size > 65) throw new IOException("3D LUT 边长应介于 2 和 65");
                    points = new float[size * size * size * 3];
                    normalized.append("LUT_3D_SIZE ").append(size).append("\n#LUT data points\n");
                }
                default -> {
                    if (size == 0 || data.length != 3 || ++count > size * size * size) {
                        throw new IOException("3D LUT 数据行不完整或格式不受支持");
                    }
                    for (int channel = 0; channel < 3; channel++) {
                        points[(count - 1) * 3 + channel] = Math.min(1f, Math.max(0f, value(data[channel])));
                    }
                    normalized.append(data[0]).append(' ').append(data[1]).append(' ').append(data[2]).append('\n');
                }
            }
        }
        if (size == 0 || count != size * size * size) throw new IOException("3D LUT 数据点数量不匹配");
        byte[] bytes = normalized.toString().getBytes(StandardCharsets.US_ASCII);
        var fd = Os.memfd_create("x7u-video-lut", OsConstants.MFD_CLOEXEC | MFD_ALLOW_SEALING);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int written = Os.write(fd, bytes, offset, bytes.length - offset);
                if (written <= 0) throw new IOException("无法保存会话 LUT");
                offset += written;
            }
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            Os.fcntlInt(fd, F_ADD_SEALS, ALL_SEALS);
            return new VideoCube(ParcelFileDescriptor.dup(fd), size, points, bytes.length);
        } finally {
            Os.close(fd);
        }
    }

    String path() { return "/proc/self/fd/" + descriptor.getFd(); }

    @Override
    public void close() throws IOException { descriptor.close(); }

    private static float value(String text) throws IOException {
        try {
            float number = Float.parseFloat(text);
            if (Float.isFinite(number)) return number;
        } catch (NumberFormatException ignored) { }
        throw new IOException("LUT 包含无效数值");
    }
}
