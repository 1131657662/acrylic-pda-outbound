# R07 首次同键激活并发失败停报（2026-10-01）

## 结论

输入及已核远端 `c1ef8707bb50ac8477d3bf56033bd5339c6447d6`，主树 `D:/acrylic-pda-outbound`、`codex/development`。依[方案](AUTONOMOUS_PLAN_2026-10-01_HANDOVER_FIRST_RACE.md)执行新隔离 MySQL attempt **811**，结果 **1 test / 1 failure / 0 errors / 0 skipped，13.739s，Gradle exit 1**。未自动重跑，未修产品，未降低断言。

两个首次 ACTIVATE 调用使用相同原键、相同冻结正文、相同主管，结果分别为 `HANDOVER_CONTEXT_CHANGED` 和 `OK`。违反[交接设计](../parallel/R07/HANDOVER_DESIGN.md) §7“同一案件重复激活返回同一个新资格及回执”。这是原键结果恢复的产品并发缺陷，**不是重复激活/双重记账已经发生**。

## 实证与边界

1. 前置使用既有 `HandoverRollbackFixture/Commands`：合成 FORMAL 来源、不同申请/审批身份、合成停止证明和非空保留占用。REQUEST/APPROVE/PREPARE 成功；本轮 ACTIVATE 回执数为0后才释放工作线程。
2. 独立连接先锁案件行。`performance_schema.data_locks` 记录事务4703/4704、线程162/163同时对**同一案件主键**请求 `X,REC_NOT_GAP`，两者状态 `WAITING`。这证明两个首次投递确实重叠，非先写好回执再重放。
3. 解锁后一个调用成功，一个返回上下文变化；原结果逐项写盘后才执行断言。JUnit明确失败于要求 `[OK, OK]` 的原回执恢复断言，非登录/编译/环境前置失败。
4. 失败前已通过：案件ACTIVATED，活动资格1、旧资格撤销1、激活回执1、审计1、outbox1（仍未发布）、非空保留占用1；scope/slot轮次各仅推进一次；`bale_commitment` typed全行与写前相同。
5. 因上述真实断言失败，后续同键顺序重放、Boot上下文重建恢复**本轮未执行**。807的历史恢复结果不冒充本轮新验收。新增用例只测试服务层与真实MySQL，不是HTTP、PDA、真实现场身份/停止证明、独立JVM或断电。

## 根因

`HandoverCommandService.execute` 在获取案件写锁之前用独立只读事务调用 `replay`。两次首次请求都读不到回执，然后在 `executeOnce` 的 `locks.kase` 等待同一案件。

先拿锁的请求推进案件并写原回执。另一个随后拿锁，从库内读到新案件状态/版本，立即执行 `Expected.validate` 并抛 `HANDOVER_CONTEXT_CHANGED`。锁内没有再次原键查询，且只捕获 `DuplicateKeyException`，没有到达唯一键冲突后的原回执恢复路径。

这与既有 M12 用例的前置不同：M12显式先创建回执后才并发重放。旧证据原文保留，不能据此宣称首次投递竞态通过。修正应另行验证锁内同键同文、同键异文、新键旧上下文、权限及原件完整性，不将所有上下文变化盲目重试或放行。本轮不实施修复。

## 离线验证、保护与证据

- 新增3份测试源码：`E2aHandoverFirstReplayIT`、`HandoverFirstReplayChecks`、`HandoverFirstReplayRace`；保留正确失败断言作为受环境门控的复现测试。未改既有测试、产品、迁移、默认开关。
- `full-offline-1/`：主/test编译成功，全服务端 **140 suites / 2893 tests / 0 failures / 0 errors / 1既有skip**。这是原有离线集通过，不包含需要独立库的811失败用例。脚本解析错误0，执行前源码摘要一致；收尾脚本新增后另做解析，不因此重跑已通过回归。
- 八项保护路径及全部服务端主代码/资源前后摘要相同。原两份非本会话PDA文档、A1.1文档、既有更正测试和其他保护草稿不编辑、不暂存。
- 真机22D2910042只通过 `adb devices -l` 枚举，起止逐字节一致；定向设备命令0、AVD启动0。未改系统MySQL，19330/19434已释放。
- D可用字节：批次开始 **31724154880**，离线后 **31668633600**，失败库保全收尾后 **31653187584**，均高于20GiB。
- 811认证停机，完整dump及292个文件ZIP逐项SHA回读一致后，仅清本批新datadir；ZIP SHA-256 `7B3EC22326976593057B64F2E3ABEA0EDD0B1689126E1F33B6C20AE6E0726215`。历史attempt与失败证据未删。

| 证据 | 本机落点 |
| --- | --- |
| 输入/远端、保护摘要、磁盘、真机枚举 | `.local/autonomous-20261001/handover-first-race/before/` |
| 编译、全套离线XML、脚本解析、源码摘要 | 同根 `full-offline-1/`、`pre-execution/` |
| 原命令、两个结果、成功响应、锁等待、16表typed快照 | `.local/e2a-cross-20260925-811/first-replay-*` |
| 失败XML/日志、dump、ZIP、关闭与设备证明 | `.local/e2a-cross-20260925-811/` |
| 逐文件保全核验、最终保护/磁盘 | `.local/autonomous-20261001/handover-first-race/closeout-811/` |
| 提交审查与实际推送回执 | 同根 `commit-review/` |

同一助手作者/自审/执行，不冒称独立审查。服务层有界反例保全不代表功能交付；Git上传源码/文档不代表忽略的本机证据已异地备份。

## 后续与停报清单

811原键竞态及[交接同步消息缺字段](R07_HANDOVER_SYNC_STATUS_GAP_2026-10-01.md)单列停报，后者只有静态证据；两者互不替代。[更正账务贡献](R07_CORRECTION_ACCOUNTING_GAP_2026-10-01.md)、分页/P08/抬头等旧缺陷继续保持；本批不修已验算法。

R06/R07整体目标仍active。继续选择不依赖这些缺陷的有界工作，下一候选为交接客户端所需查询/原回执恢复接口的源码与消费缺口，先写方案再实现；不得以新GET掩盖原命令竞态或坏outbox，不继续811重试。全开关默认false、localDeletionAllowed=false；不部署、不接原库/现网、不合main、不打标签/Release，不进入R08/R09/R10。
