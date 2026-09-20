# R09 恢复证据、分页与引用接口提案

日期：2026-09-20。与[控制契约](RECOVERY_CONTROL_CONTRACT.md)配套，状态为**设计交接，未实现正式API**。R09不修改主线OpenAPI、迁移或提交核心。

## 固定来源与版本边界

| 来源 | 已提交依据 | 恢复必须保留的字段/关系 |
| --- | --- | --- |
| FORMAL | `87e1893`及既有R05成功/取消/归档契约 | 原sourceOrderId、assignmentId、workRound、submissionId、冻结设备、正文摘要、结果版本、实际扣账标志及原签名；旧正文不补TEMP字段 |
| TEMP审计 | V31 `temporary_work_origin`、`pda_audit_event` | `(creatorDeviceId,clientOrderId)`永久绑定原authorizationId；原设备/operator/仓库/sourceRecoveryEpoch与资格一致；正式/TEMP共用原设备localSeq |
| TEMP提交 | 新提交`6d814f8`/V33 | `submission_scope`来源二选一；TEMP可无source_order_id，`canonical_order_id`可空；`submission.temporary_authorization_id`取代assignment，不构造假assignment；原稳定source、轮次、ID/控制头摘要不变 |
| TEMP正文前取消 | `6d814f8` | 签名ABORTED可无正文摘要/正式目标，保留REGISTERED来源和原资格，不制造空正文或正式订单；取消传输不释放实物占用 |
| TEMP完整上传 | `6d814f8` | 已正式化PDA_TEMP记录、唯一FH号/计数器和待审占用均属于同一恢复集；当前只是待独立确认，没有TEMP成功回执或最终出库可验 |
| 更正/多设备 | 待R07正式提交 | 原结果/调整ID、前驱、效果项、dispatchCycle、来源证明、净结果版本及引用链。未有模型不填0、不按汇总猜账务 |

本轮工具仅支持V21/V28/V32；V33明确拒绝为`UNSUPPORTED_SCHEMA`，即使表多为同名也不能套用旧检查。后续V33专项需固定新提交，在新专库覆盖无正文ABORT、整包/分块待审、唯一正式化目标/FH计数器及旧正式回归，再扩展检查器。当前V32结果不得宣传成V33恢复验收。

V33验收必须逐字段断言：正文前取消时`submission_scope.source_order_id`、`canonical_order_id`、`submission.assignment_id`及未收到正文的摘要保持空；`creator_device_id/client_order_id/temporary_authorization_id`绑定原资格，原`submissionId/workRound`和已签取消证明不变，不调用正式化分配FH号。正文后来到达仍返回原取消，不补造空正文、不创建订单、不释放实物占用。完整上传场景才核对唯一PDA_TEMP待审目标与FH计数器，而且待审不是成功/扣账/放行。本轮只读核对`6d814f8`的V33迁移与控制实现，没有执行这些新场景。

## 建议入口与鉴权

路径均为候选，主线需登记正式契约后实现。外部控制命令由独立运维通道提交，不开放公网任意上传控制文件。

| 候选操作 | 请求约束 | 响应/幂等语义 |
| --- | --- | --- |
| `GET /api/v1/recovery/status` | 受控读取；不写session、不携带内部文件路径 | 当前epoch、serviceMode、允许的恢复能力；不存在静默默认NORMAL |
| `POST /api/v1/recovery/sessions/{id}/inventories` | 设备持有效身份；当前传输epoch；稳定inventoryId、原设备、页号/前页摘要、预计页数/清单摘要 | 原页同摘要返回原ack；同ID异摘要409；最后校验完整页集前不标记清单完整 |
| `GET /api/v1/recovery/sessions/{id}/cases` | 仓库授权；绑定固定snapshotId/highWatermark的opaque cursor；limit初始100、上限200（提案） | items/nextCursor/complete；响应丢失重读原页，不更换快照混页 |
| `POST /api/v1/recovery/cases/{id}/evidence` | 独立证据权限、expectedCaseRevision、Idempotency-Key、证据类别及原摘要 | 先耐久保存内容后建引用，返回RECEIVED及证据ID；不直接返回“已重建/可放行” |
| `GET /api/v1/recovery/cases/{id}` | 原案件/仓库授权；可按原来源+轮次+submissionId查询 | 精确的已有证据、缺口、冲突和当前核对状态；404表示未掌握，不能换ID重提 |
| `POST /api/v1/recovery/cases/{id}/reconcile` | 专项能力、当前epoch/controlRevision、案件CAS、核对决定及证据清单摘要 | 幂等重建事务由主线负责；结果绑定原ID和事实版本，原签名不修改 |
| `POST /api/v1/recovery/sessions/{id}/release` | 主管/运维核对记录、固定设备及案件清单摘要、控制CAS、已发布基线 | 按控制方案分阶段释放，未知/离线影响未决拒绝；该端点当前不存在 |

管理员Web维持CSRF和仓库范围；PDA维持会话、设备签名和身份版本校验；设备停用/人员离职后的证据代传用R07专项授权和原来源证明，不放宽普通登录规则。每次读取文件引用也重新授权，不能凭摘要或objectId绕过仓库权限。限流与文件限额是技术建议，具体R07附件容量不由本提案代定。

## 证据对象与不变字段

`evidenceId`为服务端ID，稳定键建议`(recoverySessionId, sourceIdentity, workRound, submissionId, evidenceKind, originalObjectId)`，同时保留内容SHA-256；清单、正文、成功/取消证明、审计段、审批、调整链、实物材料分别引用。原设备/来源不能取当前上传者的身份替代，`uploaderId/deviceId`单列。

`sourceIdentity`使用FORMAL/TEMP判别结构；不把未正式化TEMP塞入正式orderId。`sourceRecoveryEpoch`取原正文/证明，当前`X-Server-Recovery-Epoch`仅用于传输；跨代次上传原始字节，禁止改正文后重新计算为“同一证据”。数字和UUID代次兼容按主线迁移；无效字段、未知版本、重复JSON键及冲突摘要明确拒绝。

内容先写独立临时对象并计算大小/摘要，fsync后原子放入不可变对象区，最后在同一案件事务建索引/引用。崩溃留下无引用对象可以标记待核对，不能清理被引用对象，也不能在对象未耐久时返回RECEIVED。数据库引用提交响应丢失时按同一幂等键查询。对象尚存不等于已有异地取回证明。

分块复用已确认的262144字节/32MiB上限作为正文提案，块序/块摘要/整体摘要固定；不同来源相同内容可物理去重，权限和保留引用必须独立。正文前ABORT无需上传伪零字节文件；SUCCESS必须有正文摘要与完整结果。签名算法固定、按keyId取历史验签公钥，先验签再比对仓库/来源/轮次/冻结设备；签名可信只证明原承诺，仍需明细与账务核对。

## 状态、缺口与分页

接收状态与案件结论分开：证据`RECEIVED/VERIFIED/CONFLICT/UNSUPPORTED_VERSION`；案件`NEED_PAYLOAD/NEED_AUDIT/NEED_RESULT_CHAIN/NEED_PHYSICAL_CHECK/CONFLICT/RECONCILED`。保留所有缺口集合而非只返回一个主状态；只有已验签、原事实完整、无冲突且所需实物核对完成才可RECONCILED。RECONCILED不是释放授权。

设备清单必须包含原设备序号高水位、未上传/未决批次和本机资料范围。服务端以冻结快照真实分页；游标绑定session、epoch、仓库、过滤条件和清单版本，含稳定排序键，不能OFFSET分页时拼接活动表。页内应用与cursor前进原子提交；旧epoch/条件/清单不符返回409 `RECOVERY_CURSOR_MISMATCH`，游标过期返回410 `RECOVERY_SNAPSHOT_EXPIRED`，重新取清单仍保留本地原证据与隔离。

审计按`(epoch,originDeviceId)`维护连续确认，FORMAL/TEMP不拆成各自序号流。上报已有范围与缺口，不以MAX(seq)当连续确认；旧事件ID、localSeq、采集序号和原摘要不重编。已归档的混合段应原签名验证后逐事件比对，现有V31工具已验证快照内这部分，不等于截点后缺口自动重建。

业务不存在、断网、超时、无正文、签名未知keyId、未联系设备都是待核对原因；不得推导“未出库”“未占用”或自动生成新submissionId。PC冻结正文/命令回执同样进清单，不伪造成PDA记录。

## 引用保护及订单编号恢复

同一一致截点备份原来源/资格/五个准备包/激活确认、审计/检查点/签名归档、提交控制/正文/分块/取消、正式目标/订单/结果、周期/占用/账务、幂等、Job、签名材料和`order_number_counter`。全库逻辑恢复与全文件清单核对是基础，不筛掉看似空闲的序号/防重/墓碑表。

V32快照检查按北京时间核对FH日期与流水，计数器必须存在且不低于原订单最大号；允许计数器高于已存订单最大值，不能用MAX(order_no)重建为更小计数。恢复到旧截点后，截点外已发出的FH号还可能被纸张/PDA/导出引用：**仅通过快照内计数器检查仍不能安全恢复发号**。必须从可信追加编号分配记录、连续binlog/归档及全部已恢复来源收集已用高水位；无法证明完整上限时保持发号关闭，不猜测加一或改旧号。新增库外分配证据/编号恢复策略由主线与R09在恢复接口检查点一起实现。

高水位重建以全局北京时间日期为单位，不能按仓库或仅按有最终结果的订单分组。保留“曾分配但当前订单不存在”的占号证据和原目标关联；对同一FH号映射不同目标、日志不连续、未联系设备或无法界定损失窗口形成明确缺口。只有可信证据闭合后，由受控事务把计数器推进到不小于备份值、所有已知分配值及已恢复原单值的最大值；这只是通过完整性门槛后的单调推进，不是凭MAX推断未知上限。来源/操作人/摘要和前后值留审计，旧号不重新编号。故障注入应证明证据缺失或并发发号时不能放开普通OrderNumbers调用。

R07给出调整/交接/故障代传链与保留引用；R08给出导出snapshot/resultVersion及文件派生引用。R09负责证明引用闭包和取回，不能由报表反推业务账务。恢复事实保持原结果并追加当前对账确认，原autoDeduct=false不补扣，合法取消永不复活；缺少后续调整链继续隔离。

云端原业务证据至少5年，引用/未决/有效防重可要求更久。PDA整单90天清理须等完整恢复、引用核对与可取回证明另批验收；`localDeletionAllowed=false`保持，监控PASS和本轮演练均不开放清理。

## 下一批验收材料

按固定提交分别测试：V33快照备份恢复、恢复到成功前、取消前、TEMP正式化前、R07更正前；原ID/正文/签名不变；并发同键同正文/异正文；多页丢响应/过期/旧代次；准备未确认但原审计仍保留；旧资格/停用设备代传边界；缺文件/历史keyId/调整链；跨仓库文件读取；FH截点外已发号；旧Job完成及释放CAS竞争。尚未具备R07模型的项保留待办，不伪造调整凭据。
