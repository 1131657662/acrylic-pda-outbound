# D15 恢复后任务事实与受限监控组合开发备份

新1668实例原样恢复667正式成功备份、私有文件和验签配置；实际CLI正确消费原空任务及明确合成的非空任务恢复副本，7项全部通过。此为D15/E07受影响验收单元，不计D15整体、E07 L2、D18 L3或总目标通过。

## 输入、设计与写权

固定树`D:/acrylic-pda-outbound`、分支`codex/development`，输入HEAD/实际远端`0b4e5073e27b09cca9d53d1ab631a8b16b01f9b4`。本恢复聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`唯一写者；清理聊天明确complete-released且实查idle，进度聊天idle，无子代理。证据根`.local/goal-20261016/d15-restored-jobs01/`。input01保全5385个继承输入；运行input02/03共5387项、SHA`fa1db06d7171f3aee29a480ce3bcd46fafa2b76deb69547e6ccbbba737b1ce80`一致，同号binary diff/未跟踪原字节/status/worktree/旧看板/写权/进程/监听/磁盘保留。

自主决定见`design01.md`：667原备份任务表为空，先严格原样恢复并验收；只在新派生库添加显式合成任务，再备份/恢复到空伴随schema验证监控。不将这些任务冒充真实业务任务，不修改或重启667。整个监控产品源码未变；新工具只服务隔离验收。

| 改动路径 → 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `tools/d15_jobs_restore_support.py` → 既有dump/私有文件恢复夹具 | 新实例身份/空库核验、原dump和派生dump实际import/dump、全表事实/文件守恒 | 原667报告[产品仓库恢复组合](GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md)与consumer02清单SHA`180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3`；输入02逐路径 |
| `tools/d15_jobs_restore_it.py` → 真实CLI消费者与权限反例 | 7项真实MySQL/CLI、默认告警/显式阈值正向、逐行独立计数、错UUID/撤权/载荷和写入拒绝/非空覆盖拒绝 | `reused-monitoring-inputs03.json`逐路径核7个监控产品文件与[D15 L1](GOAL_D15_JOBS_MONITOR_ACCEPTANCE_2026-10-15.md)最终测试输入一致；CLI/fixture/preserve代码未变 |
| 报告/索引/看板 → 追溯 | 自审、旧结论快照、精确范围暂存、事实生成及全部输入复核 | `review-result03.json`核5385继承输入不变、运行输入无漂移，新增两路径SHA绑定 |

没有产品源码、迁移、模型、API、Android/Web布局或写行为变化；本单元不新增Chrome、Android或业务HTTP结论，既有业务权限/事务/幂等/并发/末SQL回滚仅按未变路径及原范围复用，不能免除后续L2/L3。

## 实际运行

`allocation1668.json`先核33 worktree同号路径不存在、19378独占socket可绑定及C/D余量，才分配`.local/d15-monitor-1668/`、DB19378、schema`d15_monitor_1668`及`_restored`。预计增长≤1GiB、预留≥3GiB；实增约0.28GB，新增证据/数据库/dump/binlog/TEMP/TMP均D盘，仅子进程设置。未连接共享MySQL或旧失败库。

1. `run01.py`顺序调用既有`tools/d15_jobs_lab.ps1 -Run 1668 -Port 19378`及既有venv的`tools/d15_jobs_restore_it.py`，lab/tests退出均0。原`tests01.log`为7 tests、30.515秒、0失败/错误/跳过。
2. 只读核667全部604原件261439064 bytes后，将`private/archive-snapshot.sql`实际导入新空库。原dump和`original-restored.sql`逐字相同，SHA`fa024e4683d9471fd329107614993d4bec0368a6c919ae333f9bb363a8bcca30`；包含180张表322行（含迁移历史）。七个私有存储文件及两份开发验签配置逐字复制守恒；无新签名或原身份声明。
3. 原空任务实际`jobs-monitor`退出0/PASS/total0。只在新派生源库添加SUCCEEDED/FAILED/RUNNING/RETRY/PENDING各一条合成任务，所有其他表事实不变。备份后实际恢复到空伴随库，180张表327行全部相同，两个dump逐字相同，SHA`ac715c33340d9ee8ae7a6161e2b6d1ef1f9d9a7a4e38f888178e5a12dde7f6b6`。
4. 恢复后默认监控实际退出2，报告total5、failed1、expired_leases1、ready2、retry1、等待≥600秒；三个应告警项精确匹配。显式阈值仅放入独立测试配置后实际退出0/PASS，原产品默认不变。独立Python读取逐行status/时间计算预期计数，未调用产品聚合函数生成预期。
5. 错UUID、撤销列级权限均固定码拒绝。实际列级账号不能SELECT payload、DELETE、UPDATE或多语句；恢复器对非空目标写前拒绝。六次CLI、权限与非空覆盖及最终dump共九份守恒回执逐表前后相同，stdout无CANARY/密码/SQL，stderr为空。

## 自审、原失败与保全

作者与自审同一助手；独立消费指Python逐行算法及原生dump字节比较，不冒充独立人员审查。两新增工具147/87行、函数体均≤30行，严格目标schema/port/datadir、原文件hash及空库前置保留；无硬编码秘密、无生产入口或开关变化。旧继承Android/UI/文档/工具及`server/physical-disposition-last-request.json`未混入提交。

业务首轮全部通过。只读review01错误预期十份回执，实际九份；review02误把最终dump守恒的独立`facts-before-shutdown.json`当内嵌before字段，修正后review03逐项通过。一次PowerShell内联引号失败发生在修复脚本创建前，随后使用字面文件修复。原review01/02、修复脚本及failure说明全部保留，没有重跑数据库业务、放宽事实标准或覆盖原件。

既有`tools/d15_jobs_preserve.py`认证目标身份后dump两个schema，逐表复核、认证shutdown退出0；server.log记录正常shutdown。清单597文件经自审双次长度/hash不变，19378零监听，进程命令行无本批datadir。Windows mysqld包装PID与真正server PID可能不同，自审同时核端口与所有mysqld命令行，没有只凭旧PID宣称停机。原667清单前后不变。C/D收尾余量82983694336/21807439872 bytes见disks-03；未清理材料，原AVD/ADB及共享mysqld仅观察保留。

## 后续与未验

仅恢复后的只读任务监控得到本单元验收；未执行恢复后真实worker任务、完整应用写链、Linux/systemd限额、外部告警/异地介质、生产规模/RPO/RTO。不把合成任务、同机副本或CLI健康冒充这些标准。D15整体、E06/E07/E08剩余L2、D16正常合法退出及复杂引用、E09/D18最终候选和总目标仍未完成。

下一项核对D16正常有效guard目标的合法退出及复杂引用/可取回缺口，保护guard；再推进D13–D15剩余组合及独立候选范围。生产七类决定/TEMP归档保持关闭，`localDeletionAllowed=false`，不接原库/现网、不合main、不发布部署、不使用正式签名，不恢复取消的换机链。
