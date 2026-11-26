// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class MasterFilm {
    private static final String PRESET_INDEX = "key_master_mode_current_preset_index";
    private static final String PRESET_KEY = "key_master_mode_current_preset_key";
    private static final String GROUP_INDEX = "key_professional_filter_index";
    private static final Set<String> SELECTION_KEYS = Set.of(PRESET_INDEX, PRESET_KEY, GROUP_INDEX);
    private static final String CAPTURE_PREFIX = "../../../..";
    private static final String CAMERA = "com.oplus.camera";
    private static final List<Film> FILMS = List.of(
            new Film("portra400_master.bin", "camera_filter_oplus_he_guang"),
            new Film("positive_master.bin", "camera_filter_oplus_nong_yu"),
            new Film("blackandwhite_HC_master.bin", "camera_filter_fanchaheibai"));
    private record Film(String file, String title) { }
    private record Catalog(Map<Integer, String> group, Map<Integer, String> presets) { }

    private final CameraModule module;
    private final ClassLoader loader;
    private final LutFiles luts = new LutFiles();
    private final List<XposedInterface.HookHandle> hooks = new ArrayList<>();
    private final Map<Uri, Object> memory = new ConcurrentHashMap<>();
    private final Set<String> events = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Boolean> filmPreview = new ThreadLocal<>();
    private volatile Catalog catalog = new Catalog(Map.of(), Map.of());
    private volatile String selectedFilm;
    private volatile boolean ready;

    MasterFilm(CameraModule module, ClassLoader loader) {
        this.module = module;
        this.loader = loader;
    }

    void install() throws Exception {
        try {
            luts.load(module.getModuleApplicationInfo().sourceDir);
            installStorage();
            installCatalog();
            installRender();
            installCapture();
            ready = true;
        } finally {
            if (!ready) {
                for (var hook : hooks) hook.unhook();
                hooks.clear();
                luts.close();
            }
        }
    }

    private void installCatalog() throws Exception {
        Class<?> group = type("com.oplus.camera.filter.FilterGroupManager");
        Class<?> groupData = type("com.oplus.camera.filter.FilterGroup");
        var proGroup = group.getField("sProFilterGroup");
        var backIds = groupData.getField("mBackTypeList");
        var addBack = groupData.getMethod("addBack", String.class, Integer.class);
        attach(group.getDeclaredMethod("initFilterGroupFromIpu", Context.class, String.class, List.class), chain -> {
            Object result = chain.proceed();
            String name = (String) chain.getArg(1);
            if (!ready || !("master-back".equals(name) || "master".equals(name))) return result;
            Context context = (Context) chain.getArg(0);
            Object nativeGroup = proGroup.get(null);
            List<?> existing = (List<?>) backIds.get(nativeGroup);
            if (existing == null || existing.isEmpty() || !"default".equals(existing.get(0))) return result;
            int originalSize = existing.size();
            var indices = new HashMap<Integer, String>();
            for (Film film : FILMS) {
                int label = cameraStringId(context, film.title());
                if (existing.contains(film.file()) || label == 0) continue;
                int index = existing.size();
                addBack.invoke(nativeGroup, film.file(), label);
                indices.put(index, film.file());
            }
            if (!indices.isEmpty()) {
                catalog = new Catalog(Map.copyOf(indices), Map.of());
                note("Master catalog " + name + " " + originalSize + " -> " + existing.size());
            }
            return result;
        });

        Class<?> model = type("m9.a");
        var filterType = model.getDeclaredField("e");
        filterType.setAccessible(true);
        Class<?> data = type("bf.b");
        attach(data.getDeclaredMethod("a", List.class, java.util.LinkedList.class), chain -> {
            Object result = chain.proceed();
            Catalog previous = catalog;
            if (!ready || previous.group().isEmpty()) return result;
            List<?> entries = (List<?>) chain.getArg(1);
            var indices = new HashMap<Integer, String>();
            for (int i = 0; i < entries.size(); i++) {
                Object row = entries.get(i);
                if (!model.isInstance(row)) continue;
                String id = (String) filterType.get(row);
                if (previous.group().containsValue(id)) indices.put(i, id);
            }
            catalog = new Catalog(previous.group(), Map.copyOf(indices));
            note("Master preset catalog " + entries.size() + " with " + indices.size() + " added styles");
            return result;
        });

        var unsupportedPreset = type("com.oplus.camera.feature.captureparam.preset.PresetDataRepository$b")
                .getDeclaredConstructor(int.class);
        for (String method : List.of("b", "d")) {
            attach(data.getDeclaredMethod(method, model), chain -> {
                Object row = chain.getArg(0);
                if (ready && row != null && catalog.group().containsValue(filterType.get(row))) {
                    return unsupportedPreset.newInstance(3);
                }
                return chain.proceed();
            });
        }
    }

    @SuppressLint("DiscouragedApi") // The stock camera catalog requires an ID from its own resource table.
    private static int cameraStringId(Context context, String name) {
        return context.getResources().getIdentifier(name, "string", CAMERA);
    }

    private void installStorage() throws Exception {
        Class<?> repo = type("xa.e");
        var buffer = type("ya.f").getConstructor(String.class);
        attach(type("ya.a").getDeclaredMethod("a", Uri.class), chain -> {
            Object alternate = ready ? memory.get((Uri) chain.getArg(0)) : null;
            return alternate == null ? chain.proceed() : alternate;
        });
        Method write = repo.getDeclaredMethod("d", Uri.class, Class.class, Object.class, boolean.class);
        Method read = repo.getDeclaredMethod("a", Uri.class, Class.class, Object.class);
        Method clear = repo.getDeclaredMethod("c", Uri.class);
        module.deoptimize(write);
        module.deoptimize(read);
        module.deoptimize(clear);
        attach(write, chain -> {
            Uri uri = (Uri) chain.getArg(0);
            if (ready && isCameraStore(uri) && prepareWrite(uri.getFragment(), chain.getArg(2))) {
                if (!memory.containsKey(uri)) memory.putIfAbsent(uri, buffer.newInstance("x7u-master-film"));
                note("Process-only setting " + uri.getFragment());
            }
            return chain.proceed();
        });
        attach(clear, chain -> {
            Uri uri = (Uri) chain.getArg(0);
            if (ready && isCameraStore(uri) && isFilmParameter(uri.getFragment())) {
                if (!memory.containsKey(uri)) memory.putIfAbsent(uri, buffer.newInstance("x7u-master-film"));
            }
            return chain.proceed();
        });
    }

    private boolean prepareWrite(String key, Object value) {
        if (key == null) return false;
        if (!SELECTION_KEYS.contains(key)) return isFilmParameter(key);
        Catalog current = catalog;
        String film = null;
        if (PRESET_KEY.equals(key)) {
            if (current.group().containsValue(value)) film = (String) value;
        } else if (value instanceof Integer index) {
            film = (GROUP_INDEX.equals(key) ? current.group() : current.presets()).get(index);
        }
        if (film != null) {
            selectedFilm = film;
        } else if (PRESET_KEY.equals(key)) {
            selectedFilm = null;
        }
        return !current.group().isEmpty();
    }

    private boolean isFilmParameter(String key) {
        if (key == null || !(key.startsWith("key_master_mode_effect_") || key.startsWith("key_professional_"))) {
            return false;
        }
        return catalog.group().values().stream().anyMatch(id -> key.endsWith("_" + id));
    }

    private static boolean isCameraStore(Uri uri) {
        return uri != null && CAMERA.equals(uri.getAuthority())
                && ("mmkv".equals(uri.getScheme()) || "sp".equals(uri.getScheme()));
    }

    private void installRender() throws Exception {
        Class<?> preview = type("com.oplus.camera.filter.ipuadapter.FilterIpuAdapter");
        Method draw = Arrays.stream(preview.getDeclaredMethods())
                .filter(method -> method.getName().equals("drawFrame") && method.getParameterCount() > 15
                        && method.getParameterTypes()[6] == String.class && method.getParameterTypes()[15] == String.class)
                .findFirst().orElseThrow(NoSuchMethodException::new);
        attach(draw, chain -> {
            if (!ready || !"professional".equals(chain.getArg(15)) || !catalog.group().containsValue(chain.getArg(6))) {
                return chain.proceed();
            }
            filmPreview.set(true);
            try { return chain.proceed(); }
            finally { filmPreview.remove(); }
        });
        var paths = type("com.meicam.effect.oppo.MeisheRender").getDeclaredMethod("addPrefixAndJoin", String.class, String.class);
        hooks.add(module.hook(paths).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
            String value = (String) chain.getArg(0);
            if (!ready || value == null || !(Boolean.TRUE.equals(filmPreview.get()) || isMasterLut(value))) {
                return chain.proceed();
            }
            String mapped = luts.remap(value, (String) chain.getArg(1), "");
            if (mapped == null) return chain.proceed();
            note("Master preview LUT " + value);
            return mapped;
        }));

        Class<?> params = type("com.meicam.effect.sdk.SMsEffectParam");
        var path = params.getField("lutFilePath");
        var intensity = params.getField("intensity");
        var interpolation = params.getField("intpFactorEnable");
        var factor = params.getField("intpFactor");
        var secondPath = params.getField("secondLutFilePath");
        int matched = 0;
        for (Method method : type("com.meicam.effect.sdk.MsEffectSdkContextUtils").getDeclaredMethods()) {
            if (!method.getName().startsWith("RenderEffectWith") || method.getParameterCount() == 0
                    || method.getParameterTypes()[0] != params) continue;
            matched++;
            attach(method, chain -> {
                Object param = chain.getArg(0);
                if (!ready || param == null) return chain.proceed();
                String original = (String) path.get(param);
                if (original == null) return chain.proceed();
                float oldIntensity = intensity.getFloat(param);
                String pair = luts.contrastPair(luts.name(original), Math.round(oldIntensity), "");
                if (pair == null) return chain.proceed();
                boolean oldMode = interpolation.getBoolean(param);
                float oldFactor = factor.getFloat(param);
                Object oldSecond = secondPath.get(param);
                try {
                    path.set(param, pair);
                    intensity.setFloat(param, 100f);
                    interpolation.setBoolean(param, true);
                    factor.setFloat(param, LutFiles.contrastFactor(Math.round(oldIntensity)));
                    secondPath.set(param, "");
                    return chain.proceed();
                } finally {
                    path.set(param, original);
                    intensity.setFloat(param, oldIntensity);
                    interpolation.setBoolean(param, oldMode);
                    factor.setFloat(param, oldFactor);
                    secondPath.set(param, oldSecond);
                }
            });
        }
        if (matched == 0) throw new NoSuchMethodException("Meishe RenderEffectWith");
    }

    private void installCapture() throws Exception {
        var params = type("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsParameters");
        hooks.add(module.hook(params.getDeclaredMethod("getParameters"))
                .setPriority(XposedInterface.PRIORITY_LOWEST).intercept(chain -> {
                    String[] values = (String[]) chain.proceed();
                    if (!ready || values == null) return values;
                    int index = valueIndex(values, "filter_type");
                    if (index < 0 || values[index] == null) return values;
                    int mode = valueIndex(values, "capture_mode");
                    String filter = values[index];
                    if (!isMasterLut(filter) && (selectedFilm == null
                            || (mode >= 0 && !"professional".equals(values[mode]) && !"master".equals(values[mode])))) {
                        return values;
                    }
                    String mapped = luts.remap(filter, "", CAPTURE_PREFIX);
                    if (mapped == null) return values;
                    String[] result = values.clone();
                    int intensityIdx = valueIndex(result, "filter_intensity");
                    int strength = 100;
                    if (intensityIdx >= 0) {
                        try { strength = Integer.parseInt(result[intensityIdx]); }
                        catch (NumberFormatException ignored) { }
                    }
                    String pair = luts.contrastPair(filter, strength, CAPTURE_PREFIX);
                    if (pair != null) {
                        mapped = pair;
                        result = setParameter(result, "filter_intensity", "100");
                        result = setParameter(result, "filter_lut_intp_factor", Float.toString(LutFiles.contrastFactor(strength)));
                    }
                    result[index] = mapped;
                    note("Master capture LUT " + filter + " intensity=" + strength);
                    return result;
                }));
    }

    private static boolean isMasterLut(String filters) {
        for (String filter : filters.split(",")) {
            if (filter.endsWith("_master.bin") || filter.contains("_hdr_master_")) return true;
        }
        return false;
    }

    private static int valueIndex(String[] pairs, String key) {
        for (int i = 0; i + 1 < pairs.length; i += 2) if (key.equals(pairs[i])) return i + 1;
        return -1;
    }

    private static String[] setParameter(String[] pairs, String key, String value) {
        int index = valueIndex(pairs, key);
        if (index >= 0) {
            pairs[index] = value;
            return pairs;
        }
        String[] next = Arrays.copyOf(pairs, pairs.length + 2);
        next[pairs.length] = key;
        next[pairs.length + 1] = value;
        return next;
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private void attach(Method method, XposedInterface.Hooker hooker) {
        hooks.add(module.hook(method).intercept(hooker));
    }

    private void note(String event) {
        if (events.add(event)) module.info(event);
    }
}
