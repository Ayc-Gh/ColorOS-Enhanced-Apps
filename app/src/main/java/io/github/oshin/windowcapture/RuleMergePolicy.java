// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.util.Set;
final class RuleMergePolicy {
    private RuleMergePolicy(){}
    static boolean keepPatched(boolean enabled,Set<String> nativePages,String page){return enabled&&page!=null&&!page.isBlank()&&!nativePages.contains(page);}
}
