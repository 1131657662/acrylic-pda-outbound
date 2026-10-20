# D13 更正附件 admitted HTTP/P5 有界修复与验证（2026-10-20）

真实 CREATE 请求在恢复围栏内返回 500：既有更正消费者使用了被明确禁止的 `ResultSet.getObject`，随后暴露出局部查询使用未分类的 `IFNULL`。本批将受影响消费者改为显式 JDBC 类型读取及等价 `COALESCE`，没有放宽 JDBC/SQL 效果门。最终隔离测试覆盖附件上传、CREATE 引用、主管读取、拒绝与权限恢复、应用上下文重启后原键重放。这里只记录有界 L1；不代表完整 D10/D13、L2、D18、最终候选或第8节通过。

## 输入、范围与保全

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`；输入 HEAD、origin 与 `ls-remote` 均为 `0d5dd727bf7e2ebbbe63e52d527acb806a67c5d0`，main 未改。本聊天独占写权，另一项目聊天 idle；不套用其部署授权。
- 证据根 `.local/goal-20261020-resume/d13-correction-attachment01/`；`input-manifest.json` SHA-256 `53b1a3a37d488202d185f84d1e8bb8d0b715dbc3e6147dbcfe4622583111c94d`，记录36条展开脏路径、拟新增路径及三份文档原哈希。编辑前 `wip-before.zip` SHA `f21c9d0173b60842cc2fed9e19c72cc4cde23b9a21a7ca6e34ccf81e5a72f71a`、`fixed-before.zip` SHA `1c44a6a2a420b449034490739e58fc6d9f372741aa2567c2f562b8be2efc860a`、`product-before.zip` SHA `9e4ea918c83a11cd05f27829c521b295e7b4be70c2f3313c1b8f9d836cb62218` 保留原字节。
- 两份产品源码：`CorrectionCommand.java` 的订单锁查询指定 `byte[].class`；`CorrectionStore.java` 的回执使用 `getBytes/getString/getTimestamp`、dispatch 事实显式保留 SQL NULL 与布尔区别、确认时间使用 `getTimestamp`，相关双参数 `IFNULL` 改为 `COALESCE`。`sourceRecoveryEpoch` 和原 fallback 保持；未扩改其他更正操作。
- 新增测试 `E2aAdmittedCorrectionAttachmentIT.java`、TCP 消费者 `CorrectionAttachmentAdmittedHttp.java`、合成输入适配 `CorrectionAttachmentAdmittedInput.java` 及运行器 `docs/parallel/R09/tests/run_d13_correction_attachment.py`。测试中的固定密码仅用于 `IdentityFixture` 合成账号；生成私钥只留 `.local`，不入 Git。没有真实账号数据、原身份或生产连接。
- run1924 的 `inputs.json` SHA `ff2813fa32a060b91fd5d4849041d7a4aece0d86d08def4f822087fa880c8aeb` 绑定5908输入；文档更新前逐SHA全部相同，35条非看板继承路径逐SHA保持。六处源码及核心输出的完整哈希见 `source-closeout.json`；构建后只更新报告/索引/断点，不机械重跑业务测试。
- 旧附件容量、SQL 回滚与重启证据见 `R07_CORRECTION_ATTACHMENTS_2026-10-01.md`，仅沿历史输入复用；不能代替本批 admitted/P5 真实HTTP，也未重跑这些旧用例。

## 失败、定位与修复

每轮新建目录并拒绝复用已有目录，完整日志/XML/HTTP/身份/dump均沿各run保留；不会把失败改写成通过。

| run | DB / HTTP | 实际结果和下一依据 |
| --- | --- | --- |
| 1921 | 19349 / 19468 | CREATE 500，`CONTROL_EFFECT_UNCLASSIFIED ResultSet.getObject`，定位 `CorrectionCommand.Locks.order`；改受影响消费者的显式读取，保留 getObject/LOB 禁用 |
| 1922 | 19351 / 19470 | 500移至 `CorrectionStore.orderFacts` 嵌套 IFNULL；有限分类已允许 COALESCE，采用等价查询，不扩分类门 |
| 1923 | 19352 / 19472 | orderFacts 已通过，500移至 ledgerCount，静态调用链确认 ledgerWeight 同一遗漏；仅改对应查询 |
| 1924 | 19353 / 19474 | IT实际1项、0失败、0错误、0跳过，Gradle与运行器 exit0 |

- 每次有新栈位置和可验证修复依据，非无新假设机械重跑。`failure-run1921/1922/1923.json` 保留失败指纹、输入及XML哈希。自审发现一次补丁漏掉 `sourceRecoveryEpoch`，已按原字段和fallback恢复后才执行1924，最终原签名响应也含该字段。
- 初次 `compileTestJava` 因新 `drained` 方法漏声明受检异常失败，修正后编译 exit0，见 `compile-attempts.json` 与失败源码保全；一次 Python 准备脚本 TypeError 未启动IT，见 `run1923-prep-error.json`。均不算业务通过。
- 设计初稿 `design-and-exit.json` 的“无产品改动”是发现失败前的计划，实际范围由上述失败依据扩为两处消费者修复；原设计文件保留，不用旧计划掩盖最终改动。

## 最终实际覆盖与原件消费

- 命令：`C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_correction_attachment.py --run 1924 --port 19353 --http 19474`。运行器内部为 `gradlew.bat -p server integrationTest --tests *E2aAdmittedCorrectionAttachmentIT -x :report-rendering:prepareFont --offline --no-daemon --console=plain`。依赖已校验字体，本测试不渲染报表；排除字体准备不计报表验收。
- 隔离库 schema `acrylic_e2a_cross_20260925_1924`，身份/端口/datadir认证输出见 `identity-before.stdout` 与 `identity-final.stdout`；本轮未连接系统3306或共享13316。四run均 `mysqladmin shutdown` exit0、进程退出且专项端口无监听。
- 37次真实TCP HTTP，其中业务20次：GET13、PUT5、POST2。25份保留正文逐SHA匹配；12次CSRF正文不落盘，只保留摘要，cookie/凭据不写HTTP回执。上传原字节4 bytes，原键同文重放200、异文409；未绑定附件对主管404、无更正权上传403、外人下载404；绑定后主管和原人读200，跨主体403、错仓404、匿名401。撤主管角色403、撤原人仓库404、撤session版本401；权限/仓库/版本均在finally完整恢复，再读200。
- CREATE只生成 `SUBMITTED` 更正申请及附件引用，不是主管审批、库存变动或放行。原CREATE和上下文重启后的原键响应逐字节相同，SHA `bc2275708e72a7554b1ee656fd405af3a6881c8a92d631ada9b44491b0b51a27`；JCA及同助手的Python `cryptography 48.0.0` 离线消费均验过原签名，五项能力标志均false。
- 8组读取/拒绝/原键重放快照，每组178张非会话表、6存储文件逐值/逐SHA守恒；整轮合法变化仅7表：api_idempotency_request、correction_attachment、correction_attachment_use、correction_command_receipt、correction_document、correction_line、operation_event。五张非空原事实表 submission、submission_payload、dispatch_record、inventory_ledger、order_result_version 保持。会话表按设计不纳入守恒断言。
- P5根排空、两次上下文关闭，独立后继进程 stdout=`STARTED`。Web两次启动是同测试JVM的应用上下文重启，不能称独立部署重启；合成完成单由既有夹具准备，不能称实际PDA原身份确认。
- 离线消费者 `audit-originals.py` SHA `0dba3a36658ea6d0b5318f4dd924ef142434fa79d279c36e23159209840e4e69`，`originals-consumer.json` SHA `cef9c73caf51c9d8ea926d3b08f803cbb03ce8ad5fdeb4dcfe564e486806ff48`。它独立读取原件并核签名、原正文、快照和输入；执行者仍为同助手，不声称独立人员审查。
- run1924 JUnit XML SHA `d0b35db71b7c8909827905fa1f2159d404dc226f3a7ec6b89a41378877c44f75`；`test-result.json` SHA `faf8f70ada02fd400f923250b7a01921c2889cf48c72bd6bc8dcf50f2ad5f91a`。原XML和三轮失败XML均保留。

## 普通回归与未执行项

- 同源码执行 `gradlew.bat -p server test --offline --no-daemon --console=plain`，exit0；310 suites、4804项、4785实际通过、0失败、0错误、19跳过。`ordinary/xml/` 保存本轮全部310份XML，并逐SHA核同；运行器按本轮mtime拒收旧XML。`ordinary/result.json` SHA `03377c3ece8b1c1af61cbdbb9e26e90446ad0817bf9067ab16c34696c6df17ee`，含每个suite哈希和19项方法清单；`test`排除IT，不能替代上面的专项执行。
- 19跳过：SKU_PARITY_OUTPUT条件1项；E06_CLOSURE_ARCHIVE条件9项（RelayIndependentClosure3、Collection6）；E06_REVIEW_ORIGINALS条件8项；Windows符号链接权限环境1项。均不计通过，不为增加计数恢复取消链或伪造原身份资料。
- 本批未测主管审批及后续记账、其他更正命令的完整admitted路径、Android/Web消费者、非合成资料、原设备/原身份、完整恢复和实际最终候选。旧getObject/IFNULL可能存在于其他操作，不据本批局部修复外推全面兼容。

## 清理与接续

- 用户持续清理授权下，仅清理四个用途结束、认证停机且无消费者的隔离run之 `data/`、`binlog.000001`、`binlog.index`；12个目标、1388文件、逻辑长度889870160 bytes，逐路径/大小/SHA记录在 `cleanup-plan.json`，删除前再次全量核同。auto.cnf身份另存 `auto-cnf-retained.txt`；完整dump、SQL/原HTTP/XML/签名/快照/密钥/日志/依赖与运行命令保留。
- 首次内联删除命令在CreateProcess前被策略拒绝；任务专用脚本第一轮根路径解析错误exit1，删除前退出，原脚本保全；修正根路径后同等计划/哈希/停机/消费者/监听/绝对路径检查均通过，删除exit0。`cleanup-execution.json` SHA `05159df83dc586d3adc9b114095b463b4021272a49e21fc311f87ed0ee3ef40a`，D余量30890528768→31798149120，卷实增907620352 bytes（约0.85 GiB）。逻辑长度与卷余量分别记录；12目标均已不存在。
- 四dump SHA分别为1921 `fa6a6ff243b4c2e2d9c7111e6a06ee3319e9740f911961a0b11bb8197f1875e8`、1922 `4fd18281bec179589bccb00f2144e34e5cd9454c04fb718107e28611a3156b4d`、1923 `bdca1d6c1d1f44e273bc3232af335bd44f7128bdd2b5a83f2565c196d53126b4`、1924 `2561e6e87d772fb03ca3012b071a74f1dd336dcc49b32b19cff126db0d2bbd98`。共享Gradle/Kotlin、MySQL、缓存、旧失败unknown库、原资料/身份/Keystore、最终APK及全部继承改动不动。
- 本批不生成或覆盖APK；生产默认、正式签名、原库/现网、main、部署/Release与七类决定保持关闭，`localDeletionAllowed=false`。开发备份与清理不算验收进展。
- 下一有界任务先核对D13仍缺的有效入口与现有输入SHA；对真实缺口补最小组合，不重跑本轮已通过用例、不扩写全部更正链，不恢复换机承接、多跳、接收者确认/激活或旧run03。总目标active、未完成。
