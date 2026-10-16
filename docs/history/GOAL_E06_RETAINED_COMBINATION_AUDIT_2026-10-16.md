# E06 既有组合事实补录与电脑正向消费（开发备份）

本报告补录早于 Android03 接续页的实际服务、原模拟器及电脑消费事实，并在本批输入再次实际消费原 SAF 包。旧页只描述其当次 Android 运行，不能据“该次未跑 instrumentation”抹去此前已执行记录。E06 L2 仍缺受审批阻断的 Chrome 组合；本报告不计 E06 L2、D18 L3 或总目标通过。

## 输入、写权与影响映射

固定主树 `D:/acrylic-pda-outbound`、`codex/development`，输入 HEAD/实核远端 `e8dc7970dc01f190ebcb6c43b7edb49368700c31`。清理聊天已明确释放全部窗口且实际 idle，本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 为唯一写者，无子代理或本批重型任务。全部继承 Android/UI/文档/工具及私有请求原件保留。

本批证据根为 `.local/goal-20261016-resume/`。`input19.json` 保存 5405 路径，SHA `7641d76832bad43c1f07f60631bb6b561a3bf87ae82cca6849182df4063ad6f7`；`input20.json` 保存新增四个审计模块后的 5409 路径，SHA `d2b9682d0319c82eb5a6653521646bf82b0a15534f58919c36f47e3249c94faa`。同号 binary diff、status、未跟踪副本、worktrees、旧看板、写权、进程/监听/磁盘齐全。自审确认 input19 的 5405 路径逐字未变，仅新增本批工具。

| 改动路径 → 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `docs/parallel/R09/tests/*e06_retained*.py` 四模块 → 离线证据审计 | 实际读取原 HTTP/签名/全表/文件，核调用结果和输入漂移；不打开服务、数据库或设备 | `e06-retained-audit04/inputs.json` 共11708文件逐件长度/SHA，最终双次核同 |
| 未改 `tools/pda_rescue/` → 原包产品正向消费者 | 原 SAF 字节解密、包外登记根验签、实际 Excel 转换及独立逐值读取 | `e06-l2-bindings01/candidate-inputs.json` 的255路径仍全部匹配；本批实际输出在 audit03，audit04按原件复用 |
| 原 Android 与服务运行 → 历史组合事实 | 核原命令、原响应、数据增量/拒绝、原签与停机文件；列明现输入漂移 | 下述原目录及 audit04/source-bindings.json，不将不同输入拼为新组合通过 |
| 报告/进度索引/历史索引/看板 → 验收口径 | 链接、版本事实、当前区上限与精确路径暂存 | 版本事实只见看板生成块；旧看板先归档 |

## 原实际运行与本次离线补核

1. 原服务证据 `.local/goal-20261013/e06-conflict-audit-1539/`：prepare02、enabled01、lock01、independent01 实际 HTTP/MySQL 成功日志存在。原非空输入169表、59非空表；原业务行守恒，新增11空表后才建立明确合成的第二任务/库存/实物/主管。prepare01曾在服务迁移就绪前捕获旧清单，原断言失败、诊断和原件保留；不改称首次通过。
2. 本次重新核39份JDBC事实快照，每表原 `.hex.tsv` 行数、列数、NULL/十六进制编码和独立排序SHA一致，原数据库名、端口19332、datadir三元组一致。enabled01/facts-23→24只向association、member、idempotency及operation_event四表各追加一行，旧行全部保留；14对拒绝/重放事实除会话外逐值不变。末条SQL失败回滚、异文/成员重复拒绝、实际LOCK WAIT后SESSION_REVOKED及新JVM/default-off原键回读均有原始证据。并发/锁时序属于历史实际运行，本批只消费原件，没有重跑业务。
3. 从历史固定外部公钥独立验三份RS256原回执，核请求/context canonical摘要、额外成员/实物检查引用，核两个原ZIP和两个实物附件的原HTTP字节/长度/SHA；成员和主任务返回同一原结果。主任务ZIP同时与原Android电脑导出ZIP逐字相同。合成第二任务、实物与主管不能冒充原PDA现场事实；所有放行/记账/库存可用/删除字段保持false。
4. 原模拟器命令 `.local/goal-20261014/e06-l2-native-instrument01/command.json` 针对 `emulator-5562`，`IndependentSealedReadDeviceTest.originalSealedTaskRemainsReadOnlyAfterOfflineRestart` 原日志为 `OK (1 test)`、21.261秒。原新进程结果和前后83表、150个先存独立文件逐值守恒，本次重新核全部原行文件及长度/SHA。不是本机现在保留的TEMP5564，也不是物理PDA；本批未操作任何设备。
5. 原测试源码SHA `ba4e329358717c1007303d5ba1433fef2c23ec03435b3dc6d7d445c5817eecf2` 仍匹配。原 Android01 的90/0/0/0、Web类型/契约/构建exit0、多任务/旧消费者exit0、closure vector exit0只作为绑定历史结果；Android03后续短路径复验另见旧页，不把它们记为本批新执行。
6. 原服务JAR历史SHA `981b5c301a07f72763816a010a840b495e0a10acb5b5d7551d58f9f9033b5337`，280730630 bytes。1539重复JAR已按清理回执去重；沿 `.local/disk-cleanup-20261016-round4/duplicate-jar-plan.json` 定位保留的 `.local/goal-20261013/e06-conflict-audit-1537/server.jar`，本次长度/SHA完全相同。未恢复重复副本，也未启动旧库。
7. `.local/goal-20261014/preserve-1539-e06-l2-service-01/` 的347个保护文件本次逐件重核，与停机前清单长度/SHA仍一致；完整dump SHA `14a3db9f53374301ce75d3166c29785ff994db4f1bdfab1a9aebb035c5519d36`。原dump/shutdown均exit0、零监听；压缩前后清单结构不同，只按路径及原hash判定，不错误要求两个JSON结构相同。

## 本批实际电脑消费与输入漂移

`e06-retained-audit03/computer-output/` 是本批新输出：原 SAF SHA `b979d685981a6ea6f6e0db37874cf917057d54ee7ede0c3a609591ca5fd86966`，解密ZIP SHA `1577709afac180638244ea37623e13d63a57a2f73c520df595a7df6ba9c0c8e6`，与旧原ZIP逐字相同。产品Python使用包外原登记根验签、生成Excel；独立消费者实际回读460项值/类型/原顺序/有效整数克/非公式断言通过。五个最终XLSX预览已打开目检。仍为待对账、服务端授权未核验、非原生Excel和非现场事实。

本批复用已安装运行库，通过子进程显式设置PDA_RESCUE_NODE/模块路径及D盘TEMP/TMP；没有安装或搬移共享依赖。`runtime-command.json`保存命令和库package哈希。转换成功后仅做离线原件复核，没有重复转换以覆盖首次失败。

`source-bindings.json`精确登记旧清单中服务10项、Android16项漂移；`added-inputs.json`另登记旧清单未覆盖的服务57项和Android29项新增文件，不把“旧清单路径一致”误当整棵源码相同。

- 服务现存产品四路径为RecoveryFenceAuthority、RecoveryLeaseProcess、RecoveryLeaseRoots、RecoveryReleaseScopeSemantics：增加历史钥/lease历史核验及恢复schema兼容；新增产品类集中在同类恢复CLI/历史钥。其余漂移及新增多为恢复组合测试。E06业务域旧路径可定位复用，恢复启动/还原范围须按后续P07/E07报告及实际候选补受影响组合，不能声称整个服务输入未变。
- Android漂移包括TEMP保存失败确认反馈、设置/更新/身份/草稿/扫码布局和相应测试；新增包含连接诊断/样式与恢复、原模拟器用例。独立采集引擎、原封存读取测试、Web和Python既有255路径未变；受影响身份键盘、辅助页和TEMP入口仅按后续E02/E08正式报告SHA复用，不据此免除完整U组合或最终L3。

## 失败、自审与收尾

audit01把保全前后不同字段的清单直接比较，属于审计器假设错误；audit02缺显式现有Node模块路径，转换未发布完整目录，原partial目录保留；audit03电脑消费已通过，随后因原JAR去重路径缺失而退出。三者根因不同，均无数据库/设备写入，不清零任何旧失败计数。修正清单核对、运行库输入及原JAR取回关系后，audit04完整exit0；原run02/03日志、failure.txt和classification均保留。最终只读复核首次locale GBK解码错误也保留说明，改明确UTF-8后通过，未重跑业务。

`e06-retained-audit04/result.json` SHA `60b351b6a2378b5cfb80955b37e69fa0c6a20a6f9d33c9f888b2c4be6538e70a`；`inputs.json` SHA `5f28d3025cdb8e51b9d39f29a082b94c4bd12eec1cc64e0a664d452579e41cf0`；`final-review01.json`确认11708原件及5405继承输入再次逐字一致。四审计模块≤200行、函数体≤30行。作者/执行/自审为同一助手，独立算法/消费者不等于独立人员审查。

input20采样及最终环境另见同根disks/processes/listeners附件；C/D约82.8GB/44.5GB。本批未新建MySQL/JVM/AVD或业务监听，无本批清理。共享MySQL只观察不连接，原TEMP AVD/ADB保留。所有新证据和转换输出在D盘，预计增长小于100MB，未修改全局环境/HOME。

Chrome1540启动的自动审批 `blocked by policy` 仍未取得新允许，不能换包装或工具绕过；旧1540失败库和证据保留且不复用。下一步推进不依赖此审批的E07/E08受影响组合与D16合法引用退出依据；E09实际候选和D18最终全链仍必做。七类生产能力、TEMP生产归档及`localDeletionAllowed=false`保持关闭；不接原库/现网、不合main、不部署/发布/Release，不用正式签名。总目标active。
