# D06 混合交接与续作执行记录

本记录只登记开发与隔离验证，不表示D06或总目标通过。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。输入树D:/acrylic-pda-outbound、codex/development，HEAD与实际远端均db33fb77399c208b25ad060799766b117a8d594d；单助手唯一写者。旧看板及49条dirty完整副本/hash/diff、Git、进程、监听、磁盘和跨树迁移清单在`.local/d01-d18-20261008/d06-mixed-checkpoint-07/`。

## 范围与退出标准

接续1110已验多跳输入，补TEMP交接前驱→R06续作、R06前驱→TEMP交接的混合历史消费者。每轮资格/原签名独立核对；不可变activation/registration证明旧前缀，本机suffix绑定本轮身份，累计订单high与设备high分别校验。原正文、回执及owner保持；减包、混合两种引用、错设备/来源和改写前缀拒绝。真实HTTP/MySQL/Android同输入完成后再做受影响回归、默认APK/lint、独立消费与明确路径提交推送；LOCAL-only实际正向及前置未冻结/无canonical仍须补验。

## 实现与自审

服务端混合前驱读取不调用审批验证形成循环；CommandFacts、历史身份、E2A投影绑定本轮不可变激活设备/client，旧scope origin不改。RETAIN和续作清单允许来自不可变继承owner，释放政策不放宽。累计high减去原激活prefix后才与本机high比较，数据库registration守卫对应新增迁移，旧迁移不改。

Android精确保留历史前缀，suffix仍核本机/资格/操作人；交接前驱走其原件消费者，同一目标双消费拒绝。新R06 request移除上一轮temporaryHandoverActivation声明，原request及交接回执仍保留；真实Room测试发现该缺陷后修复。

## 当次证据与失败

证据目录统一为`.local/d01-d18-20261008/`。server-unit-01为144/0/0/0；02为211/3/0/0，旧JDBC夹具误匹配继承查询，失败保留；修夹具后03为211/0/0/0。目录前缀为d06-mixed。

Android-unit-01编译失败、0测试，夹具误用activate；02为79/2/0/2，store未load及资格期限夹具缺失；03为13/1/0/0，暴露复制旧交接声明的产品缺陷；04为79/0/0/2，HTTP条件skip不计通过；05加入真实Processor扫码suffix后13/0/0/0、exit0、sourceUnchanged。原日志/XML/命令全部保留。

compile-01误用不存在的compileIntegrationTestJava任务，exit1、0测试；compile-02改为项目实际compileTestJava后exit0、0测试、sourceUnchanged。编译不能替代真实组合。

新实际链设计：A→TEMP交接B→冻结/上传/E2A/STOP→实际HTTP RETAIN/REOPEN/ACTIVATE R06→B安装原签名、ACK、再扫码冻结上传→独立主管确认→服务重启后Android消费成功。包含减包拒绝、SQL/Room尾写回滚、丢响应及原件守恒；合成身份/开发密钥不代原身份或真机。

1111因作者分配JSON缺nextRun被provision前置守卫拒绝；0测试、未初始化数据库，原分配文件/运行器及结果保留，不复用编号。1112真实专项1/1/0/0、exit1、sourceUnchanged；接收prepare/stage/confirm/activate/freeze/upload/evidence/stop均通过，R06保留被CommandUnitOfWork高轮总门拒绝，未到续作。不是关闭开关问题；迁移已在新库执行，但尚无高轮非空升级比较。dump独立重算匹配，认证停机/PID退出/19330与19434无监听通过，原库及日志保留。

修复限定已ABORTED高轮的非空全RETAIN命令可继续下游精确集合/证据/身份验证；高轮RELEASE/QUARANTINE/CLOSE及其他旧阻断不开放。新增真实HTTP释放/隔离反例与全业务SQL无变化断言。server-unit-04为224/0/0/0、exit0、sourceUnchanged。1113重新跨33树分配，在途，先建立接收STOP，再以全表/私有文件比较执行新迁移；未收结果前不计通过。

1113最终专项1/1/0/0、exit1、sourceUnchanged。接收STOP后的非空迁移全表/私有文件字节比较、高轮RETAIN及RELEASE/QUARANTINE拒绝通过；REOPEN减包夹具预期409实际403，接收人缺order:resume。保留权限反例，随后仅给合成夹具接收人明确授予既有SUPERVISOR角色；生产岗位映射未改。

1114专项1/1/0/0、exit1、sourceUnchanged；权限反例及授权后减包拒绝通过，有效REOPEN先返回RETAINED_MANIFEST_SET_CONFLICT，未到SQL尾写。自审发现交接owner清单摘要被当作原scan摘要。新增专用原件读取，已受理正文优先、缺失时只读本人/本机/本轮严格绑定的LOCAL_FROZEN_PAYLOAD；从canonical原scan取摘要，逐包周期和集合仍精确验证。高轮激活占用断言改为精确合法状态转移，其他行与字段守恒。两次失败dump/hash/认证停机/PID/端口核对通过，均不复用。接续前完整51条dirty快照见d06-mixed-checkpoint-08；此快照早于本次摘要修复。

原件摘要修复定向unit-05为45/0/0/0、exit0、sourceUnchanged。1115专项1/1/0/0、exit1、sourceUnchanged：RETAIN/REOPEN/ACTIVATE、精确owner转移、SQL尾写回滚、Android激活/Room回滚/ACK/扫码/冻结/上传通过；独立确认返回TEMPORARY_ACCOUNTING_CONTEXT_CHANGED，仍未成功。根因为记账政策身份只取交接activation或scope创建机，漏R06 activation机；补严格本轮授权/设备/操作人/origin与互斥前驱验证。dump/hash/认证停机/PID/端口通过，失败库保留。

记账身份修复unit-06为163/0/0/0、exit0、sourceUnchanged。1116专项1/1/0/0、exit1、sourceUnchanged：混合链至Android上传通过，正常确认在temporary_command_receipt插入触发TEMP command origin binding is invalid；尚无成功，也不能凭首个500认定确认尾写注入已到达。原守卫绑定高轮R06到scope根client，补新迁移绑定独立本轮activation/origin且保留旧合法来源分支与前驱互斥。新增四类直接伪造身份拒绝；尾写必须核SQLSTATE/错误码/精确注入message。迁移号跨33树核无冲突，旧迁移不改；1116原dump/日志/库已认证停机保留。

1117专项1/0/0/0、exit0、sourceUnchanged：A→TEMP交接B→R06续作B→独立主管正常确认→服务重启后的Android成功消费完成。原前缀、精确保留scan摘要、合法owner转移、累计high/本机high、实际接收机命令origin及四种直接伪造身份拒绝通过。REOPEN与NORMAL_CONFIRM的尾写均核到精确注入message、SQLSTATE45000及错误码1644；确认注入条件要求closure已COMPLETED。减包、RELEASE/QUARANTINE拒绝及事务回滚不开放生产能力。

独立Node crypto/hash消费者`d06-mixed-consumers/chain-signatures-1117.json`核三轮原前缀、连续orderScanSeq与局部deviceCaptureSeq重叠、retained原scan摘要、实际命令/成功RSA签名与总量/删除边界；只读SQLite/hash消费者`room-1117.json`核真实Room尾写回滚、原submission正文/回执/控制/计数守恒、ACK门、丢upload/成功响应、重启及唯一耐久成功。两者已当次执行通过，只是独立工具，不是独立人员或原身份/真机证据。

1116/1117的dump已独立重算匹配，认证停机、原PID退出、19330/19434无监听通过，见`d06-mixed-checkpoint-09/retention-check.json`。该快照保存57条dirty副本/hash/diff、实际HEAD/远端/环境与跨树迁移清单，早于补强SQL续作表快照。19条继承路径逐hash一致，原stop-close及失败库原件继续保全。

自审补强REOPEN和最终确认的回滚快照，额外逐列比较续作票据、保留包、激活、submission registration、closure、处置history、work origin及授权。新增`HandoverMixedContinuationSql`只用于该链，不改生产代码；1118新编号跨33树核无占用，专项1/0/0/0、exit0、sourceUnchanged，10个Android实际阶段各1/0/0/0。增强回滚、独立Node/SQLite消费者、非空迁移全表/私有字节和dump重算、认证停机/PID/端口全部通过；原1117结论不扩为D06整体通过。证据见`d06-mixed-test-1118/`、`d06-mixed-consumers/`及`.local/e2a-cross-20260925-1118/`。

服务端普通全回归01因作者把参数传成单个逗号字符串而失败，exit1、0测试/0报告，原命令/日志保留；修正运行器调用后02为3355/0/0/1、exit0、sourceUnchanged。唯一skip是Windows符号链接条件，不计通过；普通test不含IT。Android受影响回归`d06-mixed-android-regression-01/`为514/0/0/10、exit0、sourceUnchanged，默认debug APK/lint通过，lint 0 errors；HTTP条件skip分别保留，不代真实组合。APK保留一份并登记/重算hash，见`d06-mixed-consumers/android-artifacts.json`。作者自审细项在`d06-mixed-checkpoint-09/self-review.md`；明确路径41条拟定清单，不含19条继承路径或私有产物。

lint Analyze任务当次执行，但Report最初复用旧XML；第一次产物消费者因缺文件未正确终止，结果标为无效并保留`android-artifacts-01-invalid.json`。后显式`:app:lintReportDebug --rerun`在`d06-mixed-lint-report-01/`生成当次XML/HTML，exit0、sourceUnchanged，严格消费者核0 errors；该次0业务测试，不重复计Android回归。默认APK SHA256为`81C6BEDE90C960C36C1C81FC8DDC58A232E6802DF3C7663DD7BC79D13F6C4D79`，只保存在本机D盘，未发布。

生产与签名能力、七类生产决定和localDeletionAllowed=false保持；不接原库/现网、不合main、不部署发布或正式签名。失败库不复用、1100–1102不重跑；反方向R06→TEMP交接、LOCAL-only实际正向及未冻结/无canonical前置拒绝仍待验，D04/D05政策阻断及D07–D18、第8节闸门保持未完成。

## 范围提交与反向接续

本批41条明确路径已提交推送：`4e03f9b997156ff1a8fbeeed5eee919d9362a1d8`，分支codex/development，实际远端匹配。提交标题`fix(D06): 打通交接接收机R06混合续作与最终确认`；提交前完整60条dirty副本/hash/diff、暂存清单及commit/push回执在`d06-mixed-checkpoint-10/`。19条继承路径保持，不含密钥/原资料/数据库/产物，无main或生产动作。

反向四轮设计在`.local/d01-d18-20261008/d06-reverse-design.md`：A→TEMP B→R06 B第三轮短装→该轮真实E2A/专用STOP→独立主管批准→TEMP C第四轮→成功。新增测试消费者只读该轮实际正文与STOP，不借旧STOP；SQL尾故障核精确归因，Room末写与丢响应/重开分别留证。两端compile-01/02各exit0、0业务测试、sourceUnchanged。1119已跨33树核无占用，使用新隔离库；完整28条dirty快照在`d06-reverse-checkpoint-01/`，专项在途，不计通过。产品代码若发现实际缺陷再最小修复并用新编号复验。

1119专项失败1/1/0/0、exit1、sourceUnchanged。第三轮R06实际扫码/冻结/HTTP受理通过，continuationEvidence阶段失败“原机订单扫码序列不可核验”；E2A仅为temporaryHandover读取可信前缀，未为temporaryContinuation分离混合历史。未进入第三轮STOP或第四轮交接，不计通过。dump独立重算匹配、认证停机/原PID退出/19330与19434无监听通过，失败库/datadir/binlog/原日志/XML保留。完整29条dirty副本/hash/diff在d06-reverse-checkpoint-02，19条继承路径及原stop-close快照hash一致。

补TemporaryRoundEvidenceSource，通过不可变registration及原控制身份、activation来源、完整历史/原资格/冻结原文重验前驱；E2A只消费本轮suffix，累计orderScanSeq仍严格连续、历史逐值不改。unit-01作者参数错误、unit-02漏更新两个测试构造器导致编译失败均0业务测试且保留；unit-03定向47/0/0/0、exit0、sourceUnchanged。1120新编号跨33树核无占用，四轮专项在途，19330/19434仅隔离测试；完成后先核实际报告与保全，再独立Node/RSA/canonical及只读Room消费者。LOCAL-only合法前置取消/注册路径仍在核对，禁止删除已受理payload伪造独立冻结原件。

1120最终专项1/0/0/0、exit0、sourceUnchanged；实际A首轮→TEMP B第二轮→R06 B第三轮短装→该轮本人E2A/专用STOP→独立主管批准→TEMP C第四轮→最终成功→Android重启消费完成。来源及18个接收阶段共19份Android XML各1/0/0/0。第三轮STOP与最终确认尾SQL均核精确注入message、SQLSTATE45000/code1644及完整业务/续作表回滚；丢STOP/upload/success响应、Room尾写及重开留有原件，未用第二轮STOP替代第三轮证据。

独立消费者`d06-reverse-consumers/verify-chain.cjs`当次核四轮逐值前缀/前驱digest、三设备局部counter重叠、第三轮实际STOP请求hash与RSA、最终签名/总量及删除false，输出chain-1120.json/hashes-1120.json；`verify-room.py`只读SQLite核第三轮STOP回滚/原件与累计事实、第四轮stage原件/guards、丢响应和唯一耐久成功，输出room-1120.json/third-stop-1120.json。合成身份与开发密钥，独立工具不等于独立人员、原身份或真机。

非空迁移全表与私有字节守恒、非法origin插入拒绝通过；原始证据在`.local/e2a-cross-20260925-1120/`的e2a-origin-before/after.json与marker。1119/1120 dump均独立重算匹配；认证停机、原PID退出、19330/19434无监听通过，1120回执在`d06-mixed-1120/retention.json`。失败1119的库/datadir/binlog/XML/日志继续保留，不复用。

反向服务端普通全回归`d06-reverse-server-regression-01/`为3360/0/0/1、exit0、sourceUnchanged；唯一skip为Windows符号链接，普通test不含IT。Android受影响回归`d06-reverse-android-regression-01/`为456/0/0/10、exit0、sourceUnchanged；HTTP条件skip不计通过。本范围没有Android产品源码、契约或迁移变化；默认APK仅引用上一混合批的产品输入与已登记SHA256，未声称当次重新构建。作者自审在`d06-reverse-checkpoint-02/self-review.md`，补收两端回归后通过。

提交前36条dirty完整副本/hash/diff、Git/进程/监听/磁盘在`d06-reverse-checkpoint-03/`，早于本段文档更新；19条继承路径继续逐hash保护。LOCAL-only正向和未冻结/无canonical相应阶段拒绝仍未完成，D06整体及D07–D18、第8节闸门不计通过。LOCAL-only设计在`.local/d01-d18-20261008/d06-local-only-design.md`：真实已激活接收轮先冻结并注册RECEIVING清单，不上传/finalize正文；本人LOCAL冻结/E2A与专用STOP绑定清单和不可变来源，不手造canonical，不删除已受理payload。
