// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import io.github.libxposed.api.XposedInterface;
import io.github.oshin.windowcapture.R;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class VideoLut {
    private static final String CAMERA = "com.oplus.camera";
    private static final String TAG = "X7UVideoLut";
    private static final String SELECT_LUT = "com.android.camera.action.SELECT_LUT";
    private static final String OEM_DIRECTORY = "/data/vendor/camera_process/customize_lut";
    private static final String DEFAULTS_COPIED = "pref_master_video_default_lut_copied";
    private static final String RECORD_EFFECT = "pref_log_video_record_effect_switch";
    private static final String MONITOR_STATE = "pref_log_video_monitor_state";
    private static final String LUT_MODE = "pref_log_lut_video_key";
    private static final int REQUEST_LUT = 11115;
    private static final int LIMIT = 20;
    private static final int MEMORY_LIMIT = 64 * 1024 * 1024;
    private static final int DISPLAY_LUT = -27107;
    private static final int DISPLAY_HDR_LUT = -27108;
    private static final int EXTERNAL_TEXTURE = 36197;
    private static final String CENTER_INDICATOR = "pref_assist_center_indicator";
    private static final String NATIVE_SETTINGS_PATH = "/com.oplus.camera_preferences_x7u_unlock";
    private static final Set<String> NATIVE_SETTINGS = Set.of(
            CENTER_INDICATOR, "pref_global_exposure_compensation_key_commonVideo",
            "pref_global_exposure_compensation_key_portrait", "pref_global_exposure_compensation_key_highPixel");
    private static final Set<String> VIDEO_SETTINGS = Set.of(
            "pref_master_video_log_tone_mode", "pref_master_video_custom_lut_name",
            "pref_master_video_last_lut_tone_mode", "pref_master_video_last_custom_lut_name",
            MONITOR_STATE, LUT_MODE, RECORD_EFFECT, DEFAULTS_COPIED,
            "pref_master_video_format_key", "pref_log_video_key",
            "pref_master_video_fps_key", "pref_master_video_size_key",
            "pref_master_video_size_and_fps", "pref_master_video_eis_key",
            "pref_lasted_master_video_fps", "pref_lasted_master_video_size",
            "pref_master_video_fps_restore_key", "pref_master_video_size_restore_key",
            "pref_fast_video_size_key", "pre_camera_video_bitrate", "pref_pro_histogram_key",
            "key_waveform_histogram_switch_tip", "pref_camera_global_ev_key");

    private record Entry(Object model, VideoCube cube) { }
    private record DisplayFrame(Object canvas, VideoCube cube, LogMonitorPresets.Preset preset) { }
    private final CameraModule module;
    private final ClassLoader loader;
    private final List<XposedInterface.HookHandle> hooks = new ArrayList<>();
    private final Map<Uri, Object> settings = new ConcurrentHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    // Keep old descriptors alive until process exit because the native async reader can still hold a path.
    private final List<VideoCube> openCubes = new ArrayList<>();
    private int retainedBytes;
    private final ExecutorService importWorker = Executors.newSingleThreadExecutor();
    private final LogMonitorPresets monitorPresets;
    private volatile VideoCube selected;
    private volatile boolean burnRequested;
    private final GpuVideoBurn gpuBurn;
    private volatile boolean ready;

    VideoLut(CameraModule module, ClassLoader loader) {
        this.module = module;
        this.loader = loader;
        monitorPresets = new LogMonitorPresets(module);
        gpuBurn = new GpuVideoBurn(module, loader, () -> ready && burnRequested, () -> selected);
    }

    String gpuState() { return gpuBurn.diagnosticState(); }

    void install() throws Exception {
        try {
            installSettings();
            installSessionDirectory();
            installPicker();
            installPreview();
            installMonitorPresets();
            attach(type("o9.h").getDeclaredMethod("c", int.class, int.class), chain -> {
                int index = (Integer) chain.getArg(0);
                return ready && (index == 4 || index == 8) ? 1 : chain.proceed();
            });
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    gpuBurn.install();
                }
            } catch (Exception | LinkageError error) {
                module.log(Log.WARN, TAG, "GPU recording setup unavailable on this camera build", error);
            }
            ready = true;
            note("Custom video LUT import and monitor hooks ready");
        } finally {
            if (!ready) {
                for (var hook : hooks) hook.unhook();
                hooks.clear();
                importWorker.shutdownNow();
            }
        }
    }

    private void installPreview() throws Exception {
        Class<?> canvas = type("com.oplus.camera.common.gl.o");
        var shader = type("com.oplus.camera.common.gl.a1").getDeclaredConstructor(int.class, int.class);
        var textureShader = canvas.getField("h0");
        var oesShader = canvas.getField("i0");
        textureShader.setAccessible(true);
        oesShader.setAccessible(true);
        var isLinked = shader.getDeclaringClass().getDeclaredMethod("g");
        var activeType = canvas.getField("v");
        var useManagedDisplayColor = canvas.getField("y");
        attach(canvas.getDeclaredMethod("z0"), chain -> switch (activeType.getInt(chain.getThisObject())) {
            case DISPLAY_LUT -> useManagedDisplayColor.getBoolean(chain.getThisObject());
            case DISPLAY_HDR_LUT -> false;
            default -> chain.proceed();
        });
        var renderer = type("qn.q");
        var managerField = renderer.getField("y");
        var displayCanvas = renderer.getField("u");
        var modeHolder = type("gm.r3").getField("a");
        var activeMode = type("gm.f5").getField("a");
        var masterVideo = type("um.d");
        var isLog = masterVideo.getDeclaredMethod("vk");
        var monitorSelection = masterVideo.getDeclaredMethod("Yj");
        var upload = canvas.getDeclaredMethod("h0", float[].class, int.class, int.class);
        var texture = type("com.oplus.camera.common.gl.c");
        var target = texture.getMethod("f");
        var transformedToP3 = texture.getField("l");
        var screenFrame = new ThreadLocal<DisplayFrame>();
        var framebuffer = ThreadLocal.withInitial(() -> new int[1]);
        Map<Object, VideoCube> uploaded = Collections.synchronizedMap(new WeakHashMap<>());
        Map<Object, VideoCube> uploadedHdr = Collections.synchronizedMap(new WeakHashMap<>());
        Set<Object> unsupported = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
        Class<?> lutManager = type("com.oplus.camera.common.utils.Lut3dDataManager");
        attach(lutManager.getDeclaredMethod("n", String.class), chain -> {
            Object result = chain.proceed();
            synchronized (entries) {
                selected = null;
                for (Entry entry : entries.values()) {
                    if (entry.cube().path().equals(chain.getArg(0))) {
                        selected = entry.cube();
                        break;
                    }
                }
            }
            return result;
        });
        var drawScreen = renderer.getDeclaredMethod("onDrawFrame");
        var drawCameraFrame = type("pn.h1").getDeclaredMethod("s",
                type("com.oplus.camera.common.gl.k"), int.class, int.class, int.class, int.class);
        var primaryDraw = new ThreadLocal<Boolean>();
        module.deoptimize(drawCameraFrame);
        attach(drawCameraFrame, chain -> {
            DisplayFrame frame = screenFrame.get();
            if (frame == null || frame.canvas() != chain.getArg(0)) return chain.proceed();
            primaryDraw.set(true);
            try {
                return chain.proceed();
            } finally {
                primaryDraw.remove();
            }
        });
        module.deoptimize(drawScreen);
        attach(drawScreen, chain -> {
            VideoCube cube = selected;
            Object host = chain.getThisObject();
            if (cube == null && !monitorPresets.hasPreparedFallback()) return chain.proceed();
            Object surface = displayCanvas.get(host);
            if (surface == null) return chain.proceed();
            Object manager = managerField.get(host);
            Object mode = manager == null ? null : activeMode.get(modeHolder.get(manager));
            if (!masterVideo.isInstance(mode) || !Boolean.TRUE.equals(isLog.invoke(mode))) return chain.proceed();
            LogMonitorPresets.Preset preset = null;
            if (cube == null) {
                preset = LogMonitorPresets.Preset.fromPosition((Integer) monitorSelection.invoke(mode));
                if (preset == null || (cube = monitorPresets.prepared(preset)) == null) return chain.proceed();
            }
            screenFrame.set(new DisplayFrame(surface, cube, preset));
            try {
                return chain.proceed();
            } finally {
                screenFrame.remove();
            }
        });
        var drawTexture = canvas.getDeclaredMethod("y0", texture);
        module.deoptimize(drawTexture);
        attach(drawTexture, chain -> {
            DisplayFrame frame = screenFrame.get();
            Object surface = chain.getThisObject();
            if (frame == null || frame.canvas() != surface || primaryDraw.get() == null) return chain.proceed();
            VideoCube cube = frame.cube();
            Object source = chain.getArg(0);
            int format = (Integer) target.invoke(source);
            if (format != EXTERNAL_TEXTURE && format != GLES20.GL_TEXTURE_2D) return chain.proceed();
            // The native camera draw may first prepare a texture in an intermediate framebuffer.
            int[] binding = framebuffer.get();
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, binding, 0);
            if (binding[0] != 0) return chain.proceed();
            if (unsupported.contains(surface)) return chain.proceed();
            int previous = activeType.getInt(surface);
            boolean previousP3 = transformedToP3.getBoolean(source);
            boolean hdr = frame.preset() == LogMonitorPresets.Preset.HDR;
            int displayId = hdr ? DISPLAY_HDR_LUT : DISPLAY_LUT;
            var textures = hdr ? uploadedHdr : uploaded;
            try {
                var program = format == EXTERNAL_TEXTURE ? oesShader : textureShader;
                if (program.get(surface) == null) {
                    Object created = shader.newInstance(0x7f10012d, format == EXTERNAL_TEXTURE ? 0x7f100111 : 0x7f100110);
                    if (!Boolean.TRUE.equals(isLinked.invoke(created))) {
                        unsupported.add(surface);
                        note("Camera display LUT shader did not link");
                        return chain.proceed();
                    }
                    program.set(surface, created);
                }
                // Use display-only slots so OEM uploads cannot change the active monitor.
                if (textures.get(surface) != cube) {
                    upload.invoke(surface, cube.points, displayId, cube.size);
                    textures.put(surface, cube);
                    note("Uploaded " + (frame.preset() == null ? "custom" : frame.preset().name())
                            + " monitor LUT to the camera display");
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                unsupported.add(surface);
                module.log(Log.ERROR, TAG, "Unable to prepare the camera display LUT", error);
                return chain.proceed();
            }
            activeType.setInt(surface, displayId);
            try {
                return chain.proceed();
            } finally {
                activeType.setInt(surface, previous);
                transformedToP3.setBoolean(source, previousP3);
            }
        });
    }

    private void installSettings() throws Exception {
        Class<?> repo = type("xa.e");
        var buffer = type("ya.f").getConstructor(String.class);
        var storage = type("ya.a").getDeclaredMethod("a", Uri.class);
        module.deoptimize(storage);
        attach(storage, chain -> {
            if (!ready || !(chain.getArg(0) instanceof Uri uri)) return chain.proceed();
            if (CAMERA.equals(uri.getAuthority()) && ("sp".equals(uri.getScheme()) || "mmkv".equals(uri.getScheme()))
                    && uri.getFragment() != null && NATIVE_SETTINGS.contains(uri.getFragment())) {
                // Preserve the original URI for DataManager observers and redirect only its backing store.
                return chain.proceed(new Object[]{uri.buildUpon().scheme("sp").path(NATIVE_SETTINGS_PATH).build()});
            }
            Object alternate = settings.get(uri);
            return alternate == null ? chain.proceed() : alternate;
        });
        Method read = repo.getDeclaredMethod("a", Uri.class, Class.class, Object.class);
        Method write = repo.getDeclaredMethod("d", Uri.class, Class.class, Object.class, boolean.class);
        Method clear = repo.getDeclaredMethod("c", Uri.class);
        module.deoptimize(read);
        module.deoptimize(write);
        module.deoptimize(clear);
        attach(read, chain -> {
            Uri uri = (Uri) chain.getArg(0);
            if (ready && isSetting(uri)) {
                if (DEFAULTS_COPIED.equals(uri.getFragment())) return true;
                boolean recordEffect = selected != null && gpuBurn.available() && burnRequested;
                if (RECORD_EFFECT.equals(uri.getFragment())) return recordEffect;
                Object value = chain.proceed();
                if (!recordEffect) {
                    if (MONITOR_STATE.equals(uri.getFragment()) && Integer.valueOf(2).equals(value)) return 1;
                    if (LUT_MODE.equals(uri.getFragment()) && "rec".equals(value)) return "preview";
                }
                return value;
            }
            return chain.proceed();
        });
        attach(write, chain -> {
            Uri uri = (Uri) chain.getArg(0);
            if (ready && isSetting(uri) && !prepareSetting(uri, buffer)) return null;
            if (ready && isSetting(uri) && RECORD_EFFECT.equals(uri.getFragment())) {
                burnRequested = gpuBurn.available() && selected != null && Boolean.TRUE.equals(chain.getArg(2));
            }
            return chain.proceed();
        });
        attach(clear, chain -> {
            Uri uri = (Uri) chain.getArg(0);
            if (ready && isSetting(uri) && !prepareSetting(uri, buffer)) return null;
            if (ready && isSetting(uri) && RECORD_EFFECT.equals(uri.getFragment())) burnRequested = false;
            return chain.proceed();
        });
        installCenterPreference();
    }

    private void installCenterPreference() throws ReflectiveOperationException {
        Class<?> store = type("com.oplus.camera.ui.menu.x");
        Class<?> dataManager = type("com.oplus.camera.data.DataManager");
        var instance = dataManager.getMethod("getInstance");
        var key = type("n9.d").getField("J0");
        var read = dataManager.getMethod("b", key.getType(), Object.class);
        var write = dataManager.getMethod("g", key.getType(), Object.class);
        var displayValue = store.getDeclaredMethod("Z", String.class, String.class);
        var setValue = store.getDeclaredMethod("e1", String.class, String.class);
        module.deoptimize(displayValue);
        module.deoptimize(setValue);
        attach(displayValue, chain -> {
            if (!ready || !CENTER_INDICATOR.equals(chain.getArg(0))) return chain.proceed();
            Object value = read.invoke(instance.invoke(null), key.get(null), "");
            return "".equals(value) ? chain.getArg(1) : value;
        });
        attach(setValue, chain -> {
            if (!ready || !CENTER_INDICATOR.equals(chain.getArg(0))) return chain.proceed();
            write.invoke(instance.invoke(null), key.get(null), chain.getArg(1));
            return null;
        });
    }

    private boolean prepareSetting(Uri uri, Constructor<?> buffer) {
        if (settings.containsKey(uri)) return true;
        try {
            settings.putIfAbsent(uri, buffer.newInstance("x7u-video-lut"));
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.log(Log.ERROR, TAG, "Unable to isolate a video LUT setting", error);
            return false;
        }
    }

    private static boolean isSetting(Uri uri) {
        return uri != null && CAMERA.equals(uri.getAuthority()) && uri.getFragment() != null
                && VIDEO_SETTINGS.contains(uri.getFragment())
                && ("sp".equals(uri.getScheme()) || "mmkv".equals(uri.getScheme()));
    }

    private void installSessionDirectory() throws Exception {
        attach(File.class.getMethod("mkdirs"), chain -> {
            File destination = (File) chain.getThisObject();
            if (OEM_DIRECTORY.equals(destination.getPath())) {
                for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                    if ("jh.b".equals(frame.getClassName())
                            && ("<init>".equals(frame.getMethodName()) || "<clinit>".equals(frame.getMethodName()))) {
                        return false;
                    }
                }
            }
            return chain.proceed();
        });
        var store = type("jh.b");
        var item = type("com.oplus.camera.feature.logvideo.mode.EditorLutSelectItem");
        Method title = item.getMethod("getTitle");
        attach(store.getDeclaredMethod("b", String.class), chain -> {
            if (!ready) return chain.proceed();
            Entry entry;
            synchronized (entries) { entry = entries.get((String) chain.getArg(0)); }
            return entry == null ? null : entry.cube().path();
        });
        attach(store.getDeclaredMethod("d"), chain -> ready ? models() : chain.proceed());
        attach(store.getDeclaredMethod("f", ArrayList.class), chain -> ready ? models() : chain.proceed());
        attach(store.getDeclaredMethod("g", List.class), chain -> {
            if (!ready) return chain.proceed();
            Set<String> kept = new HashSet<>();
            for (Object model : (List<?>) chain.getArg(0)) {
                if (item.isInstance(model)) kept.add((String) title.invoke(model));
            }
            synchronized (entries) {
                entries.keySet().retainAll(kept);
                if (entries.values().stream().noneMatch(entry -> entry.cube() == selected)) selected = null;
            }
            return true;
        });
        var lutMenu = type("com.oplus.camera.feature.logvideo.view.LogLutMenuView");
        Method updateRecordRow = lutMenu.getDeclaredMethod("R");
        module.deoptimize(updateRecordRow);
        module.deoptimize(lutMenu.getDeclaredMethod("d", int.class));
        attach(lutMenu.getDeclaredMethod("C"), chain -> ready ? gpuBurn.available() && selected != null : chain.proceed());
        Class<?> videoIds = type("com.oplus.camera.feature.logvideo.R$id");
        int recordTitle = videoIds.getField("record_effect_title").getInt(null);
        int recordDetail = videoIds.getField("log_record_restore_condition").getInt(null);
        attach(updateRecordRow, chain -> {
            Object result = chain.proceed();
            if (ready && gpuBurn.available() && selected != null) {
                updateGpuRecordLabels((View) chain.getThisObject(), recordTitle, recordDetail);
            }
            return result;
        });
    }

    private void installMonitorPresets() throws Exception {
        var manager = type("com.oplus.camera.common.utils.Lut3dDataManager");
        var paths = manager.getField("Y");
        var compatibilityNotice = EnumSet.noneOf(LogMonitorPresets.Preset.class);
        var lutMenu = type("com.oplus.camera.feature.logvideo.view.LogLutMenuView");
        attach(lutMenu.getDeclaredMethod("setSelectedLutPositionAndApply", int.class), chain -> {
            var preset = LogMonitorPresets.Preset.fromPosition((Integer) chain.getArg(0));
            if (ready && preset != null) {
                var stock = preset.nativeFile((String) paths.get(null));
                if (!LogMonitorPresets.readable(stock)) {
                    var context = ((View) chain.getThisObject()).getContext();
                    if (monitorPresets.prepare(preset) == null) {
                        Toast.makeText(context, "无法加载 " + preset.name() + " 监看预置", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    Object result = chain.proceed();
                    if (compatibilityNotice.add(preset)) {
                        Toast.makeText(context, preset.name() + " 兼容监看 · 未经 X7 Ultra 色彩校准", Toast.LENGTH_SHORT).show();
                    }
                    return result;
                }
            }
            return chain.proceed();
        });
    }

    private void updateGpuRecordLabels(View menu, int titleId, int detailId) {
        CharSequence title;
        CharSequence detail;
        try {
            // The row belongs to the camera app, while these IDs belong to the module APK.
            Resources strings = menu.getContext().getPackageManager().getResourcesForApplication(
                    module.getModuleApplicationInfo(), menu.getResources().getConfiguration());
            title = strings.getText(R.string.x7u_gpu_record_title);
            detail = strings.getText(R.string.x7u_gpu_record_detail);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | SecurityException ignored) {
            title = "GPU LUT";
            detail = "1080p · 30 fps · 8-bit HEVC";
        }
        if (menu.findViewById(titleId) instanceof TextView titleView) titleView.setText(title);
        if (menu.findViewById(detailId) instanceof TextView detailView) detailView.setText(detail);
    }

    private void installPicker() throws Exception {
        Class<?> camera = type("com.oplus.camera.Camera");
        attach(Activity.class.getMethod("startActivityForResult", Intent.class, int.class, Bundle.class), chain -> {
            Intent original = (Intent) chain.getArg(0);
            if (!ready || !camera.isInstance(chain.getThisObject()) || (Integer) chain.getArg(1) != REQUEST_LUT
                    || original == null || !SELECT_LUT.equals(original.getAction())
                    || !"com.coloros.gallery3d".equals(original.getPackage())) return chain.proceed();
            var picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return chain.proceed(new Object[] { picker, REQUEST_LUT, chain.getArg(2) });
        });
        var model = type("com.oplus.camera.feature.logvideo.mode.EditorLutSelectItem")
                .getConstructor(String.class, String.class);
        Method findManager = type("gb.y0").getDeclaredMethod("y", Activity.class);
        Method findPresenter = type("gb.y0").getDeclaredMethod("B");
        Method updateLutMenu = type("ih.e").getDeclaredMethod("H2", List.class);
        attach(camera.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class), chain -> {
            Intent result = (Intent) chain.getArg(2);
            if (!ready || (Integer) chain.getArg(0) != REQUEST_LUT || (Integer) chain.getArg(1) != Activity.RESULT_OK
                    || result == null || result.getData() == null || !"content".equals(result.getData().getScheme())) {
                return chain.proceed();
            }
            Activity activity = (Activity) chain.getThisObject();
            Uri uri = result.getData();
            Object nativeResult = chain.proceed(new Object[] { REQUEST_LUT, Activity.RESULT_CANCELED, null });
            if (openCubes.size() >= LIMIT || retainedBytes >= MEMORY_LIMIT) {
                showImportLimit(activity);
                return nativeResult;
            }
            importWorker.execute(() -> {
                try (var stream = activity.getContentResolver().openInputStream(uri)) {
                    String name = displayName(activity, uri);
                    VideoCube cube = VideoCube.open(stream);
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            closeUnused(cube);
                            return;
                        }
                        if (openCubes.size() >= LIMIT || retainedBytes + cube.retainedBytes > MEMORY_LIMIT) {
                            closeUnused(cube);
                            showImportLimit(activity);
                            return;
                        }
                        String unique = null;
                        try {
                            Object manager = findManager.invoke(null, activity);
                            Object presenter = manager == null ? null : findPresenter.invoke(manager);
                            if (presenter == null) {
                                closeUnused(cube);
                                Toast.makeText(activity, "请在专业视频中重新导入 LUT", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            synchronized (entries) {
                                unique = uniqueTitle(name);
                                entries.put(unique, new Entry(model.newInstance(unique, cube.path()), cube));
                            }
                            openCubes.add(cube);
                            retainedBytes += cube.retainedBytes;
                            updateLutMenu.invoke(presenter, models());
                            note("Imported custom cube " + cube.size + "^3 in camera memory");
                            Toast.makeText(activity, "已导入 LUT，回到 LUT 菜单选择", Toast.LENGTH_SHORT).show();
                        } catch (ReflectiveOperationException error) {
                            synchronized (entries) {
                                if (unique != null) entries.remove(unique);
                            }
                            if (!openCubes.contains(cube)) closeUnused(cube);
                            module.log(Log.ERROR, TAG, "Unable to update the camera LUT list", error);
                            Toast.makeText(activity, "无法更新 LUT 菜单", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception error) {
                    String message = error instanceof IOException ? error.getMessage() : "无法导入此 LUT";
                    activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
                }
            });
            return nativeResult;
        });
    }

    private ArrayList<Object> models() {
        var rows = new ArrayList<>();
        synchronized (entries) {
            for (Entry entry : entries.values()) rows.add(entry.model());
        }
        return rows;
    }

    private void closeUnused(VideoCube cube) {
        try { cube.close(); }
        catch (IOException error) { module.log(Log.WARN, TAG, "Unable to close the unused LUT descriptor", error); }
    }

    private static void showImportLimit(Activity activity) {
        Toast.makeText(activity, "本次会话已达到 LUT 导入上限，重启相机后可继续导入。", Toast.LENGTH_SHORT).show();
    }

    private String uniqueTitle(String name) {
        String title = name;
        for (int index = 2; entries.containsKey(title); index++) title = name + " (" + index + ")";
        return title;
    }

    private static String displayName(Activity activity, Uri uri) {
        String name = "自定义 LUT";
        try (var cursor = activity.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) name = cursor.getString(0);
        }
        name = name.replaceAll("[\\p{Cntrl}/\\\\]", "_").replaceFirst("(?i)\\.cube$", "").strip();
        return name.isEmpty() ? "自定义 LUT" : name.substring(0, Math.min(60, name.length()));
    }

    private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }

    private void attach(Method method, XposedInterface.Hooker hooker) { hooks.add(module.hook(method).intercept(hooker)); }

    private void note(String message) { module.log(Log.INFO, TAG, message); }
}
