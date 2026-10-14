# E06 SQL证据读取修复与实物补验

## 范围、默认和最小验收

固定主树 `D:/acrylic-pda-outbound`、开发分支；接手输入 `d5d4c9893d4f56840801b5f959431b37d39abd73`，实际远端一致。旧聊天释放回执沿实物WIP接续页；本聊天唯一写者，目标恢复active。完整继承快照见 `.local/goal-20261013/e06-audit-resume-20261014-sql03`；68个旧脏路径中仅AGENTS追加本机memory上下文，与继承业务字节无关，原件不覆盖。SQL工具/两份诊断来自后续聊天 `01a13800-6f5e-70d1-bff4-3d808873af68`，本轮核其idle/末轮interrupted且无在途进程后接续；旧release不单独作为其释放依据。写权复核为 `ownership-sql03-recheck.json`。

先采用已有MySQL CLI和新证据参数，不增加数据库包；在新副本又明确捕获空列后停止CLI全表重跑，改用已安装JDBC。每条保存表名、配置路径/数据库/参数、原stdout/stderr、退出码、耗时/字节/hash；启动错误及超时也保留证据后明确抛出，不补造退出码。凭据只通过被忽略的配置文件供给。新目录拒绝覆盖。JDBC只读单连接/可重复读事务、实际数据库/端口/datadir核对，字段仍采用同一HEX算法及独立COUNT；查询结果标为JDBC getBytes TSV，JVM stdout/stderr/exit单列，不能冒充CLI输出。

L1：`tools/sql_evidence.py`、`tools/exact_database_facts.py`、`tools/JdbcDatabaseCapture.java`、`tools/jdbc_database_facts.py`及定向测试 → 仅验收工具 → 必跑证据保全/空输出/独立COUNT/NULL与二进制/守恒反例单测、实际CLI/JDBC消费和错误库身份拒绝 → 产品源码/迁移/协议未受影响，复用实物备份 `4fead0c7abc1ef77e6ecff70dd5df6e44b98e5d5` 所绑定unit-final01/HTTP06/HTTP03/final02/browser06证据，精确输入复核沿WIP接续页与scope-hashes/runtime-binding。Chrome/Android/其他旧链未受影响，复用上述证据和理由，不新增其通过。本次是限定实物补验单元，D整体L2及D18最终L3未跑，不计D收口。

补验：只分配核未占用的新隔离副本，原1514保全dump/私有文件及原固定钥；迁移前后都用同一columns/hex/COUNT算法。原非空表仅允许Flyway/Session变化，新增实物表必须空；default-off正确分派并证明两写入口拒绝、三表COUNT为零。随后同一输入实际原包→主管核原/上下文→实际附件→逐包登记→原键/文件回读→原包/上下文不变→全部业务表守恒。合成主管/实物明确标注，不称原物理PDA/现场。实物收口后再人工关联/跨机冲突；非空补验不替代E06所有迁移冻结后的L2及D18的L3。

## 新诊断依据及失败继承

- 1522/1523原失败日志、库/datadir/dump/binlog保持；同潜在SQL读取根因计数2不清零，旧错误没有原stdout，不能事后证明具体空输出根因。
- 后续旧聊天已有 `.local/goal-20261014/e06-sql-diagnostic-01/0130.json`、原stderr：附件表CLI返回exit=1，`ERROR 2020 ... max_allowed_packet`，原stdout空；这是读取器容量的明确证据，不是业务漂移。前129条原输出仍保留。
- `e06-sql-diagnostic-02`采用DEVNULL和CLI足够包容量，全部172表/517次查询成功，62非空；warehouse独立COUNT为2，逐列编码/字段数通过。该结果只证明新的只读消费者能读取继承1519输入；不证明1522/1523已验，更不复种旧失败库。stdin变化与旧空输出的因果仍不夸大。
- 依据新证据继续补验，避免无假设原样换库。若同根因无新证据达到3次或主动诊断60分钟不收敛，按总计划保全并暂缓直接依赖。
- 新1524的 `facts-baseline-1/sql/0257` 明确记录 `SHOW COLUMNS FROM pc_submission_entry`、exit=0及stdout/stderr均零字节，DEVNULL和大包参数仍不足以保证读取。潜在根因连续总数3保留，CLI消费者不计通过、不继续机械重跑；此前完整85表与同副本JDBC行数/列名/逐值HEX摘要完全一致，`cli-jdbc-partial-parity.json`。这仍不能反向判1522/1523业务守恒或空输出原因。
- JDBC同一1524输入实际全表完成，baseline与后续统一算法、独立COUNT/字段数/编码校验。baseline启动时未单独保全Java源码字节，后续增加前置身份拒绝仅影响目标核对而未改编码；迁移/default-off/组合/边界使用冻结编码并保存实际输出，后续process-source/源码hash登记。`jdbc-wrong-identity-01`真实预期端口错误在inventory前拒绝。

## 执行回执

`e06-sql-unit01.log`：11项实际执行、退出零；JDBC增加后 `e06-sql-unit02.log` 15项实际执行、退出零。覆盖原始二进制、非零/超时/启动失败保全、拒绝覆盖、空列/空行与独立COUNT、坏hex、列变更及新增表拒绝、JDBC查询/表证据错配、失败JVM。未改产品行为、未跑完整服务/IT/构建或UI。

新副本实物补验及保全实际见[验收页](GOAL_E06_PHYSICAL_ACCEPTANCE_2026-10-14.md)。版本事实只见看板生成块。总目标未完成；生产/原库/main/发布、七类默认关闭、TEMP归档及localDeletionAllowed=false保持。
