// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

public final class Config {
    private Config() {}
    public static final String PREFS="window_capture";
    public static final String UNLOCK="unlock_flexible", SPLIT="unlock_split", MULTI="force_multi_mode";
    public static final String FREE="free_ratio", RATIO="custom_ratio", COUNT="window_count";
    public static final String EDGE_COUNT="edge_count";
    public static final String CORNER="corner", SHADOW_FOCUS="shadow_focus", SHADOW_BLUR="shadow_blur";
    public static final String JUMP="jump", NOTIFICATION="notification", LAUNCHER="launcher";
    public static final String EXCLUDE_SYSTEM="exclude_system";
    public static final String HIDE_BOTTOM="hide_bottom", HIDE_TOP="hide_top", REMEMBER="remember", FOREGROUND="foreground";
    public static final String CAPTURE="capture", LOG="log";
    public static final String AIR_GESTURE="air_gesture_entries";
    public static final String AIMEMORY_WECHAT_BILL="aimemory_wechat_bill";
    public static final String GALLERY_AI_RETOUCH="gallery_ai_retouch";
    public static final String CAMERA_INSPIRATION="camera_inspiration_recommendation";
    public static final String CAMERA_LOG="camera_log_video";
    public static final String CAST="cast_enabled", CAST_QUALITY="cast_quality", CAST_BITRATE="cast_bitrate_mbps";
    public static final String CAST_FPS="cast_fps", CAST_RESOLUTION="cast_resolution", CAST_DENSITY="cast_density";
    public static final String CAST_LOW_BANDWIDTH="cast_low_bandwidth", CAST_MODEL="cast_model_degrade";
    public static final String CAST_HARDWARE="cast_hardware", CAST_GAME="cast_game";
    public static final int MAX_COUNT=10;
    public static final int MAX_EDGE_COUNT=5;
    public static final String[] WINDOW_KEYS={UNLOCK,SPLIT,MULTI,FREE,RATIO,COUNT,EDGE_COUNT,CORNER,SHADOW_FOCUS,SHADOW_BLUR,JUMP,NOTIFICATION,LAUNCHER,EXCLUDE_SYSTEM,HIDE_BOTTOM,HIDE_TOP,REMEMBER,FOREGROUND};
}
