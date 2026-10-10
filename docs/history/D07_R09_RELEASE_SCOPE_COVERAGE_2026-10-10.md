# D07/R09 释放前固定仓库及子表覆盖

继续原目标D01–D18和第8节；本步完善实际范围清单，不计业务语义闭合、可信释放或新资格完成。版本事实只见看板生成块。

## 输入与范围

主树D:/acrylic-pda-outbound，codex/development；输入HEAD与实际开发远端cad83c8a7b4ddfa9b4cc772ac7b307d603a8ceaa及本批六条产品/专项测试差异。received-scope-close-preflight-01保全35条dirty，前received-number-postcommit-01的29条继承路径逐hash不变，69历史比较/22原STOP保护无漂移。

RecoveryReleaseAdditionalQueries将准备分片/ACK、库存导入与基线、PC作业及子链、同步投影/待发布、来源证据及子表纳入同一仓库清单。子表使用实际父键归属，固定表名和参数化仓库，不接受调用者任意ID或SQL；包含空集合，重复声明拒绝。共130表；全局审计、防重、产品、配对材料只暴露摘要，device_challenge.nonce遮盖。既有容量上限、事务行锁与提交前完整重读保持。

## 实际验证与保全

- 1267新隔离专项失败：测试把原inventory_import_row数量误写为1，原资料实际为2。只修正测试期望，未改查询或原资料；失败XML/日志/datadir/dump/binlog保留，认证停机0、原PID退出、监听空，不复用。
- 1268跨33树分配全新目标，真实MySQL恢复原1233 dump/binlog后运行应用专项1/0/0/0、exit0/sourceUnchanged；原非空准备/库存/提交子表及空PC/交接家族存在，凭据不暴露。counter、其他仓库号码和库存导入子表的事务尾写均拒绝并全库/文件回滚。
- received-scope-consumer-01：独立Node从完整数据库快照及父子关系推导成员集合，不导入产品SQL；130表声明及所有逐列/逐行/摘要一致，遗漏子行、混入其他仓库和漏空表三类反例拒绝。exit0，10输入hash不变。
- received-scope-number-consumer-01：原生mysqlbinlog独立逐笔分配与两端/目标全仓库映射一致，exit0，8输入hash不变。一次外层查看命令在消费者成功后读取不存在的failure.txt返回1，不是消费者失败；原result.json及终端记录保留。
- received-scope-regression-01：recovery/handover普通测试3301全过、113XML、无skip、exit0/sourceUnchanged；非完整server/IT。1268专项原报告在.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1268，普通报告在同层received-scope-regression-01。
- .local/d01-d18-20261010/temporary-evidence-restore-mysql-1268：dump重算一致、认证shutdown0、原PID退出、19330/19434空。数据库保留于.local/e2a-cross-20260925-1268。独立消费者、设计及自审在.local/d01-d18-20261010。

作者自审核固定父键/参数、跨仓库隔离、空集合、摘要/nonce、容量拒绝及完整尾验；独立工具不代表独立人员。close-preflight-01完整保存dirty副本/hash/diff、Git/写者/进程/监听/C/D磁盘，所有新产物在D盘，无清理。提交前后回执见received-scope-precommit-01及postcommit-01。

## 接续及限制

清单扩展没有证明设备/来源/案件/调整/库存/审计业务语义安全。下一步沿原端独立固定终点和签名围栏核对原事实，仅允许REQUEST/FAULT_FENCE的精确合法效果，ABORT/占用及未知范围继续隔离；不能笼统允许全局审计摘要变化。继而补库外PREPARE→DB排他revision/epoch/范围提交→库外COMMIT→实例两侧核验、本代次新资格、接收准备/激活与最终提交。

专项成功链为合成身份的真实应用服务调用，无新增HTTP/Android正向或APK/模拟器/真机/正式签名。原1233只读复用，1150/1151/UI-06和1100–1102未重跑；局部通过不外推完整D项。D04/D05未定政策、原资料/旧BCD/526及C01–C08继续单列。生产默认、签名及七类决定关闭，localDeletionAllowed=false；不接原库/现网、不合main、不部署/发布。
