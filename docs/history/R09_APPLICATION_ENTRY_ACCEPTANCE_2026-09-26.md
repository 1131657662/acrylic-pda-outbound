# R09 应用入口接线（第一批：DataSource 与 JdbcTemplate/事务）验收

2026-09-26。范围：把已验的连接适配器接进应用入口（Spring）的 DataSource，使 JdbcTemplate 与 JDBC 事务管理器走同一准入路径；同时回答并修复连接层「按 Connection 串行调用」的一处未完成项。本批**不**开放 NORMAL、**不**接 HTTP/Session/Worker/文件入口、**不**改生产默认行为、**不**跑 APK/浏览器/真机。

## 一、前置问题的结论：`RecoveryJdbcObjectHandler.invoke` 未同步 = 缺口，不是设计

判定依据（先回答，再动手）：

1. 文档要求：`docs/parallel/R09/APPLICATION_ADMISSION.md` 连接适配器细则写明「采用标准JDBC接口代理，**按Connection串行调用**以防执行与关闭竞争」。Statement/ResultSet/DatabaseMetaData 属于该 Connection，其调用必须落在同一串行边界内，该意图才成立。
2. 事实（本批前）：`RecoveryConnectionHandler.invoke` 有 `synchronized`，但 `RecoveryJdbcObjectHandler.invoke` 不持该监视器，且它调用的 `owner.assertUsable/beforeJdbcCall/afterJdbcCall/jdbcFailure` 都未同步。后果两类：
   - 执行可与 `close/commit/setAutoCommit` 重叠——正是文档要防的竞争；
   - 连接状态机（`poisoned/transactionActive/autoCommit/closed`）由子对象路径写入，与 `close` 的判定（`if (transactionActive && !poisoned)`、`if (poisoned) return;`）之间没有 happens-before。
3. 为何此前不判缺陷但仍是缺口：跨进程 fence 依然安全——`RecoveryWriterLease.poison()/close()` 共用类监视器，`Held.poisoned` 在 `close()` 内复检，文件锁不会被释放。受影响的是**每连接判定与错误呈现**：close 可能按过期状态决定，事务已由原始 SQL 开启却未记账。上一批（`R09_ADAPTER_CONCURRENCY_ACCEPTANCE_2026-09-26.md`）已把该结构事实记为「未改」，并明确去同步反证**未成立**，因此只能按「待补」处理。
4. 本批修法：子对象调用与连接调用共用同一监视器（`synchronized (owner)`），四个状态方法加 `synchronized`，probe 钩子覆盖子对象路径（测试 seam 仍只有一个）。

**可信反证（字节级）**：把 `synchronized (owner) {` 改为 `synchronized (RecoveryJdbcObjectHandler.class) {`（仍可编译，但不再与连接处理共用监视器）→ 定向 17 项中 **2 项失败**（`statementExecutionInsideTheBoundaryForcesCloseToObserveTheActiveTransaction`、`ambiguousStatementExecutionInsideTheBoundaryPoisonsBeforeCloseDecides`），`gradle exit=1`；备份按字节还原，`original`/`restored` SHA256 同为 `abfad87d9f7b9844b548f8130f94f5c74e7f24e0a06fb12dde93dc79f649dcbf`。首次反证尝试用 `if (Boolean.TRUE)` 替换，因缺 return 编译失败（无效反证，保留在 `r06-e2a-migration/.local/admission-entry-counterproof-01`）。

**必须保留的残留口径**：串行边界是**每条代理调用**，不是「语句生命周期」。真实 MySQL 首轮（attempt98）首次失败即为此：`executeQuery` 成功返回 ResultSet，但随后的 `ResultSet.next()` 是第二次调用，被并发 `close` 正当地拒绝为 `CONTROL_CONNECTION_CLOSED`。该失败已保留，检查据此改为单次在途调用断言；不得据此声称「同一连接的多调用组合原子」或「同一连接已完全串行」。

## 二、本批实现

1. `RecoveryAdmissionConfiguration`（新增，主代码，opt-in：`outbound.recovery.admission.enabled=true`）
   - 唯一的原始连接池只以 `@FlywayDataSource` 限定符暴露（迁移/引导是显式独立许可，不是业务写）；
   - admitted `@Primary` DataSource 供 `JdbcTemplate`/`NamedParameterJdbcTemplate`/`JdbcTransactionManager`/Spring Session 使用，因此无未限定入口可以拿到原始池；
   - 控制身份必须显式给出（`outbound.recovery.root/installation-id/restore-roots/checkpoint`），缺失或不一致在**启动期**失败（如 `CONTROL_FILE_MISSING`），不降级成未受控池；
   - 默认关闭：未启用时 bean 与行为完全不变（单测断言无 admitted bean、无 DataSource bean）。
2. 连接层串行补强（见一）。
3. 测试：`RecoveryAdmittedDataSourceTest` 15→17；新增 `RecoveryAdmissionConfigurationTest`（4）、`RecoveryAdmissionEntryFixture`、`RecoveryAdmissionEntryChecks`、`E2aRecoveryAdmissionEntryIT`、`RecoveryEntryLostCommitConfiguration`（测试专用丢失回执传输），并给 `E2aCrossSourceHttpIT` 增加可显式传入 source 的重载（原 2 参数签名行为不变）。
4. 排查中发现并修正的测试夹具问题：测试源里的 `@Configuration` 会被组件扫描进入真实应用上下文。`RecoveryEntryLostCommitConfiguration` 改为 `@TestConfiguration` + 显式条件属性，其他夹具不再使用可被扫描的嵌套配置类。

## 三、验证证据

### 定向单测（隔离副本，源码与共享树逐字节相同）
- `RecoveryAdmittedDataSourceTest` 17/17（含并发 Statement 与 close 的两项新断言）。
- `RecoveryAdmissionConfigurationTest` 4/4：admitted 为 primary、`recoveryMigrationDataSource` 是另一个 HikariDataSource 且带 `@FlywayDataSource`、默认关闭不产生 admitted bean、权威缺失启动失败、旧实例借用被拒（`CONTROL_INSTANCE_STALE`）。

### 完整单测 + bootJar（隔离副本 `D:/acrylic-entry-verify`）
- `gradlew -p server test bootJar --rerun-tasks --no-daemon` → **BUILD SUCCESSFUL**；102 个测试类 **714 tests=713 pass/1 既有符号链接条件 skip、0 failure/0 error**（本批 +6 项）。
- bootJar `acrylic-outbound-server-0.1.0.jar` SHA256 `faae1cbc236c6a21fd7423df447ccf38eabf439997d499a6328615229814938d`。

### 真实入口 + 独立非空 MySQL（attempt105，端口 19331，独立实例，非共享库）
IT `E2aRecoveryAdmissionEntryIT`：**tests=1 skipped=0 failures=0 errors=0**；输入为 V34 非空 seed → V40（含 V39/V40 迁移与触发器）。三个控制根各承担一个终态步骤：

| 检查 | 结果 |
| --- | --- |
| 入口接线 | primary=`RecoveryAdmittedDataSource`；`recoveryMigrationDataSource` 为另一 HikariDataSource；JdbcTemplate 与 JdbcTransactionManager 都指向 admitted；`unwrap`（DataSource 之外）全部拒绝（`CONTROL_DATASOURCE_/CONNECTION_/JDBC_OBJECT_UNWRAP_FORBIDDEN`） |
| 事务往返 | 经入口事务提交后可见（1）；显式回滚后不可见（仍 1） |
| 关闭竞争 | 在途 `SELECT SLEEP(3)` 期间 `close()` 等待 **2513ms** 才返回，执行本身成功，`INNODB_TRX=0` |
| 关闭意图 | 入口持租约时独立 Python 写者 `CONTROL_WRITERS_ACTIVE` 且不写记录；意图耐久后新接纳与入口写均 `CONTROL_CLOSING_INTENT` |
| 未知提交（提交期连接被 KILL） | 抛出 `TransactionSystemException`；被 KILL 事务的数据未留下（counter 不变）；同一入口后续写 `CONTROL_WRITERS_ACTIVE`；`INNODB_TRX=0` |
| 丢失提交回执（LostCommit 在入口之下） | 真实提交已生效（counter=2）；随后同一入口写 `CONTROL_WRITERS_ACTIVE`；`INNODB_TRX=0` |
| 排空与恢复 | JVM 退出后该账号连接 0、`INNODB_TRX` 0；独立写者用**原命令**完成耐久意图：revision=2、`ordinaryWritesAllowed=false`、`localDeletionAllowed=false`、`sharedGateIntegrated=false`、`status=PASS`；认证 shutdown 通过，监听消失 |
| 原事实保持 | `submission` 计数与 `server_state` 恢复代次整批前后一致；`recovery_normal_admission` 仍为 0 |

### 源码一致性
`source-identity.txt` 记录 12 个文件的双方 SHA256：10 个受测文件与共享树**逐字节相同**；仅 `E2aCrossSourceProfile`（副本内端口/ROOT 覆盖）与 `E2aCrossSourceHttpIT`（副本内 Web 端口 19444）为夹具改动，原因是共享树的 19330/19434 当时被另一写者占用。

## 四、未验 / 限制

- 未接 HTTP、Spring Session、Worker、文件入口；未做 NORMAL 双侧证据；`ordinaryWritesAllowed=false`、`localDeletionAllowed=false` 与「生产释放端点不存在」均不变。
- 未跑 APK/lint/浏览器/真机；未在共享树内运行（见五）。
- 一条语句的多调用组合不是原子（见一）；跨线程共享同一已接纳连接仍需调用方自行避免。
- 关闭竞争的「close 等待在途执行」只在**单条在途调用**上验证；未验证长时间挂起语句下的关闭时延上限（fence 保持为 fail-closed）。
- **迁移隐患登记：V40 已被应用后原地修改，admission-normal-90m/97 系列旧库 Flyway checksum 与实际文件不符，不可作为可复用绿色库，R09 之后不得再动 V40。**（证据：`admission-normal-90m` 证据目录生成于 2026-09-26 20:44:33，而 `V40__recovery_normal_admission.sql` 的 mtime 为 20:45:24，晚于该库迁移；差异即 R09 验收文档第 38 行记录的加列 `authority_revision`。若后续需要 R09 schema 变更，取下一个版本号 V42。）

## 五、并发写者（本批实查，管理事实）

- 21:16–21:31 期间另一会话在**同一服务端树** `D:/acrylic-pda-worktrees/r06-e2a-migration` 开发 TemporaryLink（`TemporaryLinkOperation/Target`、`TemporaryLinkChecks`、`E2aTemporaryLinkIT`，并把 `E2aCrossSourceProfile` 的运行号正则放宽为 3 位），用同一 build 目录连续跑 `integrationTest`（attempt 101–111）。
- 本批 98/99 两次真实 MySQL 尝试发生在该重叠窗口内、共用同一 `server/build`，因此**不作为最终证据**（attempt98 全面失败保留、attempt99 仅 closeRace 段落通过并保留）。
- 为遵守「同一文件/数据库/部署目标只有一个写者」，本批此后不再向共享树写入，真实 MySQL 与完整单测改在隔离副本完成；副本源码与共享树逐字节一致（见三）。两树最终整合与相应回归仍需在写者空闲时进行，本页不代表整合结论。

## 六、证据与保留失败

- 通过证据：`outputs/r06-product-20260925-01a0d8fa/admission-entry-isolated-105/`（23 个文件 + `manifest.sha256`，逐文件 SHA256 复算 mismatches=0），含 `admission-entry-*.txt`、`full-suite.txt`、`unknown-after-jvm-exit.json`、`after-jvm-database-drain.txt`、`source-identity.txt`、`integration-result.xml`。
- 失败/中间证据全部保留：`admission-entry-98`、`admission-entry-99`、`admission-entry-isolated-101/-102/-103/-104`（102/103/104 为副本 runner 账号命名与端口占用问题，已定位并修正；见各目录 `runner-failure.txt`）。
- 脚本：`.local/run-recovery-entry-{96,98,99}.ps1`（共享树，96 因 19330 被占未运行）、`.local/run-recovery-entry-isolated-{101..105}.ps1`（隔离副本）；反证目录 `r06-e2a-migration/.local/admission-entry-counterproof-{01,02}`。
