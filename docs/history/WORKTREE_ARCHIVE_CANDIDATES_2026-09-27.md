# 工作树与本地证据归档候选清单（2026-09-27）

> **本页只出清单，未执行任何删除。** 未运行 `git worktree remove/prune`、`git branch -d/-D`、`git clean/reset`，未删除或移动任何目录。所有释放空间的动作都需要业主**逐项批准**；仓库约定 `localDeletionAllowed=false` 继续有效。
>
> 口径：体积为 2026-09-27 00:40 逐目录实测（含 `.git` 与被忽略目录，MB）；工作树数量、每树未提交条目与"是否已并入 `codex/development`"在 2026-09-27 本页生成时刻重新实测。`.local` 列 = 该树内 `.local/` 的体积——**这是判断"能否删"的关键**：Git 能恢复已提交内容，但 `.local` 里的证据只在磁盘上。

## 0. 总量（5 个根，≈113.2 GB）

| 根目录 | 实测 MB | 说明 |
| --- | --- | --- |
| `D:\acrylic-pda-outbound` | 33,539.5 | 主树；**33 个工作树共用它的 `.git` 对象库** |
| `D:\acrylic-pda-worktrees` | 49,769.2 | 31 个注册工作树 |
| `D:\acrylic-snapshots` | 29,958.4 | 唯一 2026-09-26 22:08 时点全量快照 + 新的签名备份目录 |
| `D:\acrylic-entry-verify` | 1,258.6 | **未注册检出**（与 `r06-e2a-migration` 共用 gitdir） |
| `D:\acrylic-outbound-system` | 1,377.0 | **另一个仓库**，不属于本项目 |
| **合计** | **115,902.7（≈113.2 GB）** | |

当前工作树状态：**34 个（主树 1 + 31 + Codex 工具链 2）**；已并入 `codex/development` 且工作区干净 **29**、已并入但有未提交 **5**、**未合并 0**。

## 1. 逐棵工作树

分类口径：
- **可安全删**：已并入基线、工作区 0 项、且 `.local` < 1 MB（Git 完全覆盖，无本地证据）；
- **需要先取证**：已并入但 `.local` 有实质内容（先做 `.local` 的 SHA256 清单并决定去留，再谈删除）；
- **建议保留**：主树、快照、未注册检出、其它仓库、工具链管理的树。

| 路径（`D:\acrylic-pda-worktrees\` 之下） | 分支 | HEAD | 未提交 | 已并入 | 目录MB | `.local`MB | 分类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `datetime-display` | codex/datetime-display | daf4083 | 20 | 是 | 220.8 | 0.1 | **可安全删**（11 唯一文件 + 9 集成改动已入主线，见 [回收记录](UNIQUE_FILE_RESCUE_2026-09-27.md)） |
| `r06-c1-ledger` | codex/r06-c1-ledger | 26b224f | 0 | 是 | 7.5 | 0 | 可安全删 |
| `r06-command-vectors` | codex/r06-command-vectors | 009faa9 | 0 | 是 | 7.4 | 0.1 | 可安全删 |
| `r06-design` | codex/r06-temporary-order-design | 2369392 | 6 | 是 | 4.4 | 0 | **可安全删**（6 设计文档已入主线） |
| `r06-e2-source-model` | codex/r06-e2-source-model | a073c23 | 0 | 是 | 7.5 | 0 | 可安全删 |
| `r06-e2a-contract` | codex/r06-e2a-contract | 5de317b | 0 | 是 | 9.2 | 0 | 可安全删 |
| `r06-e2a-foundation` | codex/r06-e2a-foundation | 319305f | 1 | 是 | 61.9 | 0 | **可安全删**（日志已归档到工作区外） |
| `r06-foundation-protocol` | codex/r06-foundation-protocol | 1c5fca8 | 0 | 是 | 8.0 | 0.1 | 可安全删 |
| `r06-ledger-protocol` | codex/r06-ledger-protocol | fec06b0 | 0 | 是 | 9.1 | 0 | 可安全删 |
| `r06-write-contract` | codex/r06-write-contract | 71a78ba | 0 | 是 | 7.0 | 0 | 可安全删 |
| `r07-design` | codex/r07-handover-correction-design | 2369392 | 13 | 是 | 4.4 | 0 | **可安全删**（13 设计文档已入主线） |
| `second-wave-integration` | codex/second-wave-integration | 47dc8f2 | 0 | 是 | 216.5 | 0.1 | 可安全删 |
| `third-wave-integration` | codex/third-wave-integration | 5c06875 | 0 | 是 | 479.2 | 0.8 | 可安全删 |
| **小计（可安全删 13 棵）** | | | | | **1,042.9** | ~1.2 | 仅释放 ≈1.0 GB |
| `r06-confirmation` | codex/r06-confirmation | 9fa0059 | 0 | 是 | 204.2 | 1.1 | 需要先取证（`.local` 1.1 MB） |
| `r06-duplicate-candidates` | codex/r06-duplicate-candidates | df1a616 | 0 | 是 | 497.0 | 17.4 | 需要先取证 |
| `r06-e2a-protocol` | codex/r06-e2a-protocol | 79cad44 | 1 | 是 | 134.2 | 72.8 | **需要先取证**（`.class` 已归档；`.local` 72.8 MB 未核） |
| `r06-identity-locks` | codex/r06-identity-locks | 45a1c14 | 0 | 是 | 349.3 | 27.3 | 需要先取证 |
| `r06-ledger-runtime` | codex/r06-ledger-runtime | 0489036 | 0 | 是 | 522.6 | 199.5 | 需要先取证 |
| `r06-ledger-verification` | codex/r06-ledger-verification | b3f4265 | 0 | 是 | 278.8 | 217.8 | 需要先取证 |
| `r06-source-evidence` | codex/r06-source-evidence | 8cb9e2a | 0 | 是 | 533.4 | 213.0 | 需要先取证 |
| `r06-source-foundation` | codex/r06-source-foundation | 04a679b | 0 | 是 | 573.7 | 251.3 | 需要先取证 |
| `r06-foundation-verification` | codex/r06-foundation-verification | 8bdf1b8 | 0 | 是 | 386.0 | 323.9 | 需要先取证 |
| `first-wave-integration` | codex/first-wave-integration | 9058137 | 0 | 是 | 1,618.7 | 355.9 | 需要先取证 |
| `r06-l2-locks` | codex/r06-l2-locks | b530784 | 0 | 是 | 947.5 | 391.3 | 需要先取证 |
| `fourth-wave-integration` | codex/fourth-wave-integration | c393d2d | 0 | 是 | 928.1 | 216.5 | 需要先取证 |
| `fifth-wave-integration` | codex/fifth-wave-integration | f6e5af2 | 0 | 是 | 686.0 | 206.4 | 需要先取证 |
| `sixth-wave-integration` | codex/sixth-wave-integration | 0b1094e | 0 | 是 | 1,900.7 | 1,185.5 | **需要先取证**（提交已并入；`.local` 1.19 GB） |
| `r08-export` | codex/r08-export-rendering | f051535 | 0 | 是 | 3,509.1 | 1,772.3 | **需要先取证**（提交已并入；`.local` 1.77 GB） |
| `r09-ops` | codex/r09-ops-foundation | 584449f | 0 | 是 | 2,724.0 | 2,683.4 | 需要先取证 |
| `r09-v33-recovery` | codex/r09-v33-recovery | 94305a7 | 0 | 是 | 2,974.7 | 2,649.4 | **需要先取证**（提交已并入；`.local` 2.65 GB） |
| `r06-e2a-migration` | codex/r06-e2a-migration | d16836b | 0 | 是 | **29,958.4** | **29,339.7** | **需要先取证 + 单独决策**（见 §3） |
| **小计（需先取证 18 棵）** | | | | | **48,726.3** | ~44,755 | 潜在释放 ≈47.6 GB |

### 说明：三棵"曾经未合并"的树

`r08-export`、`r09-v33-recovery`、`sixth-wave-integration` 的提交已在 2026-09-27 合入 `codex/development`（`8bb3e57` / `1e98e44` / `f196887`，见[合并记录](MERGE_BATCH_2026-09-27.md)），所以**分支本身已无独有提交**；阻碍删除的只剩各自的 `.local`（合计 ≈5.6 GB）。

### 工作树之外的两棵（Codex 工具链管理）

| 路径 | 分支 | 未提交 | 已并入 | 分类 |
| --- | --- | --- | --- | --- |
| `C:\Users\qq113\.codex\worktrees\password-min-eight\acrylic-pda-outbound` | codex/password-min-eight | 0 | 是 | 建议保留（工具链所有，非本项目目录） |
| `C:\Users\qq113\.codex\worktrees\readable-order-number\acrylic-pda-outbound` | codex/readable-order-number | 0 | 是 | 建议保留（同上） |

## 2. 两个未注册检出与其它仓库

| 路径 | 体积 | 状态 | 唯一证据在哪 | 删了能否恢复 | 分类 |
| --- | --- | --- | --- | --- | --- |
| `D:\acrylic-entry-verify` | 1,258.6 MB（`.local` 926.6） | `.git` 指向 `D:\acrylic-pda-outbound\.git\worktrees\r06-e2a-migration`，与 `r06-e2a-migration` **共用 gitdir**；726 个缺失、11 个已修改、2 个未跟踪 | 11 个已修改文件的差异 + 2 个未跟踪文件**已归档**到 `E:\acrylic-archive-unique-20260927\patches\entry-verify-tracked-modifications.patch` 与 `unregistered-entry-verify\`；726 个缺失文件 **726/726 可从 Git 取回**（已逐个校验） | 代码可恢复；`.local` 926.6 MB 未核 | **需要先取证**（`.local`） |
| `D:\acrylic-snapshots\r06-e2a-migration-full-20260926-2215` | 29,958.4 MB（`workspace\.local` 29,327.9） | 保存 2026-09-26 22:08 时刻、`sourceHead=568683c1` + 333 条本地改动的**字节状态**，含逐文件 SHA256 manifest | 该时点状态的**唯一副本**（当前工作树已前进多个提交） | **不可恢复** | **建议保留**（除非业主明确不再需要该时点证据） |
| `D:\acrylic-snapshots\pda-signing-backup-20260927` | ~0 MB | 2026-09-27 新建的签名备份（与本机 `E:\acrylic-pda-signing-backup-20260927` 同源） | 正式签名身份的备份之一 | 可从另一份备份重建 | 建议保留（见 [PDA_SIGNING_GUIDE](../PDA_SIGNING_GUIDE.md)） |
| `D:\acrylic-outbound-system` | 1,377.0 MB | **另一个仓库**（`git@github.com:1131657662/acrylic-outbound-system.git`，master `3ac945f`，5 条未提交） | 该仓库自身的工作区 | 不属于本项目范围 | **建议保留**（不属本项目清理范围） |

## 3. 主树内部（`D:\acrylic-pda-outbound`，33,539.5 MB）

| 路径 | MB | 内容 | 分类 |
| --- | --- | --- | --- |
| `.local` | 21,404.0 | 见下表 | 逐项判断 |
| `demo-artifacts` | 10,271.0 | M71 Demo 的 APK 与现场证据（已验收的旧 Demo 范围） | **需要先取证**（确认 Releases/异地已有备份后再谈） |
| `outputs` | 770.0 | 12 个历史验收产物目录（r02/r05-v28/pda/…/r06-product） | 需要先取证 |
| `.git` | 12.8 | 33 个工作树共用的对象库 | **绝不可删** |
| 其余（源码/构建缓存等） | ~1,082 | `server` 284.5、`report-rendering` 278.3、`android` 237.2、`web` 169.9、`.gradle` 8.9、`.tools` 8.8、`docs` 7.6 等 | 保留（工作所需） |

`.local` 内部最大项（逐目录实测）：

| 子目录 | MB | 内容 | 分类 |
| --- | --- | --- | --- |
| `pda-download-avd` / `pda-upgrade-avd` / `pda-browser-install-avd` | 5,780.3 / 5,736.1 / 5,630.3 | 三个 Android 模拟器镜像（合计 **17.1 GB**） | **需要先取证**（确认不再需要这些 AVD；可重建但有成本） |
| `mysql` | 2,151.6 | 工作区 MySQL 数据目录（13316 实例；该实例当前未监听） | **建议保留**（`tools/start_development_db.py` 的工作区数据） |
| `server-runtime` | 385.9 | 历史运行时 JAR 副本 | 需要先取证（可从构建重建） |
| `brand-verification` | 234.7 | 品牌/图标核对产物 | 需要先取证 |
| `private-acrylic_outbound_test` | 151.8 | 测试存储目录（`OUTBOUND_STORAGE`） | 需要先取证 |
| `tmp` / `pda-release-20260923` / `pda-update-channel` / `r06d-submit-20260920` | 53.7 / 68.0 / 63.3 / 56.8 | 临时与历史发布/更新产物 | 需要先取证 |
| `pda-signing` / `r08-signing` / `pda-update-signing` | ~0 | **正式签名材料与口令、更新通道密钥、开发签名密钥** | **绝不可删**（见 [PDA_SIGNING_GUIDE](../PDA_SIGNING_GUIDE.md)） |

## 4. 汇总与释放预估

| 分类 | 棵/项数 | 体积 | 释放前提 |
| --- | --- | --- | --- |
| 可安全删（工作树） | 13 棵 | 1,042.9 MB | 业主批准即可；Git 全覆盖，无本地证据 |
| 需要先取证（工作树） | 18 棵 | 48,726.3 MB（`.local` ≈44.8 GB） | 先对各自 `.local` 出 SHA256 清单并决定去留 |
| 需要先取证（未注册检出） | 1 项 | 1,258.6 MB | 同上（11+2 个唯一文件已归档） |
| 建议保留 | 主树、快照、2 棵工具链树、签名目录、另一仓库 | ≈64 GB+ | 不可删或另有归属 |
| 合计 | | **≈113.2 GB** | 单次可释放上限约 **47.6 GB**（全部逐项批准后），其中最大单项是 `r06-e2a-migration` 的 29.3 GB `.local` |

## 5. 真要动手时的顺序与红线

1. **先取证再删**：对每一棵"需要先取证"的树，先只读生成 `.local` 的 SHA256 清单（含文件数/总字节），确认没有任何"唯一且未入库"的证据，再单独批准删除。
2. **先解除共享 gitdir**：`r06-e2a-migration` 的 gitdir 被 `D:\acrylic-entry-verify` 与 `D:\acrylic-snapshots\...\workspace` 两处引用；在处置这两个检出之前，**不得**删除该树的管理目录或主树 `.git`。
3. **签名材料与会话内的活跃库不动**：`.local/pda-signing`、`.local/pda-update-signing`、`.local/r08-signing` 永不删除；`mysqld`（PID 4412/5448）与构建守护进程未确认归属前不停。
4. **不对快照做任何删除**：它是 22:08 时点状态的唯一字节级证据。
5. **未经批准不执行**：`git worktree remove/prune`、`git branch -d/-D`、`git clean/reset/stash`、删除或移动任何上述目录。

## 6. 本页边界

只读清点：体积为逐目录实测，状态为本页生成时刻实测；未做跨树逐文件内容比对（除已完成的 32 个唯一文件与 726 个缺失文件校验外），因此"`.local` 内是否含唯一证据"在逐树出具 SHA256 清单前均为**待核**，不能凭本页直接删。
