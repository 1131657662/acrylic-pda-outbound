# 第五轮非空 V33 升级独立验收

执行者 W2 `/root/foundation_migration_verify`；独立树 `D:/acrylic-pda-worktrees/r06-foundation-verification`，短分支 `codex/r06-foundation-verification`，起点 `85cb7cef02fb8728599b6801a28824183305dab8`。生产协议、Flyway V34、共同占用及消费者只由 W1 编写。本目录不修改主调度五份文档、生产代码或旧共享 fixture。

## 当前检查点

2026-09-21：已在新 `127.0.0.1:19324` 实例建立唯一 V33 合成基线。首次编译在 `LegacySnapshot` 的 `ObjectNode.put` 与 JDBC 泛型推断处报两条编译错误，未运行测试、未产生种子；显式 String 局部变量修正后，`compileTestJava`、9 项环境护栏单元与 1 项种子 IT 通过，0 失败、错误、跳过。第二次只运行首次成功的种子，不是重置后重种。

本次快照为 93 表含 Flyway；92 张业务表逐列原值，其中 72 张非空、1,604 行，38 个私有文件，20 组场景。原始 `seed-first.log`、`seed-second.log`、XML、`v33-original.json` 和 `seed-manifest.json` 位于 ignored `.local/evidence/`；后者含合成身份资料，不入 Git。

独占验收断点 `06da075` 保存后，以 `62b08a7` 保留历史合入 W1 固定生产提交 `0311dfaf4255671a605a17aa3defc145df9621a6`。实际非空 V34 迁移、92 张旧业务表逐列值、原定义白名单、38 个文件、新增映射/决定基线、重复迁移 0 次、原签名前检均通过，保存 `v34-after-migration.json`：100 张业务表、1,616 行、74 张非空，加 Flyway 共 101 表。原 V1–V33 Flyway 历史逐项不变。

**原 `upgrade-first` IT 末尾未通过**：旧 seed 的 `formal-success-true` GET 返回 401，原 access token 已自然超过 900 秒；共享旧工厂没有保留设备 EC 私钥，不能真实签挑战续期。没有修改会话到期、设备 key、系统时间或鉴权。后续独立 V34 保留核验与服务层 DTO 重建单列，不能改写为原 GET 已通过；7 类新 fixture API 回归也不代替旧 seed 的真实重新鉴权。

| 原 V33 场景 | 生成方式与边界 |
| --- | --- |
| 正式 SUCCEEDED 扣账/不扣账、REVIEW_REQUIRED、REJECTED、ABORTED、RECEIVING | 原正式服务/MockMvc 与现有测试工厂；成功含签名归档 |
| 正式 REGISTERED | 定向夹具通过事务调用现有 `SubmissionStore.lock`，不是新增注册 HTTP 接口 |
| TEMP A、B、全块未 finalize、C、完整待审、库存冲突 | 原 TEMP 准备、上传、块、取消服务；B 含 400 个已确认失败输入；冲突仅定向设置本轮库存 blocked_reason |
| 正式恢复、CONTINUATION_RESERVED、正式隔离 | 原召回/恢复/续作/实物处置服务 |
| PC_RESERVED、PC 成功、PC 混合保留/释放/隔离 | 原 PC 录入/提交/交接服务 |
| 正式/TEMP 混合设备审计与连续签名归档 | 原审计接口；五份准备、激活、FH 计数器及旧幂等键随事实生成 |

原 fixture 的准备身份授权、download_ack 标记等定向数据库辅助仍按原测试工厂实现；不将其称为真实设备操作。MockMvc 不是 HTTP/设备验收。

## 护栏与验收方法

所有执行 fixture 默认关闭。开启时 URL 必须精确匹配 `127.0.0.1:19324`、已登记完整库名及参数；账号、私有目录和 flag 同时匹配。连接后在任何 Flyway/DML 前复核 `DATABASE()`、端口、`CURRENT_USER()` 与本树 `.local/mysql/data/`。种子连接前先要求精确原始库命名空间，再要求空库；六个负向命名空间即使各自配置合法也不能生成或覆盖原始种子证据。

原值快照按原主键排序，对每列保存 JDBC 原 bytes 的 Base64；NULL 单列表示。原 `receipt_text` UTF-8 字节、JSON 的数据库规范值、BLOB、FH 与原 key 均属于逐列比较。私有文件记录相对路径、长度、SHA-256。服务层 `SubmissionService.response` 以原 Head 重建 DTO 后比较，不宣称 GET/重新鉴权或原 HTTP 响应字节一致。

SHOW CREATE 原定义逐条核对；允许变化白名单仅 `bale_commitment.order_id` nullable 与 `ck_commitment_owner` 的 SOURCE_RESERVED 扩展，其余旧列类型/nullability、PK、唯一键、旧 FK/CHECK/索引必须保留。新增列、索引、表及回填另验。

缺文件/坏签名由显式验收前检验证，SQL/Flyway 本身不能核验私有文件。负向库仅使用本轮 V33 合成快照派生，命名空间为 `acrylic_outbound_foundation_verify_20260921_{dirty,dirty2,missing,signature,ddl,lock}`；各账号和私有目录独立。复制工具以全部 FK/CHECK 始终启用方式迁移空库至 V33、按依赖插入原事实，并在事务中暂存允许 nullable 的循环引用后恢复原值；不禁 FK、不删除历史、不自动 repair。原值/文件复核后才允许定向注入故障。

首个 `_dirty` 副本复制时，MySQL CHECK 3819 被 Spring 包装为 UncategorizedSQLException，复制器原仅捕获 DataIntegrityViolationException，因而失败并事务回滚；原库和日志保留。修正只在**确实暂置 nullable 引用、errorCode=3819 且 SQLState=HY000** 时延后该行，任何其他错误仍抛出。采用全新 `_dirty2`，不复用失败库。`_dirty2/_missing/_signature/_ddl/_lock` 五份完整合成副本均通过复制 IT，逐列/文件与原冻结快照相同。VIRTUAL/STORED GENERATED 列由数据库计算；DEFAULT_GENERATED 普通列仍复制原值，已在首次复制前按独立审查修正。

## 最终实际验证

以下是本执行树的实际运行，生产/迁移依赖固定为 `0311dfaf4255671a605a17aa3defc145df9621a6`。各组最终 XML 均为 0 失败、错误、跳过；上述编译、首个复制和旧 GET 失败仍单独保留。重复执行同一复制/负向测试方法不计为不同套件案例，也不与 W1 作者或其他组合结果相加。

| 验证组 | 本树实际结果与证据 |
| --- | --- |
| 环境护栏 | 最终 15 项单元通过，含 6 个负向命名空间不能写原种子证据；`preservation-final-results/` |
| 新 V33 基线 | 1 项 IT、20 组合成场景；`seed-second-results/` |
| V33 完整副本 | 同一复制 IT 分别在 dirty2/missing/signature/ddl/lock 执行 5 次，各 1 项通过；对应 `copy-*-results/` |
| 故障拒绝 | 同一负向 IT 分别执行 5 次，各 1 项通过；对应 `negative-*-first-results/` |
| V34 保留与 DTO | 1 项 IT 通过，旧逐列/定义/文件/Flyway 历史、回填、签名和服务层 DTO 核对；`preservation-final-results/` |
| SQL 关系约束 | 8 项 IT 通过，跨仓、非 TEMP、异来源 evidence set 和 SOURCE_RESERVED/null 受控反例；`compatibility-first-results/` |
| 既有 API 回归 | 70 项 IT：Continuation 12、Disposition 11、PC lifecycle 5、Recall 11、Submission 12、TEMP source evidence 5、TEMP submission 14；新 fixture，`compatibility-first-results/` |

最后一组运行合计为 8 项 schema 加 70 项 API，共 78 项 IT，首次运行通过。编译通过；W2 未运行 `bootJar`。`.local/evidence/final-test-summary.json` 留存各组 XML 计数和 SHA-256。W3 已独立检查最终 XML 和冻结快照；最终提交仍须按固定 SHA 完成正式复审。

迁移快照 `v34-after-migration.json` 与 `v34-preservation-verified.json` 在后续 API 回归前保存；后续新 fixture 产生的行不能回算为升级回填。92 张旧表逐列原值及原 38 文件不变，100 张 V34 业务表共 1,616 行的数量只对应该冻结时点。6 个 TEMP scope 的 mapping 0/1 与 decision 0 标记为 MIGRATED_BASELINE，没有伪造 evidence、command 或 event。

## 负向状态与独立审查结论

脏关系在永久 DDL 前拒绝；缺文件与损签名由验收前检拒绝。DDL 冲突保留预建 `source_evidence_set` sentinel、部分 DDL 和失败 Flyway 历史，不 repair。缺失文件仅移到副本内 `.retained-negative-original`，没有销毁。真实元数据锁证据记录 holder 8924 的 SHARED_READ/GRANTED 与迁移连接 8929 的 EXCLUSIVE/PENDING；`lock_wait_timeout=10` 实际 10,590 ms 失败，未添加 mapping_version，原值/文件保持，失败历史保留且再次迁移拒绝。见 `negative-lock-wait.json` 和 `negative-lock-outcome.json`。

对固定 W1 生产代码、V34 和旧消费者的独立只读审查未发现当前基础范围的阻断。SOURCE_RESERVED 的可选 order 同仓 FK 已在 `0311dfa` 中修正，RecoveryStock nullable order 与旧同 order 冲突路径已检查。SQL 反例只验证已声明的关系约束，不代表证据完整性或生产写入口：精确历史 mapping/canonical、set/hash/verification 语义、STOP_PROOF kind、K 的 order 与当时 canonical、resourceId/receiptId、签名字节、权限和不可变性仍由未来窄写入口负责。

原 seed GET 401 的限制尚未解决；服务层 DTO 重建和新 fixture API 回归均不能替代原身份续期、原 GET 或原始 HTTP 响应字节验收。台账执行器、证据写、决定/放行、R09 V34 恢复支持尚未开放。OpenAPI 0.29/116 路径、Android code20/Room17 保持，TEMP 仍不能确认或放行。

## 环境收尾

2026-09-21 11:35（Asia/Shanghai）再次核对 `@@port=19324`、本树精确 datadir、root@localhost 与 PID 19664 的专用配置后，经显式 TCP `mysqladmin shutdown` 正常停止。持久会话退出码 0，日志含 Normal shutdown/Shutdown complete；随后确认 PID 和 19324 监听均不存在。数据、签名、文件、失败副本与证据全部保留，见 `before-shutdown.json`、`shutdown.json`。

停机前主库 101 表、34 条成功 Flyway 历史；dirty/missing/signature 各 93 表、V33/33 条成功历史；dirty2/lock 各 93 表、34 条历史含 1 条失败 V34；ddl 97 表、34 条历史含 1 条失败 V34。该状态记录包含后续 API fixture，不能替代冻结迁移快照。

19324 初始化的第一次子进程随工具生命周期结束，之后以同一新 datadir 重启并留有 XA crash recovery 日志；当时没有业务种子。原 13316/19321/19322 及 R05/R06/R09 库、设备/AVD/M71、HTTP/代理/现网均未操作。未合 development/main、未部署；旧 B/C/D 不恢复。下一动作是提交推送本树、交 W3 固定 SHA 复审，由主协调指定后续整合。
