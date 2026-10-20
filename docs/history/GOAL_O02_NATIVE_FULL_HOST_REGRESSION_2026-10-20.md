# O02 rescue 完整主机回归（2026-10-20）

## 输入与结论

在固定主树 D:/acrylic-pda-outbound、codex/development，输入fda6887d75e002a2b3a4f6678632ec088f0a559a加保留WIP上，同一次实际执行完整13个rescue测试类63项与ContractFixtureTest两项：**14 suite / 65 tests / 0 failures / 0 errors / 0 skipped，Gradle exit0**。这是新完整主机结果，没有把历史43项与后续19项相加冒充同次回归。

运行器沿 [短路径诊断](GOAL_O02_NATIVE_SQLITE_SHORT_PATH_RECOVERY_2026-10-20.md) 的有效配置：短子进程TEMP、既有Android资源的绝对路径、专有工作/报告/生成目录，保持原NATIVE Room、原源码、原断言。该前置修复后才有依据扩大到完整相关回归。旧CANTOPEN、路径诊断和资源加载失败原件均保留。

## 实际执行

证据根 .local/goal-20261020-resume/o02-native-full-host01/；输入清单input-manifest.json。

    gradlew.bat -p android :app:testDebugUnitTest
      --tests com.acrylic.outbound.pda.rescue.*
      --tests com.acrylic.outbound.pda.preparation.ContractFixtureTest
      --offline --no-daemon --console=plain --max-workers=1 --no-build-cache
      -I <本轮根>/full-host.init.gradle
      -Pkotlin.compiler.execution.strategy=in-process

JAVA_HOME/ANDROID_HOME复用已装工具；TEMP/TMP/java.io.tmpdir仅对子进程指向 .local/t20o02b，未改全局环境。生成的test_config.properties仅在专有resources01以同一既有manifest/资源APK的绝对路径供给；原资源字节及产品构建文件不改。命令/实际环境/退出码为command01.json、gradle01.exit。

目标Test输出O02_FULL_ACTUAL_EXECUTION01并实际执行，未UP-TO-DATE/FROM-CACHE/SKIPPED。32个actionable任务里目标1个实际执行、31个编译前置up-to-date，构建耗时日志1m17s；时间只说明该样本主机测试，不作真机性能结论。无首轮资源加载警告。未运行APK打包、设备或业务服务。

## 逐类结果

| 类 | tests/failures/errors/skipped |
| --- | --- |
| ContractFixtureTest | 2/0/0/0 |
| RescueContinuationSnapshotTest | 3/0/0/0 |
| RescueCredentialTest | 4/0/0/0 |
| RescueDelegationTest | 4/0/0/0 |
| RescueFileTransferTest | 3/0/0/0 |
| RescueFormatTest | 8/0/0/0 |
| RescueGeneratedFileTest | 6/0/0/0 |
| RescueRequestDocumentTest | 3/0/0/0 |
| RescueRequestStoreTest | 7/0/0/0 |
| RescueRevocationTest | 5/0/0/0 |
| RescueRoomSnapshotTest | 7/0/0/0 |
| RescueScaleTest | 1/0/0/0 |
| RescueSupervisorStoreTest | 9/0/0/0 |
| RescueTransitionSnapshotTest | 3/0/0/0 |

覆盖原记录/冻结正文/回执一致快照、SCAN/VOID/冻结/回执竞争、跨范围与撤权、凭据持久/到期/重启、救援文件生成/恢复/签名/加密/损坏/传输失败、合成规模原序及重量。R06前驱原件读取为同人同机范围，不含取消的换机进度承接/多跳/接收者确认激活链。

## 守恒与证据

1022份Android/包装器源输入、10份既有fixture、31条非看板继承路径、两份Android资源输入全部SHA无漂移；12份旧默认XML/HTML与8份前轮诊断XML保持。本次原始XML/HTML/binary全部独立存放，不覆盖旧证据。作者、执行与自审均本助手，非独立人员审查。

| 文件 | SHA-256 |
| --- | --- |
| input-manifest.json | a12c302433f9b7da85086ac4f15a55f1933935812df10ce50a62dde5b3ace7ea |
| gradle01.log | 903142c50b2469ce3c86d7e07401280b8654cedaa9d4372bbdd833c0117697ca |
| test-result01.json | f82e3f26578b4511f3641e77d6419475151887c80104e495f1c9e6fdf5233f07 |
| test-reports01.zip | 078b9a1f09c32e52f3ca56ec711e8900c00b74ef144ea69543abe08b600f52df |

逐方法、逐类XML哈希、生成11份合成文件/元数据及守恒结果在test-result01.json。新formal/TEMP/large等文件留供下一电脑只读消费者；口令/签名材料仍按测试合成边界，不将包内公钥自证为包外可信登记。既有最终APK、原身份、tar与原件不变。全状态、Git和输出清单见closeout.md/final-manifest.json。

## 限制与接续

本轮完整的是rescue主机测试集合，不是完整Android/Room迁移、O02/O05或第8节最终候选。合成Room、签名和回执不等同原设备/原业务可信来源；未验证SAF/MTP/OTG、外置介质、真实HTTP/MySQL或当前成功终态，没有授放行/删除。独立预检聊天的非合成真实数据闸门仍按其原报告，不因本轮合成测试通过而改变。

下一有界任务可实际让电脑cli消费本轮新FORMAL/TEMP/scale文件，分开验证完整性与包外登记信任；缺可信根只能报告来源未核验，不从包内抄钥自证。继而按D/O/U矩阵补受影响组合。无需机械重跑已验字号/SAF或取消链。

清理：本轮没有新建业务库；短TEMP约46MB保留，本轮释放0字节，不重试前次自动审批拒绝，也不累计历史9.44GiB。总目标active、未完成；原库/现网、生产开关、正式签名、main、部署与Release继续关闭，localDeletionAllowed=false。
