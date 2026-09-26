# 全应用恢复门禁实施断点

2026-09-26，基于已验关闭意图version2、事务租约和V39投影。当前仍是单助手在原两树开发，未配置或部署生产门禁。

## 实际入口及覆盖方式

| 实际入口 | 必须保护的生命周期 |
| --- | --- |
| JdbcTemplate及172处事务/事务配置引用 | 外部租约先于取得连接；连接/提交UNKNOWN时保留租约并毒化实例；池中空闲连接不算已接纳业务写者 |
| Spring Session JDBC | SessionRepositoryFilter之前接纳请求，直到Session保存结束；CSRF/GET也可能写Session，不能仅拦POST |
| PDA登录/刷新/退出 | challenge/session/last_seen/审计都是受控认证写；保留权限/设备/CSRF等既有校验，不连带发新作业资格 |
| Worker poll与提醒sweep | 接纳覆盖claim、执行、独立续租、完成/失败及文件进程退出；旧Job代次仍沿V38核验 |
| ImportFiles/SubmissionFiles/ExportFiles | 文件写前须已有有效操作租约；API异常路径、未入DB引用的文件也属于排空范围 |
| E2a SourceEvidenceIntakeFiles/ObjectFiles | 读取路径也可能createDirectories；文件活动纳入请求/任务范围，不能把GET等同无写 |
| ExportRendererProcess/报告子进程 | 父操作持租约至已验退出确认；Windows Job已有实现，Linux完整隔离仍须结合服务容器证据，未知退出保持关闭 |
| Flyway及BootstrapAdmin | 常规应用实例不能凭缺控制记录自行初始化NORMAL；迁移是显式独立许可，运行期间不得与业务写并行 |

## 实施选择与完成标准

采用统一接纳组件，连接层、请求层及任务层复用同一固定实例身份；维持coordinator→writers→server_state→业务锁顺序。只在service深处额外读一次文件不足以保护Session和文件活动。全局DataSource代理不能在底层连接开放后才申请外部租约，也不能在Connection.close返回时无条件把UNKNOWN视为已回滚。

1. 先完成连接适配器：原共享租约取得后才借连接；SQL异常、begin失败、真实commit丢响应、autocommit写错误、abort/close未确认均有明确保留/毒化行为；unwrap不得提供绕过代理的原连接。不能因回调返回或关闭连接就宣称服务器事务结束。使用现有DataSourceTransactionManager避免08007被普通错误转换掩盖。
2. 建立明确的初次NORMAL登记协议及DB对应证据。初次初始化不能沿用灾后RELEASE_READY后开放能力；缺两侧一致提交证明不得放行。已有闭合恢复记录保持只读/受控状态，生产释放端点仍不存在。原安装、原数字/UUID映射、原请求和审批边界不能重新生成或猜测。
3. 在相同输入上接实际DataSource、Spring Session外层、Worker和文件执行入口；可选开发配置不得被记录为默认全覆盖，遗漏入口不得以静态扫描通过充当动态证明。
4. 独立非空MySQL与真实HTTP验证：普通正常请求、CSRF会话保存、控制关闭与活跃请求/事务/文件竞争、新请求拒绝、旧实例重启/控制缺失/DB回退、UNKNOWN后排空。原ID/原回执/业务表无意外变化；关闭后的认证/证据只按已定专用能力接入，普通提交/配对/资格发放不能借道。
5. API元数据/Android传输epoch和原事实epoch分开升级，旧客户端拒绝新作业但保留资料；UUID迁移不靠替换一个响应字符串完成。它与全部入口、恢复证据协议组成最终组合验收，不能以本页或连接单测结束总目标。

状态（2026-09-26 更新，原“仅完成入口盘点”已过期）：连接代理与连接适配器已实现并经隔离验收（`RecoveryAdmittedDataSource`/`RecoveryJdbcProxy`/`RecoveryWriterLease` 毒化，`RecoveryJdbcObjectHandler` 现已与连接调用共用同一监视器，见 [入口接线验收](../../history/R09_APPLICATION_ENTRY_ACCEPTANCE_2026-09-26.md)）。**入口接线第一批（DataSource 与 JdbcTemplate/事务）已完成并经独立非空 MySQL 验证**：opt-in `RecoveryAdmissionConfiguration` 使 admitted DataSource 成为 `@Primary`（JdbcTemplate/NamedParameterJdbcTemplate/JdbcTransactionManager/Spring Session 都取它），原始池只以 `@FlywayDataSource` 交给迁移；默认关闭，权威缺失启动即失败。**仍未接**：HTTP/Spring Session 外层生命周期、Worker 与文件执行入口、NORMAL 双侧证据与生产释放；`ordinaryWritesAllowed=false`、`localDeletionAllowed=false` 不变。后续实现/测试结果另写验收，不将本页当已完成。

## 连接适配器当批细则

采用标准JDBC接口代理，按Connection串行调用以防执行与关闭竞争；DataSource不暴露原池，Connection、Statement、ResultSet、DatabaseMetaData的反向引用全部留在代理内，vendor unwrap拒绝。固定启动控制身份，在借连接前重新核对原件并持共享租约；阶段/意图变化拒绝新连接，旧连接完成后才释放。

关闭前显式进入手动事务并rollback，确认服务器没有遗留事务，再关闭池连接；不会把逻辑池close本身当回滚证明。commit/rollback/隐式commit失败、连接类故障、无法识别的执行错误、abort异步退出及close失败均保留锁并毒化该适配器。已确认的SQL语法/数据约束错误允许显式回滚后正常收尾。UNKNOWN不能被后来的成功rollback/close洗掉，必须隔离进程并检查DB实际排空。

验收覆盖借连接前拒绝、借连接/初始化失败、自动提交丢回执、commit真实完成后丢回执、Spring事务、原始SQL开启事务后关闭、SQL失败回滚、反向引用/unwrap、并发执行与close、abort/close失败、控制命令耐久意图与原键重试。适配器本身是生命周期组件，不授普通写/生产恢复权限；NORMAL和全入口接线仍为下一子项。
