# D04同人同机来源上传：原Room与真实HTTP有界组合

本片在新隔离输入上执行原E2aAndroidHttpIT.run(false)断言体及原Android E2aRealHttpRecoveryTest。产品、Android业务断言、契约和迁移未改；只修改测试运行器的树、报告和缓存隔离。最终组合通过，但不是D04整体、D14/D18或总目标第8节通过，不追认历史失败。

## 输入与新依据

- 工作树D:/acrylic-pda-outbound，分支codex/development，输入提交5b5fccf5209bcd620a3701c6c1467cec3bfc1aea。前片[旧结构STOP前缀兼容](GOAL_D04_LEGACY_STOP_PREFIX_COMPATIBILITY_2026-10-21.md)已定位run732缺表SQL及已有兼容守卫；本次基于该新依据，不重启旧库或旧run03，不执行接收者确认/激活、换机承接或多跳。
- 证据根：.local/goal-20261021-resume/d04-android-http01/。candidate-source.zip为提交归档，baseline.json绑定输入SHA及前片postcommit-wip；运行器原字节另存E2aAndroidProcess.before.java。
- 主树唯一代码改动为server/src/test/java/com/acrylic/outbound/e2averify/E2aAndroidProcess.java：默认根沿E2aWorktree，支持E2A_ANDROID_TREE/INIT/PROJECT_CACHE/RESULT_XML；拒覆盖本次Android日志/XML，离线单worker，去掉Android子进程中的两种数据库密码环境。未放宽业务断言或生产开关。
- 从前片已编译test runtime复用服务端依赖，仅javac编译运行器与薄入口OriginalAndroidHttpMain。2622源文件中产品原字节全等；两份未在本片执行的Legacy测试仅Git CRLF/LF不同，archive-line-endings.json逐项保留，不能声称所有源逐字相同。
- source-manifest.json绑定3656份解包源码/辅助fixture输入；server-runtime-manifest.json绑定3563份运行时class/resource/JAR。conservation.json在运行结束后逐文件核同；33份非看板继承WIP及上片两APK哈希保持。未叠加PublicationDriver、PdaApplication或取消链WIP。

## 实际执行与三轮结果

prepare.py准备隔离源码/build/report/fixture，run_combination.py及各attempt/run_combination.py记录完整命令与子进程环境，combination-plan.json、javac-command.json和server-process.json绑定实际参数。薄入口直接调用原IT方法，服务端没有经JUnit运行，不能写成“服务端IT 1/0/0/0”。Android Gradle是唯一重型构建。

| 运行 | 隔离库/HTTP | 实际结果与归因 |
| --- | --- | --- |
| attempt01 / run2191 | 19391 / 19491 | exit1；启binlog后触发器迁移1419，尚未进入Android业务；原dump/log与失败输入保留 |
| attempt02 / run2192 | 19392 / 19492 | exit1；只在新实例加入log-bin-trust-function-creators=1，迁移完成；Android 1测试/1失败/0错误/0跳过，相对资源路径在隔离workingDir解析失败，业务体未执行 |
| attempt-resources03 / run2193 | 19393 / 19493 | exit0；独立classpath资源使用绝对路径，业务断言未改；原Android测试1/0/0/0，原server断言体执行完成，test-completed.marker存在 |

attempt02的ContinuationMigrationTest报告为0方法suite，没有执行取消链。第三轮使用新库、身份、HTTP和报告目录，复用android02编译输出；Gradle实际执行32 tasks，不是另构建交付APK。资源修正及原test_config字节见attempt-resources03/resource-repair.json和test-config-before.properties。前两轮失败不能与第三轮合称同次全绿。

第三轮实际attempt为source/.local/e2a-cross-20260925-2193/。新非空合成旧结构先按原IT迁移、植入历史事实，再升级至原消费者指定结构；不是原资料或原库。原Android主机消费者使用Robolectric NATIVE Room，临时作业扫码/冻结、真实HTTP上传后保全，OPEN/RAW/PAGE/STOP/VERIFY五处提交后丢响应；每次重开Room、签名源不可用后继续，断言原请求序列/原回执保持且只签一次STOP。

## 离线独立工具消费

audit_combination.py只解析本片final.sql、存储原件与android-result.json，不连接数据库。receipt_verifier.py复用已提交验签器，包外公钥来自本片IT生成材料；这是同一助手执行的独立工具核验，不称独立人员审查或真实身份认证。

- 四份OPEN/PAGE/STOP/VERIFY回执与数据库original_response_bytes逐字相同，原回执哈希核同，四个RS256签名、issuer/audience和完整绑定验证通过；capabilities全部false，含localDeletionAllowed。
- 四份原JSON请求与canonical_request_bytes逐字相同，按ACRYLIC-R06/E2A/INTAKE/1域分隔计算请求哈希；STOP manifest亦按其MANIFEST域计算，不误用裸SHA。
- 目标13个服务端证据原文件字节数/SHA核同；其中11个RAW与客户端实际上传原文逐字一致。原冻结payload哈希与客户端和数据库一致。
- 包外合成设备P256公钥验STOP签名，八条连续审计事件的原ID、顺序、正文hash及STOP绑定核同；仅IT身份，不是原Keystore签名。
- 目标submission为REVIEW_REQUIRED，来源核验为VERIFIED_COMPLETE_AT_STOP，客户端队列PARTIAL；dispatch_record和inventory_ledger均0，未取得出库成功或生产放行。
- 本链先上传再保全，原提交的同包/周期/仓库/scope占用保持REVIEW_RESERVED，绑定原submission/order，source_evidence_set_id为空；不是abort-before-payload链的SOURCE_RESERVED。另一仓/另一scope的seed占用已排除。

初版离线审计先误用裸请求/manifest哈希，后误要求SOURCE_RESERVED；这些是审计工具假设错误，不是产品失败或可修改数据库的理由。audit_combination.first.py、resume-closeout01/audit_combination.py.before和audit-correction-reason.md保全改前脚本/原因；未改产品、未重跑业务、未改DB以迎合审计。最终命令为C:/Python314/python.exe -X utf8 <证据根>/audit_combination.py，exit0；stdout/stderr/参数及脚本与输出hash见resume-closeout01/audit-command.json。

| 原件 | SHA-256 |
| --- | --- |
| 成功Android XML | c416cd43f1b0d148031a202116d5af4aa1ab6350bfc2835cc7627c60c21d87c9 |
| 成功final.sql | 826ecb242708483409633e8721f02b8cdb3a1fd6e56f37702f84391024477442 |
| independent-audit.json | 2c42bae9ae6f3717454b2f3cec16e65cdcfe4cf4891dcb1061b40677e795b54b |
| 本片运行器源码原字节 | 75d462bec596389d2fc53f91b47481cf3f8b25ae89e7b9ab0a2cc7fa9928dcbc |

## 收口、保全与未验

三个新库各自dump、认证停机，fresh核19391–19393/19491–19493无监听；共享3306/33060身份不可读，仅观察不连接不停止。ADB仅枚举M71，未启AVD或操作设备。正式签名未使用；本片私钥/Bearer只保存在.local/private，不入Git。进程/监听/磁盘见resume-closeout01；本片未清理或删除产物，释放0；前片清理被自动审批blocked by policy的目标不换工具重试。

conservation.json核3656输入、3563运行时、33份继承和两APK；本组合没有采集全库前态，不声称本片103表前后守恒，前片L1的103表证明仅属于前片输入。本片为合成同人同机、旧结构、正常上传后保全的有界组合；无本次模拟器/真机、原身份、真实现场、正式APK构建或当前全结构/部分升级矩阵，未覆盖abort-before-payload、其他来源、完整TEMP后续处置及D04全链。

只暂存运行器、本报告和两个索引四路径，按授权作开发备份并推送codex/development；看板留本地WIP，33份继承保持。实际Git回执、最终WIP字节快照与输出总清单在证据根；仅文档/断点变化不机械重跑该一次性用例。

总目标active且未完成。下一步沿D01矩阵核对仍缺的R06同人同机成功回执/中止前上传及当前结构恢复组合，先查已有对应证据与失败新依据，避免重复旧用例；D16合法退出资格、统一最终候选恢复/非空迁移、D18和第8节仍欠。原库/现网、正式签名、main、部署/Release及七类生产决定保持关闭，localDeletionAllowed=false。
