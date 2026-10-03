# 自主进度 2026-10-03

## 子项状态

| 子项 | 状态 | SHA / 证据 |
|---|---|---|
| P-R06-01 拒绝后可信停止、逐包卸货核验、释放占用 | 已实现并推送开发分支 | `d8b1ac9`；`origin/codex/development` |
| 严格主管拒绝码核验 | 已实现并回归 | 同一 SHA；字符串包含伪造反例已覆盖 |

当前提交属于开发分支阶段备份。没有合 main、标签、Release、部署、原库或现网操作。

## 自主决策记录

1. 逐包动作保留 `RELEASE` 与 `QUARANTINE`；历史 `PHYSICAL_DISPOSED` 输入归一为隔离动作，避免旧命令失效。
2. 释放只删除同时匹配仓库、来源 scope、证据集、dispatch cycle 且仍为 `SOURCE_RESERVED` 的占用；隔离继续写入处置关联。两者都在同一事务内记录明细并更新条码变化。
3. 处置前置要求提交状态为拒绝、拒绝原因数组中存在精确的 `SUPERVISOR_REJECTED` code、停止验证结果完整、证据摘要和版本一致；不再接受任意文本包含。
4. 保持生产命令 gate 和所有生产默认 false；只在隔离测试中启用入口。隔离 MySQL 与 Web 测试保留原库快照和失败资料，不删除 `.local`。

## 当次验证证据

- 定向处置/协议/回执测试：26 项通过，0 失败、0 错误、0 跳过；证据：`.local/autonomous-20261003/disposition-release-targeted-2/`。
- 完整离线服务端回归：2990 项通过，0 失败、0 错误、1 个既有条件跳过；证据：`.local/autonomous-20261003/physical-release-full-green-2/`。
- 隔离真实 MySQL 释放链：1 项通过，0 失败、0 错误、0 跳过；证据：`.local/e2a-cross-20260925-902/`。
- 隔离真实 Web 会话释放链：1 项通过，0 失败、0 错误、0 跳过；证据：`.local/e2a-cross-20260925-903/`。
- `tools/version_facts.ps1 -Check` 通过；生成事实块位于看板当前区。
- 首次定向编译因重写文件带 BOM 失败，日志保留于 `.local/autonomous-20261003/disposition-release-targeted/`；无业务测试失败。

## 仍未解决与限制

- 隔离测试通过的是合成停止验证夹具和测试主管身份；尚未证明真实 PDA、原身份/Keystore、物理卸货回执、断电恢复、长时间运行、现场审批或生产入口。
- mapping3+ 仍保持 fail-closed；R1 白名单、跨来源/高轮次、HEADER 实际恢复、历史撤回与 PHYSICAL_RETURN 周期策略继续单列。
- 工作树仍保留先前会话的未提交/未跟踪资料；完整清单见 `.local/autonomous-20261003/checkpoint-status.txt`，本次提交只包含处置释放相关路径。

## 环境与接续

- 分支 `codex/development`，实现提交 `d8b1ac9`、当前断点提交 `d52f38c` 均已推送；当前断点事实以看板生成事实块为准。
- 隔离实例 902、903 已完成认证停机，失败证据未删除；系统既有监听未操作。
- D 盘可用空间高于 20 GiB；磁盘快照见 `.local/autonomous-20261003/checkpoint-disk.json`。
- 下一步：接续 R06/R07 剩余功能，优先补真实停止链所需的原始凭据/回执边界；继续保持生产 gate 关闭。
## 本轮接续：R07 PHYSICAL_RETURN 周期重开（2026-10-03）

### 子项状态

| 子项 | 状态 | SHA / 证据 |
|---|---|---|
| R07 PHYSICAL_RETURN 受控周期重开 | 已实现并推送开发分支 | `b7809c5`；定向 XML 与完整回归见 `.local/autonomous-20261003/physical-return-current-tree-regression/`；真实隔离 HTTP `.local/e2a-cross-20260925-906/` |
| R07-P08-B 撤销确认后的占用回退 | 审计未完成，保持未实现 | `R07_P08_B_OCCUPANCY_AUDIT_2026-10-03.md`；需要业务政策与占用流转模型 |

### 自主决策记录

5. 依据 V2.3 §5.7 将 `PHYSICAL_RETURN` 与错误出库统一视为受控周期重开；不等待另行确认，因为设计已给出真实退货与错误出库同样推进周期的语义。
6. 将真实 HTTP 检查参数化为 `DISPATCH_CANCELLATION` 和 `PHYSICAL_RETURN`，复用同一证据结构，避免第二套夹具掩盖行为差异；两种类型都必须证明快照归零、占用删除、唯一重开、重复拒绝和 `CYCLE_REOPENED`。
7. 首轮隔离 HTTP 904/905 因渲染器未配置失败，保留原始日志和数据库；完成主树 report-rendering 安装与 Chromium 后，906 在相同入口通过。失败轮不计完成。
8. 发现撤销确认只反向账务且没有占用流转历史后，拒绝猜测旧周期/新周期恢复政策，保留 P08-B 缺口和审计报告。
9. 在完整离线回归通过后，只暂存本轮 8 个实现/测试/审计文件，提交 `b7809c5` 并推送 `origin/codex/development`；未把同工作树的遗留资料带入提交。

### 验证与证据索引

- 定向 `CorrectionCycleReopeningTest`：3 项通过，0 失败、0 错误、0 跳过。
- 真实隔离 `E2aCorrectionAccountingHttpIT`：1/0/0/0；覆盖取消派发和真实退货；attempt `.local/e2a-cross-20260925-906/`，认证停机和 `test-started/test-completed` 均存在。
- 完整服务端离线 `test`：2991 项，0 失败、0 错误；`SourceEvidenceIntakeFilesTest.symbolicLinkTargetCannotBeAccepted` 为既有条件跳过。
- 渲染依赖由 `report-rendering` 的 `installDist` 与 `installChromium` 生成；本轮产品提交不含迁移、APK、接口契约或生产默认开关变化，未接原库/现网。
- 失败证据保留：`.local/e2a-cross-20260925-904/`、`.local/e2a-cross-20260925-905/`；904/905 失败原因为 `EXPORT_UNAVAILABLE`，不是更正周期断言。

### 仍未解决与环境

P08-B 占用回退政策与模型、mapping3+、R1 白名单和历史更正边界仍未完成；现场 PDA/原身份/Keystore、真实退货实物、断电恢复和生产审批未验。19330/19434/19435/19436 已释放，系统 MySQL 未操作，`.local` 证据删除 0，D 盘可用空间仍高于 20 GiB；不合 `main`、不打标签/Release、不部署。

## 本轮接续：R06 mapping3+ 显式 fail-closed（2026-10-03）

### 子项状态

| 子项 | 状态 | SHA / 证据 |
|---|---|---|
| R06 mapping3+ LINK 入口守卫 | 已实现并推送开发分支 | `1bba207`；`.local/autonomous-20261003/mapping3-fail-closed-regression/` |
| R1 白名单复审 | 未发现新产品缺陷，保持现有冻结保护 | 既有 `R1_HANDOVER_WRITE_AFTER_ROLLBACK_2026-10-02.md`；本轮未放宽能力 |

### 自主决策记录

10. 现有业务只定义 v1 初始来源到 v2 `CONTROLLED_LINK` 的一次人工关联，没有定义重复关联、旧目标失效或高轮次续作的权限、占用迁移和确认语义；因此在 `TemporaryLinkOperation` 入口要求 `mappingVersion == 1`，任何 v2+ LINK 直接返回 `COMMAND_CONTEXT_CHANGED`，继续 fail-closed。
11. 用独立单测固定高版本请求在读取来源、候选、目标和映射写入前被拒绝；不改变正常 v1→v2 关联，也不改生产 gate、白名单、迁移或接口契约。
12. R1 白名单审查未发现新的静态或运行缺陷，保留既有 Primary 冻结和隔离证据，不为“完成白名单”添加未经定义的生产能力。

### 验证与证据索引

- `TemporaryLinkOperationTest`：1/0/0/0，证明 mappingVersion=2 在所有依赖调用前返回 `COMMAND_CONTEXT_CHANGED`。
- 完整服务端离线 `test`：2992 项，0 失败、0 错误、1 个既有条件跳过；证据：`.local/autonomous-20261003/mapping3-fail-closed-regression/`。
- 首次定向编译因测试夹具静态类型错误失败，修正为 `ObjectNode` 后定向测试和完整回归通过；无业务测试失败，失败输出未写入产品证据目录。
- 提交 `1bba207` 已推送 `origin/codex/development`；本轮未合 `main`、未打标签/Release、未部署、未接原库/现网。

### 仍未解决与环境

- mapping3+ 的业务重链政策、旧目标失效、跨来源/高轮次续作仍未定义；本轮只落实入口保护，不把字段可表达性写成已完成。
- P08-B 撤销确认后的占用回退、现场 PDA/原身份/Keystore、真实退货实物、断电恢复、长时运行和生产审批仍未验。
- 失败证据继续保留；系统 MySQL 未操作，监听为空，`.local` 未删除；D 盘可用空间约 49.9 GB，高于 20 GB 阈值。

## 本轮接续：R07-P08-B 撤销确认后的占用恢复（2026-10-03）

### 子项状态

| 子项 | 状态 | SHA / 证据 |
|---|---|---|
| R07-P08-B 占用恢复与追加审计 | 已实现并推送开发分支 | `9da4ca3`；定向 `.local/autonomous-20261003/r07-p08-b-targeted-5/`；完整回归 `.local/autonomous-20261003/r07-p08-b-full-regression-4/`；真实隔离 HTTP `.local/e2a-cross-20260925-913/` |

### 自主决策记录

13. 选择“当前重开周期内仍未被其他流程占用才恢复”的保守语义：撤销确认恢复原派发订单的 `DISPATCHED` 占用，但不把 bale 周期回退；任何占用冲突、原派发/更正行/重开关系不唯一或周期不匹配，都拒绝整笔确认并保持 `WITHDRAW_PENDING`。
14. 将恢复动作放在撤销确认事务内，先锁定更正、重开、派发和 bale，再写恢复占用；回执持久化后追加不可变审计行，审计行引用撤销回执，避免仅凭账务反向推断库存状态。
15. 为恢复审计新增数据库表与 append-only 触发器；生产 gate、跨进程开关、lease 和白名单默认值均未改，真实隔离测试使用独立数据库。

### 验证与证据索引

- 定向回归：12 项通过，0 失败、0 错误、0 跳过；覆盖配置、恢复逻辑、周期重开和冲突回滚。
- 完整服务端离线回归：2993 项通过，0 失败、0 错误、1 个既有条件跳过（符号链接夹具）。
- 隔离真实 HTTP：`E2aCorrectionAccountingHttpIT` 1/0/0/0；覆盖取消派发、真实退货、撤销后恢复占用、冲突回滚、审计、outbox 和认证停机。
- 失败证据和隔离数据库继续保留；系统 MySQL 未操作，监听已停，`.local` 证据删除 0。

### 仍未解决与环境

- PC 真实 HTTP、现场 PDA/原身份/Keystore、真实退货实物、断电恢复、长时运行和生产审批仍未验；不把隔离证据写成现场或生产完成。
- mapping3+ 重链政策、R1 白名单正式放行、历史更正边界仍单列；本轮只完成已定义的 P08-B 占用恢复。
- 不合 `main`、不打标签/Release、不部署、不接原库/现网；D 盘可用空间仍高于 20 GiB。

### 环境与接续

- `9da4ca3` 已推送 `origin/codex/development`；新增迁移、恢复服务、测试和验证脚本均已纳入该提交。
- 下一步继续审查 R06/R07 未完功能，优先处理已有明确契约且可独立验证的缺陷；生产默认 gate 保持关闭。

## 本轮接续：CLOSE_DISPOSITION 受控关闭（2026-10-03）

### 子项状态

| 子项 | 状态 | SHA / 证据 |
|---|---|---|
| CLOSE_DISPOSITION 受控关闭 | 已实现、定向/完整/隔离验证通过并推送 | `9deea7f`；`.local/autonomous-20261003/close-disposition-verification-targeted-2/`；`.local/autonomous-20261003/close-disposition-full-regression-2/`；`.local/e2a-cross-20260925-915/` |
| mapping3+ | 保持 fail-closed，未完成 | 缺少安全重复关联政策 |
| R1 白名单 | 无新增放行 | 继续单列复审 |

### 自主决策记录

16. 按交接状态先完成已验证 close-disposition 的暂存、提交和推送，再更新事实块与断点，避免把已完成证据留在未提交工作树。
17. 关闭只接受拒绝或中止来源，并要求可信完整停止验证、精确物料清单和非空原因；逐包释放或隔离占用，绑定来源清除和 canonical slot 关闭均在同一受控操作中完成。
18. 生产命令开关默认值保持 false；隔离测试只在测试配置中开启，不改变生产行为。
19. 保护用户遗留文档、本机请求 JSON、清理脚本和历史报告，不把它们混入产品提交。
20. 早期 targeted compile 发现错误调用 `SubmissionJson.canonical` 的 `byte[]` 参数和不存在的 `ArrayNode.parent()`；修复后才接受定向、完整和隔离证据，失败记录继续保留。
21. 先串行复核事实检查；并行执行曾读到旧事实块，未将该中间读数作为证据，随后串行 `-Check` 通过。

### 验证与证据索引

- 定向：`PhysicalDispositionVerificationTest` 7/0/0/0，见 `.local/autonomous-20261003/close-disposition-verification-targeted-2/result.json`。
- 完整：服务端离线回归 3002/0/0/1，见 `.local/autonomous-20261003/close-disposition-full-regression-2/result.json`。
- 组合：`E2aTemporaryCloseDispositionIT` 1/0/0/0，见 `.local/e2a-cross-20260925-915/result.json`；覆盖迁移、真实读取/写入、释放/隔离和重复迁移。
- 版控：暂存清单、暂存空白检查、提交回执和推送回执在本地命令记录中；提交已推送到 `codex/development`。
- 失败：早期编译失败记录不删除；修复后证据不覆盖失败记录。

### 仍未解决

原身份 PDA、真实实物、断电恢复、生产审批、现场流程、mapping3+ 安全重链政策和 R1 白名单复审仍未完成。隔离 HTTP/SQL、合成身份和测试开关不能代替现场或生产证据。

### 磁盘与环境

D 盘可用空间约 47.4 GB，高于 20 GB 停止阈值；系统 MySQL 仅确认监听、未访问；无在途 Gradle/隔离库任务；设备动作 0；`.local` 证据删除 0。未合 main、未打标签、未 Release、未部署、未接原库/现网。
