// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Keeps one read-only APK inode alive through daemon, reload and final restore. */
final class SoundCompanionCommand {
    private SoundCompanionCommand() {}

    static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid audio command argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static String script(String directory, String apk, String mainClass) {
        if (directory == null || directory.isEmpty() || apk == null || apk.isEmpty()
                || mainClass == null || !mainClass.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*")) {
            throw new IllegalArgumentException("Invalid audio companion command");
        }
        String entry = " app_process /system/bin " + quote(mainClass);
        String arguments = " " + quote(directory) + " " + quote(apk) + "\n";
        // The app drains and discards this output. Detach all standard streams
        // so package replacement cannot give the cleanup shell a broken pipe.
        return "exec </dev/null >/dev/null 2>&1\n"
                + "exec 3<" + quote(apk) + " || exit 1\n"
                + "code=\"/proc/$$/fd/3\"\n"
                + "CLASSPATH=\"$code\"" + entry + " daemon" + arguments
                // A failing/killed daemon must still reach restoration. Keep the
                // shell alive until it finishes: /proc/<shell>/fd/3 is its inode.
                + "CLASSPATH=\"$code\"" + entry + " restore" + arguments
                + "result=$?\nexec 3<&-\nexit \"$result\"\n";
    }

    static String classpath(String inherited, String apk) {
        // Direct invocation remains supported. Production uses the shell's FD,
        // including for reload inside mmlistservice's mount namespace.
        if (apk != null && !apk.isEmpty() && apk.equals(inherited)) return apk;
        if (inherited != null && inherited.matches("/proc/[1-9][0-9]*/fd/3")) return inherited;
        throw new IllegalArgumentException("Invalid audio companion classpath");
    }
}
