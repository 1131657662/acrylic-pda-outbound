# 项目现状审计（2026-09-26，只读）

本页记录一次只读审计的结论与依据，用于冻结后的下一步排序。审计未修改任何源码/配置/数据库，未部署、未动 `main`。审计对象：主树 `D:/acrylic-pda-outbound`（`codex/development`，冻结前 `dccb73b`）与服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`（`codex/r06-e2a-migration`，冻结前 `568683c`）。

## 0. 冻结已先执行

审计发现的首要风险即“已完成成果未入库”，因此先冻结再排序：主树 `3c677ad`（tag `freeze-20260926-pda-r06`）、服务端树 `d16836b`（tag `freeze-20260926-server-r06e2a`），两树工作树 0 项未提交、origin 一致。细节见[看板当前区](../ORCHESTRATION_STATUS.md#当前有效状态唯一接续入口)。

## 1. 问题（按后果排序）

### P0 成果不在 Git 里（唯一不可逆风险）
- 冻结前主树 HEAD `dccb73b`（09-24）的 `PdaDatabase` 为 **version 17**，工作树为 **27**；91 个文件被修改、258 个未跟踪（其中未跟踪代码 9,613 行、116 个 `.kt`）。
- 冻结前服务端树 268 个未跟踪，含 **V35–V41 六个迁移**、整个 `recovery/`（32 文件）与 `temporary/evidence/`（65 文件）；主树服务端只到 V34，且主树不存在 `recovery` 包，`RECOVERY_PENDING` 在主代码中命中 **0** 次。
- `docs/history` 冻结前 152 个文件中只有 **4 个**在库，而看板引用 22 份 `history/*.md`。
- `main` 落后 `codex/development` **129 个提交**；全仓 git 对象仅 11 MB，磁盘上相关目录 >110 GB（`.local` 21 GB、`D:/acrylic-snapshots` 30 GB、worktrees 50 GB、`demo-artifacts` 10 GB）。
- 结论：冻结前不存在任何一个能代表“系统现状”的提交，一次误删/`git clean` 即损失数周工作。

### P1 核心业务链在服务端是断的
- 七类生产决定命令无 handler bean、无 POST 路由：`CommandOperation.java` 自述无生产实现，`CommandUnitOfWork` 注入空列表即 `unsupported()`，`CommandExpected` 把 10 个关键 `expected` 字段锁死为必须为 null → `409 COMMAND_EXECUTION_NOT_IMPLEMENTED`。
- TEMP 归档硬拒绝（`SubmissionArchiveService`）；TEMP 导出只在服务端树且未接；R08 导出依赖未交付的 `renderer-home`，未配置即 `503 EXPORT_UNAVAILABLE`。

### P2 恢复是单向门
- 主树有 `server_state` 门禁（11 处写路径，非 `NORMAL` 即 503），但主树**没有任何代码能写回 `NORMAL`**；能置位的 `recovery/` 全在未提交的服务端树且未接线。误操作或真实故障 → 只能人工 SQL 抢救。

### P3 生产身份链缺失
- `versionCode` 仍为 **20**、`versionName` 仍为 `0.15.0-r06-submission`（功能已跨 10 个 Room 迁移）；`android/app/build.gradle.kts` 中 `signingConfigs` 命中 **0**；`tools/build_pda.ps1` 不注入 `pdaUpdatePublicKey` → 更新页必然报“更新签名公钥未配置”，且“版本必须更高”使同版本包被拒。

### P4 “离线”与验证环境不可复现
- 换电池/重启后未先联网一次即因 `TrustedTime` 校验失败落进停止；后台同步只认 Wi-Fi；只信任系统 CA。
- 服务端 136 个 IT 只有 17 个有环境门禁；多个 IT 硬编码已不存在的 worktree（`D:/acrylic-pda-worktrees/r06-ledger-verification`）与本机 `mysqld.exe` 路径；Android 4 条真实 HTTP 用例长期条件 skip。

### P5 文档与流程反向拖累
- `docs/` 567 文件 / 约 263 万字符；看板当前区 62 行却 **21,988 字符、57 条 bullet、最长单条 1,211 字**，引用 34 个 attempt 与 31 个 SHA256，全部指向被忽略的本地目录。
- 同一事实 4–15 处副本且互相矛盾（README 落后约 5 轮；`android/README.md` 同文件 4 个“当前”；`contracts/IMPLEMENTED_API.md` 一个文件三种契约版本）。
- 最近 60 个提交 **78% 只改文档**；五份治理文档自 09-22 起未提交（本批已入库）。

### P6 环境与卫生
- 34 个分支 / 33 个工作树，其中两个指向同一 SHA；工作树普遍不含当前 `AGENTS.md`。
- 仓库内曾长期存在 `android/app/hs_err_pid*.log` 崩溃现场；无 `.gitattributes` 而 `core.autocrlf=true`（本批已补忽略规则，未加 `.gitattributes`）。
- 根 `gradlew` 只 `include(":demo")`，Android 必须 `-p android`，新接手者第一条命令即失败。

## 2. 不应推翻的部分（本次实测）

- 冻结前工作树的 Android 客户端自洽：可编译，`--rerun-tasks` 全量 **111 XML / 444 项 = 440 通过 / 4 真实 HTTP 条件 skip / 0 失败 0 错误**，`assembleDebug` 逐字节复现同一 APK（SHA256 `A8C60756811C0A0961E44D27203059640EE8E0CE4E3685DAA6F2ED43576EFC9E`，与看板记录逐字一致）。
- Room 17→27 无 destructive fallback，唯一 `DROP TABLE`（17→18）有逐列 `INSERT…SELECT` 与测试；缺口是 6→7、7→8、10→11 与整链 17→27 无测试。
- 无硬编码口令/私钥；私钥在 Keystore/`noBackupFilesDir`；强制 HTTPS、不跟随重定向；更新链验签完整，未发现未验签安装或降级路径。
- 两棵树在 Git 提交层**交集为 0**（自合并基点 `7a30523` 起主树改 55 文件、服务端树改 31 文件，无重叠），同名文件仅 22 个不同且多为单侧新增；未提交层仅 4 个契约文件对撞。

## 3. 建议顺序

1. **合成单一基线**：把服务端树合进主树，解决 4 个契约文件对撞与 `OrderTemporaryCandidate*`“一边有契约无实现、一边有实现”的错配，此后只维护一棵树；其余工作树确认无未提交后归档。
2. **主流程闭环**：注册 `NORMAL_CONFIRM`/`SHORT_APPROVE` handler + POST 路由并解除 `expected` 字段锁；放行 TEMP 归档；给 `RECOVERY_PENDING` 一个受审计恢复入口，或在启动时 fail-fast 拒绝进入该状态。
3. **发布身份链**：`signingConfigs` → `versionCode` 递增 → 注入更新公钥 → 真机走通一次 App 内升级。
4. **文档收敛**：保留 `AGENTS.md`（≤80 行）、`docs/STATUS.md`（≤120 行且 ≤8,000 字符，版本四元组由脚本从 `build.gradle.kts`/`PdaDatabase.kt`/`db/migration`/`openapi.yaml` 生成）、`docs/EVIDENCE.md`（≤180 行验收索引）；其余文档降级为无状态文档。
5. **暂停扩张**：`recovery/` + `temporary/evidence/` + `protocol/` + `command/` 使服务端树从 12.7k 行涨到 21.6k 行（“恢复/证据”占比 23.8%→49.8%），但在主树中一行未落、全部默认关闭、无 bean 无路由；在主流程闭环前标记为“未接线、不进生产”冻结。

## 4. 审计边界

只读审计：未改源码/配置/数据库，未部署，未跑服务端测试与真机/模拟器；服务端结论基于静态代码与 Git/文件事实，未做运行期验证。Android 结论来自四个只读审计与一次 `--rerun-tasks` 实跑；APK 与单测数量为冻结前工作树状态。
