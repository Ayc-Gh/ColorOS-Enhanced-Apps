// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.List;
import java.util.Set;

/** Exact retired keys only: capture and unrelated preferences survive upgrades. */
final class RetiredSettings {
    private static final Set<String> KEYS=Set.of("filter","blacklist","apps","free_ratio",
            "cast_enabled","cast_quality","cast_bitrate_mbps","cast_fps","cast_resolution",
            "cast_density","cast_low_bandwidth","cast_model_degrade","cast_hardware","cast_game");
    static List<String> presentKeys(Set<String> saved){return saved.stream().filter(KEYS::contains).collect(java.util.stream.Collectors.toList());}
    private RetiredSettings(){}
}
