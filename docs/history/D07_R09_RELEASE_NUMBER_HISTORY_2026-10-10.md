# D07/R09 原全局编号历史核对

沿原完整目标继续范围释放前置，不把本子项或提交当D07/D13/D14及总目标完成。版本事实只见主看板生成块。

## 输入与实现

主树D:/acrylic-pda-outbound，codex/development；产品输入62378f8e2ddba8fe08e1b48a04c6279134ea9869及本批明确差异。此前历史覆盖13路径已提交推送该SHA，实际开发远端一致，main未动；received-history-postcommit-01确认剩余29条继承dirty与前快照逐hash未变。

RecoveryReleaseNumberHistory从独立保管的原1233扫码前/结束全库快照和同一受约束权限/binlog窗口读取全局计数器。固定DATE/INT、完整列镜像逐笔重放，验证原值/日期/连续加一，拒绝删除、跳号、回退、缺原行和不完整镜像；保留高于仍存订单最大号的已占用序号，不能以MAX猜上限。原端全仓库订单号及原目标唯一，FH日期/序号不能超出可信counter。

RecoveryReleaseNumberInventory在既有closed事务内锁定全部原订单id/order_no，只对外给count/hash；提交前重新核对。历史终点必须与目标全局counter及全仓库号码映射完全相等，不能仅核本仓库。结果绑定控制/清单/历史窗/原库及两端pin，原件在authority/恢复集外按独立hash有界读取并再验；不新建许可、路由、Spring自动注册或NORMAL。

## 当次证据与失败保留

- `.local/d01-d18-20261008/received-number-unit-01`：编译失败，0执行；新增测试误在JsonNode上调用ArrayNode.addArray，修复类型后保留首败。unit-02：20项全过，是初始产品输入。
- `received-number-unit-03`：156项全过/9XML/无skip/exit0/sourceUnchanged，含真实MySQL列位图padding、缺列/NULL/截断/错布局、未知原值/日期改写/删除/回退/跳号、无订单占号及重复日期/号码反例。
- 新1264真实MySQL专项1失败，原因是MySQL列位图高位padding为1，初稿按整字节比较而拒绝。修复只忽略超出两列的padding，实际列位与NULL位严格保持；原库dump核验/认证停机/原PID退出，失败datadir/binlog/日志/XML保留，不复用。原1233未改未重跑。
- 新1265专项1/0/0/0、exit0/sourceUnchanged；原日志逐次分配通过，为尚未补全局目标映射前的中间输入。Node01因Windows mysqlbinlog头行CRLF未trim而未读到counter，失败原件保留；Node02修复独立文本消费者后8输入hash不变、原分配逐值通过。
- `received-number-regression-01`：recovery/handover普通3301全过、113XML、无skip、exit0/sourceUnchanged，覆盖最终产品输入；之后仅补专项尾写辅助类，由1266实际编译执行，未机械重跑普通回归。非完整server/IT。
- 最终1266：新隔离目标跨33树分配，原1233 dump/binlog恢复→原归档/native→主管围栏/固定清单→真实编号历史专项1/0/0/0、exit0/sourceUnchanged。错目标counter、错原pin、反向端点拒绝；另一仓库订单号真实变动而counter不变时拒绝；counter及另一仓库号码的事务尾写均在提交前拒绝并全库/文件回滚。原事实/归档/STOP/库存保留，无新STOP/ACK。
- `.local/d01-d18-20261010/received-number-consumer-03`：Node调用原生mysqlbinlog独立逐笔解析，8输入hash不变，原两端/全局counter/全仓库目标映射/控制清单完全匹配；`received-number-inventory-consumer-02`另核清单原签名与事实。两者exit0，独立工具不代独立人员。
- `.local/d01-d18-20261010/temporary-evidence-restore-mysql-1266`：dump重算一致、认证shutdown0、原PID退出、19330/19434空；各MySQL运行原报告在`.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-<run>`，原数据库与材料在`.local/e2a-cross-20260925-<run>`。

## 保全与下一步

设计/自审及独立脚本在`.local/d01-d18-20261010/received-release-number-design.md`、received-number-self-review.md、received-number-consumer.cjs；close-preflight-01保全全部dirty副本/hash/diff、精确Git/进程/监听/C/D余量，69历史比较与22原STOP保护无漂移。全部新增产物/TEMP/Java/cache在D盘，无清理。明确暂存/提交/远端回执见received-number-precommit-01、postcommit-01；不循环提交自身SHA刷新。

没有新增HTTP/Android正向业务或正式签名，服务专项使用合成主管身份；库存/设备/来源/案件/调整/审计的整体语义闭合仍未完成。后续释放必须重读锁定实际清单，补主管与运维确认，按库外PREPARE→DB排他锁提交revision/epoch/范围→库外COMMIT→实例核两侧证明，再签发本代次新资格并接收最终提交。旧票据不激活，未知设备/来源/范围继续隔离。原全库资料显示未纳入先前清单的准备/同步/PC/库存基线/证据子表，须显式核对，不能只靠本批编号通过放行。

D08–D18及第8节保持原范围；D04/D05未定政策、原资料/旧BCD/526、C01–C08原身份与现场限制继续单列。生产默认/签名/七类决定关闭、localDeletionAllowed=false，不接原库/现网、不合main、不部署/发布；1150/1151/UI-06和1100–1102未重跑。
