# E06实物登记限定验收

## 结论与输入

实物登记WIP的严格非空/default-off与组合守恒缺口已在新副本补验通过；仅实物登记单元收口，人工关联/跨机冲突、E06/O04/U03、D14/D17/D18及总目标仍未完成。系统不因实物记录授关联、库存、占用、记账、放行或删除。合成主管/实物观察及附件明确标注，不称原物理PDA/现场实物验收。生产默认边界和localDeletionAllowed=false保持。

产品输入仍为实物开发备份 `4fead0c7abc1ef77e6ecff70dd5df6e44b98e5d5`；本轮起始HEAD `d5d4c9893d4f56840801b5f959431b37d39abd73`。22项产品源码/契约/迁移原哈希全部匹配，证据 `.local/goal-20261014/physical-product-input-comparison-01.json`。固定JAR及副本JAR均为SHA256 `133df851cde1af5656fed91d8d9d73dd059ed4d0e732dfd0e234bf234cfd578e`。版本数字只见看板生成块。

基址 `A=.local/goal-20261013/e06-physical-audit-1524`，`B=.local/goal-20261014`。核全部工作树/监听未占用后新分配1524；原1514完整dump/hash及原私有文件/固定钥恢复到19332，API仅19436。原1519与预览保留，旧失败副本不复种。执行脚本 `B/e06-physical-audit-1524.py` 与原环境模板的1524派生；SQL诊断/工具及最小设计见[SQL修复](GOAL_E06_SQL_REPAIR_2026-10-14.md)。

## 实际补验

| 验收单元 | 原证据/命令 | 实际结果与边界 |
| --- | --- | --- |
| 非空恢复/迁移 | environment-1524 provision、baseline-jdbc、start-disabled；A/inputs.json/restore.log/facts-baseline-jdbc-1/disabled-jdbc/nonempty-migration.json | 迁移前169表、59非空；同算法迁移后原表仅Flyway变化，新增三张实物表COUNT均零。逐列NULL/HEX、字段数、独立COUNT、排序多重集摘要；不把raw/hex混比 |
| 默认关闭 | disabled，A/disabled-jdbc/default-off.json/result.json | 两写入口实际409 INDEPENDENT_PHYSICAL_DISABLED，三张实物表零写；原包实际下载字节/摘要保持 |
| 同输入正向组合 | start-enabled、enabled；A/enabled-jdbc/successful.json/attachment-original.bin/combination.json/result.json | 原Android完整结束包→主管核原/完整上下文→实际附件/逐包登记→同原键回读→文件字节回读；原包、核原及除checkedAt外完整上下文不变；处理仍拒绝ORIGINAL_NOT_FROZEN。全部业务表守恒，只允许Session/实物/命令审计变化 |
| 权限/容量/异文/并发/回滚 | boundaries，A/boundaries-jdbc/refusals.json/concurrent.json/tail-rollback.json | 双权限完整有权/无权、跨仓、自检、异文、同键并发、坏摘要/空附件/超容量/集合反例；最后幂等SQL触发失败500且检查/附件引用COUNT不变，移除测试触发后原键成功。数据库UPDATE/DELETE不可变拒绝；全链重复未跑 |
| 锁后撤权与边界后守恒 | boundary-resume，A/boundary-resume-jdbc/actual-lock-wait.tsv/lock-stdin.sql/lock-stdout.log/lock-exit.json/conservation.json/result.json | 实际LOCK WAIT后同事务撤权，HTTP401 SESSION_REVOKED；不是sleep猜竞态。全表守恒；权限fixture新增一个合成用户及对应role/仓库，全部原身份行逐值不变见original-actors-conserved.json |
| 工具定向 | B/e06-sql-unit02.log、jdbc-wrong-identity-01 | 15项实际单测退出零；实际JDBC错误预期端口拒绝，inventory未读。JDBC真实消费/CLI失败原件均保留，不称独立人员审查 |

## 复用、自审和失败

L1工具路径→验收读取层→15项定向及实际JDBC/错误身份消费；本轮真实HTTP补验上述单元。产品路径未改，复用实物备份SHA所绑定unit-final01、HTTP06/HTTP03/final02及browser06、Web构建/契约/四消费者；源码22项与JAR哈希核对证明适用。Android/UI/Chrome输入未受影响，复用WIP接续页精确输入绑定证据；未新增其通过。本单元补非空与HTTP组合，不计整个D的L2，不免除最终候选L3必跑。

作者自审检查：凭据只读本机cnf，SQL动态标识严格验证；目标库/端口/datadir实际核对，JDBC只读事务；行COUNT与编码/列数均必需、增删表明确拒绝、业务变化白名单限定。产品权限/签名/事务/原键输入复用原作者审计；不是独立人员审查。CLI前85表与JDBC完全一致，其余表由JDBC完整读取，不称CLI全量成功。

- 1522/1523潜在读取根因及原业务守恒仍未判，失败库与所有原日志/dump/binlog继续保留；1524原CLI空SHOW COLUMNS计第三次、原目录不覆盖。新JDBC证据可独立证明新副本验收，不能清零旧计数。
- boundaries执行后运行器误将DELETE保留提示要求为immutable，原stderr分别是正确UPDATE immutable及DELETE must be retained；归因夹具、输入备份 B/e06-boundaries-failed-input-01.py/hash及失败日志保全。只修精确错误断言，boundary-resume引用已执行边界并补锁/全表守恒，不重复业务种子或吞异常。
- 保全脚本认证shutdown退出零后立刻查进程时短暂仍见退出中的mysqld，原entry日志保留；后核零进程/监听、dump哈希后仅续做压缩。不把该竞态当产品失败、不重复dump覆盖原件。

## 数据保全、磁盘与接续

`B/preserve-1524-e06-physical-01`含完整dump、认证shutdown、身份、原进程/监听及数据文件before/after；dump SHA256 `614a4397ec87cdffad191edd9b1d490416658ec1e079f95ead689899302be226`。用途结束、停机无消费者后仅压缩212个未压缩且至少64KiB的数据文件；339个数据文件长度/hash全保持，无删除。D盘可用变化+205144064字节；收尾C/D余量50744000512/24859205632字节，见原disks-after.json。1494/1519/5174与身份/Keystore/AVD保留。

Git为明确工具/测试/本页/修复页/断点与索引开发备份，回执保存后更新看板；不合main、不发布部署。未跑整套服务/IT/Android/Web构建或最终候选。下一先核现有主管对账/候选/关联模块，记录最小人工关联方案，在具备实物证据的输入上实现并补跨机冲突消费者；E07–E09及全部剩余闸门继续。
