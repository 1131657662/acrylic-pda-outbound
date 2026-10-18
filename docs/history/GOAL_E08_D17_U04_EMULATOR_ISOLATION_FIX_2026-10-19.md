# E08/D17/U04 模拟器隔离启动器修复（2026-10-19）

本切片只修复并验证高频 UI 运行器的模拟器隔离前置，不安装 APK、不登录、不连接真实 HTTP/MySQL、不执行 Android 业务断言。取消的换机承接、多跳、接收者确认/激活链保持 fail-closed；本切片不计 U04、D17、E06 L2、D18 L3 或第 8 节闸门。

## 触发与新假设

前三次高频首页尝试均未形成可验收的独立设备：空 datadir 被模拟器回退到默认 AVD，单个 `-datadir=` 被拒，带引号的 `-datadir` 虽启动 QEMU 但没有 ADB 设备。新的有界假设是：`-datadir` 只有在可写镜像和 AVD 索引都位于独立目录时才可证明隔离；启动器必须复制完整 AVD 内容到 D 盘、排除锁/快照，并在停机后证明默认源镜像未变化。

## 改动路径

| 路径 | 影响层 | 改动 | 直接验证 |
| --- | --- | --- | --- |
| `tools/e07-d06-d07-emulator-run.ps1` | 测试运行器/环境边界 | 拒绝同名活动 AVD；复制完整 AVD 到独立 `ANDROID_AVD_HOME`，复制 `Pixel_6.ini`，排除锁与快照；启动后核对目标 datadir 进程绑定；停机后核对默认源可写镜像 SHA | PowerShell AST 0 个语法错误、`git diff --check`；run05 完整 AVD 启动/停机事实 |

## 实际探针结果

1. run05 第一次空 datadir（`userdata`）无 `Invalid -datadir`，端口 `5616/5617` 和 `emulator-5616` 出现，但 datadir 文件数为 0；因此不算隔离。
2. run05 第二次只复制 qcow 可写镜像（`userdata-populated`）可开机，但停机后默认 AVD 的 `userdata-qemu.img.qcow2` 与 `encryptionkey.img.qcow2` 已变化，目标镜像未变化；按 fail-closed 判定仍是源回退，不进入产品测试。
3. run05 第三次复制完整 AVD 到 `full-avd-home/Pixel_6.avd`，并生成独立 `Pixel_6.ini`。`emulator-5620` 开机完成，日志无 `Unknown AVD`/`Invalid -datadir`，目标进程命令行同时含 D 盘 datadir，端口 `5620/5621` 监听，ADB 设备为 `emulator-5620`。停机后目标 userdata/encryptionkey SHA 发生独立变化，而默认 AVD 四个可写镜像 SHA 均与复制前一致。

## 证据与处置

- 证据根：`.local/goal-20261019-resume/d17-high-frequency-run05/`。
- 启动输入、进程命令、日志、ADB/端口快照、复制清单和停机核验：`launch-input.json`、`startup-verification.json`、`populated-data-manifest.json`、`populated-startup-verification.json`、`populated-stop-verification.json`、`full-avd-copy-manifest02.json`、`full-avd-launch02.json`、`full-avd-startup-verification02.json`、`full-avd-stop-verification02.json` 及对应 stdout/stderr/stop 日志。
- `full-avd-stop-verification02.json` 记录无残留 emulator/qemu、无 `5620/5621` 监听，默认源 `userdata-qemu.img.qcow2`、`encryptionkey.img.qcow2`、`cache.img.qcow2`、`sdcard.img.qcow2` 均 `sourceUnchanged=true`。
- 完整 AVD 副本、两次临时 userdata 仅为可重建测试数据；已认证停机且无监听，但本轮递归清理命令被 Windows 执行策略拒绝，未换壳绕过，目录和证据暂保留。D 盘收口时应按同一绝对路径重试并记录前后余量；原 AVD、原资料、Keystore、继承源码和失败证据不清理。

## 边界与下一步

运行器修复只解除独立模拟器启动前置，不证明人员/设备输入、登录、临时准备、首页业务、真实 HTTP/MySQL、Chrome、Python、非空迁移或恢复全链。下一次若重新入队 U04，仍需用新隔离 datadir 和可审计准备输入安装当次 APK，再执行 Android L1；任何身份、端口、源哈希或输入绑定不一致都必须停止并保全。总目标继续 active，D17 整体、E06 L2、D18 L3、第 8 节和总目标不得标记完成。
