# R09 C2 I3：最终归档 manifest（已生成，待确认后移除）（2026-09-30）

> **裁定依据**：用户 2026-09-30——方案①**归档后移除**；归档**排除 `**/build/**`**；`E2aWorktree.java:15` 的 `E2A_CROSS_ROOT` 默认值**一并改主树**；5 个 handover 缺陷版副本与 attempt-117 诊断块**归档保留、绝不并入**；8 个 `tools/` 脚本按逐行清单执行；**执行前先交付"最终归档 manifest"（文件清单＋哈希）供确认，确认后再移除**。
>
> **本页＝该 manifest 的摘要与确认请求。移除动作尚未执行。**

## 1. manifest 产物（已生成，未提交入库）

| 文件 | 内容 |
| --- | --- |
| `.local/i3-archive/manifest.tsv` | **32,383 行**，每行 `<sha256>\t<bytes>\t<相对路径>`（制表符分隔，UTF-8 无 BOM） |
| `.local/i3-archive/excluded-summary.tsv` | 被排除材料的**规则汇总**（规则、文件数、MB），使排除项不静默消失 |
| `.local/i3-manifest.ps1` | 生成脚本（可复现：同一棵树再跑一次应得同一清单） |

## 2. 归档范围与体积（实测）

| | 文件数 | 体积 |
| --- | --- | --- |
| **纳入归档（清单内）** | **32,383** | **115.9 MB** |
| 排除 | 36,626 | 14,175.6 MB |

**排除明细（`excluded-summary.tsv` 原文）**：

```text
*.log                  1449    3.1
**/private/**          2122    4.2
**/storage/**         10855  199.2
credentials             369    0
**/data/**            18218  13361.3
keys                     16    0
other                    39   29.5
build/**               3558  578.2
```

**为什么这样排除（依据 `AGENTS.md`＋`GIT_WORKFLOW.md` 第 4/53/55 条）**：

- `**/data/**`（13.36 GB，最大项）＝隔离 MySQL 的数据目录；
- `**/private/**`、credentials、keys ＝ 管理员凭据与开发签名私钥 ⇒ **不得归档、不得异地复制**；
- `**/storage/**` ＝ 运行时私有导入/业务文件；
- `build/**` ＝ 可再生构建产物（**用户已裁定排除**）；
- `*.log` ＝ 运行日志（体积小但含本机路径/凭据风险）。

> **必须写清的取舍**：被排除的 14.2 GB **不只是 build 产物**，还包括上述敏感与本地材料。归档体积因此从约 14.3 GB 降到 **115.9 MB**。**若你需要连 `data/` 一起留存，请单独说明**——按现行边界它不在归档范围内，且属"数据库/本机配置"类。
> 被排除的 `data/` 里装的是**隔离 MySQL 实例文件**，不是 attempt 的验收证据；**证据（gradle 日志、TEST-*.xml 的直接输出、marker、real-records.tsv、notes）都在 32,383 个纳入文件里**。

## 3. 必保留项的哈希（可在清单里逐条核对）

| 文件（共享树内相对路径） | SHA256 | 字节 |
| --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/temporary/command/CommandReceiptIntegrity.java` | `27e6f8e2cd4626dfcf037ace83a4c24fc6cc712eb567d7d66c0e75e614cf79f4` | 9778 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverActivation.java` | `c1323c670bf2f61ac984c793f1e4d447fe51f829a9915b4addf1bb0f5bc1ae0f` | 13318 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverCommand.java` | `a9137e243f9af18f8a2871072d37c39e18afdebecc9b7f4de4af09fd54cf80b6` | 8558 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverCommandService.java` | `0052517839cd02cce9d70b6b2a93a0b772d002e3d7c33f06ccc7f5c03858e81b` | 6764 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverReceiptCodec.java` | `a1c5fea6759fd17ae5bb9748b9e3e16dc7a0d5103bfb7ec15d8ded0dc8fa07e0` | 3834 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverStore.java` | `1f850407a8fe7bd4a7891a9017cd53aa01893e8af345f8abf718d24721484f4f` | 10578 |
| `server/src/main/java/com/acrylic/outbound/correction/CorrectionStore.java` | `4fba5eb652b394ae04e8cb746e68cd32c5dd9453fe1ac7dd388d7676d848927c` | 27554 |

**两个"绝不并入"的对象均在清单内、有哈希、可独立取证**：
- attempt-117 诊断块（`CommandReceiptIntegrity.java`，比主树多 5 行 stderr 诊断）；
- 5 个 S38/S39 修复前的 handover 缺陷副本（`disabled_at` 列不存在／`prepared_ticket_sha256` 未 `unhex`／回执 `replay` 未 `codec.verify`）。

## 4. 待确认后要执行的动作（**尚未执行**）

| # | 动作 | 说明 |
| --- | --- | --- |
| A | 依据 `manifest.tsv` 打包归档（仅纳入范围，排除项按 §2 规则剔除），归档置于 `.local/archive/`；打包后**回读校验**（抽样哈希比对 manifest） | 归档＝移除以外的唯一快照 ⇒ **必须先校验再移除** |
| B | 校验通过后 `git worktree remove` 共享树（**不改写 git 历史、不动 `origin`／主树／`main`**） | 回收约 14 GB |
| C | 8 个 `tools/` 脚本按逐行清单改默认值（`cleanup-old-attempt-datadirs.ps1:40`、`e2a-android-link-l4.ps1:28`、`e2a-batch-it.ps1:31`、`e2a-cross-source-verify.ps1:3`（**硬编码非参数**）、`e2a-link-counterproof.ps1:6`（**硬编码非参数**）、`e2a-provision-and-run-106.ps1:29`、`it-coverage.ps1:29`、`r08-provision-and-run.ps1:40`） | 前两个需**参数化**，不止改默认值 |
| D | `server/src/test/java/com/acrylic/outbound/e2averify/E2aWorktree.java:15` 的 `E2A_CROSS_ROOT` 默认值改为主树 | 用户已裁定 |
| E | 上述 C/D 改动跑一次定向回归（E2a 抽样＋R08 抽样＋一级单测），确认脚本可用 | 不跑则改动无证据 |

**请确认**：①manifest 范围（32,383 文件／115.9 MB）与排除规则是否符合预期；②是否同意按 A→E 执行（B 在归档校验通过之后）。**确认前我不做任何移动、删除、归档或脚本改动。**

## 5. 边界声明

- 本页与 manifest 生成**只读**：共享树**未移动、未删除、未归档**；`tools/` 与 `E2aWorktree` **未改**；保持 detached `ea4d166` 与 83 项脏内容原样。
- **不改写 git 历史、不动 `origin`／主树／`main`**；共享树**不并入**；不部署；`localDeletionAllowed=false`。
- manifest 自身是 `.local/` 证据（不入库）；生成脚本同样只读。
