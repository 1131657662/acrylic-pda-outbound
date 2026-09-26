# E2a V35 触发器迁移授权决策卡

2026-09-23 本机决定：用户已把项目开发与隔离数据库/运维职责交给当前助手，因此由当前助手在**仅有专用 19329 实例**的全新 attempt7/8 配置 `log_bin_trust_function_creators=ON`，保留 binlog、无 SUPER、无共享实例或生产配置变更。attempt7 清理失败已保留，attempt8 作者动态迁移通过；详情见 `ACCEPTANCE_STAGES.md`。下面目标部署实例核查及正式发布条件仍是未验事项，不是让用户寻找外部“权限持有者”。

2026-09-23 原始审查说明（attempt7 前）：此卡当时只整理待决条件，不授权修改生产/测试 MySQL，也未分配 attempt7。固定 V35 资源包含两条不可变触发器；attempt6 在非空 V34 后报 MySQL 1419，原失败证据保留。本机 mysqld 为 8.0.46；本机版本不代表部署实例版本。

## 权限持有者需要确认

1. 目标部署实例的 MySQL 版本、`@@GLOBAL.log_bin`、`@@GLOBAL.log_bin_trust_function_creators`、binlog/PITR 归档要求，以及 V35 实际迁移账号的 `CURRENT_USER()` 和 schema `TRIGGER` 权限。只读取值及脱敏授权结果即可，不提供账号密码、私钥或生产原始数据。
2. 如拟在保留 binlog 的前提下使用 `log_bin_trust_function_creators`，由数据库/运维权限持有者评估其**实例级**影响、变更窗口、监控、回退、对共享其他库的影响，并记录明确批准。验收器不设置该变量；不能把本机条件通过当作生产已批准。
3. 确认 V35 创建后的触发器 `DEFINER`、迁移账号后续保留/回收策略和运行账号权限边界。迁移前后分别核对 `information_schema.TRIGGERS` 与 `SHOW CREATE TABLE source_evidence_intake_receipt`；仍需以真实不可变 UPDATE/DELETE 负向结果证明约束有效。
4. 确认升级期间的写屏障、数据库与私有文件备份、Flyway 失败处理及共享实例恢复边界。V35 DDL 失败不能在旧 attempt6 或生产库上自动 `repair`、重跑、删库；与 R09 恢复方案一致，不整体回滚共享实例。

## 决策与执行闸门

| 决策 | 结果 |
| --- | --- |
| 已授权且可复现的 binlog/触发器创建策略，专用账号无 SUPER、具 schema TRIGGER 权限 | 由明确写权持有者记录批准、实例范围和证据后，才分配独立 attempt7；保持 binlog 开启，以真实相同前置条件运行 V34→V35、负向矩阵和前后快照。 |
| 无获批的兼容策略，或仅能靠关闭 binlog/授予 SUPER/修改固定 V35 SHA 绕过 | 保持阻断；提请迁移与运维负责人重新设计并单独审查版本化方案，不把局部夹具通过当交付。 |

目前仍没有目标部署实例的上述只读取值或正式发布决定；但本机隔离 attempt7 失败、attempt8 作者动态通过，已在本文件顶部和 `ACCEPTANCE_STAGES.md` 分列。作者聚焦 35 项与隔离动态 1 项均不能替代独立消费者、完整 server 单测、真实 HTTP/设备及现场验收。
