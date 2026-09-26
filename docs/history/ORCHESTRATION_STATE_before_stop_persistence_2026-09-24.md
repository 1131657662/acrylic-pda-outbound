<!-- 2026-09-24 STOP 持久接收前原当前区，保留原文。 -->
<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：V36 实库、真实文件写者及 STOP 纯解析

- 写权/范围：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 开发与隔离测试库；主树仅维护管理/历史。未恢复旧代理、创建新任务/工作树、覆盖其他脏改动；重型槽空闲。原库、现网、设备和正式部署仍有独立安全边界。
- Git：E2a 树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，78 项未提交/未跟踪，清单以该树 `git status --short` 为准；本轮修正未提交 V36 OPEN 复合 FK，新增隔离迁移/真实文件写者 IT 和 STOP 纯解析与测试。主树 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原 Android/server/管理脏文件及 `docs/out.xlsx` 保留。未提交、推送、合并、发布或部署。
- 作者/自审：V36 的 stage 通过固定 `OPEN` 鉴别列与复合 FK 防止同来源 PAGE 回执伪装 OPEN。全新隔离 19330 attempt3 的非空 V34→V35→V36 验证 FK/唯一/不可变触发器/事务回滚、旧行文件保留及重启；attempt4 再用真实 raw/PAGE 文件与 JDBC 写者验证实际 stage、同页一致读取、重复原记录导致页面对象事务回滚、文件仍耐久及重启留存。各自 opt-in IT 1/1 成功，实例经精确 PID/datadir 核对后鉴权停机。STOP 新增严格六字段、manifest/set 派生摘要及 P-256 签名/服务器预期身份绑定的纯解析，6 项聚焦测试通过；首次 lambda 编译错误与自审发现的过度 UUID 事件限制均已修复。
- 验证分列：最新 `gradlew -p server test --no-daemon`：49 XML、334 项/0 失败/0 错误/1 Windows symlink 跳过；`git diff --check` 通过。attempt4 作者 IT XML SHA `2A6B0C73440B909BEBFBFA0D1A5CFA7524937E2540BE1D056F4776C81B190790`；重启前后 SQL 同 SHA `9DBBFD08B0F1777A60A9BC41F76EAA2860A3C60C6774BBCF434D7322E2EC9D22`，存 E2a 树 `.local/e2a-v36-attempt4/private/`。attempt3 和较早失败/证据见该树 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。无独立消费者；无完整 PAGE 服务/真实 HTTP/Android 组合验收。
- 失败/限制：attempt1 因 Gradle 工作目录守卫在连库前失败；attempt2 使用未强制 OPEN 的旧 V36 DDL，自审发现后保留原证据，修正后使用全新 attempt3/4；未重置/复用失败库。STOP 目前仅纯解析，trustedKey 必须由未来服务端设备资料选择，未读历史 keyring、未存 STOP object/set、未逐页核对或完成 VERIFY。PAGE 原键同/异文真实回放、并发、真实文件故障/断电、可信 B/K、GET/currentEffect、HTTP/Android、现场/审批仍未验。R05 整体、code20/Room17、R09 限制不变；R06 第六步及前九步未完成。
- 环境：19329/19330 均无监听，3306 仍 PID 5448 且未连接/操作；attempt1–4 datadir/私有凭据/marker/日志均保留，private 目录只当前用户 ACL。生产七类 handler/POST、决定和放行保持关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一步：沿现树完成 PAGE 服务原键/并发/故障组合，STOP 的可信设备 key/持久对象/页根封闭及 VERIFY，然后逐包可信占用、GET/currentEffect、HTTP/Android，按自审结果完成 R06 后推进 R07–R09。人工、长时、真机、现场/正式审批可后延，但必须保留未验标记。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
