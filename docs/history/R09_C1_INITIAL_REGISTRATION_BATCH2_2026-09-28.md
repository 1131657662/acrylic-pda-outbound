# C1 第二批：初次NORMAL双侧登记适配与验证断点

## 结论与提交

**C1部分完成：第一批组件已验；第二批生产适配已提交，真实HTTP验收未完成。** 不能用单测或迁移成功替代双侧登记验收，也不能据此进入C2。

用户授权分两批实施，第一批只新增，第二批允许R09生产适配；总时间盒3–4轮。本轮在两次IT启动失败、测试探针修正及定向单测复验后收尾，不再启动新attempt，继续真实验证交用户决定。不是发现已确证产品缺陷后扩大修复。

主树`D:/acrylic-pda-outbound`、分支`codex/development`，单助手；第一批实现`37beeaa`及记录`c146f5f`已推送，第二批实现`c974188`已推送。实现与本收尾文档分开提交。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 已实现范围

第一批[协议及单测记录](R09_C1_INITIAL_PROTOCOL_BATCH1_2026-09-28.md)确定独立`INITIAL_NORMAL`原件、可信原件比对、prepare/完成文件及持久化方式；仅新增4个生产组件和3个测试文件，22项单测通过，未改既有文件。既有看板/欠债/索引按“只新增”边界留到本批同步。

第二批`c974188`共22个文件，生产改动仅在R09组件及新增迁移：

| 范围 | 文件 / 行为 |
| --- | --- |
| 独立DB登记与查询 | `InitialNormalRegistration`、`InitialNormalTransaction`、`InitialNormalQueries`、`InitialNormalVerification`；将初次NORMAL登记与既有灾后关闭链分开 |
| 库外不确定状态 | `InitialNormalStore`增加耐久UNKNOWN记录；提交结果不确定时保留证据、拒绝继续登记/核验 |
| 既有R09适配 | `RecoveryNormalIntent`、`RecoveryNormalRegistration/Rows`、`RecoveryAdmissionEvidence/Queries/Verifier`增加初次路径；未将关闭链伪装成初次NORMAL |
| 独立冻结存储 | 新增[初次登记迁移](../../server/src/main/resources/db/migration/V49__initial_normal_registration.sql)及`recovery_initial_normal`；不改旧迁移，不回填原安装/原映射/原请求 |
| 验证代码 | 初次证据核验单测、测试域HTTP探针、真库/真HTTP IT、原业务事实快照、提交丢响应检查及连接层回归包装 |

没有生产HTTP登记端点，没有C2自动接线；核验的`admitted`不授予普通业务写权限，`ordinaryWritesAllowed`与`localDeletionAllowed`保持false。未改R05/R06/R07、ConfirmedV1Adapter、契约、默认生产配置或开关；未部署、发布或合main。

## 当次验证及全部红记录

以下计数均为`tests/failures/errors/skipped`，单测与IT不合并计算。

| 执行 | 结果 | 证据与等级 |
| --- | --- | --- |
| 第一批组件 | 22/0/0/0 | `unit-first.log`及`unit-first-xml/`，模型与真实本机文件系统单测 |
| 第二批初次证据单测 | 28/0/0/0 | `unit-second.log`及`unit-second-xml/`，非真DB登记 |
| IT编译 | 成功 | `it-compile.log`；仅编译，不算HTTP通过 |
| attempt 557 | **1/1/0/0，失败** | 新隔离MySQL迁移成功；Spring启动时探针重复HTTP mapping，未发出测试HTTP请求 |
| attempt 558 | **1/1/0/0，失败** | 第二次仍为同一重复mapping；同样未进入C1登记及连接回归，不能算另一种C1业务故障 |
| 探针注册回归加入后的定向单测 | **78/1/0/0，失败** | `unit-final.log`及`unit-probe-red-xml/`，新注册测试复现重复mapping；日志名含final不代表成功 |
| 独立controller修正后定向单测 | **78/0/0/0，通过** | `unit-probe-fixed.log`及`unit-probe-fixed-xml/`，10类，已从XML逐项汇总 |

78项包含：初次模型12、存储10、初次核验6、探针唯一注册1、既有Admission配置4/核验8、AdmittedDataSource17、LeasedTransaction5、NormalIntent9、WriterLease6。连接层这些是单测回归，**不是attempt105真库回归当次通过**。没有独立消费者/代理审查；属于作者验证与自审。

557、558均由既有`tools/e2a-provision-and-run-106.ps1`配合新主树测试profile运行，使用端口19330、HTTP目标19437、各自独立datadir/schema/账号。新profile逐字核对主树、datadir/schema/账号/端口和一次性marker，没有修改旧树守卫、复用旧库或删marker。两次在非空种子上完成新增迁移，XML有迁移成功记录；**迁移之后的C1 DB登记为0次、HTTP请求为0次、attempt105回归为0次**。这指未进入这些操作，不是声称已执行SQL验证登记行数为零。

失败根因位于新测试夹具：嵌套`C1HttpProbe$Probe`作为组件及显式bean重复注册，`POST /__c1/register`映射冲突。第一次处理后558仍重现，说明修正不完整；随后改为应用默认扫描范围外的`com.acrylic.c1probe`测试配置与独立`C1HttpController`，保留单一bean注册，并补唯一注册/两个HTTP映射断言。修正仅经单测验证，**尚未在完整Spring Boot真HTTP应用中复验**。

## 证据位置与环境

- 第一批日志/XML：`.local/r09-c1-batch1-20260928/`。
- 第二批日志/XML：`.local/r09-c1-batch2-20260928/`，含`attempt557-runner.log`、`attempt558-runner.log`、`attempt557-xml/`、`attempt558-xml/`和上表单测记录。成功的10份XML另存`unit-probe-fixed-xml/`，避免后续构建覆盖。
- 失败实例及fixture原件：`.local/e2a-cross-20260925-557/`、`.local/e2a-cross-20260925-558/`，完整保留；均有started、无completed marker，无`c1-http-*.txt`，库外初次根只有协调锁文件。
- 收尾检查19300–19500无监听，无在途C1/Gradle测试，只有既有Gradle daemon；系统3306及旧验证树未动。本次未重新核查旧树未提交内容，不将历史状态算当次检查。
- attempt已用至558，557/558不能重用；559未启动，后续执行前重新查重。未提交`.local`、私钥、密码、DB、原件或构建产物。

## C1验收逐项状态

| 验收条件 | 已有证据 | 未完成 |
| --- | --- | --- |
| 库外与DB双侧一致 | 库外模型/持久化及模拟DB证据单测通过；真实迁移成功 | 真HTTP登记→DB提交/回读→库外完成→核验整条链 |
| 缺一侧不接纳 | 缺DB/缺完成原件、可信原件和状态冲突等单测 | 真MySQL与真实HTTP正反例 |
| 原安装/UUID映射/原请求保持 | 模型原字节和来源绑定单测；IT已写原表快照比较 | 同一非空fixture登记前后逐表/原字节比较尚未走到；原安装现场资料组合未验 |
| 独立于灾后释放 | 初次API和存储已分离，单测NORMAL允许/RECOVERY_PENDING拒绝 | 真库从NORMAL初始状态成功登记、关闭历史/意图拒绝 |
| UNKNOWN与重启 | 耐久UNKNOWN拒绝单测；已写真实提交后丢响应用例 | 实际提交/丢响应/重启与排空验证 |
| 连接层历史105面保持 | 相关定向单测通过 | `C1EntryRegression`当次真实MySQL回归尚未执行 |

因此C1仍未达到验收标准。后续建议先在新独立attempt复验修正后的探针，完成上述HTTP/DB/UNKNOWN/连接回归，确认fixture原事实保持；若发现真产品bug立即停报，不放宽超时或跳过断言。

## C2顺序及其余未验

C1真实验收通过后，才建议按[入口评估](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)推进：G0操作租约/旧操作收尾方案→B1 HTTP/Spring Session→B2 PDA→B3导入→B4提交文件→B5来源页/对象文件→B6 Worker/提醒→B7导出文件→B8渲染进程→B9 Flyway→B10 BootstrapAdmin→全入口组合。此为开发验收顺序；运行时迁移/初始化许可必须先于业务入口。

各入口仍需单独授权，C1不替代操作生命周期保护。现场原资料、独立审查、跨进程竞争、Windows断电、Linux持久性及进程隔离、全入口关闭竞争/异步/Session尾保存和生产恢复均未验。A3剩余21类仍待发布前，526锁行为待专项，R07抬头撤销与D范围外组合保持原记录；不自动切换其他面。
