# U04 原记录状态筛选原生消费者（2026-10-20）

只覆盖用户已批准的“全部 / 有效 / 已撤销 / 主管保留”筛选设计的隔离 TEMP 产品页面消费者；默认全部，只改变可见范围。没有产品行为、服务端、契约或迁移改动。本结果不记 U04/D17 整体、L2/L3 或总目标第8节通过。总目标保持 active、未完成；版本事实见看板生成块。

## 输入与保护

- 固定树 `D:/acrylic-pda-outbound`，输入 HEAD/远端开发分支 `bd86fe9d7c2e403fa9ab94ba10869cfce63c76a0`，分支 `codex/development`；远端 main `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。初始32条 tracked/untracked 路径和各 SHA、写者、进程、监听、盘余量在 `.local/goal-20261020-resume/u04-filter-ui-consumer01/takeover-manifest.json`。接管后运行事实 Write/Check，均退出0。
- 仅本聊天是 active 项目写者，未启动子代理。项目/系统MySQL仅观察，未连接。USB M71只读枚举，所有设备操作均明确指定 `emulator-5680`；未运行泛化 connected 测试、旧创建用例或旧 run03。
- 完整源码、schema、构建与wrapper配置输入清单 `input-files.json` SHA-256 `5ed3424ae03b88c30632bf1f3e0768ce7588347667a21217d40c9849bc9cd872`。隔离签名 TEMP fixture 从既有 `server/build/pda-temporary-contract-fixture.json` 按原字节复制，SHA-256 `6e4b9946dfc225d66f5c3ccfc84ffcbf21dd678eeeaadb9a68a62e0625cf8fdc`；未连接fixture原数据库。
- 新建 D盘 `avd-home/U04StatusConsumer01.avd`，独立配置/索引/datadir/data，未复制原userdata、未wipe任何AVD。启动和停机后均复核源Pixel_6的8个镜像SHA不变。debug签名，仅使用既有debug Keystore；正式签名未使用。

## 变更与验证标准

| 路径 | 责任与实际验证 |
| --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/AdjustmentStatusDeviceFixture.kt` | 防误跑guard、fixture SHA绑定、一次性新身份/空库检查、签名资料产品校验及真实capture/confirm/VOID写入器造数 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/AdjustmentStatusConsumerDeviceTest.kt` | 实际点击产品Spinner/按钮、分页/重建/组合筛选，读取83张Room表指纹并核对身份不变 |

造数方法单独执行一次，形成22条原记录：第1–20条为VOID，第21–22条为ACTIVE。临时准备走原 `TemporaryRepository` 与验签/事务，保存走 `TemporaryProcessor`，未直接插入scan或修改状态。网络/会话/时间边界为明确合成：复用测试签名fixture及其时间，在全新模拟器内写入测试会话。它不证明真实HTTP、登录登记、原身份、可信现场时间或业务审批。

页面消费者两次均读取同一批原记录，分别在实测1.0、2.0字号执行：

- 默认“全部”保留1–20/21–22原分页与序号；上一页/下一页真实点击。
- “有效”仅21–22；“已撤销”恰好20条且后面全不匹配，下一页禁用；重建保留选项。
- “主管保留”空结果、无下一页；清除恢复全部。未制造RETAINED行，也未调用被取消的接收者/激活链。
- 状态+产品+原序号组合只显示22；有效条件与非法0序号跨重建保持；清除恢复原记录。
- 两次各自的83张产品Room表count/hash摘要逐字相同，SessionVault读取的会话内容逐字相同。它是逻辑全表守恒，不声称运行元数据/整个应用目录tar不变。

## 实际执行结果

| 执行 | 结果 |
| --- | --- |
| `build01/execution-plan.json` 的独立 assembleDebug/assembleDebugAndroidTest | 退出0，BUILD SUCCESSFUL；旧APK未覆盖 |
| `seed01-command.json` 指定新造数方法 | adb退出0，instrumentation `OK (1 test)`，无失败/跳过 |
| `font100-first-command.json` 指定只读消费者 | adb退出0，instrumentation `OK (1 test)`；实测fontScale=1.0 |
| `font200-first-command.json` 指定同一只读消费者 | adb退出0，instrumentation `OK (1 test)`；实测fontScale=2.0 |
| `build01/lint-command.json` 的lintDebug | 退出0，0 errors / 508 warnings；未声称零警告 |
| `collect-evidence.py` 原始tar逐成员哈希及JSON复核 | 退出0，两次83表摘要守恒、结果字号/身份标志一致 |

原生命令均形如 `adb -s emulator-5680 shell am instrument -w -r -e adjustmentStatusConsumer synthetic-only -e contractSha256 <实际输入SHA> -e evidenceRun <独立目录> -e class com.acrylic.outbound.pda.AdjustmentStatusConsumerDeviceTest#<明确方法> com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner`。完整参数、原始日志、退出码、输入/输出哈希位于上述证据根；没有以adb退出0替代JUnit结果解析。

最终产品APK `apk/app-debug.apk` SHA-256 `8b9c162fda5af7aff4e23622d92c0069540c4a09808b884d3e6dced5fd279dd7`；测试APK `apk/app-debug-androidTest.apk` SHA-256 `dff19a7d43552510e8e70b0ab9a2478511e995791d78ece598b61d28c447a76d`。设备证据 `private/device-final.tar` SHA-256 `70ab5287cd9aa0e25a29298cf0e5020fed61f95c7bb430509c37d4a697af34b4`，逐成员哈希 `device-member-manifest.json`；汇总 `consumer-results.json` SHA-256 `01537d986cd723d34fce3227127131614d462ba2449ffc22d97f581c05ec9cf6`。

## 自审、停机及未验

作者/自审为同一助手；Espresso、Android原生产品入口与Python证据核验是工具消费者，不称独立人员审查。两个测试文件均低于200行，函数低于30行；只读消费者没有产品写入入口。截图只记录当时滚动位置，不能据裁切的非当前控件推论全页大字验收；控件实际通过安全可视区滚动和Espresso点击。

最初一条工具封装JS解析失败未启动shell；另一条AVD准备因误用PowerShell只读HOME变量在建目录前退出，改用局部avdHome后才启动。两者均非业务失败。停机第一次采样观察到模拟器自身20秒退出watchdog，端口已清空；等待其自然结束后的 `emulator-stop-settled.json` 确认emulator/qemu和5680/5681均为零，未强杀其他进程。完整停机/源镜像检查保留，未把首次未收敛采样写成已停机。

清理已复制保留APK及lint报告，并对三个可重建build/cache/tmp目录的3779个文件保存逐项哈希，共91,734,936逻辑字节；递归删除在shell启动前被自动操作审查以 `blocked by policy` 拒绝。没有删除，实际释放0字节，没有换方法重试；目录及新AVD、身份、原始tar/日志均保留。回执 `cleanup-before.json`、`cleanup-rejected.json`；当时C/D/E可用字节 `73775497216 / 48659804160 / 195634053120`。

仍未验：正式订单本次原生消费者、RETAINED非空可信历史输入、原身份/设备/实体键、真实HTTP/MySQL/Chrome、全部四字号及完整UI走查、统一最终候选L2/L3和第8节闸门。历史13项分页单测仅作为前序输入证据，不计当次重跑；O02 SQLite CANTOPEN暂缓继续，不机械重跑。

只暂存本次两个测试文件、本报告及两个索引的链接；继承WIP/原看板/取消链/排除JSON不混入开发备份。最终提交、远端与清理回执见证据根 `closeout.md`。生产开关、原库/现网、正式签名、main、部署/Release保持关闭，`localDeletionAllowed=false`。
