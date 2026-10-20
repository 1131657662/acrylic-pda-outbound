# D13 FORMAL 多块正文：审计尾回滚后的原件保全与重试

## 结论及覆盖界限

输入树D:/acrylic-pda-outbound、codex/development、2d555ceea605bd4f41dc15a619c93e00f79929a6加保全WIP；版本事实见看板生成块。前轮[单块故障补证](GOAL_D13_FORMAL_CHUNK_FAULTS_2026-10-20.md)已通过并收口，本轮不重跑。只读复核发现单块完整正文与chunk同hash，文件不变不能覆盖多块首次合并；因此用既有大正文输入工厂补一个独立IT，不更改产品或凭测试需求引入物理删除。

新一次性隔离输入run01执行E2aSubmissionChunkLargeRollbackIT：1项、0失败、0错误、0跳过，Gradle exit0。13次真实TCP，9块/2140856字节，1102条captures、2有效scan保持。审计尾确定SQL错误500后180表309行类型化快照逐字守恒；14个既有文件逐SHA保持，只新增1份完整原正文。相同ID/原正文重试及重放均200，原签名回执相同，1 payload/2 dispatch/2 ledger、601500整数克，无额外文件。P5独立后继实际STARTED。

这是FORMAL同人同机分块入口的合成L1及独立文件工具消费者，准备为MockMvc、受验为TCP；不是原身份/真实设备、恢复业务、所有文件生命周期或完整D13/D18/第8节。作者、执行与自审同一助手，独立Python不等于独立人员。没有新增业务功能、改权限/契约/迁移或生产配置；localDeletionAllowed=false保持。

## 退出标准与实际证据

源码SubmissionService.accept在SQL事务提交前调用SubmissionFiles.store，后者内容寻址、耐久原子保存；SQL失败不能回滚已完成的文件写入。正确可核的本轮语义是保全原件、业务行不提交、可按原ID恢复；不能在DB回滚时无依据删除可能共享的原hash文件，也不能将磁盘原件视为已成功出库或可交付结果。

本轮先记录该技术选择与最小标准到.local/goal-20261020-resume/d13-multichunk-rollback01/decision.md，再新建测试。没有放宽上一轮断言：本轮独立测试明确核“唯一新增完整原正文”，而不是把单块文件不变结论泛化。

| 步骤 | 实际核对 |
| --- | --- |
| 固定原输入 | 正文2140856字节，9块逆序上传；manifest原ID/摘要/长度固定，原captures1102、有效scan2。合成旧非空基线先迁移再准备，未复用旧失败库。 |
| 受控末SQL故障 | 仅隔离operation_event的SUBMISSION_COMPLETED trigger施加SQLSTATE42000，真实finalize返回500 INTERNAL_ERROR；原P5效果门和围栏照常运行。 |
| 数据库回滚 | SHOW TABLES全部180表/309行值、类型、null、时间、二进制和精确数字逐字守恒；payload/dispatch/ledger均0，原9个chunk引用仍在。 |
| 文件保全 | 原14文件SHA全同，只新增submissions/4f5f94db5b32f77e376efe068fe2fceb88d98472b2e9c7e5ac0087c0194f76ef.json，逐字等于原冻结正文；无receiving临时文件。没有已提交payload引用，不伪报业务成功。 |
| 原请求恢复 | 删除本轮trigger后同ID/正文finalize成功，再次同ID重放返回相同data/原签名；各1 payload、2 dispatch、2 ledger，账务负601500克。文件保持15份，没有重复或覆写原件。 |
| 运行围栏收口 | P5租约index为空、无unknown记录，独立后继stdout STARTED。fixture通用confirmed文案不用于声称另启动worker。 |

独立Python从原HTTP/meta重算请求/响应SHA，核500/200/200序列，读前后全库/文件快照、正文/manifest/持久文件，用本轮原公钥实际RS256验签并关联payload→dispatch→ledger。输入/输出与复用消费者依赖逐SHA绑定。没有使用旧XML、跳过或构建成功替代业务断言；本轮没有新失败。

## 可复现输入与命令

证据根.local/goal-20261020-resume/d13-multichunk-rollback01。input-before.json/status-before.nul/wip-before.zip保全33项起始WIP；execution-plan.json和server-inputs.zip绑定2443项服务端/Gradle输入，运行后逐SHA保持。原看板/进度/历史索引及新报告不存在状态先保存到documents-before/，独立部署预检区原文保护。原31项继承路径、上一批新增的另一聊天诊断报告及postcommit看板分别保留，不混入新业务源码。

命令：powershell -NoProfile -File .local/goal-20261020-resume/d13-multichunk-rollback01/run01/execute.ps1。内部gradlew.bat -p server recoveryControlClasspath integrationTest --tests '*E2aSubmissionChunkLargeRollbackIT' --no-daemon --offline --max-workers=1 --console=plain --stacktrace。只一重型Gradle、本轮一隔离库写者，TEMP/TMP/Java临时目录仅子进程D盘。运行前核19340/19444空闲，连接后核绝对datadir/schema/user/port/binlog；认证shutdown exit0。原系统3306、共享13316、ADB/M71/test不操作；没有Android/APK/Chrome/部署。

| 文件 | SHA-256 |
| --- | --- |
| E2aSubmissionChunkLargeRollbackIT.java | e9a66f70c7d2b9420bc03b915639b877f8b1546b56f827f6a80319b8c8aa59f6 |
| execution-plan.json | d77918dbf4f1e3db4ef28f2eb5ec0b0e75e476920e4b9930f641b6558bcec93a |
| run01/integration-result.xml | 5b202e87e6c82bbb8e0310878c4697cd0e22089d6371abee1f3344b3ec88e9be |
| consumer-result.json | 66377b09ec9173442899a188b6b6f4bbac1ce1723e8280e7df387fd00136f0b0 |
| cleanup01/plan.json | 05b5a8d7f3a83ac29584ec3170805f93d0cb1f5ff729a10ae270acf80f768b08 |

## 清理、Git和后续

用途结束、认证停机、现时无消费者/分配端口无监听后，先保全输入、命令、依赖、结果、完整类型化DB事实、HTTP/签名、P5根、失败资料及MySQL生成身份原字节，逐文件登记SHA；单一PowerShell明确路径删除347文件/232700853逻辑字节（约0.217GiB）可重建合成datadir。D可用32845168640→33082142720，卷差236974080字节另记，不当精准删除量。原冻结正文、9块、原回执/签名、MySQL身份、源码WIP和全部既有APK均保持；开发清理不解锁业务删除。

仅新测试、本报告和三断点/索引明确路径作“开发备份”，不纳入其他聊天诊断或继承取消链。准确提交、fresh远端、全部WIP逐SHA、环境/余量见本根final-manifest.json和environment-closeout.json。未跑新完整普通回归；上一轮定向/全量结果沿其各自输入，不拼接实际最终候选。

下一步只读核D13其他有效文件/导出入口现有覆盖及具体缺口，选择可独立补证项；不重复成功分块IT、CLI/SAF/字号，不重开维护或恢复取消链。全范围D/O/U、可信原事实/正常合法资格、原设备/外置介质、完整UI、恢复/非空升级组合及实际最终候选仍未满足。总目标active、未完成；正式签名、原库/现网、main、部署、Release和生产开关继续关闭。
