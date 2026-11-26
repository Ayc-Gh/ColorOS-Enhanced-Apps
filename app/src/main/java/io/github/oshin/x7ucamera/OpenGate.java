// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class OpenGate {
    private static final String TAG = "X7UOpenGate";
    private static final String OPEN_GATE = "video_size_open_gate";
    private static final Size RECORDING_SIZE = new Size(1920, 1440);
    private static final String FALLBACK_SIZE = "video_size_1080p";
    private static final String SIZE_KEY = "pref_master_video_size_key";
    private static final String FORMAT_KEY = "pref_master_video_format_key";
    private static final Set<String> NON_SDR_FORMATS = Set.of("log", "hdr");

    private OpenGate() { }

    static void install(CameraModule module, ClassLoader loader) throws ReflectiveOperationException {
        var mode = Class.forName("um.d", false, loader);
        var baseMode = Class.forName("com.oplus.camera.module.BaseMode", false, loader);
        var isFront = baseMode.getDeclaredMethod("r");
        var video = Class.forName("tm.o0", false, loader);
        var selection = video.getDeclaredMethod("h7");
        var fps = video.getDeclaredMethod("Ma");
        var preferences = Class.forName("n9.e", false, loader);
        var sizeKey = preferences.getField("G0");
        var formatKey = preferences.getField("K0");
        var logKey = preferences.getField("W0");
        var hdrKey = preferences.getField("I0");
        var dataManager = Class.forName("com.oplus.camera.data.DataManager", false, loader);
        var getDataManager = dataManager.getMethod("getInstance");
        var getSetting = dataManager.getMethod("b", sizeKey.getType(), Object.class);
        var setSetting = dataManager.getMethod("h", sizeKey.getType(), Object.class, boolean.class);
        GateState gateState = new GateState(getDataManager, getSetting, formatKey, logKey, hdrKey, fps);

        var menu = Class.forName("eq.o", false, loader);
        var core = Class.forName("lq.l", false, loader).getField("w");
        var modeHolder = core.getType().getMethod("c0");
        var state = Class.forName("gm.r3", false, loader).getField("a");
        var activeMode = Class.forName("gm.f5", false, loader).getField("a");
        var option = Class.forName("jo.b", false, loader);
        var refreshSizes = menu.getDeclaredMethod("N0", option);
        var applyItem = menu.getDeclaredMethod("K0", option);
        var findItem = menu.getDeclaredMethod("A0", String.class);
        var optionKey = option.getMethod("getOptionKey");
        var selectedItem = option.getMethod("getSelectedItem");
        var selectValue = option.getMethod("setSelectedItemValue", String.class);
        var value = Class.forName("jo.c", false, loader).getField("b");
        var checklist = Class.forName("com.oplus.camera.ui.newsetting.container.h3", false, loader);
        var dispatchTouch = checklist.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        var guide = checklist.getField("a");
        var guideIcon = guide.getType().getField("d");

        var catalog = Class.forName("com.oplus.camera.common.config.CameraSettingsConfig", false, loader);
        var addRearSizes = catalog.getDeclaredMethod("addVideoBackOptionItem");
        var openGateFlag = Class.forName("o9.h", false, loader).getDeclaredMethod("p");
        var recordingSize = mode.getDeclaredMethod("lk");
        var applyProfile = mode.getDeclaredMethod("gl", android.media.CamcorderProfile.class);
        var supports24 = mode.getDeclaredMethod("De", String.class);
        var supports60 = mode.getDeclaredMethod("Ek", String.class, String.class);
        for (var method : List.of(addRearSizes, openGateFlag, refreshSizes, applyItem, dispatchTouch,
                selection, recordingSize, applyProfile, supports24, supports60)) {
            module.deoptimize(method);
        }

        ThreadLocal<Boolean> exposeOpenGate = new ThreadLocal<>();
        List<XposedInterface.HookHandle> hooks = new ArrayList<>();
        boolean installed = false;
        try {
            hooks.add(module.hook(openGateFlag).intercept(chain -> Boolean.TRUE.equals(exposeOpenGate.get()) || Boolean.TRUE.equals(chain.proceed())));
            hooks.add(module.hook(addRearSizes).intercept(chain -> withOpenGate(chain, exposeOpenGate)));
            hooks.add(module.hook(refreshSizes).intercept(chain -> {
                Object currentMode = activeMode.get(state.get(modeHolder.invoke(core.get(chain.getThisObject()))));
                if (!mode.isInstance(currentMode) || Boolean.TRUE.equals(isFront.invoke(currentMode))
                        || !gateState.compatible(currentMode)) {
                    return chain.proceed();
                }
                return withOpenGate(chain, exposeOpenGate);
            }));
            hooks.add(module.hook(supports24).intercept(chain -> !OPEN_GATE.equals(chain.getArg(0)) && Boolean.TRUE.equals(chain.proceed())));
            hooks.add(module.hook(supports60).intercept(chain -> !OPEN_GATE.equals(chain.getArg(0)) && Boolean.TRUE.equals(chain.proceed())));
            hooks.add(module.hook(selection).intercept(chain -> {
                Object result = chain.proceed();
                Object host = chain.getThisObject();
                if (mode.isInstance(host) && OPEN_GATE.equals(result)
                        && (Boolean.TRUE.equals(isFront.invoke(host)) || !gateState.compatible(host))) return FALLBACK_SIZE;
                return result;
            }));
            hooks.add(module.hook(applyItem).intercept(chain -> {
                Object optionInfo = chain.getArg(0);
                if (FORMAT_KEY.equals(optionKey.invoke(optionInfo))) {
                    Object target = selectedItem.invoke(optionInfo);
                    if (target != null && value.get(target) instanceof String format && NON_SDR_FORMATS.contains(format)) {
                        Object store = getDataManager.invoke(null);
                        Object key = sizeKey.get(null);
                        if (OPEN_GATE.equals(getSetting.invoke(store, key, FALLBACK_SIZE))) {
                            setSetting.invoke(store, key, FALLBACK_SIZE, false);
                            Object sizes = findItem.invoke(chain.getThisObject(), SIZE_KEY);
                            if (sizes != null) selectValue.invoke(sizes, FALLBACK_SIZE);
                            Log.i(TAG, "Exited 4:3 before selecting " + format);
                        }
                    }
                }
                return chain.proceed();
            }));
            hooks.add(module.hook(dispatchTouch).intercept(chain -> {
                Object bar = chain.getThisObject();
                Object entry = guide.get(bar);
                if (entry == null || !(guideIcon.get(entry) instanceof View icon) || belongsTo(icon, bar)
                        || !OPEN_GATE.equals(getSetting.invoke(getDataManager.invoke(null), sizeKey.get(null), FALLBACK_SIZE))) {
                    return chain.proceed();
                }
                // Resizing this menu can remove the help icon while leaving its entry attached to the bar.
                guide.set(bar, null);
                try {
                    return chain.proceed();
                } finally {
                    if (guide.get(bar) == null) guide.set(bar, entry);
                }
            }));
            hooks.add(module.hook(recordingSize).intercept(chain -> OPEN_GATE.equals(selection.invoke(chain.getThisObject()))
                    ? RECORDING_SIZE : chain.proceed()));
            installed = true;
        } finally {
            if (!installed) {
                for (var hook : hooks) hook.unhook();
            }
        }
        module.log(Log.INFO, TAG, "1920x1440 SDR capture available at 30 fps");
    }

    private record GateState(Method instance, Method read, Field format, Field log, Field hdr, Method fps) {
        boolean compatible(Object mode) throws ReflectiveOperationException {
            if (!Integer.valueOf(30).equals(fps.invoke(mode))) return false;
            Object store = instance.invoke(null);
            return "sdr".equals(read.invoke(store, format.get(null), "sdr"))
                    && "off".equals(read.invoke(store, log.get(null), "off"))
                    && "off".equals(read.invoke(store, hdr.get(null), "off"));
        }
    }

    private static boolean belongsTo(View child, Object parent) {
        View current = child;
        while (current.getParent() instanceof View candidate) {
            if (candidate == parent) return true;
            current = candidate;
        }
        return false;
    }

    private static Object withOpenGate(XposedInterface.Chain chain, ThreadLocal<Boolean> active) throws Throwable {
        Boolean previous = active.get();
        active.set(true);
        try {
            return chain.proceed();
        } finally {
            if (previous == null) active.remove();
            else active.set(previous);
        }
    }
}
