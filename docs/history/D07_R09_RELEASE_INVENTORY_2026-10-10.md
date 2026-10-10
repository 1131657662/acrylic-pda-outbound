# D07/R09 主管围栏后的实际范围清单

本批仅完成可信释放的只读清单前置；不是范围闭包、恢复释放、新资格或最终交接。完整目标仍为[原计划](../AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)D01–D18及第8节，继续执行。版本事实只读看板生成块。

## 输入、实现与退出标准

主树D:/acrylic-pda-outbound，codex/development，接手HEAD及实际开发远端均aef39dc0fdd9734ee7b57d556fe9f5f5e5bf7604。received-release-resume-01逐hash验证前received-fault-pause-final-01的30条继承dirty及副本；69历史比较/22原STOP无漂移。本聊天唯一写者，前聊天idle，其他项目聊天notLoaded。设计见.local/d01-d18-20261010/received-release-design.md。

- HandoverRecoveredTemporaryReview在既有closed事务、原归档/native、同DataSource、原FAULT_FENCE命令和签名回执上核对在线主管、目标代次、设备禁用与session撤销。
- RecoveryReleaseInventoryCapture锁定实际数据库控制身份，读取固定表的仓库内设备、来源、案件、调整、库存、账务、审计，以及全局编号和任务摘要；空集合、列名、原SQL字节及行数均保留。摘要排序确定，输入/输出防变异。
- signed_token及session散列再次SHA256后返回；跨仓任务/恢复包只给全局计数和摘要。固定查询与仓库参数分离，未知表拒绝。
- 行数/字节超限明确拒绝；提交前重读控制及清单。同事务尾写使清单失效并回滚。返回明确缺口，releaseAllowed/ordinaryWritesAllowed/localDeletionAllowed均false；没有新路由、迁移、普通准入或签名开关。

## 当次验证与失败保全

| 批次 | 实际结果及范围 |
| --- | --- |
| received-release-unit-01 | 3/0/0/0、exit0/sourceUnchanged；不可变/变化失效/无释放能力 |
| 1253 | 中间MySQL清单专项1/0/0/0；早于仓库过滤与凭据遮盖收敛，不代最终 |
| 1260 | 1/1/0/0；反例夹具尝试重启故障设备，被既有触发器拒绝。修断言，不移除触发器；失败库/dump/log原件保留，认证停机，不复用 |
| 1261 | 最终产品输入MySQL专项1/0/0/0、exit0/sourceUnchanged；跨仓/凭据/错case/epoch/权限/异DataSource/外层事务/撤销session/旧清单失效及全库守恒 |
| received-release-regression-01 | 普通recovery/handover3272/0/0/0、109XML、exit0/sourceUnchanged；不是全server或IT |
| received-release-limits-01 | 补充2条容量反例及既有3条，共5/0/0/0、exit0/sourceUnchanged；只mock JDBC输入边界，不称真实MySQL容量试验 |
| 1262 | 补充提交前尾写反例后的专项1/0/0/0、exit0/sourceUnchanged；原计数器增量使提交失败，全库回滚守恒。产品输入与1261/普通回归一致；仅补专项测试，无须机械重跑普通回归 |
| Node02 / Node03 | 独立工具分别读取1261/1262：exit0、9输入hash不变；RSA原请求/围栏回执、清单摘要、逐原行/凭据遮盖、跨仓边界、全库三快照守恒及停机回执 |

Node01失败是消费者把解析后的无signedReceipt载荷与原签名封套直接比较；修为比较无签名载荷并独立验证原回执RSA及表内原字节，失败保留。独立工具不是独立人员。

1252/1254/1255被跨树随机目录子串守卫拒绝，未建库，拒绝记录保留。1253/1260/1261/1262均使用新编号；1262跨33树分配，dump重算一致、认证停机exit0、原进程退出、19330/19434监听空。原1233只读复用；1150/1151/UI-06及1100–1102未重跑。

## 原件位置与适用限制

- Gradle/XML/输入hash/命令/result：.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1262/及received-release-*。
- 分配、运行器、dump/停机：.local/d01-d18-20261010/temporary-evidence-restore-mysql-1262/；实际datadir/binlog/dump和recovered-fault清单/原回执/三快照：.local/e2a-cross-20260925-1262/。
- 独立工具：.local/d01-d18-20261010/received-release-consumer-01/02/03；脚本received-release-consumer.cjs。全量dirty/hash/diff/进程/监听/磁盘见received-release-close-preflight-01与后续precommit/postcommit/final。
- 正向为合成主管SessionUser+真实MySQL服务接缝；本批没有新正向HTTP/Android/APK/模拟器/真机、正式签名或部署。原1233或1250的HTTP证据不能外推为本清单HTTP成功。

## 自审与下一步

核对固定表参数、跨仓过滤/全局编号、凭据遮盖、同事务控制锁、尾重读、原回执验签及触发器保留。清单不能证明原截点外没有其他事实；原权限日志的实际时间窗与历史测试控制占位窗不同，后续释放必须校验覆盖关系，不能把占位时间或单源清单当闭包。

继续可信设备/来源/案件/调整/库存/审计/全局FH历史闭包、主管与运维确认、库外PREPARE→DB排他提交→库外COMMIT→实例核两侧，再接本代次新资格/准备/激活/最终提交。未知范围保持隔离，Normal登记不能代替业务释放。其余D项、政策阻断及现场清单按原目标保持。

生产默认/签名及七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署。新产物在D盘，无清理；本机证据可取回不等于异地备份。
