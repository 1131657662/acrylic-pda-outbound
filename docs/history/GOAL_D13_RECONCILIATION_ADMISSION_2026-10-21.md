# D13 缺账对账 admitted TCP / P5 有界验证

## 范围与结论

- 本片补既有缺账对账context、发现、受控原附件提交、独立主管审批的恢复准入真实HTTP消费者；沿R07/P06既有政策，不新增业务政策。
- 最终run1936 IT1/0/0/0、51 TCP/20业务POST；普通回归及受影响原缺账业务回归各有新原件。仅合成有界L1、同JVM上下文重启和P5独立后继，非独立API/worker部署、完整D13、L2/D18、实际最终候选或第8节。总目标active、未完成。
- Android/服务端/数据库版本见[看板生成事实块](../ORCHESTRATION_STATUS.md)；本片不构建替换APK、不操作设备或既有业务库。

## 输入与自审

- 固定树D:/acrylic-pda-outbound，codex/development；输入HEAD/origin/ls-remote为38736c687bd3d8ecc89bbc2a3e421e72dfa5f13a；main为c6e9e0c238c657baecbf699ba6fdbada5d979b86。本聊天独占源码/共享文档/HEAD、无子代理；另一项目聊天实查idle，不套用其test/M71授权。
- 证据根.local/goal-20261021-resume/d13-reconciliation-http01。input-and-exit.json、wip-and-fixed-before.zip、tracked.patch保全36条输入展开脏路径；除看板外35条继承逐SHA核同、保持未提交。五产品旧字节在product-before-jdbc-fix.zip，文档旧字节在docs-before-closeout.zip及board-before-*.md。
- 五产品修改：CorrectionCommand案件锁指定Long；ReconciliationContext仓库/订单锁指定byte[]；ReconciliationFacts单列UUID/JSON指定String、锁指定byte[]；CorrectionStore原receipt mapper原样提取供自身和两处引用读取共用、四个双参IFNULL改COALESCE；ReconciliationRequestStore使用相同mapper读取原签字节。
- 保留原SQL/锁序、NULL字面量、布尔/字节/时间语义、固定表列白名单及验签绑定。未改SQL/JDBC分类器或效果门、LOB/getObject禁令、权限/契约/迁移/生产配置/政策。
- 新ReconciliationAdmittedInput、E2aAdmittedReconciliationIT、run_d13_reconciliation.py；CorrectionApprovalAdmittedHttp只增GET。合成成功dispatch和实际附件由既有fixture在admission前准备，不能称原身份/现场可信事实。
- source-review.json按security-practices、testing-best-practices、single-responsibility和sql-joins自审；同助手/离线工具，不称独立人员。未为长度扩大旧类重构。

## 命令、失败与新依据

命令：C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_reconciliation.py --run N --port P --http 19434；N/P依次1933/19363、1934/19364、1935/19365、1936/19366。均新隔离，不复用失败库。

| run | 结果 |
| --- | --- |
| 1933 | 编译失败：新drained未声明checkEffect的SQLException；未进测试/业务，无当次XML。仅新测试补throws。 |
| 1934 | IT失败：空JSON_ARRAYAGG返回SQL NULL导致raw accounting parse(null)，尚未启动admitted业务；仅夹具补空JSON_ARRAY。 |
| 1935 | IT失败：真实context GET500，ReconciliationFacts.order泛型queryForList触发禁用getObject；178非会话表/6文件守恒。依栈修五产品，不放宽门。 |
| 1936 | IT1/0/0/0，Gradle/运行器exit0，认证dump/停机成功。 |
| 1937 | 原E2aReconciliationMissingPostingIT受影响回归，新隔离IT1/0/0/0，运行器exit0、认证停机；不是admitted/P5证明。 |

- 普通命令：./gradlew.bat -p server test --offline --no-daemon --console=plain。
- 1937命令：C:/Python314/python.exe -B .local/goal-20261021-resume/d13-reconciliation-http01/run-original-reconciliation-regression.py --run 1937 --port 19367 --http 19434。
- 三次失败有不同新依据，原failure.json、旧测试zip、日志/XML/dump保留。1933无XML、不借旧计数。
- 补充观察曾错误断言附件SHA应存在私有文件快照；实际原件在DB content_bytes。正确消费者核71原字节/摘要/长度/原人/仓/案件绑定和attachment行守恒，见attachment-originals-consumer.json。观察假设错误保留，不改产品/原证据。
- 接续期间本地传输/路径/UTF-8只读诊断错误未改产品；环境采样首次adb不在PATH，按已安装SDK绝对路径补核。

## 原件、守恒与账务

- 51 TCP含20业务POST：9×200、3×403、7×409、1预期500；另三个context GET200只读。原HTTP摘要绑定原body SHA，CSRF/login秘密不保留。
- 发现/提交人为同maker，审批为不同checker；审批逐ID引用原提交签字。三命令原响应/同上下文原键/同JVM上下文重启各3份逐字相同；异文409、不二次记账。9JWS经JCA和Python cryptography验真，RS256/公钥/声明核对、五能力全false。
- 末operation_event注入SQLSTATE42000导致预期500，178非会话表/6文件全回滚，移除后同原键批准成功。context/权限/事实摘要错配/末SQL/原键/重启共10组全量守恒。
- 唯一MANUAL_DEDUCT数量-1、重量-300500g、业务键引用原dispatch；reconciliation_posting逐ID绑定case/ledger/approval。stock NOT_DEDUCTED→DEDUCTED、row_version1→2；cycle1、blocked=null、upstream1、原重量保持。
- 原结果始终version1/数量2/重量601500g；submission/submission_payload/dispatch_record/inventory_ledger/order_result_version/bale_commitment/correction_effect全行保持，不伪造新结果。
- 附件原行保持，仅增一条案件引用；71原字节SHA为42352a430ae67399d390734d93fcc6d1e2167d1fb18fd268127cdcbe44cb1e23，不仅声明摘要。
- P5 leases排空、独立后继stdout STARTED。originals-consumer.json已核5921输入和35继承SHA全同；同助手离线工具不是独立人员。

## 回归与哈希

- 普通test exit0：310 suites/4804项，4785通过、0失败/错误、19跳过；SKU_PARITY_OUTPUT缺1、E06_CLOSURE_ARCHIVE缺9、E06_REVIEW_ORIGINALS缺8、Windows符号链接权限1。逐项见ordinary/result.json，skip不计通过。
- 310份XML均晚于当次输入/命令起点并原样复制逐SHA。普通测试期间仅刷新看板断点且保存旧字节，5921项中只有看板漂移、行为/测试/配置/依赖不变；1937输入5921项收口前全同。
- 原缺账回归覆盖受控附件读取、独立审批/并发原键、撤权/边界及后续实物账务冲正；原件见1937/accounting与XML，不外推全部对账命令。
- run1936 inputs SHA 7f5ae2d29cd1b4d8ce86c122ba983d84df06fce27e10131e6519d3602a2a05a5；XML SHA d5113a2cb0fc13c391f104eb221e25062fb5be2ae6620c6d73548ba0fff70b24。
- run1937 inputs SHA b1c88baab87087c34ff359aa4dee56d73a9bb16a972bd0f1b30fd95d0bfa66fb；XML SHA 444721cbf5607d2337dcead890e8a476c98039d2e7774a6bb8e11af33cbdf0c6。
- 三命令原body SHA：发现18bd903e5ae732aa4ec9573a442f7842a2bb62499fd1ab4a0421a9c8e02cdeca、提交2a2c45fa0a19e80b8ff9f5214b83fe8b0a947d244dee0d9f196229f6a972f1f2、审批6a8d768cd072b4983040c090491bb523a836ba91bf02939aed00c407f7e3deaa。
- 原件.local/e2a-cross-20260925-1933至1937；证据根final-evidence-manifest/prestage/postcommit与Git回执。Git只备份明确源码/报告/短索引，本机私有证据不虚称异地备份。

## 环境、清理与未验

- 五MySQL均认证dump/shutdown exit0、进程退出；专用DB19363–19367/HTTP19434收口核无监听。系统3306身份未知不连/不停，共享13316采样无监听不推定停机者，共享Gradle保留。ADB只枚举、不操作M71/模拟器；原身份/Keystore保持。
- 仅清五个认证停机且无消费者的data/binlog：15目标1555文件、1096751584逻辑字节，卷释放1114845184字节（约1.04GiB），D余量30223032320→31337877504。
- cleanup-plan/execution保存逐SHA、输入/命令/身份/dump、依赖锁/reconstruction-inputs.zip，auto.cnf另存。全部原HTTP/JWS/XML/日志/签名/实际附件/数据库快照/P5/dump/原资料/APK/Keystore/35继承保持。开发清理不计业务验收，不开放删除能力。
- 未验其他对账admitted链、Android/Web完整消费者、非合成来源/原身份/原设备、实际规模/长时、独立部署、完整备份恢复、实际最终候选及第8节。旧审批/撤销只沿各自输入，不拼整体通过。
- 不恢复取消的换机承接/多跳/接收者确认激活、不启动旧run03；原库/现网、正式签名、main、部署/Release关闭，七类生产决定/TEMP生产归档fail-closed、localDeletionAllowed=false。
