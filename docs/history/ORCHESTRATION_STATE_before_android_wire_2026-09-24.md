# R06/E2a Android 线协议增量前的当前状态原文

以下是被替换的当前区原文，仅作历史，不作为当前事实。

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：历史签名信任链与逐包保守 K 写者已作者自审，第六步未完成

- 目标：完成 R01–R09 所有不依赖人工辅助/长时间测试的开发；逐子项自审修复。当前沿 R06，不提前把 R07–R09 判作完成。
- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 业务及 `D:/acrylic-pda-outbound` 管理/历史；其它树未写。品牌任务已 idle；R07/R08/R09 旧任务未自动恢复。不触原库、现网、设备、正式部署/审批。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，主树 `codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；均有原有及本轮未提交改动，路径快照见 `docs/history/ORCHESTRATION_STATE_before_k_writer_2026-09-24.md` 末尾，后续以两树 `git status --short` 核实。未提交、推送、合并、打标签或部署；原 Android/server/管理脏改及 `docs/out.xlsx` 未覆盖/删除。
- 作者/代码：STOP 只保存独立对象与 `PARTIAL` 原回执，不因后续 set 事实失败回滚；VERIFY 事务内才在 PAGE/STOP 闭合、原字节、来源 B/K 事实核验后建立 set/item。原键 GET 保持本人原机 `pda:recover`、原请求/回执、来源与当前效力分离。E2a/原 TEMP/命令回执及提交/审计归档均可用当前 RSA 或显式配置的历史 DER 公钥按 kid 验签；未知 kid 不可信或归档失败关闭，E2a 原字节直出409，能力全 false。没有历史公钥私钥或自动信任入口。
- 作者/K 写者：VERIFY 的同一事务在 set/item 原字节、提交 payload、stored capture、当前 B/K 仓与周期及所属来源核准后，逐活动包按 bale ID 顺序锁 K；无 K 才插入 `SOURCE_RESERVED` 并标记条码同步，精确同源 `REVIEW_RESERVED` 不改，同 set 的重复验证不重插，他源/重包拒绝。旧 set 重验改为逐 item 同值比较；实测发现原 `manifest_bytes` 被当作16字节 UUID 读取的真实缺陷，已修复且补反例。VERIFY 仍只给 `PARTIAL`，不开放释放或任何业务决定。
- 作者/自审：先前新测试 Mockito 顺序断言失败和旧 set manifest 解析失败已修正并保留反例。历史 key 正常、轮换、未知、篡改、缺失配置，命令/审计与提交归档的轮换签名兼容，以及逐包 K 的无 K/同源/异源/重复/锁序/旧 set 变造聚焦通过。最新 `./gradlew -p server test --no-daemon` 74 XML、463 项、0 失败/0 错误、1 Windows symlink 跳过；编译通过，`git diff --check` 无空白错误。此前 OpenAPI 3.1/126 路径解析通过（此后未改 schema）。以上均为作者单元/静态/契约，不是隔离 MySQL 逐包 INSERT/事务、独立消费者、真实 HTTP/Android/APK/模拟器/真机组合验收。
- 历史隔离实库：attempt6 非空 V34→V35→V36 与合成签名归档/JDBC 1/1 是更早代码版本的独立局部证据，不覆盖本轮新增 GET/逐包改动；专用实例已鉴权停止，旧 key attempt2 binlog/SUPER 1419 失败保留。19330、19434/19435 无监听；共享 3306/PID 5448 未操作。旧私有文件/日志不清理，证据 `.local/e2a-v36-audit-attempt6/private/`。
- 失败/限制：原机 STOP 与捕获高水位/审计全量耐久、真实全链 PAGE→STOP→set→VERIFY 及新 K 写者隔离 MySQL/HTTP、Android/现场与长时仍未完成；当前 VERIFY 仅 `PARTIAL`。历史 keyring 代码具备但真实历史公钥清单未配置/部署，不能把合成轮换测试充当真实旧回执可信。STOP 文件可能在 DB 回滚后留孤儿，不等于受理。R05 整体/code20/Room17、R09 恢复与现场限制保持。七类生产决定 handler/POST/放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：审查 Android 原机停止、全设备序列与 Room 耐久接点，再实现 PAGE/STOP 本机冻结证明和原键续传；补隔离非空实库 K 写者/GET 实际 HTTP 全链，随后核 R07–R09 可开发项。每子项先自审修复再前进，启隔离库前重核重型槽，不以合成签名取代原身份。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_history_archive_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
