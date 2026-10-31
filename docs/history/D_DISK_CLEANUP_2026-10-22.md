# D 盘清理记录（2026-10-22）

本轮按用户授权继续深度清理。日期仅用于筛选候选，没有按日期整目录删除；项目源码、未提交 WIP、数据库、ADB、原资料、签名材料和唯一证据均保留。清理前已保存 `.local/disk-cleanup-20261022/baseline.json`、`wip-before.patch` 和本轮计划文件。

## 无损压缩

- Android SDK 系统镜像 4 个文件逐文件使用独占句柄执行 NTFS `FSCTL_SET_COMPRESSION`。逻辑长度和 SHA-256 全部不变，4/4 成功；计划、结果和汇总见 `sdk-image-compression-plan.json`、`sdk-image-compression-results.json`、`sdk-image-compression-summary.json`。D 盘可用空间 `29,440,798,720` → `34,349,666,304` 字节，实际释放 `4,909,477,888` 字节。
- Android NDK 与 Emulator 共享安装目录使用 `compact.exe /c /s:<absolute-root> /q`。两次命令退出码均为 0；对清单内 289 个大文件逐个复核 SHA-256 和长度，全部稳定，其中 NDK 的 234 个目标标记为 NTFS compressed，Emulator 的 55 个目标仍未压缩。目录命令还可能处理小文件，未对整棵工具目录进行全量内容验证；第二次重定向覆盖了第一次命令日志，保留的日志不能代表 NDK 完整输出。计划和结果见 `toolchain-compression-plan.json`、`toolchain-compression-result.json`。D 盘实测净增 `938,110,976` 字节，未单独测量本批物理分配减少量。执行前没有 Java、Gradle、模拟器、QEMU、编译器或 SDK 管理器消费者；后续发现其他开发聊天开始使用工具链，因此停止继续处理这些共享目录。
- 已结束的领取预览主机测试 attempt02/attempt03 编译输出和项目本地 Gradle 缓存共 6,785 个文件、逻辑 `115,165,472` 字节，使用 `compact.exe /c /s:<absolute-target> /q` 无损压缩。SHA-256 和长度全部稳定，5,695 个文件标记为压缩；12 个目录的计划、日志和结果见 `claim-build-compression-plan.json`、`claim-build-compression.log`、`claim-build-compression-result.json`。成功测试 XML、日志、命令、输入和 closeout 文件保留，未删除任何证据。D 盘实测净增 `47,493,120` 字节。

## 保留与未处理

- 本轮末次实测可用空间：C `67,115,859,968`、D `35,456,638,976`、E `195,634,053,120` 字节；空间会受并发活动波动，不能把所有前后差额视为单批释放。
- 清理文档落盘前，42 个未提交/未跟踪 WIP 文件与 29 份保留证据的 SHA-256 复核无变化；随后仅更新本记录、历史索引和看板，文件级 WIP 共 43 条。分支 `codex/development`、HEAD `d33e203d69f75ac77ca63a2d4c69b53d3b5790a6` 未变。前后清单、文档改动和终检回执见 `.local/disk-cleanup-20261022/final-checkpoint.json`。
- 未删除 `.local` 历史任务目录、隔离数据库、MySQL 数据、APK、原始回执、部署归档、共享 pnpm/Android 工具包或归属不明目录。删除请求此前被策略拦截的目标没有换接口重试。
- 未停止或连接 MySQL，未操作 ADB 设备，未启动构建、模拟器、服务、部署或 Release。后续清理应先确认共享 Android 工具链无消费者，再按同样规则处理已结束且可重建的中间产物。
- 项目缓存压缩命令曾因包含删除自身临时日志的 `Remove-Item` 被自动审批策略在进程启动前拒绝。取消删除操作、保留全部文件后，无损压缩正常完成；本轮没有成功执行删除操作。

## 2026-10-22 round02

- 操作前基线与未提交差异快照见 `.local/disk-cleanup-20261022-round02/baseline.json`、`wip-before.patch`；基线为 43 个文件级 WIP，分支及 HEAD 与此前一致。替换看板前保存 `board-before-round02.md`（SHA-256 `B4A955B9485FE65092040698702474980A3DE58C26DB9BE9062F9CC9D5E5158C`）。
- 对 5 个已由日志确认正常停机、对应端口未监听且没有活动消费者的隔离 MySQL 数据目录，按路径清单只处理 ≥64 KiB 的 `.ibd`、`.dblwr`、`ibdata*`、`undo_*` 和 redo 文件；不包含日志、binlog、dump、密钥或身份材料。使用独占句柄及 NTFS `FSCTL_SET_COMPRESSION 0x9c040`（format 1），10,882 个文件逐个核对 SHA-256 与长度，全部相同，实际分配减少 `2,046,902,272` 字节。计划 SHA-256 `836D54391B3F9B3E607F54F0315E7200AA00B455B1B5F7EC037FE238FE790E22`，结果 SHA-256 `105E222CEC3F5446A6DCF35337E4D0788E5DFD1442E7D2186BC44BABC010DE12`；完整输入/结果逐文件记录在 `.local/disk-cleanup-20261022-round02/stopped-db-compression-plan.json` 与 `stopped-db-compression-result.json`。
- 对一个非稀疏 AVD 镜像执行 `compact.exe /c /q <absolute-path>`，退出码 0；逻辑长度 `2,153,709,568`、SHA-256 `03A03DB7D317AEF86691C75D2282DC4EEE7202B89D2180386C0AD7C1B2339247` 前后不变，实际分配减少 `25,894,912` 字节。稀疏 AVD 镜像未改。命令回执、SHA 与分配量见 `.local/disk-cleanup-20261022-round02/avd-image-compression-plan.json` 和 `avd-image-compression-result.json`。
- 本轮两项压缩合计实际分配减少 `2,072,797,184` 字节；清理后实测 C/D/E 可用字节为 `67,122,212,864` / `37,696,974,848` / `195,634,053,120`。前后盘余量另受其他写入波动影响，按文件分配量报告本轮释放值。
- 未删除文件、未停止或连接运行中的 MySQL、未碰 ADB/设备、共享 Android 工具链、其他项目或外部归属不明目录。已有 D 盘外部审计显示 `D:/tmp` 约 0.34 MiB，不能形成有意义的释放；共享包仓库、其他项目依赖和大目录继续保留。最终基线、验证与盘余量见 `.local/disk-cleanup-20261022-round02/final-checkpoint.json`。
