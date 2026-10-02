# 项目清理记录（2026-10-02）

## 范围与结果

按用户要求清理可再生、与源码无关的本机产物。清理前 D 盘可用约 34.16 GiB，执行后约 52.42 GiB，释放 19,792,195,068 字节（约 18.433 GiB）。执行脚本为 `tools/cleanup_reproducible_artifacts_20261002.ps1`，脚本包含固定路径白名单和 emulator/qemu 进程检查。

已删除的白名单路径：

- `.local/pda-download-avd`（6,332,167,374 字节）
- `.local/pda-upgrade-avd`（6,234,189,665 字节）
- `.local/pda-browser-install-avd`（5,903,822,190 字节）
- `.local/server-runtime`（404,641,115 字节，开发服务器运行时 JAR 会按需重建）
- `D:/acrylic-snapshots/r06-e2a-migration-full-20260926-2215/workspace/build`（142,224 字节）
- 同快照 `workspace/server/build`（289,569,829 字节）
- 同快照 `workspace/report-rendering/build`（291,397,915 字节）
- `D:/acrylic-entry-verify/server/build`（289,371,608 字节）
- `D:/acrylic-entry-verify/report-rendering/build`（46,893,148 字节）

上述 AVD 目录只含模拟器系统镜像/快照和临时运行文件，可由测试工具重新创建；运行时 JAR 与 `build/` 均为可再生构建产物。清理前已确认没有 emulator、emulator64-crash-service 或 qemu-system-x86_64 进程。

## 保留边界

- 未删除 `.local` 中的测试数据库、隔离 HTTP/MySQL 目录、失败证据、签名材料、设备验收证据或下载/升级验收证据；`e2a-cross-20260925-856`、`mysql`、`pda-signing`、`pda-download-stage`、`pda-upgrade-stage` 仍在。
- 未删除 `demo-artifacts` 或 `outputs`。文档和历史验收记录仍引用这些路径，其中包含通过和失败证据。
- 未删除旧 worktree、全量快照或另一个仓库；这些目录的大头仍是受保护的 `.local` 数据/证据。
- 主树预存的 8 项未提交/未跟踪改动未触碰，主树没有产品源码行为变化。

## 验证与状态

- 清理脚本二次执行确认全部白名单路径不存在，二次执行的剩余字节为 0。
- D 盘可用空间 52.42 GiB，高于 20 GiB 阈值。
- 19330、19434、19435 无监听；系统 MySQL 未操作。
- 未运行产品回归测试；本次仅删除可再生本机产物，源码和业务行为未改变。
- 未合 `main`、未打标签/Release、未部署、未接原库/现网、未修改生产默认开关；长期目标按用户指令暂停。

## 自主决策记录

1. 删除三个无活动 emulator/qemu 进程且可由工具重建的 AVD 快照，以释放主要空间。
2. 删除开发服务器会自动重建的历史运行时 JAR 和旧检出/快照中的 `build/`，不触碰证据目录。
3. 保留 `demo-artifacts`、`outputs`、隔离数据库和所有 `.local` 证据，因为历史文档仍引用它们或其失败状态需要保全。
