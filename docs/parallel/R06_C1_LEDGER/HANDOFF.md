# C1 交付、实施切分与状态

日期：2026-09-21。作者 W3，独立树 `D:/acrylic-pda-worktrees/r06-c1-ledger`，分支 `codex/r06-c1-ledger`，基线 `c3009b691d9d2c0afc32dad6e8f2293d36742a3f`。本批白名单仅本目录四份 Markdown；C0已审16文件未改，生产/API/Flyway/Android未改。

文件：[设计](DESIGN.md)、[事务与证据](TRANSACTIONS.md)、[升级与验收](MIGRATION_ACCEPTANCE.md)、本交接。本轮仅方案交付，不创建纯合成模型或新测试，不能据此声称C1已实现。第三轮43单元/60IT/153离线/116路径是基线历史，本批不重跑也不相加。

## 1. 实施依赖和唯一写者

| 顺序 / 新批 | 可实施内容 | 必须先满足 / 唯一归属 |
| --- | --- | --- |
| L2 + E2设计复审 | 精确身份锁集合，来源/共同包/完整证据模型 | 各作者独立交付/复审；C1只消费，不改其树。L2真实C04结论不得从本设计推断 |
| C1a 共同协议及迁移准备 | 采纳或明确修订C1常量/字段；把C0覆盖迁入新增生产协议向量；固定E2 FK字段与非空升级 | 总调度指定一个协议/Flyway写者；查最新迁移再分配编号，C0提案不直接变生产协议 |
| C1b 台账与只读恢复 | 窄命令仓储、原字节/签名、状态版本、当前有权原键读取，内部合成命令故障注入 | L2已整合、C1a/E2必要schema可用；专属temporary命令模块作者，shared Idempotency/签名/认证若需变更先分配单一写者；此批不开产品决定 |
| M2b-2a 候选排除 | 完整候选上下文、独立决定、C10/C11 | C1b + 共同W发布屏障；权限/理由逐族核定，不附加到NORMAL_CONFIRM |
| M2b-2b LINK | 受控映射、双来源/槽、原键恢复及客户端安装 | E2来源/映射模型 + C1b + 全入口锁检查，C09/C12；共享SubmissionStore/正式订单入口只给一个执行者 |
| M2b-3 拒绝及实物处置 | 独立证据、保占用、完整清单、处置/续作 | E2共同包与证据实现 + C1b，nullable映射契约稳定即可，不强等全部LINK完成；C14/C15 |
| M2b-4a/4b 决定与最终成功 | 正常/短装分开记录、共享记账、成功签名、Android耐久验签/归档 | 上述决定失效/拒绝边界通过、正式建单对成功TEMP反向提示完成，C12/C13/C15与跨端真实样例；4a内部验证，4b才能开放产品入口 |

任何时间 Flyway、SubmissionStore/Accounting/Receipts、common Idempotency/IdentityWriteGuard、OpenAPI总索引、Room/Android公共签名解析各只有总调度明确的一名写者。独立作者提供具体接入补丁或需求后再分配；整合执行者处理冲突，主协调不写业务或处理冲突。

## 2. E2共同字段对照

| 共同事实 | C1约束 | E2接入时必须给出的证据 |
| --- | --- | --- |
| 稳定来源/原人/原资格 | source+scope+S/round+warehouse+authorization+freezeDevice+sourceEpoch按服务器记录交叉绑定 | 直接复用既有scope/T；C1同仓FK到submission_scope(warehouse_id,id)，不建source_owner实体 |
| canonical/mapping | 原回执可空，版本取实际持久值，旧事实标迁移观察 | 同仓FK、不可变mapping事件、非空V33基线与版本策略 |
| received/declared/local evidence | 三类hash/大小/对象类型分开；evidence_set_id/verification_id分别引用source_evidence_set/verification；原ABORT承诺不变 | LOCAL_FROZEN_PAYLOAD独立object，setId/hash、verificationId/版本/算法版本、逐包处置/保留清单hash与版本均进命令摘要 |
| 共同包归属 | 台账不另建TEMP占用，不以canonical当owner | 正式/TEMP同一包唯一裁决、claim/dispatch/保留/处置竞争证明 |
| 命令/证据关联 | receipt引用既有不可变证据/原映射，后续应用事件/处置/续作/新映射引用receipt | 同事务按既有父对象→request→receipt→新业务事件插入，不依赖延迟FK；证据接收与后续应用命令分离 |

已与E2作者对齐：复用scope、source_evidence_object/set/verification及共同K的bale_id主键；不另建source_owner_id，C1原evidence_manifest_id候选名收敛为evidence_set_id。双方只改各自目录。上述仍是待共同协议批审定的提案，不声称E2 schema已创建；实际迁移只能由总调度指定的唯一写者完成。

## 3. 本批实际验证与失败

当前已读AGENTS/Git管理/看板/进度、M2b/C0、IdempotencyRequests、JSON规范/签名、现有订单/设备/PC/提交回执、E1和V3/V31/V33源码，以及E2四份稿的来源/证据接点。编写前工作树干净，远端为唯一GitHub仓库；C1内容只写本目录。恢复后另按主调度明确委派执行纯管理Git保存，没有编辑其冻结正文或其它工作树业务。

本批四份文档严格UTF-8/NUL、28处本地链接、97行Markdown表格列数及敏感标记检查通过；四文件精确暂存范围与git diff --cached --check通过，C0及其它原跟踪文件未改。源码引用均在固定基线存在，未使用E2未合入相对文件链接。主调度初审与作者复核后修正原键回放/expected顺序、客户端commandId与服务端receipt主键，以及新业务事件与原回执双向非空FK风险。E2接点统一到既有scope和object/set/verification，处置/续作全部新增前置字段进入版本化命令摘要；只计文档检查，不计业务测试数量。

文档排查中曾使用Windows不支持的 `rg .../V*sql` 路径形式，改用目录加 `-g '*.sql'` 后完成定位；首次猜测的IdentityWriteLocks、submissions下TemporarySubmissionControls、E2的SOURCE_MODEL等路径不存在，随后按rg或E2索引找到真实文件。两次工具编排JS多余括号导致SyntaxError，修正后只读查询正常。它们是定位/工具调用失败，没有文件或业务状态改变，不算业务测试失败。执行中模型服务503曾中断，恢复原四稿后继续，不重建工作树。

管理Git子任务单列：主调度冻结的AGENTS/看板/进度/路线/剩余计划五文件经188本地链接、严格UTF-8/NUL、差异/暂存范围及新增敏感模式核对后，以 `d60bd047308d83ea7c1e450146152866720699a1` 提交并推送codex/development，远端一致；docs/out.xlsx保持未跟踪且SHA-256仍为C3BABA70BC900975503457C5468363DE91417F156F2441ED71A3B7BE5E8FB6C4。该提交不是C1业务整合，第三轮通过没有作为本次新跑。

本批没有运行Gradle、MySQL、HTTP、Android、浏览器或离线模型；无新数据库/进程/密钥/服务。原库、原恢复断点、AVD/M71、现网及旧审批阻断保持。原B/C/D任务未恢复。Flyway V33、契约0.29/116路径、Android code20/Room17不变。

独立复审前持续保留的未完成项：实际协议采纳及跨语言C1新域向量、E2最终FK、非空迁移、C1生产台账/查询/授权/签名原子、C03-C15中新增TEMP动作、真实HTTP丢响应/客户端耐久化、历史密钥环及R09完整恢复。正常直接确认和空理由目标保持，但尚未开放TEMP成功。

提交、推送和独立审查以实际Git及总调度记录为准；下一动作先独立审查这四份文档及E2接点，然后由总调度分配生产协议/迁移唯一写者。不合main、不部署、不因设计交付自动启动实现。
