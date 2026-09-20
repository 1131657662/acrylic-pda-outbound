# R05 持久任务执行与失败重试

日期：2026-09-19。本文记录 R05 持久任务内核的源码、本机隔离 MySQL 验证及后续Linux恢复副本演练，不代表 R05 或整套系统已验收、部署或上线。

## 2026-09-19 PDA持续同步调度接续

后续已完成独立于UploadWorker的PDA订单/条码/审计后台调度、持久重试与会话/作业保护，192项主机测试、3阶段隔离模拟器跨进程演练、构建和lint通过，见[交付记录](R05_PDA_BACKGROUND_SYNC_GUIDE.md)。这轮使用回环合成HTTP，下一步接入另一份本机V28 API验证后台真实状态和签名归档；不重复开发下文已完成的云端提醒内核，不将调度验证计成生产或真机验收。

## 2026-09-19 当前待办API与Web入口接续

后续已完成当前超时待审API、Web入口和中文历史提醒，按仓库/审批权限/当前批次筛选，真实游标分页，审批后刷新移出，见[交付记录](R05_REVIEW_INBOX_GUIDE.md)。本轮32单元/353集成及15个不同浏览器场景最终通过，首轮失败独立记录；无新迁移，契约0.21/V28。下一动作转为PDA持续同步与失败调度；下文各轮历史验证不合并计数，Linux143阻断仍待补。

## 2026-09-19 待审业务的实际进程联调

已在另一份专用本机合成库完成真实HTTP与两个独立Worker联调：超时待审只提醒一次、未到期/已审批/已拒绝不提醒，提醒后审批再扫描不重复；审计写入故障使提醒与时间线整体回滚，同任务默认5秒退避后attempt2成功。最终9个调度任务成功、提醒/时间线各1条，每轮17表/8文件核对保持，详见[联调记录](R05_REMINDER_PROCESS_GUIDE.md)。进程已停、数据保留；未修改候选业务源码，不替代Linux143或现场验证。当轮尚缺的Web待办入口及当前超时待审API现已补齐，见上节；该进程演练保持独立证据。

## 2026-09-19 Linux恢复副本接续

已用0.20/V28候选在独立恢复副本启动非Web Worker，验证正常sweep、真实任务执行中SIGKILL、systemd重启及原两分钟租约到期后attempt2成功；未缩短租约或改写成功状态。该副本没有待审超时订单，不能据此声称实际业务提醒现场通过。本轮32单元/336 MySQL集成是重新执行结果，独立于下文历史307项记录。

新增独立Worker unit和API/Worker协同备份、仅后端激活脚本，7项服务状态及8项激活边界检查通过。副本服务均已停止、MainPID为0；Java退出143被systemd误标failed，新unit补上正常退出码，仅语法检查通过，实际启停复验被自动审批拒绝，仍待补。详细证据、最终候选包及恢复边界见[V28演练记录](R05_V28_REHEARSAL_GUIDE.md)。用户明确现网保持0.18/V23，不切换、不启动公网Worker。

## 已完成

- Flyway V24 新增 `background_job`，保存任务类型、幂等键、JSON 负载、状态、尝试次数、最大尝试次数、可执行时间、租约所有者、租约 token、租约截止时间、稳定错误码和 UTC 毫秒时间。
- V25 增加按任务类型的队列/租约索引和状态约束。迁移使用 MySQL 语法，锁等待上限为10秒。
- Flyway V26 新增 `submission_review_reminder`，以提交唯一约束保存待审提醒，按仓库和提醒时间提供查询索引。
- `BackgroundJobStore` 负责入队和幂等冲突核对；JSON 以结构比较，空格或对象字段顺序变化不产生重复任务。入队可加入业务事务，业务回滚会同时回滚任务。
- `BackgroundJobLeases` 在短事务内使用 `FOR UPDATE SKIP LOCKED` 领取任务；尝试次数在领取时增加。完成、失败、续租和租约恢复都匹配任务所有者及 fencing token，旧 Worker 不能覆盖新 Worker 的结果。
- 可重试失败使用 5、10、20 秒递增退避，最高15分钟；明确不可重试错误或达到最大次数进入 `FAILED`。租约过期在 `RUNNING` 与 `RETRY`/`FAILED` 之间恢复，不会自动批准业务结果。
- `BackgroundJobWorker` 只领取已有处理器注册的任务；处理器在事务外执行耗时工作，返回的短数据库发布回调与成功状态在同一事务提交。异常只保存稳定错误码，不把异常正文、SQL 或业务负载写入任务表。
- `worker` profile 提供独立非 Web Spring 进程配置：连接池上限2、单调度线程、固定轮询间隔1秒。当前没有计划单/PDF、导出或留存处理器，`order_plan_document_task` 未被虚假标记为成功。

## 待审提醒

Worker 每分钟幂等排入 `REVIEW_REMINDER_SWEEP`。处理器只选择订单当前提交、状态为 `REVIEW_REQUIRED` 且创建时间超过30分钟的批次，在 `submission_review_reminder` 中建立一次记录，并向订单时间线写入 `SUBMISSION_REVIEW_REMINDER` 事件。审批、拒绝、释放和库存账务不由提醒改变；已经离开待审状态的批次不会补发提醒。提醒事件使用空操作人和稳定 Worker 请求号，保留后续审计查询所需的来源。

## 验证

使用本机隔离 MySQL `acrylic_outbound_test`（端口由 `.local/development.json` 提供）执行：

```powershell
gradle.bat -p server test integrationTest --tests 'com.acrylic.outbound.jobs.*' --console=plain
```

结果：8 项退避单测、24 项真实 MySQL 任务集成测试通过，0 失败、0 错误、0 跳过；覆盖幂等入队、生产事务回滚、未来任务、并发领取、被锁任务跳过、租约到期恢复、旧 token 拒绝、续租、退避、最大尝试次数、Worker 事务边界和非 Web Worker 轮询。`compileJava` 与 `compileTestJava` 同样通过。

待审提醒另行执行：

```powershell
gradle.bat -p server integrationTest --tests 'com.acrylic.outbound.inventory.ReviewReminderIT' --console=plain
```

结果：3 项真实 MySQL 测试通过，0 失败、0 错误、0 跳过；覆盖30分钟到期提醒、审批后不补发以及并发 sweep 的单条提醒/时间线幂等。测试按仓库范围隔离 sweep，避免共享隔离库中的历史待审批次影响计数。

随后 `.\tools\development_server.ps1 -Task Verify` 完整回归通过：32项单元、307项真实MySQL集成、`bootJar`，0失败/错误/跳过，日志 `.local/r05-reminder-full-verification.log`。提醒定向验证曾因未注入数据库配置、未加载开发签名密钥失败；修正验证环境后通过，环境失败记录不省略。

## 启动边界

迁移完成后，Worker 使用独立运行账号启动，并设置 `SPRING_PROFILES_ACTIVE=worker`、`OUTBOUND_MIGRATE=false` 和现有数据库/私有存储/签名配置。当前没有为公网测试地址启动该进程；尚未进行生产资源限制、主机故障、文件系统恢复或异地网络验收。

## 未完成

实际 PDF/Excel/导出处理器仍归 R08，计划单仍使用原查询状态。PDA安全缓存清理已单独完成本机回归，见[交付记录](R05_RETENTION_GUIDE.md)，不属于云端Worker处理器。后续已完成[提交归档证明](R05_ARCHIVE_CONFIRMATION_GUIDE.md)及[审计连续前缀恢复](R05_AUDIT_ARCHIVE_GUIDE.md)源码/本机回归；完整业务事实留存清理仍未实现。真实待审现场、异地HTTPS、长时作业、主机断电、生产签名和正式部署未在本轮验证。V28/PDA及待审提醒进程联调已完成，当前待审API/Web入口已完成，下一具体动作是PDA订单/条码/审计的持续同步与失败调度；隔离unit实际启停复验仍受既有自动审批阻断，保留待验，不重复执行已阻断操作。原同步、在途事实和R09留存边界保持。
