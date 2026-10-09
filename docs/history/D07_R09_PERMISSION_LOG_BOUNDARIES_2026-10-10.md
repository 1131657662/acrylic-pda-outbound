# D07/R09 原权限截点与 logged 连续性

本子项输入树 D:/acrylic-pda-outbound、codex/development、HEAD bcee57b09a9c66005dd749b92292eaa4339bdf83。原未收正文同来源组合沿[已验输入](D07_R09_UNSUBMITTED_COMPOSITION_2026-10-10.md)复用；本批补可供实际恢复使用的原权限日志证据，不计历史权限授权、业务恢复或D07/R09整体通过。版本事实仅见看板生成事实块。

## 实现与退出标准

设计/作者自审为`.local/d01-d18-20261010/permission-continuity-design.md`、`permission-continuity-self-review.md`。明确原数据库身份、独立预期操作者/设备；自己的非事务连接、短暂FTWRL全局读锁内获取有效权限、状态/版本、ACTIVE仓、UTC微秒时间及SHOW MASTER STATUS。finally同连接UNLOCK并关闭，设置等待/查询上限，NORMAL/实例/schema/epoch不符拒绝；原本机binlog目录与实际`@@log_bin_basename`绑定，读取有限原prefix hash，路径/缺日志拒绝。

原两边界bytes及prefix使用显式独立hash输入；同名日志必须同时匹配begin原prefix和end全prefix，拒RESET/替换。直接解析MySQL原bytes的CRC32、位置、同server、格式、GTID微秒提交、BEGIN/ROW v2/table-map/XID闭合；单文件、原提交时间单调且在半开窗口，完整前缀定位精确事务边界。只接受明确业务表ROW，权限相关/未知表、DDL/statement、未知事件、缺片、跨旋转拒绝；即使撤权后恢复同值也拒绝。单文件≤64MiB、事件≤262144。

输出`historicalAuthorityEstablished=false`、普通写入与本地删除false；未接`RecoverySourcePermission`或closed DML，没有HTTP/Spring bean、自动采集、签发/签名能力、迁移或契约改变。binlog只说明被记录的事实；特权关闭日志、独立保管来源、实际截点恢复仍不能用caller布尔值或两快照补齐。

## 当次验证与失败保全

- 定向01：32/0/0/0、1XML、exit0/sourceUnchanged；补活动日志格式位后02：34/0/0/0；补原prefix绑定后04：36/0/0/0，均原log/XML/result保留，位于`.local/d01-d18-20261008/r09-permission-continuity-targeted-*`。
- 1165跨33树目录子串守卫拒绝，命中既有`.local/fence-16891165335755822077`；未建库，记录`permission-allocation-1165-refusal.json`，未放宽守卫。
- 新1166真实MySQL：1/1/0/0、exit1/sourceUnchanged。夹具ABORTED漏receipt触发原CHECK；原capture已执行，日志区间未过。dump/log/XML/datadir/binlog保留，不复用。
- 新1167：1/1/0/0、exit1/sourceUnchanged。解析器漏MySQL FDE的mutable BINLOG_IN_USE位，按MySQL格式归零该位后校CRC修正；官方`mysqlbinlog --verify-binlog-checksum`核同失败原件exit0，不是关CRC。失败库保留，不复用。
- 新1168：真实MySQL1/0/0/0、1XML、exit0/sourceUnchanged，九类权限表改后恢复同值仍拒绝，锁释放/嵌套事务拒绝/session禁关binlog。其权限snapshot不含TEMP创建权限，且当时未加原prefix绑定，只计相应logged状态，不作为完整TEMP权限或最终原prefix证据。Node01七字节整数API失败exit1原件保留；消费者修后02日志44输入、SQL4输入hash不变/exit0；官方11份区间全部exit0。
- 新1169：真实MySQL1/0/0/0、1XML、exit0/sourceUnchanged；实际创建角色/TEMP flag与有效权限、原prefix采集/绑定、九类全局权限变化还原反例、错误instance/日志目录、finally锁释放、嵌套事务拒绝、账号不能关闭session binlog通过。全表/文件快照独立核只有明确一行submission的status/receipt变化，其余表/文件不变。工具随后仅收拢测试Window参数，最终普通回归将验证编译/逻辑；不机械重跑一次性1169。
- Node1169-01日志44输入及SQL4输入hash不变、两脚本exit0；核原canonical/pin、CRC/位置/事务/半开提交微秒、逐SQL角色关系/状态/版本/仓及整库差异。官方mysqlbinlog11份原prefix校验exit0/输入不变。它们是独立工具消费者，不是独立人员/原现场身份。
- 回归03：2884/0/0/0、69XML、exit0/sourceUnchanged；命令虽含auth/devices/users模式，实际执行XML均recovery，本轮在最终prefix修正之前。回归05：2886/0/0/0、69XML、exit0/sourceUnchanged，普通recovery test/非IT，非完整server。其后自审去除可能撤销session的pda_session业务表允许项，新增明确拒绝测试，定向06为37/0/0/0、1XML、exit0/sourceUnchanged；未改其他恢复模块，不机械重跑一次性库。

每个MySQL实例均新分配，不接3306/13316，只有19330；1166/1167失败与1168/1169均dump重算一致、认证shutdown0/原PID退出/19330和19434空。runner/分配/证据在`.local/d01-d18-20261010/permission-continuity-mysql-<run>/`，Gradle在`.local/d01-d18-20261008/r09-permission-continuity-mysql-<run>/`，DB原件在`.local/e2a-cross-20260925-<run>/`。独立消费者在`permission-consumer-1169-01`、`permission-mysqlbinlog-1169-01`；1168失败消费者原件保留。

1169 dump SHA256：`6C1AE623AC00612D71C194520C61F00DA8D17C53EAA75C0741A541361DFA2141`。此前失败dump及1168 hash见各dump-result，不删除旧失败。原1164 binlog只读研究仍是旧fixture时间，不作为权限窗口原证据。

## Git、环境与接续

本聊天唯一写者、其他项目聊天notLoaded；22保护项与26接续dirty hash见`permission-continuity-resume-01`，继承文件不混提交。原FORMAL final/磁盘final已按既有hash核一致不重跑1150/1151/UI-06及1100–1102。范围Git回执见permission-continuity-precommit-02，最终完整dirty/环境快照见permission-continuity-postcommit-03，不重造已推提交。新增产物/TEMP在D盘，既有3306/13316/ADB不操作。

后续必须受约束原源保管及权限依据→全新隔离截点实际恢复→原ID/正文/序号/回执closed重建→D07主管围栏/接收激活/最终提交/尾SQL/重启。本批logged事实不允许旁路原权限授权，不能无限堆观察接口替代实际恢复。原FORMAL准备缺失、续期撤销/高轮/跨来源、原资料/旧BCD/526、失败/恢复限制与C01–C08继续单列。D06/D07整体、D08–D18与总闸门未完成，目标active。

生产默认/签名/七类决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布/部署/正式签名，不绕设备或审批阻断。
