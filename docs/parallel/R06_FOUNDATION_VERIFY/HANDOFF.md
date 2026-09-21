# 第五轮非空 V33 升级独立验收

执行者 W2 `/root/foundation_migration_verify`；独立树 `D:/acrylic-pda-worktrees/r06-foundation-verification`，短分支 `codex/r06-foundation-verification`，起点 `85cb7cef02fb8728599b6801a28824183305dab8`。生产协议、Flyway V34、共同占用及消费者只由 W1 编写。本目录不修改主调度五份文档、生产代码或旧共享 fixture。

## 当前检查点

2026-09-21：已在新 `127.0.0.1:19324` 实例建立唯一 V33 合成基线。首次编译在 `LegacySnapshot` 的 `ObjectNode.put` 与 JDBC 泛型推断处报两条编译错误，未运行测试、未产生种子；显式 String 局部变量修正后，`compileTestJava`、9 项环境护栏单元与 1 项种子 IT 通过，0 失败、错误、跳过。第二次只运行首次成功的种子，不是重置后重种。

本次快照为 93 表含 Flyway；92 张业务表逐列原值，其中 72 张非空、1,604 行，38 个私有文件，20 组场景。此处不声称 V34 已迁移或验收通过。原始 `seed-first.log`、`seed-second.log`、XML、`v33-original.json` 和 `seed-manifest.json` 位于 ignored `.local/evidence/`；后者含合成身份资料，不入 Git。

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

所有执行 fixture 默认关闭。开启时 URL 必须精确匹配 `127.0.0.1:19324`、已登记完整库名及参数；账号、私有目录和 flag 同时匹配。连接后在任何 Flyway/DML 前复核 `DATABASE()`、端口、`CURRENT_USER()` 与本树 `.local/mysql/data/`。种子只允许空库，拒绝再次写入原基线。

原值快照按原主键排序，对每列保存 JDBC 原 bytes 的 Base64；NULL 单列表示。原 `receipt_text` UTF-8 字节、JSON 的数据库规范值、BLOB、FH 与原 key 均属于逐列比较。私有文件记录相对路径、长度、SHA-256。GET 回执另按 DTO JSON 比较，不宣称原 HTTP 响应字节一致。

SHOW CREATE 原定义逐条核对；允许变化白名单仅 `bale_commitment.order_id` nullable 与 `ck_commitment_owner` 的 SOURCE_RESERVED 扩展，其余旧列类型/nullability、PK、唯一键、旧 FK/CHECK/索引必须保留。新增列、索引、表及回填另验。

缺文件/坏签名由显式验收前检验证，SQL/Flyway 本身不能核验私有文件。负向库仅使用本轮 V33 合成快照派生，命名空间为 `acrylic_outbound_foundation_verify_20260921_{dirty,missing,signature,ddl}`；各账号和私有目录独立。复制工具以全部 FK/CHECK 始终启用方式迁移空库至 V33、按依赖插入原事实，并在事务中暂存允许 nullable 的循环引用后恢复原值；不禁 FK、不删除历史、不自动 repair。原值/文件复核后才允许定向注入故障。

## 尚待执行

- 消费 W1 固定提交，独立审查 V34 与旧消费者；不复制在途源码。
- 正常非空升级、重复迁移、旧签名/私有文件/原字段/原约束核对及 DTO 回放。
- 独立脏关系、缺文件、损签名、DDL 部分失败副本；失败库和 Flyway 记录保留。
- 新 SOURCE_RESERVED 及 evidence FK 的跨仓、非 TEMP、异来源/null 绕过反例；旧路径受控冲突。

19324 初始化的第一次子进程随工具生命周期结束，之后以同一新 datadir 重启并留有 XA crash recovery 日志；当时没有业务种子。原 13316/19321/19322 及 R05/R06/R09 库、设备/AVD/M71、HTTP/代理/现网均未操作。未合 development/main、未部署；旧 B/C/D 不恢复。
