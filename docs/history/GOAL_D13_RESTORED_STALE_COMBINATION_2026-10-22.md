# D13完整恢复后独立API/worker stale拒绝组合（2026-10-22）

## 输入与范围

输入提交00f0acd2aeb583d750991c780c6301bcde3764c0，固定树D:/acrylic-pda-outbound。只增加隔离测试、测试进程握手、封闭runner选项及独立Python原件消费者；产品源码、配置、契约、迁移、Android和Web无改动。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。

证据根：.local/goal-20261022-resume/d13-restored-stale01/。baseline.json及inherited-wip.zip保存40条继承路径，edited-originals.zip保存改前测试与看板；acceptance-design.md先定标准。前片按[恢复渲染失败与后继重试报告](GOAL_D13_RESTORED_RENDERER_RETRY_COMBINATION_2026-10-22.md)复用，不重跑。

## 实际执行

非空历史种子迁移→合成TEMP经真实HTTP生成原PENDING双格式任务→完整SQL/storage/合成验签材料恢复→独立API/非Web生产定时worker正常完成→排空本片worker调度器但保持JVM→HTTP第三PENDING任务→独立Python推进权威→旧HTTP/worker显式拒绝→全事实守恒→正常停机→独立消费者读取原件。

| 执行 | 隔离输入 | 实际结果 | 结论 |
| --- | --- | --- | --- |
| execution01/run1646 | DB23646、HTTP24646、build .local/s22h | 1测试/1失败/0错误/0跳过，69.733秒，exit1 | 最终全表快照socket耗尽，整轮失败，不计通过 |
| execution02/run1647 | DB23647、HTTP24647、build .local/s22i | 1测试/0失败/0错误/0跳过，59.591秒，exit0且completed=true | 本有界隔离组合通过，独立Python消费者exit0 |

两轮库根分别为.local/e2a-cross-20260925-1646/与1647/。精确命令、参数、输出、退出码见execution01-launch.json、execution02-launch.json、consumer02-command.json及各run命令记录、原XML；不复制本机凭据。

首轮六HTTP及worker拒绝已发生，但最后CannotGetJdbcConnectionException / CommunicationsException / BindException: Address already in use: getsockopt使整轮失败。快照夹具逐查询新建TCP造成Windows本地socket耗尽。失败XML SHA-256：6923a0904b9660e6479a882485b0ff6f9a3d7dc2d1a93a9d4d59334da5bceb29。原日志、HTTP、库、dump/binlog保留，见execution01-failure-preservation.json；同根因失败计数1。第二轮仅本IT复用已有小连接池（source/target各最多2连接），不减守恒断言，不改产品或系统网络，不复用旧库；依据见run02-design-amendment.md。

## 原件消费者与守恒

independent-consumer02.json读取实际HTTP正文/metadata、完整SQL/DDL/迁移快照、文件、进程及权威历史；输出SHA-256：ca8ded213a7aa84a8a7f25ed85145e1e4b0d6db588b2d3bc140f07d1c9b184ec。

- 恢复后先核179表；正常消费仅background_job、operation_event、spring_session、spring_session_attributes变化。stale前后完整快照逐字相同，含179表、DDL、迁移、15文件；源完整快照、原8文件及源恢复合成验签材料保持。
- 原双任务各一次成功，真实HTTP下载、原键、异文、跨仓拒绝核对。XLSX7表/2明细/0公式/原顺序/整数克，SHA-256：cd7aa320f934b05c4955bade281f049a55cbf9170f9ae5d44848a3c8d96a5ef3。
- PDF2页/2明细/0注解/原顺序，SHA-256：b1faf5ff0b1e4fffa95b4da319f1ae301e2c072c8d4d937acd29683d79985f21。两页Poppler实际渲染并目视无裁切/重叠/缺字；原/有效2包601.5kg，明确合成样例。见pdf-visual02/render.json及review.json。
- HTTP三阶段分别7/16/11条，最后六条精确503 CONTROL_INSTANCE_STALE；第三任务仍PENDING、attempt0、无artifact/lease。
- 独立API/worker实际不同PID（运行原件18652/14596）；worker排空后JVM保持并显式调用生产poll及enqueueReviewReminderSweep，两处拒绝日志、直接admission准确码、liveRoots=0和正常关闭标记齐全；不是只停调度器记通过。
- 独立Python控制权威实际推进revision2、ordinaryWritesAllowed=false，全部位于本run private/stale-authority/。本片非P5，不计P5 stale通过。
- 两轮离线binlog消费者exit0，source/restore Table_map为374/409与374/408；恢复切点后source Table_map及全限定源行引用均0，见binlog-consumer02.json。未重启库补跑。

源码2629文件、外部输入275文件执行前后逐SHA保持，见execution02-source.json、external-after.json、source-review02.json。8代码/runner/消费者安全审查唯一命中是HEAD原有环境变量选项拼接，无硬编码秘密，未决0；文档与暂存集合在closeout另核。

## 环境与空间

两轮MySQL均认证shutdown exit0，专属监听/Java无残留（以closeout fresh实查为准，不按旧PID推定）。系统3306/33060只观察、未SQL连接，内部身份未认证；ADB只读枚举、Debug Keystore只读SHA，未操作现场设备或正式签名。

仅对已结束自有.local/s22h、.local/s22i及两run datadir无损NTFS压缩，8199文件长度/SHA全保持，减少分配451542841字节，删除0字节。D卷观测36295237632→36799541248字节；卷净变与分配减少分列，不累计前片。命令、输入、依赖锁及结果见compact-plan.json、compact-result.json、dependency-inputs.json和进程/监听快照。未换通道重试被拦截删除，失败及一次性原件保持。

## 未验与边界

仅本次合成、非P5独立权威的有界恢复后stale组合通过；不代表D13–D15整体、P5 stale/UNKNOWN/crash/OS拒绝kill、错根/错库/水位全部入口、Android/Chrome/原模拟器/现场或D18最终候选通过。D16正常有效guard/复杂引用、第8节仍未完成，不拼历史局部通过。

继承WIP保持，历史索引只暂存本片入口。开发备份、推送、文档、压缩不计验收；总目标active、未完成。生产开关/TEMP归档、正式签名、原库现网、main、部署/Release关闭，localDeletionAllowed=false。换机承接/多跳/接收者确认激活/旧run03不恢复。

下一步从未验矩阵选有界独立项，先定影响和退出标准；不重跑1647/1646/1645/1644/旧run03。实际提交/远端回执、继承快照与两轮所有原件清单见本片closeout/。
