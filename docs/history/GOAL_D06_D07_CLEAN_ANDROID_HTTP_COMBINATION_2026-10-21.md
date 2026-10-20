# D06/D07 干净提交 Android / HTTP 有界组合

本片验证实际开发提交的撤回、重发、新设备从头作业和签名回执持久消费。只收口本页列明的合成隔离组合，不计D06/D07/D13–D18整体、统一最终候选或总目标第8节通过。没有产品或测试源码修改，没有重跑四字号、旧SAF、D04暂缓链或取消的接收者确认/激活链。

## 输入与保全

- 固定树D:/acrylic-pda-outbound、codex/development；开工HEAD及fresh开发远端为606b38bdccba70a3c85df4adb32e82d52a111645。本聊天01a1520d-c412-7402-b77d-311a043b3d1e唯一主树写者；另一同项目聊天fresh为notLoaded，无子代理。
- 证据根.local/goal-20261021-resume/clean-http-dependency01。baseline-wip.json与wip-before.zip保存全部34展开WIP；33份非看板继承SHA与上片清单核同，不把未提交PublicationDriver、PdaApplication或取消链叠加进输入。
- candidate-server-source.zip由该提交git archive提取2620份服务端、渲染、契约及构建输入，SHA为75a04fea1594dca6116f1294167ae6468c4ff700c4db4955859b4c8a04fcdcf3；逐文件绑定在candidate-server-manifest.json。源目录及构建输出都在本片.local，未覆盖主树JAR/报告。
- Android复用clean-android-head01/apk两份保全Debug APK；fd97fd76f36f00286234b20ecb613c4e94ce6b3a与本候选的Android/Gradle提交差异为空。产品APK SHA为266aa47be893729c558b430ace39c4f3a704db513f1fd9bb1ed202212d988e91，测试APK为5a9b3c670748b5031aee6344c6f849a39b1e3bb3de0d122c4705daee42386763。未重构建、覆盖或清理APK。
- 旧run1614之后服务端恢复SQL/事务及审计路径已有变化，旧不同输入结果不能代替本片组合；本次不将上片32项文件组合算作本片新测试。

## 实际执行

执行入口和精确参数为prepare_source.py、run_fixture.py、run_native.py、database.py、native_environment.py及对应plan/command JSON；TEMP/TMP/Java临时目录仅子进程指向本片D盘目录。build01在提交源上实际testClasses/bootJar，exit0；新JAR SHA为75c4d72d9eba70bdba19b6f0fb72ecab3af548266297e28de5e48a04cf0c38b3。未执行渲染业务，字体准备明确排除，不称完整服务端回归。

fixture01仅初始化新空隔离库后遇运行器错误：reports.gradle对复合构建report-rendering查找不存在的integrationTest，业务测试未执行；原日志、退出1和认证停机保留。修正运行器仅按rootProject.name限定服务端任务后使用另一全新隔离库，不复用前库。

- fixture02：新库acrylic_clean_http_20261021_2188、MySQL 19388；原FaultRestartConsumerFixtureIT实际1/0/0/0、exit0，创建四张合成订单与三类消费者输入。只是从空库迁移，不称非空升级或真实用户身份。输入保存在.local/goal-20261011/fault-restart-consumer-2188/private/input.json，路径为原测试硬围栏所需，无旧用例重跑。
- 同库新服务仅绑定127.0.0.1:19488；故障撤回开关只在该合成实例命令行开启，生产默认与主树配置未改。应用签名沿既有本地IT钥，非正式签名。
- 新AVD CleanHttp2188/5690由既有config和已装system image冷启动，不复制userdata或Keystore、不擦除或写入旧模拟器。实际qemu命令和hardware-qemu.ini均绑定本片新目录；config的AvdId/displayname仍为来源标签，不能把它当原模拟器身份。真机M71仅枚举，无操作。
- 原FaultRestartDeviceTest四方法各为OK (1 test)：新设备独立登记→撤回重发→从头领取/下载→真实WorkActivity扫描→冻结→HTTP上传成功验签；强停新进程回读；扫码中消费签名撤回并保留本地原单；再次强停后撤回保持。第二、第四方法前均实际force-stop。
- native-results.json四项passed=true，native-exit.json为passed=true；没有改断言或APK。Web管理HTTP由instrumentation客户端驱动，没有本片Chrome或Web页面操作证据。

## 独立离线消费者

audit_combination.py只读解析认证停机前的新dump与本次Android tar；consumer-source从同提交逐字提取既有fault_recovery_evidence/accounting，不连接数据库或网络。离线SQL解析器不执行SQL，Room只在派生副本以mode=ro/query_only读取。

- 83张Room表；唯一local_submission为SYNCED。原冻结正文6242字节与服务端submission_payload所指私有原文件逐字一致，SHA为1ea6fcd2da5909b7bc5cea769eb7204cd03b0f91ba9af6005fcf14ac76bfeb23；本地成功回执与服务端原文对象相等。
- 一次成功、两包、601500整数克；dispatch两笔、ledger两笔各-1且重量逐笔对应；唯一结果版本及DISPATCHED占用绑定同submission。新设备不同于旧assignment，序号从1开始，旧采集ID不迁移。
- 三份撤回均无STOP原件，状态WITHDRAWN；包外固定IT公钥实际验证一份成功及三份撤回的RS256签名/受众/正文。原机local_recall.proof与服务端原撤回回执相等，RECALLED原作业保留。合成IT信任不等同原身份/现场信任。
- temporary_handover_confirmation、temporary_handover_activation、recovery_handover_confirmation为空，未执行取消链；产品结果localDeletionAllowed=false。成功回执本身无该字段，不补造字段。
- independent-audit.json SHA为af28f93f10cdb74ff15ce63bb8db1ab25d51e67b17b3594ef1e453606586fd22；原tar SHA为28b64534b18f249e0e85c42ec44f461d2461f166d540de01382d373c4b064a4c；最终dump SHA为2851bbf7b9d8f5eda626fe4496bb4f01231c5de07aa8cd54563600486b23012c。逐成员SHA在offline-probe.json。

独立工具消费者仍由同一助手执行，不称独立人员审查。旧MockMvc合成订单标签出现替换字符，本片不声称中文标签/页面文案通过；原样保留，不凭功能成功掩盖夹具编码限制。

## 停机、清理与限制

新HTTP、AVD已停止；两次新MySQL均认证停机，19387/19388/19488/5690/5691无监听及本片重型消费者。共享3306/33060身份不可读，只观察、不连接或停止。收口进程/监听/空间保存在本片对应文件。首轮与次轮同名根元数据曾被运行器覆盖，metadata-before-native保留后续阶段前字节；首轮仅能使用其保全停机/日志/auto.cnf，不虚称两轮所有元数据都独立完备。后续输出已改为分实例目录。

依用户持续清理授权，拟清两个已认证停机的合成datadir，但自动审批在进程创建前返回blocked by policy；删除未执行，释放0字节，未换工具/包装重试。源、依赖锁、运行参数、原dump/XML/日志/tar、签名材料、新AVD及全部继承仍在。既往9.44GiB不重复计。其他工具载荷/PowerShell语法失败在业务执行外，记tooling-notes.md。

本片不含新的Chrome、SAF/外置介质、断网文件往返、备份恢复、admitted/P5故障矩阵、并发/末SQL/权限全矩阵、非空迁移或完整回归；旧不同输入证据单独引用。D04可信前缀、D16正常合法退出、统一D18候选及第8节仍未完成。原资料/旧BCD、原身份/真机/现场和长时继续单列；生产开关、原库/现网、正式签名、main、部署与Release继续关闭。
