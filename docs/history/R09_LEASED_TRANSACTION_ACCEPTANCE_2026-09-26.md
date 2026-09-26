# R09 真实数据库事务生命周期租约验收

2026-09-26。两树分支/HEAD仍为主树codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c及服务端树codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95，加既有及本批未提交差异。未提交、推送、合main或部署。

## 完成标准和最终实现

- 固定实例启动时的完整库外快照；每次事务先在协调锁内复核原记录并取得共享写者租约，之后才开数据库连接/事务。旧快照、缺失协议和已有外层事务在回调/连接之前拒绝。
- 持租约直到提交/回滚及同步回调、Spring资源清理返回。afterCommit还没返回时即使数据已提交，控制命令仍须返回WRITERS_ACTIVE且原历史/指针不变。SQL失败正常回滚后可继续事务。
- 提交/回滚结果UNKNOWN保留原租约并毒化实例，不能以清理调用返回当作已确认回滚；进程隔离后再验证实际DB连接/事务排空。禁止通过REQUIRES_NEW/NOT_SUPPORTED挂起外层事务，让独立内层结果逃逸。
- `RecoveryLeasedTransaction`与`RecoveryTransactionLease`使用DataSourceTransactionManager/TransactionTemplate及同步状态。组件未注册Spring bean/路由；仅封装生命周期，调用者仍须在同一事务中先验证业务权限、阶段能力和DB投影，不能把它当成已经接好的业务门禁。

## 自审修复与当批验证

证据根 `outputs/r06-product-20260925-01a0d8fa/`：

1. `leased-transaction-focused-01/02/03.log`均保留。最终新增5项单测覆盖旧快照、外层同步/缺协议拒绝、提交结果未知及独立内层拒绝；完整`leased-transaction-server-full-01.log`及XML为671项=670pass、1既有符号链接条件skip、0failure/error。bootJar成功。
2. attempt75独立非空V34→V38数据库的真实事务验证发现JdbcTransactionManager将08007提交异常转换为普通DataAccessResourceFailureException，测试未继续HTTP。改用保留TransactionException/STATUS_UNKNOWN的DataSourceTransactionManager，并加丢提交回执反例。75失败XML、数据库与日志保留，已认证shutdown。
3. attempt76事务+HTTP通过；attempt77将控制记录恢复集明确绑定本次真实data/storage目录，并在测试JVM退出后查询账户其他连接/INNODB_TRX均为0，再按原命令推进。两次是补强前证据，不替代最终78。
4. **attempt78最终输入**：独立19330非空V34→V38；Python生成原控制记录、Java直接消费，不经Java重建原件。实际MySQL提交前不可见/afterCommit已可见两窗口中Python推进均拒绝；回滚、SQL唯一键错误、外层/独立内层拒绝、固定旧实例拒绝均验。
5. 提交丢回执通过测试专用JDBC代理在**真实MySQL commit成功后**抛08007；真实行已变为4，Java仍报告UNKNOWN并保持原文件租约、拒绝后续回调，Python命令仍不能推进。测试JVM退出后，连接/活动事务分别0/0，原命令成功推进revision2，仍ordinaryWritesAllowed=false。无生产故障开关。
6. 同一attempt78随后运行现有服务真实HTTP TEMP普通/短装XLSX、扫码Excel、PDF、汇总、重试及FORMAL/PC兼容；Java IT1/1、浏览器1/1，无skip。这是**原接口兼容回归**，不是新租约已接入HTTP。原始身份仍是合成fixture，不称现场原身份。
7. `leased-transaction-attempt78/`保存XML/log、事务完成标记、JVM退出后数据库排空、原命令重试和认证shutdown身份；私有凭据、签名材料和业务文件留服务端`.local/e2a-cross-20260925-78`。75–78全部认证shutdown；19330/19434/19435/19436无监听，共享3306 PID5448/父4412未动。
8. `leased-transaction-source-manifest.json`982项，SHA256 `a52f152ff4f00f573c14e4054b4acbfb6ebb5e22c9fb8b1cd04698a2745feba1`；JAR SHA256 `a8deb85ad201e72f227be5b2b3dc4b4da139447e78224ca16aec5f873e44c7e9`。两树全部dirty路径见`leased-transaction-final-main/server-status.txt`。输入稳定后完整单测与78之间无业务源码漂移。

运行器首版插入代码时因换行匹配失败而退出，未执行数据库动作；修正LF匹配后才分配75。该失败与75业务测试失败分开记录。

## 口径及下一项

作者/自审均当前助手；Python原件→Java与真实进程控制为独立实现消费，数据库/HTTP/浏览器为组合验证，无独立人员。无Android变化，未新跑APK/lint；未做项目Linux143启停、PDA/Keystore、断电或长期验证。

本组件限制受管理的Spring事务；任意直接连接/线程/独立文件写者仍需完整入口盘点和接线。尚未实现UUID/数字epoch/DB投影桥梁、关闭意图拒新写、普通NORMAL初始化/证据绑定释放、API/Session/Worker/Flyway全覆盖。下一项接这些缺口，不能结束R09或R01–R09总目标。localDeletionAllowed=false、七类生产决定/关闭禁用、原库与main/发布边界保持。
