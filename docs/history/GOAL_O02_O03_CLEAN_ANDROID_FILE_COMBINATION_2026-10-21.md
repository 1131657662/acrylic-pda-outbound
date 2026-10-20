# O02/O03 干净开发提交的 Android 文件消费者组合

## 结论与范围

输入为开发提交 fd97fd76f36f00286234b20ecb613c4e94ce6b3a。本片没有修改产品或测试源码：独立 Android/Gradle 快照实际运行六类32项主机测试，32通过、0失败、0错误、0跳过，Gradle实际退出0；随后由同提交的电脑工具独立消费本次新生成的四份应急文件，三个CLI场景符合预期。只补干净提交的有界 Android/文件组合，不是完整O02/O03、D12/D17/D18、统一最终候选或第8节通过。

应急文件使用本次合成Room输入；返还消费者使用另一个历史合成成功批次的封存原件。两组输入分别固定，不能称同一业务事务或本片真实HTTP/MySQL全链。作者、执行、自审均本助手；Python是独立实现消费者，不是独立人员审查。

证据根：.local/goal-20261021-resume/clean-android-combination01/。版本事实只读看板生成块。全部本地输出在D盘。

## 输入关系与保护

- candidate-source.zip由git archive HEAD android gradle gradlew gradlew.bat获取，SHA-256为08fcdbe65a80472f2690e44dfc99188fb2a0fa81a8ac5033bf3c27b75984ff97。1027份提交文件与上片独立构建快照在运行前后全部核同，仅允许Git换行规范化；没有继承WIP叠加。
- candidate-source-verification.json保存每个提交文件/快照SHA；historical-relevant-source-comparison.json比较历史O02输入。相关应急/返还产品及测试保持；PdaApplication和正式ContractFixture与历史工作树输入不同，因此旧工作树回归不直接冒充本提交的实际组合。
- 选择Room快照、保存/VOID/冻结/回执替换的并发一致性、加密私有文件生成/恢复、介质失败及封存返还签名消费者。不重复原生字号、旧SAF或取消链；其他主机套件没有因此获得当次通过。
- computer-source.zip是同提交42份tools/pda_rescue文件，独立提取后导入，逐文件与主树规范化字节核同，不依赖未提交源码。
- baseline.json/wip-before.zip保全开工34展开脏文件。conservation-final.json核对33份非看板继承文件、上片81份原证据、全部1027提交源未变。仅看板/两索引和新报告改变；取消链13项、physical-disposition、规则、部署报告等继承保持。
- external-inputs-before.json与external-inputs-after.json逐字相同，SHA为4764db72c1f0c1c5f87bd1bcc035b9d0c2ec5473f7fb6c57f36f2c4e69c99c04。12份显式辅助输入按上片登记核同，缺失optional fixture不补造。

封存返还来源沿[原消费者报告](GOAL_O03_CURRENT_ANDROID_SEALED_RETURN_2026-10-20.md)：原ZIP摘要2b1adca2ac197c6447a942d1ca4052edd2f70d650bda367d5c4498dd08033f4f；原下载/电脑返还文件摘要ac3d73ea35f35b0e96ab75dccd85b5c00f85e1ba05176a13e7ae2f9b2c652363；包外历史公钥摘要52b9156aa36db7db068451d3434e93559dd1993e150b0cd2dcec544f0e8fe9f9。定位来自原运行记录，不从包内复制公钥建立信任。原件和定位记录复读核同，没有读取私钥、重签或重发HTTP。

## 实际主机验证

完整参数/子进程环境在host01-execution-plan.json，执行入口run_host.py。复用上片同源隔离build/cache；本片新路径保存XML、测试二进制、工作目录及新文件，不覆盖旧XML/APK。TEMP/TMP和Java临时目录为.local/t21cb01，Android资源配置为原构建产物绝对路径。NATIVE模式、原测试和断言未改。测试任务禁用UP-TO-DATE/缓存复用，日志明确实际执行；编译等31项任务复用同源构建缓存。

命令为独立源码gradlew.bat -p其android目录，两个-I分别指定原build隔离脚本和本片selection.init.gradle，运行:app:testDebugUnitTest --offline --no-daemon --max-workers=1 --console=plain，使用原隔离project-cache和-Pkotlin.compiler.execution.strategy=in-process。六类各用--tests完整指定，前缀com.acrylic.outbound.pda。

| 实际类（省略前缀） | 数量 | 覆盖 |
| --- | ---: | --- |
| rescue.RescueRoomSnapshotTest | 7 | 到期/重开只读、FORMAL/TEMP、冻结/回执原字节、权限、并发保存、秘密字段拒绝 |
| rescue.RescueTransitionSnapshotTest | 3 | VOID/冻结/回执替换不混合跨事务事实 |
| rescue.RescueGeneratedFileTest | 6 | 加密生成/恢复、损坏/跨仓拒绝、原清单绑定、错签名、生成中断 |
| rescue.RescueFileTransferTest | 3 | 流复制/回读、只读/截断/损坏/满盘或断开模拟失败 |
| relay.RelaySealedReturnTest | 9 | 封存双签名、原人机仓/正文/汇总/删除边界/重复属性/终态保护 |
| relay.RelayReceiptVerifierTest | 4 | 合成签名正确/错误根、错范围、变更及终态反退 |

实际32/0/0/0、退出0；host01/gradle.log和六份原XML保留。host01-result.json SHA为b2a7595e2c11b20c3198c834e954d9f6271c90abeeaa0a9cd0affb8374c59b53。无跳过，未复制旧XML。未设置旧恢复确认HTTP变量，未调用旧bridge/接收者/激活。

## 新文件电脑独立消费

consume_files.py用独立提交源码实际核验包签名、成员摘要/字节、原序号、TSV/NDJSON；另用独立整数运算核每包有效1条、300500克及产品汇总一致。四份原输入前后SHA保持。

| 新文件（相对host01/build） | 字节 | SHA-256 |
| --- | ---: | --- |
| rescue-room-vectors/formal.zip | 8076 | 3570eaced2f43240a540450ae739b7d2bc1368f60f4b5faf058cce4b162acb5d |
| rescue-room-vectors/formal-frozen.zip | 9849 | 0319fb26ba72091df9d6ac0c4282b0117f605b2e39d5eac2ab74c60dc9d9c6af |
| rescue-room-vectors/temporary.zip | 7553 | a42f2e6e8d6edb0a2f81cdb7de408b3a940d8e41c4f69cad15d9891f6f7fd42d |
| rescue-generated-vectors/room-generated.pdarescue | 7920 | 2d859360f436b357cdd347875d5eddd5b4177ab2c738ef5489984652af074b0e |

本次测试签名钥没有独立登记来源，四包仅完整性有效，sourceTrusted=false、businessReceiptVerified=false。没有从包内抄钥自证。冻结样例回执是合成未知结果字节，不冒充真实成功回执。

对本次原格式加密文件实际调用Python模块cli：

1. 新目录发布退出0，独立复读全部13成员逐字核同，报告完整性true、来源/业务回执未核验。
2. 同输入增加--require-trusted，退出1，不创建目标目录；缺登记失败关闭。
3. 指向已存在的本片输出目录退出1，原输出全部SHA保持，不覆盖原件。

适配器只把公开合成测试口令从标准输入交给getpass，产品解密/核验/发布代码未替换；不是交互终端或USB/MTP/OTG验证。命令、stdout/stderr、退出码及SHA各自保存；computer-cli-results.json SHA为ce94b202f193288655a995a56b17fc99263cba07a3c3a785e65743e00f695c50。未生成新Excel/PDF/图像，不套旧文件显示验收。

## 工具、环境和限制

无本片业务失败。运行器传输和报告传输各有一次工具字符串解析失败，均在写入/业务执行前，后以正确载荷写入；host01只实际执行一次。只读初探的换行差异由正式规范化核对纠正，不当行为漂移。过宽的本片只读Keystore扫描已按实际PID/完整命令核对后停止，共享ADB/MySQL/Gradle未停止；详见tooling-notes.md。

唯一写者本聊天；另一项目聊天fresh查为notLoaded，不沿用旧idle。只观察系统3306/33060，身份/命令不可读，不连接不停。ADB只枚举M71，不操作原设备；未启模拟器、HTTP或隔离MySQL，未构建/覆盖任何APK或使用正式签名。前后进程/监听/磁盘及Git回执在证据根。

清理评估：仅新增小证据/源归档/短TEMP，没有新AVD/业务库；既有大可重建产物删除曾被自动审批拒绝，不换工具/包装重试。删除/释放0字节，历史9.44GiB不重复计；原资料/身份/Keystore/签名/唯一证据/最终APK/继承源码及生产边界保持。

未覆盖设备页面/SAF、真实HTTP/MySQL、非空迁移、Web/浏览器、完整备份恢复和统一候选L3。两组文件输入未合并为端到端事务。D04可信前缀、D16可信合法退出资格、D18统一候选等缺口仍在。总目标active、未完成；生产开关、原库/现网、正式签名、main、部署/Release关闭，localDeletionAllowed=false。

后续按同提交、历史输入及直接依赖核定剩余Android/HTTP/恢复组合；复用本片文件结果，不机械重跑32项或字号，不为正向材料恢复取消链或补造可信资格。
