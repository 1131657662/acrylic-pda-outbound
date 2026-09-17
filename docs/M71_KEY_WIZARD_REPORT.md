# M71 按键向导开发与安装记录

## 最新结果：2026-09-16 实体按键映射通过

用户已独立完成向导。已从连接的M71（`22D2910042`，Demo `0.1.19-demo` / versionCode 20）只读提取并核对有效配置：**7键、14/14次完整实体DOWN/UP，位置已由操作者确认，状态APPLIED**。设备记录应用时间为北京时间2026-09-16 18:03:21.163，配置指纹与当前Android构建一致。

| 位置 | keyCode | scanCode | source | 结果 |
| --- | --- | --- | --- | --- |
| 正面扫描键 | 293 | 317 | 257 | 两次一致 |
| 左侧扫描键 | 293 | 310 | 257 | 两次一致 |
| 右侧扫描键 | 293 | 311 | 257 | 两次一致 |
| 回车 | 66 | 28 | 257 | 两次一致 |
| K1 | 288 | 315 | 257 | 两次一致 |
| K2 | 289 | 316 | 257 | 两次一致 |
| 实体返回 | 4 | 158 | 257 | 两次一致 |

核对内容：按下/松开身份与时间配对、每键两次一致、独立按压间隔≥300ms、各位置组合标识无重复、全部为实体事件；私有有效配置和COLLECTED/APPLIED副本的14次样本一致。日志含一次`KEY_PROFILE_APPLIED`，未见该runId的报告副本失败。K1第二次有一个长按repeat，正确只计一次按压，没有额外推进。

`active.json`为生效依据；`draft.json`仍标DRAFT是保留采集草稿，不代表应用失败。Demo扫码页恢复前台时自动加载有效配置，不需要修改硬编码、重装或再做按键向导。`deviceId`此次主键盘为6、返回键为5，只作诊断，不参与长期映射。

证据目录：[physical-3b28c3bd-020c-4809-89af-b137d7c65063](../demo-artifacts/m71-20260916-keys/physical-3b28c3bd-020c-4809-89af-b137d7c65063/)；包含原始active/draft、两个报告副本、匹配runId的原始事件、设备指纹及[verification.json](../demo-artifacts/m71-20260916-keys/physical-3b28c3bd-020c-4809-89af-b137d7c65063/verification.json)。active SHA-256：`1ee719ec0a3ae5b36c50d990727dc1842145d3afdb0a11496d5badcf637160b3`。

本次按键取证仅核查已有真实操作证据，没有重启、注入按键、清库或改配置。**后续B模式现场复验与真实断电恢复也已通过，Demo三项现场验收全部关闭。**三个扫描键真实广播及保存通过，取下电池重启后active配置摘要保持不变，详见[M71_ACCEPTANCE_REPORT.md](M71_ACCEPTANCE_REPORT.md)。不增加新验收项，继续已获授权的正式开发。

## 以下为0.1.8开发安装时的历史记录

日期：2026-09-16｜设备：22D2910042｜APK：0.1.8-demo / versionCode 9｜Room：3。

## 结论

按键向导已完成开发、自动检查和保留数据安装。已在 M71 检查开始页、正面键提示页、虚拟键拒绝及退出重开；停留在“开始 / 继续验证”准备页。用户目前不在设备旁，因此真实位置验证 **待执行**，进度 0/14，未创建或应用本轮已确认配置。

操作说明见 [KEY_VALIDATION_GUIDE](KEY_VALIDATION_GUIDE.md)。原 B 模式、音效和出库事实保持；正式系统开发仍受完整 Demo 验收门槛约束。

## 实现与检查

| 项目 | 结果与证据范围 |
| --- | --- |
| 顺序向导 | 正面/左/右扫描、Enter、K1、K2、返回，各两次完整按压；自动推进、可回退重测、无倒计时 |
| 配置生效 | 完整采集后由操作者确认位置；私有有效配置原子写入，返回扫码页自动加载，无需改源码 |
| 单元与 Room 检查 | 79 项，0 失败、0 错误、0 跳过；包含原 55 项以及新增 24 项按键状态、存储恢复和映射检查 |
| 构建与 Lint | 构建和签名检查成功；Lint 0 错误、51 警告，警告明细原样保留，未声称无警告 |
| 480×800 页面 | 开始、当前键及错误提示在本机可见，底部操作按钮可达；最终汇总采用滚动区域，其完整真实操作仍待用户 |
| 普通 ADB 虚拟按键 | 注入 keyCode=293，实际收到 scanCode=0/deviceId=-1/physical=false 的 DOWN/UP，正确拒绝，0/14 不变 |
| 原厂广播模拟 | ADB 发原厂 Action 被系统以受保护广播拒绝，未到达应用；不算广播隔离真机通过。向导代码仅接诊断回调、不调用出库引擎，实际扫描时的隔离仍需现场复核 |
| 退出/重启 | 退出后 force-stop 备份，再重开显示同一草稿 runId、0/14；非空进度恢复已由自动测试覆盖，真机非空恢复待现场 |
| 数据完整性 | 升级及向导检查前后 14 张表全部逐条一致，Room 3，integrity_check=ok，foreign_key_check 为空 |
| 历史数据规模 | 12 单；12,089 个输入候选；12,077 扫码；12,077 防重记录；36,276 审计事件，数量及内容均保留 |
| 私有配置保留 | 原 SharedPreferences 字节一致；原私有文件仅 AndroidX profileInstalled 安装时间标记改变。新增空按键草稿，active.json 不存在 |
| 本机原测试单 | cac82949-7073-40e5-949d-6828d7fd4705，1 包 / 305.5kg，条码 003B0051602 |
| 内存观察 | 准备页一次 dumpsys TOTAL PSS=50,135KiB（约49MiB）；只是页面采样，不代表整应用峰值验收 |
| 音频 | PASS/FAIL 文件 SHA-256 与 0.1.7 完全一致 |

此前 0.1.7 的 4 包/秒、10 分钟、2,400 条一致性和时延记录仍保留在 [B 模式报告](M71_B_MODE_REPORT.md)。其 P95=121.992ms 超过100ms目标的问题，以及旧 M71-D04 和其他完整门槛，未因本次向导更新而关闭。本次没有重复宣称新版连续性能或真实断电通过。

## 证据

目录：`demo-artifacts/m71-20260916-keys/`。

- `before-0.1.8-full.tar`、`after-0.1.8-full.tar`：含 DB/WAL/SHM、SharedPreferences、私有 files。首次 `before-0.1.8.tar` 仅保留旧范围；发现 Android 上 test 为 shell 内置后修正工具，重新生成完整备份。首次文件保留，正式比较使用 full 文件。
- `upgrade-and-wizard-integrity.json`：全部表逐条对照及私有文件比较；`installed-apk-hash.json`：安装 APK 与交付 APK 相同。
- `wizard-diagnostics.ndjson`：开始、虚拟按下/松开、暂停的原始事件。
- `wizard-intro.png`、`wizard-front-prompt.png`、`wizard-reject-virtual.png`、`ready-for-user-verified.png`：页面与最终准备状态。目录内其他黑屏截图是系统息屏时的失败截图，不用作 UI 通过证据。
- `development-checks/`：79 项 JUnit XML/HTML、Lint、签名信息、音频摘要。
- `continuation-state.json`：当前待办、原单状态、未完成的真实验证。

已安装 APK SHA-256：`2953479a7eb75e5020f1dd142c38d92897636a0369d342da04ce226298b81f84`。

未执行 git commit、git push、卸载或清库。
