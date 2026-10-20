# D13 两阶段更正撤销 admitted HTTP/P5 有界 L1（2026-10-21）

## 范围与结论

按 [R07 政策 P-R07-08](../BUSINESS_POLICY_R07.md) 消费既有“原审批人发起撤销、原申请人确认”产品入口。本批只新增两份测试及一个隔离运行器，产品源码、权限、SQL/JDBC 分类器、P5 围栏、契约、迁移和生产配置均未改变。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。

run1932 在新隔离 MySQL、真实 TCP Cookie/CSRF 及 admitted P5 根上实际执行 IT **1 test / 0 failures / 0 errors / 0 skipped**，运行器和 Gradle exit0。原审批人发起后仍保持有效账务，原申请人确认后逐笔冲正一次；末条 outbox 失败全量回滚，同原键重试成功；两阶段原响应及上下文重启重放逐字节一致。run1931 的未知结果围栏失败单列保留，不能计通过。

这是合成身份/来源的有界 L1；应用上下文在同一 JVM 重启，P5 后继另进程启动。不能称独立 API-worker 部署、原身份/原设备、完整 D13、L2、D18、实际最终候选或总目标第8节通过。

## 输入与改动

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/origin/ls-remote 为 `fe46dd11fcd0d3ec5cdbef4d13e3948dde161945`；main 为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`，未改 main。
- 本聊天独占主树源码、共享文档及 HEAD；无子代理。另一项目聊天在收口复核为空闲，其 test/M71 授权不套用，本批不操作该设备、APK、test 服务或其报告。
- 继承输入为36条展开脏路径（35条非看板继承 + 看板）；三个新文件加入后39条。文档收口及提交后的精确清单另存，生成事实的 `dirty_entries` 不是展开路径数。
- 35条继承文件逐 SHA 保持，含已取消链、physical-disposition、规则、archive、四份部署报告及继承测试/脚本；不暂存、不还原、不覆盖。

| 新增路径 | 行数 | SHA-256 |
| --- | ---: | --- |
| `server/src/test/java/com/acrylic/outbound/inventory/CorrectionWithdrawalAdmittedInput.java` | 69 | `8dc7f2d2689b4a4f38fee18a94766492a440dddecc6beff4133340e1ad5d4cc2` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aAdmittedCorrectionWithdrawalIT.java` | 150 | `7b2eddd47b689e3a7d31e12b08d3741be994119f8c1f1d89060f43b4ebebdf95` |
| `docs/parallel/R09/tests/run_d13_correction_withdrawal.py` | 49 | `28145524995b2d150d682c8b070764a7ca1d01dc97401d2ec27f6f5b72807272` |

复用已提交审批输入工厂与真实 HTTP 消费者；额外合成同仓其他申请人/审批人验证所有权。原始 JDBC 只用于隔离夹具、注入故障及核账，不代替产品 HTTP 命令。运行器拒绝复用旧 run，仅复制当次 marker 之后新产生的 XML，finally 认证导出/停机。

## 实际命令与失败归因

在固定树执行：

```powershell
C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_correction_withdrawal.py --run 1931 --port 19360 --http 19481
C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_correction_withdrawal.py --run 1932 --port 19361 --http 19482
```

两次 Gradle 命令均为 `gradlew.bat -p server integrationTest --tests *E2aAdmittedCorrectionWithdrawalIT -x :report-rendering:prepareFont --offline --no-daemon --console=plain`。命令参数、输入锁定、数据库身份、stdout/stderr 和退出码沿各 run 原件。

run1931 已到达 CREATE/APPROVE、WITHDRAW_PENDING 和四次身份拒绝。测试触发器注入 SQLSTATE `45000`，超出既有 knownStatementFailure 接受的23/42类；真实 P5 围栏按未知结果 fence/halt70。Gradle 命令 exit1，未生成当次最终 JUnit XML，运行器没有复制前次 XML；不算通过，也没有产品撤销缺陷证据。unknown记录、poisoned root、原 HTTP、binary、日志及认证 dump 保留。只将新夹具注入改为可确定语句失败的 `42000`，产品分类器和围栏原字节不变；不用失败权威根续跑。

run1932 使用全新 DB19361/HTTP19482，IT 与运行器实际 exit0。run1931 binary 离线打印首次因控制台 GBK 编码失败，只是观察输出失败；原件未改，后续 UTF-8/ASCII JSON 消费成功。失败分类见本批 `run1931-failure.json`。

## 原件消费结果

- 真实 TCP 共46次；更正业务 POST 17次：8×200、4×403、4×409、1×预期500。不把登录/CSRF或预期拒绝计为业务成功。
- 原审批人 WITHDRAW 得 WITHDRAW_PENDING；有效结果、账务和占用未回退。原申请人 WITHDRAW_CONFIRM 得 WITHDRAWN；数量2保持，重量601501g回到601500g。
- 原 WEIGHT_DIFF 数量0/重量-1g 原行保留；仅新增一条 WITHDRAWAL_RESTORE 数量0/重量+1g，restores_entry_id、原更正行和业务键逐笔引用原分录。effect仍1，文档回执2→3→4；仅审批与撤销新增两版结果。
- 发起方无权/非原审批人、确认方无权/非原申请人四次403；末 outbox 注入42000导致500后178张非会话表和6文件全量相同，去除触发器后同原键确认成功。
- 两阶段各有原响应、同上下文重放及上下文重启重放；三份字节一致，同原键异文在重启前后各409。五组拒绝/回滚/原键/重启快照各178表/6文件相同。
- 8份业务200响应 JWS 由 Java JCA 与 Python cryptography 验真，五能力均false。非空原 submission、submission_payload、dispatch_record、inventory_ledger 全行保持，原结果及审批结果原行保持。
- P5 活跃根和 leases index 排空，围栏仍有效，独立后继进程 stdout 为 STARTED。
- 同助手离线 Python 原件消费者核5917项运行输入无漂移、35项继承 SHA 全同；不是独立人员审查。

与 run1928 比较1120份产品源码逐 SHA 不变，复用[审批批次](GOAL_D13_CORRECTION_APPROVAL_ADMISSION_2026-10-21.md)实际普通回归310 suites/4804 tests、0失败/0错误/19 skips及共享 outbox run1930。19 skips（SKU外部向量1、closure资料9、review原件8、Windows符号链接1）仍未执行，不计通过；本批不机械重跑同源码普通测试。此次三个新测试/运行器已在 run1932 实际执行。

## 证据位置与哈希

证据根 `D:/acrylic-pda-outbound/.local/goal-20261021-resume/d13-withdrawal-http01/`；两次运行原件分别在 `.local/e2a-cross-20260925-1931/`、`.local/e2a-cross-20260925-1932/`。本机私有材料未宣称异地备份。

| 原件/清单（相对证据根） | SHA-256 |
| --- | --- |
| `input-and-exit.json` | `7634d26e4fafee0ef334e9ce6eb4f9f660f02006331ce44a13f8c7003eba0a27` |
| `source-closeout.json` | `84f238d626d10ff30ff727be6dcfa4c230a99b409cf484465c5dee11d70e04ad` |
| `originals-consumer.json` | `6595fe164769a2979c1d401d89d48bda3aadb4ec8dc78f6b1ef2a5b3525f5772` |
| `run1931-failure.json` | `5d28ba544ffc442620924f177d098d0b710a4033086d1a3985055e6d1b72d2de` |
| `docs-before-closeout.zip` | `725a481db3aa11e1da597576d163ec1fc859bb8c8daf7d12551bd2589251be3b` |
| `environment-closeout.json` | `742c3a8af41558b1e849a8ab0581594ad8d3225902d26ada312c204cad3eb37a` |
| `cleanup-plan.json` | `09d87336b15b351585ffca5c2fcd5a2bfed66dfa1e9103790240af6fa9c9dcf9` |
| run1932原 `TEST-com.acrylic.outbound.e2averify.E2aAdmittedCorrectionWithdrawalIT.xml` | `7f91ce4e5ef0af06e4994aad938ffa6e6eed2556101f2fe769ac2bdcaafba6f1` |

两阶段原响应 SHA：WITHDRAW `bc93da42090382b4be79ef547702f03385a2b362d50df9c6c3568ce608c8c07a`；WITHDRAW_CONFIRM `6df4353e10f8997a78cae2413f9780d3af021c6286513b5e2eb0deff81913edf`。audit-originals.py、成功消费结果和运行输入封存；文档随后改变，不重跑其“所有输入未变”断言。

resume-closeout01另保全接续时39条脏路径、原字节zip、tracked patch、全部SHA及新环境；正式收口前旧看板/索引已有两份明确原字节档案。最终本地 final-evidence-manifest.json、prestage-manifest.json、postcommit-before-board.json、final-receipt.json 记录完整原件及六路径开发备份实际回执；HEAD事实按看板生成块核实。

## 环境、清理与边界

run1931/1932 均已认证 MySQL shutdown exit0、进程退出；DB19360/19361、HTTP19481/19482复核无监听。系统3306身份未知，未连接；共享13316收口采样无监听，未连接或停止、不推断停机者。共享 Gradle/Kotlin 保留；ADB只枚举，M71未操作；debug/release Keystore只读哈希不变，无 APK 构建或签名。

已按持续清理授权只清两个认证停机隔离 run 的 data、binlog.000001、binlog.index：6目标/694文件，逻辑445091900 bytes；D盘前31094599680、后31547994112 bytes，实际卷释放 **453394432 bytes（约0.42 GiB）**。逐文件SHA、命令/依赖输入、原数据库身份、认证dump及auto.cnf保留；无在途消费者或残留目标。清理已执行一次，不重复执行。所有 HTTP/JWS/XML/日志、P5未知原件、原身份/密钥、最终APK和继承源码保持。

本批仅六个明确路径作“开发备份”：三个新测试/运行器、本报告和两个短索引；看板及35项继承不提交。提交和清理不增加任何验收等级，不改变总目标active、未完成。

下一步按D13实际入口映射选择尚缺admitted信任输入的独立命令组合，先核源码/报告覆盖，不重跑本批两阶段撤销、已验审批/CREATE附件/四状态筛选或旧run。完整D13及受影响Android/Chrome/Python/非空迁移L2、实际最终候选和第8节仍缺证据。

换机进度承接、多跳、接收者确认/激活链不恢复，旧run03不启动。七类生产决定/TEMP生产归档保持fail-closed，`localDeletionAllowed=false`；原库/现网、正式签名、main、部署和Release继续关闭。
