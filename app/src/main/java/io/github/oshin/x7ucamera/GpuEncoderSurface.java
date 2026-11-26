// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.x7ucamera;

import static android.opengl.EGL14.*;
import static android.opengl.GLES30.*;

import android.graphics.SurfaceTexture;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class GpuEncoderSurface {
    private static final String TAG = "X7UGpuEncoder";
    private static final int EXTERNAL_TEXTURE = 0x8D65;
    private static final long FIRST_FRAME_TIMEOUT_MS = 3000;
    private static final String VERTEX = """
            #version 300 es
            layout(location = 0) in vec2 position;
            uniform mat4 transform;
            out vec2 coordinate;
            void main() {
                coordinate = (transform * vec4(position * 0.5 + 0.5, 0.0, 1.0)).xy;
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float;
            uniform samplerExternalOES cameraFrame;
            uniform highp sampler3D cube;
            uniform float grid;
            in vec2 coordinate;
            out vec4 color;
            void main() {
                vec3 inputColor = clamp(texture(cameraFrame, coordinate).rgb, 0.0, 1.0);
                vec3 index = (inputColor * (grid - 1.0) + 0.5) / grid;
                color = vec4(texture(cube, index).rgb, 1.0);
            }
            """;

    private final CameraModule module;
    private final Surface encoder;
    private final int width;
    private final int height;
    private final Runnable onRecordingFailure;
    private final HandlerThread thread = new HandlerThread("X7U LUT encoder", Process.THREAD_PRIORITY_DISPLAY);
    private final Handler handler;
    private final Handler deadlineHandler = new Handler(Looper.getMainLooper());
    private final float[] matrix = new float[16];
    private EGLDisplay display = EGL_NO_DISPLAY;
    private EGLConfig config;
    private EGLContext context = EGL_NO_CONTEXT;
    private EGLSurface offscreen = EGL_NO_SURFACE;
    private EGLSurface output = EGL_NO_SURFACE;
    private SurfaceTexture frames;
    private Surface input;
    private int cameraTexture;
    private int lookupTexture;
    private int vertexBuffer;
    private int program;
    private int transformUniform;
    private int gridUniform;
    private VideoCube uploaded;
    private volatile Recording recording;
    private Recording rendering;
    private Runnable firstFrameDeadline;
    private volatile boolean closed;
    private long timestampOffset;
    private long lastTimestamp;
    private long gaps;
    private long framesWritten;
    private long framesSeen;
    private long armRequestedNs;
    private long firstSubmittedPtsNs;
    private long lastSubmittedPtsNs;
    private long firstSwapReturnedNs;
    private long lastSwapReturnedNs;
    private volatile boolean renderFailed;

    private record Recording(VideoCube cube, GpuSessionState.Attempt attempt) { }

    static GpuEncoderSurface open(CameraModule module, Surface encoder, int width, int height,
                                  Runnable onRecordingFailure) throws Exception {
        var bridge = new GpuEncoderSurface(module, encoder, width, height, onRecordingFailure);
        var initialized = new FutureTask<Void>(() -> {
            bridge.initialize();
            return null;
        });
        bridge.handler.post(initialized);
        try {
            initialized.get(2000, TimeUnit.MILLISECONDS);
            return bridge;
        } catch (Exception failure) {
            bridge.close();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private GpuEncoderSurface(CameraModule module, Surface encoder, int width, int height,
                              Runnable onRecordingFailure) {
        this.module = module;
        this.encoder = encoder;
        this.width = width;
        this.height = height;
        this.onRecordingFailure = onRecordingFailure;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    Surface cameraInput() { return input; }

    boolean canStart() { return !closed && !renderFailed; }

    synchronized void start(VideoCube cube, GpuSessionState.Attempt attempt) {
        if (!canStart()) {
            onRecordingFailure.run();
            return;
        }
        Recording clip = new Recording(cube, attempt);
        recording = clip;
        clearFirstFrameDeadline();
        long requestedNs = System.nanoTime();
        if (!handler.post(() -> {
            if (recording != clip) return;
            rendering = clip;
            framesSeen = 0;
            framesWritten = 0;
            lastTimestamp = 0;
            timestampOffset = 0;
            gaps = 0;
            armRequestedNs = requestedNs;
            firstSubmittedPtsNs = 0;
            lastSubmittedPtsNs = 0;
            firstSwapReturnedNs = 0;
            lastSwapReturnedNs = 0;
            module.log(Log.INFO, TAG, "GPU LUT armed for recording, grid " + cube.size);
        })) {
            failRecording(clip, "GPU encoder worker could not start the recording", null, false);
            return;
        }
        firstFrameDeadline = () -> failRecording(clip, "No GPU video frame was submitted within 3 seconds", null, true);
        deadlineHandler.postDelayed(firstFrameDeadline, FIRST_FRAME_TIMEOUT_MS);
    }

    synchronized Runnable pauseForRecorderStop() {
        Recording clip = recording;
        if (clip == null) return null;
        long requestedNs = System.nanoTime();
        recording = null;
        clearFirstFrameDeadline();
        // Keep the EGL producer connected while MediaRecorder drains its pending samples.
        return () -> handler.post(() -> {
            if (rendering != clip) return;
            rendering = null;
            destroyOutput();
            if (framesSeen != 0) {
                module.log(Log.INFO, TAG, "GPU LUT frames acquired=" + framesSeen + ", submitted="
                        + framesWritten + ", long timestamp gaps=" + gaps + ", renderFailed=" + renderFailed);
                module.log(Log.INFO, TAG, "GPU recording boundaries ns: arm=" + armRequestedNs
                        + ", ptsOffset=" + timestampOffset + ", firstSubmittedPts=" + firstSubmittedPtsNs
                        + ", lastSubmittedPts=" + lastSubmittedPtsNs + ", firstSwapReturn=" + firstSwapReturnedNs
                        + ", lastSwapReturn=" + lastSwapReturnedNs + ", stopRequest=" + requestedNs);
            }
            framesSeen = 0;
        });
    }

    void sessionLost(GpuSessionState.Attempt attempt) {
        Recording clip = recording;
        if (clip != null && clip.attempt() == attempt) {
            failRecording(clip, "Camera2 closed the GPU recording session", null, false);
        }
    }

    private void failRecording(Recording clip, String message, Throwable error, boolean waitingForFirstFrame) {
        synchronized (this) {
            if (clip == null || recording != clip || (waitingForFirstFrame && firstFrameDeadline == null)) return;
            renderFailed = true;
            recording = null;
            clearFirstFrameDeadline();
        }
        if (error == null) module.log(Log.ERROR, TAG, message);
        else module.log(Log.ERROR, TAG, message, error);
        handler.post(this::destroyOutput);
        onRecordingFailure.run();
    }

    private void clearFirstFrameDeadline() {
        if (firstFrameDeadline == null) return;
        deadlineHandler.removeCallbacks(firstFrameDeadline);
        firstFrameDeadline = null;
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        recording = null;
        clearFirstFrameDeadline();
        handler.post(() -> {
            destroyOutput();
            if (frames != null) {
                frames.setOnFrameAvailableListener(null);
                frames.release();
            }
            if (input != null) input.release();
            if (program != 0) glDeleteProgram(program);
            glDeleteBuffers(1, new int[] { vertexBuffer }, 0);
            glDeleteTextures(2, new int[] { cameraTexture, lookupTexture }, 0);
            if (display != EGL_NO_DISPLAY) {
                eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                if (offscreen != EGL_NO_SURFACE) eglDestroySurface(display, offscreen);
                if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
                eglReleaseThread();
            }
            thread.quitSafely();
        });
    }

    private void initialize() {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        checkEgl(display != EGL_NO_DISPLAY && eglInitialize(display, null, 0, null, 0), "initialize");
        int[] attributes = {
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL_NONE
        };
        var configs = new EGLConfig[1];
        var count = new int[1];
        checkEgl(eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] != 0, "choose config");
        config = configs[0];
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, new int[] { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE }, 0);
        checkEgl(context != EGL_NO_CONTEXT, "create context");
        offscreen = eglCreatePbufferSurface(display, config, new int[] { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE }, 0);
        checkEgl(offscreen != EGL_NO_SURFACE && eglMakeCurrent(display, offscreen, offscreen, context), "bind pbuffer");

        program = link();
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "cameraFrame"), 0);
        glUniform1i(glGetUniformLocation(program, "cube"), 1);
        transformUniform = glGetUniformLocation(program, "transform");
        gridUniform = glGetUniformLocation(program, "grid");
        var ids = new int[2];
        glGenTextures(2, ids, 0);
        cameraTexture = ids[0];
        lookupTexture = ids[1];
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(EXTERNAL_TEXTURE, cameraTexture);
        textureParameters(EXTERNAL_TEXTURE);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, lookupTexture);
        textureParameters(GL_TEXTURE_3D);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glGenBuffers(1, ids, 0);
        vertexBuffer = ids[0];
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        FloatBuffer corners = floats(new float[] { -1, -1, 1, -1, -1, 1, 1, 1 });
        glBufferData(GL_ARRAY_BUFFER, 8 * Float.BYTES, corners, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        checkGl();

        frames = new SurfaceTexture(cameraTexture);
        frames.setDefaultBufferSize(width, height);
        frames.setOnFrameAvailableListener(this::onFrame, handler);
        input = new Surface(frames);
        module.log(Log.INFO, TAG, "GPU input ready " + width + "x" + height + ", " + glGetString(GL_RENDERER));
    }

    private void onFrame(SurfaceTexture source) {
        if (closed) return;
        Recording clip = recording;
        boolean firstFrame = clip != null && clip == rendering && framesWritten == 0;
        long callbackNs = firstFrame ? System.nanoTime() : 0;
        try {
            glActiveTexture(GL_TEXTURE0);
            source.updateTexImage();
            if (clip == null || clip != rendering || recording != clip) return;
            long latchedNs = firstFrame ? System.nanoTime() : 0;
            VideoCube cube = clip.cube();
            long sourceTimestamp = source.getTimestamp();
            if (lastTimestamp == 0 && sourceTimestamp > 0) {
                long now = System.nanoTime();
                long boot = SystemClock.elapsedRealtimeNanos();
                if (Math.abs(now - sourceTimestamp) > 1_000_000_000L) {
                    if (Math.abs(boot - sourceTimestamp) > 1_000_000_000L) {
                        throw new IllegalStateException("Camera timestamp does not match either Android clock");
                    }
                    timestampOffset = now - boot;
                }
                module.log(Log.INFO, TAG, "First frame matches recorder clock with correction " + timestampOffset + " ns");
            }
            long timestamp = sourceTimestamp + timestampOffset;
            if (timestamp <= lastTimestamp) return;
            framesSeen++;
            if (lastTimestamp != 0 && timestamp - lastTimestamp > 50_000_000L) gaps++;
            lastTimestamp = timestamp;
            long windowAndBindNs = 0;
            if (output == EGL_NO_SURFACE) {
                long beforeWindowNs = firstFrame ? System.nanoTime() : 0;
                output = eglCreateWindowSurface(display, config, encoder, new int[] { EGL_NONE }, 0);
                checkEgl(output != EGL_NO_SURFACE && eglMakeCurrent(display, output, output, context), "bind recorder Surface");
                if (firstFrame) windowAndBindNs = System.nanoTime() - beforeWindowNs;
            }
            long lookupUploadNs = 0;
            if (uploaded != cube) {
                long beforeLookupNs = firstFrame ? System.nanoTime() : 0;
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_3D, lookupTexture);
                int edge = cube.size;
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB16F, edge, edge, edge, 0, GL_RGB, GL_FLOAT, floats(cube.points));
                glUniform1f(gridUniform, edge);
                checkGl();
                uploaded = cube;
                if (firstFrame) lookupUploadNs = System.nanoTime() - beforeLookupNs;
            }
            source.getTransformMatrix(matrix);
            matchRecorderOrientation(matrix);
            glViewport(0, 0, width, height);
            glUniformMatrix4fv(transformUniform, 1, false, matrix, 0);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            checkGl();
            if (recording != clip) return;
            checkEgl(EGLExt.eglPresentationTimeANDROID(display, output, timestamp), "set recorder timestamp");
            long beforeSwapNs = firstFrame ? System.nanoTime() : 0;
            checkEgl(eglSwapBuffers(display, output), "submit recorder frame");
            lastSubmittedPtsNs = timestamp;
            lastSwapReturnedNs = System.nanoTime();
            if (++framesWritten == 1) {
                firstSubmittedPtsNs = lastSubmittedPtsNs;
                firstSwapReturnedNs = lastSwapReturnedNs;
                synchronized (this) {
                    if (recording == clip) clearFirstFrameDeadline();
                }
                module.log(Log.INFO, TAG, "First GPU LUT frame submitted to the encoder");
                module.log(Log.INFO, TAG, "GPU first-frame stages ns: callbackAfterArm="
                        + (callbackNs - armRequestedNs) + ", latchAfterArm=" + (latchedNs - armRequestedNs)
                        + ", windowAndBind=" + windowAndBindNs + ", lookupUpload=" + lookupUploadNs
                        + ", swap=" + (lastSwapReturnedNs - beforeSwapNs));
            }
        } catch (RuntimeException error) {
            failRecording(clip, "GPU encoder frame failed", error, false);
        }
    }

    private static void matchRecorderOrientation(float[] transform) {
        // The original recorder encodes the camera buffer's axes and applies its own orientation
        // metadata. SurfaceTexture adds the producer's display rotation on top of its GL Y flip.
        // Keep the crop, use the original buffer axes, and leave rotation metadata to the recorder.
        float left = transform[12] + Math.min(0, transform[0]) + Math.min(0, transform[4]);
        float bottom = transform[13] + Math.min(0, transform[1]) + Math.min(0, transform[5]);
        float width = Math.abs(transform[0]) + Math.abs(transform[4]);
        float height = Math.abs(transform[1]) + Math.abs(transform[5]);
        transform[0] = width;
        transform[1] = 0;
        transform[4] = 0;
        transform[5] = -height;
        transform[12] = left;
        transform[13] = bottom + height;
    }

    private void destroyOutput() {
        if (output != EGL_NO_SURFACE) {
            eglMakeCurrent(display, offscreen, offscreen, context);
            eglDestroySurface(display, output);
            output = EGL_NO_SURFACE;
        }
    }

    private static void textureParameters(int target) {
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static FloatBuffer floats(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    private static int link() {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        try {
            int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            try {
                int result = glCreateProgram();
                glAttachShader(result, vertex);
                glAttachShader(result, fragment);
                glLinkProgram(result);
                var status = new int[1];
                glGetProgramiv(result, GL_LINK_STATUS, status, 0);
                if (status[0] != 0) return result;
                String message = glGetProgramInfoLog(result);
                glDeleteProgram(result);
                throw new IllegalStateException(message);
            } finally {
                glDeleteShader(fragment);
            }
        } finally {
            glDeleteShader(vertex);
        }
    }

    private static int compile(int kind, String source) {
        int shader = glCreateShader(kind);
        glShaderSource(shader, source);
        glCompileShader(shader);
        var status = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
        if (status[0] != 0) return shader;
        String message = glGetShaderInfoLog(shader);
        glDeleteShader(shader);
        throw new IllegalStateException(message);
    }

    private static void checkEgl(boolean success, String operation) {
        if (!success) throw new IllegalStateException(operation + " EGL 0x" + Integer.toHexString(eglGetError()));
    }

    private static void checkGl() {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("OpenGL ES 0x" + Integer.toHexString(error));
    }
}
