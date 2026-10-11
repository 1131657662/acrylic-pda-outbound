# D07/R09 恢复交接限定资格的PREPARE

接续[本代次新资格](D07_R09_RECIPIENT_QUALIFICATION_2026-10-11.md)，原计划D01–D18及第8节保持，整体未完成。版本事实见主看板事实块。输入主树D:/acrylic-pda-outbound、codex/development、7e99b5fea76849ccd3ca88d57ccec3411884ca50；本批精确提交/远端见提交后快照，不循环提交自身SHA。

## 范围与实现

沿HandoverCommandService既有PREPARE、Expected CAS、签名回执、幂等和审计，在原closed/native事务中消费RecoveryTemporaryQualification。先锁并实时核验原签名指定主管、登记人、接收人/仓库/设备、原机禁用及会话撤销；核库外pin、持久资格、基线、材料与有效窗，提交前再验。普通HTTP仍受RECOVERY_PENDING/NORMAL门禁，不新增公开写入口。

新PREPARE显式使用targetQualificationId，准备签名保存source正文/代次与target传输代次的限定描述。恢复资格不冒充temporary_authorization，不创建旧同代次activation ticket。HandoverPreparation普通路径沿原资格/票据逻辑，恢复路径由封闭验证类型显式选择，原字段和来源映射保持。

全原表比较仅接受精确签名效果：handover_case版本推进、指定接收者、准备摘要和时间，以及PREPARE回执、对应幂等、审计各一行。比较副本逐列验签后逆投影，再接既有准入、实物、运维、双侧及原全表守恒。digest-only表先读取实际全库raw并核columns/count/hash，精确移除该行并重算摘要；不排除整表。beforeCommit保留实际raw、材料及原资料重验。

## 当次证据

- 1304全新隔离目标只读复用原1233 dump/binlog/pin，实际MySQL专项1测试、0失败/错误/跳过、exit0/sourceUnchanged。成功准备、原键重放/并发、有效异文IDEMPOTENCY_CONTENT_MISMATCH，错接收者/仓库/案件/资格/代次/旧字段、登记人越权均有拒绝。最后审计SQL、跨仓原行/准备摘要/接收身份/有效窗/签名材料尾变均回滚；原业务行/文件/迁移历史保持。
- 有效窗尾测只推进同一事务连接的MySQL session timestamp，afterCompletion恢复；未修改全局时钟。签名材料尾测只改本次合成资格文件，finally恢复原字节。
- 新JVM1852与父19944不同；重载原pin、原PREPARE字节，无新签名或登记，返回原回执逐byte相等且数据库不变。命令、stdout/stderr、PID/exit和结果原件保留。
- 独立Node消费者14输入不变，150表精确核对、8篡改反例通过；RSA/P-256、原1233固定hash、资格/正文、逐列/时间与子JVM结果分层消费。工具独立不代独立人员或现场。
- 最终1306在相同产品输入补两个调用竞争首次PREPARE，至少一个提交；所有已提交响应逐byte一致、原键重放一致，原表精确只增一组效果。专项1/0/0/0、exit0/sourceUnchanged；子JVM36844与父36488不同，DB不变/无新签名/原回执一致。消费者02为15输入/150表/8反例通过，补消费首次并发回执；1304为补首次并发前有效中间输入。
- 受影响普通recovery/handover回归3317测试、116XML、无失败/错误/跳过、exit0/sourceUnchanged。专项与回归各2714输入运行期间无漂移；1304/普通回归后仅RecoveryReceivedPreparationChecks专项文件改变，最终1306无后续源码漂移，产品/普通测试输入一致。收口核验初次遗漏1304该预期测试变化而拒绝，原JSON保留，修正输入关系后无未解释漂移；见received-prepare-close-verification(-initial).json。

专项报告.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1304/；库/原回执/快照/重放位于.local/e2a-cross-20260925-1304/；保全回执.local/d01-d18-20261010/temporary-evidence-restore-mysql-1304/。dump成功、认证shutdown=0、PID退出、监听空，不复用保全库。消费者为.local/d01-d18-20261010/received-prepare-consumer-01/，设计/作者自审为received-prepare-design.md及received-prepare-self-review.md。

最终目录同前替换为1306/consumer-02；普通回归.local/d01-d18-20261008/received-prepare-regression-01/。1305因新增并发记录调用不存在的无参数array编译失败、0测试，修正后1306通过；失败库/报告/dump/binlog与认证停机回执保全，不复用。1306同样dump成功/认证shutdown=0/PID退出/监听空。

## 失败、保护与限制

compile-01名称遮蔽、compile-02测试引用包私有类型已修且原输出保留。1299仅非事务operation_event AUTO_INCREMENT快照差异；只规范该DDL字段，原快照保留。1301误按完整行解码digest-only表已修，未删除原比较；1299/1301失败库/XML/log/dump/binlog保全。1300/1303因跨树目录撞号被分配守卫拒绝，未建库。1302是补反例/新JVM前中间通过，不代1304完整输入。

接续received-prepare-resume-01保存完整dirty/hash/副本/diff、Git/远端/写者/进程/监听/磁盘；60项接续比较、69历史/22STOP无漂移。继承29非看板路径保持，不混入本批提交。生产默认/签名及七类生产决定能力关闭，localDeletionAllowed=false；1293 UNKNOWN保留，原1233只读，不重跑1150/1151/UI-06或1100–1102。新产物及子进程TEMP/Java/cache在D盘，无清理，私有材料未异地备份。

本项只验证服务层PREPARE。普通ACTIVATE受恢复门禁拒绝、PREPARE接缝拒绝ACTIVATE；不把此项当作旧票据激活消费者专项。无正向HTTP/Android下载确认、激活或最终提交，也无APK/模拟器/真机/正式签名/部署。下一步继续指定接收者下载确认→新资格激活→最终提交，旧正文/票据不可跨代次激活；随后D08–D18及全部总闸门。合成主管、运维签名和接收密钥不代原身份、现场实物或设备证明。
