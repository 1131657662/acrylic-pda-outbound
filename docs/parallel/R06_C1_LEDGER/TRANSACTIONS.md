# C1 事务、授权及恢复

本文件是 [DESIGN](DESIGN.md) 的可实施事务方案；当前没有命令执行器或实测事务结论。基线锁事实以 [L1](../../../server/src/main/java/com/acrylic/outbound/common/IdentityWriteGuard.java) 和后续 L2 的已审提交为准，旧 [锁图](../R06_M2B/LOCK_GRAPH.md) 用于定位风险，不能当成所有锁已存在。

## 1. 新命令事务

采用最外层 REQUIRED、READ_COMMITTED 单事务。解析/资源限制及提示读可提前做；提示只定位锁集合，不能授权或证明当前版本。控制器不得在事务提交前向客户端写出成功字节。

1. 先验证当前身份和历史读取范围，并查精确actor/scope/key：已提交键核验原文后走第2节只读回放，不检查旧expected是否仍当前，也不要求当前写权限。原键查询未命中不返回“可以换键”；请求若明确要执行原命令，才进入以下新写分支。从服务器原来源定位actor、原操作员、关联/处置所需人员、仓库、设备、会话和两边来源/订单；客户端值只用于查找。
2. 取 E S → G X → 完整 U 按 UUID 排序 → W → D → 所需 H，保留 W 互斥，消费 L2 统一入口，不在新 C1 内另建一套 access。角色权限写者也必须在 G 内；任何旁路仍存在就不能把权限快照称为原子保证。
3. 同锁内重验当前身份和读取范围，立即再次查询精确actor/scope/key，处理步骤1后另一事务已提交的情况。命中时同字节回放、异正文409；这次查询仍必须在任何写权限/expected判断之前。只有仍未命中才核验本族写权限、Web/PDA auth_version、仓库、设备状态/版本和会话，再锁T/全部C → 全部O/已有L → 相关A/S，遵守已审锁集合。原操作者/目标/来源提示变化时整事务回滚重取，不能持O再补未排序U，不能改原命令。
4. 从数据库核对全部expected及E2集合/验证/处置清单版本，检查source/current/round/目标和原资格绑定。无canonical时跳过O/L并要求nullable字段匹配；不得为台账造订单或assignment。received与declared分开，独立证据不替代已接受正文。
5. 新键在所有既有父锁之后预留request行；唯一竞争失败回滚整个事务，再通过独立只读流程取原结果，不在已失败事务继续查询/执行业务。只读查不到或发现完整性缺边仍报结果未知/损坏，不能自行补半笔台账。
6. 新业务执行器按具体命令复核完整候选、决定职责、E2证据/共同包归属和库存周期，再按原 decisionVersion 条件更新一次。后续包顺序为规范 B ID 下 B/P/K，现有共享记账路径只允许单一执行者适配；台账不自己扣账。
7. 预分配receipt/业务事件ID，按实际决定形成原上下文和完整回执并签名，检查字节及摘要。插入仅引用既有原上下文/证据父对象的receipt，再插入以receipt为父的新决定/映射/处置/续作及应用事件，随后审计及需要的outbox。任何错误抛出回滚，包括RuntimeException签名失败、审计故障、版本冲突或约束异常；业务错误不通过catch后return成功状态使Spring提交半笔事务。不能设计receipt和新映射互为非空FK且指望事务结束才检查。
8. 提交后输出存好的业务回执字节。客户端收到并不表示其已耐久安装；响应丢失/本地保存失败均只查原键。最终放行仍要等待后续成功协议和 Android 验签耐久保存。

步骤1和3的原键查询是只读，不提前锁命令行；来源键/业务锁先于步骤5的新命令行预留，避免两个关联命令先持彼此命令再反向等来源。跨源同键时G/U当前会串行，数据库全局唯一键仍是最终护栏；未来减少粗锁必须重新证明锁图，不借本方案承诺吞吐。

## 2. 回放与重新登录

| 情况 | 返回/下一动作 | 禁止产生的副作用 |
| --- | --- | --- |
| 当前身份有权，同 actor/scope/key/正文已提交 | 核 request→receipt 一一对应、字节/hash/token/原命令，再返回原响应；查询另给 currentEffect | 不重跑版本更新、审计业务事件、签名、扣账或发号 |
| 同键仅理由/期望版本/命令类型/目标不同 | 明确内容冲突；客户端保留原意图，先核对原结果 | 不覆盖旧命令、不自动创建新键 |
| 新键争同 decisionVersion | 一笔提交，另一笔回滚并报告上下文冲突；重取当前状态供人核对 | 不把“业务相同”当作可签第二个成功决定 |
| SQL提交成功、HTTP响应丢失 | 同身份原键查出原字节；HTTP无法判断COMMIT时先查询 | 不把网络失败记为已拒绝或释放 |
| SQL提交前进程退出/签名失败/审计失败 | 新事务查不到已提交键；可以在当前资格仍有效且原上下文仍相同的条件下重试原命令 | 不保存成功台账、版本增量或孤立业务决定 |
| 退出后以同 principal 重新登录 | 新 session 只证明当前访问资格；可查询旧 session 的原回执，旧 sessionId 保持 | 不把 session 放入唯一域，不重生 commandId |
| 新登录导致 userAuthVersion 变化，旧命令未提交 | 原命令返回版本冲突；保留原意图、重新核对后才可创建新命令 | 不用新登录数据替换旧正文 expected 或续期原资格 |
| 换人登录或跨仓 | 按当前权限返回通用无权/不存在，不能用 UUID 取他人原命令 | 不把 actor 改成当前人后回放他人命令；不泄露有无记录 |
| 不知道键是否提交、404、超时或服务不可用 | 显示结果未知并保留资料；可信恢复后继续原键查询 | 404不证明未装包/未出库，不换键重做，不释放 |
| 字节损坏、request/receipt缺边、未知kid或外层/签名不一致 | 报不可核验/完整性失败并保留证据；交恢复核对 | 不重编码补摘要，不用当前密钥重签伪造原回执 |

同键历史回放先鉴权，随后只读，不要求历史 expected 仍等于当前状态，否则映射/轮次变化后会无法取得原证据。只有“原键尚无提交且要新执行”才做当前写授权及 expected 判断。旧 transportRecoveryEpoch 不能突破现有普通写 E 门禁；代次变了可在当前允许的只读能力内查询历史，但原 POST 不自动改 epoch 再执行。

C1首版不新增权限。提案仅限本人原键的读资格为：当前仍具原命令族的执行权限，或具E1既有 `temporary_order:confirm` 证据读权限；两种都要求当前原仓库范围。族别从已存命令取得，不能让查询参数任意声明低门槛族。前者让link/处置等原有执行者取自己的结果，不额外强加confirm；后者让失去该族写权限但仍有证据读资格的人只读历史。两者都不满足则拒绝，未来跨人审计读取另行定义。未命中新命令分支只要求本族真实执行权限，不因查询复用增加额外confirm门槛；回放取得读资格绝不授予重新执行权。

## 3. 原事实和权限绑定

| 回执事实 | 唯一可信来源 / 锁内检查 |
| --- | --- |
| actor/principal、userAuthVersion | 当前认证主体验证后的 app_user；[Access](../../../server/src/main/java/com/acrylic/outbound/auth/Access.java) 重新加载活动状态/auth_version，不用浏览器快照 |
| 权限/角色/仓库集合 | [IdentityStore](../../../server/src/main/java/com/acrylic/outbound/auth/IdentityStore.java) 的真实 user_role/role_permission/user_warehouse；排序后落快照。userAuthVersion不能单独代表未来独立角色修改 |
| 原操作员/原设备/资格 | submission.operator_id、freeze_device_id、TEMP source、temporary_work_origin.authorization_id 和 temporary_authorization 交叉相等；不是当前批准人或当前登录设备 |
| session/device（适用时） | 当前会话及设备锁内真实记录；Web 无 PDA session/device 填 null，不借用来源设备冒充当前设备；不能保存 access token |
| originalContext | 锁内 C/S/A/L/O/候选与计划原事实；命令expected只是条件，必须逐项与服务器读取相等 |
| decision内容与时间 | 实际执行器产生且职责验证通过的内容、服务器事务时刻；不能把客户端 decision、currentWriteAllowed 当作决定 |
| E2证据结论 | 不可变证据清单及其验证记录、原控制和审计闭包；客户端完整性声明、空数组和E1已知COUNT均不能证明完整 |

角色/权限定义修改即使不增 auth_version，也会影响当次实际权限查询；未来增加角色写入口时必须同 E/G 前缀并递增受影响的授权版本或提供独立权限定义版本，且锁内重新算完整权限集合。只有快照存储而无角色写锁参与仍存在撤权竞态，不能用 C0 的合法签名测试代替 C13。

正常确认只需已确认的真实权限和业务条件，不要求 reason 非空或另一个审批人。短装批准与 TEMP 确认分别检查，普通 confirm 不能批准短装，review 不能独自确认 TEMP，整单0包不能出库；异常自批例外未配置不默认放开。排除/link/reject/处置/续作的权限和理由沿已确认业务规则逐族实施，不从 C0 的样例校验扩张授权。

## 4. E2来源与证据接点

本批与 E2 使用相同稳定组：warehouseId、`{kind:TEMP,creatorDeviceId,clientOrderId}`、scopeId、submissionId、workRound、authorizationId、freezeDeviceId、originalOperatorId、sourceRecoveryEpoch；canonical/slot/assignment均可不存在。命令另绑 transportRecoveryEpoch 与当前映射/决定版本；它们不是原来源身份。

1. E2直接复用既有submission_scope及temporary_work_origin，不新建source_owner实体。新回执通过 `(warehouse_id,scope_id)` FK 引用新增唯一 `(submission_scope.warehouse_id,id)`，S/round/资格/二元source另核组合引用；不得新增另一套TEMP占用owner。K仍以单独bale_id主键共同裁决，source_scope_id就是此scope的归属引用。
2. 当前canonical不等于持有实物来源。回执的original_canonical_order_id可null，original_mapping_id只指命令输入时已存在的不可变映射历史事件，并核scope+mapping_version。LINK结果新映射通过业务事件指回receipt；不能让原回执指向会覆盖from/to的可变行，也不形成双向非空插入循环。
3. `evidence_set_id` 对应E2 `source_evidence_set.id`，原文件对象为 `source_evidence_object.id`；`evidence_verification_id` 对应 `source_evidence_verification.id`，并通过set/scope/S/round组合关系保证同一份证据。处置/续作expected显式绑定setId/hash、verificationId/版本/算法版本、逐包处置/保留清单hash与版本，全部进入commandSha256及原回执。独立LOCAL_FROZEN_PAYLOAD不能改写submission_payload、原ABORT的payloadPresent/hash、receivedPayloadSha256或原签名；declared、received、独立本机证据三类字段分开。
4. 无正文ABORT和部分块ABORT仍可被来源证据引用；无canonical时新回执 FK 不要求 order/slot/assignment。收到独立证据不创建FH号，不变 REVIEW_REQUIRED，不开启释放或续扫；完整原机证明及物理去向由 E2/后续处置验收。
5. FK次序保持scope/已有证据先持锁，命令预留及receipt后写，再写引用receipt的应用事件/处置/续作父记录；验证不临时补未排序U/O/B。证据首次接收的自己的原键/回执与以后处置命令分开；证据对象/集合/验证记录不要求反向引用尚未产生的处置命令，后续应用事件负责连接。

E1现有读取不提供持久mapping_version或完整性证书；C1不能把现场读到的数量/当前目标翻译成这些新事实。没有已注册S的独立原机证据属于E2/R09接收范围，不得为了C1回执凭空创建S。

## 5. 事务外文件与原子边界

命令/回执小字节存在数据库，避免“已提交决定但原回执文件没落盘”。原正文及大证据仍按文件先耐久、再在同一决定事务建立不可变引用；失败留下的孤儿文件不得算已受理，收集清理要由后续受控任务处理且不能触及原证据。

签名可在锁内生成并在提交前失败，不能安排在 COMMIT 之后补签。文件耐久、数据库提交、HTTP与客户端落盘无法组成一个数据库事务，各阶段分别故障注入；不得声称本方案实现跨存储原子。台账/审计/outbox/决定/映射/记账的数据库部分必须同一事务，禁止 REQUIRES_NEW 把成功回执提前提交。Job/fence仍归R09独立批，Worker不能绕过当前E创建最终决定。
