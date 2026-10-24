# D13 已有绑定跨实例后继：真实 TCP COMMIT 应答丢失

## 结论与边界

实际组合于2026-10-24完成，证据目录名沿2026-10-23开工日。固定树`D:/acrylic-pda-outbound`，输入`codex/development` / `5b999439172ad0c2ecd2db23e56e5c5e59f02dc4`，实际开发远端核同，main不动。仅10个Java测试接线/观察文件与5个Python测试/消费者，以及本片文档；未改产品源码、契约、迁移、Android/Web或生产默认。

本片消除上一片“driver成功返回后合成08007”的证据缺口：**原服务器实际COMMIT OK包被本机TCP代理扣留、从未转发，driver尚未返回时独立直连已见提交；随后关闭该连接，原MySQL driver自行抛CommunicationsException / 08S01并原样到达产品代理，实际产品halt70。** 测试不制造SQLException、不由测试调用halt、不以DTO或小表模型替代实际导出事务。

仅合成测试权威，`syntheticTestOnly=true/productProvisioning=false`。作者/自审是同一助手，Python为独立工具消费者而非独立人员。D13整体、可信生产供应、其余入口/故障、OS拒绝自halt、D16/D18及第8节仍未完成；总目标active。构建、清理、文档、开发备份均不计业务验收。

## 输入、写权和原件

- 根`.local/d13-bound-wire-ack-loss-20261023/run01/`。baseline、before-implementation、before-review-fix、before-cleanup02保存全部dirty原字节/SHA、tracked patch、派生来源与实核环境；前片22条非看板继承逐字节保护，不代提交或清理。
- `compile02`冻结2766源/资源/工具输入、110依赖，最终输入无漂移；1141产品/资源/renderer及三构建文件与前片逐路径/SHA相同，见input-review.json、pre-cleanup-review.json。不以未改输入免除最终候选L3。
- 无委派/消息；本助手独占本片及新隔离资源。续接采样时本项目其他聊天notLoaded，其他项目及C清理聊天active，仅记实际状态；共享缓存不属本片清理范围。旧PID不当活跃事实。
- 系统3306/33060身份未知，仅观察，不连接/停止。ADB仅枚举原两设备；续接快照枚举时ADB daemon自行启动，未操作设备。Debug Keystore和最终APK原SHA保持，无正式签名。
- it01新1797/1798失败后保全且认证停机；it02重新分配1799/1800，不复用失败库。各源/目标不同UUID、端口、datadir、账号及随机凭据，初始化/认证/SQL恢复/最终dump/优雅停机argv与退出码留在各attempt。

## 同输入实际组合

1. 已有binding/NORMAL源，完整非空SQL/storage恢复到另一真实MySQL实例；181表、8原文件、185触发器含definer核同。后继四表仅追加4/1/1/1、代次+1；原binding/NORMAL及原源事实保持，原源使用后继权威拒绝。
2. 实际API和独立非Web生产worker分别认证目标；P5原件/进程/HMAC/index核同。原代次旧任务失效，新键XLSX/PDF由真实renderer生成，经HTTP下载独立逐值/顺序/重量解析。API额外P5观察使用显式测试token；第三任务由实际scheduler取得WORKER_JOB根和连接/P5，不用手造worker根代替。
3. 真实HTTP创建第三个XLSX任务。完成事务中同物理连接见SUCCEEDED、实际artifact和1条REPORT_EXPORT_GENERATED；独立直连仅RUNNING、attempt1、无artifact、0审计。保存独立CONNECTION_ID、行与全表/文件快照。
4. 仅worker测试Hikari jdbc-url指向127.0.0.1专用透明代理；原datasource URL仍直连做身份核验。代理只连接本片目标23800，按真实握手connectionId精确选择该完成事务COMMIT；原13字节命令与11字节真实OK包以独占新文件保存，普通登录/SQL包不落盘。
5. 服务器OK已收到但仍扣留时，独立直连已见同任务SUCCEEDED/产物/单审计；lose-ack、driver失败和成功返回标记均不存在。只有background_job与operation_event预期变化，原任务及其他表/文件保持。实际文件/审计摘要核同，无重复完成或二次审计。
6. 之后关闭该TCP连接。原driver抛`com.mysql.cj.jdbc.exceptions.CommunicationsException`、SQLState `08S01`，栈含真实driver和EOFException；driver未成功返回。观察器只记录并原样重抛，产品触发halt70，无正常关闭标记，生命周期OS锁由持有转释放。
7. 原lease/index/process逐字节保持，仅新增匹配operation/process的UNKNOWN。六真实HTTP先OUTSTANDING再五次FENCED，拒绝前后所有数据库/文件守恒；孤儿lease/index可退役，UNKNOWN原件不变。旧API正常关闭，另一个fresh产品API实际认证目标后启动exit1/OUTSTANDING、无ready/新process/业务写入；空index不能解除围栏。

## 改动与验证

| 路径/职责 | 实际验证 |
| --- | --- |
| e2averify/E2aSplitWorkerMain、E2aSplitWorkerProcesses | 只新增显式wire测试scope；31个直接调用输入编译exit0 |
| e2averify/E2aSuccessorCrossInstanceWireCommitIT、E2aWireCommitHttp、E2aWireCommitOptions | 新双实例恢复、真实HTTP/worker/提交前后独立直连与精确worker代理接线 |
| recovery/RecoveryWireCommitConfiguration、RecoveryWireCommitDataSource、RecoveryWireCommitBoundary | 原migration池内侧只观察，原driver实际失败原样传递 |
| recovery/RecoveryBoundWireCommitChecks、RecoveryBoundWireCommitProof | 原P5/OS锁/UNKNOWN、实际API与fresh拒绝守恒 |
| d13_wire_packets.py、d13_wire_proxy.py、test_d13_wire_proxy.py | 原包framing、精确连接选择、扣ACK/EOF及非选中原包正常转发，最终6项全过 |
| d13_wire_commit_visibility.py、verify_d13_cross_instance_wire_commit.py | 独立消费原包/原异常/全表/文件/P5/身份/HTTP及实际产品退出 |

Java前缀为`server/src/test/java/com/acrylic/outbound/`；Python前缀为`docs/parallel/R09/tests/`。完整路径/SHA见最终冻结和提交清单，不涉及产品或取消链接线。

| 当次执行 | 结果 |
| --- | --- |
| compile01 / compile02 | 均exit0；自审加强后的最终输入为compile02，无漂移 |
| unit01 | 103/0/0/0，驱动exit0；非服务端全量test |
| adjacent-compile02 | 31个直接调用输入编译exit0；非重跑旧IT |
| proxy-unit01 / proxy-unit02 | 首轮5过1失败，修复本连接显式关流后的Windows10038收尾后6项全过 |
| it01 / 驱动 | 1项失败 / exit1；renderer依赖缺失，未选中COMMIT，不算ACK点通过或失败 |
| it02 / 驱动 | 新1799/1800，1/0/0/0，均exit0；selectedCount=1、errors=[]、threadsAlive=0 |
| consumer01 | exit0，独立原件证明生成 |
| consumer-negative01 | 正常副本接受，13组篡改均拒绝，原件SHA不变；不计新增业务测试数 |
| consumer-after-cleanup | exit0；两输出清单缺件逐项匹配清理审计，完整proof原字节相同 |

主要命令（实际Python绝对路径/argv/环境/脚本SHA/退出码在各command/launch回执）：

```powershell
python -B .local/d13-bound-wire-ack-loss-20261023/run01/compile.py compile02
python -B .local/d13-bound-wire-ack-loss-20261023/run01/launch.py run_unit.py unit01 unit compile02 unit01
python -B .local/d13-bound-wire-ack-loss-20261023/run01/adjacent.py
python -B .local/d13-bound-wire-ack-loss-20261023/run01/launch.py run_product.py it02 compile02 it02
python -B .local/d13-bound-wire-ack-loss-20261023/run01/consume.py it02 compile02 consumer01
python -B .local/d13-bound-wire-ack-loss-20261023/run01/launch.py challenge_consumer.py consumer-negative01
```

仅解释已发生命令，不授权覆盖既有目录或重启已清库。纯文档/断点更新不重跑业务。

## 失败归因和修复原件

代理首轮在已关闭的目标连接上recv触发Windows10038；只对该连接已显式closing的关流后错误容忍，其他错误仍关闭失败，原件在proxy-fix01、proxy-unit01。最终代理6项与实际it02分别通过。

it01第一新导出任务三次renderer失败：`NoClassDefFoundError: com/fasterxml/jackson/databind/Module`。旧renderer目录历史31文件中28个依赖已缺，不归因于未经证明的操作者。保留旧不完整目录及renderer失败日志；按前片清单SHA从现存缓存找到28个精确依赖，在本片新目录重建31文件完整运行时，无下载、无产品修改；runner-origins、before-environment-fix、renderer-recovery.json及外部输入清单记录来源。新运行时保留供后续复用，避免再次依赖不完整旧目录。it01未进入ACK故障点，不能改称业务通过。

续接一次快照误用Windows PowerShell，因缺GetRelativePath失败；部分before-cleanup保留。改用已安装PowerShell7产生完整before-cleanup02，不覆盖旧目录。此为快照工具失败，不是业务失败。

## 原件、清理和保护

原件/清后proof SHA：`6160b36cb3dd12a1b7d148e3c42d0b8b1c7c1f24252010f22f604c798aef7c3d`。真实OK SHA：`27a47e68036c641c149c7485fc65b5e815d57f0652be4c01ed547067c5a71d23`；UNKNOWN SHA：`66621c7f8e52a4eca7b01830fd1503d9a0304dfa08d4030eb057a564bc0c568a`；第三产物SHA：`25ad27ce5d08cd3725698bd82d6961572cbf742d0a0d9de4f307ff85c8ff4708`。源382/目标27输出清单、完整控制lineage/原异常/原包/HTTP/身份/签标/文件/两流日志保留。

仅在认证停机且重新核无监听/消费者后清本片明确可重建数据；先留逐文件SHA、解析绝对路径、输入/依赖/命令/结果、必要恢复SQL原行，失败库另存失败job/审计原行和归因，不清原失败日志。清理回执分别在cleanup01、cleanup-fixtures01、cleanup-failed01。

| 范围 | 逻辑字节 | D卷可用窗口（字节） |
| --- | --- | --- |
| 成功双实例9路径 | 447949497 | 32905093120 → 33361793024 |
| 14个正常/篡改副本 | 26456877 | 33361780736 → 33388515328 |
| 环境失败双实例9路径 | 447880772 | 33387200512 → 33843277824 |

合计逻辑清理922287146字节；卷窗口另列，不能把并发活动/分配差异写成精确净释放。成功库清后消费proof逐字节相同，失败库不复用。原资料/身份/Keystore/签名回执/唯一原证据/最终APK/WIP/继承及生产边界文件不清，共享缓存不清。`localDeletionAllowed=false`保持。

## 接续与未完成

下一有界项先定向核OS拒绝自halt已有工具与实际产品路径，区分未覆盖组合和不具备平台意义的注入，记录退出标准后分配新资源；不重跑本片或旧一次性IT。其余入口/故障、可信生产权威仍需逐项补齐，Android原身份导出/TEMP与独立SKU出口、D16合法资格、D18最终实际候选和其他D/O/U仍依看板及完成标准，不转成现场免验。

明确路径开发备份/推送仅codex/development，实际回执见closeout-submit01；共享混合看板只留WIP、提交后刷新事实。旧D13叙述归档为本报告同目录的ORCHESTRATION_STATUS_BEFORE_BOUND_WIRE_COMMIT_2026-10-24.md，完整旧混合看板原字节/SHA在closeout-docs，Android继承整段原字节保持。总目标active、未完成；不恢复换机承接/多跳/接收者确认激活/旧run03，生产开关、正式签名、原库现网、main、部署/Release关闭。
