# D07 FORMAL可信归档故障换机执行记录

本范围开发中，总目标active、未完成。版本事实仅见[看板生成事实块](../ORCHESTRATION_STATUS.md)；设计在`.local/d01-d18-20261008/d07-formal-design.md`。不制造原机STOP/ACK，不将TEMP附件验收外推为FORMAL通过。

## 接续输入与保护

树D:/acrylic-pda-outbound，分支codex/development，HEAD/实际远端均c0c5a3b71dcb3f992d38fb08fd4deda6c7aa93ac。原891c1f0基线已沿授权推进，未重置。d07-formal-start-19保存23条dirty，早于FORMAL源码；接续全量39条dirty副本/hash/diff、HEAD/远端、33树迁移分配、全部进程/监听/磁盘在d07-formal-checkpoint-20。protection.json核19条继承及原d06-stop-close-20261008三项hash一致；writers.json核其他项目聊天notLoaded，本聊天唯一写者。

接续未发现Gradle/测试/模拟器运行；19330/19434/5173无监听，3306/13316/ADB/19387为既有环境不操作。C/D余量见快照disks.json，新增产物继续D盘。失败库/日志/原件不复用、不删除；1100–1102不重跑。

## 已实现但未验收

FORMAL独立引用解析、原召回恢复包只读重验、独立故障围栏迁移与存储；故障同事务撤原assignment/offline资格，设备禁用/会话撤销沿原链。原召回REQUESTED、stop_receipt和resolution_receipt为空。审批/接收manifest沿来源分支提供faultReceipt，无stopReceipt。改动清单与原字节见checkpoint-20，不与继承改动混合提交。

以上为初始实现范围，后续实际适配与验证见下方接续记录；未通过内容不能以初始设计或编译代替验收。

## 实际验证

- d07-formal-unit-01：两解析类26/0/0/0、exit0/sourceUnchanged，命令及原XML在该目录。
- d07-formal-unit-02：普通handover定向269/0/0/0、exit0/sourceUnchanged，命令及原XML在该目录。仅编译和普通测试，不能代替MySQL迁移或FORMAL业务链。

## 接续动作

先自审后端并补原键/读面重验；补Android独立故障消费者、Web字段与契约；核跨树一次性分配、磁盘和写者后建立全新隔离库，执行设计退出标准。达标后受影响回归、APK/lint、浏览器/独立消费者及明确路径提交推送；再推进无归档与R09跨截点。D06/D07整体、D08–D18及第8节未通过。生产默认/签名/七类能力关闭、localDeletionAllowed=false；不接原库/现网、不合main、不部署发布/正式签名。

## 1145–1147接续与快照修复

1145实际专项1/1/0/0，JDBC归档时间读取类型错误导致500；修正后1146真实MySQL/HTTP/Android专项1/0/0/0、exit0/sourceUnchanged，至接收激活/续作消费者通过。该输入尚不含后补的最终提交、重启成功、非空TEMP迁移保全及实际FORMAL产品表单，不能外推完整退出标准。原XML/HTTP/Room在`.local/e2a-cross-20260925-1146/`；运行输入/命令/结果在`d07-fault-test-1146`。两库均dump独立重算、认证停机/原PID退出，回执`d07-fault-1145/1146`，不复用。

补最终记账/重启成功消费者、非空TEMP围栏/回执/附件原件迁移核对及真实Web FORMAL分支后，1147专项1/1/0/0、exit1/sourceUnchanged。非空TEMP实际迁移全表/私有字节和原键回执保全已执行；随后`HandoverFormalFaultRefusals.beforeFence`自审批反例的快照早于新登录，而登录新增审计事件使全表比较失败。未到新的正向FORMAL围栏/产品页/最终提交，不计新链通过。原库、日志/XML、迁移前后事实保留，dump独立hash及认证停机/原PID退出在`d07-fault-1147`。

接续实际HEAD/远端仍c0c5a3b71dcb3f992d38fb08fd4deda6c7aa93ac，codex/development。本聊天唯一写者、其他项目聊天notLoaded；无测试/Gradle/模拟器消费者，19330/19434/5173无监听，既有3306/13316/ADB/19387不操作。`d07-formal-resume-22`保存75条dirty全量副本/hash/diff、全部进程/监听和磁盘；19条继承及原STOP快照三项hash一致。快照时C剩余81940656128字节、D剩余22027964416字节，新增产物继续D盘。

仅将自审批快照移动到登录完成之后，保持每张耐久表/私有字节守恒检查；下一步用全新隔离编号复验完整新链，再补独立工具/作者自审/回归/APK及明确路径提交推送。失败和skip不隐藏，不重跑1100–1102，不使用正式签名或原库/现网。

## 1148与1149实际链

1148专项1/0/0/0、exit0/sourceUnchanged，Web源码逐hash无漂移。实际主管Web表单填FORMAL原召回/恢复包/摘要/截至和保管字段，丢响应后重载原键、真实HTTP重试、读面及390/720/1440布局通过；Playwright原日志/截图在`e2a-cross-20260925-1148/approval-workflow/formal-fault-browser`。主管/保管位置为合成夹具，原发布/领取/召回/上传由MockMvc处理；故障、审批/准备/激活、接收/最终提交/回读为真实HTTP，不能称原身份/真机或全来源实际产品输入。

同一隔离输入先产生真实非空TEMP围栏/归档/回执，然后执行FORMAL迁移；升级前后所有既有表/私有字节逐值一致，TEMP原键回放返回原字节。此处TEMP附件表未造非空附件，不外推附件升级证明。FORMAL原提交真实ABORTED、召回仍REQUESTED且stop/resolution为空；独立主管围栏撤原assignment/offline资格、禁用设备/撤PDA Session，无原机新STOP/ACK和handover_evidence伪造。

Android消费实际签名faultReceipt/manifest、原包前缀、Room确认/新assignment/续作，丢续作响应后重开原键重试；本轮新输入局部capture序号从1开始，全单序号继续。实际冻结正文经HTTP最终记账SUCCEEDED，原键重试/服务端重启回读不重复记账；原提交无dispatch。最终2笔dispatch/ledger、1个结果版本及精确整数克减量由SQL和独立工具核对。故障围栏及最终提交末审计分别注入SQLSTATE=45000/code=1644，全部耐久表回滚；Android成功回读丢响应/Room末写故障/重开仍保持原正文及唯一成功。来源、接收、重启成功三份当次Android XML各1/0/0/0。

作者自审发现FORMAL原字节实际损坏及首次并发覆盖尚缺；补反例后新1149专项1/0/0/0、exit0/sourceUnchanged，保留1148独立实际页面证据（此后只改测试/消费者，产品字节无行为变化）。1149错ID/hash/三截至、保管早于归档、缺文件/坏原文前置与回放/读面、撤权、自审批、减包或无处置凭据拒绝后全耐久表守恒；围栏/原包/原召回状态不可改，旧Session停止请求401。首次同键两个实际HTTP写者等待共享守卫锁，唯一FORMAL围栏/回执/outbox/审计、返回同原字节，marker及各HTTP原响应保留；最终链与三份Android XML继续各1/0/0/0。

1148/1149均有dump原件与哈希，独立重算一致、认证停机/原PID退出、19330/19434空监听，回执`d07-fault-1148/1149/retention.json`。完整输入/源码哈希/diff/命令/退出与原XML在`d07-fault-test-1148/1149`，原HTTP/Room/SQL/binlog/datadir在对应`e2a-cross`目录；失败1145/1147继续保全，未复用。

## 独立消费、页面回归与作者自审

`d07-formal-consumers-1148/1149`：原生Node RSA/issuer/audience/canonical、原请求摘要/原包/冻结正文前缀/签名回执和账务快照，以及Python只读SQLite/hash各exit0。1149扩展消费者独立核原设备DISABLED、PDA Session/assignment/offline授权撤销、非空迁移全部原表/私有字节及18组拒绝/回滚前后快照逐值一致；日志`node-expanded.log`与源码hash/result保留。仅独立工具、合成身份和开发密钥，不代独立人员/原身份。

`d07-formal-web-boundary-01`：4/0/0/0、类型构建exit0/sourceUnchanged，FORMAL丢响应原键恢复、原引用展示和390/720双动作布局；引用1148签名结构但HTTP是合成边界，单列。`d07-formal-temp-web-regression-01`：19/0/0/0、类型构建exit0/sourceUnchanged，既有TEMP故障/附件恢复、权限/存储/跨页/身份和布局受影响回归。实际产品小屏截图已由作者查看；Web契约解析exit0，日志`d07-formal-contract-01`。

普通server回归`d07-formal-server-regression-01`为3420/0/0/1、exit0/sourceUnchanged（Windows符号链接skip未执行，普通test不含IT）。定向unit-03为269/0/0/0；Android-01为33/2/0/0，错误的跨manifest绑定测试夹具失败保留，修后Android-02为33/0/0/0；compile-01/02/03及Android-compile-03均exit0/0测试，仅编译证据。Web-01/02/03/04为类型/构建记录，不能取代1148页面与独立边界。

作者自审在`.local/d01-d18-20261008/d07-formal-self-review-01.md`。FORMAL实际PDA产品页/模拟器、非空附件FORMAL组合与升级保全、非法跨源INSERT、无可信归档代传及R09跨截点仍属开发未验；D06/D07整体、D08–D18及第8节未通过，政策阻断/历史原资料/旧BCD与C01–C08保持。开发范围保存不等于完整FORMAL/D07收口或生产授权。

Android受影响回归`d07-formal-android-regression-04`为185/0/0/4、exit0/sourceUnchanged；4个一次性HTTP条件skip未执行，实际HTTP证据来自1148/1149。默认debug APK和lint任务exit0，未用release/正式签名或开发开关；lint报告任务UP-TO-DATE，沿Gradle输入复用并另存原XML/HTML/hash，0 errors/221提示，不伪造新生成时间。APK SHA256为`741946E9C627ADFB863E0A6B3FBB96AF0AE5B986392ED6954C04E2398ED2DA69`，一份保留在该目录，artifact-hashes.json和lint-result.json可核。

阶段保存前`d07-formal-close-23`保存77条完整dirty副本/hash/diff、Git/进程/监听/磁盘/33树迁移分配；该快照早于最后证据及看板文字，不覆盖后续断点文档。明确FORMAL路径与暂存/提交/推送/实际远端回执登记同目录；19条继承与原STOP三项逐hash保护，本地数据库/私有资料/原件/APK未上传或异地备份。下一子项是FORMAL实际PDA页面、非空附件及跨源INSERT反例，之后继续无归档/R09分段接入和D08–D18/第8节。
