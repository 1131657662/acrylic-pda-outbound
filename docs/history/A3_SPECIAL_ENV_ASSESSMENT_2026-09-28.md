# A3剩余21类专用环境评估（2026-09-28）

> 后续执行状态：用户授权11类但规定“需要改守卫即停报”，已在配置前按此边界停止，未执行测试。见[配置前置阻断](A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md)。下文保留原评估及其“允许新精确profile适配”的前提，不视为已建成环境。

## 结论与本轮边界

**结论：值得按组继续，不需要21套独立构造方案；但不能把“一库多类”直接换算成21类在3–5轮必然完成。** 建议先做共享运行条件最清楚的11类（Ledger作者2类＋独立验证中的9类），其余10类按迁移阶段/原件链另排。3–5轮可作为条件性计划，不能作为已验证工期。

本轮输入为主树D:/acrylic-pda-outbound、codex/development、321bbe0，与origin一致；按用户“1轮、只评估”执行。只读取21类、环境守卫、关联fixture/构造器及历史证据，检查本机路径存在性和监听；没有连接数据库、启动服务、建库、改测试或业务代码，也没有分配新attempt。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

配置范围已证实大于直接套用R08构造器：三组守卫绑定旧树datadir/证据路径，旧目录已存在；一次性seed与历史迁移测试还要求旧schema阶段。按“比预期大即停报”的边界，本轮停在本评估，未尝试初始化旧目录、删除marker、覆盖原件或绕过守卫。

## 共用范围

这里区分MySQL实例、schema和构造方案。每类仍须有独立attempt/XML；一个构造方案可以反复供应新隔离实例，也可以管理明确的前后阶段，不能因共用方案而默许共用受污染的数据。

| 组 | 类数 | 共用判断 | 不能直接合并的原因 |
| --- | --- | --- | --- |
| Ledger作者侧 | 2 | 共用一套作者环境配置；守卫提供作者/组合两种可选profile，二选一即可 | 与ledgerverify的端口、库、账号、datadir、签名目录均不同；两个Spring上下文需分别运行，生产边界类不能导入合成CommandOperation |
| Ledger独立验证 | 11 | 共用一套ledgerverify配置；9类每用例自建来源，另2类是空库seed与保留原件接续 | seed必须空schema且无marker；retained必须同一来源/设备私钥，不能与任意新库互换，也不能在日常回归之后补跑seed |
| Foundation | 6 | 共用一个实例/一套分阶段构造方案；正向4类沿同一个基线schema推进，复制/负例2类复用派生流程 | 完整负例需5种场景各自的schema、账号、文件副本；不是全部6类顺序跑一个schema |
| 编号迁移 | 1 | 一套小型空库构造，测试自行迁移 | 要停在编号迁移前的阶段，普通先迁到底的构造不适用 |
| Android原输入组合 | 1 | 一个新库保持到服务端→Android→服务端链结束 | 成本在配套原输入生成，不能拿已有body文件配任意新库 |

合计为5组可复用流程，其中Ledger为2组。按原守卫运行需3个专用实例profile（作者二选一＋ledgerverify＋foundation），另2组可顺序占用新隔离实例；无需21个专属端口或21轮手工配置。Foundation的负例分库与按类新datadir仍有实际运行成本，不能按“类数”省略。

## 环境profile的精确要求

下面是源码已有坐标，不是本轮新分配。密码只由本机安全配置提供，不写入文档。URL参数也参与精确比较。

### L-A：Ledger作者2类

依据[LedgerRuntimeEnvironment](../../server/src/test/java/com/acrylic/outbound/temporary/command/LedgerRuntimeEnvironment.java)第12–44行。

- 作者profile：127.0.0.1:19326；库acrylic_outbound_ledger_20260921；账号outbound_ledger_20260921@127.0.0.1；根目录D:/acrylic-pda-worktrees/r06-ledger-runtime/.local。
- 可选组合profile：127.0.0.1:19328；库acrylic_outbound_wave6_20260921；账号outbound_wave6_20260921@127.0.0.1；根目录D:/acrylic-pda-worktrees/sixth-wave-integration/.local。
- URL统一为jdbc:mysql://127.0.0.1:端口/库名?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true。
- datadir必须为对应根目录/mysql端口/data/；OUTBOUND_STORAGE=根目录/private-files；私钥作者为signing/private.pk8，组合为signing/private.der，公钥均为signing/public.der。路径逐字/Path比较；连接后复核datadir、端口、DATABASE()和完整CURRENT_USER()。
- LEDGER_RUNTIME_VERIFY=true、OUTBOUND_MIGRATE=true，OUTBOUND_DB_URL/USER/PASSWORD及OUTBOUND_AUTH_SIGNING_PRIVATE_KEY/PUBLIC_KEY配套。这些是测试条件，不是开放生产决定handler。

### L-V：Ledger独立验证11类

依据[LedgerVerifyEnvironment](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerVerifyEnvironment.java)第10–44行及[共用fixture](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerVerifyFixture.java)。

- 127.0.0.1:19327；库acrylic_outbound_ledger_verify_20260921_r2；账号ledger_verify_r2_20260921@127.0.0.1。
- URL=jdbc:mysql://127.0.0.1:19327/acrylic_outbound_ledger_verify_20260921_r2?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC。
- 根目录D:/acrylic-pda-worktrees/r06-ledger-verification/.local；datadir=mysql/data/，OUTBOUND_STORAGE=private-files-r2；OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=signing-private-r2.der，PUBLIC_KEY=signing-public-r2.der（均接根目录）。LEDGER_VERIFY=true；连接后要求端口、库、完整账号、datadir精确一致。
- 普通9类继承LEDGER_VERIFY_IT=true，按运行阶段迁移到被测源码需要的schema；证据写evidence-r2，合成设备密钥写seed-keys-r2。LedgerSeedIT另设LEDGER_VERIFY_SEED=true，强制旧阶段目标V34且必须空库；不能预迁移到底。
- 并发两类通过[LedgerLockEvidence](../../server/src/test/java/com/acrylic/outbound/ledgerverify/LedgerLockEvidence.java)读取performance_schema.data_locks/data_lock_waits/threads，需对应SELECT；断言READ-COMMITTED和真实等待边，不能调整超时或跳过取证。
- SignedMetadata直接使用上述配套RSA密钥重签；RetainedSeed额外依赖同一submission对应的seed-keys-r2设备EC密钥，不能重新造一个设备代替。

### F：Foundation六类

依据[FoundationEnvironment](../../server/src/test/java/com/acrylic/outbound/foundationmigration/FoundationEnvironment.java)第10–65行、[复制器](../../server/src/test/java/com/acrylic/outbound/foundationmigration/LegacyCopy.java)与[历史验收](../parallel/R06_FOUNDATION_VERIFY/HANDOFF.md)。

- 实例127.0.0.1:19324；根目录D:/acrylic-pda-worktrees/r06-foundation-verification/.local，datadir=mysql/data/。
- 正向库acrylic_outbound_foundation_verify_20260921；账号foundation_verify_20260921；OUTBOUND_STORAGE=根目录/private-files；FOUNDATION_VERIFY=true、FOUNDATION_CASE为空。
- 派生库为正向库名加_case；账号fndv_20260921_case；storage=private-files_case。case白名单为dirty/dirty2/missing/signature/ddl/lock；dirty2与dirty是同一种脏关系测试的不同命名空间，历史dirty失败保留，不应为凑覆盖重置它。
- URL=jdbc:mysql://127.0.0.1:19324/对应库名?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC；连接后核对datadir、端口、库及账号名前缀加@（源码未限定host为127.0.0.1，不能误记成完整账号比较）。
- 基线原值与文件摘要在evidence/v33-original.json，来源清单在seed-manifest.json；seed只允许基线命名空间。正向为“空库→V33来源→V34迁移→保留/约束”；不是跟随源码迁移到底。
- 副本先由FoundationCopyIT把空schema迁到V33并复制数据库原值，私有文件副本须构造器另行保全/复制并核hash，配套签名验证键也必须匹配。LegacyCopy不负责复制文件；不能只建一个空库就直接跑Negative。
- 负例包含脏关系、缺文件、坏签名、DDL冲突、元数据锁5种，应隔离各自schema/账号/storage，FK/CHECK持续启用、失败库保留；不得自动repair/reset。锁例还需performance_schema.metadata_locks/threads可读及元数据锁采集可用，保持原10秒锁等待条件，不能放宽。

## 21类逐项矩阵

L-A/L-V/F完整坐标见上文。所有行本轮状态都是只读评估、未执行。

| 类 | 守卫/显式条件 | 数据、文件或隔离前置 | 共用及顺序 |
| --- | --- | --- | --- |
| LedgerAuthorIT | L-A；LEDGER_RUNTIME_VERIFY | 每用例新TemporaryFixture；只在本类导入LedgerAuthorOperations；签名/审计spy收尾复位 | 与作者边界类共用构造配置，顺序独立进程/attempt |
| LedgerProductionBoundaryIT | L-A；LEDGER_RUNTIME_VERIFY | 原生产上下文无测试CommandOperation，验证无新台账写入/仅GET；默认生产能力关闭 | 同L-A，不能混入作者测试配置 |
| LedgerConcurrencyIT | L-V；LEDGER_VERIFY_IT | 自建来源；锁证据权限；READ-COMMITTED；同键/异文/同版本三竞争 | 共用L-V运行配置，保持原超时 |
| LedgerCorruptionIT | L-V；LEDGER_VERIFY_IT | 自建来源后定向损坏回执关联/字节；坏行与证据保留 | 共用配置；污染数据不能作为其他用例seed |
| LedgerFaultIT | L-V；LEDGER_VERIFY_IT | 自建来源；request/version/receipt/event/audit/signature边界注入后复位spy | 共用L-V运行配置 |
| LedgerHistoricalReadIT | L-V；LEDGER_VERIFY_IT | 自建来源；改变mapping/当前指针和恢复状态；finally恢复全局状态 | 共用配置，顺序运行避免全局状态互扰 |
| LedgerIdentityRaceIT | L-V；LEDGER_VERIFY_IT | 自建来源；人员/仓库/会话两连接竞争，锁证据权限 | 共用L-V运行配置，保持原超时 |
| LedgerReadAccessIT | L-V；LEDGER_VERIFY_IT | 独立owner/outsider来源与当前角色/仓库 | 共用L-V运行配置 |
| LedgerReplayIT | L-V；LEDGER_VERIFY_IT | 每例生成完整/空取消/部分块/完整后取消来源，保存设备键用于真实challenge重登 | 共用L-V运行配置，不依赖LedgerSeedIT先成功 |
| LedgerRetainedSeedIT | L-V；LEDGER_VERIFY_IT及LEDGER_VERIFY_RETAINED | evidence-r2/retained-source.json指向库中原submission；原私有文件、签名键和设备EC键闭合 | 同L-V体系但必须保留链；单列恢复时点，不能将重建来源称历史原件 |
| LedgerSecondLookupIT | L-V；LEDGER_VERIFY_IT | 自建来源；锁前miss/竞争提交/锁后重放；不改屏障时限 | 共用L-V运行配置 |
| LedgerSeedIT | L-V；LEDGER_VERIFY_SEED | 0表、无seed-started.json；测试自迁到V34，生成四种来源及设备键/manifest | 同体系的最前阶段；已用旧r2不能重种 |
| LedgerSignedMetadataIT | L-V；LEDGER_VERIFY_IT | 自建来源，配套RSA私公钥，变造语义后有效重签 | 共用L-V运行配置；不替换原件历史签名 |
| FoundationCopyIT | F；FOUNDATION_CASE为六个非空白名单之一 | 副本0表；v33-original.json及配套文件；自迁V33、关系完整复制 | 同一复制流程逐副本执行，须先于该副本Negative |
| FoundationNegativeIT | F；FOUNDATION_NEGATIVE；非基线case | 副本恰处V33且原值/文件/签名预检通过；5种破坏场景 | 同实例多schema，每场景保留独立失败状态 |
| FoundationPreservationIT | F；FOUNDATION_PRESERVED；基线case | 恰处V34；原值/文件/seed-manifest及配套签名；只验保留/DTO、零迁移 | 与Upgrade成功后的基线共用，不能先迁移到底 |
| FoundationSchemaIT | F；FOUNDATION_SCHEMA；基线case | 恰处V34且有原非空两仓数据；每测试@Transactional回滚 | 可共用正向基线；不在空白schema上验 |
| FoundationUpgradeIT | F；FOUNDATION_UPGRADE；基线case | 恰处V33，原快照/文件/签名预检；自迁V34并验证原历史、回填、DTO | Seed之后；升级只能执行一次，不重置旧库 |
| FoundationV33SeedIT | F；FOUNDATION_VERIFY；requireSource | 基线0表；旧阶段生产路径生成20组合成来源并保存原件 | 正向链起点；源码对旧schema的兼容性尚未本轮验证 |
| OrderNumberMigrationIT | OUTBOUND_NUMBER_UPGRADE_DB_URL非空；使用OUTBOUND_DB_USER/PASSWORD | 新空schema，测试自行准备前代并迁到V32，要求后半段仅1次迁移 | 独立于L/F阶段；无硬编码端口/库名/datadir，需构造器补精确隔离核验 |
| TemporaryAndroidSubmissionIT | R06_ANDROID_BODY_VERIFY=true；普通Spring数据源 | server/build/pda-temporary-android-body.json与同库原temporary_authorization/用户/设备/库存来源匹配 | 保持同一新库跑跨端生成链；可复用普通构造器的保持实例能力 |

### 编号迁移与Android的关键补充

[OrderNumberMigrationIT](../../server/src/test/java/com/acrylic/outbound/inventory/OrderNumberMigrationIT.java)第20–33行：未传OUTBOUND_NUMBER_PREDECESSOR_LOCATION时前段停V30；传入时停V31，再到V32并断言只执行1次。源码迁移目录已包含V31与V32，所以不能忽略此前置直接按普通空库入口运行。后续需配置无重复迁移的前代location，或单独评估测试启动阶段的最小适配；本轮未选定配置、未执行，不据此宣告产品缺陷。历史[编号验收](../ORDER_NUMBER_GUIDE.md)记录曾用受控前代脚本副本，不能未经核对照搬到含同版本脚本的源码目录。

Android是既有主机Robolectric/Room输入链，不必为此新增模拟器或真机环境：[TemporarySubmissionContractFixtureIT](../../server/src/test/java/com/acrylic/outbound/inventory/TemporarySubmissionContractFixtureIT.java)生成同库准备和签名回执→[TemporarySubmissionContractTest](../../android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporarySubmissionContractTest.kt)消费preparation并调用真实冻结代码生成body→[TemporaryAndroidSubmissionIT](../../server/src/test/java/com/acrylic/outbound/inventory/TemporaryAndroidSubmissionIT.java)用同身份新测试会话经服务层验收/幂等。三步必须保留同一库与签名/私有文件，主机测试的输入文件依赖也要齐备。现有body存在不代表与任意库匹配；这条链也不等于实际HTTP登录/设备验收。

## 本机可用性与成本上限

仅Test-Path元数据核对：作者19326/组合19328、ledgerverify19327、foundation19324的固定datadir均已存在；前述端口均无监听。未连接旧实例，未核实库内阶段、账号授权或键与库的一致性，路径存在不等于可直接复用。

ledgerverify的seed-started.json、retained-source.json和seed-keys-r2目录存在，seed-manifest.json不存在；[历史交接](../parallel/R06_LEDGER_VERIFY/HANDOFF.md)记录r2种子在首complete后失败，随后以明示恢复时点接续保留验收。此为既有历史，不能删marker补种以抹掉失败，也不能推定四场景种子已完整。Foundation的v33-original.json、seed-manifest.json、v34-after-migration.json存在；主树Android body存在；本轮未打开这些含原始材料的文件或读取私钥。

这使“仅参考R08构造器换库名”不可行：R08可在新attempt datadir使用固定库名，上述三组却会拒绝新datadir。后续若要保留全部旧目录原样，建议评估**仅测试环境的新增精确profile**，同步root/storage/证据/种子键路径及护栏单测，不删旧profile、不采用任意URL/路径前缀放行。L-V的桥接器、重签器和锁证据还引用固定ROOT/DATABASE，不能只改一个环境变量。该建议尚未实现或验证，需要下一轮明确纳入测试基础设施范围；无需据此修改R05/R06/R07生产代码。

旧阶段Seed/Foundation还需固定前代生成方与本次迁移/消费者的源码关系。即使端口通了，较新应用能否在旧schema创建来源也不能凭静态守卫保证；若需大范围改fixture或重造业务场景，应停止并另报，不计作普通专用库配置。

## 建议实施顺序（待用户决定）

1. 第一阶段，1–2轮：给作者与ledgerverify运行类准备新隔离profile/构造器，保留旧目录与证据；优先跑2＋9类。可复用e2a-batch-it.ps1的独立attempt/XML汇总，按类准备新状态并保留失败库；不强求共享一份可变数据库。并发只验既有命令竞争，不深入526；撞条码同类或确认真bug即停报。
2. 第二阶段，约1轮：处理LedgerSeed/Retained的空库→保留链，以及编号迁移启动阶段。只有原事实来源与前代兼容明确才运行；不能用新生成事实冒充旧r2保留事实。每个链路阶段单独attempt/证据，依赖阶段要明确保留同库，不能每类重新初始化。
3. 第三阶段，约1–2轮：Foundation正向链与5种负例副本，Android完整输入链；可并入前面空余时间，但不为轮次目标简化负例或跳过跨端消费。

因此3–5轮只是上述环境适配小、原件可用、无新增故障时的估算；已有固定旧路径和阶段依赖，**本轮不能承诺21类全量收口**。建议用户先决定是否按第一阶段开工；若只接受“两个新库、零测试环境适配、2–3轮全21类”的范围，则不值得强行推进，应保留专项清单并转其他方向。

本轮不新增通过/失败/skip，剩余仍21类；526保持疑似缺陷待专项。A3主体已被用户接受，R09 C2已有范围文档但本轮没有实施。交回用户决定继续A3剩余、转R09 C2或其他方向。
