# 自主接续方案（2026-09-30）

## 真实断点

- R06 A1.1 已按原收口记录完成；R06 首轮反向候选独立消费、R07 迁移后主管签名审批、分离身份误批撤销均已有当次实跑证据。
- R06 A1.2/A1.3 尚未整体收口。M2b-3 审计确认 TEMP 拒绝→处置→续作需要新的 TEMP 回执、来源锁、处置证据和权限模型；当前不能从既有源码或政策安全推出实现，列为待决，不改首轮 fail-closed 守卫。
- 当前工作区已有 R07-P08-A 草稿：V51 append-only 触发器、真实 MySQL IT 及离线检查脚本；此前 799/800/801 仅作失败/临时修正证据，不计本轮验收。
- 两份非本会话 PDA 文档、全部 .local 证据、生产默认开关和真机边界均保持不变。

## 本轮子项

| 子项 | 做什么 | 退出标准 | 证据与依赖 |
| --- | --- | --- | --- |
| R06-M2b3-A | 保留 TEMP 桥接审计结论，登记缺失模型与待决权限 | 不删除首轮限制、不把正式续作路径冒充 TEMP 支持；报告源码锚点和未验边界 | `docs/history/R06_M2B3_BRIDGE_AUDIT_2026-09-30.md`；无数据库依赖 |
| R07-P08-A | 完成 V51 更正事实/回执 append-only 保护的测试域收口 | 离线 `testClasses` 通过；所有 tools/*.ps1 parseErrors=0；新隔离 MySQL 从 V34→V50→V51；非空数据上 UPDATE/DELETE 负向断言全部拒绝；允许的状态机生命周期 UPDATE 通过；XML 为 1/0/0/0，started/completed marker 齐全；同一 attempt 二次调用被幂等守卫拒绝 | `server/src/main/resources/db/migration/V51__correction_append_only_guards.sql`、`E2aCorrectionAppendOnlyIT.java`、`.local/e2a-cross-20260925-<run>/`；不改产品默认开关 |
| R07-P08-B | 评估更正撤销后的逐笔占用回退 | 仅在 P08-A 通过且源码/政策足够明确时实施；否则记录待决，不伪造完成 | 依赖 P08-A 与现有撤销链；本轮不先行 |
| 收口 | 更新进度、看板当前区、当日进度记录并提交推送 | 每个标完成项都有本轮 XML/日志/SHA；失败证据保留，未验边界单列 | Git/远端/磁盘/真机快照 |

## 执行纪律

- 多步操作写成并执行 `.ps1`；只整文件重写，不做字符串/行级修补。
- 一次只启一个 AVD；本轮 P08-A 不启 AVD，真机 `22D2910042` 只做前后枚举快照，任何设备命令锁 `-s emulator-*` 并设置 `ANDROID_SERIAL`。
- 不部署、不接原库/现网、不合 `main`、不打标签/Release；admission/effect-gate/cross-process/lease 默认值和 `localDeletionAllowed=false` 不改。
- 既有通过测试一旦回归失败，停止当前子项并保留证据；磁盘低于 20GB 时停止。
- 只清理本轮新建 attempt 的 datadir，保留日志、XML、marker、dump、ZIP 和失败证据。

## 待决清单

1. R06 TEMP REJECT 独立写授权与处置/续作桥接模型未由现有政策推出，暂不实现。
2. R06 候选排除授权仍未定；不新增权限码或普通确认权。
3. R1/S4b 写后回滚验证受既有 Primary 冻结白名单守卫阻断；不放宽白名单，不绕过。

## 下一步

立即执行 R07-P08-A 离线自检；通过后分配新的隔离 attempt 复跑真实 MySQL IT，再根据结果更新进度与看板。