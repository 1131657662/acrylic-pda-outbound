# A3 Ledger 11类配置前置阻断（2026-09-28）

## 结论与停止点

**按用户“需要改守卫→停下报告”的当次边界，本轮在专用库配置前停止；11类均未执行。** 新隔离datadir不被已有精确守卫接受，而守卫允许的三个旧datadir均已存在且非空。要保留旧目录/库/失败证据并使用新隔离库，需要新增精确测试环境profile；这就是用户明确要求停报的范围，本轮未修改。

前轮[环境评估](A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)的1–2轮建议以“新隔离profile/构造器可小范围适配”为前提，并未证明零守卫改动可开跑。本轮授权同时明确“如果需要改守卫则停”，因此不能把“授权11类”解释成授权该适配。分类为环境前置阻断，不是测试失败或新产品缺陷。

输入：D:/acrylic-pda-outbound、codex/development、806625f，origin一致且开始时工作树干净；只有当前助手写文档，无在途IT，19326/19327/19328无监听，系统3306未动。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 已核对证据

| 环境 | 守卫要求 | 本机只读核对 | 对新隔离库的影响 |
| --- | --- | --- | --- |
| Ledger作者 | [LedgerRuntimeEnvironment](../../server/src/test/java/com/acrylic/outbound/temporary/command/LedgerRuntimeEnvironment.java)第12–44行；仅AUTHOR/COMBINATION两profile，精确URL/账号/storage/签名路径，连接后核datadir/端口/库/完整账号 | 作者D:/acrylic-pda-worktrees/r06-ledger-runtime/.local/mysql19326/data存在，顶层25项；组合D:/acrylic-pda-worktrees/sixth-wave-integration/.local/mysql19328/data存在，顶层24项 | 同端口/库名/账号配新的attempt datadir仍会被拒；19328并非空白备用位置 |
| Ledger独立验证 | [LedgerVerifyEnvironment](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerVerifyEnvironment.java)第10–38行；固定19327、库acrylic_outbound_ledger_verify_20260921_r2、账号ledger_verify_r2_20260921@127.0.0.1和旧ROOT下的datadir/storage/签名 | D:/acrylic-pda-worktrees/r06-ledger-verification/.local/mysql/data存在，顶层26项；evidence-r2/seed-started.json存在 | 不能只给R08构造器换端口/库名来保持新datadir隔离；ROOT还被原件/重签/设备键等测试helper引用 |

上述数量仅是文件系统顶层项数，不是数据库表数；没有打开数据库、查询schema阶段、读取密码/私钥或核验旧库是否可直接续跑。精确坐标和完整11类前置详见前轮评估，本轮仅确认足以触发停止条件的事实。

R08构造器使用每attempt新data/storage，并包含建库初始化流程；直接指向旧目录不能当成新隔离环境。本轮没有运行该构造器，也没有移动/替换旧datadir、设置目录链接或重新初始化旧库。若改为复用历史实例，需另行明确历史库写入、迁移和证据保护范围；本轮未选择这条路径，也不宣称它必然不可行。

## seed标记的准确影响

[LedgerSeedIT](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerSeedIT.java)第23–27行要求空schema且seed-started.json不存在；该类不在本次11类中。

选中的9类继承[LedgerVerifyFixture](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerVerifyFixture.java)，它检查环境身份并按用例自建来源，没有调用LedgerSeedIT的一次性marker检查。因此**不是“旧marker直接挡住9类”**，也不需要为这9类重建旧种子。marker仍原样保留；阻断点是新隔离环境无法满足固定旧目录守卫。

## 11类逐类结果

所有行均为NOT-EXECUTED（配置前置阻断），没有JUnit XML，不计通过/失败/错误/skip。未分配或消耗557及后续编号；后续实际运行前仍须重新查重，不能据此宣称这些号在所有树可用。

| 类 | attempt | 结果/分类 |
| --- | --- | --- |
| LedgerAuthorIT | —（未分配） | 未执行；作者环境精确datadir阻断新隔离构造 |
| LedgerProductionBoundaryIT | —（未分配） | 未执行；作者环境精确datadir阻断新隔离构造 |
| LedgerConcurrencyIT | —（未分配） | 未执行；ledgerverify精确datadir阻断新隔离构造 |
| LedgerCorruptionIT | —（未分配） | 未执行；同上 |
| LedgerFaultIT | —（未分配） | 未执行；同上 |
| LedgerHistoricalReadIT | —（未分配） | 未执行；同上 |
| LedgerIdentityRaceIT | —（未分配） | 未执行；同上 |
| LedgerReadAccessIT | —（未分配） | 未执行；同上 |
| LedgerReplayIT | —（未分配） | 未执行；同上 |
| LedgerSecondLookupIT | —（未分配） | 未执行；同上 |
| LedgerSignedMetadataIT | —（未分配） | 未执行；同上 |

专用库配置结果：未创建新实例、schema、账号、签名或storage；未启动/重置旧实例。批次未开始，只有本次停报文档提交，不存在已完成的3–5类测试批次。

## 另外10类的前置清单（本次范围外）

| 类 | 前置 |
| --- | --- |
| FoundationV33SeedIT | 新登记基线空schema、配套旧阶段生成方与签名/私有文件，生成原值快照；不得覆盖旧基线 |
| FoundationUpgradeIT | 同一非空前代基线、原值/文件/签名预检后单次迁移；不能预先迁到底 |
| FoundationPreservationIT | 升级后指定阶段、原快照和seed-manifest，核原值/文件/DTO与零重复迁移 |
| FoundationSchemaIT | 指定升级阶段的非空多仓样本，事务回滚隔离；不能用任意最新空库代替 |
| FoundationCopyIT | 已冻结原快照与文件，白名单负例空schema/独立账号/storage；复制前后逐值核对 |
| FoundationNegativeIT | 5种场景的独立副本，原件与签名预检、元数据锁观测权限；失败库保留、不repair/reset |
| OrderNumberMigrationIT | 独立升级URL、准确前代阶段/无重复迁移location，测试自迁并断言单次目标迁移 |
| TemporaryAndroidSubmissionIT | 同库服务端准备→Android主机真实冻结body→服务端消费；库内原授权、身份、库存与正文一致 |
| LedgerSeedIT | 精确新登记空schema且无对应新marker，旧阶段来源/设备键生成；旧marker不得删除 |
| LedgerRetainedSeedIT | retained-source指向同库保留submission，原私有文件/签名及设备EC键闭合，明确保留基线时点 |

## 交回决策与未验

本轮未改业务、测试、工具、契约、超时、生产配置或开关；未发现新产品bug，526仍为疑似缺陷待专项、未深入。A3主体历史26类107项绿保持；本次11类未执行，所以总未跑仍为21类，不能写成只剩10类。

若继续这11类，应另行决定是否允许仅测试基础设施新增精确profile及关联路径适配，并保持旧profile/旧库/marker原样；这不是生产业务改动，也不应采用关闭守卫、任意路径放行或改超时的方式。若继续坚持零守卫改动且不写历史库，本方案无法在该边界内配置，应保留21类专项清单并考虑R09 C2或其他方向。R09 C2实施未启动，等待用户决定。
