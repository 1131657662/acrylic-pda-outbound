# R06 M2b 写阶段锁图与最小接入方案

日期：2026-09-20。只读代码基线：`90581375128ee426fcf979a76eb44f4bb7734fef`，契约 0.27 / Flyway V33 / Android code20 / Room17。**本文是实施准备，不是并发验证报告；没有修改业务代码或运行数据库。M2b 写阶段阻断未关闭。** M2b-1 只读候选不因此停工。

依据：[M2b 方案](../../R06_M2B_IMPLEMENTATION_PLAN.md)、[Q01～Q16](../../OPEN_QUESTIONS.md)、[V2.3](../../系统设计方案与开发计划-V2.3.md) §6.6、6.8、9.6。原命令与证据见[配套契约](COMMAND_EVIDENCE_CONTRACT.md)，批次、责任和待执行场景见[交接](HANDOFF.md)。

## 1. 读图方式

`S` / `X` 分别指共享/排他行锁；`R` 是普通一致性读，**不是锁**；`FK-S` 是子行插入/更新检查父键时可能取得的共享锁；`DML-X` 是写入产生的排他锁。箭头表示同一事务内的先后，不表示每条路径都会经过所有节点。不存在行的查询在 RC 下不能当作稳定的“空范围锁”。唯一键、现有仓库互斥和锁后重查仍是必要条件。

表中“事实”由当前源码及迁移确认；“风险”是满足相应竞争前提时的静态等待环；“待验证”必须用真实 MySQL 两连接和最终持久状态证明。多表 `FOR UPDATE`、外键、唯一键冲突可能扩大锁集合，执行计划中的精确顺序本轮没有实测。

| 符号 | 当前资源及含义 |
| --- | --- |
| E | `server_state(id=1)`；`lockWritable` 取 S 并校验 NORMAL / 数字代次字符串 |
| G | `user_management_guard(id=1)`，当前人员管理及 TEMP 准备使用 X |
| U / W / D / H | `app_user` / `warehouse` / `device` / `pda_session` |
| T / C | `temporary_work_origin(creator_device_id,client_order_id)` / `submission_scope` |
| O / L / S | `outbound_order` / `order_submission_slot` / `submission`；此表 S 单独作资源符号时指 submission，锁模式仍写为 S-lock 或 X |
| A | `order_assignment` / `offline_authorization`，或 TEMP 原 `temporary_authorization` / 激活确认 |
| B / P / K | `bale` / `inventory_position` 及快照行 / `bale_commitment` |
| N / J | 全局北京时间日期 `order_number_counter` / `background_job` |

## 2. 当前入口的真实路径

入口鉴权的普通读仅列为前检。`@Transactional(READ_COMMITTED)` 简记 RC；没有显式 isolation 的 DEFAULT 不能在文档中擅自写成 RC，实际运行值须验收记录。普通 `@Transactional` 为 REQUIRED，嵌套调用加入外层事务，内部声明不会重新选择隔离级别。

| 入口 / 事务 | 当前顺序及证据 | 缺口或复用边界 |
| --- | --- | --- |
| 人员 create/update/password，RC | `UserAccess.lock`：前检 R → E S → G X → 权限 R；`UserStore.update`：U DML-X → user_role/user_warehouse 删除/插入（U/role/W FK-S）→ 按 user 撤销 H DML-X；回执再引用 U/W | 不是“G 后已经锁住仓库”。`UserAccess` 不锁 W；仓库父锁来自 grants/回执 FK。新方案必须包含新旧授权仓库集合与目标用户 |
| TEMP baseline/prepare/ack/原请求恢复，RC | `TemporaryAccess.lock`：前检 R → E S → G X → `PdaAuthLocks.device`：W X → D X → 会话/权限 R；后写 A/准备包/ack | G 已在 W 前。`PdaSessions.recheck` 是普通 JOIN 查询，不是会话 X；A 写入还引用 U、D、库存、政策父键 |
| 设备启停/临时准入，RC | `MutationAccess.lock`：前检 R → E S → W X；随后 `DeviceStore.find(...,true)` D X → D 更新 → 按 device 撤销 H；回执/审计 | 当前没有 G 或锁后完整 Web 鉴权；`temporaryOrder` 另查 manage 权限。不能把当前前检当作原子撤权屏障 |
| PDA login，RC | W X → D X → 用户/授权 R → challenge 更新 → H 插入（U/D FK-S）→ D last_seen 更新 | 当前没有 E/G，认证是 R09 独立许可能力，不可简单套普通 NORMAL-only 门禁 |
| PDA refresh/logout，RC | refresh：W X → D X → `PdaSessions.find` 的 H JOIN U `FOR UPDATE` → challenge → H 轮换 → D；logout：W X → D X → H 撤销 | JOIN 可能同时锁 U/H，不保证只锁 session，也不能臆测 JOIN 内两表顺序。人员更新 U 后可能等待 W |
| 短码配对审批/确认，RC | `PairingAdministration` 经MutationAccess E/W → pairing行X；`PairingRegistration.operate` 先challenge X、随后E S，已批准时W X → pairing行X → 批准人权限R → 新D插入（批准人U FK-S） | 配对确认是额外W→U边，不能漏掉；challenge→E也是实际次序。新的日常身份闭合应在事务入口先提示读challenge/approval，再按E/G/U/W取锁并重新验证challenge；不在已持W后补G |
| 正式建单/修订/取消/发布，RC | `MutationAccess` E S → W X；已有单 O X → 幂等查询/版本核对 → publication/receipts/outbox 写；新建单 W X → N 插入/锁 X → 新 O/修订（U/W/库存等 FK） | 无 G；`OrderService.revise` 的 READY 分支也发布，PC start(DRAFT) 也嵌套发布。重复候选最终化屏障须覆盖这些旁路 |
| 正式 PDA claim/prepare/renew，RC | `PdaAccess.lock`：前检 R → E S → W X → D X → 权限/会话 R → O X；claim 插入 A（U/D/订单/政策 FK）；renew 还锁当前 offline_authorization X | 没有显式 G/U/H。`PreparationAccess.lock` 再检版本；这些检查不等于对人员修改持锁互斥 |
| 正式提交整包/分块/取消，RC | `SubmissionAccess.pda` E S → W X → D X → 权限 R；`SubmissionStore.lock`：已有 C X → O X → S X；新 C 插入先触 O FK-S，再显式 O X；新 S 引用 U/D/A/C；current 和 L 插入/更新在后 | L 当前依靠 O/仓库互斥，并非在所有入口显式先锁。不能误写成现有代码已完整实现 V2.3 的活动槽锁序 |
| TEMP 提交整包/start/abort，RC | 同上 E/W/D；`TemporarySubmissionControls`：T upsert X 与 A FK-S → C X/插入 → 若 canonical 非空 O X → S X/插入 → C.current 更新 | 不经过 `TemporaryAccess`，所以没有 G；资格到期/准入变化不抹掉历史。原身份/来源仍须验证；首轮只支持 round=1 |
| TEMP 完整正文接收，加入上行 RC | 原控制已锁 → 正文验证/耐久文件 → `TemporaryFormalization` N X → 新 PDA_TEMP O/修订 → C.canonical 更新 → 新 L → payload/capture/event → B/P/K → REVIEW_REQUIRED | 仅待审保留，无 TEMP 最终记账。新 O 是本事务创建；不能把这个特例推广为关联已有目标时随时追加 O 锁 |
| 分块 chunk/finish、批次归档，RC | `SubmissionAccess` E/W/D → `lockExisting` C X → 有目标才 O X → S X → upload/chunk/payload 或 archive；finish 调 `accept` | ABORTED/REJECTED 不能重新接收；TEMP 归档当前明确拒绝。`lockExisting` 不重写原来源 |
| 正式 Web approve/reject，RC | `SubmissionAccess.web` 前检 → E S → W X → 再鉴权 R → C X → O X → S X → review 插入（actor U FK-S）→ reserve / accounting | 当前明确拒绝 TEMP；不能只去掉 TEMP 拒绝或复用单个 approved 布尔值 |
| PC start/writer/record/freeze/review/resume，RC | `MutationAccess` E S → W X → O X → work/命令；record/freeze B X → P（JOIN）X → K X；review/resume 在 O 后调用 `requireOperator` U S-lock；相关回执也引用 U | `PcStore.required` 本身是 R，work DML 的互斥目前由 O/W保证；不能把 R 标为 work X。PC 中途嵌套 access/publish 将来不得在 O 后首次补 G/U |
| PC 异常处置/交接，RC | E S → W X → O X → `PcExceptionChecks.permission` 原/目标/批准人员 U S-lock → B/K 等处置 | 用户集合可能多于当前操作者，实施时要在 W 前收齐；目前存在 W→U 边 |
| 正式召回/恢复/续作，RC | Web E/W；PDA E/W/D；`RecallStore.lockOrder` C X → O X；再 change_request X、recovery/旧 S、B/K；`ContinuationApproval` 在 C/O 前经 `ContinuationAuthorization` 再 W/D → 原 U X | 仍是正式 assignment 路径，不支持无 canonical TEMP。续作新授权和处置写不可通过补假 assignment复用 |
| 审计上传/归档，RC | E S → W X → D X → 身份 R → T upsert → event（原 U/D/来源 FK）→ checkpoint X / archive | FORMAL/TEMP 同原设备连续序号；不能拆流。归档 restore 仍受普通 E 门禁，不等于 RECOVERING 证据入口 |
| 库存/映射/政策发布，主要 RC | `MutationAccess` E S → W X → mapping/product/snapshot/bale/position DML → warehouse 激活/outbox；导入写 `ImportWriter` 为 DEFAULT，E→W | 批量 DML 与 JOIN 的内部锁序未证明。当前 W X 将同仓上传/PC/发布串行；不得为并行吞吐擅自移除 |
| R08 enqueue/retry，producer RR / retry DEFAULT | E S → U X → 锁后 Access 首次普通读 → 原键/容量 → 新快照或原 snapshot → J 插入 | 时钟修复保留此顺序；不能因新锁 helper 在 U 前引入普通 RR 读而使撤权变旧快照。本批不改 R08 |
| Worker 完成/续租/失败 | `BackgroundJobLeases` RC + REQUIRES_NEW，先 J X；complete 再执行 callback；`ExportJobHandler.publish` callback 才 E S | 实际存在 J→E，未统一到 R09 提案 E→J。claim/recoverExpired/renew/fail 本身没有恢复代次门禁；只有原租约 token 保护，不能声称全 Worker 栅栏完成 |

补充：`ServerState.lockWritable` 自身无事务注解，锁持续性依赖调用者事务；不能把它挪到事务外“先调用一下”。`BackgroundJobStore.enqueue` 加入当前事务；外层 RR 不因该方法标 RC 而变成 RC。只读审阅及候选没有任何上述业务写锁的新增义务。

## 3. 外键与唯一约束也属于锁图

| 实际迁移 / 子写入 | 父键及锁影响 |
| --- | --- |
| [V1](../../../server/src/main/resources/db/migration/V1__identity.sql) user_warehouse / user_role | grants 重建发生于目标 U X 之后，引用 W/role；U→W 不能从图中删掉。auth_version 是用户版本，不自动保护任何未来绕开人员服务的角色权限写者 |
| [V7](../../../server/src/main/resources/db/migration/V7__devices_sessions_and_assignments.sql) pda_session、assignment、offline_authorization | H insert 引用 U/D；assignment 引用 W/O/revision/publication/D/U/snapshot/policy；授权引用 assignment/U/D。先锁 W 再插入带 U FK 的子记录也会等待 U |
| [V9](../../../server/src/main/resources/db/migration/V9__formal_submissions.sql) C/S/L | C.source_order→O；S→C/A/D/U；C.current→S；L→O/C/current S。这是实际循环引用结构，应先锁既有对象，再按依赖插入新对象并设指针；“一律先插scope”不是无条件安全规则 |
| [V30](../../../server/src/main/resources/db/migration/V30__temporary_preparations.sql)、[V31](../../../server/src/main/resources/db/migration/V31__temporary_audit_origins.sql)、[V33](../../../server/src/main/resources/db/migration/V33__temporary_submission_sources.sql) | T→TEMP authorization；C→T；C.canonical→同仓 O；S→TEMP authorization。TEMP `source_order_id`、assignment 和 nullable canonical 的 CHECK 均保留，不通过占位行绕过 |
| [V4](../../../server/src/main/resources/db/migration/V4__inventory_snapshots_and_mappings.sql)、V9 包/库存/占用/结果 | P→B/snapshot_bale；K→B/O/S 等；dispatch→B/O/S/capture；ledger→dispatch/B；result→O/S，O.current_result→result。逐包顺序还要包含快照 JOIN 锁及既有处置/续作父引用 |
| [V10](../../../server/src/main/resources/db/migration/V10__controlled_recall.sql)、[V11](../../../server/src/main/resources/db/migration/V11__controlled_continuation.sql)、[V13](../../../server/src/main/resources/db/migration/V13__physical_disposition_evidence.sql) | 召回引用 O/A/人员；续作引用 C/O/旧 S/D/恢复包/新 A/人员；处置引用恢复包/O/B/批准人/应用 S。不能只锁本次请求行便判断权限、轮次与占用稳定 |
| 唯一索引 | T 二元来源、TEMP C 二元来源、正式 source_order、C+round、L.order、capture/event ID、bale commitment、dispatch 的周期唯一性、命令键和 N 日期会产生写入等待；RC 下读到不存在不能代替这些裁决 |

当前逐包子序：`SubmissionStock` / `PcStock` 为 B→P→K，`RecoveryStock` 为 B→K，`ContinuationChecks` 为 B→K→P，处置也在 B 后改 K。P/K 的反向边不是单独的已证实死锁：同一个 B X（及 W X）已先串行。实施建议统一为每个按规范 UUID 排序的 B→P（需要时）→K，并证明任何批量写和多包路径都遵守父锁前置；没有这个证明不能去掉现有 W 互斥。

## 4. 已识别的反序与安全边界

| 编号 | 静态事实与形成等待环的前提 | 当前结论 / 修复责任 |
| --- | --- | --- |
| L01 | 人员修改 G→U X→W FK-S；PC review/resume、PC交接为 W X→U S-lock；续作为 W/D→U X | 可以排出两连接等待环；未实跑死锁。共享写前缀执行者须同批纳入人员、PC、续作，不能只修新TEMP服务 |
| L02 | PDA login 持 W/D 后插 H 的 U FK-S；refresh 持 W/D 后 H JOIN U FOR UPDATE；人员修改先持 U 再等 W/撤 H | 会话路径也是 L01 的边，且 JOIN 实际锁集须取证。鉴权执行者须在最外层收集 U/D/H，不得在 `PdaSessions` 深处首次取 G |
| L03 | 新TEMP写若在 `SubmissionAccess` 之后补 G/U，会成为 W/D→G/U；现有人员/TEMP准备是 G→W | 不能局部补锁；要在 W/D 之前接入统一入口。当前没有实施新TEMP写，所以不是本轮引入运行缺陷 |
| L04 | 映射/关联要持两个 C/O/L；正式发布/claim/PC入口目前只锁 O、不查重定向来源 | 新关联必须锁完整集合并改各入口检查 active slot/redirect；否则可能产生第二作业入口。不能仅靠新增关联表唯一键保护业务 |
| L05 | 先查候选后普通写；另一事务同时 create/revise/publish/PC start(DRAFT) 新正式单 | 现有 W X 可作为同仓共同屏障，写决定和最终化必须在同锁内重查候选全集。M2b-1 页面查询只是信息，不产生排除效力 |
| L06 | 来源/当前目标/原操作人提示读后改变，服务已锁一部分 C/O 才发现新成员 | 不持锁逆序补集合；整事务回滚，用同命令重新读取锁集合，再核对客户端期望版本。旧命令上下文不适用则明确失效，不自动改正文 |
| L07 | Worker J→E；未来恢复协调者若持 E X 后核对/更新 J，将与之构成反序 | 交 R09 共享屏障批处理。claim/renew/fail/complete 等须在各自事务内先 fence 后 J；不能在 callback 后补门禁，不能把今日租约测试算灾后验证 |
| L08 | N 是跨仓日期锁；当前正式创建/TEMP正式化都已持 W 后取 N | 目前两个事务只取本仓、共享 N 等待本身无环；关联/恢复不得持 N 后回头取别仓、用户或来源。正文前ABORT绝不调用 N |
| L09 | 配对确认W→批准人U FK-S，且consume先challenge X后E S；管理员审批经E/W→pairing行 | 日常闭合纳入配对确认的身份前缀；challenge重排要保留原签名、原挑战一次消费及同事务回滚。恢复认证白名单仍留R09独立批，不顺便开放新配对 |

RR 的另一类风险不表现为死锁：锁前普通读建立旧快照，锁后再查也可能读旧权限。新业务写固定 RC 并锁后鉴权；保留 R08 已有 RR 入口的特殊顺序。对 R09 的外部控制文件“提交前再读一次”也不能代替持锁和停写排空。

## 5. 最小可实施统一方案（提案，尚未编码）

比较三个选项：仅在新TEMP服务补用户锁无法闭合现有反序，不能选；一次性替换全项目认证/事务框架改动过大，不作本批前提；**推荐先闭合与TEMP写共享人员、仓库、设备、来源、库存的入口集合，保留已有粗粒度仓库互斥。** 首版复用 G 单行锁；它会增加跨仓写串行，验收要记录等待与事务时长，不能承诺吞吐提升，也不把性能技术选择变成业务车次限制。

最外层 REQUIRED RC 写事务的建议顺序：

1. 从认证主体和服务端只读来源查出锁集合提示；只作定位，不作最终授权，不改业务。集合包含实际操作者、需要核验的原操作员/交接目标、原/目标仓库、设备、会话、两边来源及订单。客户端 ID 只作为受校验的定位参数。
2. E S（未来由 R09 的同事务能力门禁承担）→ G X → 所需已有 U X 按 ID → 所需 W X 按 ID → 所需 D X 按 ID → 本动作要轮换/撤销/据以授权的 H X 按 ID。取得父锁后再次验证身份、用户/设备版本、权限和仓库；禁止把原操作者替换成批准人。用户及会话集合锁后不一致则回滚重取。
3. 需要改变的原资格/来源 T、已有 C 按来源稳定键/ID → 所有 O 按 ID（含已有 L 按 order ID）→ 相关已有 A、S 按稳定 ID → 本动作的原命令/决定/恢复对象 → 按 B ID 的 B/P/K → 审计/outbox/派生任务。已有只读资格不必无理由升级 X，但撤销/替代链须纳入集合。
4. 新 C/S/L 的插入依赖另行处理：先锁所有既有父对象和目标，插 C（current 为空）、插 S、再更新 C.current/插 L；不存在的 L 由已经持有的 O/W 和唯一约束守护。若唯一键输给已存在来源，整事务回滚后读取并比对原身份，不能 REPLACE 或覆写。新TEMP正式化的新 O 可在 N 锁后创建；关联已有 O 不享有此特例。
5. 保留 V2.3 的业务锁层次 **C→O（含既有L）→S→B**。若新 FK 或后续共享实现迫使反向取得已有父锁，先改锁集合获取位置，不以“数据库会处理”忽略反向边。N 是新订单分配附加锁，不反向获取此前资源；所有需要的既有 U/W/D/C/O 应在取 N 前确定。
6. 在锁内重读 canonical/current/round/mapping、完整候选、目标空白状态、原资格/激活证明、权限与包状态；任一集合/版本改变，拒绝过期决定。业务状态、命令原回执、决定效力、审计及 outbox 同事务；签名先准备成功，再提交记账。提交失败原ID仍可恢复。

G 只有参加者遵守才有意义。下表是首个“闭合批”必须核对/接入的范围；不表示本轮已授权同时修改所有文件，也不是每个入口都要强制取所有类型行锁。实施负责人由总调度指定且同一时间只有一个共享核心写者。

| 范围 / 责任文件 | 必要动作 |
| --- | --- |
| 人员：UserAccess/UserService/UserStore | 最外层收集 actor/target U 及新旧 W；G 已有，维持后续 DML/FK/H 全在统一前缀之后；任何角色/授权新增写者也要参加 |
| 公共 Web：MutationAccess、DeviceService、OrderService/OrderReleaseService、PublicationService、PcWorkService/PcFinalization/PcException* | G/U 在 W 前；RC 下锁后重新 Access；PC 原人/接续人先查集合，不能在 O 后才发现未锁 U；nested publish 仅重验已持集合 |
| PDA：PdaAuthentication/PdaSessions、PdaAccess/PdaAuthLocks、TemporaryAccess、SubmissionAccess、RecallAccess、ContinuationAuthorization | 由最外层入口先取 G/U，再调用 W/D；helper 不偷偷补前缀。refresh JOIN拆分为明确 U/H锁或证明已有父锁齐全；认证恢复能力由 R09 单独定义 |
| TEMP：TemporarySubmissionControls/TemporaryFormalization、SubmissionStore、将来的 decision/evidence 模块 | 固定 T/C 与 nullable canonical；双来源集合、L归属和原命令台账；不更改原控制身份、原正文或原签名 |
| 邻接：PdaAuditService/AuditArchiveService、Preparation/Assignment/Renewal、ImportWriter、PairingRegistration/Authority/Challenges | 这些入口同样会取 W/D、写 U/D FK/来源；闭合批不能遗漏，DEFAULT隔离及配对challenge→E重排单列核验 |
| Worker/恢复：ServerState、BackgroundJobLeases/Worker、各 callback | 单独共享接入批修 E/J 顺序及所有 lease操作fence；R06决定不直接调用 REQUIRES_NEW job 完成作为业务原子提交 |

本提案没有给既有 helper 换名或强制抽取通用框架；具体最小补丁由源码执行者按调用图选定。文件名单/验证场景冻结后才能并行分配 Android 契约消费者；不能让两个执行者分别重写同一 access/store。

## 6. 复核源码索引

上述方法可在固定提交逐项定位。没有从其他工作树的未提交业务代码推导当前实现。

| 主题 | 实际源码 |
| --- | --- |
| 恢复与人员 | [ServerState](../../../server/src/main/java/com/acrylic/outbound/common/ServerState.java)、[UserAccess](../../../server/src/main/java/com/acrylic/outbound/users/UserAccess.java)、[UserService](../../../server/src/main/java/com/acrylic/outbound/users/UserService.java)、[UserStore](../../../server/src/main/java/com/acrylic/outbound/users/UserStore.java) |
| 公共入口 | [MutationAccess](../../../server/src/main/java/com/acrylic/outbound/inventory/master/MutationAccess.java)、[WarehouseBaseline](../../../server/src/main/java/com/acrylic/outbound/inventory/master/WarehouseBaseline.java)、[DeviceService](../../../server/src/main/java/com/acrylic/outbound/devices/DeviceService.java)、[DeviceStore](../../../server/src/main/java/com/acrylic/outbound/devices/DeviceStore.java) |
| PDA身份 | [PdaAuthLocks](../../../server/src/main/java/com/acrylic/outbound/pda/PdaAuthLocks.java)、[PdaAccess](../../../server/src/main/java/com/acrylic/outbound/pda/PdaAccess.java)、[PdaAuthentication](../../../server/src/main/java/com/acrylic/outbound/pda/PdaAuthentication.java)、[PdaSessions](../../../server/src/main/java/com/acrylic/outbound/pda/PdaSessions.java) |
| 配对邻接 | [PairingAdministration](../../../server/src/main/java/com/acrylic/outbound/pairing/PairingAdministration.java)、[PairingRegistration](../../../server/src/main/java/com/acrylic/outbound/pairing/PairingRegistration.java)、[PairingAuthority](../../../server/src/main/java/com/acrylic/outbound/pairing/PairingAuthority.java)、[PairingChallenges](../../../server/src/main/java/com/acrylic/outbound/pairing/PairingChallenges.java) |
| 领取/准备/续期 | [AssignmentService](../../../server/src/main/java/com/acrylic/outbound/pda/AssignmentService.java)、[AssignmentStore](../../../server/src/main/java/com/acrylic/outbound/pda/AssignmentStore.java)、[PreparationAccess](../../../server/src/main/java/com/acrylic/outbound/pda/PreparationAccess.java)、[AuthorizationRenewalService](../../../server/src/main/java/com/acrylic/outbound/pda/AuthorizationRenewalService.java)、[TemporaryAccess](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporaryAccess.java) |
| 提交控制 | [SubmissionAccess](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionAccess.java)、[SubmissionStore](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStore.java)、[SubmissionService](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionService.java)、[SubmissionChunks](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionChunks.java)、[TemporarySubmissionControls](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporarySubmissionControls.java) |
| 待审/记账 | [TemporarySubmissionIntake](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporarySubmissionIntake.java)、[TemporaryFormalization](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporaryFormalization.java)、[SubmissionReview](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReview.java)、[SubmissionFinalizer](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionFinalizer.java)、[SubmissionStock](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStock.java)、[SubmissionAccounting](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionAccounting.java) |
| 正式及PC | [OrderService](../../../server/src/main/java/com/acrylic/outbound/orders/OrderService.java)、[OrderReleaseService](../../../server/src/main/java/com/acrylic/outbound/orders/OrderReleaseService.java)、[OrderNumbers](../../../server/src/main/java/com/acrylic/outbound/orders/OrderNumbers.java)、[PcWorkService](../../../server/src/main/java/com/acrylic/outbound/pc/PcWorkService.java)、[PcFinalization](../../../server/src/main/java/com/acrylic/outbound/pc/PcFinalization.java)、[PcStock](../../../server/src/main/java/com/acrylic/outbound/pc/PcStock.java)、[PcExceptionChecks](../../../server/src/main/java/com/acrylic/outbound/pc/PcExceptionChecks.java) |
| 召回/续作/处置 | [RecallAccess](../../../server/src/main/java/com/acrylic/outbound/recall/RecallAccess.java)、[RecallStore](../../../server/src/main/java/com/acrylic/outbound/recall/RecallStore.java)、[RecoveryPackages](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryPackages.java)、[RecoveryStock](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryStock.java)、[ContinuationApproval](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationApproval.java)、[ContinuationAuthorization](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationAuthorization.java)、[ContinuationChecks](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationChecks.java) |
| 库存/审计/Worker | [PublicationService](../../../server/src/main/java/com/acrylic/outbound/inventory/master/PublicationService.java)、[SnapshotWriter](../../../server/src/main/java/com/acrylic/outbound/inventory/master/SnapshotWriter.java)、[PdaAuditService](../../../server/src/main/java/com/acrylic/outbound/audit/PdaAuditService.java)、[PdaAuditOrigins](../../../server/src/main/java/com/acrylic/outbound/audit/PdaAuditOrigins.java)、[AuditArchiveService](../../../server/src/main/java/com/acrylic/outbound/audit/AuditArchiveService.java)、[BackgroundJobLeases](../../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundJobLeases.java)、[ExportJobHandler](../../../server/src/main/java/com/acrylic/outbound/exports/ExportJobHandler.java) |

本轮结论仅为静态锁图和可执行修复建议。真实锁等待、死锁受害者/回滚、撤权线性化点、批量DML执行计划和新旧路径组合尚未验证，下一批按 HANDOFF 场景逐项取证。
