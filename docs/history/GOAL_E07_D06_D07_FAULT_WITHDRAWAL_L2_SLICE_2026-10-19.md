# E07/D06-D07 故障撤回、重发与恢复隔离切片（2026-10-19）

本切片在当前工作树 `9c6feaffe39633206734accac7c945a4b4bfcc1f` 上执行，验证 D06/D07 现行“后台坏机撤回、重新发布、新设备独立作业、恢复后不重复记账”路径。输入、身份、开发签名和数据库均为新建隔离事实；未连接原库/现网，未恢复 R07 已取消的换机进度承接、多跳或接收者确认/激活链。

## 执行与结果

- `E2aFaultWithdrawalRecoveryIT` run1615：新隔离 MySQL、V99 非空迁移、真实 Spring HTTP、撤回→发布→新设备领取→成功提交、五个截点 SQL/文件快照、原 binlog 回放和恢复后真实 HTTP 重放，`1/0/0/0`，Gradle 退出 0。证据根为 `.local/goal-20261019-resume/e07-recovery-run1615/`，集成 XML SHA-256 为 `8C97D50D3C999365C6FF0F7A014DF530135454569E2A82D3E2C15A7DC91A74AC`。
- run998：`FaultWithdrawalApiIT` `7/0/0/0` 与 `FaultWithdrawalDefaultIT` `1/0/0/0`；覆盖无 STOP 撤回、成功终态保护、版本冲突、仓库/角色权限、同键同文幂等、同键异文拒绝、末条幂等写失败回滚，以及有权身份下生产默认关闭零写。隔离数据库身份、180 张表/53 张非空表、1,205,924-byte dump 和认证停机回执保存在 `.local/goal-20261019-resume/fault-withdrawal-suite1616/`。
- run999：`FaultWithdrawalHttpIT` `4/0/0/0`、`FaultRestartApiIT` `3/0/0/0`、`FaultRestartReliabilityHttpIT` `4/0/0/0`；覆盖真实 HTTP 丢响应重放、重新发布/新设备领取、原提交与撤回竞争、审批与实物撤回竞争、并发发布/领取、尾写失败恢复旧轮次/发布、并发幂等和出站消息回滚。XML 与进程/监听回执保存在 `fault-withdrawal-suite1616/run999/`。

业务测试合计 `19/0/0/0`，恢复跨截点测试另计 `1/0/0/0`。998/999 的 MySQL 均由隔离账号认证关闭，19330 与 19434 在停机后无监听；共享 MySQL、ADB、Vite 和 Gradle 守护进程只观察未停止。

## 输入、验证和复用

- 运行命令使用全新 attempt：`tools/r09-e2a-entry-rerun.ps1 -Run 998/-Run 999`，并以 `--rerun-tasks --no-daemon` 实际执行 Gradle integrationTest；`-AppMigrates` 仅用于隔离实例首次迁移。源代码哈希见 `fault-withdrawal-suite1616/source-hashes.json`，结果汇总见 `result.json`。
- 直接受影响层为服务端召回/提交/库存事务、真实 HTTP/MySQL 和恢复跨截点；本切片没有 Android、Chrome、Python、原模拟器或现场身份输入。历史 D10 报告仅作契约和测试入口复用依据，不替代本轮输入。
- 测试覆盖正向、权限、终态保护、事务回滚、同键幂等、异文拒绝、并发竞争、锁序列化相关路径和 default-off 有权调用；未将合成隔离身份解释为原 PDA、人工主管、现场回执或生产授权。

## 清理与边界

998/999 已停机且无消费者；清理前绝对路径、文件字节和监听见 `cleanup-before.json`。递归删除被执行策略拒绝，未绕过，清理后 `releasedBytes=0` 并保留 datadir，见 `cleanup-after.json`。恢复 run1615 的原 binlog、检查点和唯一恢复证据继续保全。

本报告只收口一个隔离开发切片，不计 D06/D07 完整 L2、E07/D17、E06 L2、D18 L3、第 8 节闸门、O/U 整体或总目标完成。统一最终候选仍需按第 8 节组合 Android、真实 HTTP/MySQL、Chrome、Python、非空迁移、原模拟器和恢复全链，并继续满足可信原身份、现场审批、外置保管和 D16 合法退出条件。
