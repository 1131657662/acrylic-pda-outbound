# A3非e2a剩余逐类目录（2026-09-28）

**接续状态：A3主体完成，21类剩余项已转为“发布前完整回归清单”。** 用户决定本阶段收手，不继续配置profile或补跑；见[收尾记录](A3_REGRESSION_CLOSURE_2026-09-28.md)。下文运行证据及环境限制保留，旧推进建议不再作为当次执行指令。

原清点输入83e633b：136个非e2a源文件中67类已有批次绿，69类待核；513–526跑14类（13绿/1红），55类未跑，8类R08按专用验收去重。用户授权继续其余47类后，531–556两批新增26类107项绿，**余21类待跑**；529/530无XML不计执行，545编排编码失败不改变其13项绿。原69类中累计执行40个不同类（39类全绿、526另挂疑似专项），21类待跑、8类历史去重。A3-526不阻断其他类；527/528同类重现不增类数。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

本目录不声称55类在所有历史入口从未运行，也不以缺XML推定失败；只记录当次批次证据与接续条件。结果与停报原因见[本批记录](A3_NON_E2A_WAVES_2026-09-28.md)。

授权恢复后的逐批结果见[接续记录](A3_POST526_REGRESSION_2026-09-28.md)；下表保留已跑类的attempt，未跑类仍逐项列出。

用户接受A3主体后，本轮完成[21类环境需求评估](A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)：值得分组继续，Ledger为作者2类/独立验证11类两套profile，Foundation为同实例的正向链/多负例schema；先建议2＋9类，余10类有阶段/原件前置。固定旧datadir导致配置超出只换库名，3–5轮为条件性估算。本轮只评估，21类仍未跑、无新attempt，实施交回用户决定。

后续授权11类同时限定“需要改守卫即停报”；配置前复核确认新隔离datadir需新增精确profile，已按边界停止。[11类逐项未执行/另10类前置](A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md)已记录：无新attempt，11类未计绿/红/skip，**总未跑仍21类**。旧seed标记不直接阻止选中9类，未删标记、未重建种子、未操作旧库；后续需决定测试环境适配或转其他方向。

## A3-526另挂待专项（不阻断其他类）

- BarcodeSyncConcurrencyIT：526红保留，527原样4/0/0/0、528原样4/1/0/0；43份锁快照和线程栈证明SKIP LOCKED查询等待pending主键锁。按用户决定为**疑似产品缺陷，尚未确证，待专项**，不占A3轮次；真实业务入口、执行计划和跳锁有效性待验。见[专项范围](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)。本轮排除此类及条码同步相关类。

## 普通非e2a及生成夹具（16类）

16类均已在本次两批执行通过；夹具绿不等于浏览器/Android消费。

| 类 | 本轮状态/输入 |
| --- | --- |
| DeviceBrowserFixtureIT | 通过，attempt 551，1/0/0/0 |
| OrderUiBrowserFixtureIT | 通过，attempt 552，1/0/0/0 |
| PairingApiIT | 通过，attempt 547，9/0/0/0 |
| PairingBrowserFixtureIT | 通过，attempt 553，1/0/0/0 |
| PairingConcurrencyIT | 通过，attempt 546，4/0/0/0 |
| PcSearchIT | 通过，attempt 548，2/0/0/0 |
| PdaAndroidFixtureIT | 通过，attempt 554，1/0/0/0 |
| TemporaryAuditContractFixtureIT | 通过，attempt 540，1/0/0/0 |
| TemporaryContractFixtureIT | 通过，attempt 541，1/0/0/0 |
| UserManagementBrowserFixtureIT | 通过，attempt 555，1/0/0/0 |
| UserManagementConcurrencyIT | 通过，attempt 543，3/0/0/0 |
| UserManagementRollbackIT | 通过，attempt 544，1/0/0/0 |
| UserPasswordPolicyIT | 通过，attempt 545，13/0/0/0；编排编码错误另列 |
| BackgroundJobLifecycleIT | 通过，attempt 542，10/0/0/0 |
| BackgroundJobWorkerIT | 通过，attempt 550，8/0/0/0 |
| OrderNumbersIT | 通过，attempt 549，5/0/0/0 |

## 身份锁与精确身份集合（9类）

9类均已在本次新隔离实例通过，端口/库名/账号守卫保持，35项全绿；不外推其他专项库环境。

| 类 | 本轮状态/输入 |
| --- | --- |
| IdentityLockAuthenticationIT | 通过，attempt 535，5/0/0/0 |
| IdentityLockBusinessIT | 通过，attempt 533，3/0/0/0 |
| IdentityLockBypassIT | 通过，attempt 534，3/0/0/0 |
| IdentityLockRevocationIT | 通过，attempt 532，9/0/0/0 |
| IdentityLockTemporaryIT | 通过，attempt 531，6/0/0/0 |
| IdentitySetContinuationIT | 通过，attempt 537，1/0/0/0 |
| IdentitySetFinalizationIT | 通过，attempt 536，2/0/0/0 |
| IdentitySetPcIT | 通过，attempt 538，4/0/0/0 |
| IdentitySetSessionIT | 通过，attempt 539，2/0/0/0 |

## Ledger作者/生产边界（2类）

LedgerRuntimeEnvironment要求固定旧树datadir、库/账号/签名/存储及显式条件；本轮未动该环境。

| 类 | 本轮状态/输入 |
| --- | --- |
| LedgerAuthorIT | 未跑；发布前完整回归 |
| LedgerProductionBoundaryIT | 未跑；发布前完整回归 |

## Foundation前代迁移（6类）

需要前代库、原件/负向案例与专用坐标；不能替换成已经迁移完成的普通空库回归。

| 类 | 本轮状态/输入 |
| --- | --- |
| FoundationCopyIT | 未跑；发布前完整回归 |
| FoundationNegativeIT | 未跑；发布前完整回归 |
| FoundationPreservationIT | 未跑；发布前完整回归 |
| FoundationSchemaIT | 未跑；发布前完整回归 |
| FoundationUpgradeIT | 未跑；发布前完整回归 |
| FoundationV33SeedIT | 未跑；发布前完整回归 |

## Ledger独立验证（11类）

LedgerVerifyEnvironment逐字守卫datadir/端口/数据库/账号/签名/存储；seed与retained类还要求前序事实。

| 类 | 本轮状态/输入 |
| --- | --- |
| LedgerConcurrencyIT | 未跑；发布前完整回归 |
| LedgerCorruptionIT | 未跑；发布前完整回归 |
| LedgerFaultIT | 未跑；发布前完整回归 |
| LedgerHistoricalReadIT | 未跑；发布前完整回归 |
| LedgerIdentityRaceIT | 未跑；发布前完整回归 |
| LedgerReadAccessIT | 未跑；发布前完整回归 |
| LedgerReplayIT | 未跑；发布前完整回归 |
| LedgerRetainedSeedIT | 未跑；发布前完整回归 |
| LedgerSecondLookupIT | 未跑；发布前完整回归 |
| LedgerSeedIT | 未跑；发布前完整回归 |
| LedgerSignedMetadataIT | 未跑；发布前完整回归 |

## 其他专项输入/组合（3类，1通过/2待跑）

见下表各自条件；未设置条件制造空跑。

| 类 | 本轮状态/输入 |
| --- | --- |
| OrderNumberMigrationIT | 未跑；发布前完整回归；独立前代空库/升级URL及前代迁移输入，测试自行迁移，不能沿用-NonE2a先迁完 |
| R09V33FixtureIT | 通过，attempt 556，10/0/0/0；新隔离datadir，精确19309/r09_ops_source/r09_source坐标与显式条件保持，不等于C2实施 |
| TemporaryAndroidSubmissionIT | 未跑；发布前完整回归；实际Android冻结正文必须对应同库原授权，不能拿旧body配新空库 |

## R08已有专用入口验收（8类，本次跳过不重复）

按用户授权从后续批次队列排除，已有专用入口历史证据，不重复D。这里的“跳过”是计划去重、实际未执行，**不是JUnit skip，不计本轮通过或新增验收**。属于批次聚合视野缺口，不标为功能未验。

| 类 | 本轮状态/输入 |
| --- | --- |
| R08AuthorizationRaceIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08CenterPaginationIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08ClockBoundaryIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08ExportFenceIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08ExportIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08PreviewSeedIT | 已有专用入口验收（种子生成）；本次跳过，不重复执行 |
| R08RetryIT | 已有专用入口验收；本次跳过，不重复执行 |
| R08SummaryIT | 已有专用入口验收；本次跳过，不重复执行 |

R08历史证据见[专用库方案与各轮验收](R08_DEDICATED_DB_PLAN_2026-09-27.md)、[B组确认](R08_B_GROUP_CONFIRMATION_2026-09-28.md)和[D交付确认](R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)；PreviewSeed是种子生成，不等于最终导出验收。

## 接续规则

按用户收尾决定，剩余21类仅保留为发布前完整回归清单，本阶段不配置profile或继续跑批。发布前重新核对源码与输入、环境写权和唯一attempt；本机旧manifest含已用号不得复用，557+实际执行前查重。526疑似缺陷另挂专项，8类R08保留专用历史证据；不以清单转移或skip计绿，不删除失败证据/marker，不弱化守卫或放宽超时。R09 C2只进入实施准备评估，生产接线待另行授权。
