# R06 E2a 迁移验收准备

**2026-09-24 签名归档及审计全覆盖增量**：`ARCHIVE/AUDIT_ARCHIVE` PAGE 原件核对稳定原键、服务器归档索引、RSA 签名、耐久原文件与库内各条原审计；STOP 只读门槛要求 PAGE 的 AUDIT 与归档区间无缺口、无重叠覆盖 1..签名审计截点。后端全套 412 项/0 失败/1 Windows 跳过；新隔离 audit attempt6 实库 1/1、身份核验后停机。历史轮换 keyring、捕获高水位/原机停止耐久、可信 set/VERIFY、完整 HTTP/Android 与生产权限仍未完成；证据及原始失败见 `ACCEPTANCE_STAGES.md` 顶部。

**2026-09-24 原件/授权/STOP 端点增量**：PAGE 内 `AUDIT_RECORDS` 原字节和库内同一设备事件绑定，库内 FORMAL/TEMP 审计逐条重查原授权及代次；只读 STOP/VERIFY 门槛要求签名审计截点的末条为同来源、同原人和同 ID 的 TEMP STOP。完整后端 397 项/0 失败/1 Windows 跳过；全新隔离 audit attempt4 与后续 endpoint attempt5 各一次 1/1，分别按当时版本单列。未覆盖签名归档、全页覆盖/高水位、历史 keyring、可信 set/VERIFY/HTTP/Android 或生产迁移权限；详见 `ACCEPTANCE_STAGES.md` 顶部。

**2026-09-24 全设备审计前缀增量**：STOP/VERIFY 只读门槛逐条重算原设备从 1 到签名 STOP `auditThroughSeq` 的 FORMAL/TEMP 共用审计序列及原文摘要，核旧 `submitted_event` 冲突，不依赖 MAX/count。后端 391 项/0 失败/1 跳过；新的隔离 audit attempt2 实库 1/1，上一 attempt1 只代表未加跨表检查的旧候选。页内原始 AUDIT/ARCHIVE、STOP 事件端点、逐包与 set/VERIFY 仍未闭环；详见 `ACCEPTANCE_STAGES.md`。

**2026-09-24 STOP 写入信任源修正**：内部 STOP 写入与 VERIFY 只读门槛现共用服务器选择的设备公钥来源，首次解析在只读事务、落库锁内重新解析，当前登记与选择结果不一致即拒绝。聚焦和完整后端 385 项/0 失败/1 跳过；首次聚焦的旧错误码断言失败已修并保留记录。这是作者单测，不是新的实库/HTTP/设备验收。

**2026-09-24 当前公钥来源增量**：STOP 只读门槛同时支持管理员直接登记的当前设备公钥和仍与登记一致的已完成配对公钥；聚焦与完整后端 384 项/0 失败/1 跳过。全新隔离 key attempt3 在关闭本地 binlog 的实例上完成合成直接登记/配对 JDBC 1/1；此前 key attempt2 因 binlog 触发器权限报 1419，失败库未重用。此结果不证明生产 binlog 迁移授权、历史换 key、真实配对审批、完整 VERIFY 或 R06 阶段完成，详见 `ACCEPTANCE_STAGES.md` 顶部。

**2026-09-24 后续作者验证**：新隔离 attempt4 在修正 V36 上用真实 raw/PAGE 文件及 JDBC 写者验证索引、重复原记录回滚与重启留存，鉴权停机；全量 server 单测 328 项/0 失败/1 跳过。证据见 `ACCEPTANCE_STAGES.md` 顶部。尚不是完整 PAGE 服务/HTTP/Android/并发验收，R06 未完成。

**2026-09-24 V36 动态断点**：新隔离 19330 attempt3 的非空 V34→V35→V36、OPEN 类型复合外键、唯一键、触发器、事务回滚、旧行/文件及重启持久检查已通过（1 项作者 IT）；attempt1 前置守卫失败和 attempt2 自审前旧 DDL 结果均保留，不算最终候选。证据和局限见 `ACCEPTANCE_STAGES.md` 顶部。PAGE 真实写者、HTTP、设备、并发及可信核验仍未验，生产入口不开放。

**2026-09-23 本轮接续**：已对 attempt8 保留的 XML、28 案、V34/V35 快照和 SHA 独立只读复核，不重跑 one-shot；原 `docs/sku.txt` 同字节只读本机副本补齐后，完整 server 单测 177/177 通过，日志及哈希见 `ACCEPTANCE_STAGES.md`。下段首次 1/177 失败作为历史保留。V35 仅是接收回执 schema，生产 OPEN/PAGE/STOP/VERIFY 窄写者与独立动态消费者仍待实现；不据此开启 POST、决定或放行。

**2026-09-23 最新作者动态结果**：用户明确将本项目开发和隔离数据库/运维职责交给当前助手；仅本树专用 19329 实例启动时开启 `log_bin_trust_function_creators`，binlog 保持开启，专用账号无 SUPER，固定 V35 不变。attempt7 完成迁移主体与 28 负向检查，但自动停机身份探针失败，后经精确身份核对由专用账号鉴权停机，整体仍为失败。修正探针等待后，全新 attempt8 V34→V35、28 负向、前后保留及鉴权停机 1 项 IT 通过，19329 无监听；35 项聚焦单测通过。`bootJar` 已构建，完整 server `test` 177 项中 1 项因原 `../docs/sku.txt` 缺失失败。独立消费者、真实 HTTP/设备/生产环境另验；不合 main、不部署。证据、SHA 与旧失败见 `ACCEPTANCE_STAGES.md`。

**2026-09-23 原预检增量（历史）**：只在隔离验收器加入 V34 seed 前的只读触发器前置核查，记录专用库/账号、MySQL 版本、binlog、`log_bin_trust_function_creators`、schema TRIGGER 与 global SUPER 结论到本次专用 `trigger-preflight.json`（CREATE_NEW）。当时 35 项 E2a 聚焦作者测试通过，尚未启动新库、分配 attempt7 或运行动态迁移/负向矩阵；此状态已由上方 attempt7/8 结果更新。兼容审查及本机决定见[触发器迁移授权决策卡](TRIGGER_DEPLOYMENT_DECISION.md)，旧证据完整保留。

**2026-09-23 最新接续**：attempt4/5 的合成 fixture 错误已定向修复，attempt6 完成非空 V34 seed 和 `before-v35.json`，但 V35 的 `CREATE TRIGGER` 在开启 binlog、专用账号无 SUPER 的本机隔离实例报 MySQL 1419；三次均经鉴权停机、19329 无监听，目录及证据保留。28 项最新聚焦测试通过，但 V35、负向矩阵、after 比对均未通过。下一步先审查生产兼容的 binlog/迁移授权方案，不能靠关闭 binlog 或扩大权限把本机通过冒充生产交付；以总调度看板顶部为当前状态，下面旧结论为历史。

**2026-09-23 09:01 当前断点**：原树 `568683c` 上的本机验收器修改未提交；三次精确 one-shot 环境分别失败于 PowerShell 模块、重复 ACL 与 Windows mysqld 子进程监听归属检查，均未进入 V34 seed/V35。attempt3 遗留的专用子进程已按 PID/datadir/端口核实后停止，属于非鉴权停机；原目录、marker、日志和 XML 证据按 `ACCEPTANCE_STAGES.md` 保留，不重用。下一步先修启动时的精确账号引导与子进程所有权/鉴权停机，再审查并分配新环境；下文原环境和「未运行」为当时准备状态，不覆盖此断点。

基线：`7a30523743a4317d2f9f007a6b99613b168de449`。本目录只记录 W2 的独立验收矩阵和环境护栏；不修改生产迁移、总契约或 API，也不声称已有 intake 接收通过。

## 当前结论

当前整合候选已包含生产协议/V35候选 `fa7981d`、foundation `319305f` 和 protocol `79cad441`，并保留各自历史；本树最新静态护栏修复为 `aea2c5d` 后续提交。尚未运行最终编译、数据库、Flyway、seed 或动态矩阵，重型槽归部署执行者；整合不计验证通过。

- V35 资源已进入整合候选，runner 会从实际 classpath 校验唯一资源及固定内容 SHA 后才允许启动。
- V34 已有 `source_evidence_object`、`source_evidence_set`、`source_evidence_item`、`source_evidence_verification`，以及 `SOURCE_RESERVED` 的 `bale_commitment` 分支，可作为复用基线。
- `source_evidence_object.kind` 已允许 `MANIFEST_PAGE`；`source_evidence_item.item_kind` 只有 `CAPTURE/SCAN/AUDIT/STOP/ARCHIVE`，没有 PAGE。分页根和顺序须由 manifest bytes 与窄写者校验，不能把 PAGE 当作现有数据库枚举。
- `temporary_command_receipt` 的 `command_type` 只允许七类业务命令，不能承载 E2a intake 原键/原回执；需要 W1 提供独立 envelope/idempotency schema 后再验。
- 当前 `SourceEvidenceService` 只有只读 GET，`pda:recover` 是现有独立证据能力；本批不新增 POST 或扩大权限。
- 本工作树只做迁移验收准备。W1 提供 V35 精确 SHA、接口卡和合成数据入口后，才可把矩阵中的“待 schema”改为实际结果；此前不得启动数据库、迁移或重型构建。当前 runner、fixture SQL 和负向 SQL 仅为惰性资源，完整环境护栏未通过时不会触碰数据源。

## 环境护栏

唯一允许的新环境：`127.0.0.1:19329`，数据库 `acrylic_outbound_e2a_migrate_20260921`，账号 `e2a_migrate_20260921@127.0.0.1`，datadir `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/mysql19329/data`，storage `.local/private-files`，签名 `.local/signing/{private.der,public.der}`。必须先确认端口、datadir、schema、账号和工作树路径精确匹配；默认禁用启动。不得复用旧库、旧 datadir、旧 seed 或旧 EC 私钥，不删除 marker/失败库，不自动 repair。

顺序固定为：先以 Flyway target 34 建立 V34 非空基线，再消费 W1 固定的 V35/后续迁移 SHA；禁止首建直接跳到未来迁移。实际启动、建库、重型 Gradle 或动态验收须由主协调排期，本代理在此之前只提交准备材料。

代码护栏 `E2aMigrationEnvironment.requireExact` 还会拒绝非精确工作树，并确认 datadir、私有文件和签名目录均解析在该工作树内；它不创建目录、不检查监听器、不连接数据库。

### Operation-specific reference matrix

The intake receipt references follow the contract lifecycle; they are not one
all-or-none group across every operation:

| Operation | object_id | evidence_set_id | evidence_verification_id | Expected parent state |
| --- | --- | --- | --- | --- |
| OPEN | NULL | NULL | NULL | intake envelope only |
| PAGE | NOT NULL | NULL | NULL | MANIFEST_PAGE object |
| STOP | NOT NULL | NULL | NULL | STOP_PROOF object; set is created only after matching |
| VERIFY | NULL | NOT NULL | NOT NULL | existing set plus appended verification |

The V35 acceptance must reject any other combination, while each non-null
reference still uses the full V34 scope/submission/round composite parent.
`MANIFEST_PAGE` versus `STOP_PROOF` kind, fixed route scope, and lifecycle
state remain narrow-writer checks because the receipt table cannot constrain a
referenced object's kind directly. A STOP can be recorded with missing pages
as `PARTIAL`; the writer must prevent set closure and complete VERIFY until
the page set is closed, rather than treating transport arrival order as a
database invariant.

## 验收矩阵

| ID | 目标 | 关键断言 | 证据要求 | 状态 |
| --- | --- | --- | --- | --- |
| M01 | V34 非空基线 | TEMP S A/B/C-ABORT、mapping/null、原 command receipt、SOURCE_RESERVED 代表行存在；迁移前旧表字段/bytes/files SHA 快照完整 | W1 V35 前置卡、V34 SQL 查询输出、快照 JSON/XML | 待 W1 SHA |
| M02 | 旧事实保留 | V34→未来迁移后原 S、原 payload/upload/null 语义、mapping_version、decision_version、原回执和私有文件字节不变 | before/after 快照逐字段 diff、文件 SHA 清单 | 待 W1 SHA |
| M03 | intake 唯一性 | `(principal,routeScope,originalKey)` 同文重读原 response bytes；异文 CONFLICT；失败事务后独立只读重查 | schema unique/FK 证据与事务日志 | 待 schema |
| M04 | intake 关系 | envelope 到 scope/S/round/object/set/verification 的复合 FK 正确；错误 FK/CHECK 回滚并报告失败，不吞为幂等冲突 | `SHOW CREATE TABLE`、负向 SQL 及回滚后计数 | 待 schema |
| M05 | 原证据对象 | object kind 白名单、32 MiB 上限、SHA/byte_count/file_key 一致；文件先耐久后引用，缺失/篡改 fail closed | DDL CHECK/FK + 文件/DB 故障日志 | 待 schema |
| M06 | manifest/page | `MANIFEST_PAGE` object 可复用；item_kind 不伪造 PAGE；manifest/page hash、顺序、缺页和终止标记由窄写者校验 | W1 接口卡、固定向量、分页负向结果 | 待 schema |
| M07 | verification | `PARTIAL/CONFLICT/UNVERIFIABLE/VERIFIED_COMPLETE_AT_STOP` 追加版本；原验证不可 UPDATE/DELETE；无 STOP/耐久/历史 key 不得 VERIFIED | 版本行快照、约束错误、状态响应原字节 | 待 schema |
| M08 | SOURCE_RESERVED | 无 order 的共同 K 只在可信 facts 与逐包 owner/cycle 一致时保守占用；不由客户端声明，不释放、不续扫、不确认 | B/K 关系查询、owner/cycle 负向日志 | 待 schema |
| M09 | 负向迁移 | NULL/FK/唯一/CHECK、未知 key、旧字段改写、文件缺失、重复页、错 manifest、原 bytes/hash 改变均有失败证据 | 每项独立失败日志/XML；不以历史结果代替 | 待 schema |
| M10 | 停止保留 | 19329 正常停止后保留日志、库、私有文件和签名；`remainingListeners=0`；不触碰 13316/19321–19328 | 主协调排期、停止日志、监听器快照 | 待排期 |

本批尚未执行测试、启库、迁移或 HTTP/设备验收；所有“待”状态必须在消费 W1 精确 SHA 后更新，不用历史证据冒充本次通过。
