// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.util.Map;
import java.util.Set;
final class FeatureReadiness {
    static boolean ready(Map<String,Boolean> paths,Set<String> required){return !paths.isEmpty()&&paths.values().stream().anyMatch(Boolean::booleanValue)&&required.stream().allMatch(key->Boolean.TRUE.equals(paths.get(key)));}
    private FeatureReadiness(){}
}
