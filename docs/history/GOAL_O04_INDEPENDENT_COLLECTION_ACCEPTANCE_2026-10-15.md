# O04 独立采集验收

## 范围

本批收口 O04 独立 SKU 采集的 Android 本地实现与隔离单测：独立资料、任务计划、采集队列、SCAN/VOID、三来源防重、原字节与摘要保留、救援只读快照及加密包生成。独立来源仍是待主管对账的采集事实，不产生正式出库、库存扣减、服务端确认或放行；`localDeletionAllowed=false` 与生产默认关闭保持。

本批不宣称 E06 L2、实物 PDA、主管人工关联、跨机现场事实或 D18 L3 完成。

## 输入与证据

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`；运行输入 HEAD 见 `.local/goal-20261015/o04-independent-unit-03/head.txt`。
- 受影响源码输入哈希：`.local/goal-20261015/o04-independent-unit-03/input-hashes.json`，覆盖 `IndependentScanValidation.kt`、`IndependentTaskRepository.kt`、`IndependentScanReception.kt`、`IndependentScanSaving.kt`。
- 服务端凭据向量沿用已保存的实际生产者输出：`.local/goal-20261013/e06-identity-server-unit-03/server-original-vector.json`。该向量的身份类别和合成边界按其原验收记录保留，不把它写作原 PDA 身份。
- 本批命令、退出码、日志、输入/输出哈希：`.local/goal-20261015/o04-independent-unit-03/command.txt`、`exit-code.txt`、`gradle.log`、`evidence-hashes.json`。
- 独立救援包与信任根：`.local/goal-20261015/o04-independent-unit-03/vectors/independent.zip`、`trust.json`；测试 XML 在同目录 `xml/`。

## 验证

执行：

```text
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\acrylic-pda-outbound\.local\rtmp-o04-03 -Duser.timezone=UTC
E06_CREDENTIAL_VECTOR_OUTPUT=D:\acrylic-pda-outbound\.local\goal-20261013\e06-identity-server-unit-03\server-original-vector.json
INDEPENDENT_RESCUE_VECTOR_DIR=D:\acrylic-pda-outbound\.local\goal-20261015\o04-independent-unit-03\vectors
gradlew.bat -p android testDebugUnitTest --tests com.acrylic.outbound.pda.independent.* --no-daemon --console=plain
```

结果为 90 项执行、0 失败、0 错误、1 个既有跳过，Gradle 退出码 0，构建日志含 `BUILD SUCCESSFUL`。测试覆盖 Room 非空迁移、不可变原始任务/资料、队列和暂停、SCAN/VOID 原子尾写回滚、跨来源防重、跨重启读取、权限/签名边界、导出不携带原始凭据、加密包损坏拒绝及救援快照守恒。

同一输入在深层临时目录的首次运行保留于 `.local/goal-20261015/o04-independent-unit-01/`：90 项中 38 项为 `SQLiteCantOpenDatabaseException`，另 1 项因未绑定凭据向量/输出目录失败（共 39 项失败、1 项既有跳过）；没有产品写入或数据库事实被改变。将 Java 临时目录移至短路径并绑定上述既有向量和输出目录后复验通过，失败指纹未删除。

运行前后 `listeners-before.json`/`listeners-after.json` 仅有共享 MySQL `3306`（PID 5288）；未启动项目 API、Web 或隔离业务端口。进程、磁盘与输入快照均保留，源文件哈希未变。

## 自审与边界

- 独立采集固定原 SKU 字节、任务计划、原始输入和审计链；后续资料不能替换已创建任务的原身份、凭据或计划。
- 手输、重复、未知条码和同码异文进入显式确认/阻断路径；VOID 只改变独立采集状态，不生成正式业务回执。
- 救援包只输出凭据标识/摘要和登记设备信任根，不输出原 signed credential、登录 token、密码或私钥；包外信任根仍需人工核对。
- Robolectric/合成服务端向量只证明隔离实现和消费者契约；原 PDA、实物、主管和现场规模仍属于未验范围。

## 结论与接续

O04 独立采集开发切片达到本批退出标准，可计入 O04 的实现与隔离验收证据。它不改变 E06 L2、D18 L3、U03 完整电脑/人工关联或总目标状态；总目标继续保持 `active`。

下一动作是复用本批输入继续 E06 受影响组合和剩余 D/O/U 缺口，随后在实际候选输入执行 D18 全链整合回归。生产默认、删除边界、原库/现网和发布边界保持关闭。
