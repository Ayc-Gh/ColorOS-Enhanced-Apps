# 实机实现与适配分析

## 研究对象

- 设备：OPPO Find X7 Ultra / PHY110。
- 系统：PHY110_17.0.0.100(CN01)，Android 17 / API 37，arm64。
- 参考实现：16.14.1912，versionCode 1455，现代模块 min API 101 / target API 102。
- LSPosed：2.2.1（7912），API 102。
- 读取来源：手机 `pm path` 指向的 参考实现 安装包，以及 `/system/framework/services.jar`、`oplus-services.jar`、`oplus-framework.jar`。
- 公开标签：`v16.14.1912(1455)`，提交 `e870b90dc01bc021e7bc749a69ddfd59eb484c78`。公开标签仍含旧版 YukiHookAPI 代码，不能据此认定当前安装包的所有实现。

安装包中的字符串使用 LSParanoid 的 Standard / Reverse / Shift 编码，部分入口另有控制流混淆。分析仅还原相关调用点，不打包原安装包与系统实现。

## 已核对的实现

### 小窗与分屏页面

安装版功能定义在反编译别名 `i33`，主要 hook 在 `h33`，启动行为在 `kd0`。这些是该安装包的混淆别名，不是稳定 API。

| 开关 / 设置 | 原安装版观察到的入口 |
| --- | --- |
| 移除小窗限制 | `FlexibleWindowUtils.isUnSupportCallerFlexibleWindow` → false；各 `isSupportFlexibleWindow` → true；`isInMultiWindowFlexibleBlackList` / `isFlexibleTaskInPSBlackList` / `isDisableCustomZoomWindow` → false |
| 不支持的比例 | `getUnSupportRatiosInFlexibleTask(String)` 返回空字符串，类型为 String，不能像旧公开源码一样写成 Boolean |
| 多模式 | `isSupportMultiMode()` / `isPsPlusEnable()`；检查调用栈，排除 `OplusSplitScreenManagerService` 与 `TaskExtImpl`，仅用于 Flexible 调用链 |
| 自由比例 | `isFlexibleTaskInSupportFreeRatiosList(Task)` → true；`getUnSupportRatiosSetInFlexibleTask(String)` 返回空 Set |
| 自定义比例 | `FlexibleTaskInfo.getRatio()` 返回用户数值 |
| 数量与外观 | `FlexibleWindowManagerService.getMaxWinNum(int)`、`getCornerRadius(int)`、`getShadowRadiusFocused(int)`、`getShadowRadiusUnfocused(int)` |
| 隐藏控件 | `FlexibleTaskCaptionView.initCaptionView()`、`FlexibleCaptionView.updateThreeButtonVisibility(boolean)`；隐藏 `mBottomHandleView`、`mControlView`、`mControlBackgroundView` |
| 贴边运行 | `exitFlexibleTask(Task,boolean,int,int)` 的原因 16 分支；记录贴边任务，重挂父 Surface 并隐藏；对焦点、展开和删除路径做配套处理 |
| 贴边状态 | `FloatHandleController.shouldForceLaunchIfTaskInFloatHandle(ActivityInfo)`、`isAlloawLockFloatPhone()`；相关接口有 OEM 历史拼写错误 |
| 优先小窗 | `FlexibleTaskController.adjustOptionsForSmartMultiWindow(...)`、`isFlexibleAcrossTaskJump(...)`、`getLaunchFromTask(...)`；`OplusSplitScreenAppReader.inInnerTaskJumpList(...)`、`isSupportLightOpen(...)` |

通知启动使用 Zoom launch flags 5；桌面启动使用 24，并设置 `key_flexible_smart_across_task_jump`。启动模式值 100 为 OEM 小窗。系统最终任务可能显示 fullscreen，是否为小窗应结合 `FlexibleTaskInfo`、caption 和实际边界判断，不能只看 AOSP windowingMode。

“分屏与小窗”是页面名称。原小窗的 `force_multi_window_mode` 不能等同于完整的“所有不支持应用强制分屏”。因此独立模块新增单独的分屏开关，不把两者混在一起。

### 截图和录屏

原开关的设置键为 `android / disable_flag_secure`。分析开始时该值为 true。关闭其他截图模块后，重启启用框架 hook 调试，在 system_server 实际转储中确认 `WindowState.isSecureLocked()` 被挂钩。此开关解除窗口的安全判断，并非替换截图 APP。

该安装包其他入口有混淆；这里把运行时已确认的窗口 hook 和独立模块的扩展实现分开描述，不将未证实的额外处理归于 参考实现。

## 本机必须补充的适配

### 小窗数量不只有一个限制

本机 `FlexibleWindowUtils.getMaxFlexibleTaskNum()` 在小设备多小窗 / 全景分支直接返回 2。单改服务的 `getMaxWinNum(1)` 无法覆盖该分支。独立实现还处理：

1. 服务 `getMaxWinNum(1)` → N；其他场景保留。
2. 服务 `getTasksNumber()` 至少 N+3，用于查询小窗后方全屏任务。
3. Utils `getMaxFlexibleTaskNum()` → N。
4. Utils `getMaxWinNum()` 至少 N+3，为布局策略保留余量。
5. 对会内联这些短方法的全景策略调用者解除优化。
6. `OplusFlexibleTaskLayoutPolicy.avoidOverlappingAreas(Rect,ArrayList,int,float,int)` 按约 32dp 纵向错开，并检查屏幕与系统栏边界。

### 真正的分屏兼容

检查本机实际接口后，覆盖普通应用 Task / ActivityRecord 的分屏支持方法及 display-area 分支，开放 `TaskDisplayArea.supportsNonResizableMultiWindow()` 和最小尺寸判断，并配合 `OplusSplitScreenManagerService` 的厂商能力判断。只对标准应用任务返回支持，保留 home、recents 等特殊任务。

### 捕获链路

独立模块保留 `WindowState.isSecureLocked()`，另外处理：

- `WindowManagerService.addWindow` / `relayoutWindow`：复制 LayoutParams 后仅清除 FLAG_SECURE，保留其他参数，避免修改调用方持有的对象。
- `WindowState.setSecureLocked(boolean)`、`Task.setSecure(boolean)`：本机 Android 17 实际存在的安全图层设置入口。
- `WindowSurfaceController.setSecure(boolean)`：供仍存在此类的兼容分支，缺失则跳过。
- 对关键系统调用者解除内联优化，避免只在调试环境命中。

客户端测试 Activity 的 flags 仍可显示 `secure=true`，而系统服务端的窗口标记已清除。这可以验证模块保留了应用自己的参数，并在服务端处理。

### 设置与恢复

现代 libxposed API 102；只需 `system` 作用域。远程偏好每个进程保持监听器强引用，用户设置关闭时调用原方法。开关默认为 false，数值 -1 表示系统默认。控件隐藏使用 WeakHashMap 保存原可见性，恢复时通过 View.post 回到其线程；贴边任务在全局窗口锁中恢复 Surface。

每个接口单独检查参数与返回类型并独立安装。日志只记录接口与首次应用事件，避免每帧输出。没有常驻服务、网络访问或系统文件替换。

## 边界

普通 FLAG_SECURE 解除不代表能读取硬件保护的 DRM 视频。分屏不会重写应用 UI。贴边保留系统任务不能覆盖应用自己在 onPause 里暂停的逻辑。多个同类模块同时修改相同系统接口可能互相覆盖，必须停用重叠功能。
