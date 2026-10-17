# D13 正常根P5退役切片

状态：有界服务端L1及修改后完整普通回归通过，开发备份收口中；不计D13整体/L2/D18L3。固定树D:/acrylic-pda-outbound、codex/development，基底0526578c7a8b89c23b1e9a2c4a79c054355861d2；同一聊天单写者，无子代理。版本事实见看板事实块。

## 默认、依据和可验证标准

依总目标第3节自主授权，沿G0 §5.6及原RecoveryOperationRegistry/RecoveryFenceAuthority补正常退役，不另建权威。备选直接删除再改索引存在丢失阻断证据窗口；采用P3内先把原租约原子移到`.json.retiring`，再更新并读回签名索引。该标记不是可自动清理的pending孤儿；任何中断均保持拒绝，原进程记录永不删除。根在绑定已清、子操作零、写者未毒化的确认终态才能退役。无P5的旧适配器保持既有过期绑定观察，仍核子操作和毒化。

P5决策未确认：保留根/L2并毒化，沿原权威UNKNOWN序列；决策已确认后的标记清除或L2释放失败：重试一次，仍失败只做本地围栏，不误报业务UNKNOWN。文件索引写入force，身份/HMAC/所属进程均核验；本批不声称真实断电目录耐久性已验。P3正常竞争使用既有有界等待，避免把短时占用当不可信结果。

| 改动路径 | 影响层 | 必跑与完成标准 | 复用依据 |
| --- | --- | --- | --- |
| recovery/RecoveryOperationLease.java、RecoveryOperationRegistry.java、RecoveryWriterLease.java | 根/绑定/子操作/L2 | 正常一次退役、未排空/绑定/毒化拒绝、决策失败隔离、决策后本地围栏；请求根/数据源/写者回归 | 精确路径SHA见本批source-bindings，不用旧通过替代受影响行为 |
| recovery/RecoveryLeaseRetirement.java、RecoveryLeaseScan.java、RecoveryFenceAuthority.java | P3/P5/进程证据 | 实际文件/签名索引读回、所有权/重复拒绝、两中断窗口不自动修复、真实Windows重命名拒绝→halt70→后继拒绝 | 原进程/围栏序列代码未重建；直接及复用依赖均绑定SHA |
| recovery/RecoveryAuthorityLeaseChannel.java、RecoveryAdmissionConfiguration.java | 生产惰性接线 | 实际HTTP/Session保存覆盖P5，正常请求后无残留；生产调度/renderer回归与default-off | 原HTTP/调度组合输入因产品漂移重新执行 |
| recovery/RecoveryEffectSql.java、RecoverySqlTokens.java、RecoverySqlExpression.java、RecoverySelectSql.java | SQL效果分类 | 实际Session/权限EXISTS/作业/导出语法；fenced零委托、prepared执行期撤权、未知/恶意语法拒绝及有界输入 | run1564 inputs.json逐Java路径SHA与本批source-bindings完全一致；修改后完整普通回归另存 |
| exports/ExportProducer.java、ExportDownloads.java、jobs/BackgroundJobLeases.java | 二进制锁查询 | 保持原SQL/参数/事务，显式byte[]读取；真实创建/同键重放/异文/越仓/下载及生产worker完成/失败 | 不透明getObject限制保持；SQL/授权语义未改；不能外推未触及ExportRetry分支 |
| temporary/command/CommandScalarRows.java、CommandStore.java、CommandHistoricalIdentity.java、CommandBusinessIntegrity.java | TEMP历史核验读取 | 二进制ID/原文本/unsigned整数/时间/SQL NULL；opaque类型拒绝；真实P5下TEMP原确认输入继续供导出核验 | 仅更换标量读取，未开放旧换机命令或恢复取消链；历史身份/业务政策仍按原验证器 |
| 对应Java测试/独立JVM消费者及Python runner | 运行器/独立消费者 | 新隔离库/端口/非空迁移、真实HTTP/SQL/导出、XML新鲜度、认证停机/保全 | 合成身份/控制树/开发签名明确；不冒充原设备事实 |

以上路径前缀为server/src/main/java/com/acrylic/outbound/；完整清单、SHA及必跑证据在.local/goal-20261017-resume/d13-retirement01/。本批仅有界服务端L1，不计D13整体/L2/D18L3；Android、Chrome、原模拟器及D16缺口保持。七类能力、TEMP生产归档、localDeletionAllowed=false及所有生产边界未放宽。

## 输入与首轮验证

接续只读核对后，inputd13retirement-before01.json共5552项，SHA897a75500b4c80636b1ee8cf4b7775239a3874a901b6685c5e1342723c0ba92e，与前序postpush03逐字相同；完整patch/未跟踪副本/ownership/环境/remote同号保存。随后运行version_facts -Write/-Check通过，唯一写权继续登记当前聊天。

首轮普通定向测试exit0，原日志retirement-unit01.log、XML在d13-retirement01/unit01/；未计完整server test。后续实际组合及失败/清理/提交回执在收口时补齐，不以本页准备冒充通过。

## 接续实际结果及失败保全

本段相对证据均在`.local/goal-20261017-resume/`，attempt位于`.local/e2a-cross-20260925-<run>/`。输入resume02为5560项/SHA15f5301bf599fb2dffe8edfe098fbd1e7a53089a26fe3cd990ca63064bf88b84，failed1560-03为5562项/SHAa5435b9abdb496b933914c0e8932716fd124f7c03ec145caf44964e8bd1998ae；完整patch、未跟踪副本、ownership和环境同号保存。后续输入另有快照，不用旧输入代表新增源码。

- run1560（DB19339/HTTP19443）失败：新增P5实际登录返回500 `CONTROL_EFFECT_UNCLASSIFIED`，效果门的SELECT小语法不支持Spring Session JOIN和作业筛选；worker同时失败。已经发生实际HTTP/业务写入，不能统称纯夹具快速修复。原XML副本、SQL dump、HTTP、日志及输入保持；已认证停机，`mysql-stopped.json`记录exit0/监听关闭。
- 同轮原生子JVM在启动前错误使用严格权威读取器读取相对classpath JSON，另有运行器不存在的测试名；二者均已定向修复。未复用失败库。
- run1561使用`--native-only`：45项普通定向、3项IT全部零失败/错误/跳过。正常根退役后签名索引为空，原进程记录保持，独立后继JVM启动。实际Windows CreateFile拒绝FILE_SHARE_DELETE导致真实原子重命名拒绝，F1prime/F2/F3/F4及真实halt70、后继拒绝通过。renderer丢终止回执仍是显式MODEL，不冒充真实OS拒绝kill。
- 独立Python消费者`d13-retirement01/consumer01/result.json`核验原租约/索引HMAC、原进程SHA、孤儿副本逐字一致、UNKNOWN身份、原生输出和XML通过；该结果明确只含native/unit，不证明HTTP。
- 修复共享引用计数锁：`RecoveryWriterLease.reference()`与acquire/close使用同一class锁，拒绝已丢引用句柄；8线程共8000次借还通过，验证不早释/不泄漏。
- 完整服务端普通测试`retirement-server-test01.log`已exit0，4710项、零失败/错误、19项条件跳过；原XML/逐套件SHA及计数在`d13-retirement01/server-test01/`。此输入早于下述SQL改动，不外推为SQL修改后完整回归。两次线程采样显示测试实际推进，不是已确认挂死。

## SQL兼容默认及可验证标准

沿G0 §4.2自主采用有界SELECT解析器，替换不断扩大且易歧义的单条正则。保留原常量SELECT和SHOW免门边界；新支持的关系读取、标量内建函数、JOIN、EXISTS/子查询、布尔筛选/IN/BETWEEN、JSON取值、CAST、排序/限量/行锁全部为SOURCE_READ且委托前过原效果门。只允许明确内建函数及类型/时间单位；未知/限定名函数、注释、多语句、变量、文件输出、WITH/UNION和未消费尾随token继续拒绝。长度、token和嵌套深度有上限。既有ResultSet不透明getter限制未放宽。

新增直接路径：`RecoverySqlTokens.java`（词法和资源上限）、`RecoverySqlExpression.java`（有限标量/谓词）、`RecoverySelectSql.java`（完整SELECT结构）、`RecoveryEffectSql.java`（分类接线）；测试`RecoverySelectSqlTest`以实际Session/作业/权限/导出SQL验证准入与fenced零委托、prepared执行期撤权和恶意/未知语法零委托。运行器增加分类测试。均为服务端恢复效果层，不改数据库结构、生产开关或权限。直接必跑为这些分类/效果门测试和新隔离P5 HTTP→生产worker→renderer/文件→下载→排空→后继JVM组合；Python消费者必须实际消费该轮输入，非空迁移必须实际执行。Android/Chrome/原设备不在本切片L1中，仍按总目标另验。

SQL首轮定向exit0，日志`retirement-sql-unit02.log`，原XML在`d13-retirement01/sql-unit02/`。新组合run1562（DB19341/HTTP19447）在途，不预记通过；输入`inputd13retirement-before1562-05.json`共5568项，SHAe1ec6f4cfc50e6c69a6c9718bd46332e608401c0214dd44464f3af5ea520a4df。后续事实沿新证据追加。

run1562最终失败：真实P5登录和Session读写已经通过，导出创建在无类型`queryForList`内部的`ResultSet.getObject`被原效果门拒绝，SQL分类本身不是本轮根因。该轮已认证停机exit0/19341无监听，完整原XML/SQL/HTTP保留。按新证据把ExportProducer/ExportDownloads及BackgroundJobLeases.lockOwner的二进制ID锁查询改为显式byte[]映射；不放宽getObject或不透明locator守卫。直接受影响的创建/下载/worker complete/fail路径由下一真实HTTP组合及普通相关回归验证；未触及的ExportRetry同类旧问题不冒称已修。

run1563（DB19342/HTTP19448）执行上述类型修复。执行期间自审发现Unicode大小写折叠可能把未知函数名变成ASCII白名单，新增“字面量掩蔽后非ASCII拒绝”及dotless-i反例；修改发生在本轮compile之后，故本轮输入只能绑定其原inputs.json，不用其结果代表这两行后改动，须以新候选补验。已有语法失败仅一次，无新证据机械重跑为零；新getter根因首次实际失败，仍分别计数。

## 修复后实际验收

run1563最终失败：导出锁读取通过后，TEMP历史核验的无类型行映射再次触发`getObject`拒绝，真实HTTP返回409；不是SQL解析失败。认证停机exit0/监听关闭。新增CommandScalarRows只按SQL元数据读取标量，原正文/二进制ID/SQL NULL/unsigned BIGINT保持，BLOB/CLOB/ARRAY/REF/SQLXML/OTHER拒绝；CommandStore、历史身份及成功事实读取改用该mapper。未删原验证、未开放任何旧换机路径。getter同类问题两次暴露，随后有明确调用栈和定向修复；未重复无新依据失败。

run1564（DB19343/HTTP19449）使用新隔离库及修复后源码：126普通定向+4IT零失败/错误/跳过，runner exit0。实际执行旧非空业务基线到候选迁移，随后default-off、admitted及P5三种模式的真实Spring Session/HTTP→非Web生产调度→renderer/文件→下载；五份XLSX/PDF完成。401/越仓404、原键重放、异文409、持根阻推进、rootless/stale拒绝及末条SQL失败回滚通过。P5排空后索引为空、两个原应用进程记录保持；独立后继JVM成功后增加第三条记录。MySQL认证停机exit0/监听关闭，应用HTTP监听关闭。

同轮原生测试实际覆盖正常退役及Windows禁止FILE_SHARE_DELETE导致原子重命名拒绝→原UNKNOWN围栏→真实halt70→后继拒绝。进程证据保留。renderer丢终止回执仍明确为MODEL，不能称真实OS拒绝kill；中断标记测试也是显式窗口模型，未声称真实断电目录耐久性。

独立Python消费者第一次错误预期进程记录数为2，未计通过；修正为核原两个记录并允许已实际启动的第三个后继记录后，`consumer1564b/result.json`对同一原件消费通过，没有重跑产品。核60条HTTP、五份导出、原顺序/整数克/无公式、五快照orders相同、P5签名/HMAC、原进程记录/UNKNOWN及原生XML。dump SHA256 `afe0a8ecb2f697d60bd3251535332737876185fa893d17af1117f0110350e9e4`。两份PDF共四页均实际渲染看图，无裁切、重叠或缺字；P5预览`consumer1564b/p5-pdf-1.png`及`p5-pdf-2.png`单独补看。消费者是同作者编写的独立工具，不冒充独立人员审查。

修改后完整普通`server test`已exit0（session85746）：308套件、4756项、零失败/错误、19条件跳过。原XML/逐文件SHA、全部跳过名称在`d13-retirement01/server-test02/result.json`及`xml/`，日志`retirement-server-test02.log`；不是IT、Android、Chrome或整体L2。跳过由SKU_PARITY_OUTPUT、E06_CLOSURE_ARCHIVE、E06_REVIEW_ORIGINALS未设置及Windows符号链接条件触发，未执行不计通过。线程采样`retirement-server-test02-thread01.log`保留，显示恢复凭据测试中的实际RSA运算。此前4710项结果仅按原输入保留，不覆盖本轮。

## 自审、继承保护与开发产物清理

`d13-retirement01/source-bindings.json`记录32个直接路径及复用依赖的逐路径SHA；`candidate-binding.json`核本轮Java输入与run1564零漂移。逐路径影响层/必跑/复用映射见`impact-map.json`。完整快照`inputd13retirement-closeout06.json`共5571项/SHAa5c29958fdf090662223c390e03436c78d1b38d6a5aa998591fed9eb84aaba3a；完整patch/全部未跟踪副本/写权/进程监听磁盘/远端同号保留。之后只更正文档及证据，最终断点另取。

`inherited-protection.json`对5552项本切片基底逐字核对，只发现明确列出的本切片源码与看板改动，无意外变化。自审核排空条件、原权威/P3索引顺序、两阶段失败归属、Writer统一锁、SQL全消费/资源上限和不透明读取拒绝；不另立权威，不把标记或活跃租约当垃圾。既有大文件只改本项必要接线，未借本次重排继承业务代码。

清理前已保存四轮全部逐文件SHA、输入/patch、初始化/Gradle/认证停机命令及结果、依赖绑定，并核实进程/监听无消费者。仅删除run1560/1562/1563/1564的可重建合成datadir及binlog，共1392文件、890627334逻辑字节；D盘余量56065372160→56973672448 bytes，实测增加908300288 bytes。清理前后C78688804864、E195634057216 bytes；精确进程、路径及磁盘回执`cleanup-before.json`/`cleanup-after.json`。原XML/dump/HTTP/日志/签名/身份/唯一证据、handoff-final01及run03指定失败证据均保留。该清理不改变D16或业务删除政策。

39路径开发备份已提交推送`bcbff6a54df5ece2b4b0c57bf82bacef8e710bc1`，实际远端核对一致。提交前完整快照`inputd13retirement-precommit08.json`共5572项/SHA2b2098812861ac6fbc770367ffab09d05725ed61c2dd8ea9ec3a91473d58b4d5；提交后断点另存，不把它混作提交内容。本切片不声称全部文件/HTTP入口、原设备/Android/Chrome、完整跨入口受影响组合已经验收；统一最终候选及第8节所有闸门仍未满足。
