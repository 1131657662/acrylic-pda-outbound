# R09 C2 应用入口接线评估（2026-09-28）

## 1. 结论与本次范围

**涉及恢复准入、认证、调度、文件及进程、启动迁移等大块生产实现，按用户边界停在评估；建议逐入口分批授权，不一次接完。** 首先明确C1证据与操作租约的组合语义，再从HTTP/Spring Session外层开始。每批单独验收，全部入口与组合验收完成之前不称为“全应用门禁”。

输入主树D:/acrylic-pda-outbound、codex/development、fc7ebbd。A3主体已收尾，剩余21类仅属[发布前完整回归清单](A3_REGRESSION_CLOSURE_2026-09-28.md)，本次没有配置profile或补跑。R09只读源码和历史证据，未改生产/测试/工具代码、数据库、开关或契约，未启动服务、未分配attempt。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

评估依据：[APPLICATION_ADMISSION入口表](../parallel/R09/APPLICATION_ADMISSION.md)、[C2范围](R09_C2_PROJECT_SCOPE_2026-09-28.md)和下列源码。下面的验收均为**建议、尚未执行**；源码调用关系不代替真实入口覆盖证明。

## 2. 已有证据与前置缺口

| 范围 | 已有事实 | 不能外推的结论 |
| --- | --- | --- |
| JdbcTemplate/事务/连接 | [RecoveryAdmissionConfiguration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionConfiguration.java) opt-in使admitted DataSource成为Primary，原池只带Flyway限定符。[R09入口attempt105](R09_APPLICATION_ENTRY_ACCEPTANCE_2026-09-26.md)在独立非空MySQL验过连接接纳、事务、关闭竞争及UNKNOWN；不是本轮复跑 | Spring Session使用该DataSource，不等于Session外层请求已受保护；连接间的文件活动、独立事务和子进程也未因此覆盖 |
| C1 NORMAL登记 | [历史验收](R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md)有attempt90m组件绿；后续authority_revision变更后的真实库复验未绿。源码已有[RecoveryNormalRegistration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryNormalRegistration.java)，不是Spring bean、没有路由 | 不能写成“完全没有实现”，也不能写成“C1已经完成”。旧库checksum隐患已在入口验收登记；不得改旧迁移或直接把旧库当绿色复用库 |
| 运行时双侧核对 | [RecoveryAdmissionVerifier](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionVerifier.java)已有只读比对；生产源码搜索未见其被应用入口调用。[RecoveryAdmittedDataSource.acquire](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmittedDataSource.java)核对外部固定身份、关闭意图并借租约，不调用该Verifier | 生命周期租约、双侧证据与业务权限是不同条件；只包一层writer lease不构成NORMAL准入 |

历史登记组件允许无外部意图的登记并标记`UNRECORDED_EXTERNAL_INTENT`，而运行时Verifier缺意图会拒绝；这个差异已在历史验收记录，**不在C2中顺手修、也不据登记成功开放写入**。C1仍是独立前置，必须明确可复验的原件/DB双侧证明和允许态条件。attempt105的`recovery_normal_admission=0`证据尤其不能当成NORMAL全链已验。

## 3. 实施前必须明确的公共依赖（G0，不是新增业务入口）

1. **同一身份、同一操作生命周期。** 请求、任务、文件和连接共用固定安装/实例权威；操作租约先于任何连接或文件活动。保持coordinator→writers→server_state→业务锁顺序，不在已经持业务锁时反向接纳。复用已验连接层，必要的衔接改动须另列授权范围和回归，不能借C2重写事务机制。
2. **新操作拒绝与旧操作收尾须分别设计。** [RecoveryWriterLease.acquire](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryWriterLease.java)每次先检查关闭意图，随后才增加已有租约引用；[RecoveryControlIntents.requireAdmission](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlIntents.java)会拒绝待决关闭意图。因此给请求外包租约后，Session尾部新借连接、Worker独立续租/完成仍可能被拒。它是组合接线的已知设计风险，尚无本轮动态失败，不能直接删除守卫或允许所有线程复用旧租约。
3. **建议收尾模型。** 关闭意图后拒绝新操作；已接纳操作仅在原固定身份和受限子操作上下文内完成必要收尾，不能启动无关新业务；不能确认完成则保留租约/毒化并按已有隔离流程排空。具体子连接准入与中断策略必须在G0形成可审查方案并获相应实施授权。不得仅靠ThreadLocal覆盖异步/独立线程，也不把finally返回当作完成证据。
4. **双侧核验不能形成借连接递归或校验后竞态。** 接线方案应明确在受保护的生命周期内如何读DB准入事实、何时重核权威、如何保留固定身份；不得把Flyway原始池作为普通入口绕行渠道。C1未满足时可验缺权威/关闭态拒绝，允许态全链验收必须等C1证据满足。
5. **启动与隔离输入。** 先用专用非空库、隔离文件根、可信控制原件/原ID/回执；测试初始化与应用入口验收分开记录。迁移、Bootstrap、HTTP与Worker何时可启动必须互斥。未接完的入口保持测试隔离、不参与局部通过结论；生产默认值不变。

## 4. 逐入口：生产面、前置与最小验收

| 入口 | 源码事实 / 涉及生产面 | 前置 | 最小动态验收建议 |
| --- | --- | --- | --- |
| HTTP / Spring Session外层 | [Web Security](../../server/src/main/java/com/acrylic/outbound/auth/SecurityConfiguration.java)有Web登录/CSRF/退出；[RequestIdFilter](../../server/src/main/java/com/acrylic/outbound/common/RequestIdFilter.java)为最外层请求ID过滤器。无恢复准入过滤器；Session由框架配置 | G0；核实实际Servlet过滤器顺序。租约在SessionRepositoryFilter之前取得，晚于最终Session保存释放；ERROR/ASYNC分派不漏、不重复放行。框架Session过期清理也需盘点，不能用请求过滤器覆盖结论代替 | 真服+真HTTP+非空MySQL：CSRF GET、登录、普通GET、退出及错误响应的Session写正确；屏障卡住最终保存再关闭，旧操作有明确收尾证据，新请求在Session写之前拒绝；异常/UNKNOWN不早释；默认关闭行为回归。异步生命周期须有专门容器证据，即使业务源码暂未使用也不宣称自动支持 |
| PDA challenge/login/refresh/logout | [PdaAuthController](../../server/src/main/java/com/acrylic/outbound/pda/PdaAuthController.java)、[PdaAuthentication](../../server/src/main/java/com/acrylic/outbound/pda/PdaAuthentication.java)、[PdaSessions](../../server/src/main/java/com/acrylic/outbound/pda/PdaSessions.java)：challenge消费、token轮换/撤销、device.last_seen、审计；独立[PdaSecurity](../../server/src/main/java/com/acrylic/outbound/pda/PdaSecurity.java)为stateless Bearer链，已有CSRF配置与Web不同 | HTTP外层已验；明确关闭态认证是否有专用能力。保留设备签名、权限、版本及两条安全链的既有校验；不能简单加入auth URL豁免 | 原设备密钥/可信身份的真HTTP登录→刷新→退出；逐值核对session、last_seen和审计；错误签名/权限仍拒绝；关闭竞争不产生半条凭据。未获关闭态专用能力时拒绝，不借登录恢复发新作业资格 |
| Worker poll及提醒sweep | [BackgroundWorkerProcess](../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundWorkerProcess.java)有poll和独立提醒入队调度；[BackgroundJobWorker](../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundJobWorker.java)覆盖recoverExpired→claim→handler→complete/fail；[BackgroundJobLeases](../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundJobLeases.java)使用REQUIRES_NEW；[JobRecoveryFence](../../server/src/main/java/com/acrylic/outbound/jobs/JobRecoveryFence.java)保持启动epoch | G0；在runOnce及独立sweep写前接纳，不能只包claim事务。先验无文件DB任务；导出任务完整验收依赖ExportFiles/renderer两批 | 真Worker进程+MySQL：正常claim/续租/成功/失败/过期回收和提醒入队；关闭分别卡在claim前、handler中、完成前；新poll/sweep拒绝、已接纳者不提前释租；独立续租/完成的关闭竞争有结果，旧epoch/token不能发布；进程退出后连接/事务/文件写者均排空 |
| ImportFiles | [ImportService.upload](../../server/src/main/java/com/acrylic/outbound/inventory/ImportService.java)先store再writer.save；[ImportFiles](../../server/src/main/java/com/acrylic/outbound/inventory/ImportFiles.java)创建目录/临时文件、force、move及cleanup。影响库存导入与原件持久性 | HTTP外层；明确multipart容器暂存与应用文件的接纳/清理边界，不能等DB事务开始才取得租约 | 真HTTP上传，原字节/摘要与DB引用一致；分别在临时写入、move后DB登记前关闭/异常，未入DB引用的活动仍阻止排空；拒绝的新请求不落应用文件；已知清理完成与未知状态分开留证 |
| SubmissionFiles | [SubmissionFiles](../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionFiles.java)含store、read及confirm(force)。共用于提交、归档、审计和更正/续办证据，触及R05/R06/R07共享面 | HTTP/PDA外层；盘点所有调用方，优先外围接线。不能只拦store而遗漏confirm；若必须修改已验业务类，先单列范围申请，不在本批默认改 | 原FORMAL/TEMP请求与字节真实提交/归档，摘要、原回执和引用一致；关闭卡住store/confirm和异常cleanup时不早释；现有权限、幂等与默认归档开关回归；不更改业务确认/更正逻辑 |
| ExportFiles（申请、快照、下载） | [ExportProducer.create](../../server/src/main/java/com/acrylic/outbound/exports/ExportProducer.java)在事务内存快照再入队；[ExportFiles](../../server/src/main/java/com/acrylic/outbound/exports/ExportFiles.java)负责快照/force/读；[ExportDownloads.read](../../server/src/main/java/com/acrylic/outbound/exports/ExportDownloads.java)下载时写审计，GET不能豁免 | HTTP外层；Worker与renderer有清晰交接。父HTTP结束不是异步任务结束，作业执行需独立持租约 | 真HTTP申请、快照入队、下载；逐值核对快照和产物hash/长度/审计。关闭覆盖快照写、未入DB引用文件、下载读和审计；既有默认v1与已验CORRECTED内容回归，ConfirmedV1Adapter不改。完整XLSX/PDF生成留到renderer组合批 |
| SourceEvidenceIntakeFiles / ObjectFiles | [页文件](../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeFiles.java)storePage建目录，readPage本身只检查目录；[对象文件](../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeObjectFiles.java)verify/read经objectDirectory调用createDirectories。影响R06原证据接收/校验/读取 | PDA外层、可信原始输入；同属证据文件入口一批，页/对象分别列断言；不放宽路径、签名或资料守卫 | 真HTTP原件接收/verify/read，正文hash/长度/原ID/回执逐值相等；在缺子目录的隔离样本验证对象“读取”隐含写也先接纳；关闭时目录/对象不越过门禁；缺原件按既有规则拒绝，不补造 |
| ExportRendererProcess / 报告子进程 | [ExportJobHandler](../../server/src/main/java/com/acrylic/outbound/exports/ExportJobHandler.java)调用[ExportRendererProcess](../../server/src/main/java/com/acrylic/outbound/exports/ExportRendererProcess.java)建目录/启动进程、定时renew；已有Windows Job、子进程排空及drainFailed。尚无R09操作租约传递 | Worker、ExportFiles批已验；复用已有进程管理，父租约覆盖启动前至确认整个写进程树退出，含tmp/log/产物和失败路径 | 真HTTP申请→真实Worker→实际XLSX/PDF渲染，关键字段值回读；在运行、超时、中断、退出不明各点关闭，未知退出不能释放或发布产物；确认子进程树及DB排空。Windows证据不外推Linux容器隔离 |
| Flyway | [RecoveryAdmissionConfiguration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionConfiguration.java)保留migration-only原池；[application.yml](../../server/src/main/resources/application.yml)迁移显式启用、clean禁用。迁移生命周期不属于普通请求/事务准入 | 独立迁移许可及互斥协议；明确schema尚未存在时不能依赖尚未建成的业务准入表，迁移后才允许正常入口尝试准入；不修改历史迁移 | 两个独立进程竞争“迁移/业务启动”，证明互斥；无迁移许可拒绝且不自动登记NORMAL；失败/UNKNOWN保留关闭和证据；非空独立库从指定前代升级后逐值核原事实；默认迁移不启用 |
| BootstrapAdmin | [BootstrapAdmin](../../server/src/main/java/com/acrylic/outbound/auth/BootstrapAdmin.java)是显式开关的CommandLineRunner，经JdbcTemplate事务创建仓库/管理员/授权/审计，已有空身份库约束；未直接使用Flyway原池 | 独立初始化许可、迁移完成、C1初始化与业务准入顺序明确；该入口是身份权限生产边界，不得因启动缺控制记录而自动运行 | 隔离空身份库显式初始化成功；无许可/已有用户/错误控制身份拒绝且无半套用户授权；中断回滚和启动竞争；与Worker/HTTP业务写互斥；默认bootstrap关闭。不拿非空业务库冒充空身份初始化场景 |

Spring Session过期清理的实际调度线程与容器multipart暂存属于框架路径，本轮只登记为实现前必查项，没有运行应用证明它们的完成边界。生产源码搜索未见业务ASYNC控制器，仍不能据此跳过过滤器ERROR/ASYNC分派设计。若盘点发现独立写入口，补列单独批次后再做，不把未验框架行为合并为通过。

## 5. 建议接入顺序与每批边界

| 顺序 | 独立批次 | 依赖 / 当批出口 |
| --- | --- | --- |
| G0 | C1证据核对＋操作租约接线设计 | 先只核对原件与历史缺口，形成上述嵌套/收尾/启动方案；C1补实现/真实复验须独立授权。未达允许态条件时不推进允许态验收 |
| B1 | HTTP / Spring Session外层 | 首个实现批：同一身份、外层接纳、最终Session写、拒绝和UNKNOWN；DB使用已按隔离许可准备好的非空库，应用Flyway/Bootstrap保持关闭 |
| B2 | PDA认证入口 | 依赖B1；专门验证stateless链、签名和认证写；不新增关闭态认证放行 |
| B3 | ImportFiles | 依赖B1；真HTTP文件先写后DB的关闭竞争 |
| B4 | SubmissionFiles | 依赖B1/B2；提交/归档共享文件生命周期，不动既有业务语义 |
| B5 | 来源证据页/对象文件 | 依赖B2；同一证据入口，覆盖读中隐含写及原件守恒 |
| B6 | Worker poll / 提醒sweep | 依赖G0；先完成纯DB任务与独立事务收尾，其他文件任务不计本批完整覆盖 |
| B7 | ExportFiles申请/快照/下载 | 依赖B1；与B6确定作业交接，渲染尚未验时不宣称整个导出链已接 |
| B8 | 渲染子进程 | 依赖B6/B7；串起真HTTP→Worker→文件→实际进程退出，复验任务续租/完成与关闭竞争 |
| B9 | Flyway迁移准入 | 独立启动批，依赖G0许可设计；须在任何生产启用或新安装组合之前完成 |
| B10 | BootstrapAdmin初始化准入 | 依赖B9及C1初始化方案；身份库初始化单独验收，不改变默认关闭 |
| 最后 | 全入口组合验收 | C1和各入口均满足后，在同一可信输入上验正常运行、关闭/排空、重启、权威缺失/回退及DB回退；单批局部绿不代替总验收 |

这是一条**开发和验收顺序**，不是把迁移放到运行时末尾：实际启动必须先满足显式迁移/初始化许可，完成后才允许HTTP/Worker正常准入。前面各批可使用独立准备好的测试库，因此B9/B10可单独实现，不能据前批绿启用生产全应用门禁。

每个入口一个实现批（建议各给1–2轮独立时间盒，按复杂度再评估），实现与验收文档分别提交、每批推送；未满足当批最小标准保留断点。这里不承诺总轮数，也不把Worker、所有文件和渲染打包成一次授权。框架额外入口或租约语义超出批准范围即停报。

## 6. 需用户授权/决策的边界

- **实施范围**：可先授权G0依赖核对/设计；G0就绪后另授B1外层接线及所必需的连接租约衔接，生产默认不变。B2–B10分别授权；R05/R06/R07已验业务代码、ConfirmedV1Adapter继续排除。
- **C1与关闭态能力**：C1真实双侧证明未完成，不自动修复/登记；关闭态哪些认证/证据操作可进入需明确政策。未决时拒绝，普通提交、配对、资格发放不能借道，不新增生产释放端点。
- **启动权限**：迁移/管理员初始化独立许可、原库操作、生产切换与发布均不由C2代码实现授权代替。默认配置、七类生产决定和TEMP归档开关保持原状；localDeletionAllowed=false。
- **部署平台**：Windows通过不能授予Linux进程隔离结论；Linux服务容器/文件持久性及现场恢复另列验收。若需要契约变更仅先写草案，发布另行决定。

## 7. 本次验证与未验边界

本次只有作者静态评估及文档检查：核对入口表、上述源码调用/生命周期、历史NORMAL与入口105证据范围；检查引用、diff和看板事实块。无C2实现、无单测/IT/真实HTTP/新attempt，无独立审查，不将历史连接验收写成此次全链通过。

仍未验：C1最终结构的双侧登记真实复验；所有C2请求/任务/文件/进程/启动组合；Session尾保存与框架清理、异步/错误分派、multipart暂存；关闭后的受限旧操作收尾；Linux进程/持久性、Android传输代次与原事实代次组合、原资料/现场恢复及生产释放。A3剩余21类和526专项、R07抬头撤销及D范围外组合均保持独立未验。

**交回用户决定是否按上述顺序实施。建议先G0、再单批B1；本轮不自动开工。**
