# A3回归阶段收尾（2026-09-28）

## 用户决定与整体结论

**A3主体完成，剩余21类转为“发布前完整回归清单”，正确执行时机是发布前完整回归，不在本阶段继续。** 停止专用环境配置、profile适配及补跑；转入R09 C2实施准备评估，生产实施仍须另行授权。

清点覆盖136个非e2a IT源文件，此前没有一份同批全量通过证据；其中67类已有批次绿记录，69类待核。后续对这69类实际覆盖40类：39类全绿，BarcodeSyncConcurrencyIT（526）保留疑似锁缺陷待专项；另21类专用环境/输入未跑，8类R08已有专用入口验收，按用户决定去重。这是**40/69待核类已获得当次运行证据**，不是136类全量通过，也不是从136类零覆盖起步。

V43–V48对已覆盖且通过的共享路径未见破坏，A3“把未知状态变成已知覆盖、降低共享面回归风险”的核心目标已达成并获用户接受。526的真实业务可达性、执行计划及SKIP LOCKED有效性仍待专项，不能被整体结论覆盖，亦未确证由上述迁移引入。

输入为主树D:/acrylic-pda-outbound、codex/development、5d977ea。本次只做收尾记录，无测试、无新attempt、无代码/数据库/配置变化。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 证据索引与计数边界

| 范围 | 实际证据 | 限制/提交 |
| --- | --- | --- |
| 原69类待核中的第一批 | 513–526共14类，13类全绿，526一项commit timeout | [逐类记录](A3_NON_E2A_WAVES_2026-09-28.md)，红证据保留 |
| 避开526后第一批 | 531–542共12类47项全绿 | ca985be，[接续记录](A3_POST526_REGRESSION_2026-09-28.md) |
| 避开526后第二批 | 543–556共14类60项全绿 | 321bbe0，同上；两批合计26类107项 |
| 526专项 | 527原样绿、528原样红，43份锁快照及线程栈 | [待专项范围](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)，3648629；重复取证不增覆盖类数 |
| 专用环境评估 | 逐类守卫与输入核对，未执行 | 806625f，[评估](A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md) |
| Ledger 11类推进 | 新隔离目录需新增精确profile，触发用户停报条件，11类全部未执行 | 5d977ea，[停报记录](A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md) |

529/530入口未执行、529重用编号被守卫拒绝、545编排编码错误及既有cleanup警告均保留；不将环境入口失败计为业务失败或JUnit skip。500签名误分类及修复前红色单测、526/528历史红也继续保留。证据主要为真实隔离MySQL＋Spring/MockMvc/真实事务，生成夹具不等于浏览器/Android实际消费，不外推外部HTTP或现场验收。

## 发布前完整回归清单

下列21类全部转入发布前，尚未执行；各类精确库/账号/端口、文件、种子与阶段要求见[逐类目录](A3_NON_E2A_REMAINING_2026-09-28.md)和环境评估。

| 组 | 类数 | 类名 | 发布前前置 |
| --- | --- | --- | --- |
| Ledger | 13 | LedgerAuthorIT、LedgerProductionBoundaryIT、LedgerConcurrencyIT、LedgerCorruptionIT、LedgerFaultIT、LedgerHistoricalReadIT、LedgerIdentityRaceIT、LedgerReadAccessIT、LedgerReplayIT、LedgerRetainedSeedIT、LedgerSecondLookupIT、LedgerSeedIT、LedgerSignedMetadataIT | 新精确测试环境profile/关联路径适配、独立库与证据；seed/retained另需空库或保留原件链，不删旧marker |
| Foundation | 6 | FoundationCopyIT、FoundationNegativeIT、FoundationPreservationIT、FoundationSchemaIT、FoundationUpgradeIT、FoundationV33SeedIT | 新精确profile；旧阶段非空基线、正向升级链与5类负例独立schema/文件，失败副本保留 |
| 编号迁移 | 1 | OrderNumberMigrationIT | 专用升级URL、准确前代阶段和无重复迁移输入；不预迁移到底 |
| Android原输入组合 | 1 | TemporaryAndroidSubmissionIT | 同库服务端准备→Android真实冻结→服务端消费，原授权/身份/库存/签名/正文一致 |

其中Ledger/Foundation受固定旧目录守卫约束，需要先决定新增精确profile适配；编号迁移与Android的主要前置是阶段/原输入，不误写成它们也有相同datadir守卫。清单转移不是通过、豁免或永久排除，发布前须重新核对源码、编号、环境与授权，再执行完整回归。

## 后续边界

本阶段不再配置profile、初始化或复用旧库、删除标记、放宽超时，也不重复R08已有专用验收。A3-526单列待专项；不能仅凭A3主体完成作发布决定。完整21类、526处理、跨端/外部HTTP、生产恢复准入和历史现场限制均未被本次收尾验收。

下一步仅评估[R09 C2既有范围](R09_C2_PROJECT_SCOPE_2026-09-28.md)：按应用入口逐项列生产面、前置、验收和分批建议，不实际改代码、不开放开关、不发布契约。实施交回用户决定。
