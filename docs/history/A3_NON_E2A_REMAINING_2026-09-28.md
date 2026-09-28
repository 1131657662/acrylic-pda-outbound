# A3非e2a剩余逐类目录（2026-09-28）

清点输入83e633b。按批次入口XML清点136个非e2a源文件，67类已有绿，69类待核；执行513–526共14类（13绿/1红），55类未跑。8类R08已有专用入口验收，本次不重复；**待跑47类已获授权继续，A3-526疑似缺陷另挂待专项、不阻断其他类**。527绿/528红为同类重现，不增覆盖类数；未修产品/测试。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

本目录不声称55类在所有历史入口从未运行，也不以缺XML推定失败；只记录当次批次证据与接续条件。结果与停报原因见[本批记录](A3_NON_E2A_WAVES_2026-09-28.md)。

## A3-526另挂待专项（不阻断其他类）

- BarcodeSyncConcurrencyIT：526红保留，527原样4/0/0/0、528原样4/1/0/0；43份锁快照和线程栈证明SKIP LOCKED查询等待pending主键锁。按用户决定为**疑似产品缺陷，尚未确证，待专项**，不占A3轮次；真实业务入口、执行计划和跳锁有效性待验。见[专项范围](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)。本轮排除此类及条码同步相关类。

## 普通非e2a及生成夹具（16类）

后续批次未启动；夹具通过也不等于浏览器/Android组合验收。

| 类 | 本轮状态/输入 |
| --- | --- |
| DeviceBrowserFixtureIT | 未跑 |
| OrderUiBrowserFixtureIT | 未跑 |
| PairingApiIT | 未跑 |
| PairingBrowserFixtureIT | 未跑 |
| PairingConcurrencyIT | 未跑 |
| PcSearchIT | 未跑 |
| PdaAndroidFixtureIT | 未跑 |
| TemporaryAuditContractFixtureIT | 未跑 |
| TemporaryContractFixtureIT | 未跑 |
| UserManagementBrowserFixtureIT | 未跑 |
| UserManagementConcurrencyIT | 未跑 |
| UserManagementRollbackIT | 未跑 |
| UserPasswordPolicyIT | 未跑 |
| BackgroundJobLifecycleIT | 未跑 |
| BackgroundJobWorkerIT | 未跑 |
| OrderNumbersIT | 未跑 |

## 身份锁与精确身份集合（9类）

继承IdentityLockFixture固定坐标/账号守卫；本机新隔离实例适配草稿未执行，不能称环境已验。

| 类 | 本轮状态/输入 |
| --- | --- |
| IdentityLockAuthenticationIT | 未跑 |
| IdentityLockBusinessIT | 未跑 |
| IdentityLockBypassIT | 未跑 |
| IdentityLockRevocationIT | 未跑 |
| IdentityLockTemporaryIT | 未跑 |
| IdentitySetContinuationIT | 未跑 |
| IdentitySetFinalizationIT | 未跑 |
| IdentitySetPcIT | 未跑 |
| IdentitySetSessionIT | 未跑 |

## Ledger作者/生产边界（2类）

LedgerRuntimeEnvironment要求固定旧树datadir、库/账号/签名/存储及显式条件；本轮未动该环境。

| 类 | 本轮状态/输入 |
| --- | --- |
| LedgerAuthorIT | 未跑 |
| LedgerProductionBoundaryIT | 未跑 |

## Foundation前代迁移（6类）

需要前代库、原件/负向案例与专用坐标；不能替换成已经迁移完成的普通空库回归。

| 类 | 本轮状态/输入 |
| --- | --- |
| FoundationCopyIT | 未跑 |
| FoundationNegativeIT | 未跑 |
| FoundationPreservationIT | 未跑 |
| FoundationSchemaIT | 未跑 |
| FoundationUpgradeIT | 未跑 |
| FoundationV33SeedIT | 未跑 |

## Ledger独立验证（11类）

LedgerVerifyEnvironment逐字守卫datadir/端口/数据库/账号/签名/存储；seed与retained类还要求前序事实。

| 类 | 本轮状态/输入 |
| --- | --- |
| LedgerConcurrencyIT | 未跑 |
| LedgerCorruptionIT | 未跑 |
| LedgerFaultIT | 未跑 |
| LedgerHistoricalReadIT | 未跑 |
| LedgerIdentityRaceIT | 未跑 |
| LedgerReadAccessIT | 未跑 |
| LedgerReplayIT | 未跑 |
| LedgerRetainedSeedIT | 未跑 |
| LedgerSecondLookupIT | 未跑 |
| LedgerSeedIT | 未跑 |
| LedgerSignedMetadataIT | 未跑 |

## 其他专项输入/组合（3类）

见下表各自条件；未设置条件制造空跑。

| 类 | 本轮状态/输入 |
| --- | --- |
| OrderNumberMigrationIT | 未跑；独立前代空库/升级URL及前代迁移输入，测试自行迁移，不能沿用-NonE2a先迁完 |
| R09V33FixtureIT | 未跑；专用r09_source坐标和显式条件，原阶段样本，不等于C2实施 |
| TemporaryAndroidSubmissionIT | 未跑；实际Android冻结正文必须对应同库原授权，不能拿旧body配新空库 |

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

用户已授权继续上列47类，526疑似缺陷另挂专项、不占回归轮次。按共享表风险和环境条件选取，每批12–15类、单类独立attempt；避开条码同步相关类，8类R08不进入manifest。新失败仅分类记录，不深入；遇条码同类或明确真bug停报。本轮2–3批，每批独立提交推送。旧第2/3批manifest未执行且含已用号不可直接复用；529+须查重。不得弱化守卫、打开生产开关、修改已验业务或以skip计绿。R09 C2保持只记录、未实施。
