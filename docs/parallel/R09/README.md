# R09 运维基础：交付入口

本轮交付可独立验证的备份检查、隔离恢复演练、监控及恢复接口提案。2026-09-24 新增已提交 `23ae8c4` 的 V34 来源证据隔离快照恢复检查，见[V34专项记录](V34_RECOVERY_GUIDE.md)。此前固定业务基线 `9058137` 的 V33 TEMP 演练见[V33记录](V33_RECOVERY_GUIDE.md)。V34 只核对保留字节与引用，**不是可信 VERIFY 或灾后重建**；工具仍拒绝未知版本，未部署、不能解除业务隔离。

## 首轮隔离与基线（历史）

- 工作目录：`D:\acrylic-pda-worktrees\r09-ops`；分支：`codex/r09-ops-foundation`。
- 起点：`2369392076083a5a4f59d8ea3d6edebe5067a03f`，从当时已提交的 `codex/development` 创建。没有复制原开发目录未提交的变更。
- 所有新增源码、配置、文档和可提交证据均在本目录。原开发目录只读；公共路线/总进度、正式 API、Flyway 文件、Room、同步/记账实现及版本号未改。
- 初次只读看到原目录有 `AGENTS.md`、`README.md`、总路线/进度、设计规范/PDA模板变更及未跟踪的 PDA 更新专项设计；均保留。
- V21/V28成果已由`4499716`提交推送；V32增量的提交/推送状态以[STATUS.md](STATUS.md)最新记录和Git为准。没有合并、打标签、发布，未访问/更改公共服务器、现有备份计时器、两台 PDA 或真实业务数据。

## 文件导航

| 文件 | 用途 |
| --- | --- |
| [STATUS.md](STATUS.md) | 实际进度、验证证据、失败过程、后续“继续”入口 |
| [R05_READY_EXTENSION.md](R05_READY_EXTENSION.md) | 2026-09-20 接入已提交 R05/V28 归档、审计与 Worker 的增量证据 |
| [V33_RECOVERY_GUIDE.md](V33_RECOVERY_GUIDE.md) | V33原控制/正文/分块/取消、93表恢复与专用夹具隔离；失败过程与最终证据 |
| [V34_RECOVERY_GUIDE.md](V34_RECOVERY_GUIDE.md) | V34合成文件引用、101表空库恢复、可逆负例与首次失败证据 |
| [R06_READY_EXTENSION.md](R06_READY_EXTENSION.md) | V32 TEMP原来源/签名准备包、FH计数器及93表恢复核对，56项运维测试 |
| [RECOVERY_CONTROL_CONTRACT.md](RECOVERY_CONTROL_CONTRACT.md) | 库外权威控制、数字代次兼容、共享事务/Worker栅栏提案；尚未接入 |
| [RECOVERY_EVIDENCE_CONTRACT.md](RECOVERY_EVIDENCE_CONTRACT.md) | 证据分页、原来源与引用、V33取消和截点外FH高水位门槛；尚未接入 |
| [BACKUP_AND_RESTORE.md](BACKUP_AND_RESTORE.md) | 恢复材料清单、隔离命令、检查与保密要求 |
| [UPGRADE_AND_ROLLBACK.md](UPGRADE_AND_ROLLBACK.md) | 程序升级/回退、迁移失败、数据库恢复手册 |
| [MONITORING.md](MONITORING.md) | 指标、退出码、候选配置、部署前验证 |
| [DEPENDENCIES.md](DEPENDENCIES.md) | 完整 R09 剩余事项及 R05～R07 依赖 |
| [ops/cli.py](ops/cli.py) | 唯一常规命令入口；错误脱敏，失败退出 2 |
| [ops/fault_drill.py](ops/fault_drill.py) | 仅用于专用恢复库的故障演练，所有故障注入恢复原值 |
| [config/](config/) | 待审阅的监控、资源限制配置；没有自动安装脚本 |
| [tests/](tests/) | 合成归档、路径与监控失败测试 |

## 复用与适用边界

复用 R02 的 MySQL 逻辑备份、停写截点、数据库记录数/CHECKSUM、私有配置归档和版本清单思路；复用仓库现有 `SubmissionApiIT` 合成业务场景及开发签名密钥生成器。没有改写 `deploy/test/` 的现有部署/备份脚本。

`verify-r02` 可以只读检查既有 R02 五组件及 R05 增加服务状态清单后的六组件归档；本轮只在合成格式上验证它，没有读取服务器备份。新 `backup/restore` 命令**只接受本任务专用实验环境**，不是向任何库恢复的通用管理员工具。业务一致性检查明确支持 V21、V28、固定提交`87e1893`的 V32、固定业务提交`9058137`的 V33 及固定提交`23ae8c4`的 V34；未来表结构变化须更新检查并重新演练，不默默跳过未知表/业务规则。

SHA-256 清单用于发现损坏；它没有证明不可信来源的真实性。实验归档含合成数据库、随机凭据和新签名私钥，只在受限的 `.local/r09/` 保存；不得上传 Git、Web 下载目录或普通日志。异地加密、防覆盖保留和完整 RPO/RTO 尚未建立。
