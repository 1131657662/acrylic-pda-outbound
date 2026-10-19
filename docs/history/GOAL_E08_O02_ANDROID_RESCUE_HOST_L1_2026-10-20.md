# E08/O02 Android rescue 当前源主机回归 L1（2026-10-20）

本切片验证当前 Android rescue 源的格式、资格、撤销、请求文档、文件传输和只读授权边界，并为电脑端 O02 消费者建立当前源回归证据。只运行 JVM 主机单测；没有安装 APK、启动模拟器、连接设备或访问服务端/数据库。

## 输入与命令

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD `f02c5a03530558770ffbd4f833a7fa26982e5904`。
- 当前 rescue 主源码及相关测试的逐文件 SHA、字节数、Java 版本和临时目录见 `.local/goal-20261020-resume/o02-android-rescue-host-run02/input-manifest.json`，SHA-256 `C667F98357E5381AAA10F384D86054F644E76EC7D9674FC4475FD3C9F1B318F2`。
- 首轮宽过滤器命令：`gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.rescue.* --no-daemon --console=plain`，证据 `.local/goal-20261020-resume/o02-android-rescue-host-run01/`。它实际收集 63 项，19 项失败、0 error、0 skip；Gradle 退出 1。失败原件保留，主要出现在 `RescueGeneratedFileTest`、`RescueContinuationSnapshotTest`、`RescueRoomSnapshotTest`、`RescueScaleTest` 和 `RescueTransitionSnapshotTest`，日志中分别有 `JSONObject` 和 `SQLiteCantOpenDatabaseException`。
- 修正版只选择不依赖上述失败路径的 8 个类，命令见 `.local/goal-20261020-resume/o02-android-rescue-host-run02/gradle.log`；临时目录通过 `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=.../tmp` 绑定到 D 盘。

## 结果

run02 共 43 项：`RescueCredentialTest` 4、`RescueDelegationTest` 4、`RescueFileTransferTest` 3、`RescueFormatTest` 8、`RescueRequestDocumentTest` 3、`RescueRequestStoreTest` 7、`RescueRevocationTest` 5、`RescueSupervisorStoreTest` 9；全部 0 failure / 0 error / 0 skipped，Gradle 退出 0。Gradle 日志 SHA-256 `E91F6C9A1214CDB3566E8880B7A5F57606BA46AFB40AECD8914DB895150CF5CD`，退出回执为 `0`。

逐类 XML 均保存在 `android/app/build/test-results/testDebugUnitTest/`，并在本次输出中重新核对 SHA：

| 类 | 结果 | XML SHA-256 |
| --- | --- | --- |
| RescueCredentialTest | 4/0/0/0 | `D7C1BD920183DCEE13B86B93C51A444243160BBFABA3C620873DBBAC1D6B7772` |
| RescueDelegationTest | 4/0/0/0 | `F3356469E2245E08774F03737946D9C5D7EE6CAD62724538D9107CABB90853D8` |
| RescueFileTransferTest | 3/0/0/0 | `C2AE341F4FE6240C03DBE12B6CF22E25D0154EF5D22DB6C4E273D13BAC7A2FA4` |
| RescueFormatTest | 8/0/0/0 | `B1DCCB9DED858D02A9B16E4B5709F1596FDB9C63055738FFB492E6118551F807` |
| RescueRequestDocumentTest | 3/0/0/0 | `2F1DCBC6CF8305F604825E983E89493D143F84546BCA7A9F07F93BB5E8E533E1` |
| RescueRequestStoreTest | 7/0/0/0 | `282A453882EED57EDC13ADAC5127588600E4F51537A15195231A2006EED13CCA` |
| RescueRevocationTest | 5/0/0/0 | `5B14881FF2CA43D16CC9ED68AA71C293452260278685EE4F349C70734B140A80` |
| RescueSupervisorStoreTest | 9/0/0/0 | `F554D7342ABC07B3BE9A14A7E83BF85A4FD52F4510D58C51407D74D80E93615A` |

## 边界

这次只证明当前源的主机层格式和 fail-closed 规则；宽过滤器中的 19 个失败没有被隐藏或改写。没有运行 `RescueGeneratedFileTest`、Room snapshot/scale/transition/continuation 的失败类作为通过，也没有把主机单测替代 Android Activity、SAF/MTP/OTG、真实 HTTP/MySQL、原身份、外置介质或业务回执。

系统观察到的共享 MySQL 仅保持原监听、未连接或停止；ADB 仅保持观察，不操作设备。没有构建或覆盖 APK，生产默认、`localDeletionAllowed=false`、正式签名、部署、Release、main 合并及取消的换机承接链均保持关闭。

本报告是 E08/O02 Android rescue L1 开发证据，不关闭 O02/O05、D11/D12/D14/D15/D17、E06/E07/E08 L2、D18 L3 或第 8 节总目标闸门。

## 证据

- `.local/goal-20261020-resume/o02-android-rescue-host-run01/gradle.log`：宽过滤器失败原件，63 项中 19 失败。
- `.local/goal-20261020-resume/o02-android-rescue-host-run02/input-manifest.json`：当前源输入 SHA。
- `.local/goal-20261020-resume/o02-android-rescue-host-run02/gradle.log`、`gradle.exit`：43 项定向回归及退出回执。
- `android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.rescue.*.xml`：逐类 XML 与 SHA。
