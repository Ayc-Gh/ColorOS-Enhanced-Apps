// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import android.app.Activity;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.CamcorderProfile;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class GpuVideoBurn {
    private static final String TAG = "X7UGpuVideoBurn";
    private static final String REOPEN_CAMERA = "GPU 直出不可用，请退出专业视频后重新进入再录制";
    private static final String RECORDING_FAILED = "GPU 直出已中断，当前视频可能不完整。请停止录像，退出专业视频后重新录制";
    private final CameraModule module;
    private final ClassLoader loader;
    private final BooleanSupplier requested;
    private final Supplier<VideoCube> chosen;
    private final Map<Surface, Route> bridges = new IdentityHashMap<>();
    private final Map<Object, SessionCallback> cameraSessions = new IdentityHashMap<>();
    private final Map<Object, Surface> recorderSurfaces = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Object> configuring = new ThreadLocal<>();
    private final AtomicBoolean failed = new AtomicBoolean();
    private volatile boolean hooksInstalled;
    private volatile boolean supported;

    private static final class Route {
        final Surface encoder;
        final GpuEncoderSurface bridge;
        final WeakReference<Activity> host;
        final GpuSessionState sessions = new GpuSessionState();
        volatile Object nativeSubmission;

        Route(Surface encoder, GpuEncoderSurface bridge, WeakReference<Activity> host) {
            this.encoder = encoder;
            this.bridge = bridge;
            this.host = host;
        }
    }

    GpuVideoBurn(CameraModule module, ClassLoader loader, BooleanSupplier requested, Supplier<VideoCube> chosen) {
        this.module = module;
        this.loader = loader;
        this.requested = requested;
        this.chosen = chosen;
    }

    boolean available() { return hooksInstalled && supported && !failed.get(); }

    String diagnosticState() {
        if (!hooksInstalled) return "unavailable";
        if (failed.get()) return "runtime_failed";
        if (!supported) return "ineligible";
        synchronized (bridges) {
            for (var route : bridges.values()) if (route.sessions.ready() != null) return "routed";
        }
        return "eligible";
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void install() throws Exception {
        SessionConfiguration.class.getMethod("setStateCallback", Executor.class, CameraCaptureSession.StateCallback.class);
        Class<?> videoMode = type("tm.o0");
        Class<?> master = type("um.d");
        var profile = videoMode.getField("N2");
        var getFrameRate = videoMode.getMethod("Ma");
        var isFront = videoMode.getMethod("r");
        var activity = videoMode.getMethod("P0");
        var isLog = master.getMethod("vk");
        var configure = master.getDeclaredMethod("Y7", type("com.oplus.camera.ai"));
        Class<?> builder = type("com.oplus.ocs.camera.CameraDeviceConfig$Builder");
        Class<?> videoConfig = type("com.oplus.ocs.camera.CameraDeviceConfig$VideoConfig");
        var buildVideo = builder.getDeclaredMethod("setVideoConfig", videoConfig);
        var input = videoConfig.getMethod("getVideoSurface");
        var size = videoConfig.getMethod("getVideoSize");
        var cameraType = videoConfig.getMethod("getCameraType");
        var factory = videoConfig.getConstructor(String.class, Size.class, Surface.class);
        Class<?> recorder = type("rm.r0");
        var recorderType = recorder.getField("v");
        var setInput = recorder.getMethod("n", Surface.class);
        var start = recorder.getMethod("C");
        var stop = recorder.getMethod("D", boolean.class);
        var release = recorder.getMethod("g");
        Class<?> processor = type("rm.k0");
        var nativeSurface = processor.getField("q");
        var destroyMode = processor.getMethod("l0", boolean.class);
        var device = Class.forName("android.hardware.camera2.impl.CameraDeviceImpl", false, CameraDevice.class.getClassLoader());
        var submitSession = device.getDeclaredMethod("createCaptureSession", SessionConfiguration.class);
        module.deoptimize(configure);
        module.deoptimize(buildVideo);
        module.deoptimize(setInput);
        module.deoptimize(submitSession);
        var installed = new ArrayList<XposedInterface.HookHandle>();
        try {
            add(installed, configure, chain -> {
                Object mode = chain.getThisObject();
                configuring.set(mode);
                try {
                    return chain.proceed();
                } finally {
                    configuring.remove();
                }
            });
            add(installed, buildVideo, chain -> {
                Object mode = configuring.get();
                Object details = chain.getArg(0);
                if (mode == null || details == null) return chain.proceed();
                Size resolution = (Size) size.invoke(details);
                Surface encoder = (Surface) input.invoke(details);
                var currentProfile = (CamcorderProfile) profile.get(mode);
                supported = encoder != null && resolution != null && resolution.getWidth() == 1920
                        && resolution.getHeight() == 1080 && currentProfile != null
                        && !Boolean.TRUE.equals(isFront.invoke(mode))
                        && (Integer) getFrameRate.invoke(mode) == 30 && Boolean.TRUE.equals(isLog.invoke(mode));
                if (!available() || !requested.getAsBoolean() || chosen.get() == null) {
                    Object result = chain.proceed();
                    retire(encoder);
                    return result;
                }
                Activity host = hostActivity(activity, mode);
                Object replacement;
                try {
                    GpuEncoderSurface bridge = bridgeFor(encoder, host);
                    replacement = factory.newInstance(cameraType.invoke(details), resolution, bridge.cameraInput());
                } catch (Exception error) {
                    module.log(Log.ERROR, TAG, "Unable to route the camera recording output through OpenGL", error);
                    fail(host, "GPU 直出准备失败，将尝试使用原生录像输入");
                    Object result = chain.proceed();
                    retire(encoder);
                    return result;
                }
                Object result;
                try {
                    result = chain.proceed(new Object[] { replacement });
                } catch (Throwable error) {
                    fail(host, REOPEN_CAMERA);
                    throw error;
                }
                return result;
            });
            add(installed, submitSession, this::submitCameraSession);
            add(installed, setInput, chain -> {
                Object nativeRecorder = chain.getThisObject();
                Object result = chain.proceed();
                if (recorderType.getInt(nativeRecorder) == 1 && chain.getArg(0) instanceof Surface surface) {
                    recorderSurfaces.put(nativeRecorder, surface);
                } else {
                    recorderSurfaces.remove(nativeRecorder);
                }
                return result;
            });
            add(installed, start, chain -> {
                Route route = findRoute(recorderSurfaces.get(chain.getThisObject()));
                if (route == null || route.nativeSubmission != null) return chain.proceed();
                VideoCube cube = chosen.get();
                GpuSessionState.Attempt attempt = configuredAttempt(route);
                if (!available() || !requested.getAsBoolean() || cube == null || attempt == null || !route.bridge.canStart()) {
                    warnHost(route.host.get(), REOPEN_CAMERA);
                    return false;
                }
                Object result = chain.proceed();
                if (Boolean.TRUE.equals(result)) {
                    route.bridge.start(cube, attempt);
                    if (configuredAttempt(route) != attempt) route.bridge.sessionLost(attempt);
                }
                return result;
            });
            add(installed, stop, chain -> {
                GpuEncoderSurface bridge = findBridge(recorderSurfaces.get(chain.getThisObject()));
                Runnable finish = bridge == null ? null : bridge.pauseForRecorderStop();
                try {
                    return chain.proceed();
                } finally {
                    if (finish != null) finish.run();
                }
            });
            add(installed, release, chain -> {
                GpuEncoderSurface bridge = findBridge(recorderSurfaces.remove(chain.getThisObject()));
                Runnable finish = bridge == null ? null : bridge.pauseForRecorderStop();
                try {
                    return chain.proceed();
                } finally {
                    if (finish != null) finish.run();
                }
            });
            add(installed, destroyMode, chain -> {
                Surface encoder = (Surface) nativeSurface.get(chain.getThisObject());
                try {
                    return chain.proceed();
                } finally {
                    retire(encoder);
                }
            });
            hooksInstalled = true;
        } finally {
            if (!hooksInstalled) for (var hook : installed) hook.unhook();
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private Object submitCameraSession(XposedInterface.Chain chain) throws Throwable {
        if (!(chain.getArg(0) instanceof SessionConfiguration config)) return chain.proceed();
        synchronized (config) {
            var delegate = config.getStateCallback();
            Executor executor = config.getExecutor();
            if (delegate == null || executor == null) return chain.proceed();
            Object device = chain.getThisObject();
            SessionCallback session = null;
            Route nativeRoute = null;
            OutputConfiguration gpuOutput = null;
            Object nativeSubmission = null;
            synchronized (bridges) {
                var previous = cameraSessions.remove(device);
                if (previous != null) previous.supersede();
                for (var output : config.getOutputConfigurations()) {
                    if (output == null) continue;
                    Surface surface = output.getSurface();
                    Route route = findGpuInput(surface);
                    if (route != null) {
                        var attempt = route.sessions.submit();
                        if (attempt == null) {
                            continue;
                        }
                        session = new SessionCallback(device, route, attempt, delegate);
                        cameraSessions.put(device, session);
                        gpuOutput = output;
                        break;
                    }
                    Route candidate = bridges.get(surface);
                    if (candidate != null && candidate.sessions.retired()) nativeRoute = candidate;
                }
                if (session == null && nativeRoute != null) {
                    nativeSubmission = new Object();
                    nativeRoute.nativeSubmission = nativeSubmission;
                }
            }
            if (session == null) {
                try {
                    return chain.proceed();
                } catch (Throwable error) {
                    synchronized (bridges) {
                        if (nativeRoute != null && nativeRoute.nativeSubmission == nativeSubmission) nativeRoute.nativeSubmission = null;
                    }
                    throw error;
                }
            }

            try {
                gpuOutput.setTimestampBase(OutputConfiguration.TIMESTAMP_BASE_MONOTONIC);
                config.setStateCallback(executor, session);
                return chain.proceed();
            } catch (Throwable error) {
                if (session.finish()) fail(session.route.host.get(), REOPEN_CAMERA);
                throw error;
            } finally {
                // CameraDeviceImpl reads the callback during submission and retains it with the new session.
                config.setStateCallback(executor, delegate);
            }
        }
    }

    private GpuEncoderSurface bridgeFor(Surface destination, Activity activity) throws Exception {
        synchronized (bridges) {
            var existing = bridges.get(destination);
            if (existing != null) {
                existing.sessions.prepare();
                existing.nativeSubmission = null;
                return existing.bridge;
            }
            if (bridges.size() >= 2) throw new IllegalStateException("Previous recording surfaces are still in use");
            var host = new WeakReference<>(activity);
            var created = GpuEncoderSurface.open(module, destination, 1920, 1080, () -> {
                failed.set(true);
                warnHost(host.get(), RECORDING_FAILED);
            });
            bridges.put(destination, new Route(destination, created, host));
            return created;
        }
    }

    private Route findGpuInput(Surface input) {
        if (input == null) return null;
        for (var route : bridges.values()) if (input == route.bridge.cameraInput()) return route;
        return null;
    }

    private void retire(Surface encoder) {
        synchronized (bridges) {
            Route route = bridges.get(encoder);
            if (route == null) return;
            route.sessions.retire();
            disposeIfUnused(route);
        }
    }

    private void disposeIfUnused(Route route) {
        if (!route.sessions.canDispose() || !bridges.remove(route.encoder, route)) return;
        route.bridge.close();
        module.log(Log.INFO, TAG, "Released the GPU recorder route");
    }

    private Route findRoute(Surface surface) {
        synchronized (bridges) {
            return bridges.get(surface);
        }
    }

    private GpuSessionState.Attempt configuredAttempt(Route route) {
        synchronized (bridges) { return route.sessions.ready(); }
    }

    private GpuEncoderSurface findBridge(Surface surface) {
        Route route = findRoute(surface);
        return route == null ? null : route.bridge;
    }

    private Activity hostActivity(Method getter, Object mode) {
        try {
            return getter.invoke(mode) instanceof Activity activity ? activity : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            module.log(Log.WARN, TAG, "Unable to find the camera Activity for GPU status", failure);
            return null;
        }
    }

    private void fail(Activity host, String message) {
        if (failed.compareAndSet(false, true)) warnHost(host, message);
    }

    private void warnHost(Activity host, String message) {
        module.log(Log.WARN, TAG, message);
        if (host != null) host.runOnUiThread(() -> {
            if (!host.isFinishing() && !host.isDestroyed()) Toast.makeText(host, message, Toast.LENGTH_LONG).show();
        });
    }

    private final class SessionCallback extends CameraCaptureSession.StateCallback {
        private final Object device;
        private final Route route;
        private final GpuSessionState.Attempt attempt;
        private final CameraCaptureSession.StateCallback delegate;

        SessionCallback(Object device, Route route, GpuSessionState.Attempt attempt,
                        CameraCaptureSession.StateCallback delegate) {
            this.device = device;
            this.route = route;
            this.attempt = attempt;
            this.delegate = delegate;
        }

        void supersede() { route.sessions.supersede(attempt); }

        boolean finish() {
            route.bridge.sessionLost(attempt);
            synchronized (bridges) {
                boolean wasCurrent = route.sessions.finish(attempt);
                cameraSessions.remove(device, this);
                disposeIfUnused(route);
                return wasCurrent;
            }
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            synchronized (bridges) {
                route.sessions.configured(attempt);
                if (route.sessions.ready() == attempt) module.log(Log.INFO, TAG, "Camera2 configured the GPU recording output");
            }
            delegate.onConfigured(session);
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            if (finish()) fail(route.host.get(), REOPEN_CAMERA);
            delegate.onConfigureFailed(session);
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            finish();
            delegate.onClosed(session);
        }

        @Override
        public void onReady(CameraCaptureSession session) { delegate.onReady(session); }

        @Override
        public void onActive(CameraCaptureSession session) { delegate.onActive(session); }

        @Override
        public void onCaptureQueueEmpty(CameraCaptureSession session) { delegate.onCaptureQueueEmpty(session); }

        @Override
        public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
            delegate.onSurfacePrepared(session, surface);
        }
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private void add(ArrayList<XposedInterface.HookHandle> handles, Method method, XposedInterface.Hooker hook) {
        handles.add(module.hook(method).intercept(hook));
    }
}
