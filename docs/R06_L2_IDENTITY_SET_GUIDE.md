# R06 L2 精确身份锁集合与原人变化

日期：2026-09-21。基线 `c3009b691d9d2c0afc32dad6e8f2293d36742a3f`，工作树 `D:/acrylic-pda-worktrees/r06-l2-locks`，短分支 `codex/r06-l2-locks`。本批续接 [L1](R06_IDENTITY_LOCK_GUIDE.md) 与 [锁图/场景](parallel/R06_M2B/HANDOFF.md)，不开放 TEMP 决定、关联、拒绝或放行。

## 实现范围

保持最外层事务的 E/G 前缀及仓库互斥。`IdentityWriteGuard.users` 去重后按规范 UUID 字符串顺序逐行取得 U 排他锁，与现有 `Ids.bytes` 的无交换 BINARY(16) 无符号字节顺序一致，不使用 `UUID.compareTo` 的有符号 long 次序。用户是否有效、权限及仓库范围仍由原鉴权逻辑持锁重验；锁到某用户不等于授予其权限。

| 范围 | 完整集合与重验 |
| --- | --- |
| MutationAccess / SubmissionAccess / RecallAccess | 实际 Web 操作者在 W 前取得 U；PC 与续作调用者一次传入额外原人/目标集合。重复嵌套 access 只重取本事务已经持有的成员 |
| PC review/resume、异常 stop/propose/review/apply | G 前提示读取原 operator、指定 username 对应目标、原计划 target/approver 及当前计划 ID；与 actor 合并排序 U，再 W/O；O 后重新读取相同提示，变化则 `PC_IDENTITY_CONTEXT_CHANGED`，不补新成员、不静默换目标 |
| PC 回执 | 先在已持 actor/W/O 的范围恢复同 actor、原 key、原 hash 的既有回执；没有回执才执行提示一致性检查。并发同一交接导致 operator 改变时仍返回原回执，其他旧请求整体回滚。原 `requireOperator` / `ExceptionChecks.permission` 仅重验已持 U，不在 O 后首次取 U |
| ContinuationApproval / Authorization | 原 assignment/user/device 提示先读取，actor+原 user 在 W/D 前排序锁定；原授权身份在 C/O 后再次比较。原人变化返回 `CONTINUATION_IDENTITY_CONTEXT_CHANGED`，不以批准人替换原操作人；无现有 PDA 会话参与的离线授权不伪造 H |
| PDA login/refresh/logout 与现有作业入口 | login 先定位既有用户；refresh 先定位 H/U/D。E/G→U→W→D→精确 H，取得 H 后复核 user/device 绑定并重新验证状态、版本、令牌和权限；refresh 移除原 H JOIN U 的隐式锁。恢复期间既有认证行为保留 |
| 人员与设备批量撤会话 | 人员 actor/target U 排序；人员旧/新仓库集合按 UUID 排序，随后会话按 ID 排序锁定。设备先 actor U/W/D，再锁定该设备 H 集合后撤销。新人员/新会话的首次插入仍受 G 和既有父锁保护，不声称不存在行已被提前锁住 |
| 邻接写入口 | 导入 actor、配对批准人、同步用户均在首次 W/关联写之前锁定。审计上传/归档从原授权核对所有历史 operator，与当前上传人合并取得 U，再 W/D/H；同设备跨人员的原审计不被改写 |

新类为 `PcIdentityLocks`、`PdaSessionLocks`；其余沿用现有 access/store/service。没有迁移、生产签名协议、Worker fence 或新的业务接口。Flyway V33、OpenAPI 0.29/116 路径、Android code20/Room17 保持。普通有权人员正常直接确认目标及 `localDeletionAllowed=false` 不变。

## 实际验证

仅使用本树新建 MySQL `127.0.0.1:19321`、库 `acrylic_outbound_l2_20260921`、独立账号/签名/私有目录。L1 与 R08 测试只增加此精确 URL/账号护栏；测试前继续检查实际 DATABASE。原共享 13316 在恢复时没有监听，未启动或修改其数据目录。无 HTTP 服务、设备、AVD、M71 或现网操作。

| 当次验证 | 结果 |
| --- | --- |
| 基础编译与单元 | `compileJava`、`compileTestJava`、43 单元通过；当前生产代码的 `bootJar` 已生成 |
| 第一轮定向 | 8 项通过，包括 3 项新 PC 集合场景与原 5 项认证/恢复门禁 |
| 第二轮定向 | 32 项通过，包括当时 6 项新 L2 和原 26 项 L1；后续完整回归覆盖相同用例，不重复累计 |
| 较大范围回归 | 531 项中 523 通过、6 项 R08 夹具配置失败、2 项跳过；失败原 XML/日志保留 |
| 补充最终验证 | 15 项通过：最终 9 项 L2 与 6 项 R08 撤权；报表 `installDist` 成功。对相同测试类采用最后一次完整执行替换，最终 **532 项不同集成用例通过，0 失败，另 2 项跳过** |

跳过的是 `OrderNumberMigrationIT`（未提供独立 V30/V31 升级库）及 `TemporaryAndroidSubmissionIT`（缺真实 Android 冻结输入）。本批无迁移改动，不创建这些额外环境冒充必要验证，也不将跳过计入通过。XML 中 E1 三个不同参数化方法共用 `[1] suffix=` 等显示名称，不能用 class+显示名称直接去重，否则会错误少算 6 项；本地摘要以最后一次完整 class suite 为单位替换。

9 项新增覆盖：实际交接改变原 operator、真实审批改变 approver、真实替换方案改变 target、同 key 同正文交接跨 operator 变化返回原回执；PC review/resume 原人变化两例、续作原授权原人变化一例；refresh 等待 U 时尚未持 W/D/H；无符号高/低 UUID 反向输入、重复/空成员下先等低 UUID 而不先锁高 UUID。

其中 PC review/resume 与续作原授权三例使用新库中的受控原人字段变化夹具，真实事务/真实两连接并保留 G/U/W/O，专测提示失效防御；不是已实现的改派/更正业务入口。其余 PC 变化经既有真实 HTTP 控制器的 MockMvc/service/事务链执行，无网络服务器。并发仅用 spy 暂停真实 SQL 或门禁调用，未 mock 数据库或事务。

## 场景证据与边界

| HANDOFF 场景 | 本批实际证据 / 尚未覆盖 |
| --- | --- |
| C03 | 原 L1 login/refresh/logout 与真实人员撤权；新增真实 refresh 等 U，P_S 显示 app_user 等待且未持 W/D/H；新增高/低 UUID 排序。批量撤 H 的既有人员/设备回归通过；未声称所有 H 批量执行计划均已取证 |
| C04 | 7 项上述原人/计划/批准人/回执测试；P_S 显示 G 等待及两端实际锁，事务回滚/提交状态保存；最终 work/plan/continuation/占用/dispatch/回执数量另存 JSON |
| C05/C06 | 本次重跑 L1 设备停用/临时准入、跨仓 G、导入/配对旁路，并跑原准备/上传/取消和恢复认证；未实现的新 TEMP 决定不计入 |
| C07/C08 | 重跑 TEMP 同来源并发唯一正式化、上传/abort 并发，以及正文先到/取消先到顺序用例；原签名/占用/无正文边界保留。未新增强制两种提交先后的双连接屏障，尚无成功 TEMP 取消路径 |
| C09 | 原 PDA/PC 抢领和正式修订/发布回归；LINK/双来源映射/目标空白和两目标集合尚未实现，未验 |
| C15 | 原不同 PC 单同包竞争、库存发布并发、正式/TEMP冲突、autoDeduct=false、签名/审计失败回滚回归通过；未完成全部反向输入包序和批量 DML 锁集证明 |

全部本机证据在本树 `.local/evidence`，不入 Git：`focused-first`、`focused-second`、`regression-first/test`、`regression-first/integrationTest`、`supplement-first`、`final-test-summary.json`。`C04-*.json` 保存真实等待/连接/事务结果，`C04-*-state-*.json` 保存最后持久状态；`C03-refresh-user-before-warehouse-*`、`C03-unsigned-user-order-*` 保存 U 等待及尚未持有后续行的证据。没有在旧源码上重放死锁，不声称所有死锁/吞吐/SLA已验证。

## 失败、环境与接续

初始化先因原 13316 未监听失败；独立实例首次初始化缺父目录，随后目录校验因 mysql batch 转义反斜线失败，均在任何业务测试之前。修正目录和 `--raw` 精确校验后使用全新专库；工具原输出摘录在 `environment-initial-failures.txt`，未生成配置时的构建失败在 `.local/compile-first.log`，不作为成功。

较大回归的 6 项失败发生在 R08 创建导出夹具时，返回 503；新工作树未提供真实 renderer-home。保留 `.local/regression-first.log` 和对应 XML，构建本树报表运行目录并通过本地 Spring 配置接入后，六项撤权测试全部通过。没有修改 R08 生产入口或 RR 快照顺序，也没有弱化断言。

本批仍不关闭完整 M2b 写阶段：TEMP 来源归属/共同包占用、非空迁移、命令台账/不可变签名回执、关联/拒绝/最终确认、Android 保存与归档、反向正式提示及 R09 恢复/Job fence继续排队。真实 HTTP/设备/现场、长时负载未验。作者结果与后续组合验证不得相加。

交付只提交推送短分支，待独立审查后由总调度安排执行者整合；不合 main，不发布/部署，不改主协调五份管理文件或 `docs/out.xlsx`。本树专库、签名和证据保留；结束时仅正常停止自己的 19321 实例，原服务保持发现时状态。
