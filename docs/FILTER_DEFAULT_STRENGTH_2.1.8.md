# 2.1.8：固件原生滤镜默认强度核查

从 PHY110_17.0.0.100 的 `my_product.img` 提取 `/app/OplusCamera/OplusCamera.apk`，版本为 7.006.191（60000）。分区校验符合固件校验文件，相机 APK 的 SHA-256 与此前从手机提取的当前相机完全一致：`9eed439a83d6f4e9e666619626cd5371cc31da7db31a05138f35745cc8c2aa97`。

原生默认值方法 `zf.d.j(String)` 对 `mono.cube.rgb.bin`（单色）、`blackandwhite.cube.rgb.bin`（黑白）、`blackandwhite_HC_normal.bin`、`blackandwhite_HC_master.bin` 返回 50，其余通用滤镜返回 100。已分别核对反编译方法和原始分支指令；无关方法的反编译告警不作为证据，原始指令检查正常完成。

调色控制器 `ag.y.b` 把该值作为未保存强度时的默认值；滤镜界面 `ag.t.d3` 在 effect-style 能力开启且所选列表／索引有效时也使用该默认值，其余分支使用 100。此前设备原生运行时快照记录该能力值为 1，实际关闭／开启模块的对照也都记录单色、黑白为 50。固件 ODM 的加密配置没有解码，不把运行时能力值称作已直接解出的固件配置。

因此本机原有单色、黑白在默认强度下保留部分颜色，与原生行为一致。已有保存强度可覆盖默认值。新增反差黑白采用低／中／高对比度插值，不能把它的强度含义等同于普通单色、黑白与原图混合。

本次仅在电脑分析固件，没有访问手机、修改固件或更改模块 APK。公开摘要见 [validation.json](evidence/filter-default-strength-2.1.8/validation.json)，未附带完整 OEM APK 或反编译类。
