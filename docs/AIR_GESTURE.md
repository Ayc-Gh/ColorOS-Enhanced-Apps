# 1.3.0：隔空手势适配

主界面增加第四个“隔空手势”入口。AON（感知与手势识别）和 AOD（息屏显示）分别设置。

## 设备与对照

适配目标为 OPPO Find X7 Ultra / PHY110，PHY110_17.0.0.100(CN01)，Android 17。读取本机 AONService 15.0.73 和手势体感 8.6.0 分析实际实现，不根据其他机型推断硬件能力。

对照模块的原开关在 `com.oplus.gesture` 与 `com.aiunit.aon` 两个包的进程中，把以下六项 `OplusFeatureConfigManager.hasFeature(String)` 查询均改成 true：

- `oplus.software.aon_gestureui_side_enable`
- `oplus.software.aon_gestureui_enable`
- `oplus.software.aon_gesture_press`
- `oplus.software.aon_gesture_control_enable`
- `oplus.software.aon_sensorhub_enable`
- `oplus.software.aon_explorer_enable`

本机实测中，同时强制开启后，AONService 初始化日志显示所有引擎都被跳过，包含原生 `sensorhub_engine`。原因是 Explorer 分支先排除 SensorHub，而 SensorHub 分支又排除其他引擎。两种能力同时强制开启会造成互斥条件冲突。

关闭对照开关并只重启 AONService 后，日志恢复创建 `sensorhub_engine`。用户在系统“学习隔空接听来电”页面确认识别成功。手势体感中的能力查询结果由 `lb.i` 及活动静态字段缓存；没有重启手势体感时，额外入口会继续显示，这不等于 AONService 仍需要强制开启六项能力。

## 独立实现

- 新入口仅处理 `com.oplus.gesture` 包，包含其主进程和设置进程。
- 只开放前四项设置相关查询，保留原始调用，再按开关决定是否开放入口。
- 不改写 SensorHub、Explorer、原有接听与静音能力。不向 `com.aiunit.aon` 安装钩子，不增加其作用域。
- 不修改系统文件、原生算法、模型、相机权限、应用支持列表或系统手势开关。
- 新设置键 `window_capture/air_gesture_entries` 默认关闭，独立于窗口、投屏和 AOD 设置。
- 三个原有入口及其配置保留。关闭新开关并重启两个组件，恢复原生入口。

设置页提供系统设置入口和三个系统学习页面。学习页面使用系统已有的 `com.oplus.action.AONLearnActivity`，分别传入 `learn_type` 393220、393222、393224；不注入识别结果。

## 生效与恢复

1. 覆盖安装同签名 APK，在框架中启用 ColorOS 增强并勾选“手势体感”。原有四个作用域保留。
2. 关闭其他模块中同类的引擎强制开关，避免重叠。
3. 在“隔空手势”页启用解锁，点击“应用并重启两个组件”。该操作需要 Root；只重启 `com.oplus.gesture`、`com.aiunit.aon` 并重新打开系统设置。
4. 在系统中分别设置接听、滑动与暂停／播放，并检查“支持的应用”。解锁入口不自动开启这些系统开关。
5. 可通过系统学习页面验证。实际应用还受系统支持列表、前台场景、机身姿态、距离与光线影响。

按钮只运行固定的组件列表，不接受用户输入的命令。没有 Root 时可在系统应用管理中手动停止两个组件后重新进入设置。重启会重新计算进程中的能力缓存，因此开关改变后应按此流程生效。

## 验证边界

本版已实机安装并重启加载，签名与既有整合版一致。56 项单元测试通过，正式版构建和静态检查通过（0 错误、26 警告）；既有 44 个 Java/Kotlin 文件中，只有配置常量、主入口和设置桥接这三个文件调整接线，其余 41 个功能文件保持逐字节一致。

实机关闭解锁并通过页面按钮重启两个组件后，恢复只有“隔空接听或静音”的原生入口；重新开启并重启后，恢复三个设置项。系统原有三个手势开关值保持原样。日志确认本版只在手势体感进程安装新增钩子，AONService 仍创建原生 `sensorhub_engine`，没有创建冲突的 Explorer 引擎。核对时临时启用的诊断日志已恢复原值。

用户在本版运行期间，分别完成系统学习课程并确认隔空接听、隔空滑动、隔空暂停／播放识别成功。**这三项是学习页面的实体手势验证，不代替真实来电或第三方视频应用的控制验证。** 后两者未在这轮测试。

对照故障、原生引擎恢复、独立模块结果分别记录，不将入口显示、学习课程识别和真实应用控制混为同一项通过。具体构建与实机验证结果见 `evidence/gesture-1.3.0/validation.json`。未验证的其他 ROM、整夜功耗或全部应用兼容性不扩大为已验证。
