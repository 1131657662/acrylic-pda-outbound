# R06 M2b-2b 受控关联人工确认闸门验收（2026-09-26）

当前助手实现、自审并在隔离非空 MySQL 上验证；**无独立人员复审**。本批只交付受控关联的“人工确认闸门 + 已签名关联决定”，把来源指向正式单的**指针切换未实现**（见 §5），生产入口继续关闭。

## 1. 交付范围与依据

| 项 | 内容 |
| --- | --- |
| 政策依据 | [R06 已确认政策](../../BUSINESS_POLICY_R06.md) P-R06-03「临时单与正式单的关联**必须人工确认**，系统**不自动判定**」；[M2b 实施准备](../../R06_M2B_IMPLEMENTATION_PLAN.md) §2/§3 M2b-2b |
| 新增源码 | `server/.../temporary/command/TemporaryLinkOperation.java`、`server/.../temporary/review/TemporaryLinkTarget.java` |
| 修改源码 | `CommandOperation`（新增 `requiresCandidateContext`）、`CommandUnitOfWork`（放行 LINK 命令类型）、`CommandExpected`（LINK + 候选绑定必填）、`CommandErrors`、`CommandDecision`（LINK 决定形状）、`CommandBusinessIntegrity`（LINK 历史闭合）、`CommandDecisionEffect`（LINK 当前效力）、`TemporaryConfirmationOperation`、`TemporaryDecisionCandidates`（`reviewed`） |
| 测试/工具 | `server/src/test/.../inventory/TemporaryLinkChecks.java`、`.../e2averify/E2aTemporaryLinkIT.java`、`tools/e2a-cross-source-verify.ps1`（新增 `-Link`）、`tools/e2a-link-counterproof.ps1` |
| 迁移 | **无**。`temporary_command_receipt.command_type` 的既有 CHECK 已包含 `LINK`（V34），本批不分配 Flyway、不改表结构 |
| 生产入口 | 生产上下文仍无任何 `CommandOperation` bean（IT 断言 `getBeansOfType(CommandOperation.class)` 为空）；回执 `capabilities` 五项全 false；`localDeletionAllowed=false` |

## 2. 人工确认如何被强制

LINK 是冻结 v1 命令族中既有的 `commandType`（`temporary_order:link`），`expected.candidateContextSha256` 是既有字段，本批不新增命令字段、不改命令/回执 hash 域。

1. **没有显式人工动作就没有决定**：`CommandExpected.validate` 要求该类命令的 `expected.candidateContextSha256` 必须是非空文本，否则以 `TEMPORARY_MANUAL_CONFIRMATION_REQUIRED` 拒绝；LINK 也必须带非空 `reason`（命令形状既有规则）。系统内没有任何按车号/产品的自动关联或自动合并路径。
2. **人工核对与完整候选集合绑定**：该值等于 `TemporaryDecisionCandidates.snapshot(warehouse,submission,body).contextSha256`（M2b-1/M2b-4a 页面同一算法，本批未改其定义）。它覆盖仓库、来源上下文（产品/车号/正文摘要）与整组候选（条数、指纹=逐候选头信息含状态/revision/row_version/inventorySnapshotId/已有作业标志、检索区间、政策版本）。写入前在**锁内**重算并逐字比较，不一致即 `COMMAND_CONTEXT_CHANGED`。
3. **重复正式单必须先由人工核对**：完整候选集合为 0 条 → `TEMPORARY_LINK_TARGET_NOT_FOUND`；多于 1 条 → `TEMPORARY_LINK_DUPLICATES_UNRESOLVED`（逐单核对/排除台账属未实现的 M2b-2a，故此处**失败关闭**，不猜目标）。恰好 1 条时该条即人工核对的目标。
4. **锁内二次重验**：`TemporaryLinkTarget.lockAndVerify` 对目标正式单取 `FOR UPDATE` 后重读：必须同仓 `source='WEB'`、`status='READY'`、`current_revision/row_version` 与核对时一致、无 assignment/PC 作业/提交/result，库存版本一致，计划行与来源计划完全一致；否则分别 `TEMPORARY_LINK_TARGET_CHANGED / _HAS_WORK / _INVENTORY_DIFFERS / _PLAN_DIFFERS`。
5. **本批不切换指针**：决定以签名回执持久（`businessDecision.kind=LINK`，`submissionReceipt.applyState=NOT_APPLIED`），并声明 `LINK_POINTER_SWITCH_NOT_APPLIED`。因此决定登记不会改变 mapping/scope/slot/既有单状态，也不会释放占用。存在短装实绩时直接 `TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED`（短装审批与冲突分流属 M2b-4a 产品入口，未开放）。

## 3. 隔离非空 MySQL 验证（attempt110）

环境：`tools/e2a-cross-source-verify.ps1 -Run 110 -Link`，独立实例 127.0.0.1:19330、独立 datadir/schema/账号、非空 V34→V36 迁移 + 合成种子、真实签名与真实 HTTP，结束为认证 shutdown。

| 场景 | 期望 | 结果 |
| --- | --- | --- |
| 正向：人工核对后唯一候选 | 记录 `RECORDED`/LINK 决定；同键重放返回**原字节**；真实 HTTP 读回原回执且 `currentEffect.evaluation=LINK_DECISION_CURRENT`；mapping=1、canonical 不变、被审 PDA_TEMP 单仍 `REVIEW_REQUIRED`；decision_version=1；回执/幂等各 1 条；capabilities 全 false | 通过 |
| 跳过人工确认（`candidateContextSha256=null`） | `TEMPORARY_MANUAL_CONFIRMATION_REQUIRED`；无回执、无幂等行、decision_version=0、mapping/canonical/单状态不变 | 通过 |
| 伪造/过期候选绑定（改 1 位十六进制） | `COMMAND_CONTEXT_CHANGED`；同上不变 | 通过 |
| 重复正式单未逐单核对（2 条候选） | `TEMPORARY_LINK_DUPLICATES_UNRESOLVED`；同上不变 | 通过 |
| 无正式候选（0 条） | `TEMPORARY_LINK_TARGET_NOT_FOUND`；同上不变 | 通过 |
| 核对后目标被改动（row_version+1） | `COMMAND_CONTEXT_CHANGED`；同上不变 | 通过 |
| 核对后计划被原地改动（同 revision 改 `order_line.planned_count`，候选头指纹不变） | `TEMPORARY_LINK_TARGET_PLAN_DIFFERS`；同上不变 | 通过 |
| 短装实绩来源 | `TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED`；同上不变 | 通过 |

IT 结果：`tests=1 skipped=0 failures=0 errors=0`（`E2aTemporaryLinkIT`，耗时 18.689s）。

回归：同一批源码的服务端完整单元套件 `test --rerun-tasks` 为 `BUILD SUCCESSFUL`，102 个 XML/`tests=714 skipped=1 failures=0 errors=0`（唯一跳过为既有的 Windows 符号链接条件用例 `SourceEvidenceIntakeFilesTest.symbolicLinkTargetCannotBeAccepted`）；日志 `.local/link-full-unit-110.log`。本批未重跑 `integrationTest` 的其余 IT（含独立 Python 消费者 IT），见 §5。

证据：`.local/e2a-cross-20260925-110/`（`integration-result.xml` SHA256 `47279209018B5C07BF21A114263F32EFAC9BFC70698622CC9FDEA163A17EA0C4`、`integration.log` SHA256 `078028653500F5423FC16E259678ADF662B01DA32E44553F24D8BC0B189A4152`、`connected-identity.tsv`/`shutdown-identity.tsv` 同 SHA256 `C9248E0B30EAECFE4FDBF3162F70ECE0EE8C7AD14053A3D6AF31095470AAD7AA`、`test-started.marker`/`test-completed.marker`）。

当批源码 SHA256（这些是 attempt110 实际编译运行的输入）：

| 文件 | SHA256 |
| --- | --- |
| `temporary/command/TemporaryLinkOperation.java` | `0145408D2E08FD5AA823A21D4A7E442C8F2D57DB8C8839D6C327A334B5A460DA` |
| `temporary/review/TemporaryLinkTarget.java` | `5FCF5CB43CDC32F51F7A9ECBF3A9E2895B2AE2B11D80E36819D8292AF972BB39` |
| `temporary/review/TemporaryDecisionCandidates.java` | `F209FC6ED88D59FC95135C5180150233ED01E975D342852ADA843C36925F28F7` |
| `temporary/command/CommandExpected.java` | `DE26BB8CD70487A0ED001D30B05576D6C570154A8992FD0D75B96697A316780A` |
| `temporary/command/CommandDecision.java` | `578CBBDFF75A558BF3A3227C89BA2CBB072A57D629FBD96A0ABDA099FE9E6460` |
| `temporary/command/CommandBusinessIntegrity.java` | `00CF2A6484FCC2D6B6C5848ACE2934A204481131581EA7A232B399239EBAE498` |
| `temporary/command/CommandDecisionEffect.java` | `E64D9912B8551ABB9401028B6B1895B1D611F6B216272B4433EFC6DE7D69A1A1` |
| `temporary/command/CommandOperation.java` | `366989694840EA188068AE2D914D62863C0E033F04E7664D7392C8ACAB6AA3E1` |
| `temporary/command/CommandUnitOfWork.java` | `EA9E231077EF1070D99DA98BA383F53FE66F7861FA5C6B054767E119801D57D3` |
| `temporary/command/CommandErrors.java` | `E97EB080CEA08929A675815680F1F487B2701AB71691D1844A7F2023679B2361` |
| `temporary/command/TemporaryConfirmationOperation.java` | `6AD304C0F58D0F452DB1EC9C2B7DE4F24010453FB75FB7A469D484B44A0C9C38` |
| `inventory/TemporaryLinkChecks.java` | `22D871D3669A65E6FEC2B14928F6D64F966F475AFCE9194D0D13688B50A322FB` |
| `e2averify/E2aTemporaryLinkIT.java` | `E88DC72C30E8307777919CA5B4AF8610C82D9F626B90E2CA8314944A7EAC6798` |
| `tools/e2a-cross-source-verify.ps1` | `A6C751318077C1E2A6A6833E674240CA07C6FAC0305FE20F0302C3C18EC38926` |

## 4. 错误路径可伪证验证（attempt111）

按 AGENTS「不接受当前实现下通过」，用 `tools/e2a-link-counterproof.ps1` **按字节**只删除 `TemporaryLinkOperation` 中“锁内重算候选摘要与命令绑定逐字比较”这一条判断，其余判断保留，重跑同一 IT：

| 项 | 值 |
| --- | --- |
| 原始/还原后 `TemporaryLinkOperation.java` SHA256 | `0145408D2E08FD5AA823A21D4A7E442C8F2D57DB8C8839D6C327A334B5A460DA`（还原后复算一致） |
| 去守卫后 SHA256 | `4300ED322C55B61AD0AA2A23F0CE67032C80C57AC60CECA808ABEB21626C7463` |
| 去守卫后 IT | **失败**（`tests=1 failures=1`，断言 `forgedBinding`：期望 `COMMAND_CONTEXT_CHANGED`，实得 `COMMAND_RECEIPT_INTEGRITY_FAILURE`） |
| 证据 | `.local/link-counterproof-111/hashes.txt`、`.local/e2a-cross-20260925-111/integration-result.xml`、`.../integration.log` |

结论（如实记录，不夸大）：删除该守卫后**该拒绝用例真实失败**，说明错误路径验证不是同义反复；同时可见第二层独立保护——回执完整性会用已存 `businessDecision.candidates.contextSha256` 重算 `expected`，伪造绑定同样被 `COMMAND_RECEIPT_INTEGRITY_FAILURE` 拒绝，即该场景下**没有产生任何关联决定**。去守卫证据与失败 attempt 全部保留。`CommandExpected` 中“候选绑定必须为非空文本”的守卫（`TEMPORARY_MANUAL_CONFIRMATION_REQUIRED`）本批只以 attempt110 的拒绝用例验证，**未做第二次按字节伪证**，留待后续批次补。

## 5. 未实现 / 未验（不得据本批推断完成）

> **后续更新（2026-09-26）：本节第 1 条的“指针切换未实现”已由同日的[受控关联指针切换验收](R06_CONTROLLED_LINK_SWITCH_ACCEPTANCE_2026-09-26.md)取代**（映射 v+1/CONTROLLED_LINK、scope canonical、slot 改指、被取代单 `LINKED` 已在同一事务内实现并验收）。原文保留为当时边界。

- **指针切换未实现**：映射 v+1（含新 `origin` 常量，需新 Flyway 与检查约束调整）、`submission_scope.canonical_order_id/mapping_version`、`order_submission_slot` 改指与被取代 PDA_TEMP 单状态，均未写、未验；`applyState=NOT_APPLIED` 即此意。因此「受控关联」整体未完成，`TEMP` 生产入口与七类生产决定仍关闭。
- 排除/台账（M2b-2a）未实现：候选多于 1 条时无法逐单排除后关联，本批按失败关闭处理。
- 跨来源续作、拒绝/实物处置、短装与冲突分流、Android 映射安装与验签、正式单侧反向提示的**关联侧**接线均未在本批改动。
- 锁序：目标正式单在既有来源锁链之后加锁，尚未纳入经验证的完整锁图（[LOCK_GRAPH](../R06_M2B/LOCK_GRAPH.md) 的写阶段阻断仍然有效）；本批未做真实并发/死锁验证。
- 未运行：完整服务端套件之外的 R06_LEDGER_PROTOCOL 独立 Python 消费者 IT（`E2aTemporaryDecisionProtocolIT`）本批未重跑；Android 未改、未跑 APK/lint；无浏览器/设备/现场/长时/断电验证；无独立人员复审。

## 6. 本批环境适配与保留失败（本机为 Windows PowerShell 5.1）

| attempt | 结果 | 原因/修复 |
| --- | --- | --- |
| 101 | 失败 | 运行器用 `[Convert]::ToHexString`，PS 5.1 无此方法；改为逐字节 `x2` 十六进制 |
| 102 | 失败 | PS 5.1 `Start-Process -PassThru` 的 `ExitCode` 为空导致误判初始化失败；改为校验 datadir `ibdata1` 实际存在 |
| 103 | 失败 | `--tests` 运行号 103 超出 `E2aCrossSourceProfile` 的 `[1-9][0-9]?`；放宽为 `[1-9][0-9]{0,2}` |
| 104 | 失败 | PS 5.1 下 `*> log` 仍把原生 stderr 升级为终止错误；改为 `cmd.exe /c "... > log 2>&1"` 取 `$LASTEXITCODE` |
| 105 | 失败 | Spring 上下文创建 `lostCommitAdmittedDataSource` 失败（`RecoveryEntryLostCommitConfiguration` 的 `@ConditionalOnProperty` 在本机该次被求值为真）；后续同一入口多次成功，**按环境抖动保留、未定位根因** |
| 106 | 失败（对照） | 既有 `E2aTemporaryConfirmationIT` 在本机失败于 `TemporaryConfirmationRaceProbe.save`，与本批代码无关；说明本批 IT 与既有 IT 的环境条件不同，不能互相替代 |
| 107 | 失败 | 测试夹具建正式单时先插 `current_revision=1` 触发 `fk_order_current_revision`；改为先建单（NULL）→建 revision→回填 |
| 108/109 | 失败 | LINK 效力判定把“读回执的会话上下文差异”当成失效；改为仅业务原因可判为 `CONTEXT_CHANGED`（`ACTOR_CONTEXT_CHANGED:*` 描述读取会话）后 110 通过 |
| 110 | **通过** | 本批正式验收输入 |
| 111 | 失败（预期） | 去守卫伪证 |

各 attempt 目录、失败日志与数据库均保留，不清理、不回填历史结论。

## 7. 下一步（本批之后的有界子批）

1. M2b-2b-ii：以本批签名 LINK 决定为授权，同一事务落地映射 v+1 / scope / slot / 被取代单状态，并新增所需迁移与非空升级验证；同时把 `LINK` 的 `currentEffect` 从 `NOT_APPLIED` 改为可判「仍在生效/已被新映射取代」。
2. M2b-2a 候选排除台账，解除“多于 1 条候选即失败关闭”。
3. 完整锁图与真实并发（撤权/领取/发布竞争）验证后再关闭写阶段阻断。
