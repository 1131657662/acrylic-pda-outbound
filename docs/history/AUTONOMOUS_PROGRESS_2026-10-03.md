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
