# R06 实际代码、扩展与依赖清单

日期：2026-09-18；审阅基线 `2369392076083a5a4f59d8ea3d6edebe5067a03f`。**这里只读核对源码与既有交付记录；没有运行新的业务测试。** “可复用”指机制存在，不代表已支持 TEMP，也不代表本轮验证通过。

## 1. 基线与避免误判

- 起点契约 [openapi.yaml](../../../contracts/openapi.yaml) 为 0.17.0；迁移最高 V21；[PdaDatabase](../../../android/app/src/main/java/com/acrylic/outbound/pda/data/PdaDatabase.kt) 为 Room9；[Android构建配置](../../../android/app/build.gradle.kts) 为 0.9.0-r05-sync/code10。本文不预占后续版本号。
- [R05 订单同步记录](../../R05_ORDER_SYNC_GUIDE.md) 已追加 test 部署：提交 `4e0e249476aded2ab491751143a94e7e37c02f30`，只读入口/迁移保护已核验，未更新 PDA APK；不能把顶部历史“未部署”解释为当前测试服务一定未部署，也不能把部署解释为临时单已运行。
- [V5](../../../server/src/main/resources/db/migration/V5__order_drafts_and_revisions.sql) 虽允许 `PDA_TEMP` 和 `LINKED` 枚举，当前没有临时建单/上传/关联服务。枚举占位不等于完整能力。
- [SubmissionControl.read](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionControl.java) 明确只接受 FORMAL，并要求非空 assignmentId；[V9](../../../server/src/main/resources/db/migration/V9__formal_submissions.sql) 的 scope/source_order_id、submission/assignment_id 非空且有正式单外键。
- 原目录的 AGENTS、README、总进度、路线、设计总规范、PDA规范和应用更新设计为其他任务未提交内容，本轮不复制、不覆盖、不代提交。

## 2. PDA 复用与扩展定位

所有路径固定于起点；后续 R05 合入后须重新核对。

| 现有模块与代码 | 已实现 | R06 要扩展或保持的边界 |
| --- | --- | --- |
| [OrderCards](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt)、[PreparationViewModel](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt) | 本机/待领取分页上下文、领取未知/拒绝恢复与资料准备入口 | 加临时草稿/任务来源和单一主动作，不把 formal assigned 列表当临时任务；当前行视图重建模式需在临时长列表核对内存和位置 |
| [PreparationRepository](../../../android/app/src/main/java/com/acrylic/outbound/pda/preparation/PreparationRepository.kt)、[PackageVerifier](../../../android/app/src/main/java/com/acrylic/outbound/pda/preparation/PackageVerifier.kt)、[LocalActivation](../../../android/app/src/main/java/com/acrylic/outbound/pda/preparation/LocalActivation.kt) | 三份固定 gzip、签名/摘要、行数、原子激活和下载确认 | 抽出经证据支持的下载/校验边界，新增 TEMP 资格和仓库包；当前 prepare/ack 都按 assignment 路径且计划须服务器签名 |
| [InventoryDecoder](../../../android/app/src/main/java/com/acrylic/outbound/pda/preparation/InventoryDecoder.kt)、[PreparedOrder](../../../android/app/src/main/java/com/acrylic/outbound/pda/data/PreparedOrder.kt) | 条码/整数克、周期、状态行一致性；正式计划绑定 | 新包提供稳定 productId/mappingId及规则版本；临时本机计划由授权范围验证。不能借已有 PreparedOrder FK 存伪正式授权 |
| [JwtVerifier](../../../android/app/src/main/java/com/acrylic/outbound/pda/security/JwtVerifier.kt)、[TrustedTime](../../../android/app/src/main/java/com/acrylic/outbound/pda/security/TrustedTime.kt)、[PdaSession](../../../android/app/src/main/java/com/acrylic/outbound/pda/network/PdaSession.kt) | RS256、固定字段/身份版本/48h、服务器锚点；重启或墙钟偏移阻断 | TEMP 独立用途与 claims；保留时间/身份规则，不承诺跨设备重启离线新增；在线权限失效和历史资格核验分开 |
| [WorkStore](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkStore.kt)、[WorkOrder](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkOrder.kt) | 正式 assignment 作为本地容器，续作用当前资格包装原资料，统计一致性校验 | 建议 localWorkId/context 适配 FORMAL/TEMP；现有 assignmentId PK/FK 及导航参数是迁移敏感点，需旧数据回填和全链核对 |
| [WorkEngine](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkEngine.kt)、[WorkReception](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkReception.kt)、[WorkValidation](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkValidation.kt)、work/WorkSaving.kt、work/WorkCorrections.kt | 单写者队列、接收顺序、B模式确认意图、事务保存/防重、错误暂停、受控末包撤销 | 通过资格/上下文适配支持 TEMP，保留所有耐久边界与确认绑定；不得另建临时专用共享扫码栈。WorkValidation 当前读取准备时 available，还未具备 R05 最新条码流 |
| [WorkScan](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkScan.kt)、[ScanDao](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/ScanDao.kt) | captureId 作为扫描主键，全本机条码 guard，序号与审计 | 临时/正式必须同一防重域；guard 不能被映射、退出、上传成功或清理擅自释放。新周期解除依赖 R07 及已知状态 |
| [WorkActivity](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/WorkActivity.kt)、[WorkRenderer](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/WorkRenderer.kt)、[WorkDetailsActivity](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/WorkDetailsActivity.kt) | 原生扫码/手输、实体键、详情、FLAG_SECURE | 加临时标签/固定计划来源，不重做键位政策；临时建单页为新增，严格遵守480px实际空间 |
| [SubmissionFreeze](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/SubmissionFreeze.kt)、[LocalSubmission](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/LocalSubmission.kt)、[SubmissionJson](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/SubmissionJson.kt) | 原正文规范摘要/事务冻结，32MiB限制，未决输入和0包阻断 | source 当前写死 FORMAL，需 TEMP 头/计划/资格、逐产品原因、映射外置；不得重写历史冻结字节 |
| [SubmissionTransport](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/SubmissionTransport.kt)、[SubmissionRepository](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/SubmissionRepository.kt)、[UploadWorker](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/UploadWorker.kt) | 先查原批次、整包/分块、同进程同步互斥、Wi-Fi唯一任务及有限退避 | TEMP 路由和来源恢复；当前要求原人原机。已有 Worker 不等于 R05 通用后台任务/提醒/审计/清理完成 |
| [ReceiptVerifier](../../../android/app/src/main/java/com/acrylic/outbound/pda/submission/ReceiptVerifier.kt)、[SubmissionActivity](../../../android/app/src/main/java/com/acrylic/outbound/pda/ui/SubmissionActivity.kt) | 签名/摘要/来源/逐产品/序号/扣账核对后 SYNCED | 当前期待 sourceOrderId 和 resultVersion=1；需签名 TEMP→canonical映射、当前轮次/目标证明、新待决条件及仅成功可离场文案 |
| [SyncRepository](../../../android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncRepository.kt)、[SyncApply](../../../android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncApply.kt)、[SyncWorkGuard](../../../android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorkGuard.kt) | 订单流完整基线/游标/恢复阻断；LINKED目前只是阻断提示 | TEMP source/mapping映射恢复入口，条码流叠加不放本机锁；R05独立恢复保护不可由临时新资格绕过 |
| [AuthorizationRenewalRepository](../../../android/app/src/main/java/com/acrylic/outbound/pda/renewal/AuthorizationRenewalRepository.kt)、[ContinuationRepository](../../../android/app/src/main/java/com/acrylic/outbound/pda/continuation/ContinuationRepository.kt)、[DispositionInstall](../../../android/app/src/main/java/com/acrylic/outbound/pda/continuation/DispositionInstall.kt) | 正式单受控续期、原机续作和累计处置核验 | TEMP资格续期、未正式化scope恢复、映射兼容及全部卸下结束。换机不在现有实现内 |

迁移建议的最小边界是“稳定作业容器与实际授权分离”，不是全工程重构。先列出当前所有 assignmentId 语义（容器/真实授权/路由/原来源），逐个决定适配；不能全局文本替换。旧已冻结正文、Keystore、PreparedOrder历史资格、capture序号、guard、当前批次、续作/召回证据及R05同步保护必须逐项比对迁移前后。

## 3. 服务端与 Web 定位

| 代码 | 可复用 | 必须扩展/限制 |
| --- | --- | --- |
| [UserService](../../../server/src/main/java/com/acrylic/outbound/users/UserService.java)、[UserAccess](../../../server/src/main/java/com/acrylic/outbound/users/UserAccess.java)、[PdaAccess](../../../server/src/main/java/com/acrylic/outbound/pda/PdaAccess.java) | 角色/仓库约束、版本、在线重验、禁止越权授予 | temporary create/confirm/link 未在权限迁移中定义；增能力角色和设备允许临时作业资格，不假称已可单独配置权限 |
| [AssignmentService](../../../server/src/main/java/com/acrylic/outbound/pda/AssignmentService.java)、[AuthorizationRenewalService](../../../server/src/main/java/com/acrylic/outbound/pda/AuthorizationRenewalService.java)、[PreparationPayloads](../../../server/src/main/java/com/acrylic/outbound/pda/PreparationPayloads.java) | 锁后身份复核、48h签名、固定快照/可用状态打包 | 全部仍依赖正式单；新增仓库级TEMP资格、准备、ack及续期，不能不建单就直接调用 |
| [OrderCommands](../../../server/src/main/java/com/acrylic/outbound/orders/OrderCommands.java)、[OrderWriter](../../../server/src/main/java/com/acrylic/outbound/orders/OrderWriter.java)、[OrderPublishing](../../../server/src/main/java/com/acrylic/outbound/orders/OrderPublishing.java) | 计划正整数/唯一代码、版本/汇总、正式号与发布历史机制 | TEMP正式化须稳定来源唯一；后补正式单发布时加入重复提示与并发边界；不得将待审TEMP发布成可领取普通单 |
| [SubmissionService](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionService.java)、[SubmissionStore](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStore.java)、[SubmissionChunks](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionChunks.java) | 控制头/正文分离、abort先到、原批次查询、同轮唯一current | TEMP来源scope、无canonical控制头、关联REDIRECTED、分块与abort锁序；当前读写映射均假设正式单 |
| [SubmissionEvidence](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionEvidence.java)、[SubmissionSnapshot](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionSnapshot.java)、[SubmissionEvents](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionEvents.java) | 原身份/序号/时间/快照、独立重算和证据留存 | TEMP资格、计划产品ID、审计来源链；order_id关联事实需区分来源与canonical投影，避免关联时改原事件 |
| [SubmissionStock](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStock.java)、[SubmissionFinalizer](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionFinalizer.java)、[SubmissionAccounting](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionAccounting.java) | 单包周期/占用重验、待审预留、整单入账/可选扣账 | finalizer现在读取 AssignmentStore 且 Approval只有布尔值；改为来源适配和独立多条件决定，不能直接“复用”即认为已支持临时异常 |
| [SubmissionReview](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReview.java)、[SubmissionReviewController](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReviewController.java)、[SubmissionReceipts](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReceipts.java) | Web短装审批、拒绝保锁、签名成功/abort回执 | 当前实际路径为 `/review`，单批次单决定；新增临时确认/短装/关联重审绑定及职责分离、签名映射/命令回执查询 |
| [ContinuationRequests](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationRequests.java)、[ContinuationApproval](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationApproval.java)、[DispositionStock](../../../server/src/main/java/com/acrylic/outbound/continuation/DispositionStock.java) | 原机完整恢复、新轮次、保留/释放/隔离及占用原子转移 | 扩展TEMP及无正文abort来源；新增全部卸下结束事务，当前正式流程不能借0包替换完成 |
| [SyncService](../../../server/src/main/java/com/acrylic/outbound/sync/SyncService.java)、[SyncStreamStore](../../../server/src/main/java/com/acrylic/outbound/sync/SyncStreamStore.java)、[SyncBaselineStore](../../../server/src/main/java/com/acrylic/outbound/sync/SyncBaselineStore.java) | 提交后批量发布、固定ORDER投影/分页/高水位 | 当前不携带授权/完整回执/临时映射。TEMP映射事件只触发有权详情核对，不用ORDER状态事件单独放行 |
| [OrderSubmission.vue](../../../web/src/orders/OrderSubmission.vue)、[OrderProductPicker.vue](../../../web/src/orders/OrderProductPicker.vue)、[OrderDetailPage.vue](../../../web/src/orders/OrderDetailPage.vue) | 已有计划、差额/明细、短装处理及详情布局 | 临时审核/重复对比/关联、审核明细分页；旧OrderSubmission请求仅保存在内存，不声称刷新/关页恢复已实现 |
| [users/pending.ts](../../../web/src/users/pending.ts)、[devices/pending.ts](../../../web/src/devices/pending.ts) | 同标签页sessionStorage保存原命令、存储失败不发送的模式 | 可借鉴，不照搬为关闭浏览器也可靠；R06须服务端命令历史/回执恢复和当前版本核对 |

## 4. R05 交接能力

| 依赖 | 起点状态 | R06 所需能力/交接证据 | 未就绪时边界 |
| --- | --- | --- | --- |
| 人员/仓库/设备权限版本 | 人员管理与版本撤会话已有；TEMP权限无 | 最新权限目录、grant/disable变更、资格签发/续期的共同规则；证明撤权后停止新增且事实保留 | 可做设计；不能用界面按钮模拟权限闭环 |
| 条码可用状态流 | 尚未完成；准备包available只是截点 | 快照/基线配对、业务版本/dispatchCycle/blocked/commitment语义、分页/游标、完整激活；本机guard优先且不被refresh解锁 | 可以隔离验证TEMP建单原型，不能称安全离线全闭环已交付 |
| 订单状态流与独立恢复保护 | 已实现源码/本机记录，待现场验证 | 增量乱序/旧版本、映射目标停止/完成如何定位到TEMP本机来源；提供可消费事件/详情接口 | 映射必须另查签名证明；不能只看到COMPLETED就放行 |
| 受控续期 | 正式assignment已实现 | 临时资格续期的请求/历史/签名规则；同原请求重试固定期限与原未决输入处理 | 不重置创建时间续48h；过期只读/恢复 |
| 通用审计补传 | 未完成 | 原设备事件连续确认、缺口恢复、幂等、单单证据与全设备覆盖区分 | 临时原事实必须本机留存；不得提前清审计 |
| 持久任务/重试 | 现有上传Worker；通用后台任务仍待补 | 唯一任务、租约/fencing、手动与自动互斥、服务重启恢复、有限失败接手、Retry-After | 不复制新任务框架；不把测试假任务当持久上传 |
| 待审30分钟提醒 | 未完成 | 以服务端首次待审时间/决定版本去重，权限范围正确；提醒失败不改业务状态 | 手动查询仍需可用；全量R06交付明确提醒依赖状态 |
| 安全清理/留存 | 未完成 | TEMP草稿/在途/未知/待审/拒绝未处置/guard/资格/映射/正文/墓碑引用保护，90天/≥5年 | 不上线自动删除方案 |
| APK安全升级 | 原目录仅新增设计，会话1自行安排 | safe point、安装互斥及迁移检查清单纳入TEMP事实/命令/映射；实际升级证据 | 本任务不改更新方案、不安装/发布新包 |

交接以明确提交号和契约字段为准，不要求先人为完成全部R05才能做某个已授权的隔离编码子项，但共享同步/Room/权限/上传模块动工前必须确认会话1对应改动已稳定可整合，避免两个任务同时改公共结构。本轮不向会话1发指令或接管任务。

## 5. R07 与其他边界

| 场景 | R06 责任 | R07/其他能力与触发条件 |
| --- | --- | --- |
| 拒绝后原人原机保留/减包/全部卸下 | 设计并实现TEMP受控停止、恢复、处置/续作或结束；保锁和实物凭据不可缺 | 不应全部推迟给R07，否则R06拒绝分支不闭环 |
| 原机损坏/丢失或人员换机 | 持久保留source及原身份/序号；提供可核验恢复包，界面阻断接管 | R07逐条来源证明、完整高水位、旧授权撤销和新机激活；不是PC换人能力 |
| 同码他单已成功、错车出库 | 本单不确认、不抢占、不修改旧周期 | R07错误出库/账务更正，核实实物后决定后续资格 |
| 退货后同码再出 | 不删除历史guard，不把sku重现当可用 | R07收货凭据、反向账务、dispatchCycle+1，R05传播状态后新输入引用新周期 |
| 已成功临时单更正、关联到已有成功单 | 只读原结果，显示后续处理要求 | R07原结果+调整+当前净值；不把两张已有扫码单自动合并 |
| 已出库重量更正 | 保留原sku/重量文本和签名回执 | R07更正凭据；R08按原出库日最新净结果导出 |
| 恢复代次变化/服务器灾后 | 独立阻断、原代次证据留存 | R09恢复UUID、重建/对账、解除恢复保护；R06不增加“一键解除” |
| Excel/PDF | 双号、原设备/序号/时间质量和映射为R08留字段 | R08生成下载；R06成功不依赖生成PDF，不提供PDA打印 |

## 6. 进入编码的具体条件

本轮仅设计完成；下列是下一编码任务的准入检查，不是当前自动执行计划：

1. 用户明确启动某个 R06 编码范围；本任务的普通“继续”仅核对/修订这些设计，不扩大授权。没有必要重问已确认业务规则。
2. 记录 R05 可整合提交，重新核对本分支与最新development差异，明确共享文件归属；迁移/契约版本以整合时实际最新值分配，不预占V22/Room10等编号。
3. 完成来源与本机容器适配的字段对照：正式旧 assignment 容器、TEMP空assignment、原originAssignment、canonical映射、冻结回执兼容和旧APK遇到新类型的拒绝路径。先验证迁移前后原数据，不直接改公共Room尝试。
4. 将 [专项设计§7](DESIGN.md#7-接口提案不是正式-openapi) 的接口提案收敛为真实schema/控制头/回执测试向量；决定 TEMP准备包与R05条码流基线的配对、审计确认/任务恢复，以及canonical映射的签名版本。提案不可先写进OpenAPI当已实现能力。
5. 建立隔离MySQL、合成库存、受控签名密钥和权限角色组合；不复制真实业务库/密钥到worktree。准备旧Room9及以后R05最终版本的非空迁移夹具。
6. 首批可独立编码范围建议为“临时资格/准备 + 稳定草稿/计划 + 原来源适配”的内部验收；只有提交、确认/短装/关联、拒绝处置及恢复全部通过后才对用户开放完整闭环。中间阶段明确开发中，不用模拟结果放行。
7. 交付前具备M71、两台PDA、PC与异地网络条件，按验收方案记录APK/后端/迁移/契约版本及证据；代码完成、本机通过、真机、异地、生产分开。

## 7. 真正未决与建议默认

| 项目 | 当前建议 | 收口责任/时间 |
| --- | --- | --- |
| R05最终字段与交接提交 | 复用R05命名空间、恢复保护、任务框架，不另立并行同步 | 技术整合前，只读核对会话1实际交付 |
| 容器及迁移细节 | localWorkId分离真实授权；旧容器回填原assignmentId，旧正文不变 | 编码前迁移设计/非空夹具验证；没有业务选择需重问 |
| 映射/决定/资格schema版本 | 新TEMP用途明确，旧FORMAL兼容，不占版本号 | 契约实现时分配并用测试向量核对 |
| 疑似重复时间窗 | 默认前后48h，可配置扩大查询；只做候选，人工裁决 | 使用合成场景验证后现场校准；不阻塞设计，不声称已确认参数 |
| 指定授权人员/设备 | 默认不给全体操作员确认权，管理员按名单授予能力角色 | 测试可用隔离账号；真实投用前由业务管理员配置 |
| R07未交付场景可用时间 | 阻断换机/已出库更正，保留证据与占用 | 随R07验收；不能给出虚构可用日期 |

本轮已能给出推荐方案，不存在需要用户重复回答“是否允许短装/是否先放车”等问题。独立权限、自批限制、48小时和保锁底线均已写成实施验收条件。
