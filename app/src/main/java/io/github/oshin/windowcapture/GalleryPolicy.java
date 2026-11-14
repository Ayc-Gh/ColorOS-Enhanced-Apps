// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Restore one absent local capability; all service and editing policies remain native. */
public final class GalleryPolicy {
    public static final String HOST="com.coloros.gallery3d";
    public static final String KEY="gallery_ai_retouch";
    static final String FEATURE="feature_is_support_ai_retouch";
    static boolean unlock(boolean enabled,String feature,boolean nativeValue){
        return enabled&&!nativeValue&&FEATURE.equals(feature);
    }
    private GalleryPolicy(){}
}
