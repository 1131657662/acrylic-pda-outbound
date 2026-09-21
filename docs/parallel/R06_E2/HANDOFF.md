# R06 E2 模型准备交接

日期：2026-09-21。第四轮 W2，独立工作树 `D:/acrylic-pda-worktrees/r06-e2-source-model`，分支 `codex/r06-e2-source-model`，起点 `c3009b691d9d2c0afc32dad6e8f2293d36742a3f`。只编辑 `docs/parallel/R06_E2/`；主目录未跟踪 `docs/out.xlsx` 未操作。本批没有业务代码、数据库、实际迁移、OpenAPI、签名或 Android 修改。

## 1. 本批交付

- [来源归属模型](SOURCE_OWNERSHIP_MODEL.md)：固定 T/C/S 原来源，扩展共同 K 唯一裁决，明确 nullable canonical、无订单占用、关系约束及旧消费者适配。
- [完整原机证据](ORIGINAL_DEVICE_EVIDENCE.md)：A/B/C 原事实、独立证据对象、捕获/全设备审计/停止/文件完整性、拒绝保占用、全部卸下结束、受控续作和 R07/R09 闭包。
- [非空迁移与验收](MIGRATION_AND_ACCEPTANCE.md)：V33 非空升级/失败保护、6 组迁移夹具、18 组来源/事务/并发/跨端待验场景。

正常 `temporary_order:confirm` 有权人员直接确认目标保持，不新增普通确认必填理由或强制主管审核。短装、实物处置和续作按原独立权限/职责规则；证据读取不授写权。C0 仅提案与离线向量，本方案不把其 reason 规则或签名 audience 直接变成生产门槛。

## 2. 关键决策与已识别风险

| 决策 | 后续执行约束 |
| --- | --- |
| 保留 `bale_commitment.bale_id` 唯一键 | 无第二套 TEMP 占用/可用库存；新证据多来源声称同包可记录冲突，但只能由共同 K 裁决 |
| 扩展 K 的来源 FK/新 owner，并严格限制 order_id=null 分支 | 不伪造订单/assignment；旧正式/PC 分支的 order 必须非空；加入新数据前旧消费者须全部兼容 |
| scope 保持永久归属，canonical 只是当前目标 | 无目标证据可接收；受控映射不能改原 payload/签名/原捕获；映射持久化与 C1 由同一共享迁移批定稿 |
| received、declared 和后来 localFrozenEvidence 分开 | ABORT 原回执原字节和 payloadPresent 不变，独立证据不调用旧接受链路 |
| 完整性绑定原机停止截点与完整集合 | 已知计数/审计 MAX/空列表不能证明完整或车辆为空；PARTIAL 可以保守占用，不能释放/续扫 |
| 处置与零包结束独立于出库成功 | 全卸下仍保留隔离 K/全部历史，旧 S 终态不变；不造 0 包 SUCCEEDED 或改账 |

静态风险：`RecoveryStock.reserve` 对 `BIN_TO_UUID(order_id)` 的结果直接 `.equals`，新 null order 行会触发空指针风险；须在同一共享实现批修复并用 E07/E08 真正复验。当前 V33 不存在此新数据分支，本批没有运行复现，不能写成已修复或现网故障。

## 3. 文件唯一负责人和依赖

| 范围 | 本轮负责人 / 后续归属 |
| --- | --- |
| access/auth/store、PC/续作原人集合和精确 U/H | L2 执行者 `/root/l2_identity_locks` 独占；E2 只读消费其合同，不能修改 |
| 本目录四份 E2 文档 | 本执行者 `/root/e2_source_model` 独占，交独立审查后冻结 |
| C1 命令原文/不可变原回执、expected/setHash/版本绑定 | C1 执行者独立设计；E2 只提供依赖字段，不新增台账表或生产 hash 域 |
| Flyway 新编号、K/证据/映射关系、共同库存与正式/PC/TEMP 消费者 | **本轮未派实现**；下一批由主调度明确指定一个共享执行者后才能改，不能由 E2/C1 各领一份同表迁移 |
| source evidence 新 API、权限/版本、原机停止/续作签名、总 OpenAPI | 同一协议负责人统一冻结，与上述共享实现者明确顺序；E1 现有读取三入口不改版本含义 |
| Android Room、冻结/停止/证据上传/激活与签名消费者 | 后续单一 Android 执行者，先等新协议/真实向量；code20/Room17 当前不变 |
| R07 代传/交接/成功更正；R09 fence/跨代次/工具适配 | 后续独立批；旧 B/C/D 任务继续暂停，不因本交接恢复旧会话 |
| 五份管理文件、进度/下一派工 | 主调度；E2 不写主目录管理文档、不合并/处理冲突 |

建议执行顺序：独立审查 E2/C1 → 消费已审 L2 的精确锁合同 → 单一共享执行者固定并交付 schema/共同 K 兼容和非空迁移 → 有界原机证据写与完整性验证 → C1 支撑的处置/续作 → Android 对应耐久消费者。候选排除/受控关联可按各自依赖推进；整个关联实现不是无 canonical 证据模型的前置。没有完整 R09 不阻止日常方案，但跨代次写继续关闭。

## 4. C1 接点核对

已只读核对本轮 `D:/acrylic-pda-worktrees/r06-c1-ledger/docs/parallel/R06_C1_LEDGER/` 的 DESIGN、TRANSACTIONS、HANDOFF 草稿；这是并行设计接点，不把未提交草稿当生产事实。双方最终文件仍须独立审查，以下固定 E2 提供的具体关系，避免实施者另猜一张 source owner 表。

| C1 消费点 | E2 明确提供 / 约束 |
| --- | --- |
| `source_owner_id` 建议名 | E2 不新增独立 source_owner 主体，永久归属为既有 `submission_scope.id`。后续可为 `(warehouse_id,id)` 增复合唯一键，让 receipt 的 warehouse/scope 复合 FK 真正约束同仓；不从 canonical 找来源 |
| `evidence_manifest_id` 建议名 | 对应本方案 `source_evidence_set.id`，明确引用原 `setHash` 和 `source_evidence_verification.id`。单个 LOCAL_FROZEN_PAYLOAD 是 object，不等于完整 set；关系不得只指文件摘要丢失来源 |
| PHYSICAL_DISPOSITION / REOPEN_WORK 的 expected | 除 C0 字段，还必须覆盖 evidenceSetId/setHash、verificationId、原停止对象、逐包处置或保留清单摘要；这些字段显式进入正式命令 schema/hash/原回执并补向量，不放到未摘要 header，不让默认 null 绕过前置 |
| FK/写入顺序 | 先有原 T/C/S、证据 object/set/verification；后续命令持其稳定引用，再写处置/续作及应用事件。处置引用命令实际 receiptId，receipt 只引用先存 set/verification；不建立互相先插入的非空 FK 环 |
| 无注册 S 的证据 | 本批日常 E2 聚焦已有 S 的 ABORT/REJECT/已冻结来源；无 S 不由 C1 造 S 接收，转以后明确派发的证据发现/R09 恢复范围，也不声称当前 E2 已覆盖 |

C1 的推荐生产 route/hash 域/audience 与 C0 提案不同，E2 不复制或发布这些常量；正式采纳仍需共同协议批的新跨语言向量。当前只冻结“哪些可信事实必须绑定”，不把本轮文档交付当协议已实现。

## 5. 实际核对与限制

已读取项目 AGENTS/Git 约定、最新看板/进度/路线、M2b 三份交接/锁图/证据合同、E1 指南/读取源码、C0 README/向量边界、V9/V10/V11/V13/V17/V33 相关迁移，以及 TEMP 接受/正式化、共同占用、正式恢复/续作/处置、PC 防重、条码投影与 Android TEMP 冻结事实。使用 brainstorming 梳理架构选项，结合 sql-schema-design/security-practices 核对关系/空值与授权边界；按已授权设计任务直接交付文档，没有重复要求确认已定业务规则。

已用严格 UTF-8 解码核对四份文档，38 处本地 Markdown 引用指向 31 个实际文件，0 缺失；源码涉及的关键方法、空值和迁移字段均按固定基线直接读取确认。另核对共同 K 主键、owner 分支、当前 E1 完整性 false、原 ABORT 不重写、普通确认空理由及新版本未实施边界。最终暂存限定本目录四个路径，并执行差异空白检查。

没有运行 Gradle、MySQL、C0 离线、HTTP、Web、设备或迁移测试，没有构建 APK/JAR；测试矩阵是待验清单。第三轮作者/整合通过记录只作依赖背景，不计为本次 E2 验证。中途模型服务返回 503 导致任务暂停，恢复后使用原树原文档续接；这不是产品/测试失败。

检索阶段曾尝试不存在的 `docs/parallel/R06_C0` 和 `docs/parallel/R06_C1` 路径，返回路径不存在；随后按实际目录 `R06_COMMAND_VECTORS` / `R06_C1_LEDGER` 完成核对。这是只读检索失误，没有测试失败、数据变更或迁移执行。原库/恢复断点、原文件/密钥、共享 MySQL、AVD/M71、现网和既有自动审批阻断均保持；未启动/停止服务，不重试已拦截 HTTP/代理。

本分支按授权仅提交推送自己的四份文档，实际 SHA 在对主调度回报中给出；不预填当前提交自身 SHA。未合 development/main、未打标签/发布/部署。完成后等待独立审查/下一派工，不自行进入生产实现。
