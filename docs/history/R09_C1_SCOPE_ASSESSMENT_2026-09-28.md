# R09 C1 初次NORMAL双侧登记：范围评估与停报（2026-09-28）

## 1. 判定与本次执行边界

**判定c：需要独立初次NORMAL登记组件；同时涉及b，即R09生产代码的调整。不能按a“仅接线/补验”继续。** 已按用户“若需要改生产代码或新组件，先报告并等授权”的边界停止实施，未改代码、未启动数据库/服务、未新增测试或分配attempt。

输入D:/acrylic-pda-outbound、codex/development、bf568a0；主树开始时干净、与origin一致。单助手，无在途测试，检查的专用测试端口无监听，系统3306未动。只在本树提交评估及看板/欠债/索引等文档；不写旧验证树，不配置A3 profile，不启动C2。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

本结论是**设计定义与既有组件路径的静态差异**，不是本轮真实HTTP故障，也不声称线上已经发生越权。旧组件不是Spring bean、无生产路由，返回`ordinaryWritesAllowed=false`；但其行为不足以满足本次C1验收定义。

## 2. 定义与源码证据

[APPLICATION_ADMISSION.md第23行](../parallel/R09/APPLICATION_ADMISSION.md#L23)要求：

> 建立明确的初次NORMAL登记协议及DB对应证据。初次初始化不能沿用灾后RELEASE_READY后开放能力；缺两侧一致提交证明不得放行。已有闭合恢复记录保持只读/受控状态，生产释放端点仍不存在。原安装、原数字/UUID映射、原请求和审批边界不能重新生成或猜测。

| 核查项 | 直接证据 | 对C1路径的影响 |
| --- | --- | --- |
| 库外协议能否表示初次NORMAL | [Python control_model](../parallel/R09/ops/control_model.py#L11)及[Java RecoveryControlCommand](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlCommand.java#L22)仅接受QUIESCING→RECOVERING→VERIFYING→RELEASE_READY；初始必须QUIESCING。[ControlStore.initialize](../parallel/R09/ops/control_store.py#L17)亦作同样约束 | 没有可直接复用的“已有NORMAL基线初次登记”库外写入路径；不能通过把phase改名或伪造灾后链来补验 |
| DB绑定/映射来自哪里 | [RecoveryClosedProjection](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryClosedProjection.java#L45)首次绑定要求revision1/QUIESCING；[RecoveryProjectionRows.append](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryProjectionRows.java)把server_state设为RECOVERY_PENDING；进入RECOVERING还改变恢复代次映射 | 现有绑定工具属于关闭恢复链。正常基线登记不能为了适配该工具而虚构一次灾后换代 |
| 登记器真正接受什么 | [RecoveryNormalRegistration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryNormalRegistration.java#L53)要求投影终态RELEASE_READY；新登记要求RECOVERY_PENDING，再调用enableNormal | 现有类名虽称initial NORMAL，实际路径是关闭链后的状态转换；并非在独立正常基线上登记两侧事实 |
| 缺外部意图时怎样 | [RecoveryNormalIntent.load](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryNormalIntent.java#L24)缺文件返回null；登记器第51、58–62行跳过crossCheck，以UNRECORDED_EXTERNAL_INTENT及terminal.hash作替代，仍落登记行并设NORMAL | “库内NORMAL/登记成功”不能作为两侧一致的证明。新C1路径不能保留缺意图成功分支。该行为已在历史验收公开记录，不在本轮自行修 |
| 运行时比对能否独立解决 | [RecoveryAdmissionVerifier](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionVerifier.java#L45)缺意图会拒绝；第68行仍要求RELEASE_READY投影，第72行要求DB为NORMAL；生产源码未见入口调用它 | Verifier拒绝与登记器改变DB状态不能混为同一结论；只把Verifier接上也没有补出独立初次登记协议 |
| DB存储是否已有NORMAL投影形态 | [关闭投影迁移](../../server/src/main/resources/db/migration/V39__closed_recovery_control_projection.sql)的control_phase约束只含关闭四阶段；[登记迁移](../../server/src/main/resources/db/migration/V40__recovery_normal_admission.sql)是冻结登记行，外键依赖安装绑定 | 不能在旧投影表直接塞NORMAL，也不能原地改历史迁移。是否新增独立基线表/字段，须随新协议定稿决定，不在评估中分配迁移号 |
| 旧测试的成功代表什么 | [RecoveryNormalChecks.afterReleaseReady](../../server/src/test/java/com/acrylic/outbound/recovery/RecoveryNormalChecks.java#L14)在关闭链投影后直接调用登记器；第36–43行明确断言“无意图也REGISTERED且DB NORMAL”。Verifier单测用合成视图 | 这些测试覆盖既有关闭链组件行为，不证明本次定义的初次NORMAL。不能照旧测试再跑绿，就将C1标完成 |

[历史attempt90m](R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md)仍是旧组件验收证据；authority_revision变更后真实库复验未绿、旧库checksum隐患继续保留。[入口attempt105](R09_APPLICATION_ENTRY_ACCEPTANCE_2026-09-26.md)验连接/事务且登记行为0，不是C1验收。上一轮C2评估所述“已有组件、真实复验未完成”仍成立；本轮进一步确认：**缺口不止验证，还缺独立初次协议。**

## 3. C1完整范围：需要哪些事实

以下是待授权的最小范围与验收要求，不是已发布协议，也不是对字段格式或业务政策的自行定案。

| 侧面 | 必需证据 / 复用基础 | 本次确认的缺口 |
| --- | --- | --- |
| 库外身份与可信锚点 | 原installationId、原数字/UUID映射及其来源、原请求/请求摘要、明确数据库schema/实例身份和登记目标；独立于DB恢复集的权威根及外部可信checkpoint。可复用[RecoveryControlLocation](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlLocation.java)、路径防替换/规范字节/摘要及底层持久化工具 | 现有NormalIntent只有绑定关闭链头的可选意图读取器，没有独立初次NORMAL事实模型、受控写入器与完成证明。摘要自洽不是身份可信来源，不能用测试随机UUID替代原身份 |
| 库外持久性 | 显式操作、原命令幂等、追加保留、原子发布/落盘、崩溃可判定；最终完成证据必须与DB提交对应，半完成只允许原键继续核对 | 不能用测试中的Files.writeString产生文件就宣称生产登记协议完成。需固化初次登记的跨存储完成判据；是否需要单独库外COMMIT记录随协议审查确定，不直接套用灾后释放流程或在评估中决定新增表 |
| DB事实 | 安装与实际MySQL server UUID/schema绑定、可信数字/UUID对应、冻结登记记录（原请求/摘要、权威版本/摘要与完成证明的对应）、server_state | 已有recovery_installation_binding/recovery_control_epoch/recovery_normal_admission可作存储基础，但写入和读取假定关闭链；初次NORMAL路径及存储区分未实现 |
| 双侧一致性 | 同一安装、DB实例/schema、原映射、原请求、权威记录及冻结登记逐值对应；保留明确的提交阶段，禁止缺侧、回退、换库/换身份后放行 | [RecoveryAdmissionEvidence/Queries](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionQueries.java)目前给Verifier的视图有限，且Verifier依赖关闭投影；需对初次协议重新定义证据视图和一致性校验，不能仅比较两段hash |
| NORMAL状态 | 已证实的正常基线完成双侧登记后，DB保持NORMAL；登记期间普通入口仍不开放，缺侧/不一致不产生“可接纳”结论。初次登记不把RECOVERY_PENDING当替代起点 | DB标志NORMAL与可接纳是不同条件。不得为跑通夹具先把正常基线置为RECOVERY_PENDING、模拟恢复再转回。已有灾后闭合记录必须保持受控，不经C1自动释放 |

该范围不包括C2全入口接线、不修改R05/R06/R07业务、不改变原签名正文/请求/回执、不把UUID改写到已有数字列、不发新作业资格、不开放生产开关。原件或可信映射缺失时必须列为阻塞，不能重新生成。

## 4. 需要新增/调整的文件与必要性（提案，尚未实施）

| 文件/模块 | 建议改动范围 | 为什么必要 |
| --- | --- | --- |
| 新增初次NORMAL库外登记模型、读写/完成证明组件；放在R09模块内，具体类名/文件名设计时确定 | 接受原身份/原命令；显式登记、持久化、幂等和崩溃后核对；与关闭链记录区分 | Python/Java已有控制协议均只支持关闭阶段，没有可接线的初次NORMAL生产写入器。复用底层安全路径、规范字节、持久化/锁工具，不复制整套恢复系统 |
| RecoveryNormalIntent.java | 新路径的证据必须存在且绑定初次基线；缺文件不能作为成功登记输入。旧格式如何只读识别单独明确 | “可选外部意图+关闭链头”不能表达本次要求；不将旧格式静默解释为新协议 |
| RecoveryNormalRegistration.java / RecoveryNormalRows.java | 明确区分初次登记与既有关闭链；新路径只核验可信正常基线，缺侧拒绝，不走RELEASE_READY→NORMAL；记录与重放逐值一致 | 现有入口要求RECOVERY_PENDING，并有缺意图成功分支。仅新增bean或测试端点不能改变这些前提 |
| RecoveryAdmissionEvidence.java / RecoveryAdmissionQueries.java / RecoveryAdmissionVerifier.java | 提供初次基线、实际DB身份与对应提交证据的只读视图，校验缺侧/冲突/回退；保留业务开放能力为false | 现有Verifier硬编码RELEASE_READY；需支持独立初次证据且不能把旧关闭记录放行 |
| R09控制根布局/租约衔接（RecoveryControlHistory.java、RecoveryWriterLease.java、RecoveryLeasedTransaction.java等按设计确认） | 核定新初次证据的隔离布局与实例绑定，复用锁序/UNKNOWN保留；仅做确实必要的适配 | 现有事务租约启动时读取关闭链快照。不能直接用一个新文件绕过闭合链守卫，也不能为C1把所有关闭阶段泛化成开放态 |
| 可能新增Flyway迁移（不预占编号） | 若既有表不能无歧义保存独立基线/完成证明，则新增保留式存储；不改既有关闭阶段CHECK或旧迁移 | 旧表只允许关闭阶段；原V39/V40及既有登记事实必须保留，不通过删触发器/删行/重建旧库来适配 |
| 测试域C1定向IT、真实HTTP探针和隔离运行入口 | 以真实组件验证正常基线和所有缺侧/崩溃情况；HTTP探针只在测试域启动，无生产路由 | C1要真HTTP+真MySQL，但不因此提前接C2或新增生产释放端点。旧RecoveryNormalChecks不作为新语义验收模板，历史结果保留 |

旧的关闭链协议、投影及CLI仍保持只读/受控边界；不简单给`control_model.py`/`RecoveryControlCommand`加一个NORMAL枚举来绕过原限制。ServiceModeStartupNotice只是提示，不是登记实现，本轮不借评估修改共享common代码。

## 5. 工作量与后续最小验收建议

预计**3–4轮**完成独立C1最小闭环（非工期承诺）：首轮固化协议/原件输入/DB存储与崩溃状态并实现库外组件；第二轮DB登记及Verifier衔接；第三轮真实MySQL+真实HTTP正反例、原事实守恒及重启/故障验证；若存储迁移或失败恢复需要补验，再单列一轮。若协议/原件前置不满足，停止而非硬凑2–3轮。本轮仅使用范围评估阶段；新组件、生产改动和可能迁移均未获实施放行。

建议下一次授权按第4节限定R09范围：先完成初次登记协议与最小组件，不含C2、不含灾后释放、不含原库操作或上线。若审查后需扩大共享面，再停报。各实现/文档独立提交推送；attempt从557+执行前查重，当前未分配。

| 验收组 | 最低可接受证据（均尚未执行） |
| --- | --- |
| 正常基线 | 专用非空MySQL的可信NORMAL输入，不绕行灾后链；使用同一原安装/映射/请求登记，库外完成证明和DB记录逐值相等，真HTTP测试入口读到admitted=true，同时生产开放能力仍false |
| 缺侧拒绝 | 分别缺权威/可信锚点/库外意图或完成证明、DB绑定/映射/登记；真HTTP得到不可接纳结果，无自动补建或业务写。即使DB service_mode已经NORMAL，也不能代替缺侧证据 |
| 冲突与回退 | 原键同字节幂等；同键异正文、换安装/DB实例/schema/映射、任一侧回退、损坏或不一致均拒绝；不得重造身份、覆盖历史或猜测原请求 |
| 交叉存储崩溃 | 库外准备后、DB提交前/后、库外完成前/后中断与提交UNKNOWN；无双侧完成不接纳，原键恢复核对不重复写；锁与UNKNOWN沿既有规则保留 |
| 原事实守恒 | 前后比对原安装、全部相关UUID/数字映射、原请求/签名/回执字节及关键业务表；准许变化仅限显式C1登记事实，不伪造关闭投影或灾后换代 |
| 重启与范围 | 重启消费同一可信原件，缺证据启动/准入拒绝；无新增生产路由/默认开关变化；隔离实例排空并保留所有红色证据。真HTTP探针不能冒充C2全应用接线 |

## 6. C2顺序及未验

C1尚未完成，C2继续等待。C1通过后，按[既有入口评估](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)先确定操作租约/旧操作收尾的衔接，再依次B1 HTTP/Session、B2 PDA、B3导入文件、B4提交文件、B5来源证据文件、B6 Worker、B7导出文件、B8渲染进程、B9 Flyway、B10 Bootstrap，最后全入口组合；各批需用户授权。运行时迁移/初始化必须先于业务入口，开发验收顺序不等于启动顺序。

本次验证仅静态源码/历史证据/引用及文档检查、事实块Write/Check；未执行单测、IT、真实HTTP或数据库操作，无新的绿/红attempt。

未验：独立初次NORMAL协议与组件、双侧真实库/HTTP组合、崩溃恢复与原件守恒；旧登记组件后续结构复验、所有C2入口、Linux/跨主机持久性、跨端/现场与生产释放。A3剩余21类、526专项、R07抬头撤销和D范围外组合不变。

**交回用户决定是否授权第4节的新组件及R09生产适配范围。停报依据是本次用户明确硬边界，不是工具或技能要求另行审批。**
