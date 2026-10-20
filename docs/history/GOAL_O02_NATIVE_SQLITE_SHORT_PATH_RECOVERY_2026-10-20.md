# O02 原生 SQLite 主机运行器短路径复验（2026-10-20）

## 本轮结论

固定树 D:/acrylic-pda-outbound、codex/development，输入2930d8ecf211eacf9d837f6854ab2854bbd5039a与保留WIP。未改产品、测试、SQLite模式、断言、业务配置或继承源码。

旧失败集合中的19个方法及2个ContractFixture检查在本轮一次有界执行中 **21 tests / 0 failures / 0 errors / 0 skipped，Gradle exit0**，六类XML逐方法核对。19项全部实际执行，未排除失败方法；旧43项历史通过未重跑，也不将两轮相加为同一次完整回归。此前单方法先行复验为1/0/0/0，不计入这21项总数。

这是当前源、合成契约/身份下的NATIVE Room主机消费者证据：FORMAL/TEMP原记录、冻结正文/已有回执逐字复制、身份清除/到期后只读、越范围拒绝、同事务快照与SCAN/VOID/冻结/回执交错、文件损坏/错签名/跨仓/加密产物恢复、规模及R06同人同机前驱原件读取。它不证明设备、SAF/USB/MTP/OTG、原身份、真实HTTP/MySQL、O02/O05整体或最终候选。

## 重新入队依据与原失败

旧原件保留于 .local/goal-20261020-resume/o02-fixture-recovery01/，verification04为21项中19个SQLite CANTOPEN，verification05单项仍失败；原日志/XML/报告未改写。verification05虽显示父目录存在且可写，但这不能排除native路径长度问题。

本轮只读实测旧诊断XML中的完整DB路径为292字符，WAL为296；改为子进程短TEMP D:/acrylic-pda-outbound/.local/t20o02a 后，按同一后缀估算220/224字符。历史 [O04本地消费者](GOAL_O04_INDEPENDENT_COLLECTION_ACCEPTANCE_2026-10-15.md)也明确记有深路径CANTOPEN、短路径复验通过，为新可验证假设，故没有机械重跑旧19项。

本轮保留SQLiteMode.NATIVE与原WorkFixture；未切换LEGACY、绕过Room、删除业务围栏或修改产品测试断言。成功支持短运行目录配置能解除本次阻断，不声称已证明所有Windows CANTOPEN都由同一原因导致。

## 运行分层与失败保全

证据根 .local/goal-20261020-resume/o02-short-path-diagnostic01/。

1. attempt01：只选原frozenPayloadAndReceiptAreCopiedFromRoomWithoutReserialization方法。为避免覆盖旧文件，把Test.workingDir设为新证据根；Android Gradle生成的test_config.properties却含相对资源路径，资源加载先失败（1/1/0/0，exit1），尚未触及SQLite，见result01.json与xml01/gradle01.log。不是业务或短TEMP假设反证。
2. attempt02：仍只选上述方法；在专有resources02中加入同一Android资源的绝对路径配置并置于测试classpath前端。原manifest/APK资源与生成配置不改，使用同一NATIVE runner，1/0/0/0、exit0；见runner-correction02.json、result02.json与xml02。
3. attempt03：单方法通过后，仅取verification04原20选择器（19失败方法与一类2检查），6 suite/21/0/0/0，exit0。逐方法及SHA在test-result03.json。普通方法名含RecoveredForExternalCopy表示“恢复生成文件”，不属于取消的recovered-handover；已按明确六类allowlist核对。R06前驱只读属于同人同机原件，不恢复换机承接链。

准备时有内联选择器校验脚本失败，均发生于Gradle启动前：过宽的字符串排除错误匹配普通文件恢复方法，后改精确类清单；完整说明在selector-preparation-note.txt。实际只有上述三次Gradle测试，失败原件与每次日志各自保留。

主要命令：

    gradlew.bat -p android :app:testDebugUnitTest
      --offline --no-daemon --console=plain --max-workers=1 --no-build-cache
      -I <证据根>/short-temp-resources03.init.gradle
      -Pkotlin.compiler.execution.strategy=in-process
      --tests <selectors03.json中的20个原选择器>

绝对命令、实际环境、启动时间及退出回执分别为command01/02/03.json、gradle01/02/03.exit。TEMP/TMP/java.io.tmpdir仅对子进程使用D盘短目录；原全局环境不改。XML/HTML/binary、工作目录及生成文件全部指向本轮根，目标Test强制实际执行且禁用缓存，无UP-TO-DATE假通过。12份既有默认XML/HTML逐SHA仍保持；未调用APK打包任务或覆盖最终APK。

## 逐输入守恒与产物

- 1022份Android/包装器源输入、10份原fixture、31条非看板继承路径逐SHA无漂移；原失败XML及本轮Android资源字节亦保持，详见input-manifest.json、test-result03.json、prior-reports-preserved.json。
- 原合成契约由既有服务端生产者生成，未重新造身份/回执替代真实资料；本轮未连接任何MySQL、业务HTTP或设备。
- 新formal.zip、formal-frozen.zip、temporary.zip、room-generated.pdarescue和large.zip属于本轮合成输出，生成位置/逐文件哈希在test-result03.json；保留为后续只读消费者输入，不将生成文件等同外置副本已核验。

| 文件 | SHA-256 |
| --- | --- |
| xml02 单方法XML | 6703a3f4c694928889dbd5ad15d57c779b572e51ac4fc63ee91d25031921c2c9 |
| gradle02.log | ba828156858d0a5d397010b453ddcd7e68f9a8c0831a0715ec5407916b95c3f3 |
| test-result03.json | d0c62d2639844d1b86c4a46eb8cc885cfa4b9065fd8268ac937e249b75bce12f |
| test-reports03.zip | 198b4532d4117b0763d83e0751a26694bba4d2b2b3f0280176c6c9c76a7657e9 |

作者/执行/自审均本助手；产品消费者不等于独立人员审查。收口Git、工作区、环境和输出总表见closeout.md/final-manifest.json。临时目录约50MB保留，释放0字节；前次清理自动审批拒绝未换包装重试，不累计历史9.44GiB。

## 后续及关闭边界

本轮解决的是旧19个方法在当前主机的可执行性与原断言验证，后续测试应使用短子进程TEMP和正确绝对资源引用。仍须同一候选的完整相关回归及O02文件/客户端/HTTP组合，不能用本轮21项代替。原身份、可信来源、外置介质和现场条件继续单列；不重复已验字号/SAF或已取消链。

总目标active、未完成；第8节仍未满足。原库/现网、生产决定、正式签名、main、部署与Release关闭，localDeletionAllowed=false。开发备份、治理、清理本身不计验收。
