# E08/D17 独立 Android 只读 UI L1（2026-10-18）

本切片只验证仍在范围内的 TEMP `CONTINUATION_BLOCKED` 前驱明细页。它使用新隔离 AVD、新 Debug 包身份和合成输入，不启动 recovered-confirmation、TEMP 接收确认、激活、多跳或换机承接链。

## 输入与边界

输入来自 `.local/goal-20261016-resume/temporary-details01/host04-prefix-input/`，复制到 `.local/goal-20261018-resume/d17-ui-prefix-run01/input/` 后逐 SHA 绑定：

- `identity.json`: `b5992d6b2d4df9af88778b0d41eabdce4a30ee02797c415caafb058e36363f33`
- `synthetic-session.json`: `d50dbb0ced82896bf0118f330fd4e7092b097c96c4d90960c2bbeb38067baa36`
- `synthetic-temporary-prefix.db`: `8daa7596449ff603d24afbff34e188ac80628963e5a49825aa4e886b2c9643f6`

运行器 `.local/goal-20261018-resume/d17-ui-prefix-run01/run_prefix.py` 创建独立 `Pixel_6` 数据目录、端口 `5580`，安装当前 Debug 产品/测试 APK，导入合成数据库和会话，然后执行：

```text
adb -s emulator-5580 shell am instrument -w -r -e temporaryDetails synthetic-1704 -e evidenceRun d17-ui-prefix-run01 -e class com.acrylic.outbound.pda.TemporaryDetailsPrefixDeviceTest com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## 实际结果

`instrument.exit` 为 `0`，`instrument.log` 为 `OK (1 test)`。产品入口实际完成：阻断前驱只读显示、状态筛选、完整条码定位、跨身份拒绝、恢复身份、返回；断言调整/取消/前后页入口保持不可用，并检查“不得授予新作业或实物放行资格”。测试结束后独立 AVD 已停机，在线 USB 设备未安装或操作。

导入 DB 与设备回收 DB 的 `user_version` 和页数一致；`temporary_draft`、`temporary_scan`、`temporary_submission` 三张业务表的完整行值一致（分别 2、1、1 行）。SQLite 主文件哈希从输入值变为 `dca73bcd052673ca3292980af2db8a70c4af654772536702deab7388900a07f9`，因此本轮不宣称文件字节守恒通过；业务表事实保持不变。

证据在 `.local/goal-20261018-resume/d17-ui-prefix-run01/`：命令、安装日志、输入哈希、instrumentation 日志、前后 DB 哈希、设备 DB、直接回收文件和 AVD 日志均保留。首次 tar 回收尝试因 `run-as` 无权直接写 `/data/local/tmp` 失败，见 `collector-initial-failure.json`；修复为 `exec-out` 流式回收后完成，未重新执行产品测试。部分 inspection 文本/PNG 文件为零字节，故不以它们作为视觉证据；本报告只采用 Espresso 实际交互断言和业务表比较。

这是合成输入上的独立 Android UI L1 开发备份，不替代原 PDA、原身份、真实 HTTP/MySQL、Chrome、Python 消费、非空迁移、原模拟器或恢复全链。D17 整体、E06 L2、D18 L3、第 8 节闸门及总目标继续未完成。
