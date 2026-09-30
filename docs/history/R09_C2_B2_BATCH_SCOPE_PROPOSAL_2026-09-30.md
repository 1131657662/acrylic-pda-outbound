# B2 提案：R09 C2 单批范围与最小验收（PDA 认证入口，2026-09-30）

> **状态：提案，未获批。未获批不实施、不自动进入下一阶段。** 本页只是范围与验收定义，**不含**任何实现、开关开启或生产声明。
>
> 授权依据：用户 2026-09-29 裁定②——"先产出 **B2 单批范围＋最小验收提案（只读产出）**，获批后再接线"。登记见[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)「已裁定/授权的接续决定」B2 行。

## 1. B2 是什么（按既有入口评估，不重新定义顺序）

[R09 C2 应用入口接线评估](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md) §5 建议的顺序里：**B1＝HTTP/Spring Session 外层**，**B2＝PDA 认证入口**（"依赖 B1；专门验证 stateless 链、签名和认证写；不新增关闭态认证放行"）。

**本提案只定义 B2 这一独立批**，不把 B1 的 Session 外层工作并入本批；B1 若尚未完成，B2 的验收必须能与 B1 各自的证据分开记录（见 §5 前置）。

## 2. 现有事实（读源码得出，附路径）

| 事实 | 证据 |
| --- | --- |
| PDA 认证入口共 5 个端点：`POST /api/v1/pda/auth/challenges`、`/auth/login`、`/auth/refresh`、`/auth/logout`、`GET /devices/me` | `server/src/main/java/com/acrylic/outbound/pda/PdaAuthController.java:16-30` |
| 认证链是 **stateless Bearer**（`PdaSecurity` + `PdaBearerFilter`），与 Web 的 Session/CSRF 链**不同** | `pda/PdaSecurity.java`、`pda/PdaBearerFilter.java` |
| PDA 包内**当前没有任何** recovery/admission/lease 引用 | 对 `server/src/main/java/com/acrylic/outbound/pda/` 全目录 grep `Recovery\|Admission\|Lease` 只命中 `serverRecoveryEpoch` 业务字段（`AssignmentService.java:46`、`PdaOrdersController.java:29,38`、`PreparationAccess.java:44`），与恢复**准入**无关 |
| 已实现的接纳组件是连接层：`RecoveryAdmittedDataSource`（核对外部固定身份、关闭意图、借租约），opt-in 时成为 Primary | `recovery/RecoveryAdmittedDataSource.java`、`recovery/RecoveryAdmissionConfiguration.java` |
| 既有 PDA 认证测试资产（**本批必须回归，不得回退**） | `inventory/PdaAuthenticationIT.java`、`PdaCapabilitiesIT.java`、`PdaSigningUnavailableIT.java`、`PdaAndroidFixtureIT.java`、`PdaAuditIT.java` |
| 关闭态：`admission.enabled`、`effect-gate.enabled`、`cross-process-enabled`、`lease.enabled` 全部**默认 false** | 见[看板](../ORCHESTRATION_STATUS.md)当前区；`RecoveryEffectConfiguration.java:35-36` |

**结论性事实**：PDA 链**尚未接线**，因此本批是"接线＋接线后的最小验收"，不是"修 bug"。

## 3. 建议的 B2 单批范围

### 3.1 范围内（本批只做这些）

| # | 范围项 | 目标行为 |
| --- | --- | --- |
| S1 | 上述 **5 个 PDA 认证端点**的生命周期接纳 | 每个**将要写 DB** 的请求：在**打开连接之前**取得操作生命周期接纳；在**最后一次写完成之后**才释放 |
| S2 | **`PdaBearerFilter`** 的准入顺序 | 准入发生在鉴权/权限校验**之后、**业务写**之前**；不得为了接纳而放宽或跳过既有鉴权/权限/版本/签名校验 |
| S3 | 关闭态下的**行为等价性** | 开关全 false 时，PDA 链路的响应、状态码、审计与**接线前逐值等价**（回归证明，不新增放行） |
| S4 | 关闭竞争的最小覆盖 | 在 challenge 消费、token 轮换、logout 撤销三个写点分别卡住关闭意图，断言"新请求拒绝、已接纳者不提前释租" |
| S5 | 错误与异常路径 | 鉴权失败/权限不足/版本不符/签名错误的响应**不变**；异常路径**不得**提前释放租约、**不得**产生半条凭据 |

### 3.2 明确排除（本批**不做**，避免范围膨胀）

- **不**新增"关闭态专用认证能力"，**不**为登录成功开辟任何新作业资格；关闭态下的拒绝保持原样（评估原文："不新增关闭态认证放行"）。
- **不**改 `AuthorizationRenewalService` 的续期/授权发放语义（"借登录恢复发新作业资格"是明确禁区）。
- **不**改业务链：`PreparationController`、`PdaOrdersController`、`AssignmentService`、`PreparationService`、`AssignmentStore`、`PreparationStore`、`PdaSessions` 的**业务**语义。
- **不**触碰：Flyway 迁移、`BootstrapAdmin`、Worker/提醒 sweep、ImportFiles、SubmissionFiles、ExportFiles、SourceEvidence/ObjectFiles、ExportRendererProcess、temporary 命令链。
- **不**改七类生产决定 handler/POST/放行/证据写入/关闭能力；`localDeletionAllowed=false` 不变。
- **不**改默认开关值；**不**部署、**不**接原库/现网。

### 3.3 与 B1 的边界

- 若 B1（HTTP/Spring Session 外层）在同批之前完成：本批复用其"租约先于 Session 保存释放"的结论与证据，但**必须**用 PDA 的 stateless 链**重新**验收（Bearer 无 Session 尾保存，租约释放点不同）。
- 若 B1 未完成：本批**仍可**独立实施与验收，但**不得**把 PDA 通过写成"C2 认证外层已接"；两批证据分列。
- 两批**不得**争用同一写者/数据库/端口；单次只允许一个 Gradle 构建与一个数据库写者。

## 4. 最小验收（每条都要能判"过/不过"）

| # | 断言 | 判据（唯一期望结果） |
| --- | --- | --- |
| V1 | **先接纳、后连接** | 请求内可观察到"接纳取得"早于"连接借出"；用一个连接借出探针/事件序列证明，**不是**靠静态阅读 |
| V2 | **默认关闭等价** | 四开关全 false 时，5 个端点的响应体/状态码/审计行与接线前**逐值相等**（同一输入对比留证） |
| V3 | **既有校验不被绕过** | 错误签名、缺失/过期 token、越权设备、版本不符 ⇒ **仍拒绝**，拒绝码与接线前一致；**不得**出现"接纳成功即放行" |
| V4 | **关闭竞争（三个写点）** | 在 challenge 消费 / token 轮换 / logout 撤销分别注入关闭意图：新进入请求**拒绝**；已接纳者**不提前释租**且不留半条凭据 |
| V5 | **异常不早释** | 异常/超时/UNKNOWN 路径下租约**不**提前释放；请求结束后**无**连接泄漏（池计数前后证据） |
| V6 | **无半条凭据** | 任一路径失败后，`pda_session`/token/`device.last_seen`/审计**不出现**半写入（逐值核对） |
| V7 | **不含新作业资格** | 关闭态下登录/刷新**不**产生新作业资格；断言"关闭态拒绝"而非"静默成功" |
| V8 | **重启与代次** | 进程重启后旧 epoch/旧 token **不得**发布新凭据；`X-Server-Recovery-Epoch` 语义不变 |
| V9 | **启动互斥** | 迁移/Bootstrap 与 PDA 业务启动**互斥**；未接完的入口保持隔离，不参与局部通过结论 |
| V10 | **失败/跳过留证** | 全部失败轮与跳过轮证据保留、不删除不重写；skip 记"未尝试" |

**证据类型（必须分开记录，不得互相替代）**
- **真实 HTTP＋非空隔离 MySQL**：V1/V3/V4/V6/V7/V8（真服、真请求、真库）。
- **组件/定向测试**：V2（逐值等价对比）、V5（池计数）、V9（启动互斥）。
- **不得**用 MockMvc、服务 DTO、合成签名或合成身份替代上表中的真实 HTTP 项。

## 5. 前置条件（开工前必须成立）

1. **G0**：操作生命周期接纳的接线方案（嵌套顺序、收尾模型、启动互斥）已形成可审查方案——见[入口评估 §3](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)。B2 只实现 PDA 这一批，不借机重写事务机制。
2. **隔离环境**：专用非空库、隔离文件根、可信控制原件/原 ID/回执；隔离库仅回环、端口 19400–19999（E2a 实验舱 19330），**禁 3306**。
3. **开关默认值不变**：`admission.enabled`、`effect-gate.enabled`、`cross-process-enabled`、`lease.enabled` 全部 **false**；需要生产形态验证时按**单独授权逐项开**。
4. **单写者**：本批实施期间同一树/库/端口只有一个写者。

## 6. 预计成本与拆分建议

| 子项 | 内容 | 预计 |
| --- | --- | --- |
| B2-1 | PDA 认证入口接纳接线（S1/S2/S5） | 1 轮实现＋1 轮定向验证 |
| B2-2 | 默认关闭等价与既有校验回归（S3、V2/V3） | 1 轮 |
| B2-3 | 关闭竞争三点与异常路径（S4、V4/V5/V6/V7） | 1 轮 |
| B2-4 | 重启/代次/启动互斥（V8/V9） | 1 轮 |

建议：**先做 B2-1＋B2-2**（能否"零行为变化地接线"是本批的核心风险），过线后再做 B2-3/B2-4。

## 7. 边界声明

- 本提案**不构成**生产/部署/main/发布授权。
- 开关全默认 **false**；不部署、不接原库/现网、不合 main、不打标签/Release；`localDeletionAllowed=false`。
- 本批**不得**开放任何"关闭态认证放行"，**不得**借登录恢复发新作业资格。
- 窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS`、K1 现状与 `DEBT-R09-C2-LEASE-ANCHOR` 不因本批改变。

## 8. 请用户裁定的三个问题

1. 是否批准 §3 的**范围与排除项**（只接 5 个 PDA 认证端点＋Bearer 准入顺序，不动续期/业务链/文件/Worker/渲染/命令链）？
2. 是否同意按 §6 拆成 B2-1→B2-4，且**先做 B2-1＋B2-2**（零行为变化接线＋回归）再决定是否继续？
3. 是否确认 §5 的**前置**（G0 方案、隔离环境、开关全关、单写者）在开工前逐项核对并留证？
