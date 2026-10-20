# D13 主管更正审批 admitted HTTP/P5 有界修复与验证（2026-10-21）

主管审批的真实 HTTP 请求在恢复围栏内先后暴露泛型 JDBC 读取和局部 SQL 不兼容。本批修复明确属于 APPROVE 调用链的消费者，保留 `getObject`/LOB 禁用及既有 SQL/效果门。最终隔离输入实际执行主管审批、一次更正记账、原键重放、同 JVM 应用上下文重启后的重放和 P5 后继。这里只记录合成来源的有界 L1，不代表完整 D13、L2/D18、真实原身份/原设备、最终候选或总目标第8节通过。

## 输入、写权与原字节

- 树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/origin/ls-remote 均为 `a36079537cb37b39923ad5936f69b723888bb11e`；main 未改。本聊天独占主树源码/共享文档/HEAD，另一活跃聊天处理另获授权的 test/M71 产品流程，本批不操作其设备、APK、服务器或数据，不套用其授权。
- 证据根 `.local/goal-20261021-resume/d13-approval-http01/`。`input-manifest.json` SHA-256 `d3f47ceb283a374b6f4add7e383e48553ab84c17cf9606c31ad3435d0a2e2b7e`，`wip-and-fixed-before.zip` SHA `827c7063b0915ec4a94025b0783b4fd33099d1d74058a20c96beb65f2dd539ae` 保存继承改动及固定文件原字节；文档收口前另存 `docs-before-closeout.zip` SHA `6be06a203646002b55cd9eb748da382345b2ae2080c3d556e987c5a7fa91a73a`。
- run1928 `inputs.json` SHA `e5ad81c530a9fb9853bd6b5222b98355e2721d2d4aea2fe89e68648844b534b3` 绑定5913个磁盘输入，文档收口前逐SHA无漂移；35条非看板继承脏路径逐SHA保持。`source-closeout.json` 记录六个源码/运行器原字节摘要与普通回归，构建后只改报告/索引/断点，不以文档更新机械重跑业务测试。
- `pre-run-resumed.json` 曾错误读取不存在的 files/wip 字段而只检查0条，不能作为继承守恒证据；原件保留。后续 `pre-run-inheritance-verified.json` 使用 dirty 字段实际核35条，且本批离线消费者、source-closeout 再次全量核同。

## 实际改动与自审

- `CorrectionCommand.Locks.document` 单列行锁查询指定 `Long.class`；不改文档锁/订单锁次序及权限。
- `CorrectionStore.documentFacts` 的三个双参数 IFNULL 改为等价 COALESCE，仍保留原字面量 `null` 哨兵。`lines`、`lockCurrentVersion`、`dispatchesForUpdate`、`effectiveScan` 使用明确 getString/getLong/getInt/getTimestamp/getBoolean，SQL NULL 字符串保留 null，布尔使用 wasNull，数量/重量仍是 Number。bale/commitment 单列锁读指定 byte[].class。
- `lockCurrentVersion` 的 SQL 内注释移为 Java 注释，因有限分类器明确拒绝 SQL 注释；原 PDA 来源回退 `COALESCE(original_submission_id,submission_id,'null')` 不变。
- `nextOutboxEntityVersion` 的 max(order row_version, epoch/order outbox max)+1 改用同一 SQL 语句中的 CASE；两个子查询在同一语句快照内，epoch绑定两次，order绑定一次。订单锁、强制事务和 insertOutbox 版本谓词保持；普通和专项回归见下文。
- 未修改 JDBC/SQL 分类器、效果门、权限、契约、迁移、生产配置和业务政策；未顺带修撤销/对账命令的其他泛型读取。共享方法回归不外推所有命令 admitted 验收。
- 新增 `E2aAdmittedCorrectionApprovalIT`、TCP消费者 `CorrectionApprovalAdmittedHttp`、合成准备/原始 JDBC 消费者 `CorrectionApprovalAdmittedInput` 及 `docs/parallel/R09/tests/run_d13_correction_approval.py`。固定密码只属于既有 IdentityFixture 的随机合成主体；生成密钥、cookie/CSRF、私有配置不入库。测试只保留响应必要头，CSRF正文不落盘。
- 既有存储/命令大文件本批不拆分；这是已有权范围内的兼容修复，避免为代码长度规则扩大重构。新增文件按职责分离。自审仍由本助手完成，不声称独立人员审查。

## 原失败和新依据

各轮均新目录，拒绝复用旧run；以下失败原HTTP、XML、日志、身份、dump和输入保持，`failure-run*.md` 保存指纹与修复依据。

| run | DB / HTTP | 实际结果 |
| --- | --- | --- |
| 1925 | 19354 / 19476 | CREATE200、权限拒绝403；自批预期403实际500，Locks.document 泛型读取触发 getObject 禁用 |
| 1926 | 19355 / 19477 | 显式行锁读已通过，documentFacts 的 IFNULL 被有限SQL门拒绝，自批仍500 |
| 1927 | 19356 / 19478 | 自批403恢复；合法审批到outbox时 GREATEST 被拒绝，事务回滚，不能称审批通过 |
| 1928 | 19357 / 19479 | 最终 admitted HTTP/P5 IT实际1项、0失败/错误/跳过，Gradle和运行器exit0 |
| 1929 | 19358 / 19480 | 追加共享outbox回归的本地运行器端口与旧夹具19434不符，首次CSRF连接失败，未到业务断言 |
| 1930 | 19359 / 19434 | 仅本地运行器端口改为核实空闲的19434，新输入完整专项IT1/0/0/0 |

- 每次失败都有新栈/新配置依据，不是同根因无假设重跑。run1929失败不修改产品/旧测试断言；原运行器另存 `run-outbox-regression-before-port.py`。
- run1925原XML SHA `fb2f65750fb61a87a70da2d8ecbf71fe22fa98b511c3e584b92d3fffd6df5fd`；run1926 `a380cc9a058222e1ee2562934631048d7436335d5fbe351e34d9d791baf52333`；run1927 `ea1cbf13f2c1de262dcdec8b64eb029c57fe77d7cd0dd04d66e0750806187113`。原件不被后续成功覆盖。

## 最终 HTTP、记账和原件消费

- 命令：`C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_correction_approval.py --run 1928 --port 19357 --http 19479`；内部 `gradlew.bat -p server integrationTest --tests *E2aAdmittedCorrectionApprovalIT -x :report-rendering:prepareFont --offline --no-daemon --console=plain`。不渲染报表，排除字体准备不计报表验收。
- 合成已完成来源由既有 SupervisorCorrectionFixture 在 admission 前准备。admitted上下文的所有业务命令用真实Cookie/CSRF及TCP，不以MockMvc代替；不冒充原设备/原身份出库。22次TCP包含8次更正业务POST：4个200、3个403、1个409。其余为合成登录及CSRF；11份保留正文SHA核同，11份CSRF只留摘要。
- 无审批权maker拒绝403、checker无建单权拒绝403；maker临时获主管角色仍自批403，角色finally完整恢复。checker合法APPROVE签名状态EFFECTIVE；数量2不变、结果重量601500→601501，恰一条WEIGHT_DIFF（quantity_delta=0、weight_delta_g=-1）、一个effect、文档两份命令回执，仅新增一版结果。
- 同主体/同原键/同正文重放200且逐字节相同，异文同键409；同JVM上下文关闭/重新启动后原键重放200、账务不重复。四份原JWS均由JCA及Python cryptography离线验真；三次APPROVE响应SHA相同 `7e38ee75cba21d2abac3937d9b0c6fd2e5a8bb213451f3686f48c9b26a25856f`，五能力均false。
- 五组权限/自批/角色恢复/原键/重启重放快照，每组178张非会话表、6存储文件完全一致。四张非空原事实表 submission、submission_payload、dispatch_record、inventory_ledger全行保持；原order_result_version各行保持，只增一版。整轮13张合法变化表完整列于 `originals-consumer.json`，不声称全库零写入。会话表按设计不纳入守恒断言。
- 两个admitted上下文根排空、效果检查完成，P5独立后继进程stdout=STARTED。上下文重启仍是同测试JVM，不称独立服务部署或完整恢复。
- `originals-consumer.json` SHA `c892e5a89537208c5aa328e5f6c44c41346e738d7619d205c3aa8052921887c9`；最终JUnit原XML SHA `94c9d6231a914c3bb97fa633abc02a6113118fbb43075bcf6ca3eedde0721278`，test-result SHA `8dc9e186d16332580365232a77c34b16e1ad417efb868a0da92d8a839afbb8fa`。消费者为同助手离线工具，不声称独立人员或另一次HTTP。

## 回归、清理和未验

- 同产品源码普通 `gradlew.bat -p server test --offline --no-daemon --console=plain` exit0，310 suites/4804项，4785实际通过、0失败/错误、19跳过。原310份XML及逐suite哈希保存在本根 ordinary/xml 与 result.json；结果SHA `1e5bbf12d02e92e4906fe6b04f8604bd17f5599badd7606675a326618e97aee5`。跳过按SKU外部向量1、closure外部资料9、review originals资料8、Windows符号链接环境1逐方法记录，均不计通过。
- 共享outbox表达式另用新隔离run1930执行既有 E2aCorrectionOutboxIT：四更正类型、回滚/原键、订单领先/outbox领先分支和强制事务边界实际通过。13组前后快照相同、7组order/outbox/projection版本一致。XML SHA `94db6af3a6a50bc4d512c6d38ad14d4eb2e8d29ab965806da96d81f38a160fb2`，离线 `outbox-consumer.json` SHA `5d82fab69ebdb1f17260085a699199f9dfbe02b90346f8dc6a2838ff1f20f24b`。本地消费者初稿错估25组，实际枚举13完整组，断言写出前失败；修正消费者计数后核同，不改原件/测试、不重跑业务。该回归不是其他命令admitted全面验收。
- 六run均核隔离schema/user/datadir和认证shutdown exit0，进程退出、专项DB/HTTP无监听；共享13316指.local/mysql/data、系统3306身份未知，本批均未连接/操作。共享Gradle/Kotlin及另一聊天在途test/M71操作不动。debug Keystore SHA与输入核同；正式身份材料只读摘要，不用于构建签名，本批无APK构建或覆盖。
- 按持续授权仅清六个停机run的data/binlog：18明确目标、2082文件、逻辑长度1335750423 bytes；两次卷增907411456及454828032，总计1362239488 bytes（约1.27 GiB）。逐文件SHA/命令/输入/身份/dump哈希在cleanup-plan与cleanup2-plan，两个execution均exit0/无残留。首轮脚本正则路径校验错误在删除前退出，原脚本和SHA保留后改为明确路径白名单。两个前置JS传输语法错误未执行任何工具或删除。
- 原HTTP/签名/XML/日志、六dump、数据库身份、P5配置、输入ZIP、原资料、Keystore、最终APK、全部继承改动和生产边界文件保持；不将清理/提交计验收。无原件异地备份声明。
- 本批未验完整更正/撤销/对账admitted链、Android/Web完整消费者、非合成资料/原身份、原设备、完整备份恢复、独立API-worker部署、最终整合输入及第8节。已批准四状态筛选沿既有实现/证据，不重复开发。总目标active、未完成。
- 收口逐路径摘要见本证据根prestage-manifest.json（45条展开脏路径、35条继承保持）、self-review.json（310份普通XML及两个专项XML原SHA复核）、environment-closeout.json及final-evidence-manifest.json。安全扫描唯一命中为既有随机隔离主体的固定测试密码，本批九路径未见生产凭据或真实用户资料，不代表全仓历史秘密审计。收口时共享13316已无监听，未连库、未作停机推断，系统3306身份仍未知。ADB首次PATH解析失败在写文件前退出，改已安装SDK绝对路径枚举；收口脚本一次字符串转义错误在解析阶段退出，修正后只整理证据，未重跑业务。
- 下一动作按D13证据映射核剩余有效入口和信任输入，只补实际缺口；不从本批共享方法修复外推其他命令已验。换机进度承接、多跳、接收者确认/激活、旧run03不恢复；七类生产决定/TEMP生产归档fail-closed，localDeletionAllowed=false，正式签名/原库现网/main/部署/Release持续关闭。
