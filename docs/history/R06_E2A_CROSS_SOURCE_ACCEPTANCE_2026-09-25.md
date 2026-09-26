# R06/E2a 跨来源事实验收（2026-09-25）

当前入口仍为主树 `docs/ORCHESTRATION_STATUS.md` 顶部。本记录是当前助手作者、自审及隔离组合验证，不是另一独立审查者的验收，也不是生产或真机结果。

## 接管事实与版本

- 会话 `01a0d612-1e51-78c2-9eca-f15671339987` 已建立 active 持续目标；旧任务 idle/interrupted/notLoaded，未发现 Java/Gradle 在途执行或 19330/19434/19435 监听。接手后文件哈希复核仅出现本会话改动。
- 主树 `D:/acrylic-pda-outbound`：`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。
- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`：`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。两树 origin 均为 `https://github.com/1131657662/acrylic-pda-outbound.git`。
- 接管初始未提交路径/字节摘要：主树 `outputs/r06-e2a-takeover-20260925-01a0d612/{main,server}-initial-{status.txt,files.json}`，分别 122/156 个文件；全部保留。没有提交、推送、合并、部署或 main 写入。
- 看板红测已被旧会话实际修复：Mockito 明确 `(byte[]) call.getArgument(2)`，用例迁入 `SourceEvidenceIntakeCrossSourceTest`。旧复现失败及修复证据保留在 `outputs/r06-e2a-takeover-20260924` 和 `outputs/r06-e2a-takeover-20260925`，不重复修改已完成夹具。

## 实现与自审

- 继承代码以全设备连续 CAPTURE 前缀核对签名高水位；本 TEMP 原正文中的 CAPTURE/SCAN 必须齐全。其他来源须由同设备、同仓 `submitted_capture` 定位其他提交，并读取摘要一致的持久原正文；缺失、伪造、遗漏、重复、跨仓失败封闭。
- `SourceEvidenceIntakeBusinessFacts` 只返回本 TEMP 扫码给 `SourceEvidenceIntakeSetWriter` 的占用写者；其他来源条目仍保存在证据集合，但不取其包锁或写其 K。完整性与业务事实验证发生在集合写入之前，失败事务不得留下集合、明细或占用。
- 本会话修复 `E2aCrossSourceFixture`：条码从 13 位改为 11 位；插入库存位置前补足对应快照明细外键事实。
- 真实 HTTP 暴露生产缺陷：VERIFY 在事务外读取要求 MANDATORY 的登记设备公钥，导致 500。已在 `SourceEvidenceIntakeVerifyService` 通过 `transactions.read` 读取初始签名上下文；写事务中的登记公钥、STOP、PAGE 原件重验保持。
- 自审覆盖原正文摘要校验、其他来源查询设备/仓库约束、全设备前缀与本 TEMP 数量区分、K 输入过滤及事务回滚。未引入政策或关闭/放行能力。

## 本轮实际验证

- 初次聚焦：4 XML/35 项，0 失败/错误/跳过。
- 事务修复后聚焦：5 XML/43 项，0 失败/错误/跳过。
- 事务修复后服务端全套：75 XML/481 项，0 失败/错误，1 Windows symlink 跳过。报告在 `outputs/r06-e2a-takeover-20260925-01a0d612/full-after-transaction-results`，日志 `full-after-transaction.log`；这是本轮结果，旧 463 项不作本轮依据。
- 服务端 `.local/e2a-cross-20260925-4`：独立 datadir/schema/随机凭据及受限 ACL，连接后核对 datadir/port/schema/current_user；19330 仅 loopback，binlog=1、log_bin_trust_function_creators=1。先建立并造数 V34，再迁至 V36，在非空库执行来源事实正负例。
- 同一隔离实例的真实 HTTP 19434：未认证 401；OPEN/PAGE/STOP/VERIFY 成功与原键重放、GET 原字节一致；篡改他源捕获摘要 VERIFY 返回 409，未留 set/K；还原事实后原请求成功，5 条证据明细、1 个 VERIFY 记录，本 TEMP 包为 SOURCE_RESERVED，他源包无占用；同键异体 409。
- IT 为 1 项，0 失败/错误/跳过，XML SHA256 `8A5FBE2903D481F3729C14433FD01C10C3EB3502ADFEEE745F46B6FADAE17AE0`；15 份 HTTP 原响应及完成标记保存在上述隔离目录。
- 单元测试与实库/HTTP 均由当前助手执行；没有另一独立消费者或 Android→服务端组合结果。

## 失败证据与范围限制

- cross attempt1（继承）：13 位 barcode 违反 CHAR(11)，造数失败。
- cross attempt2（本轮）：库存位置缺少快照明细，外键失败。
- cross attempt3（本轮）：SQL 正负例与 OPEN/PAGE/STOP 已过，VERIFY 初始设备公钥读取缺少事务，HTTP 500。以上独立数据目录、报告、日志不删除/重置。
- attempt4 的 RAW 由服务端内部持久化原语准备，没有 RAW HTTP 路由验证。身份、原材料与设备签名均为隔离合成夹具，不能替代原身份、真实 Android Keystore、配对审批、设备或生产证据。
- 返回仍为 PARTIAL，`localDeletionAllowed=false`，七类生产决定 handler/POST、放行和关闭能力未开放。R05 code20/Room17、历史 keyring 部署、真机/现场/长时与 R09 恢复限制继续未验。
- 本轮独立 MySQL 已核对身份并认证 shutdown；19330/19434/19435 无监听、Java/Gradle 无在途；共享 3306/PID5448 未操作。
- 下一子项：Android Room19 基础上的 PAGE/manifest/root、RAW 请求与原键耐久，STOP 原机签名，OPEN→PAGE→STOP→VERIFY 丢回执与重启保守续传；R06 确认/处置/成功回执及 R07–R09 可独立事项继续。
