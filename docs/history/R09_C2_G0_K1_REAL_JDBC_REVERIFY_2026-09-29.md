# R09 C2 G0-K1-1 真实 JDBC 驱动特征验证（2026-09-29）

> 输入基线：`681814acb9dfd6d53f0be8589bcc5ebdcdcce039`（`codex/development`）。**本轮为独立有限授权**（用户明确批准「R09 C2 G0-K1-1 真实 JDBC 驱动特征验证」）：新增测试代码，在**独立 MySQL 隔离实例**中补齐 SQL 调用形态、事务可见性与隐式提交证据。
> **本轮不授权 B1 产品实现**，不接受 W、不放宽安全承诺、不开放生产隔离能力；未改 `src/main`、生产 Python、构建配置、迁移、契约与默认开关；未接 HTTP/Session；未实施效果门、P5 或锁协议；未做跨进程/kill/halt 实验。
> **口径（必须分开）**：`DRIVER|` ＝**直接真实驱动行为**（真实连接、独立观察连接核对已提交效果）；`RULE|` ＝**测试侧候选规则结果**（有限样本分类器，非产品实现）。模型与 Mock 结果不并入本节。
> **结论**：本轮补齐了 K1-1 中“**真实驱动行为**”这一格的部分证据，**但 K1 总体仍未通过**（未通过原因不变：生产效果门未实现/未验、窗口 W、F1′/F3/F5 全未确认分支）——不得因本轮通过而把 K1 改写为通过，也不得进入 B1。
> **状态保持（与本轮有限授权分开）**：`K1 仍未通过`；`B1 产品实现待授权`（本轮的“新增测试代码”是**独立有限授权**，不等于 B1 授权）。

## 1. 前置检查与隔离环境（修改之前采集）

| 项 | 实测 | 证据（`.local/r09-c2-g0-k1-real-jdbc-20260929-a/`） |
| --- | --- | --- |
| 分支 / HEAD / 实际远端 / 领先落后 | `codex/development` / `681814a` / `git ls-remote` 同 SHA / `0/0` | `00-pre-*.txt` |
| **修改前**工作区 | status 为空、无 `.git/index.lock`、`git diff --check` 退出 0 | `00-pre-checks.txt`、`00-pre-diff-check.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97、状态哈希 `C76522C5…`（前后各一次） | `01-pre-*`、`09-post-*` |
| 138 项历史脏文件 | 逐项 SHA-256 比对 **0/138 不一致** | `02-pre-138-hash-verify.txt` |
| 端口/数据库/证据目录查重 | 端口 **19438** 未被占用（文档中 20 处 `19438` 全为 Android `seq=`/业务条码等无关数字，严格端口语境 0 处）；`g0k1_jdbc_20260929` schema/账号全新；证据目录唯一后缀 `-a`，未覆盖既有 K1 目录 | `05-env-dedup.txt` |
| 隔离实例身份 | 端口 **19438**、MySQL **8.0.46**、datadir `D:\acrylic-pda-outbound\.local\mysql19438-g0k1\data\`、账号 `g0k1_jdbc_20260929@127.0.0.1`、`REPEATABLE-READ`、监听 PID **19692** | `07-db-identity.txt` |
| 与系统 3306 的分离 | 系统 3306 属 PID **5448**（**未连接、未查询、未使用**）；本轮实例为独立进程、独立 datadir、独立端口 | `07-db-identity.txt` |
| 凭据 | 仅存 `.local/mysql19438-g0k1/settings.json`（不入库、不复制进证据目录） | `05-env-dedup.txt` |

## 2. 真实驱动行为（DRIVER，17 条记录）

每条均记录：调用、SQL、连接参数（不含凭据）、事务前后状态、**独立观察连接**的核对结果。完整原始表：`it-records.tsv`（28 行，UTF-8）、`08-driver-records.tsv`。

| 形态 | 真实结果（独立观察连接） | 记录要点 |
| --- | --- | --- |
| 手动事务 + `commit()` | 提交前 **0** 行、提交后 **1** 行 | 观察前后差异即提交证据 |
| `setAutoCommit(true)` 隐式提交 | **1** 行（未调用 `commit()`） | 事务状态 `autoCommit=false → true`；隐式提交真实发生 |
| 手动事务 `rollback()`（负控） | **0** 行 | 证明上一条的可见行确由隐式提交产生 |
| 原始 `COMMIT`（Statement） | **1** 行 | **驱动 `getAutoCommit()` 仍为 false** |
| 原始 `SET AUTOCOMMIT=1` | **1** 行；服务端 `@@autocommit=1` | **驱动侧标志仍为 false** ⇒ `getAutoCommit()` 不能作提交证据（实测两次） |
| `PreparedStatement.executeUpdate()`（无参） | **1** 行 | SQL 仅在 prepare 期可见 |
| `CallableStatement.execute()`（无参，过程体写入） | **1** 行 | 调用体仅在 `prepareCall` 期可见 |
| `Statement.executeBatch()`（`addBatch(sql)`×2） | `[1,1]` ⇒ **2** 行 | |
| `Statement.clearBatch()` + `executeLargeBatch()` | `[]` ⇒ 行数仍 **3** | 清空后批内容不再存在 |
| `PreparedStatement.addBatch()`×3 + `executeLargeBatch()` | `[1,1,1]` ⇒ **3** 行 | 无参批依赖 prepare 记忆 |
| 可更新 `ResultSet`（`CONCUR_UPDATABLE`） | `insertRow` ⇒ **2** 行；`updateRow` ⇒ `val=updated`；`deleteRow` ⇒ **1** 行 | 三个变更方法都真实提交 |
| 注释引导语句（`/* hint */ INSERT …`） | 服务器**接受并执行** ⇒ **1** 行 | 与候选规则“不可判定即拒绝”方向相反（见 §3） |
| 多语句（默认 URL，无 `allowMultiQueries`） | **驱动拒绝**（`SQLException`） | 属**驱动不支持的形态**，单列、不计为已覆盖 |
| 多语句（`allowMultiQueries=true`） | 两条均执行 ⇒ **3** 行 | 连接参数改变行为 ⇒ 不能依赖驱动拒绝来兜底 |
| 会话级 `SET SESSION …` | 接受 | 无数据效果 |
| 厂商语法 `SOME VENDOR SYNTAX` | **服务器拒绝** | 单列 |
| DDL 隐式提交（`autoCommit=false` 下的 `CREATE TABLE`） | DDL 前 **0** 行 ⇒ DDL 后 **1** 行；随后 `rollback()` 仍是 **1** 行 | **DDL 隐式提交未完成事务**，且不可被后续 rollback 撤销 |

新建 schema 内产物（保留，不清理）：13 张 `t_*` 测试表、1 个测试过程（`g0k1_noarg`），见 `10-db-post-state.txt`。

## 3. 候选规则 vs 真实形态（RULE，10 条记录）

`RULE|` 记录为测试侧有限样本分类器（{@link G0K1JdbcBoundaryTest.CandidateGate} 的嵌套模型）对**同一批真实 SQL 文本**的判定，**不是**产品实现。完整表：`09-rule-records.tsv`。

| 形态 | 候选判定 |
| --- | --- |
| `SELECT …` | `NO_GATE` |
| `INSERT …` | `GATE` |
| `COMMIT` / `SET AUTOCOMMIT=1` | `GATE`（提交类优先） |
| `CREATE TABLE …` | `GATE`（DDL 为写入类；本轮实测 DDL 会隐式提交，故选“过门”是承重的） |
| `/* hint */ INSERT …` | `REFUSE_UNCLASSIFIED` |
| 多语句 `INSERT …; INSERT …` | `REFUSE_UNCLASSIFIED` |
| `{call g0k1_noarg()}` | `REFUSE_UNCLASSIFIED` |
| `SET SESSION …` / `SOME VENDOR SYNTAX` | `REFUSE_UNCLASSIFIED` |

**方向性说明（诚实记录）**：候选规则对“注释引导语句、多语句、CALL、会话级 SET、厂商语法”一律**委托前拒绝**，而真实服务器对其中**注释引导语句与会话级 SET 是接受的**（多语句需连接参数）。这是**安全优先的保守选择**，不是“已证明等价于真实语义”；其代价是将来效果门会拒绝一部分驱动本可执行的语句，需在 B1 实施时明确为产品行为。

## 4. 本轮验证了什么 / 没有验证什么

**已验证（真实驱动、真实服务端、独立观察连接）**：Connector/J + MySQL 8.0 下，`commit()`、`setAutoCommit(true)`、原始 `COMMIT`/`SET AUTOCOMMIT=1`、无参 `PreparedStatement`/`CallableStatement`、普通/大型批处理与 `clearBatch`、可更新 `ResultSet` 三个变更方法、注释/多语句/CALL/DDL 的真实行为与提交可见性；并实测 `getAutoCommit()` **不能**作为提交证据。

**未验证（继续单列）**：生产效果门与其接线（B1）、HTTP/Session、P5/租约/`fenced.lock` 锁协议、真实跨进程共享锁与真实进程终止、**窗口 W**、F1′/F3/F5 全未确认分支、生产默认 Session 清理、孤儿解除与生产释放、Java/Python 允许集差异、C1 历史未验项、断电/Linux 持久性与现场组合。**本轮不使用、不覆盖**系统 3306、原库与现网。

## 5. 运行记录（分层，逐轮留档）

| 层 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 定向单测回归 | `gradlew -p server test --tests 'com.acrylic.outbound.recovery.G0K1*Test' --tests '…RecoveryAdmittedDataSourceTest' --tests '…RecoveryWriterLeaseTest' --rerun-tasks --no-daemon --console=plain` | **0** | **60 项 / 0 失败**（G0K1 37 个方法） |
| 隔离库 IT | `gradlew -p server integrationTest --tests 'com.acrylic.outbound.recovery.G0K1RealJdbcBoundaryIT' --rerun-tasks --no-daemon --console=plain --info` | **0** | **13 项 / 0 失败 / 0 跳过**；27 条 `G0K1REAL|` 记录 |

IT 运行环境：`OUTBOUND_DB_URL`＝本轮精确 URL（`jdbc:mysql://127.0.0.1:19438/g0k1_jdbc_20260929?…`）、`OUTBOUND_DB_USER`＝`g0k1_jdbc_20260929`、`OUTBOUND_DB_PASSWORD`＝隔离实例口令、`G0K1_EVIDENCE_DIR`＝本轮证据目录。测试类 `@BeforeAll` **强制校验** URL/账号必须精确匹配上述隔离实例且不含 `3306`，否则直接失败（fail closed）。

## 6. 提交与边界

- 仅新增/修改**测试代码**（`server/src/test/java/com/acrylic/outbound/recovery/G0K1RealJdbcBoundaryIT.java`）与文档；`src/main`、生产 Python、构建配置、迁移、契约、默认开关均未改。
- 未发布/部署/合 `main`/打标签；未操作共享树；七类生产决定、TEMP 生产归档、生产释放继续关闭，`localDeletionAllowed=false`。
- **停止点**：完成驱动验证即停止；K1 总体状态不因此改变，不进入 B1。若发现真实产品缺陷则单列阻断、不扩范围修复（本轮未发现）。
