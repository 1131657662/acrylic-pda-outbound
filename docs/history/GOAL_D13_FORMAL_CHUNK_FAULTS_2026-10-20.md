# D13 FORMAL 分块入口：并发、审计尾回滚与锁后撤权

## 有界结论

输入为 D:/acrylic-pda-outbound、codex/development、4a7fb48df3a05082e195a98706772b545385a685 加清单中的 WIP；远端同 SHA 已实际 ls-remote 核同。四状态筛选沿用户批准及既有实现/消费者，不重复实现或凑字号。本轮只增加四份服务端测试源码，不改产品、权限、契约、迁移或生产配置；版本事实见看板生成块。

新 run09 的 E2aSubmissionChunkFaultIT 实际执行 1 项，0 失败、0 错误、0 跳过，Gradle exit0；一个一次性基础设施生命周期内按独立合成订单分别运行三个受验场景，不把三个场景计作三项测试。11 次实际 TCP HTTP、原签名回执、完整类型化数据库和文件快照经独立 Python 文件消费者核同。P5 后继独立进程实际 STARTED。只收口 FORMAL 同人同机分块 finalize 的该有界 L1，不计整体 D13、L2、D18 或第8节完成。

准备阶段使用既有 MockMvc 输入工厂及非空旧基线迁移；受验请求才是实际 TCP。身份、库存、设备、订单、签名及 P5 权威均为隔离合成输入，不能替代可信原材料、原身份或真实设备。作者、执行和自审为同一助手；独立工具不等于独立人员。

## 三类实际证据

| 场景 | 实际结果与核对 |
| --- | --- |
| 并发 finalize | 原生锁住 user_management_guard；performance_schema 观察两个不同等待线程后释放。两个200返回相同 data（含原 signedReceipt；请求信封可不同），仅1 payload、2 dispatch、2 ledger，合计601500整数克。 |
| 审计尾 SQL 故障 | 对 operation_event 的 SUBMISSION_COMPLETED 插入施加隔离 trigger，SIGNAL SQLSTATE 42000；实际500 INTERNAL_ERROR。完整180表类型化行快照及9文件摘要逐字不变。删除本轮 trigger 后同ID/原正文重试200；1 payload、2 dispatch、2 ledger且RS256验签通过。 |
| 锁等待后撤权 | 锁住 app_user，观察1个实际等待线程后在持锁事务增加 auth_version 并提交；返回401 AUTH_EXPIRED。将本轮合成身份版本恢复至基线后，180表及10文件快照逐字不变，没有受验业务写入。身份版本修改/恢复是测试外部动作，不称整个过程零SQL。 |

完整快照覆盖行值、null、JDBC类型、时间、二进制及精确数值；复用既有 HandoverRollbackFacts.encodeRow，避免 Jackson 将 LocalDateTime 当普通对象而失败。独立消费者从原回执和快照关联 submission/payload/dispatch/ledger，核对正文 SHA、持久文件及账务负601500克；从本轮原公钥实际验签，两次成功回执及并发重放一致。撤权后的快照457行包括合成准备事实，不冒充真实业务规模。

故障位于 SubmissionAccounting.commit → SubmissionEvents.transition → AuditWriter.append 的审计尾写；此前 dispatch、ledger、结果、槽位、状态和出站消息写入一并回滚。测试不将该单点覆盖泛称全部SQL故障。P5租约index最终为空，无 unknown 标记，独立后继 stdout 为 STARTED；测试fixture通用confirmed文案提及scheduler，但本轮没有另启动worker，不据其文案扩大覆盖。

本轮三个冻结正文各5735字节、各1块；文件守恒只对这些实际输入成立。多块合并首次创建完整payload文件后再遇末SQL失败的文件引用/孤儿生命周期未由本轮覆盖，不以先前大正文成功用例替代。

## 失败原件与修复依据

| run | 实际失败/限制与后续依据 |
| --- | --- |
| 01 | MySQL启动参数中的含空格路径拆分，连接握手失败；未通过，不重用。 |
| 02 | 测试目录重复创建 FileAlreadyExistsException；未通过。 |
| 03 | 原同步设计尚未发出受验请求即等待，未观测锁等待；失败XML保留，不再启动。 |
| 04 | 身份串行锁先于scope，只能观察一个waiter；改为阻塞共同身份管理锁以观测两个请求。 |
| 05 | 并发已取得两原回执，类型快照遇LocalDateTime编码失败；改用既有类型编码器。 |
| 06/07 | 原SIGNAL 45000被产品按未知结果处理，写RECOVERY_LEASE_FENCED/unknown原件并Runtime.halt(70)，随后Gradle通道reset。根因已定位，不能再写成随机Gradle故障。产品只将23/42类SQLSTATE视为确定语句失败；测试注入改42000，未修改、关闭或绕过生产围栏。 |
| 08 | 500回滚及并发断言通过，实际401 AUTH_EXPIRED已经保存；wire把data:null当存在data而返回NullNode，断言误读空code。只改hasNonNull，保存错误信封并增加可独立回读的前后快照。 |
| 09 | 依上述新依据，新路径/库/账号/端口一次复验通过；非无依据重跑。 |

run06中的integration-result.xml误复制旧run05输出，原件保留且integration-result-provenance.md明确不能算run06结果。run07起比较全局XML前后SHA，未变化不复制；run09有新XML和一次性完成marker。旧失败不被新通过改写。10项 HandoverRollbackFactsTest 普通定向测试已实际执行0失败/错误/跳过，其原XML另封存；不是全量普通回归，也不和IT相加冒充同次。上一轮完整普通回归沿[原报告](GOAL_D13_FORMAL_SUBMISSION_CHUNKS_2026-10-20.md)输入，不声称本轮重新执行。

## 输入、命令及保全

证据根：.local/goal-20261020-resume/d13-submission-faults01。resume08-preflight保存36项WIP原字节/逐SHA、tracked.patch及2443项服务端/Gradle输入；run09/server-inputs.zip及execution-plan.json重新绑定修复后全量输入。31项非本批继承路径逐SHA守恒；其他聊天新增的503诊断报告只读保护，不混入本批提交。原看板/进度/历史索引及不存在的新报告路径均先登记在documents-before，原索引字节在originals.zip。

实际命令为 powershell -NoProfile -File .local/goal-20261020-resume/d13-submission-faults01/run09/execute.ps1；内部 gradlew.bat -p server recoveryControlClasspath integrationTest --tests '*E2aSubmissionChunkFaultIT' --no-daemon --offline --max-workers=1 --console=plain --stacktrace。一次重型Gradle及一个本轮隔离库写者；TEMP/TMP/Java临时目录仅子进程指向D盘。run09为MySQL19339/HTTP19443，运行前后核绝对datadir/schema/user/port/binlog身份；认证shutdown exit0、收口端口释放。共享13316及身份不操作，系统3306未知不连接。无Android构建、ADB/M71/test业务、原库或现网操作。

| 输入/证据 | SHA-256 |
| --- | --- |
| E2aSubmissionChunkFaultIT.java | ea350cb3c33c8b547c04b8f76ecd913bad6c0e03c8ed150d41530cd0f840f9c4 |
| SubmissionChunkFaultState.java | 02824e458a5bb189175634a12c597790cbe4532bff34046c31bed26c36e643cd |
| SubmissionChunkFaultWire.java | 27f331a4683399160761a4361c0931801f3bc006cac68546fbaf79aa4c6c0c9c |
| SubmissionChunkNativeLocks.java | afcf9cf13d2f09cb73ba495727293d23e08c578704b81081119d17bfa9a5824a |
| run09/execution-plan.json | a8a22e3bc168f1a63c8817b5342bc32c6e6fd257aab8069542a2fcadbd492ccc |
| run09/integration-result.xml | 44bf3dc9dd339ca97374ffbd0a53c4b90970e127a26a2bd6c1ed6958d1b59b95 |
| consumer09-result.json | 819652b5685142ca4efdafb2dd2ab4485ce60c9fc79cf38860d1d2791726c43d |
| cleanup04-09/plan.json | a8bc0065437bea1096564929fa086063eaca1046727deaefbb4537042fc56001 |

## 清理与下一动作

run04–09用途结束且认证停机exit0，当前无进程引用、分配端口无监听。先保存逐文件SHA/输入/锁/命令/结果、全部失败与签名HTTP、类型化DB事实及MySQL生成身份原字节，再以单一PowerShell明确路径清理六个可重建合成datadir：2082文件、1334473737逻辑字节（约1.24GiB）。D盘可用31747817472→33108340736，观测差1360523264字节；卷变化与逻辑删除分别报告，不重复计旧9.44GiB。清理脚本首轮数组表达式错误在删除前停止，修正和原脚本均保全。run01–03未有本批所需显式shutdown退出回执，原日志虽能辅助证明停机，仍保留不追加启动；不清任何原身份、签名、唯一证据、APK、继承源码或生产边界文件。

精确最终提交/远端、WIP逐SHA及环境收口见本根final-manifest.json、environment-closeout.json。只按四测试源码、本报告和三个断点/索引明确路径作开发备份；不将治理、清理或提交计验收。下一有界动作先只读比较D13其余有效文件/导出入口的既有覆盖与当前调用，选实际最小缺口；不重复本轮成功IT、CLI/SAF/字号，也不恢复已取消链。

总目标active、未完成。其他D/O/U必做范围、可信原材料/正常合法资格、原设备/外置介质、完整UI、必要非空迁移组合和实际最终候选/第8节仍未满足。localDeletionAllowed=false、七类生产决定/TEMP生产归档、原库/现网、正式签名、main、部署、Release持续关闭。
