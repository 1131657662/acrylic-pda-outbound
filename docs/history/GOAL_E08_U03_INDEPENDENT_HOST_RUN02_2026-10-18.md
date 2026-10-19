# E08 / U03 独立采集与数据保全 host 验证 run02（2026-10-18）

## 范围与结论

本轮只验证独立采集、独立来源防重和救援快照的 Android host/Robolectric 消费。它补充 U03 的实现级证据，不是 Android 页面 L1，也不替代真实 DocumentsUI、独立设备会话、原身份/Keystore、真实 HTTP/MySQL 或 E06 L2。

结果：修正运行执行 17 项，0 failure、0 error、0 skip，Gradle 退出码 0。凭据 parity 1 项、救援快照 12 项、正式/独立/临时跨来源防重 4 项均消费了现有实现和已登记的服务端签名向量。

## 输入、命令与证据

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/远端以看板事实块为准；本轮没有产品源码改动。
- 证据根：`.local/goal-20261018-resume/u03-independent-host-run02/`。
- 既有凭据输入：`.local/goal-20261013/e06-identity-server-unit-03/server-original-vector.json`，仅作签名向量消费，不生成替代身份。
- 救援输出写入本轮新建的 `vectors/`，没有覆盖 `.local/goal-20261015/o04-independent-unit-03/vectors/` 历史原件。
- 命令：

  ```text
  JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\acrylic-pda-outbound\.local\tmp\u03-host-run02 -Duser.timezone=UTC
  E06_CREDENTIAL_VECTOR_OUTPUT=D:\acrylic-pda-outbound\.local\goal-20261013\e06-identity-server-unit-03\server-original-vector.json
  INDEPENDENT_RESCUE_VECTOR_DIR=D:\acrylic-pda-outbound\.local\goal-20261018-resume\u03-independent-host-run02\vectors
  .\gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.independent.IndependentCredentialParityTest --tests com.acrylic.outbound.pda.independent.IndependentRescueSnapshotTest --tests com.acrylic.outbound.pda.independent.IndependentScanSourceTest --no-daemon --console=plain
  ```

- 原始日志：`gradle.log`；JUnit XML：`xml/`；逐文件来源哈希：`source-sha256.json`；产物哈希：`artifact-sha256.json`；执行后环境/监听/ADB/磁盘：`environment-after.json`。
- 收口清理尝试：`cleanup-attempt.json` 记录了两个本轮 Java/Robolectric 临时目录的明确绝对路径、输入哈希与文件计数；Windows 执行策略拒绝递归删除，未绕过，释放 `0` bytes。该文件已纳入本目录逐文件哈希清单。

## 失败前置与修正运行

首轮 run01 有意记录了未绑定 `E06_CREDENTIAL_VECTOR_OUTPUT` 的错误：第一个 parity 用例在 `IndependentCredentialParityTest.kt:14` 以 `IllegalStateException` 拒绝，随后停止本次由当前会话启动的 Gradle 进程，原日志、线程栈和停止前进程清单保存在 `.local/goal-20261018-resume/u03-independent-host-run01/`。这是运行器输入缺失，不是产品业务结果，未将其计为通过或隐藏。

绑定既有向量并使用独立输出目录后，run02 的三个 XML 分别为 1、12、4 项，均为 0/0/0；日志末尾为 `BUILD SUCCESSFUL`。host fixture 使用合成隔离数据库/密钥，仅证明字节守恒、原件引用、跨来源 guard、VOID/待对账和救援包信任边界。

## 页面与生产边界

- 未启动项目 API、隔离 MySQL 或 emulator；ADB 仍只有 USB M71 `22D2910042`，未操作原设备。
- 现有 `IndependentCredentialDeviceTest`、`IndependentRescueDeviceTest`、`IndependentScanDeviceTest` 和 `IndependentSkuDeviceTest` 都要求独立 AVD、断网、预置独立任务/会话/Keystore，或主机在真实 DocumentsUI 中选择原始文件。本轮未找到可证明可恢复的独立设备 tar/会话输入，因此没有伪造页面运行。
- 独立 host 通过不能计 Android UI L1、U03 完整验收、E06 L2、D17、D18 L3 或第 8 节闸门；`localDeletionAllowed=false`、七类生产决定、TEMP 生产归档、正式签名、原库/现网、部署和发布继续关闭。

## 复用与下一步

- 复用依据：`history/GOAL_O04_INDEPENDENT_COLLECTION_ACCEPTANCE_2026-10-15.md` 的既有独立实现边界和签名向量路径；本轮只在未改动的独立实现上重新消费并保留当次 XML。
- 下一步仍是取得可证明的全新独立设备输入后执行页面实际消费：凭据/任务绑定、完全断网、真实 DocumentsUI 选择、扫描/VOID/导出、Room/原件守恒、重启/撤权 fail-closed。没有这些输入时保持阻断并推进不依赖它的独立范围。
