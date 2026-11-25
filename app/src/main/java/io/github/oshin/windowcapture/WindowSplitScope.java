// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** A display-area query has no task of its own. Only an enclosing split query may opt it in. */
final class WindowSplitScope {
    interface Query { Object run() throws Throwable; }
    static boolean isPhoneCanvasRequest(int displayId, int launchScenario) {
        return displayId == 0 && launchScenario == 2;
    }
    record CanvasActivity(String packageName, String className, int uid) {
        boolean matches(CanvasActivity other) {
            return packageName != null && !packageName.isEmpty() && className != null && !className.isEmpty()
                    && uid >= 0 && equals(other);
        }
    }
    static boolean canvasTask(int requestedId, int actualId, int activityType,
                              CanvasActivity requested, CanvasActivity actual) {
        return actualId >= 0 && (requestedId == -1 || requestedId == actualId)
                && activityType == 1 && requested != null && requested.matches(actual);
    }
    private final ThreadLocal<String> standardPackage = new ThreadLocal<>();
    boolean active() { return standardPackage.get() != null; }
    boolean matches(String name) { return name != null && !name.isEmpty() && name.equals(standardPackage.get()); }
    Object query(boolean standard, String pkg, Query original) throws Throwable {
        String previous = standardPackage.get();
        if (standard) standardPackage.set(pkg == null ? "" : pkg);
        else standardPackage.remove();
        try {
            Object result = original.run();
            return standard ? Boolean.TRUE : result;
        } finally {
            if (previous == null) standardPackage.remove();
            else standardPackage.set(previous);
        }
    }
}
