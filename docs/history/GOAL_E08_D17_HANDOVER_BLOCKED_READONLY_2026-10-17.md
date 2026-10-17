# E08 / D17 HANDOVER_BLOCKED 只读明细收口（2026-10-17）

本单元把现有 `HANDOVER_BLOCKED` → `TemporaryDetailsActivity` 入口收敛为只读原件核对，不恢复已取消的接收者确认、激活、多跳或换机进度承接。它是 E08/D17 的有界 UI 开发备份，不计 D17 整体、E06 L2、D18 L3 或总目标完成。

## 范围与改动

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt` | Android 明细页状态呈现与入口控制 | `TemporaryDetailsReaderTest`；`lintDebug`；`assembleDebug`；`assembleDebugAndroidTest`；独立 Android instrumentation（本轮未执行） | 接管输入 `.local/goal-20261017-resume/handoff-final01/input-final.json` SHA `BCF12D8B77D195D6B7EF05C0680CA72C0C40753755256441BD6F4A94F39E86B5`；改后源码 SHA `760BE95AA3C2C3C86C173B4C10558D206DC8D7CEA5931312DC4709BED1A992BA` |
| `docs/history/GOAL_E08_RECOVERED_CONFIRMATION_SCOPE_AUDIT_2026-10-17.md` | 范围/调用事实审计 | 文档核对、路径调用检索 | 审计输入 HEAD `16f6911e73dba039c4f2d12aca0c6c479ba03c12`；改后 SHA `1C3C57C96395718A66BFFE7E6F35F39C728B9B844AD67459038F1E93924DE774` |

`HANDOVER_BLOCKED` 成功读取后显示“旧冻结原件仅供只读核对”，隐藏“取消本机草稿”和“调整已扫码包”，继续提供只读应急导出、分页、原序号/完整条码定位、重读和返回。读取异常默认保持只读并保留原记录提示。普通 DRAFT/SCANNING 状态仍按原有资格结果显示调整/取消入口。

## 验证

- `.\gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.temporary.TemporaryDetailsReaderTest`：通过；32 actionable tasks，`temporary-details-test-c-cache.log`。
- `.\gradlew.bat -p android :app:lintDebug`：通过；报告 `android/app/build/reports/lint-results-debug.html`，`lintDebug.log`。
- `.\gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest`：通过；Debug APK SHA-256 `BD688ED0221C6E0CFA8AC12F396E17602B67F4573A5A300B588CB2B13EFB1522`，AndroidTest APK SHA-256 `1724F25B2892D7B4F670EC66DFC1B8F1E740994FCD44C64040E3C4A7F84AE548`。
- 证据目录：`.local/goal-20261017-resume/e08-handblocked-readonly-01/`。第一次使用 D 盘专用 wrapper 缓存卡在 Gradle 分发下载，原始日志保留；随后复用已有 C 盘 Gradle 分发、临时目录仍在 D 盘，任务成功。

## 未验与边界

本轮没有安装 APK、没有运行独立 instrumentation、没有连接共享 MySQL/ADB/TemporaryAdjustment1364，也没有启动旧 recovered-confirmation run03。当前共享 MySQL `3306/33060`、ADB `5037/5040` 和原模拟器 `5564/5565` 的 PID/监听前后未变化。UI L1 仍未通过；需新建未占用的独立 AVD/身份后，实际走 `HANDOVER_BLOCKED` 导航、窄屏/大字、返回/导出和零写入证据，再决定是否继续 D17 UI 组合。

## 保留

继承 WIP、旧 worker/dialog/fixture/bridge、失败日志、原资料、身份/密钥及 handoff-final01 快照均未删除或覆盖。生产默认关闭、`localDeletionAllowed=false`、R07 撤回重发政策和 R09 恢复围栏保持不变。
