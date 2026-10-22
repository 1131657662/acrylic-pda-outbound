# 提交源完整普通回归与原件条件消费者

固定开发提交的完整服务端普通测试实际执行4810项，4809通过、0失败/错误、1跳过。四类依赖外部原件的消费者在同一次完整运行中展开24项并全部通过；没有改写旧轮19项跳过。仅收口本片普通回归，不计专项IT、D18最终候选或第8节通过，总目标active、未完成。

## 输入、历史关系与写权

- 树D:/acrylic-pda-outbound，codex/development；输入HEAD和实核开发远端571c34aa9eb8d08f0fb643f36542259a575c7702。main未改，版本事实见看板生成块。
- 证据根`.local/goal-20261022-resume/server-skips01/`。baseline.json、inherited-wip.zip、tracked/staged.patch及status/refs/remote/worktrees保全40条展开继承WIP；开始前逐SHA核同前片收口。独立源`.local/s22s/`从固定提交git archive导出全部6055文件，不叠加继承源码；build/cache在`.local/s22b/`，仅本轮子进程TEMP/TMP和Java临时目录在`.local/s22t/`。
- 本聊天单独执行/自审，无子代理；其他清理聊天仅有磁盘范围，未向其发送消息。运行前后没有其他Java/Gradle写者；主树非本片文档维护的继承字节保持，最终逐路径清单见closeout/。
- 原普通回归的310份XML逐项核同历史SHA，4804项、19跳过保持；空skipped标签没有原因正文，原因来自同源注解、assumption及输入核查。historical-ordinary-audit.json保存逐方法映射，不以摘要改写旧XML。
- 后续发现[历史封存消费者](GOAL_E06_CURRENT_SEALED_CONSUMERS_2026-10-20.md)已有四类24项实际通过；四份XML及33原件/两个归档逐SHA核同。该轮2438项源清单中22项后来变化，均属server；历史局部结果不能直接替代本次完整提交输入。historical-consumers-audit.json及materials-and-source-audit.json保存核对。本片没有再单独重复旧24项。

## 本轮命令与实际结果

完整命令、所设环境和字体哈希见command.json；运行器run-ordinary.py与isolate.gradle原样保留。使用独立源gradlew.bat、`-p <独立源>/server test --offline --no-daemon --console=plain --max-workers=1 --no-build-cache --project-cache-dir <专用cache> -I <专用init> -Pr08FontFile=<既有字体>`。不使用测试过滤器、不排除prepareFont，不复用UP-TO-DATE测试结果。

测试workingDir保留同提交server源码，src、contracts及docs相对输入完整；两个recovery子进程classpath任务生成后桥接到独立源server/build，避免沿主树旧输出启动。清除继承的相关业务/恢复环境变量后，只设置本轮显式输入；实际runtime classpath另存JSON。字体核原SHA后走原prepareFont，不另行下载或替换。

公开原件、两个归档及授权SKU样本共36份只读复制，来源与副本SHA均存external-inputs.json。原身份、Keystore和生产秘密不用于测试；原件来自历史合成隔离Android产物，不冒充现场身份。新SKU输出仅写独立源.local/sku-parity.json；本片未让Android消费本轮新向量。

| 范围 | tests/failures/errors/skipped |
| --- | --- |
| 全部普通测试，310份XML | 4810/0/0/1 |
| SkuParserParityVectorTest | 1/0/0/0 |
| RelayIndependentOriginalTest（参数化实际展开） | 14/0/0/0 |
| RelayIndependentCollectionTest | 6/0/0/0 |
| RelayIndependentClosureTest | 3/0/0/0 |

Gradle实际运行11个任务，退出0，7m5s；exit.json记录结束于2026-10-22T19:49:25+08:00。普通test按原构建定义排除IT，因此此处没有新增真实HTTP/MySQL业务验收。

唯一跳过为SourceEvidenceIntakeFilesTest.symbolicLinkTargetCannotBeAccepted。源码在Windows创建符号链接遇FileSystemException后以assumption跳过；本轮XML仍为空skipped，不宣称其记录了异常细节。未改测试条件、未以junction替代、未请求提升权限，符号链接断言本轮未执行。历史18条外部条件条目包含未展开参数化模板，配置原件后展开24项，因此总数较原4804增加6；不能把这24项再次加到4810上。

## 输入守恒、保全与清理

input-conservation.json核6055份提交源及36份外部来源/副本全同，sourceBad/externalBad均空；原签原件、历史XML/日志和归档保留。本轮没有改产品源码、测试、契约、迁移、权限或生产配置。用户批准的四状态只读筛选已有实现和消费者，本轮没有重复开发或重跑其字号用例。

关键输出SHA-256：

| 文件（本片证据根内） | SHA-256 |
| --- | --- |
| result.json（含全部suite逐SHA） | 0958f3e66b882cba02b76a7290d64d603a91d78be9a9d35131e353c8c1b5b8e7 |
| input-conservation.json | 49a71ecce6d99017427496656d9ac13844e4d900e3fb976795c89304cb21e263 |
| gradle.log | ac0bdc73ae462f72c58fbd99bb3c1d3c4cb96208d1f91c83a40c258a64e20054 |
| source-manifest.json | c2a7fe1b5927f08c067b52a7cbe73d451b3f657e341a0de182de4cbde7287cae |
| external-inputs.json | d1b3777e2503efd853a18a87cda843da7f91a3aae6c40f31b7a1cdbcad6074f2 |

文档维护前原字节/不存在状态见docs-before-closeout.zip和document-preimages.json；运行前旧看板原字节在board-before-run.zip，叙述另见[归档](ORCHESTRATION_STATUS_BEFORE_ORDINARY_2026-10-22.md)。只把本片报告、归档和两条索引变化作开发备份；历史索引仅暂存HEAD加本片入口，继承段落不混入提交，看板保持本地断点。

实际认证本轮Python/Gradle/test JVM均已退出，之后仅对本轮专用project-cache做无损NTFS压缩：3121678逻辑字节，减少分配1814931字节，删除0，全部长度/SHA保持；D盘余量38778667008→38780481536字节。cache-compact-plan/result/log及runtime-before-compact.json保留明确路径、命令/退出码、逐文件SHA和空间；不重复累计前片压缩、不重试既有自动审批拒绝。原XML/日志、输入和输出、数据库事实、原资料/身份/Keystore/原签回执、唯一证据、最终APK和继承源码均保留。

## 适用限制与下一动作

系统MySQL80及3306/33060仅观察服务/监听，未连接SQL且内部库身份未认证；ADB只枚举，未操作设备/模拟器或签名。没有APK构建/安装、专项IT、真实业务HTTP/MySQL、Chrome、外置介质、部署或Release。作者、执行及自审均为同一助手，不称独立人员审查。

本片提交只含文档，不改变已测产品输入；Git回执、实际远端和完整WIP终态另存closeout/。前片32项Android、旧24项与本片普通回归各按各自输入计证，不拼为最终候选。

下一有界动作沿D16核正常成功订单有效work_guard的可信退出与完整引用/可取回证明，先形成与既有90天/五年规则一致的最小开发方案；不能删guard、改状态或补造归档资格让现有工具接受目标。D16仍是开发缺口，D18及其余矩阵/第8节尚未满足，不移入C档。原资料/旧BCD、原身份/现场、完整恢复与最终整合按矩阵继续。

七类生产决定/TEMP生产归档保持fail-closed，localDeletionAllowed=false；换机承接、多跳、接收者确认/激活与旧run03不恢复。正式签名、原库/现网、main、部署和Release关闭。
