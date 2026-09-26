<!-- CURRENT_STATE_START -->
## 被替换当前区快照（2026-09-23）

E2a intake 查询切片已完成：`SourceEvidenceIntakeReceipts.existing` 以 `READ_COMMITTED`、只读、默认 `REQUIRED` 查询 `(principal,route,originalKey)`，同键同请求回放原响应，同键异请求冲突，无行 `NOT_FOUND`，数据库异常继续抛出。迁移树聚焦 intake 12/0，完整 server 单测 189/0；该结论没有事务外冲突恢复、写者、动态 V35、HTTP 或设备验收。

主树为 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，E2a 树为 `D:/acrylic-pda-worktrees/r06-e2a-migration` / `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`；两树已有未提交改动均保留。共享 3306 由 PID 5448 监听，19329 未监听；未操作原库、现网、设备或正式部署。

下一步原定为设计 intake 写者：第一次/锁后第二次原键查询，严格协议/事实校验、文件耐久后写入；仅确定为 `uq_evidence_intake_original` 冲突且原事务回滚后，才独立只读重查；其它 FK/CHECK/锁错误失败关闭。
<!-- CURRENT_STATE_END -->
