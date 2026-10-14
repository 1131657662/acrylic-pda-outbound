# E07/P07 隔离恢复锚点重锚验收（2026-10-14）

本页记录 P07 的停机重锚开发子项。范围是 P5 lease-root 的外部锚点轮换、保留进程记录重签和空租约索引重签；不接原库、现网、外置介质或真实 PDA，不把本页结论扩展为 E07、D18 L3 或总目标完成。

## 实现范围

- `server/src/main/java/com/acrylic/outbound/recovery/RecoveryLeaseRekey.java` 提供显式调用的重锚入口。调用者同时提供旧锚对应的 `RecoveryLeaseRoots` 和 32 字节新锚；新锚不写入 P3/P5 树、Git 或返回值。
- 重锚在 P3 协调锁内执行，先核对旧 lease-root、安装身份、文件身份和旧锚 HMAC，再拒绝活动写者、非空租约索引、残留 lease 文件或相同新旧锚。
- 进程记录、空 `leases/index.json` 和 `lease-root.json` 分别写入临时文件并原子替换；替换前复制全部目标，任一替换失败会恢复全部目标并保留导致失败的 pending 文件。
- 根标记只保留新 `anchorId` 和新的绑定摘要；返回值仅包含旧/新锚 ID、标记摘要、进程记录数和租约数。

## 验证与证据

- 输入：分支 `codex/development`，提交 `72f7e3f4c3ca1a522810a227610f2023811fd88c`；远端 `origin/codex/development` 同 SHA。接管前看板快照、完整脏路径和事实刷新记录见 `.local/goal-20261014/p07-rekey-01/`。
- 定向命令：`./gradlew.bat -p server test --tests com.acrylic.outbound.recovery.RecoveryLeaseRekeyTest --no-daemon --rerun-tasks`，退出码 0；XML 报告为 4 tests、0 skipped、0 failures、0 errors。原始输出见 `rekey-test-rerun.log`，命令时间和退出码见 `rekey-test-rerun-result.txt`。
- 通过场景：保留进程记录重签并用新锚验证；活动进程拒绝；非空租约拒绝；根标记替换失败后的进程记录、索引和标记完整恢复。测试不写共享 MySQL，也不启动服务监听。
- 本轮源码 SHA-256 见 `source-sha256.txt`；进程、监听和 C/D 余量见 `processes.txt`、`listeners.txt`、`disk.txt`。现场快照显示共享 MySQL 3306 和原 AVD 进程仍保留，项目 API/Vite 与隔离实例未启动。

## 边界与未验

- 这是同机、合成 `FenceTestSupply` 的开发验证；没有证明异地介质取回、生产 RPO/RTO、原身份/原设备或现场停机窗口。
- P07 尚未覆盖真实运维编排、外部秘密供应、跨主机文件系统行为和最终候选整合；这些缺口仍属于 E07/E09/D18 后续工作。
- 生产 handler/POST/放行/证据写入/关闭能力和 `localDeletionAllowed=false` 保持关闭；失败库、原日志和既有 E07/O05 证据继续保全。

本专项通过只表示 P07 隔离重锚代码和定向测试通过，不表示 E07、D18 L3、任何现场清单或总目标完成。
