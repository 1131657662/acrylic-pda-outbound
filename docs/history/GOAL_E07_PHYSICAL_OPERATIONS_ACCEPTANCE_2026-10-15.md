# E07 原检查点实物核验与运维批准组合开发验收（2026-10-15）

本批收口原检查点精确重建、非空升级后的实物核验、独立 JVM 原请求重放及运维批准事务切片。仅为开发备份；E07 L2、D14/D15 整体、E06 L2、D18 L3 和总目标仍未完成。合成主管、实物声明、运维身份与同机恢复不代表原 PDA、现场实物或外部保管事实。

## 输入与影响

树 `D:/acrylic-pda-outbound`，分支 `codex/development`；实际运行 HEAD/远端均为 `8d97743be419606f9a744b06ef0e66d9577689e6`。本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 是源码、库、看板及索引唯一写者；清理聊天 round3 已 complete-released，接续时实查 idle，无并行写者或重型任务。继承 Android/UI/文档/工具及私有请求保持，未随本批暂存。

证据根分别为 `.local/goal-20261015/e07-cross611/` 和 `e07-cross612/`。每批 `run-input.json` 固定 5,265 个文件及运行方法、HEAD/远端/写权；`source-before.json` 固定来源 `.local/e2a-cross-20260925-1233/` 的 393 个文件。两批独立 Python 实际重算 SHA/长度均不变。完整改动、补丁、进程/监听/磁盘见 612 的 `finalize-input.json`、`finalize-status.bin`、`finalize.patch`、`environment-finalize.json`。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径与 SHA） |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aReceivedCheckpointIT.java` | 两个已有原检查点 IT 入口 | 611 实物、第二 JVM 重放；612 运维；非空升级、权限/回滚/并发、实际消费者及完整保全 | 611 `impact-plan.json`；仅在精确原检查点核对后增加两次 `E2aReceivedCheckpointUpgrade.verify` |
| 报告、看板及索引 | 验收入口与断点 | 旧结论归档、事实生成、全部改动与暂存核对 | 本报告、612 `precommit-review.json`、`post-commit.json` |

自主决定：沿已验证的严格升级器升级后，再运行既有实物与运维组合；不改产品、迁移、API或默认开关。旧实物消费者的原字段比较算法保留，改为消费明确的 `history-upgrade-after.json`；Python 另独立核对原 146 表到升级后的精确效果。未以放宽原字段断言解决升级兼容。成员消费者复用 610 的完整 137 表版本；原旧脚本保留。

612 `reuse-inputs.json` 逐路径列出 3,725 个未变的 server/Android/Web/tools 输入及前后 SHA，唯一差异就是上述两行 IT 接线。610 的九类、54 项回归原 XML 已重新核对计数与 SHA，作为输入未变的复用证据，不冒充本次重跑。Android/Web/Python 产品及公开路由未变，本切片实际执行应用服务和 MySQL；不声称新增成功 HTTP 业务入口或新增 Chrome/Android 场景。

## 实际执行

分配前实查 33 worktree 无同号路径、19330/19434 无监听。611 与 612 顺序使用端口，独立 datadir/schema 分别为 `.local/e2a-cross-20260925-611/`、`acrylic_e2a_cross_20260925_611` 及对应 612；旧失败库和已保全停机库未复用。专属临时目录 `.local/t611`、`.local/t612`，仅子进程环境生效。

1. **611 实物**：`powershell -NoProfile -File .local/goal-20261015/e07-cross611/run.ps1`，`approvesPhysicalScopeAcrossOriginalCheckpoint` 原 XML 1/0/0/0，test/runner 退出 0。6 项无权拒绝、末审计 SQL 回滚、未知范围回滚、2 项尾事务回滚、8 项语义反例及并发后原键恢复通过。业务结果见 `release-physical-review-checks.json`。
2. **611 独立 JVM**：同一仍活动且成功的隔离库在最终保全前运行 `restart.ps1`，`replaysPhysicalApprovalInNewJvm` 原 XML 1/0/0/0、退出 0。sourcePid 8872、replayPid 4572，实际 JVM 不同；批准原字节、数据库及文件保持，放行仍 false。两阶段原日志/XML/exit 分别保存在 `phase1/`、`phase2/`。
3. **612 运维**：`powershell -NoProfile -File .local/goal-20261015/e07-cross612/run.ps1`，`verifiesOperationsApprovalAcrossOriginalCheckpoint` 原 XML 1/0/0/0，test/runner 退出 0。11 字段、5 来源绑定、跨仓拒绝、事务预检、beforeCommit 批准原件与信任根尾变回滚通过；运维核验前后数据库完全相同，批准原件和身份 SHA 另行重算。见 `operations-approval-checks.json`。
4. **非空升级**：两批均核对原 146 表原字段、19 私有文件、迁移历史前缀守恒；33 新增业务表空；四项权限及角色精确追加，capture revision 从原 submission/assignment 精确回填。未改原快照。版本事实只见看板生成块。
5. **Node 实物/编号**：两批分别实际运行 `received-physical-consumer.cjs` 和 `received-physical-number-consumer.cjs`。每批实物消费 23 输入、137 表、3 原签名命令、精确批准效果及 8 负例通过；编号消费者实际消费原生 mysqlbinlog 的 8 输入、分配 1 次及全局 counter 精确值通过，输入全部不变。
6. **Node 运维/Python**：612 实际运行 `received-operations-consumer.cjs 612 operations-consumer01`，14 输入、规范 JSON/RSA/固定身份/完整来源与批准绑定及 8 负例通过。两批 `verify-independent.py` 实际重算冻结输入、非空升级、XML、全表/dump/storage 和停机文件；612 同时再次核对 611 停机文件，无重启。结果分别见 `independent-verification.json`。消费者源码、原输入清单、结果均保留。

作者与自审是同一助手；Node/Python 为独立算法消费者，不是独立人员审查。自审确认只改测试接线，原检查点验证仍先于升级；严格原字段/重复行/新表为空断言、原键/异文/权限/尾事务检查及生产默认均保持。

## 原诊断与保全

611 首次回执脚本将 PowerShell UTF-16 日志按 ASCII 原字节检查，断言失败；`phase-receipt-attempt1.json` 保留。按 BOM 解码后从真实 XML/log/exit 与冻结输入生成可追溯 profile 回执，补验通过，未伪造业务结果。首阶段嵌套 PowerShell 因 MySQL 继承句柄延后结束；业务 XML/exit 已完成并先复制，最终停机后 runner 正常退出 0。第二阶段 runner JSON 的 testExit 被 PowerShell 序列化为带 value/PSPath 的对象，原 exit/XML 正常，原 JSON 不覆盖。612 改为当前 PowerShell 直接调用 runner；这些均是运行器/回读问题，不计产品失败或删除旧证据。

两库各 181 表，JDBC/dump/storage 前后守恒，认证停机、19330/19434 零监听；停机 hash/长度分别 414/416 个文件一致。dump SHA256：

- 611：`af68fd8e56e354df1c709c31c29b2094cbfc7af063764ac9541bf4e8ba3b614b`。
- 612：`665d40a2dd5778c3adb03548b5e4b13ed33c5b2f04ea610ead747f36a4fb4e34`。

命令为各批 `python preserve.py <编号>`；认证身份、全部 schema 清单、dump、原 SQL/日志/XML/binlog/datadir、存储与停机 hash 均在各批 `preserve/` 或隔离目录保留，禁止重启/复用。收尾 C/D 可用 78,877,573,120 /67,424,559,104 bytes，无项目重型任务。共享 MySQL 仅观察。

生产决定 handler/POST/放行/证据写入/关闭能力、TEMP 归档边界及 `localDeletionAllowed=false` 保持。未接原库/现网、未合 main、未发布部署或使用正式签名。阶段提交/远端回执见 612 `post-commit.json`；本机证据尚不是异地备份。

下一项先核对已验撤回/更正报告和对应输入 SHA，定位跨检查点事实恢复及旧资格不复活、成功不重记账的直接缺口，只补受影响组合；随后继续 E07、E08/E09 与剩余 D/O/U。旧失败计数、设备/Chrome 阻断与现场未验继续保留；本切片不计 E07 L2、D18 L3 或总目标通过。
