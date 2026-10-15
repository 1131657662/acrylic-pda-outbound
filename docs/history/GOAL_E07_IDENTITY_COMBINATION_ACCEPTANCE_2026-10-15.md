# E07 更正身份与两阶段撤回组合开发验收（2026-10-15）

本批在新的隔离 MySQL 上复核身份保护迁移后的真实更正、主管审批及两阶段撤回入口，并独立消费原签名回执、逐行账务和保全结果。620 通过；619 在初始化阶段因任务临时目录不存在而退出，未发生业务写入。仅为 E07 受影响组合开发备份，不计 E07 L2、D14/D15 整体、D18 L3 或总目标完成。合成身份、开发签名和合成初始出库不代表原 PDA、原身份或现场事实。

## 输入与范围

工作树固定 `D:/acrylic-pda-outbound`，分支 `codex/development`，运行 HEAD/远端均为 `ffe2439c8a7ac57a5bfaaf9b1a2b307980de83ea`。清理聊天于 21:22 再次确认 complete-released、无在途写入；当前聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 为唯一写者，无子代理。所有继承 tracked/untracked 输入均保护，未暂存或修改本批以外的源码、私有请求及文档。旧看板先保存为 `.local/goal-20261015/e07-combo01/board-before-combo.md`。

| 改动路径或复核入口 | 影响层 | 必跑清单 | 输入及复用依据 |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionIdentityWithdrawalIT.java`（未修改） | 非空升级后的身份保护与真实 HTTP/MySQL | 创建、审批、撤回、原申请人确认，身份分离、权限、原键和异文拒绝 | SHA256 `9677ce05246c519dc15f3dc77af4da6dbc2afd23ba148510ad735cf8a05750b6` |
| `server/src/test/java/com/acrylic/outbound/inventory/SupervisorWithdrawalChecks.java`（未修改） | 实际角色/仓库权限、原回执及账务冲正 | 四个拒绝、两阶段间事实不变、原键回放和异文 409、原出库不变 | SHA256 `23e0bf9312fe81243e53e95391b12d26cd664f741c3984f36eebe3c3e0962353` |
| `.local/goal-20261015/e07-combo01/consume620.py` | 只读独立证据消费者 | 全输入、XML、180 表、RSA、数据库二进制 SHA、角色分离、冲正、dump 和停机文件 | 源 SHA256 `17c91c37f8bb7a4fd5093a15e55aaab3814f062cfcaa01625147112633559885` |
| 本报告、两个验收索引、看板 | 证据追溯 | 链接、事实块、当前区上限、明确路径暂存审查 | 不改产品行为；不重复无关 Android/Web 链 |

`input620.json` 绑定 5,277 项 tracked/untracked 文件的长度和 SHA256，清单 SHA256 为 `025770629a3704914dd1d9d328d86f88b3e73ed50fd7f462373d62ccbcbfe363`。`resume-input.json`、补丁、Git 状态和 worktree 清单保存接续时输入；消费者在文档修改前逐项核验全部输入不变。五检查点恢复继续按[更正报告](GOAL_E07_CORRECTION_CHECKPOINT_ACCEPTANCE_2026-10-15.md)及该页 SHA/输入清单复用，本批不重复或扩大其范围。

## 实际运行

分配前复核 33 个 worktree、19330/19434 零监听、无项目 Java/Gradle/模拟器重型任务，620/datadir/任务临时目录未被占用。C/D 可用字节为 `83418169344`/`65564585984`，预估本轮增长 3 GiB。运行器先创建 `.local/t620`，仅对子进程设置 TEMP/TMP/Java 临时目录。

```text
powershell -NoProfile -File .local/goal-20261015/e07-combo01/run620.ps1
python .local/goal-20261015/e07-combo01/preserve.py 620
python .local/goal-20261015/e07-combo01/consume620.py
```

620 身份为 schema `acrylic_e2a_cross_20260925_620`、用户 `e2a_cross_20260925_620@127.0.0.1`、端口 19330 和 `.local/e2a-cross-20260925-620/data`。原始命令、进程/身份及环境证据在 `allocation620.json`、`run620.ps1` 和保全目录；凭据只存本机忽略目录。集成 XML 为 1 test / 0 failures / 0 errors / 0 skipped，runner/test 退出码均为 0；初始非空资料经实际迁移后执行业务。

真实回执状态依次为 `SUBMITTED → EFFECTIVE → WITHDRAW_PENDING → WITHDRAWN`。创建/最终确认由原申请人执行，审批/发起撤回由原主管执行；申请人不能发起、另一主管不能发起、主管不能确认、另一申请人不能确认，四次均返回 `CORRECTION_PERMISSION_DENIED` 且状态不变。待确认阶段账务及净值仍保持更正生效值；最终确认追加 `WITHDRAWAL_RESTORE` 反向记录，逐行关联原 correction line/dispatch，数量和重量相抵。原 `dispatch_record`、`inventory_ledger` 等 source 快照前后相等。两次原键回放逐字节相等，同键异文均为 409，未重复记账。

独立 Python 实际消费 5,277 项输入、XML、全部 180 表、4 份 RSA 回执、4 项权限拒绝及 375 个实例文件；同时核对数据库原 response/canonical command 的原始字节和二进制 SHA256。`consumer620-result.json` 为 PASS，结果 SHA256 `8da5b2a3839fa343cc5c6ae97f8a6266945fcd69c33e7dc79bf5b0de07f0c98b`。这是作者自审下的独立算法，不等于独立人员审查。

11 个预分配 schema 中主 schema 有 180 表，其余为空。JDBC/dump/storage 前后守恒，dump SHA256 为 `8fd897e3cd78ff40fc7f458a791edc693f4910d9b36e3819ab4492679316fbf5`。认证 shutdown 成功，19330/19434 零监听；375 文件的长度/哈希及原库、dump、binlog、XML、日志、签名和私有身份均保留。接续复核无项目重型任务，C/D 可用字节为 `83411607552`/`65324998656`，详见 `ownership-resume.json`；后续提交环境另留快照。

## 失败和未验范围

619 的 `RUNNER_MYSQL_TEMP_DIRECTORY_MISSING` 与 614 同根因，计数不清零；保留失败库目录、initialize 日志及 `failure619.json`，不复用。620 先创建任务目录后重新分配运行，通过后保全。消费者首次把 32 字节 `response_sha256` 当 UTF-8 文本而失败，原脚本和 `consumer620.log` 保留；修正为 digest 与原始字节比较，并增加 canonical command 二进制 SHA 核对，`consumer620-binary-fix.log` 记录退出 0。该失败只发生于离线读证据，没有数据库或产品写入。

本批未修改生产开关、迁移、产品 Java、Android、Web 或契约。七类生产能力、TEMP 生产归档和 `localDeletionAllowed=false` 保持原边界；未接原库/现网、未合 main、未发布部署或使用正式签名。取消/缺更正围栏、E07 全部组合、Android/Chrome/原模拟器、外置介质实际保管回取及最终候选 L3 尚未由本批证明。总目标保持 active，继续这些直接缺口及不依赖它们的 E08/E09、D/O/U 工作。
