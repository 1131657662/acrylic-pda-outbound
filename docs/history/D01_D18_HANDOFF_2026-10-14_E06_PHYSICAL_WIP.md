# D01–D18 接续：E06实物证据开发备份与暂停

## 1. 收口结论与范围

用户要求收口在途工作并交给新会话。本次停止新增功能及补验，不把预算、暂停或开发备份记为验收通过。总目标未完成，按用户要求暂停；新会话显式恢复后接手唯一写权。

E05净值/O03已按各验收页收口，不从头重做。E06资料、身份、任务、扫码/VOID、导出、完整结束、主管核原及完整核对依据均沿后续验收记录接续。本次只将**实物证据登记保存为开发中备份**，未完的稳定全量非空/组合守恒、人工关联与跨机冲突保留。不能据本备份判E06、O04/U03、D14或D18整体通过。

实物WIP已有专用默认关闭入口、同仓双权限、实际附件与逐包观察、完整上下文绑定、不可变签名回执、原键恢复及Web产品表单；不授关联/库存/记账/放行/删除能力。设计依据见[实物设计](GOAL_E06_PHYSICAL_DESIGN_2026-10-14.md)，源码与迁移精确路径/哈希见本机`e06-physical-audit-20261014-01/scope-hashes.json`。版本事实只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。

工作树固定`D:/acrylic-pda-outbound`，分支`codex/development`；收口前HEAD及实际远端均为`52f9626de00dfe00b4fe2637ac483cad9758008a`。23明确路径实物开发备份为`4fead0c7abc1ef77e6ecff70dd5df6e44b98e5d5`；本接续页的文档提交/推送回执、最终HEAD和全部残留改动由下述final快照保存，接手先核实际状态。

本机统一前缀：

- `A = D:/acrylic-pda-outbound/.local/goal-20261013`
- `H = D:/acrylic-pda-outbound/.local/goal-20261014/handoff-e06-physical-wip-01`

## 2. 可复用证据与本次收口核对

| 范围 | 原证据及输入关系 | 能证明的边界 |
| --- | --- | --- |
| 定向服务/构建 | `A/e06-physical-unit-final01`：110项、零失败/错误/跳过，bootJar成功；本次2404项输入全匹配 | 定向普通测试与构建；非完整服务/所有IT |
| 空执行 | `A/e06-physical-unit-final02`：UP-TO-DATE、tests=0 | 不能计新增测试执行 |
| HTTP产品/附件 | `A/e06-physical-http-06`、`http-03`及对应日志 | 实际Android已结束原包、合成主管/实物附件；权限、原键、字节、尾SQL、锁后撤权等定向证据 |
| 新JVM/独立消费 | `A/e06-physical-http-final02` | 固定外部公钥、重启后原记录、容量和篡改拒绝；不称原物理PDA/现场实物 |
| Web | `A/e06-physical-browser-06`：退出零，8项输入全匹配；390宽丢响应/重载原键截图已目视 | 实际Chrome一场景；不是完整UI重设计通过 |
| Web构建/契约/消费者 | `A/e06-physical-web-build02.log`、`e06-physical-contract01.log`、`e06-physical-consumer02.log` | 继承构建/契约及四项消费者通过，非本次重跑 |
| 非空/default-off | `A/e06-physical-audit-1521/disabled` | 原非空输入实际迁移；两个写入口关闭并记录物理表零写。旧快照格式无独立COUNT，严格全量守恒仍待稳定消费者补证 |
| 作者自审 | `A/e06-physical-audit-20261014-01` | runtime-binding、源码/迁移/类字节、权限/事务/签名/原键等作者自审；不是独立人员审查 |
| 收口核对 | `H/input-comparison.json` | 22项实物范围、2404项服务输入、8项浏览器输入全匹配；本次未改业务行为/测试/迁移 |

绑定JAR为`A/e06-physical-runtime-final03/server.jar`，SHA256为`133df851cde1af5656fed91d8d9d73dd059ed4d0e732dfd0e234bf234cfd578e`，原绑定记录见`runtime-binding.json`；接手核实际文件。源码备份不含`.local`数据库、密钥、APK、原资料、私有文件或实际附件，不能称这些已异地备份。

## 3. 失败、保全及下一补验的具体入口

| 尝试/指纹 | 实际结果与解释 | 保全 |
| --- | --- | --- |
| 两次Gradle入口错误 | 从server目录调用不存在的wrapper、错误选择`:server:test`；均未执行测试。正确入口是根wrapper加`-p server`，见`A/e06-physical-build.ps1`。没有专门原日志，本记录为事后事实登记，不冒充原日志 | 继承已验XML与构建不覆盖 |
| 1520元数据误禁 | 原断言只允许Flyway变化，健康/CSRF读取会写Session；运行器允许变化集合已修正 | `A/e06-physical-audit-1520/disabled-consumer.log`、失败dump/hash/datadir |
| 1521动态字段误比 | `checkedAt`为每次页面读取生成的显示时间；仅排除此字段后比较其余完整内容/摘要 | `A/e06-physical-audit-1521/enabled-consumer.log`、dump/hash/datadir |
| 1522全量SQL读取异常 | 组合登记、原键回读、实际附件/原包/上下文检查执行，最终守恒出现warehouse before=0、after=2；不能据此判业务漂移或通过 | `A/e06-physical-audit-1522`：enabled日志、before/successful、database.sql/hash；8次单表诊断均186字节同哈希 |
| 1523全量SQL读取异常 | 改ASCII逐列十六进制、独立COUNT/字段数校验后，baseline在SHOW COLUMNS断言失败；未启动API/未做业务补验。失败未记录表名/原输出，疑似app_user但尚未证实 | `A/e06-physical-audit-1523/baseline-consumer.log`；只生成首表文件，单独app_user读取成功6行；15对有/无分号读取均232字节同哈希 |

1522/1523按**同一潜在SQL事实读取根因连续2次**保守继承，未查明原因，不置零。没有新证据的第3次失败或主动诊断60分钟未收敛时，暂缓此消费者及直接依赖部分，推进独立项。健康Session/动态checkedAt是另外已解释的运行器错误，不混计；业务补验尚未通过。

下一步直接读取：

1. `A/e06-physical-stable-facts.py`与`A/e06-physical-audit-consumer-1523.py`。先为每次SQL保存表名、真实参数（配置文件路径即可，勿打印密码）、stdout/stderr/退出码；核查消费者导入上下文和子进程stdin，依据新证据再决定CLI修复或已安装JDBC读取。本机Python为`C:/Python314/python.exe`；未安装pymysql/mysql包，不为换方案重复安装整套工具。
2. **不能原样调用1523脚本做default-off**：底部只有baseline/combination分派；其中未被调用的defaults使用旧raw快照，而新stable快照包含columns/hex摘要，格式也不可直接比较。下一版须用同一种算法取得迁移前后事实，并正确分派，不修改旧结果或把动态业务事实加入宽松白名单。
3. `capture()`使用新目录且拒绝覆盖。原失败目录与1520–1523库不复种，不吞空输出/断言；先只读最小诊断。下一业务验证使用核实未占用的新编号（1524只是候选，不是预分配），端口19332/19436，不能碰保留的19330/19435或19331/19434。
4. 环境模板`A/e06-physical-audit-environment-1523.ps1`支持provision/start-disabled/start-enabled/stop-api；恢复源是保全1514 dump与1514私有文件，沿原固定钥。生成新副本时核所有编号/端口/路径替换、输入哈希、预计增长和实际库身份，不直接重跑旧模板。
5. 稳定补验退出标准：严格非空迁移/原事实守恒；default-off；同新隔离输入的原包核对→实际附件→登记→原键/文件回读→业务表守恒组合。只允许Session/实物/命令审计等预定变化，不允许库存/占用/submission/result/ledger漂移。保留全部原HTTP/签名/数据库证据后形成实物验收报告。
6. 实物验收后继续主管人工关联、跨机原件冲突消费者，再E07恢复/外置副本、E08留存/UI缺口和E09候选；按总计划第8节逐行核最终闸门。不能因暂缓跳整步或把开发缺口转为现场条件。

## 4. 运行环境、磁盘和写权交接

本轮1520–1523均已保全并认证停机；1523的新增保全位于`A/e05-preserve-1523-e06-physical-audit-close01`，含完整dump、SHA256、身份、shutdown与零监听回执。`H/preserve-1523.log`/`preserve-result.json`登记实际命令和退出零；失败datadir/binlog与原日志保留。本轮未清理任何开发或业务字节。

以下环境继续保留；PID为截点值，接手核实际命令/监听：

| 环境 | 端口/PID | 说明 |
| --- | --- | --- |
| 预览库1494 | 19330 / 11308 | 不复种、不停机 |
| 预览API/worker | 19435 / 36640；worker1192 | E05保留JAR及renderer依赖 |
| Vite | 5174 / 37712 | 网页预览保留 |
| 实物库1519 | 19331 / 13268 | 继承HTTP/浏览器输入保留 |
| 实物API | 19434 / 40236 | physical-runtime-final03固定JAR |
| 共享与身份 | 3306、ADB5037、AVD5562/5564 | Keystore/AVD保持 |
| 补验端口 | 19332/19436无监听 | 1523已停机，不表示旧失败库可复种 |

无重型构建/测试/消费者在途；闲置Gradle daemon不是写者。收口时网页5174与两API健康只读检查、进程/监听及精确磁盘由`H/final`记录。1523保全后C/D余量为56277774336/25649074176字节，后续以final/disks.json为准。

新增数据库/导出/日志/APK/临时目录优先D盘`.local`或既有D盘产物位置；TEMP/TMP/Java临时仅子进程，不改全局或HOME/CODEX_HOME。每个重型动作先量余量及预计增长。清理仅限确认归属、无消费者、可实际重建的开发产物，先保存输入/命令/锁/结果/哈希并核绝对路径；失败库/dump/binlog、原日志/XML/HTTP签名/原件/原资料/唯一产物/最终APK不得删。清理不改变localDeletionAllowed=false或D16标准。

`H/final`保存最终HEAD/实际远端、全部未提交/未跟踪原字节/hash/diff、进程/监听/磁盘和继承保护比较；`H/release.json`在暂停后登记本聊天释放业务/Git/数据库写权。最终快照生成工具为`A/e06-audit-resume-checkpoint.ps1`，手写断点不替代实际核验。继承Android/UI/历史/工具与AGENTS外部上下文保持，本机`server/physical-disposition-last-request.json`保留且不提交。

暂缓旧项仍继承：U01/U02首屏519dp>509dp连续3次、草稿添加无产品连续3次；D04/A1.4/1043完整前缀5次、原资料/旧BCD/526未验、full01/02 native崩溃、1342失败/1343取消、1364/1365恢复失败。不恢复已取消换机承接，无新依据不重跑。

## 5. 可直接复制给新会话

```text
/goal 恢复并完整执行 D:/acrylic-pda-outbound/docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md，完成D01–D18、第8节全部必做闸门、O01–O05/U01–U04及UI重设计。

严格按 D:/acrylic-pda-outbound/docs/history/D01_D18_HANDOFF_2026-10-14_E06_PHYSICAL_WIP.md 接续。先读实际AGENTS.md、Git工作流、看板当前区/生成事实块和本接续页；核实际HEAD/分支/远端、全部改动与.local/goal-20261014/handoff-e06-physical-wip-01/final快照、release.json/目标暂停/实际写者、进程监听/隔离库及C/D余量后接手唯一写权。旧聊天已按用户要求暂停并释放写权，不恢复旧执行；保留网页预览和继承1519实物环境。

E05净值/O03及E06资料、身份、任务、SCAN/VOID、导出、结束、核原/主管核对的已验切片沿验收页复用，不从头重做。E06实物登记仅作开发中备份，严格非空/组合守恒仍未过，人工关联/跨机冲突仍未实现验收。第一步读取e06-physical-stable-facts.py和audit-consumer-1523.py及1522/1523失败原日志，为SQL读取补表名/参数/原stdout/stderr/退出码，再按新证据诊断；同潜在根因连续2次继承，不原样换库重跑、不吞异常。1523已dump/hash认证停机，失败库保留，后续业务补验只新隔离副本；1524仅候选需核未占用。1523消费者的default-off分派及raw/hex比较格式缺口按接续页修正。

固定D:/acrylic-pda-outbound，单助手单写者，保护继承Android/UI/文档/工具和本机请求原件。按已有开发授权自主决定并记录最小方案，逐项实现→定向验证→作者自审修复/复验→明确路径提交推送codex/development。实物补验收口后继续人工关联/跨机冲突，再E07恢复/外置副本、E08留存/UI、E09候选及全部剩余闸门。旧暂缓计数不清零，同根因3次无新证据或主动诊断60分钟未收敛则保全、暂缓直接依赖并推进独立项；不记通过，不缩小目标。

不接原库/现网、不合main、不发布部署，不绕过设备/审批阻断，不恢复已取消换机进度承接；生产默认七类决定/TEMP归档、localDeletionAllowed=false与D16标准保持。合成身份/实物/签名和模拟器不冒充原设备现场事实。

严格继承磁盘规则：新产物优先D盘.local，TEMP/TMP/Java临时仅子进程，不改全局或HOME/CODEX_HOME；重型动作先测C/D余量/预计增长。清理先保SHA/改动/命令/锁/结果/哈希，核明确绝对路径与消费者；失败库/datadir/dump/binlog、原日志/XML/HTTP签名/原资料、Keystore/AVD身份、唯一证据与最终APK保留。每阶段更新看板/磁盘和证据；只有全部完成闸门实际满足才标总目标complete。
```
