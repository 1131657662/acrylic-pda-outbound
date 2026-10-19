# U04 独立作业队列满边界 L1（2026-10-19）

`IndependentScanEngine.enqueue` 拒收满队列输入后，只递减内部计数，页面仍显示被拒收输入。修复立即发布递减后的计数，再保持入口已满的阻断。已有测试补充拒收后的计数和阻断断言；没有新增共享抽象。

## 输入、设计与覆盖

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD 与只读远端核验为 `d58c86554c4d7abb14d8922ddbd7f3e873fc98c2`。完整继承路径/哈希与原差异保存于 `.local/u04-independent-queue-run01/takeover.json`、`status-before.z`、`tracked-before.patch`；只提交本切片。
- `IndependentWorkActivity` 的扫描和手动输入经共享 runtime 进入该引擎，`IndependentWorkDisplay` 直接渲染 `ui.queued`。路径、源码哈希、覆盖和复用依据见同根 `source-review.json`。
- 验收标准先于修复：阻塞第一条输入的 actor 边界；一条在途加 64 条排队，第 66 条拒收后计数仍为 65、入口阻断且不可接收；暂停后释放 worker，全部 65 条原输入按连续序号保留为 RECEIVED，未产生有效扫描，更新租约最终归零。
- 按 AGENTS 与总目标第3节已有授权，自主选择与正式/TEMP 引擎一致的最小状态发布修复。没有改变队列容量、业务资格、Room schema、网络契约或权限。

## 当次验证与失败

运行器 `run-test.ps1` 记录命令、输入文件 SHA 和子进程环境。命令为 `gradlew.bat -p android -I <本证据根>/isolate-build.gradle testDebugUnitTest --tests com.acrylic.outbound.pda.independent.IndependentScanEngineTest --offline --console=plain --no-daemon --max-workers=1 --project-cache-dir <本证据根>/project-cache`。

| 阶段 | 实际结果 | 原证据 SHA-256 |
| --- | --- | --- |
| 预修复对照 | 4 项、1 失败、0 错误/跳过，退出 1；第51行 expected 65 but was 66 | `android-prefix02.xml`：`13737e4f3d9a9868d51cbee11b68ef9f79e2f173f2d2ae60336797fcd69ecd88` |
| 修复后 | 4/0/0/0，退出 0，16.708s | `android-final.xml`：`d547eb780c0dc70fa1a17bba397f48bf916cae3851b5b8b29d2e1deb2e12a5ed` |
| 原运行器失败 | Windows PowerShell 把 Java 的环境提示 stderr 提升为 NativeCommandError；未取得 XML，不计业务失败 | `prefix-wrapper-failure.txt`、空 `android-prefix.log`、`android-prefix-input.json` 保留 |

改用已安装的 pwsh，并只在 native 调用周围允许 stderr、显式保存退出码后取得上述有效结果。其余三项覆盖手动显式确认与新引擎、末 SQL 失败后按原 ID 恢复、过期资格拒绝。`git diff --check` 通过；引擎127行、测试103行，作者自审完成，未冒充独立人员审查。

原输入 JSON 的 “in-memory Room” 措辞不准确；实际为 `PdaDatabase.open(context)` 的文件型 Room、Robolectric native SQLite 和合成凭据。原件未改，勘误见 `evidence-wording-correction.md`。

## 证据与边界

所有本机证据位于 `.local/u04-independent-queue-run01/`；TEMP、TMP、Java 临时目录、构建和项目缓存均在该 D 盘根，复用已安装共享依赖。

| 文件 | SHA-256 |
| --- | --- |
| `android-prefix02.log` | `00b10d83c40d0bef181697e1c99a4a24cc97f3275087c410ae829ad4f762ba80` |
| `android-prefix02-input.json` | `e34268cf901ee20d18bdb30bf861b4bb9a1e69f58cfd5da90306ff389051ae77` |
| `android-final.log` | `d9e2e70d6c43373e06ac02c6ef9372affd4ba2ab8046293b20655604ec27a461` |
| `android-final-input.json` | `669816f04bb0af8e3cc45aa0ff54a3b3ca810d23ed2fad9cc327e10dc874a36e` |
| `IndependentScanEngine.kt` | `92563199be795849110a72887e5cfed0c226f713e7cc33204c06002b2a920f2c` |
| `IndependentScanEngineTest.kt` | `efe8e829b5c127c159aa9427b7909950d70801cb1559f6d3715dc02bcac13aed` |

此外只读检查了已完成 tar 审计的 audit02 manifest，76个输出文件的长度/哈希均匹配；JSON 有效，无需修复。该检查见 `audit02-manifest-check.json`，没有重跑 tar 审计或旧设备用例。

环境与清理见同根 `environment-closeout.json`、`closeout.json`，开发提交/远端回执见 `git-delivery.json`。原 APK、tar、日志、正式身份、继承源码与唯一证据保留。

切片收口清理仅删除本次可重建的 generated/intermediates/kotlin/kspCaches/tmp 和 project-cache，保留 reports/test-results/outputs、全部日志/XML及临时目录中的原日志。3176文件逻辑长度54,217,608字节；D盘余量61,199,761,408→61,260,079,104字节，卷净增60,317,696字节。逐文件路径/SHA、命令、依赖输入及无消费者证明在 `cleanup-before.json`，结果在 `cleanup-after.json`。首轮 Windows PowerShell 长路径哈希失败、尚未删除任何内容；保留 `cleanup-first-failure.txt` 后由已安装pwsh在同范围完成，非绕过自动审批。

本切片只计合成输入主机 U04 L1 开发备份；未构建/安装 APK、启动 ADB/模拟器、运行 Activity、真实 HTTP/MySQL、Chrome、Python 业务消费者或迁移验收。没有连接系统/原库/现网、使用正式签名、部署、Release 或合 main。取消的换机承接、多跳、接收确认/激活与旧 run03 不恢复，`localDeletionAllowed=false` 保持。U04/D17 整体、D16、D18、最终候选及第8节仍未完成，下一步选择具体有效产品缺口。
