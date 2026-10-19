# D 盘开发产物清理记录

2026-10-12 用户要求在不影响开发的情况下清理 D 盘。执行聊天为 `01a12d69-d48c-77f2-b045-33d03dd40a01`，开发聊天为 `01a12a96-895f-7d32-875a-e8a785419cb9`。用户另行明确授权两聊天通知并协调；开发聊天已确认避开本次历史 RAM 与旧 DLL，并负责在下一次看板断点引用本记录。清理聊天不写共享看板、源码、Git 索引或开发聊天正在使用的产物。

输入/核验树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD `950518ec6484fb484c68f526647a0a6b790d5fe1`。版本事实只见看板生成事实块。证据根为 `.local/disk-cleanup-20261012/`；保存 Git/status/diff、97 份脏文件原字节与 SHA256、重建依赖输入哈希、进程/监听和磁盘快照。清理后的 97 份脏文件均存在且与该快照字节一致；并发开发引入的额外改动不由清理聊天代提交。

## 实际执行

1. 对 `.local/d01-d18-20261010/received-confirmation-ui-{1335,1336,1337,1340,1341,1342,1343}/avd/snapshots/default_boot/ram.img` 和 `.local/goal-20261011/e01-device-01/avd/snapshots/default_boot/ram.img` 共 8 个文件执行 `compact.exe /c /i /q`。逐项核对绝对路径、不跟随 reparse point、AVD 身份不在活动进程中，并测试文件独占读取。每项保存压缩命令、原日志、退出码、逻辑长度、前后 SHA256 和 Win32 实际分配空间。8/8 退出 0，逻辑长度及 SHA256 均保持；文件占用减少 `34360262656` 字节（约 32.000 GiB）。这部分仅改变 NTFS 存储表示，原路径、内容和恢复材料保留。
2. 删除 `.local/tmp` 下修改时间早于本日 08:00 的 34 个 `conscrypt_openjdk_jni-windows-x86_64*.dll`，总逻辑长度 `93324288` 字节（约 89.001 MiB）。每份均逐哈希证明与保留的 Conscrypt 依赖 JAR 内原 DLL 相同；打开不允许读写共享的句柄后才删除，无锁定失败。源 JAR 及其哈希保留，由 Conscrypt 加载器从 `META-INF/native/conscrypt_openjdk_jni-windows-x86_64.dll` 按需重新提取；34/34 已删除且复核路径不存在。脚本、白名单、依赖条目、命令和逐项删除回执保留。

未调用预存的 `tools/cleanup-rebuildable.ps1`，因为它会清理活动服务构建目录。未删除数据库/datadir/dump/binlog、失败资料、原日志/XML、签名/HTTP 回执、原身份/Keystore、Room、其他项目、共享工具/缓存或交付 APK。交付 APK 的原 SHA256 清单与本次实测相符，核验回执为 `retained-apk-hashes.json`。

## 磁盘与并发环境

清理前 D 盘可用 `9771503616` 字节（约 9.101 GiB），08:13:53 核验为 `36167614464` 字节（约 33.684 GiB），净增加 `26396110848` 字节（约 24.583 GiB）。文件减少约 32.087 GiB 与磁盘净变化分开记录；另一个开发聊天同时继续测试/构建并新增产物，净变化包含其在途写入，不将差值误记为清理失败或释放量。该截点 C 盘可用 `64536801280` 字节（约 60.105 GiB），E 盘不操作；精确余量见 `disks-after.json`。

活动模拟器 `ScanAdjustment1363`、`TemporaryAdjustment1364` 及共享 MySQL/ADB 保持运行。活动 server Gradle 测试、`server/build`、隔离运行库和活动 AVD 目录不操作。没有停止任何开发进程；只停止本清理聊天自己的冗余只读盘点进程。新增脚本/日志/快照都保存于 D 盘本项目 `.local`，TEMP/TMP 仅对子进程设置，不修改全局环境。

## 证据与接续

`ram-plan.json`、`ram-01` 至 `ram-08` 回执、`ram-result.json`、`ram-final-attributes.json` 保存压缩验证；`conscrypt-source.json`、`dll-delete-plan.json`、`dll-delete-results.json`、`dll-delete-summary.json` 保存重建来源与删除结果；`cleanup-summary.json` 保存汇总，Git/脏文件/进程/监听/磁盘快照均在同一证据根。

本次没有业务代码变更，不启动新的产品回归或构建，也不将开发聊天的在途验证记作本次通过。清理聊天不提交/推送，不合 main，不接原库/现网，不发布/部署；开发目标与未通过项继续按原计划接续。业务 `localDeletionAllowed=false`、D16 完成标准及失败证据保留边界不变。
