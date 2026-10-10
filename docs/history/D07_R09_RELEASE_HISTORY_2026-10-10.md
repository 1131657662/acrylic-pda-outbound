# D07/R09 释放前原历史覆盖核验

这是原完整目标中范围释放的一个前置，不能代替设备/案件/调整/库存/FH语义闭合、主管运维释放或最终交接。版本事实只见主看板生成事实块。

## 输入与实现

主树D:/acrylic-pda-outbound，codex/development，产品输入HEAD为219121f0c3d46402086fb395bea3c923b482bc8f及本批8条产品/测试差异；全部dirty与输入hash见本机received-history-close-preflight-01和各运行source-before/after。唯一写者为本聊天；继承29路径不混入提交。

RecoveryReleaseHistoryCoverage重读closed authority与原归档、native所需原材料，绑定不可变清单的控制revision/hash/epoch、原manifest/rows/payload、设备/用户/仓库/原代次；原受约束账户和完整ROW日志独立pin验证后，要求控制possibleLossFrom/Through完全落在可信窗口内。输出只读不可变事实及摘要，释放、普通写和删除能力均false。

原1233日志真实窗口为2026-10-10T08:02:16.567648Z至08:02:43.509898Z，binlog位置414246至492663。此前合成控制的占位日期不覆盖它，不能拿旧占位窗口作为业务闭合。本次另建1263目标和明确绑定真实窗口的合成运维权威，没有改旧控制、原日志或原签名；不把合成运维身份当原端事实。

## 实际验证

- `.local/d01-d18-20261008/received-history-unit-01`：6/0/0/0、exit0/sourceUnchanged；精确覆盖通过，前漏、后漏、两侧漏、旧占位窗口及未受控历史拒绝。
- `.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1263`：新目标真实MySQL+应用专项1/0/0/0、exit0/sourceUnchanged。原1233扫码前dump/binlog全量重建后，原归档/native→主管围栏→清单→实际历史覆盖；错控制/错source rows拒绝，核验前后全库/文件相等。成功接缝是合成SessionUser的服务调用，没有新增正向HTTP/Android最终链。
- `.local/d01-d18-20261010/received-history-inventory-consumer-01`：独立Node清单消费者通过；`received-history-consumer-01`：exit0、6输入hash不变，独立重算canonical hash、原begin/end与log前缀、原库/时间窗及清单控制绑定。独立工具不代独立人员或现场。
- `.local/d01-d18-20261008/received-history-regression-01`：recovery/handover普通测试3280全过、111XML、无skip、exit0/sourceUnchanged；覆盖本批测试夹具变化，非完整server/IT。
- `.local/d01-d18-20261010/temporary-evidence-restore-mysql-1263`：dump重算一致、认证shutdown0、原进程退出、19330/19434监听空；原DB/log/XML/dump保留。1263无失败尝试；原1260等失败及Node首败仍见前记录，不删除或复用。

## 自审、断点与后续

自审确认覆盖结果不能自行构造、保留只读数据副本；旧清单或控制绑定失败关闭，底层原始权限/签名/摘要校验复用已验消费者。覆盖结果本身不是活数据库租约，最终范围判断和释放事务仍必须重读并锁定清单及两侧证明，不能仅持有该对象就放行。

close-preflight-01保全37条dirty副本/hash/binary diff、精确Git/远端、进程/监听及C/D余量；69历史比较、22原STOP保护无漂移。新证据与TEMP/Java/cache均在D盘，无清理。范围清单18路径已推219121f0c3d46402086fb395bea3c923b482bc8f，实际远端一致；本批明确提交/推送回执及剩余继承核对见received-history-precommit-01、postcommit-01。不循环提交看板自身HEAD刷新。

下一步读取原全库快照及连续日志，严格限定合法围栏差异，固定设备/来源/案件/调整/库存/审计与全局编号高水位；未知范围继续隔离。然后按库外PREPARE→DB排他锁提交→库外COMMIT→实例核双方证明，再接本代次新资格与接收最终提交。D01–D18及第8节继续，D04/D05政策阻断与C01–C08原限制不变。1233只读复用，1150/1151/UI-06和1100–1102未重跑；生产默认/签名/七类决定关闭、localDeletionAllowed=false，不接原库/现网、不合main、不部署发布。
