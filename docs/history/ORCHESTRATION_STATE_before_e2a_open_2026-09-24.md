<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-23 E2a intake 冲突恢复边界：独立重查与单测通过

- 模式/写权：当前助手直接负责本项目开发、隔离测试库及相应运维；旧总调度/代理身份不恢复，不新建任务/树。当前仅在原 E2a 隔离树修改 intake 查询与测试；主树管理文档由当前助手维护。其他未提交工作均保留，同一目标不并写；重型槽空闲，未对原库/现网/设备/正式部署取得新授权。
- Git：主树 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`（本地领先 origin 7），原 Android/server 开发、管理文档、PDA 记录和 `docs/out.xlsx` 等改动保留；本轮只更新主树管理记录。E2a 原树 `D:/acrylic-pda-worktrees/r06-e2a-migration` / `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，迁移验收改动及 intake 类/测试仍未提交；精确清单以两树 `git status --short` 为准。本轮未提交、推送、合并或部署。
- 本轮代码：新增 `SourceEvidenceIntakeRecovery`，只接受可明确识别为 MySQL `23000/1062` 且约束为 `uq_evidence_intake_original` 的冲突；原写事务仍活动时拒绝重查；事务外调用 `SourceEvidenceIntakeReceipts.recheckAfterRollback`，以 `REQUIRES_NEW`、只读、`READ_COMMITTED` 独立查询。同键同请求回放原 response bytes，同键异请求返回 `CONFLICT`，完整回滚后无行返回 `UNKNOWN`，损坏回执返回 `INTEGRITY_FAILURE`，其它数据库错误继续抛出。
- 作者聚焦验证：`SourceEvidenceIntakeReplayTest` 13/0、`SourceEvidenceIntakeRecoveryTest` 7/0、`SourceEvidenceIntakeTransactionTest` 2/0；作者完整 server 单测 `./gradlew -p server test --no-daemon` 成功，26 XML、199/0失败/0错误/0跳过，结果在迁移树 `server/build/test-results/test/`。`git diff --check` 无空白错误；这是 Java 单测及 Spring 事务代理验证，不是动态数据库/HTTP/设备验收。
- 历史作者迁移证据：E2a 聚焦 35/0；非空 V34→V35 动态 attempt8（全新 `20260923h`/19329、binlog=1/trust=1/TRIGGER=1/SUPER=0）1/0，通过 28 案并停机，证据在迁移树 `.local/evidence-e2a-attempt8/` 与 `.local/e2a-dynamic-attempt8-20260923.stdout.txt`；本轮未重跑。独立只读复核先前比对 SHA、Flyway 34→35、旧表与私有文件不变；不是第二次迁移，详见 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 独立消费者与组合限制：既有 `E2aProtocol`/向量独立纯协议桥接是历史结果，本轮未重跑；本轮 189 项是作者单测而非独立消费者或文件/DB/HTTP 组合验收。V35 只给 INSERT-only 回执约束，不代表 OPEN/PAGE/STOP/VERIFY 接收闭环或可信 B/K 占用。
- 保留失败：attempt1–6 及 attempt7 停机身份探针失败证据均保留；attempt7 后虽鉴权停机，原运行结果仍是失败。先前首次全 server 单测因隔离树缺原 `docs/sku.txt` 失败的日志 `.local/e2a-server-test-bootjar-20260923.stdout.txt` 保留；后用主树原样本同字节只读副本才获得此前 177/0 与本轮 189/0，未把旧失败改写成通过。
- 环境：19329 和旧 Gradle 55363 无监听；共享 3306 仍由 PID 5448 监听，未连接、停止或操作；本轮无数据库启动。固定 V35/协议源码与生产配置未改；七类生产 handler/POST、决定/放行继续关闭，原库/现网/设备/审批边界保留。R05 整体未验、Android code20/Room17 历史、真实 HTTP/设备、现场/R09 限制继续单列；PDA 问题用户称已解决，本 E2a 未复核。
- 下一步：把恢复入口接入最小 OPEN 写者：第一次原键查询 → 既有来源/身份/锁序和第二次查询 → 严格 OPEN 正文/事实校验 → 不创建 set 的 intake envelope INSERT；只在写事务回滚后调用本轮独立重查。文件耐久、PAGE/STOP/VERIFY、独立消费者、动态数据库、HTTP/设备分别验收；不合 `main`、不部署、不复用失败库。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
