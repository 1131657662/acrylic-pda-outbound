# V36 dynamic check 前的当前区原文（2026-09-24）

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：非空 PAGE item 解析、V36 跨页索引及 PARTIAL 写者

- 模式/写权：当前助手在原 E2a 隔离树单写开发与测试，主树只维护当前状态/历史；未恢复旧代理、创建任务/工作树或覆盖原未提交改动。隔离测试库由当前助手负责；原库、现网、设备和正式部署有独立边界，重型槽当前空闲。
- Git：主树 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，领先 origin 7；原 Android/server/管理与本机 `docs/out.xlsx` 保留，仅新增状态归档并更新管理。E2a 树 `D:/acrylic-pda-worktrees/r06-e2a-migration` / `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，73 项未提交/未跟踪，精确文件见该树 `git status --short`；本轮新增 `E2aPageItem`、`SourceEvidenceIntakePageItems`、`SourceEvidenceIntakePageItemWriter`、V36 迁移及四个聚焦测试，修改 PAGE/锁序代码和两份协议文档。未提交、推送、合并、发布或部署。
- 作者实现：非空 `pageCore.items` 固定十二字段、原 UUID/hash/数值与扫描字段、页内 record/object 去重及精确 byteCount；仍是声明而非可信事实。V36 新建只插入 `source_evidence_page_item_stage`，以来源/S/轮次限制跨页原记录及原对象重复，复合 FK 绑定同来源 OPEN、页对象和 raw 对象，锁序追加 stage。PAGE 写者先在事务外读 raw 索引并逐文件校验，再耐久存页；锁后重读对象、检查集合 128 MiB、INSERT stage，最后 INSERT `MANIFEST_PAGE` 对应签名 `PARTIAL` 回执；同页同摘要新原键只在 stage 完全一致时重用。仍无生产 HTTP、可信包占用或放行。
- 自审/作者验证：测试初次因 lambda 局部变量复用编译失败、全数字 UUID 大写负测无效而失败，均已修复。复审 V36 OPEN 外键最初只连原键，已补同 scope/S/round 复合父键。聚焦 item 严格格式、页内/跨页索引、原始文件缺失/篡改、锁后变化、总量、既有页一致及锁序通过；`./gradlew.bat -p server test --no-daemon` 成功，48 XML、328项/0失败/0错误/1跳过，结果在 E2a 树 `server/build/test-results/test/`；`git diff --check` 返回 0。静态 DDL 测试不是 MySQL 迁移通过，Windows 符号链接特权用例跳过、目录 fsync/断电未证。
- 独立/组合/历史：本轮无独立消费者；组合仅限作者 Mockito + `@TempDir` 原字节文件测试，未做实际 MySQL INSERT/并发/回滚/重启、跨语言向量、HTTP、Android 构建/模拟器/真机、现场或部署。历史 attempt8 V34→V35 与独立只读复核见 E2a 树 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md` 和 `.local/evidence-e2a-attempt8/`，不能代替 V36/PAGE 动态验收。
- 失败/限制：attempt1–7、旧缺样本 bootJar 失败继续保留。PAGE 原始 `recordSha256`、device seq/状态及扫描包归属仍是未可信声明，STOP→VERIFY、可信 B/K、GET/currentEffect/HTTP/Android 未做；V36 实库迁移和 FK/唯一/触发器及 PAGE 原键重放、故障回滚未动态核。R05 整体、Android code20/Room17、R09/现场/审批限制不变；R06 第六步及前九步目标仍未完成。七类生产 handler/POST 与决定/放行关闭，`localDeletionAllowed=false`，不合 `main`、不部署。
- 环境：19329/19330 均未监听，共享 3306 仍为 PID 5448，但本轮未连接/操作；Docker CLI 存在而 Linux daemon 不可连。本轮仅 Java 单测和私有临时目录文件测试，未启动数据库或复用旧库/失败路径。
- 下一步：在新命名的隔离 V36 库动态核迁移、FK/唯一/trigger、PAGE 非空原键同文/异文、事务回滚和真实文件引用，不重用 attempt8 数据；之后 STOP→VERIFY、可信逐包核验/读路径、HTTP/Android，再依序续 R06–R09。长时、真机、现场、正式审批按用户允许后延，但保留未验标记。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
