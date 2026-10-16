# E08 独立 SKU 返回导航 L1（2026-10-16）

本批补验 E08/U01–U04 的一个直接客户端缺口：在真实系统栏和软键盘安全区域内完成“数据 → 独立 SKU → 身份 → 真实软键盘输入/清除 → 返回 SKU → 返回数据页”。四档字号均通过，正式收口为该导航切片 L1 开发备份；不计 E08/U 整体、E06 L2、D18 L3 或总目标完成。

## 输入、范围与写权

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，运行输入 HEAD/远端为 `302b9eb37470ef272dce0d7895f572c0662301f9`。本批开始前的完整工作区输入由 `.local/goal-20261016/e08-sku-navigation02/input02.json` 保存；`input03.json`、`input04.json` 与其逐项相同，均为 5384 项，清单 SHA-256 为 `27fee1cfe647e5459c3151b99614df3cdc17908e7da60063285dffb941f31ce9`。对应 status、binary diff、未跟踪副本、worktree、旧看板、写权、进程/监听和 C/D 余量均保存在同一证据根。

唯一写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天已明确 `complete-released` 且复查 idle，其他同目录进度聊天 idle。证据快照中的旧 `released` 或 owner 字段不作为写权依据。现有 Android、文档、工具、服务端测试及 `server/physical-disposition-last-request.json` 等继承改动均保留。

## 改动 → 影响层 → 必跑清单 → 复用依据

| 改动路径 | 影响层与必跑 | 复用依据 |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/DataCredentialLayoutDeviceTest.kt` | Android UI 导航；测试 APK 构建；真实 AVD、系统栏/IME 安全区滑动、非凭据文本输入/清除、两次返回 | 产品 UI/XML 与身份页布局输入按前次报告精确路径/SHA 复用；证据中 `evidence-manifest01.json` 的 `reusedProductInputs` 记录九个产品路径 |
| E08 证据脚本与报告 | 输入绑定、守恒、环境收尾 | `.local/goal-20261016/e08-sku-navigation02/input02/03/04.json`、`evidence-manifest01.json`、`manifest-double-check01.json` |

前次 `GOAL_E08_CREDENTIAL_LAYOUT_L1_2026-10-16.md` 中的导航失败保留为历史事实。本批仅把真实安全区域滑动作为新依据，未改写历史失败，也没有复用 1538 或其他旧失败库。

## 实际实现与验证

旧 `ScrollToAction` 不能可靠处理真实系统栏/IME 可见范围；本批改为读取 root window insets，计算系统栏与 IME 的安全上下界，并在最多 12 次实际慢速滑动后检查目标控件完整落入该范围。SKU 返回后立即确认数据页已选中。测试只输入并清除非凭据文本，不点击预置、核验、撤销、导入、激活或任何生产决定入口。

先构建测试 APK（`build01-result.json`，退出码 0），然后在同一原始 `TemporaryAdjustment1364` AVD 的 `emulator-5564` 上实际运行四档独立 instrumentation：

| 运行 | 字号 | 结果 | 用时 |
| --- | ---: | --- | ---: |
| `font100-01` | 1.0 | passed | 54.859 s |
| `font130-01` | 1.3 | passed | 52.001 s |
| `font150-01` | 1.5 | passed | 62.777 s |
| `font200-01` | 2.0 | passed | 76.369 s |

每档的 `consume_device.py` 独立读取设备 SQLite 副本：83 张表、328 行，业务变化 `{}`；358 个既有文件长度/SHA 不变，包含 session、绑定、救援凭据和原作业证据。`profileInstalled` 作为 AndroidX 标记单列，实际也未变化。100%、150%、200%截图已目检；200% SKU 返回按钮完整可达，身份输入框保持单行横向滚动与永久标签。

首轮 `font100-01` 运行器错误发生在 instrumentation 启动前，原因是本地 runner 将 baseline 中已有 `font` 再次作为关键字传参；指纹与摘要保存在 `font100-01-runner-failure.json`，没有伪称存在原始业务日志。修正 runner 后四档均通过，原失败原件继续保留。

产品 APK 复用 SHA-256 `bf4428d36f6ae773aaaae6dc31e4dbbae8aa0c10c9a8bf918d965e57550e5c69`；本批测试 APK SHA-256 `897a01749df10bd986aa34b469f20d665dee3a0c68fc2b626306e1785fc6a443`。证据清单 `evidence-manifest01.json` 共 2018 个文件、161406238 bytes，双次长度/SHA 核验一致；manifest SHA-256 为 `a742604e23fa7c21d116171d5d109d1b47f94d6f9f0ba2e7c19caaf9b784581c`。

## 环境收尾、边界与未验

测试结束已恢复字号 `1.0`、硬键盘 IME 设置 `0`，清空 reverse，强停 App；`device-final01.json` 保存最终状态。没有本批新服务、MySQL、Chrome 或隔离库写入；共享 mysqld 仍只观察，不连接或停止。C/D 余量、进程、监听与停机证据保存在 `disks-04.json`、`processes-04.json`、`listeners-04.json`。

正式报告后 `input06.json` 与同号附件保存完整 5385 项输入及实际环境；C/D 可用空间为 82991923200/22088376320 bytes。`review01.json` 逐项确认从切片前输入只有本批五条路径变化，5380 项继承输入不变，九条产品路径 SHA 可复用，提交测试字节与四档实际运行输入一致。作者、执行与自审为同一助手，独立 Python 不等于独立人员审查。

本报告只证明上述 Android 导航切片 L1。E08/U01–U04 完整组合、真实 HTTP/MySQL 受影响回归、E06 L2、E09 候选和 D18 最终 Android/真实 HTTP/MySQL/Chrome/Python/非空迁移/原模拟器/恢复 L3 仍未完成；旧失败计数和原件不清零。生产默认、七类生产决定、`localDeletionAllowed=false`、原库/现网/main/发布边界保持关闭。

本批完成后下一动作是更新事实块与断点，提交并推送开发备份，然后继续 E06 受影响组合、E07/E08 剩余、D13–D16 与 E09；本批不宣告总目标完成。
