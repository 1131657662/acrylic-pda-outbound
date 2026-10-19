# E08/D17/U04 高频首页隔离启动失败保全（2026-10-18）

本记录收口高频首页 L1 的隔离模拟器启动器诊断。没有修改产品源码、测试前置或登录逻辑，也没有安装或操作 USB PDA。三次尝试使用独立 D 盘 userdata，均未形成可供产品验收的隔离 ADB 设备，因此不计 U04、D17、E06 L2、D18 L3 或第 8 节闸门通过。

## 输入与环境

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`。
- AVD：`Pixel_6`，Android Emulator `36.6.11.0`；请求端口依次为 `5610`、`5612`、`5614`，userdata 均位于 `.local/goal-20261018-resume/d17-high-frequency-run0{2,3,4}/userdata`。
- 运行前确认无本项目 Gradle/HTTP/模拟器监听；另一任务的 `5564/5565` 与系统 MySQL 仅观察，未停止或连接。USB `22D2910042` 仅枚举，未安装、写入或触摸。

## 三次实际结果

| 尝试 | 命令行处理 | 原始结果 | 判定 |
| --- | --- | --- | --- |
| run02 | `Start-Process -ArgumentList` 传入 Windows 路径 | `invalid command-line parameter: \\userdata` | 启动器在参数解析阶段退出 |
| run03 | 单个 `-datadir=...` 参数 | `unknown option: -datadir=...` | 启动器拒绝该形式 |
| run04 | `-datadir` 与带引号路径分成两个参数 | 进入 QEMU 初始化并写出 `androidboot.serialno=EMULATOR36X6X11X0`，但未形成 `emulator-5614` ADB 设备或 `5614/5615` 监听；随后进程退出 | 仍不能证明独立 datadir 或登录态产品运行 |

run04 的日志没有新的明确错误；这不是业务成功证据。不能把默认 AVD 的序列号、合成登录、fixture 或旧页面状态当作隔离设备绑定。现有 `HighFrequencyOfflineDeviceTest` 的扫码、暂停、恢复测试未运行。

## 证据

| 路径 | SHA-256 |
| --- | --- |
| `.local/goal-20261018-resume/d17-high-frequency-run02/emulator.stdout.log` | `9BA5ADCAE7B7D838E4538E67F9AA89B42CB2DD52BC57213FB3A1566A360EC6C2` |
| `.local/goal-20261018-resume/d17-high-frequency-run02/emulator.stderr.log` | `F2F84559C378DE3282AAB786C701F9976539585D25809C8067249E533F4AE7AC` |
| `.local/goal-20261018-resume/d17-high-frequency-run03/emulator.stdout.log` | `9BA5ADCAE7B7D838E4538E67F9AA89B42CB2DD52BC57213FB3A1566A360EC6C2` |
| `.local/goal-20261018-resume/d17-high-frequency-run03/emulator.stderr.log` | `691EB404A4622C0E8198E810F19A902A784D6DA797FECDF199DE6A1DC9B3B084` |
| `.local/goal-20261018-resume/d17-high-frequency-run04/emulator.stdout.log` | `84771FD208D8D82663DAED07E8B2D93777A2420835A3161B3E35706187480DED` |
| `.local/goal-20261018-resume/d17-high-frequency-run04/emulator.stderr.log` | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` |

## 处置与重新入队条件

三次尝试和 userdata 目录继续保全，不再无新依据重复相同启动参数。重新尝试前必须先证明 `userdata` 已实际创建并被启动器使用，确认独立端口和 ADB 序列号，再用可审计的设备绑定与真实登录输入运行首页测试。不得伪造登录、直接显示导航、重启旧 run03 或以 USB 真机替代隔离前置。

