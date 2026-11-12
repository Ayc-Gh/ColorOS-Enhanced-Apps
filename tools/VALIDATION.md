# 可选的 root ADB 测试入口

`DeviceProbe.java` 仅用于本机测试，不打包进正式模块。它提供小窗启动、按任务启动分屏、退出分屏、清除本测试包任务等命令。`clean` 只处理与本测试应用安装标识完全匹配的任务，实际标识见 `probe/build.gradle.kts`。

先构建并安装 `probe/`；再用 Android SDK 的 android.jar 编译此 Java 文件，使用 d8 生成 classes.dex，推送为 `/data/local/tmp/windowcapture-probe.dex`。

```powershell
adb shell 'CLASSPATH=/data/local/tmp/windowcapture-probe.dex app_process /system/bin DeviceProbe flex 1'
adb shell 'CLASSPATH=/data/local/tmp/windowcapture-probe.dex app_process /system/bin DeviceProbe split 任务ID'
adb shell 'CLASSPATH=/data/local/tmp/windowcapture-probe.dex app_process /system/bin DeviceProbe dismiss'
adb shell 'CLASSPATH=/data/local/tmp/windowcapture-probe.dex app_process /system/bin DeviceProbe clean'
```

测试应用的 Probe1 不允许调整大小，Probe2–5 允许调整大小，各有独立 taskAffinity。启动 Probe1 时 `--ez secure true` 申请 FLAG_SECURE，`--ez secure false` 关闭申请。私有文件 `.Probe1.txt` 保存任务、ticks、resumed、secure 状态。

截图与录屏对照必须重新创建目标窗口；系统可能缓存既有图层。请只用测试页面采集证据，测试结束后清理自己的测试任务和 APK。
