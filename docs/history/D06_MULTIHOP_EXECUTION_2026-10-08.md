# D06 累计历史与多跳接续执行记录

本批是 D01–D18 总目标的一部分，目标 active、未完成。版本事实仅见[看板事实块](../ORCHESTRATION_STATUS.md)。生产、签名、原库、现网、main、发布部署与删除边界保持。

## 输入与保护

树 D:/acrylic-pda-outbound，分支 codex/development；HEAD 与实际远端为 891c1f0136278b95400b934b19c2b9b1afd7bdcc。本聊天唯一写者，其他开发聊天 idle/notLoaded。全部 dirty 的副本、哈希、diff、Git、进程、监听及磁盘在 `.local/d01-d18-20261008/d06-multihop-checkpoint-01/` 和 `d06-multihop-checkpoint-02/`。继承资料、清理工具和私有请求不纳入本批。原收口快照登记的三项哈希重算匹配。

## 退出标准与实现

- 每轮独立资格与不可变 activation/registration、本人 STOP 和 E2A 根逐轮核验；累计 captures/scans/events 前缀完全保留，新设备只证明其新 suffix。不得要求 B 重签 A 的事实，也不得忽略旧事实资格。
- A→B→C 使用真实隔离 HTTP/MySQL 和 Android Room 消费者，B 实际停止、主管再次核对批准，C 新输入最终成功；每机本地计数可重叠，累计订单序号连续。减包、改前缀、未冻结及无原文的输入必须拒绝且 SQL 事实保持。
- LOCAL_FROZEN_PAYLOAD-only 与缺失 canonical 的允许/拒绝边界、混合高轮历史和新开轮实际产品页单独补验，不从其他通过结果外推。

历史消费者改为逐帧读取与回放，避免审批验证再次递归调用自身；保留每轮原资格和原 STOP 验证。原文读取先绑定前驱摘要，逐值核累计前缀，再要求本轮 manifest 覆盖新 suffix。LOCAL-only 前驱读取已补不可变 ticket/停止集/源设备/操作者及文件摘要绑定，但尚未取得本批真实组合证据。

## 已执行与失败保全

- `d06-multihop-compile-01` testClasses 通过；`d06-multihop-unit-01` 定向普通 test 280/0/0/0，sourceUnchanged。普通测试不能替代 IT。
- 1104 专项 IT 1/1/0/0、exit1。A 来源与 B prepare/stage/confirm/activate/freeze/upload/evidence/stop 分别通过；B→C 审批原文读取失败，B manifest 未覆盖 A 只读前缀。失败路径 `.local/e2a-cross-20260925-1104/` 与 `d06-multihop-test-1104/` 保留，不复用数据库。
- 1104 dump 成功、认证 shutdown 成功。原 retention.json 因检查过早记录 launchExited=false，保留原件；`d06-multihop-1104/retention-recheck.json` 补核 PID 10908 已退出、19330/19434 无监听，dump 重算与登记哈希一致。
- `d06-multihop-payload-unit-01` 8/1/0/0，夹具错误把当前 ID 置于订单字段，JDBC varargs 匹配也不正确；02 命令参数错误、0 项未执行；03/04 仍失败，原 XML/日志保留。修夹具并让反例断言具体错误后，05 为 8/0/0/0、sourceUnchanged。不把之前偶然拒绝计为反例通过。
- 1105 专项 1/1/0/0、exit1、sourceUnchanged。B 原文审批消费者通过，主管批准时 DISPOSITION_RESERVATION_CORRUPT：B 清单只含本轮 suffix，而实际占用含继承包。dump/认证停机/PID退出/无监听通过，原件保留、不复用。
- 占用核对从不可变交接 activation owner 补入保留包，和本轮 E2A 活跃扫描一起精确比对；重复、缺失、周期错误仍拒绝。`d06-multihop-reservations-unit-01` 33/0/0/0；加入 LOCAL-only 前驱真实文件测试后 `d06-multihop-local-unit-01` 34/0/0/0，均 sourceUnchanged。单元 SQL mock 不能替代真实数据库。
- 1106 专项 1/1/0/0、exit1、sourceUnchanged。原文和继承占用消费者、错误周期 HTTP 拒绝及整事实不变通过；正向审批因夹具复用申请人触 HANDOVER_SELF_APPROVAL_DENIED。保留失败后为下一批建立独立主管，并明确检验申请人自批仍拒绝。dump、认证停机、PID退出和无监听通过。
- 1107 专项 1/1/0/0、exit1、sourceUnchanged。独立主管批准通过，C prepare通过；C stage下载原文触只读事务中的 FOR UPDATE，MySQL READ ONLY拒绝，未到C最终成功。`.local/e2a-cross-20260925-1107/`、`d06-multihop-test-1107/`原件和失败库保留；dump、认证停机、PID退出和无监听通过，不复用。
- 全部47条dirty副本/hash/diff与Git/进程/监听/磁盘新增保全至`d06-multihop-checkpoint-03/`。历史读取改用append-only票据非锁查询，当前写入与激活仍用原加锁路径；数据库不可变UPDATE/DELETE触发器仍在。`d06-multihop-history-unit-01`受影响普通测试312/0/0/0、sourceUnchanged；只读真实HTTP复验使用新编号，不从单测外推。

自审身份为作者；独立文件/契约消费者不等于独立人员。合成账户/开发密钥/Room JVM 消费者不替代原身份、实体 PDA 或产品页操作。D06 仍未整体通过；D04/D05 政策阻断、D07–D18 和原计划第8节继续执行。

## 本轮运行器与后续验证

1108因作者调用Windows PowerShell而在provision随机密码API处失败，0项业务测试、未创建应用schema。空实例已保全全库dump/hash/binlog/datadir/日志并以该实例root认证停机，PID退出、19330/19434无监听；记录在`d06-multihop-1108/retention.json`。原失败不删除，编号不复用；runner补PowerShell 7前置检查。

1109再次跨33树核对分配，使用新datadir、同一修复源码实际执行。专项1/1/0/0、exit1、sourceUnchanged；B全阶段通过，C prepare通过，C stage服务端原文下载已越过READ ONLY故障，但Android错误将设备本地高水位与累计订单高水位比较而拒绝。真实输入是A/B各本地seq=1，累计订单seq=1/2；B STOP设备high=1、订单high=2。dump/hash/认证停机/PID/端口通过，原件及失败库保留。

客户端改为累计订单序号连续且与正文/签名订单high一致，再以停止机origin的deviceCaptureSeq验证本地high，不改变原签名或前缀。补局部计数重叠正向、缺前缀及本机超high反例；修正旧单测夹具将正文订单high写为设备seq的语义错误。android-unit-01作者测试方法拼写导致编译失败，0项未执行；02为38/14/0/1，作者夹具属性初始化在manifest之后导致captureHighWatermark缺失，原件均保留。修正初始化顺序后03为38/0/0/1、sourceUnchanged；HTTP条件skip不能计通过。

2026-10-09接续：1110重新跨33树分配，新一次性实例执行同一真实多跳组合，专项1/0/0/0、exit0、sourceUnchanged。A实际来源 → B本人prepare/stage/confirm/activate/freeze/upload/evidence/stop → 独立主管再次核对批准 → C本人prepare/stage/confirm/activate/freeze/upload/最终success各实际消费者通过；C成功回执、账务与原前缀守恒通过。B继承包错误周期拒绝及整SQL事实不变、本人自批拒绝、错机/坏证明/异文、outbox/最后SQL/Room回滚、丢响应、重开和服务重启见原件。设备本地seq均从1起，累计订单seq连续。

证据在`.local/e2a-cross-20260925-1110/`、`d06-multihop-test-1110/`、`d06-multihop-1110/`；dump/hash、认证停机、PID退出及19330/19434无监听均通过，failed attempts不复用。Android消费者合成登录/开发密钥，不能冒充原身份或真实PDA。

混合R06/TEMP历史自审还发现TemporaryRoundPredecessor、TemporaryRoundEvidence和Android TemporaryContinuationSource仍有只认R06前驱/同设备前缀约束，HandoverTemporaryPayload只认TEMP交接前驱；这些缺口须分别补实现与有效验证，不能由纯交接多跳通过外推。新开轮实际页面测试已转到Android instrumentation源码，独立build-01在途；使用1110的C原activate-final.db及实际签名ACK回放，不重复启动数据库。

新开轮产品页验收设计：从实际activate-final.db与原ACK回执建立模拟器消费者；取消确认不得改变阻断草稿，丢ACK响应保持阻断和原键，重新打开重试后进入SCANNING并导航真实作业页，旧前缀/占用/身份限制及重开后状态保持。回放HTTP页面证据与真实HTTP组合分开记录，不冒充原身份/真机。

start-ui-build-01因作者Espresso两个pressBack静态导入冲突失败；0 instrumentation。修正明确调用后build-02通过、sourceUnchanged；应用APK复用build-01已产的相同源码字节，AndroidTest APK由02生成，hash/来源登记。start-ui-01在runner复制未落盘的UP-TO-DATE APK时失败，未启动模拟器、0测试；补复制并登记来源后start-ui-02以640×960/font_scale=2.0在途。构建/页面失败均保留，不能计通过。服务端普通test全回归regression-01并行使用独立服务端输出，在途；UI固定的APK副本不被覆盖。

start-ui-02实际instrumentation为1项/1失败；TEMP_START已进入真实新轮弹窗，但原系统横排按钮使“返回核对”仅44%可见，未到ACK或开轮，不能计页面通过。instrument日志、logcat、应用tar和输入hash保留，模拟器已关闭且5560/5561无监听。下一实现：TEMP_START使用专用滚动纵排内容和按钮，原提示/取消/确认语义不改；复验大字取消、丢ACK保持阻断、原键重试、实际作业导航及force-stop进程重开。

server-regression-01为3339/3/0/1、exit1、sourceUnchanged。三项均在PhysicalDispositionOperationTest：旧通用JDBC mock把本轮包同时返回为继承包，导致精确占用核对重复而拒绝。改为分别只模拟本轮源清单与实际占用查询；继承查询保留默认空，不降低生产验证。原XML/日志保留，须复验后才可记回归通过。TEMP_START专用滚动纵排弹窗已实现，build-03在途。

接续核验：build-03通过且sourceUnchanged；disposition-unit-02为34/0/0/0、exit0、sourceUnchanged。start-ui-03实际1/1/0/0：取消、大字纵排按钮及丢响应阻断走到，第二次ACK回放遗漏serviceMode/serverRecoveryEpoch/serverTime完整信封而未入库，未到开轮或导航。原instrument/logcat/tar/hash和只读提取Room保留，模拟器退出True；不计页面通过。补齐夹具完整响应信封且不改变原signedAck，build-04在途。

全部57条dirty副本/hash/diff、Git、33树迁移分配、进程/监听/磁盘保全至`d06-multihop-checkpoint-04/`，不包含随后修正的UI夹具。HEAD/实际远端仍为891c1f0136278b95400b934b19c2b9b1afd7bdcc，无本次提交推送。C/D可用精确字节分别80698658816/31938977792；没有项目数据库/模拟器监听或重型构建在该快照时点在途，既有3306/13316保持不操作。

复验收口：补齐回放信封后`d06-start-ui-build-04`退出0、sourceUnchanged；UI-04 opening/restore各1/0/0/0。640×960、font_scale=2.0实际产品页完成取消、丢ACK保持`HANDOVER_BLOCKED`、原键重试、进入真实临时作业页和force-stop重开；应用tar、截图、logcat、Room副本和模拟器退出记录在`.local/d01-d18-20261008/d06-start-ui-04/`。该消费者使用合成会话与1110签名ACK回放，未替代真实身份/真实HTTP。

`d06-multihop-server-regression-02`实际全量server test为3339/0/0/1、exit0、sourceUnchanged，唯一skip为Windows符号链接条件；修复后的PhysicalDisposition mock分别模拟源清单与继承占用。独立消费者证据在`.local/d01-d18-20261008/d06-multihop-consumers/`：1110链Node签名/哈希核对通过（3轮、3设备、本地计数重叠、连续累计序号、成功账务与删除边界），两阶段Room SQLite只读核对通过；`d06-multihop-android-regression-02`为448/0/0/9、exit0、APK/lint成功、sourceUnchanged。失败的首个消费者脚本因R06命令回执audience和WAL提取错误保留为`*-01`，已修版本为`*-02`。

1110的B/C受理前均实际HTTP提交减包（删除原scan）、未冻结（删除末STOP事件）及改原capture反例，400/INVALID_SUBMISSION且全业务SQL快照不变；原件在各消费者`incomplete-sql/`，覆盖受理边界。更早的主管批准/准备下载中未冻结或无canonical边界及LOCAL-only正向仍须单独组合验证。

本批涉及的混合R06/TEMP前驱解析、`LOCAL_FROZEN_PAYLOAD`真实HTTP组合、未冻结/无canonical前置组合和D07–D18仍未完成；本批结果不改变D06整体及总目标未完成状态。

作者自审：历史原文读取不调用审批消费者形成依赖环；每轮独立资格、不可变activation/registration、原STOP、恢复代次、前缀和末事务核对保持。只读历史查询不取得FOR UPDATE，写入路径保持锁与不可变触发器。设备high与累计订单high分别校验；继承占用和本轮suffix精确合并，重复/缺失/周期错仍拒绝。没有契约/迁移变更或生产开关放宽。Android默认构建未传隔离开关，lint为0 errors；APK保存在`d06-multihop-android-regression-02/app-debug.apk`，SHA256为`5BA12CF4399A1F3C380F5256350D3AA77BF02072CEE3C0193D925C2D70859700`，仅开发产物，未发布。

Android regression-01作者将--tests置于lint任务之后，Gradle参数拒绝、0测试；原命令/日志/sourceUnchanged保留，02修正任务选项顺序后实际通过。服务端普通test不包含IT，HTTP条件skip均未执行；本批独立消费者为工具消费者，不等于独立人员或现场验收。
