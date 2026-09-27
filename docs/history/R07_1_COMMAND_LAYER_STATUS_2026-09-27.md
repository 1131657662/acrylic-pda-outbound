# R07-1 交接命令层：实现与未验状态归档（2026-09-27）

> **本页用于防止后续误以为 R07-1 已完成。** 命令层已实现且通过编译与全量单测，但**端到端断言一条都没跑**。

## 1. 已实现（仅编译 + 全量单测粒度）

| 范围 | 内容 | 提交 |
| --- | --- | --- |
| 并行命令内核 | `com.acrylic.outbound.handover`：`HandoverCommand`（解析/规范字节/摘要、自带身份与权限门 `order:handover`/`order:resume`、来源与案件 `FOR UPDATE` 锁、expected 锚定 scope 或 case+row_version）、`HandoverStore`（`api_idempotency_request` 原键预留 + 不可变 `handover_command_receipt`、案件乐观推进、证据/清单写入）、`HandoverReceiptCodec`（OfflineSigner 签名、字段与能力位校验、五项能力全 false）、`HandoverCommandService`（外层事务、同键同文回放原字节、同键异文 409、DuplicateKey 冲突后读回、单一审计事件） | `8beea8e` |
| REQUEST / APPROVE | REQUEST：FORMAL-only、`SUCCEEDED` 拒绝并指向更正、原结果四维映射、`UNIQUE(scope_id,open_flag)` 唯一开案；APPROVE：必须显式声明四维证据并逐项复核（证据完整/停止已证/实物一致/原结果已终结非 SUCCEEDED/高水位非空/原机停止证明已记录/逐包清单必填） | `8beea8e` |
| PREPARE / ACTIVATE | PREPARE：仅 APPROVED 可准备、指定目标设备/人员、清单非空、写准备票据 → `TARGET_PREPARED`；ACTIVATE：同事务撤销旧资格 → `nextEpoch` → **复用 `OfflineSigner` 与与 `AssignmentService` 同字段形状的 Claims**、以目标设备/人员为主体代签 → `AssignmentStore.insert`（`order_assignment` + `offline_authorization` + `outbound_order`）→ 推进 `order_submission_slot.work_round` 与 `submission_scope.work_round`（旧轮次做乐观条件）→ 保留占用零写操作（激活前后计数比对，变化即 `HANDOVER_RESERVATION_RELEASED`） | `ec58801` |
| 控制器 + 契约（辅助逻辑，已达该粒度完成） | `POST /api/v1/handover-commands`（受 `outbound.handover.commands.enabled` 门控，默认 404）；`handover-command-paths.yaml`；契约 **0.34.0 → 0.35.0**（paths 129 → 130） | `ec58801` |
| 缺陷修复 | ①`scopeFacts` 只认 TEMP 作用域 → FORMAL 来源一律 404；②原结果维度用了不存在的状态值（`DRAFT/PENDING/REVIEWING`），`REGISTERED`（从未冻结）会落到 `UNKNOWN` 而永远无法激活 | 本地提交 |

**实际验证**：`compileJava` 通过；服务端全量 `test` **718 项 / 0 失败 / 1 既有条件 skip / 0 error**。

## 2. 未验（不得声称完成）

- **14 条断言全部未跑**：8 条反例（未停止 / 未决未归零 / 缺审计 / 摘要冲突 / 包冲突 / 恢复代次非 NORMAL / 非主管审批 / `SUCCEEDED`）、3 条幂等（同键同文回放、同键异文 409、丢响应按原键恢复、404 不当成"未发生"）、3 条并发（并发批准唯一结果、重复激活唯一活动轮次、保留包切换所有者无释放窗口）、原子性两模式（`store.insert` 之后失败 / 轮次推进之后失败，断言三处写入+案件+轮次+回执全部回滚）。
- **outbox 未接线**：归属已查明为共享 `order_sync_outbox`（`V6:35`，写入方 `AssignmentEvents`/`SubmissionEvents`/`PcReceipts`/`RecallEvents`/`OrderPublicationStore`）；R07 应复用它（`entity_version` = 新 `assignment_epoch`、`event_type='HANDOVER_ACTIVATED'`），**尚未落代码**。
- **激活三处写入的原子性**：设计上是同一写事务（`TransactionTemplate` PROPAGATION_REQUIRED），**未在真实库上用故障注入验证**。
- Web 入口未做。

## 3. 阻塞点：FORMAL 来源夹具（已升级为"验证基础设施"类目）

累计 **5 轮未成**：

| attempt | 结果 |
| --- | --- |
| 138 | 编译期：守卫星树缺 R07 主代码、测试里 `a` 挂错对象 |
| 139 | 运行期：FORMAL 链路 MockMvc 400（当时看不到原因） |
| 140 | `400 INVALID_SUBMISSION：同一提交ID的来源不可更改` |
| 141 | `400 INVALID_SUBMISSION：冻结审计批次错误`（`SubmissionEvidence.java:79` 要求末条 FROZEN 审计 `detail` = 当前提交 id） |
| 142 | `409 CAPTURE_CONTENT_CONFLICT：原设备采集序号已用于不同事实` |
| 143 | 方向 2：自建 device/app_user/session/assignment → `401`（新人员缺 `pda:login`） |
| 144 | 方向 2：补 `PDA_OPERATOR` 角色 → `404`（认证已过，FORMAL 提交端点对该设备/资格绑定仍不成立） |

结论：**根因是复用 TEMP 夹具的设备侧采集序号与审计批次；方向 2 的自建设备/资格路径亦未成立**。这不是"多修几处"能解决的，需要：

- **独立时间盒**（长于普通子项）；
- **独立验收标准**（夹具自身可验证：产出 `ABORTED`（含合法回执正文）与 `REGISTERED`（从未冻结）两种可激活来源，绑定 `source_order_id = canonical_order_id`，带真实 `order_assignment`/`order_publication`/`inventory_snapshot`/`policy_version`，能种入 `STOP_PROOF` 与保留占用，且不改既有 TEMP/PDA 夹具与生产代码）；
- **现在不启动**：待 R07 其他部分或 R06 完成后专门排期。

## 4. 粒度约定（自本批起适用于 R07 后续子项）

- **核心逻辑**（命令链 / 记账 / 激活 / 回执 / 幂等 / 并发）→ **必须端到端**（真实 MySQL + 真实 HTTP）。
- **辅助逻辑**（UI / 查询 / 展示 / 文档 / 契约登记）→ **编译 + 全量单测 + 隔离模型迁移**。
- 每个子项开始时把边界写进范围。

## 5. 结构性事实（供排期参考，不在本批解决）

R06 端到端用了九轮（attempt 115–123 才跑通 TEMP 的 link→confirm），R07 FORMAL 夹具累计五轮未成。**端到端验证成本被系统性低估**——这不是能力问题，而是排期问题；在某个时点需要重新评估端到端粒度与"验证基础设施"是否单列时间盒。
