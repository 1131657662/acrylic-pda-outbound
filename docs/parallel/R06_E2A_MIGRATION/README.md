# R06 E2a V34 非空迁移验收准备

基线：`7a30523743a4317d2f9f007a6b99613b168de449`。本目录只记录 W2 的独立验收矩阵和环境护栏；不修改生产迁移、总契约或 API，也不声称已有 intake 接收通过。

## 当前结论

- 最高迁移是 V34；本批不分配或编写 V35。
- V34 已有 `source_evidence_object`、`source_evidence_set`、`source_evidence_item`、`source_evidence_verification`，以及 `SOURCE_RESERVED` 的 `bale_commitment` 分支，可作为复用基线。
- `source_evidence_object.kind` 已允许 `MANIFEST_PAGE`；`source_evidence_item.item_kind` 只有 `CAPTURE/SCAN/AUDIT/STOP/ARCHIVE`，没有 PAGE。分页根和顺序须由 manifest bytes 与窄写者校验，不能把 PAGE 当作现有数据库枚举。
- `temporary_command_receipt` 的 `command_type` 只允许七类业务命令，不能承载 E2a intake 原键/原回执；需要 W1 提供独立 envelope/idempotency schema 后再验。
- 当前 `SourceEvidenceService` 只有只读 GET，`pda:recover` 是现有独立证据能力；本批不新增 POST 或扩大权限。
- 本工作树只做迁移验收准备。W1 提供 V35 精确 SHA、接口卡和合成数据入口后，才可把矩阵中的“待 schema”改为实际结果；此前不得启动数据库、迁移或重型构建。

## 环境护栏

唯一允许的新环境：`127.0.0.1:19329`，数据库 `acrylic_outbound_e2a_migrate_20260921`，账号 `e2a_migrate_20260921@127.0.0.1`，datadir `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/mysql19329/data`，storage `.local/private-files`，签名 `.local/signing/{private.der,public.der}`。必须先确认端口、datadir、schema、账号和工作树路径精确匹配；默认禁用启动。不得复用旧库、旧 datadir、旧 seed 或旧 EC 私钥，不删除 marker/失败库，不自动 repair。

顺序固定为：先以 Flyway target 34 建立 V34 非空基线，再消费 W1 固定的 V35/后续迁移 SHA；禁止首建直接跳到未来迁移。实际启动、建库、重型 Gradle 或动态验收须由主协调排期，本代理在此之前只提交准备材料。

代码护栏 `E2aMigrationEnvironment.requireExact` 还会拒绝非精确工作树，并确认 datadir、私有文件和签名目录均解析在该工作树内；它不创建目录、不检查监听器、不连接数据库。

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
