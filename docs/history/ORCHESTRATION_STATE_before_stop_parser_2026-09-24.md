# STOP parser 前的当前区原文（2026-09-24）

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：V36 非空动态迁移及 OPEN 约束自审

- 模式/写权：当前助手在 `D:/acrylic-pda-worktrees/r06-e2a-migration` 单写 E2a 开发与隔离库；主树仅维护管理当前区/归档。没有恢复旧代理、创建 app 任务/树、覆盖主树其他脏文件；重型槽空闲。原库、现网、设备和部署仍有独立安全边界。
- Git：E2a 树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，当前 75 项未提交/未跟踪（精确清单以该树 `git status --short` 为准）；本轮新增 V36 动态 IT/约束测试，修正未提交 V36 DDL，并更新专项证据。主树 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，既有 Android/server/管理脏改动与 `docs/out.xlsx` 均保留。未提交、推送、合并、部署。
- 作者实现/自审：V36 stage 新增默认且 CHECK 固定 `OPEN` 的鉴别列，复合 FK 绑定 V35 回执的 `operation_kind`，修复原来仅按来源键连接而可指向 PAGE 回执的实质漏洞；PAGE 写者接口不变。静态 V36 测试通过；新一次性 19330 attempt3 在非空 V34 seed 后迁移 V35→修正 V36，验证同来源 PAGE 冒充 OPEN、跨来源 raw、缺失 OPEN、重复原记录/对象、UPDATE/DELETE 均拒绝，事务回滚、旧行/文件保留、Flyway 34/35/36 成功及重启后表/回执/FK 仍在。作者 opt-in IT 1/1，通过；静态测试 1/1，通过。不是 PAGE 真实服务组合验证。
- 独立/组合/历史：本轮无独立消费者或 HTTP/Android 组合验收。V36 使用合成对象引用，未上传真正原始文件，不证明 PAGE 完整服务。上轮完整后端单测 328 项/0失败/0错误/1跳过是历史本树结果，本轮未重跑全部。V35 attempt8 独立只读复核仍是历史，不能代替 V36。
- 失败/限制：attempt1 因 Gradle `user.dir` 为 server 而在 DB 连接前的守卫失败，XML/日志/库保留并鉴权停机；attempt2 迁移/重启通过但自审发现 V36 未强制 OPEN，作为旧 DDL 证据保留；新 attempt3 才是修正后结果。原 attempt1–7 与旧缺样本 bootJar 失败保留。PAGE 原键真实同文/异文、并发、文件与 DB 故障回滚、fsync/断电、STOP→VERIFY、可信 B/K、GET/currentEffect、HTTP/Android 仍未验/未做。R05 整体、Android code20/Room17、R09/现场/审批限制不变；第六步和前九步均未完成。
- 环境/证据：19329/19330 无监听；共享 3306 PID 5448 未连接/操作。attempt3 专用目录 `.local/e2a-v36-attempt3/private/` 保留 XML `C8DFF97116868A72322ED0F4577C75E9627659C8FDE2FDF5C726B411AAB68D54`、重启前后同 SHA `8AB4BE57A24338DF99F37D07A06A0E89CA876D67A17B5B3818D6498F59CA5B9B` 的 SQL 输出和认证停机日志；专项见 E2a 树 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。attempt1/2 路径及失败未删除或重用。
- 下一步：沿本树做真实 PAGE 服务/文件/原键重放、并发与故障组合验收；再实现 STOP→VERIFY、可信逐包核验/只读路径、HTTP/Android，完成 R06 子项自审后推进 R07–R09。人工、长时、真机、现场和正式审批可后延但保持未验。七类生产决定 handler/POST 与放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
