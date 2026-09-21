# W2 非空升级验收独立复审

审查者 W3，2026-09-21。已完成对 W2 最终 `8bdf1b87c3b18110c2551bdc89ff9c1f7d6728b3` 的独立只读复审，相对生产基线共 20 个独占验收文件。生产基线固定为 W1 `0311dfaf4255671a605a17aa3defc145df9621a6`，W2 以 `62b08a7` 保留历史接入，最终生产/V34 零差异；本地/远端一致、工作树干净。当前基础范围未发现整合阻断，原 seed GET 401 限制保留；这不是组合新跑或第五轮业务闭环验收。

## 方法与范围

只读核对 W2 独占 `foundationmigration` 测试、`inventory/FoundationLegacySeed.java` 桥接、交接记录和 ignored 原始日志/XML/JSON。没有运行 W2 测试、连接数据库、操作实例、修改其代码或改动旧恢复资料。生产源码独立复审另见 [W1 报告](W1_REVIEW.md)。

现场复核 JSON 采用按旧列名从升级后行投影的方式，逐项对比 JDBC 原 bytes 的 Base64/NULL；没有仅凭行数或摘要判断原值保持。文件比较覆盖相对路径、长度、SHA-256；旧 Flyway 历史单独比较。不读取或输出 `seed-manifest.json` 中的身份 token。

## 已独立核对的原始事实

- 唯一合成 V33 基线：92 张业务表，72 张非空，1,604 行，38 个私有文件；加 Flyway 共 93 表。
- `v33-before-upgrade.json`、`v34-after-migration.json` 与原快照的 92 张表全部原列逐值一致，38 文件一致。升级后新增 8 张业务表；原 V1–V33 历史逐项相同，新增 V34 记录 success=true。
- `_dirty2/_missing/_signature/_ddl/_lock` 五份复制快照也保留全部原列和 38 文件。复制器在全 FK/CHECK 启用状态下按引用顺序插入，允许的 nullable 循环字段在同一事务恢复，再逐值核对。
- 默认禁用与 URL/账号/目录/库/端口/datadir 护栏源码已读；唯一源库种子和负向副本必须分别受约束，不把测试开关视为可以写任意库。

## 独立预审修正与反例归因

1. 首次复制前修正 `DEFAULT_GENERATED` 普通列被当成生成列而跳过的问题；仅 `VIRTUAL GENERATED`/`STORED GENERATED` 由数据库重建。此项是静态发现，没有声称修前动态失败。
2. 旧 `receipt_text` 存储字节与服务 DTO/HTTP 结果分列；原定义按白名单核对，只有 K.order_id nullable 与 owner CHECK 的 SOURCE_RESERVED 扩展允许改变。
3. 异来源 set 反例先建合法 K、只换 set；非 TEMP scope 反例保持同仓 S/device/actor，有效避免其它 FK 先失败掩盖目标关系。
4. 脏关系和 DDL 负向需失败后核对原列/文件；脏关系以定向注入后的快照为比较基准，DDL 部分建表与失败历史保留，不假称整体回滚。
5. 元数据锁需绑定实际 holder 的 GRANTED 与不同连接 PENDING；超时后保留失败 V34、拒绝直接重试，并核对原值和首个永久 ALTER 未生效。
6. 种子已通过 `requireSource()` 在创建 DataSource 前精确限制唯一源库，避免在误选空负向副本时覆盖共享 `v33-original.json`/`seed-manifest.json`。新增 6 个环境反例确认完整合法负向环境可被一般护栏接受、但被种子护栏拒绝；最终 15 护栏单元全部通过，没有重跑种子。

## 失败与验证口径

- 初次夹具编译在 ObjectNode/JDBC 泛型处出现两条错误，未运行种子；之后 9 护栏和 1 种子 IT 通过。
- `_dirty` 第一次复制失败：暂置 nullable 引用触发 `ck_submission_scope_source`，MySQL 3819/HY000 被 Spring 包装为 UncategorizedSQLException；整批复制事务回滚，原副本留存。修正只对确有暂置引用的该错误延后行，换全新 `_dirty2` 后通过；不重用失败库。
- `upgrade-first` 整项 IT 失败：前段迁移、原值、schema、回填、重复迁移和签名前检已完成，末尾旧 accessToken 已自然超过 900 秒，GET 返回 401。原设备 EC 私钥未由旧工厂持久化，不修改会话、时间或设备 key 绕过鉴权。
- 后续 `SubmissionService.response` 原 Head → DTO 等值是独立服务层检查；新 fixture API 回归亦单列，均不能改写为旧 seed GET/重新鉴权已通过。
- 缺文件/坏签名由显式验收前检阻止，不是 Flyway SQL 具备文件或验签能力。合成 STOP_PROOF/set 仅检验 FK/CHECK，不能计作生产证据验证或 SOURCE_RESERVED 业务写完成。

## 最终原始材料核对

已读取逐批原始 XML：最终环境护栏 15、V33 首次成功种子 1、五份复制各 1、五类负向各 1、V34 独立保留核验 1、新关系 schema 8、七类旧 API 70 项通过，均 0 失败/错误/跳过。它们是不同阶段和目的，保持分列；`upgrade-first` 的 1 项失败仍保留。

七类 API 为 ContinuationApiIT 12、DispositionApiIT 11、PcExceptionLifecycleIT 5、RecallApiIT 11、SubmissionApiIT 12、TemporarySourceEvidenceIT 5、TemporarySubmissionIT 14，共 70。这批创建新合成 fixture，不证明旧 seed 已重新鉴权。原值完整比对证据保存于 API 回归前的 `v34-preservation-verified.json`。

元数据锁 JSON 记录连接 8924 持有 submission_scope 的 SHARED_READ/GRANTED，连接 8929 的 EXCLUSIVE/PENDING；实际超时 10,590ms。固定测试要求抛出 Flyway lock timeout、没有 mapping_version 永久列、保留失败 V34 且直接重复迁移受阻。独立读取 retained JSON 确认 `_dirty2/_ddl/_lock` 均有 V34 success=false、原列和 38 文件保留；脏关系按仅注入后的快照对比。

缺文件反例仅把一份文件改名为 retained 原件，原 bytes/长度/摘要仍在，数据库原列未改；坏签名仅改变 submission.receipt_text，文件未改。两者均没有 V34 历史，是前检阻断。DDL 部分失败保留新建的 source_evidence_object 与预置 failure_marker，不宣称无永久 DDL。

已读取 `before-shutdown.json`/`shutdown.json`：作者再次核对 19324/datadir/PID 19664 后，经显式 TCP 正常 shutdown，11:35 的记录确认进程与监听均不存在，文件保留。主库 101 表/V34 成功，dirty/missing/signature 各 V33，dirty2/lock 有失败 V34，ddl 保留部分 schema；未清理失败历史。该停机前状态包含后来 API fixture，不能替代先前冻结迁移快照。

本报告不把 W1 作者结果、W2 结果、W3 离线 233 项或未来组合测试相加；C0 原件未改未重跑。真实旧 seed 重新鉴权、HTTP/设备、台账执行器、TEMP 决定/放行及完整 R09 恢复仍不在本结论内。下一步由主调度固定三方 SHA 后派独立新环境整合；复审者没有自行启动该动作。
