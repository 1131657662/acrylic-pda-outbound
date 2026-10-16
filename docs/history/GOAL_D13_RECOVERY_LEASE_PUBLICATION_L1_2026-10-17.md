# D13 跨进程 lease 发布 L1 开发备份

本单元补 D13/P07 的跨进程 lease 边界，范围限真实应用的 lease 发布、进程退出后的孤儿拒绝/退休和下一进程重新启动；不外推 D13 全部 HTTP/Session/worker/文件/导出组合、D18 L3 或生产恢复。执行前输入快照为 `.local/goal-20261017-resume/input50.json`，SHA-256 `13ed4773ffebfa9ed7c8f620c3222cb0d336b928919b4b3ebc007112cee671a9`。测试源码 `server/src/test/java/com/acrylic/outbound/recovery/RecoveryLeasePublicationRealIT.java` 未修改；harness 使用已提交的 `-InstanceRoot` 修复。

使用全新 `server/.local/mysql19445-r09batch` MySQL 实例、schema/account `r09_batch_20261017`，应用端口 19458；执行前确认 19445/19458 无监听。`RecoveryLeasePublicationRealIT` 1 test、0 failures、0 errors、0 skipped，Gradle exit 0、56 秒：第一个真实应用根发布 P5 lease；子进程在孤儿存在时以 `RECOVERY_LEASE_OUTSTANDING` 拒绝并退休记录；记录退休后第二个子进程成功启动。测试后实例 exit 0 停机，端口释放，datadir 保留。

证据位于 `server/.local/d13-leasepublication01`：`batch-summary.json` 1088 bytes SHA `06a8be322008837054d49f29d3f87cdfba7fb8baea30313a4f54046c14c36dd6`；`run101-batch-recoveryleasepublicationrealit/real-records.tsv` 1459 bytes SHA `6e5dfd4d5275b8f8e256c7421f91f586ecbe330a43bf5c6946e79789d756c68`；`lease-publication.txt` 909 bytes SHA `0ec798bc029492efd865e56df3bb2085a064c973d618fba70b88351db05831ba`；XML 24733 bytes SHA `00ca91f5043cd3f9d2e7e29c5afe8f034a61cb6b31c7c9b1f1b04902d5411845`；manifest SHA `59f2a00ede3c7795f860cad0c5d464f73b089021967c8f143dbeb95e3af791b5`；postflight SHA `56cf9f3453480934df7537b91243b4bf1dee8bff823b0c75763163c23691de5a`。合成 control root/身份仅用于隔离开发，不代表原设备、原钥或生产授权。

本单元不覆盖 worker/文件/renderer/export、错库/水位反例、Android/Chrome/Python、现场身份、生产开关或 D13 整体；失败及旧证据仍按看板保留。
