# D13 已有绑定跨实例后继：真实产品完成事务提交与UNKNOWN

## 结论和边界

2026-10-23；固定树 `D:/acrylic-pda-outbound`，输入 `codex/development` / `64b607228813764b35437f6b296aa83de2bc0920`，实际开发远端核同，main不动。本片涉及9个Java测试/运行器文件、2个Python原件消费者和5个文档，不改产品源码、协议、数据库结构、Android/Web或生产默认。开发备份和清理不计验收进展；以下仅计实际执行的有界组合。

**真实MySQL提交与合成丢ACK必须分开**：实际生产worker完成导出事务，原驱动 `commit()` 成功返回后，测试边界合成 `SQLException(...,"08007")`。这不是实际网络断开或真正线路丢ACK。注入仅显式测试启动源，位于原migration池与产品 `RecoveryRequestAdmittedDataSource` / `RecoveryConnectionHandler` 之间；没有在产品代理外层丢异常、替换权威、手动造worker根或由测试调用halt。

P5供给仍 `syntheticTestOnly=true/productProvisioning=false`，不是可信生产供应。作者、自审为同一助手，Python是独立工具消费者而非独立人员。D13整体、其余入口/故障、可信权威及实际网络丢ACK、D16/D18与第8节仍未完成；总目标active。

## 实际输入与保护

- 根 `.local/d13-bound-jdbc-unknown-20261023/run01/`；baseline/before-implementation/before-cleanup保存完整dirty原字节、逐SHA、tracked patch、目标原件及实核进程/监听/设备/Keystore/磁盘。23条初始dirty含22条非看板继承，均与前片最终清单一致并保护；共享看板不整路径暂存。
- 其他项目和清理聊天复核idle；本助手独占本片，无子代理或消息。系统MySQL3306/33060身份未知，仅观察；ADB仅枚举两原设备，不安装、不清数据、不改签名。最终APK与Debug Keystore原SHA保持，未操作正式签名。
- 新资源1795/1796先核全部worktree及端口；不同UUID、datadir、端口、账号及独立随机凭据；真实认证身份、初始化/启动/dump/import/最终dump/优雅shutdown命令和回执在各attempt。已清实例不得重启复用。
- 冻结 `compile01` 的2753输入、110依赖及输出/命令SHA；与前片1139产品/资源/渲染器输入加两个服务端构建输入合计1141条逐路径/SHA相同（`input-review.json`），不将不同计数口径混用。未改产品范围只按相同字节复用，不免除最终候选L3。

## 同输入实际组合

1. 已有binding/NORMAL源，完整非空SQL/storage恢复到不同真实MySQL实例。恢复前后181表、8原文件及185触发器含definer核同；后继四表仅追加4/1/1/1、代次+1，原行与原源全事实保持；后继权威在原源拒绝。
2. 实际API和独立非Web生产worker启动，分别认证实际目标；P5根/身份/进程/HMAC/索引核绑定。原代次两旧任务仍失效；新键XLSX/PDF由实际renderer生成、HTTP下载和独立回读，原任务不改。
3. arm之后从真实HTTP创建第三个新XLSX任务。真实scheduler的WORKER_JOB根持CONNECTION及P5；实际ExportJobHandler生成并force文件，BackgroundJobLeases.complete更新SUCCEEDED、产物引用及REPORT_EXPORT_GENERATED审计。不是小表MODEL，也不是重复INSERT回滚。
4. 提交前暂停：同物理连接已见SUCCEEDED/产物/1条审计，**独立连接**只见RUNNING、attempt1、无artifact、0条完成审计。保存原始行、数据库/CONNECTION_ID和完整181表/文件快照；两个连接ID明确不同。
5. 放行真实driver commit，驱动成功返回后、合成丢ACK之前再次暂停：独立连接已见相同任务SUCCEEDED、产物、1条审计。前后只有background_job和operation_event变化，旧任务逐列不变，新任务仅六个预期列变化；其他表、文件和Flyway保持。核实际文件字节/哈希及审计jobId/字节/哈希一致，无双写或第二次审计。
6. 之后才合成08007，产品连接代理进入UNKNOWN/真实halt70。没有正常关闭标记，OS生命周期锁释放；故障后原lease/index/process逐字节保持，仅新增匹配operation/process的UNKNOWN原件。产品F1/F2/F3/F4审计保留两原日志流。
7. 六个真实HTTP先OUTSTANDING后五次FENCED；拒绝前后全部数据库/文件事实相同。扫描只退孤儿lease/index，UNKNOWN原字节保持；API自身admit/checkEffect也拒绝。旧API正常关闭，另一个fresh产品API认证同目标后启动exit1/OUTSTANDING，无ready/正常关闭标记、无新process/业务改写。空index不能解除UNKNOWN围栏。

## 改动与验证映射

| 路径/职责 | 影响与实跑 |
| --- | --- |
| e2averify/E2aSplitWorkerMain、E2aSplitWorkerProcesses | 仅新增显式commit测试scope与子进程握手；30个冻结直接调用输入编译exit0 |
| e2averify/E2aSuccessorCrossInstanceCommitIT、E2aCommitUnknownHttp | 新双实例全恢复/实际HTTP/worker/提交前后独立观察/拒绝守恒 |
| recovery/RecoveryCommitFaultConfiguration、RecoveryCommitFaultDataSource、RecoveryCommitFaultBoundary | 仅worker测试原池内侧单次真实完成事务注入，实际产品代理触发halt70 |
| recovery/RecoveryBoundCommitChecks、RecoveryBoundCommitProof | 原P5/OS锁/UNKNOWN/六HTTP/fresh拒绝核验 |
| docs/parallel/R09/tests/d13_commit_visibility.py、verify_d13_cross_instance_commit.py | 原件独立消费、逐表/列/文件/签标/身份/响应回读；7组篡改副本拒绝 |

Java完整路径均以 `server/src/test/java/com/acrylic/outbound/` 为前缀；源码清单/SHA在 `input-review.json` 和冻结清单，命令在对应目录。主要命令：

```powershell
python -B .local/d13-bound-jdbc-unknown-20261023/run01/compile.py compile01
python -B .local/d13-bound-jdbc-unknown-20261023/run01/launch.py run_unit.py unit01 unit compile01 unit01
python -B .local/d13-bound-jdbc-unknown-20261023/run01/adjacent.py
python -B .local/d13-bound-jdbc-unknown-20261023/run01/launch.py run_product.py it01 compile01 it01
python -B .local/d13-bound-jdbc-unknown-20261023/run01/consume.py it01 compile01 consumer01
python -B .local/d13-bound-jdbc-unknown-20261023/run01/launch.py challenge_consumer.py consumer-negative01
```

上述为可读简写；实际使用已安装Python绝对路径、完整argv、环境与退出码在launch/command.json及各子目录，不能仅凭命令重跑旧库或覆盖输出。

| 当次验证 | 结果 |
| --- | --- |
| compile01 | exit0，无输入/依赖漂移 |
| unit01 | 103/0/0/0，驱动exit0 |
| adjacent-compile01 | 30个直接调用输入，exit0 |
| it01与实际驱动 | 1/0/0/0，均exit0，新1795/1796 |
| consumer01 | exit0，原件proof生成 |
| consumer-negative01 | 正常副本接受，7组篡改拒绝，原件SHA保持；非新增业务测试数 |
| consumer-after-cleanup | exit0，逐项核已删清单后与原proof逐字节相同 |

本片无业务测试失败。最初工具启动假设了不存在的PowerShell7安装路径，未执行snapshot/facts即失败，已在实际已安装宿主内重试，原失误记于baseline/protection.json；不当作产品失败或隐藏为一次成功命令。提交前扫描先误报既有System.getenv凭据拼接（未暂存），精确排除该表达式后发现新叙述归档末尾空行（已暂存但未提交）；两轮退出/原检查文件和改前字节保留，trim仅移除末尾多余空行，不改历史原文事实，完整旧看板字节仍保留。前片观察器it01失败、普通halt71/原生退役拒绝、六死亡点仍只在各原报告范围保留，不重跑或覆盖。

## 原件与持续清理

原件proof SHA：`6c6ee971cb708cf2084fb308d2b70359aaae952edd5eec21ac313fd3c30b874b`；UNKNOWN原件SHA：`505888691e1da6ad0f3905d2e7c1f5f0827fccb1560f092a207628b2fd8691bc`；第三任务产物SHA：`2487ff25e259d30d5f6f59ce3ab72a6793c7deb658bf608c70c61610dbbde29b`。源378项输出及目标输出逐件哈希见it01两清单；完整控制lineage、签标/身份/HTTP/文件/行/DDL/Flyway/触发器/两流日志和编译冻结均保留。

两实例真实认证shutdown exit0、身份前后核同，新核无监听/消费者后，只清9个明确可重建datadir/binlog/index/dump路径，逻辑447946571字节。关键SQL原行先逐字提取为cleanup01/retained-recovery-rows.sql.txt，删前逐文件SHA及认证回执保留；D窗口28703924224→29160624128，观察净增456699904字节，分别报告逻辑量和卷变化。清后消费者核两输出清单4/3项缺件均匹配审计，完整proof字节相同。

7组篡改及正常基线的8个用途结束的副本目录另清15090974字节，保留构造命令/输入原SHA/逐件SHA/正常接受和逐项拒绝摘要；D窗口29160607744→29175836672，观察净增15228928字节。两次逻辑清理合计463037545字节（约0.431GiB），不是精确卷释放量。源原件、UNKNOWN、身份/签回执、失败证据、最终APK、WIP及继承改动均不清。

## 未验与接续

本片不改生产能力、取消范围或验收口径；localDeletionAllowed=false。实际网络ACK丢失、OS拒绝自halt、其他入口/故障及可信生产权威供给仍未完整组合，不把本机可做缺口转移现场。下一有界项先定向核已有工具与实际产品路径，选择确实未覆盖的故障组合，保存新输入/退出标准、分配新资源，不复用已清库。D13/D16/D18及其他D/O/U与第8节依旧未完成；原身份/真机/现场/最终候选缺口分别继承看板。

开发备份仅沿已授权codex/development、明确路径暂存/提交/推送；实际提交/推送回执在closeout-submit03；前两次暂存检查原件另保留closeout-submit。共享看板单独留WIP、提交后刷新事实避免循环提交，22项非看板继承保持。不合main、不连接原库/现网、不正式签名、不部署/Release，不恢复换机承接/多跳/接收者确认激活或旧run03。
