# D15 升级检查点与恢复切片验收（2026-10-15）

本页记录 D15 升级检查点、非空迁移与恢复到空伴随库的隔离开发切片。它不代表 D15 全部、E07 L2、D18 L3、旧程序兼容、异地恢复或生产恢复通过。

## 范围与输入绑定

- 改动路径：`server/src/test/java/com/acrylic/outbound/e2averify/E2aUpgradeCheckpoint.java`、`server/src/test/java/com/acrylic/outbound/e2averify/E2aUpgradeRecoveryIT.java`。
- 影响层：Flyway/JDBC 非空升级、SQL dump/import、私有 storage 与开发签名字节守恒、空目标恢复拒绝覆盖、隔离 MySQL 停机与证据保全。
- 必跑清单：测试编译；真实隔离 MySQL；非空种子；迁移到升级前检查点及增量迁移；升级后 synthetic probe；SQL dump/import 到全新空伴随 schema；重复恢复拒绝；源库升级后事实保留；storage/签名字节、全表与停机监听保全。
- 复用依据：1548 的失败日志、失败 XML、dump、数据库和 failure fingerprint 仅作失败背景；1549 的 1/1 结果仅作候选实现背景。1550 使用独立数据库、端口、datadir、storage、身份和证据目录，不复用前两次数据库。
- 本批接管输入为 HEAD `83b4f1a288dd191b2af360ce092885a65e2dd24d`、分支 `codex/development`；输入快照、工作树 patch、旧看板和磁盘/进程/监听快照见 `.local/goal-20261015/d15-upgrade03/`。

## 运行结果

### 1548：失败并完整保全

证据根：`.local/goal-20261015/d15-upgrade01/`。真实迁移、非空种子和升级前快照已经发生业务写入，随后旧夹具因反复创建 DriverManager 连接触发 Windows `java.net.BindException: Address already in use: connect`。失败 XML、dump、datadir、storage、日志和 failure fingerprint 全部保留；不计通过，也不复用数据库。

### 1549：功能通过，保留为候选历史

证据根：`.local/goal-20261015/d15-upgrade02/`。`E2aUpgradeRecoveryIT` 1/1 通过，真实迁移、非空数据、增量迁移、SQL dump/import、storage/签名字节、升级后 probe、空目标恢复及非空覆盖拒绝均执行。保全结果 `result.json` 为 `PASS`，2 个数据库、359 张表、`dumpConserved=true`、`authenticatedShutdown=true`、`listenerCount=0`；最终守卫生命周期修正后由 1550 重新执行，1549 不单独收口。

### 1550：最终切片通过

证据根：`.local/goal-20261015/d15-upgrade03/`；隔离身份文件为 `1550-identity.txt`，端口为 19330，伴随库为源库名加 `_restore`，datadir 为 `.local/e2a-cross-20260925-1550/data`。

- 命令：`gradlew.bat -p server recoveryControlClasspath integrationTest`，子进程使用 D 盘 Java 临时目录和 `E2A_CROSS_VERIFY=true`；日志为 `1550-gradle.log`。
- Gradle 退出码 0，`1550-test-result.json` 标记 `completed=true`；JUnit XML `TEST-com.acrylic.outbound.e2averify.E2aUpgradeRecoveryIT.xml` 为 `tests=1, failures=0, errors=0, skipped=0`，并存在 `.local/e2a-cross-20260925-1550/test-completed.marker`。
- `E2aUpgradeCheckpoint` 先保存规范化 baseline、SQL dump、storage、开发签名字节；升级后在源库插入 synthetic product probe。恢复仅允许导入同身份核验且完全空的伴随 schema；第二次恢复因非空目标被拒绝，源库升级后的 probe 与事实仍保留。
- `1550-preserve/result.json`：`status=PASS`、`databases=2`、`tables=359`、`dumpConserved=true`、`authenticatedShutdown=true`、`listenerCount=0`、`attemptHashedFiles=561`、dump SHA-256 为 `e0764f1ad315c3fb9b1086a87687d6da5045585b6ca82c610b3f2540df6b705e`。
- `dump-conservation.json` 标记 `unchanged=true`；JDBC 表/列/行摘要、storage 文件和开发签名字节均前后相等。19330 与 19434 无监听；共享 MySQL 3306/33060 未操作。

## 自审与边界

两份新增 Java 文件分别为 122 行和 91 行，均低于 200 行；方法体保持在 30 行以内。未发现密码打印或硬编码，恢复目标同时核对数据库名、当前用户、端口、datadir 和空表集。`AutoCloseable` 关闭源库与恢复库连接池，避免测试结束后遗留连接。失败运行器、原始日志/XML、失败库、dump/binlog 和唯一证据继续保留。

本切片只证明隔离环境中的一次非空增量升级与“旧检查点恢复到空伴随库且拒绝覆盖”的开发事实；不证明旧 APK/旧客户端回退、生产恢复、外部介质/异地保管、现场身份、完整 D15、E07 L2、D18 L3 或总目标第 8 节完成。

## 后续

继续推进 E07 业务跨截点重建、E06 L2/D18 L3 受影响组合和剩余 D/O/U 闸门；保持生产 default-off、`localDeletionAllowed=false`、不接原库/现网、不合 `main`、不发布部署。总目标仍为 active。
