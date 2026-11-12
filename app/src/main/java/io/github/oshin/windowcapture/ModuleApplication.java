// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
public final class ModuleApplication extends com.op.aod.enhance.AodApplication {
    @Override public void onCreate() { super.onCreate(); SettingsBridge.init(this); }
}
