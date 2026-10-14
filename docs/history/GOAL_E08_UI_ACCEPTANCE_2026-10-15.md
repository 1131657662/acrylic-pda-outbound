# E08 页面与作业体验开发验收（2026-10-15）

本页记录本轮 Work、Submission、Temporary、Handover 页面重设计及恢复确认交互的开发证据。输入绑定见 `.local/goal-20261014/ui-resume-01/ui-resume-01-input-snapshot.json`；短路径复验输入状态、HEAD 和逐文件 SHA 见 `.local/goal-20261015/ui-resume-02/full-13-shortpath/input-status.txt`、`input-head.txt`、`input-sha256.txt`。所有继承改动和旧失败证据均保留，不把本轮开发备份写成 E08、E06 L2、D18 L3 或总目标通过。

## 实现范围

- Work 首屏改为可滚动的当前作业布局，优先显示车辆/来源、已保存数量、继续扫描、最近保存反馈和异常入口；关键动作保持至少 48dp 触控高度并修正字体内边距。
- Submission、Temporary 和 Handover 页面增加明确的下一动作、保存/暂停/恢复语义、技术详情次级入口、恢复确认对话框及失败重试反馈；普通队列、耐久落盘和恢复状态继续由已有模型驱动。
- Temporary 产品选择支持搜索；数量修改、移除和脏改动提示保持分离。新增恢复确认显示/worker、下一动作模型和页面测试类均保留在工作树，未清理或覆盖既有未跟踪文件。
- UI 改动路径包括 `android/app/src/main/java/com/acrylic/outbound/pda/ui/` 下的 Work、Submission、Temporary、Handover 相关类，以及 `android/app/src/main/res/layout/activity_{work,submission,temporary_draft}.xml`；对应 Android host/device 测试位于 `android/app/src/test` 与 `android/app/src/androidTest`。

## U01–U04 对照

- U01：Work 首屏、继续动作、已保存反馈、恢复确认和 Temporary 搜索已有源码与主机测试覆盖；滚动首屏和 48dp 控件布局已通过资源编译。真实 PDA 导航、系统栏、实体键及真机键盘未验。
- U02：Submission/Temporary 下一动作、队列与持久失败文案、恢复确认重试和技术详情已接线；主机测试覆盖状态选择与恢复确认路径。真实后台/网络不可用组合和长时队列未验。
- U03：Handover/恢复显示区分原件、确认和技术信息，Temporary 页面保留独立资料搜索；只读导出、电脑副本核验、USB/MTP 和 O02/O03/O04 全链未由本轮收口。
- U04：共享 XML 页面约束、字体内边距和窄屏滚动布局已更新；构建资源检查通过。320dp 四字号完整走查、连续扫码性能、队列峰值、30 分钟稳定性和操作员实测仍属未验。

## 验证证据

- `.local/goal-20261014/ui-resume-01/android-lint-build.log`：`lintDebug`、`assembleDebug` 和 `assembleStaging` 均成功。APK SHA-256：debug `34DBED215309BE80D0F948CFDCF7AA110BFC3D3E995F2CD2328B76AC759993B8`，staging `57CA01F05AD649FEAC1EC9E71D89EA5E84A900CEF391E0C10A5A61A2BCDD3EA9`。
- `.local/goal-20261015/ui-resume-02/full-13-shortpath/command.txt`：在 `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\acrylic-pda-outbound\.local\rtmp-ui-02` 下执行完整 6 个测试类（共 13 tests）；`gradle.log` 显示 `BUILD SUCCESSFUL`，`exit-code.txt` 为 `EXIT_CODE=0`。
- `.local/goal-20261015/ui-resume-02/full-13-shortpath/test-results/`：6 个 XML 合计 13 tests，0 failures、0 errors、0 skipped。`disk-before.json` 与 `disk-after.json` 记录该次隔离运行的临时目录和 D 盘余量变化。
- 先前长路径运行的两个失败 XML 仍保存在 `.local/goal-20261015/ui-resume-02/` 及 Android 测试输出目录，失败均为 Robolectric/Room 打开默认 `outbound-pda.db` 的 `SQLiteCantOpenDatabaseException`；短路径把 Java 临时目录移到项目 D 盘后复验通过。这是运行器路径根因证据，不代表产品数据库逻辑或真机行为已验。
- 原 AVD `emulator-5562`、共享 MySQL 3306 和全部旧保全目录继续保留；本轮没有启动项目服务、没有接原库/现网，也没有执行真实 Chrome 或生产操作。

## 结论与后续

本轮 UI 主机测试在短路径隔离运行中 13/13 通过，形成可审阅的 E08 开发备份；这不等于 U01–U04 完整验收。仍需真机导航/系统栏/键盘、硬件性能与长时队列、适用 Chrome 路径、E06 受影响组合、E07/E09、D01–D18 第 8 节闸门及 D18 L3 最终候选回归。总目标保持 `active`，`localDeletionAllowed=false` 和生产默认关闭边界不变。
