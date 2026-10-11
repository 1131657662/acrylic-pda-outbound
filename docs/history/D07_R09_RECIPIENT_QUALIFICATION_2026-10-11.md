# D07/R09 本代次恢复交接新资格

本项接续指定接收者准入，新增限定scope/case/接收人的target代次资格。原正文、source代次、原来源映射保留，旧票据不可激活；不计D06/D07或总目标完成。版本事实只见主看板生成块。

输入为主树D:/acrylic-pda-outbound、codex/development、098dd7fa0a9f5806fbce7e7a730f5d86cbfdaf11，实际开发远端一致、main未动。全部dirty副本/hash/diff/Git/环境见.local/d01-d18-20261010/received-qualification-resume-running-01；29条继承非看板dirty、69项历史比较/22原STOP无漂移。前次摘要遗漏的在途普通回归已接住并实际完成，没有重跑已验范围协议或原来源。

## 实现与边界

独立pin/RS256签名的RECOVERED_TEMP_HANDOVER_QUALIFICATION绑定原准入bytes摘要、双侧COMMIT、完整控制、原来源/正文/source代次、target传输代次、指定接收者身份/设备版本/公钥及基线/时窗。能力仅PREPARE/DOWNLOAD/CONFIRM；不创建通用temporary_authorization，不给首轮CREATE/SCAN/SUBMIT。原机保持禁用，新机普通TEMP=false，服务RECOVERY_PENDING，ordinaryWritesAllowed/firstRoundAllowed/mayResumeScanning/localDeletionAllowed均false。

沿原closed archive/native/lease/journal及IdentityLocks在同DataSource事务签发；完整原表、成员、物理/运维/双侧与beforeCommit核验保留。仅新增精确原签名字节审计，整表逐列核对并拒绝未知额外行；基线/身份/材料/审计在提交前再验。reopen只重载原pin/bytes且要求原审计存在，不能缺行再造。幂等返回原资格不表示过期资格可用于后续操作，接收准备仍须实时验有效窗。

设计与作者自审在.local/d01-d18-20261010/received-qualification-design.md及received-qualification-self-review.md。只有单助手作者，独立工具消费者不等于独立人员。

## 实际验证与保全

- 新1298只读复用原1233 dump/binlog重建，非空迁移全原表/文件守恒。既有主管/范围/准入仅作本实例依赖准备，不以此重复声称旧协议验收。
- E2aReceivedQualificationIT专项1测试、0失败/错误/跳过、exit0/sourceUnchanged：签发、原字节重放、两线程、异有效签名/错purpose/scope/准入/COMMIT/案件/能力/source代次/原摘要/基线/时窗/缺审计拒绝、末SQL及跨仓原行/身份/签名材料尾写全回滚、审计UPDATE/DELETE拒绝。
- 实际新JVM仅重载原pin/bytes，无新签名、DB不变、PID不同、exit0；为提交后丢响应/重启重放证据，没有新增halt切点。原件在.local/e2a-cross-20260925-1298/qualification-*。
- 独立Node消费者received-qualification-consumer-01：18输入hash不变、150表精确核验、独立运维RS256/接收P-256/双侧COMMIT/原来源pin、5项反例及新JVM/retention通过，exit0。原命令为node .local/d01-d18-20261010/received-qualification-consumer.cjs 1298 received-qualification-consumer-01。
- 受影响普通recovery/handover回归3317测试、116XML、0失败/错误/跳过、exit0/sourceUnchanged；非全server或全部IT。日志/XML/命令及完整输入见.local/d01-d18-20261008/received-qualification-regression-01/；专项见同根r09-temporary-evidence-restore-mysql-1298/。
- compile-01因测试缺import失败保留，compile-02修正通过；没有删除失败原件。1298 dump成功、认证shutdown=0、原PID退出、任务监听空，见.local/d01-d18-20261010/temporary-evidence-restore-mysql-1298/retention.json。

没有新增正向HTTP/Android组合、APK/模拟器/真机。合成SessionUser、独立运维签名及新接收钥是开发条件，不代原身份或现场。1293持久UNKNOWN、全部保全/失败库及原件保留不复用；原1233只读，1150/1151/UI-06/1100–1102未重跑。产物/TEMP/Java/cache在D盘，无清理；私有材料未声称异地备份。

明确路径提交及推送回执见.local/d01-d18-20261010/received-qualification-commit-paths.json和received-qualification-push-receipt.txt，全部继承保护与提交后Git/环境见received-qualification-postcommit-01。源码备份不代表本机私有证据已备份。

## 接续

下一项为显式消费新资格的接收准备/下载确认，再接激活与最终提交。仍须原表精确签名效果/守恒、target与source代次分离、首轮隔离、closed journal/身份锁序、实际MySQL/HTTP/Android组合及末SQL/重启/并发/独立消费。不得把旧通用票据改成可跨代次激活，不开NORMAL、不把资格结果变公共写许可。

D06/D07其他缺口、D08–D18及第8节全部闸门仍未完成；D04/D05未定政策、原资料/旧BCD/526和历史失败不移入现场。生产默认/签名、七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署、不正式签名，C01–C08保留。
