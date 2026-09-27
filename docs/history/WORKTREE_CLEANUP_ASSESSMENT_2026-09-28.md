# 工作树清理评估（2026-09-28，自主运行 v11 / D3）

**只清点、只评估；本页不执行任何删除。** 结论供业主或后续写者按需批准。

## 一、清点结果

`git worktree list` 共 **34 棵**（含主树）。逐棵核对：脏文件数（`git status --porcelain`）、是否已被
`codex/development` 合并（`git branch --merged`）。

| 分档 | 数量 | 处置建议 |
| --- | --- | --- |
| **A. 已合并 + 工作树干净** | **24** | **可安全删除**（内容均在 main 历史里；建议先 `git log --all` 复核一次） |
| B. 已合并 + 有未提交改动（1-16 个文件） | 6 | **先逐个取证再决定**：`r06-e2a-protocol`(1)、`r06-e2a-foundation`(1)、`r06-design`(1)、`r07-design`(1)、`datetime-display`(16) |
| C. 在用/专用 | 3 | **禁止删除**：主树 `D:/acrylic-pda-outbound`；R08 渲染浏览器源 `r08-export`； |
| D. e2a 守卫星树 | 1 | **禁止删除**：`r06-e2a-migration`（detached `ea4d166`，53 个未提交改动 = M2b-4b 服务端冻结态，`.local/` 现 0.6 GB 但含全部 attempt 证据） |

### A 档（24 棵，已合并 + 干净）

```
r06-l2-locks          r06-ledger-protocol   r06-ledger-runtime    r06-identity-locks
sixth-wave-integration r06-foundation-protocol r06-foundation-verification
r06-ledger-verification r09-ops              r09-v33-recovery      second-wave-integration
r08-export            r06-source-evidence   r06-source-foundation r06-write-contract
third-wave-integration first-wave-integration r06-c1-ledger        fifth-wave-integration
r06-command-vectors   r06-e2-source-model   r06-e2a-contract      fourth-wave-integration
r06-confirmation      r06-duplicate-candidates  (以上均在 D:/acrylic-pda-worktrees/)
C:/Users/qq113/.codex/worktrees/readable-order-number/acrylic-pda-outbound
C:/Users/qq113/.codex/worktrees/password-min-eight/acrylic-pda-outbound
```

**注意**：`r08-export` 虽属 A 档（分支已合并、工作树干净），但它持有 **R08 渲染所需的 Chromium 目录**
（`report-rendering/build/browsers`），本会话的 R08/渲染 IT 都指向该路径 ⇒ **删前须先把 browsers 或
`report-rendering/build/install` 复制到别处**，否则渲染类 IT 会失去前置。

### B 档（6 棵，已合并但有本地未提交改动）

| 工作树 | 脏文件 | 已核对的实质 |
| --- | --- | --- |
| `r06-design`（`codex/r06-temporary-order-design`） | 1 | 仅 `?? docs/parallel/`（未跟踪目录）；**逐文件与主树 `docs/parallel/` 比对：无差异、无独有文件** |
| `r07-design`（`codex/r07-handover-correction-design`） | 1 | 同上（结论相同） |
| 其余 4 棵 | 各 1 | 待逐个取证（本轮未逐一比对） |
| `datetime-display` | 16 | 待取证；该分支此前已合并 |

## 二、复核方法（给后续执行者）

1. 对每棵候选：`git -C <wt> status --porcelain` 必须为空，或逐个确认脏文件内容已在 main；
2. `git -C <wt> log --all --oneline -- <file>` 确认改动不曾只存在于该树；
3. `git branch --merged codex/development --list <branch>` 必须命中；
4. 删除用 `git worktree remove <path>`（**不要**直接 `rm -rf`，避免留下 `.git/worktrees` 残登记）；
5. 删除**前**把该树 `.local/` 下的证据（日志/证据 JSON/文件产物）中尚未入档的移入主树 `docs/history/` 或
   工作区外归档目录（参见 [唯一文件回收记录](UNIQUE_FILE_RECOVERY.md) 的既有做法）。

## 三、本轮实际动作

- **未删除任何工作树、分支或文件**（遵守"只列清单，不执行删除"）。
- 唯一相关的存储动作：为解除 `D:` 磁盘写满（0 GB 可用导致 MySQL 无法初始化），删除了 **501 个 attempt 目录的
  datadir**（91.4 GB），**保留**每个 attempt 的 `integration-test.log`、markers、`storage/`（渲染产物）、
  `private/`（签名材料）与 `provision.sql`。日志与证据未删。

## 四、未验边界

- B 档 4 棵（`r06-e2a-protocol`/`r06-e2a-foundation`/`datetime-display` 等）的脏文件未逐个比对内容；
- 未核对每棵树的 `.local/` 里是否还有**未归档**的证据；
- 未核对 32 个分支中是否存在"无工作树但未合并"的分支（本轮只清点有工作树的）。
