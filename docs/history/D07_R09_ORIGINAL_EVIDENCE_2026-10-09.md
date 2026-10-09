# D07/R09 原TEMP设备E2A原件核验前置

输入树D:/acrylic-pda-outbound、codex/development、f867a8b1b8c01a4ef36ed5c4ac8e3a56f334ce3b与本批六条Java。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。original-evidence-design.md、original-evidence-self-review.md、original-evidence-inprogress-10在`.local/d01-d18-20261009/`，记录设计、作者自审及全部32dirty副本/hash/diff/进程/监听/磁盘；22继承逐hash一致。原FORMAL final和磁盘final全部22路径当次逐hash一致、原HEAD为实际HEAD祖先，无恢复/重置，单写者，无独立人员审查。

## 实现与退出标准

`RecoveryOriginalEvidence`只读接原OPEN/PAGE/STOP、所有原对象字节、显式预期身份及历史设备公钥；复用原E2A解析器/P256域验签，不能从bundle自行取根。页链、manifest页序/总量、跨页记录/object ID唯一、原对象canonical/hash/length及集合完整逐条重验，输入防御拷贝。无Spring bean/HTTP/DB/DML/签发/客户端或迁移变化，返回摘要与计数，普通写权和删除权均false。

OPEN sourceId是原authorizationId，SCAN originalDeviceSeq是原SAVED localSeq，不能拿来源orderScanSeq替代。deviceCapture、来源输入和audit从1连续；失败输入占用来源序号，实际scan可稀疏，按原capture/state/保存审计/order/cycle/barcode匹配，VOID/失败原因由既有迁移验证器重验。原冻结正文末项FROZEN、captureId=null；原同事务随后STOP是独立下一审计边界，与原stopEventId和签名水位匹配。未来STOP事务版本拒绝。

原1151/产品TemporaryFreeze证实正文只有events，无auditEvents：修正原新代码误要求双数组的差异，夹具同步为native形状；auditEvents/auditCoverage/auditArchives或混合前缀字段、ARCHIVE、高轮/跨来源明确拒绝。`RecoveryInitialRecords`仅新增直接正文重载，原入口仍调用相同校验，无改造原正文或补签STOP。

仅第一轮、同一TEMP、完整未归档设备审计。该接口尚不证明历史设备登记根取得/保管、独立pin、权限/实物占用/原提交终态，也不提供恢复写入。既有InitialSource依赖auditCoverage/auditEvents，仍不能直接消费native正文；下一子项须同一独立pin绑定原E2A再重验相同原准备/ACK/终态/权限/占用，不能追加字段冒充原件。

## 当次验证

定向01为66/0/0/0（旧夹具）；修原正文形状及稀疏scan、FROZEN/未知字段/页链/额外对象反例后02为78全过；自审补STOP未来版本/错事件、冻结漏capture与device缺口后最终03为82/0/0/0、4XML、exit0/sourceUnchanged。全部命令/log/XML/source前后hash/result在`.local/d01-d18-20261008/r09-original-evidence-targeted-*`。合成身份和内存开发EC签名不代原身份/现场。

受影响Recovery/Draft/Evidence/Preparation普通回归04为3110/0/0/1、126XML、exit0/sourceUnchanged，入口`.local/d01-d18-20261008/r09-original-evidence-regression-04/`。普通test不含IT；Windows符号链接测试skip未执行，不计通过。本段没有新DB/HTTP/Android/Web/模拟器/APK/真机/部署证据。

新保留原件只读消费者读取1151的formal-migration-before.json指定收存行与device公钥、原payload、11个原对象，未接库/加载复制私钥/签发。消费者01 compile0/run1（误用DER摘要检查登记文本摘要），原日志/result保留；按产品DeviceCrypto和原STOP不同摘要域修正后02及最终03 compile/run均0。1页11项、device输入1/来源输入1/STOP审计8、冻结正文7项，与原payload摘要一致；13份输入前后hash一致，report/result/source hash/原命令见`.local/d01-d18-20261009/retained-e2a-consumer-1151-03/`。历史公钥/expected由同一保留快照提供，不能声称独立登记根/pin取得、原设备身份、权限/占用/终态或实际重建通过。

## Git、环境与接续

开发提交只明确暂存六条Java、本页、历史索引、验收索引和看板；原件/快照/私有材料/数据库不上传。提交前完整快照与暂存/commit/push回执见`.local/d01-d18-20261009/original-evidence-precommit-11/`，提交后实际HEAD/远端与完整dirty/环境见`original-evidence-postcommit-12`，继承路径不混入。Git备份不代表本机原件异地可取回。

无重型测试在途，既有3306/13316/ADB不操作，精确C/D余量在回归04/disk.json与提交快照disks.json。新增日志/TEMP/Java/cache/消费者均D盘，无清理。1150/1151/UI-06和1100–1102不重跑，失败库/原件/日志/XML/datadir/dump/binlog均保留不复用。

下一批：同一pin绑定原E2A与native冻结正文、历史登记公钥及原准备/ACK/终态/权限/占用，再落实故障前采集点和独立pin保管。全新隔离实际截点恢复→原ID/正文/序号/回执闭事务重建→D07主管围栏/接收激活/最终提交、尾SQL回滚/重启仍必做，原E2A/原recall包不能被通过Proof替代。

D06/D07/R09、D08–D18及总目标active、未完成；续期撤销/高轮/混合/跨来源/成功取消更正、D04/D05政策缺口、1151原FORMAL准备缺失、原资料/旧BCD/526、历史失败/恢复限制和C01–C08保持。生产默认/签名和七类生产决定能力关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
