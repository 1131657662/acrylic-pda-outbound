# U04 独立采集声效未就绪提示 L1（2026-10-19）

独立采集页此前没有消费 `WorkSound.error`。保存已经完成但声效资源未就绪时，正式/TEMP 作业页会显示“声音未就绪，请以页面保存状态为准”，独立页只显示保存文案，操作者无法看到声效故障提示。本切片把该提示接入独立页，并保留保存状态作为业务事实。

## 输入与改动

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `f0702c53461c64c0b8514033fd7ad3a40a9ca931`；继承改动未整体暂存或回退。
- `IndependentWorkActivity.kt` 将应用声效状态传给显示器；`IndependentWorkDisplay.kt` 在保存/队列文案后追加非空声效提示，空提示不产生空白行。
- 新增 `IndependentWorkDisplayTest.kt`，验证保存文案与声效故障同时可见，以及空声效提示不生成空行。没有改变队列、Room、网络、权限、迁移或生产开关。
- `IndependentSoundReadinessTest` 已有证据证明 `WorkSound.error` 会在保存回调后设置；本切片首次组合运行因该既有 Robolectric native SQLite 输入失败而未采用，失败原件保留。

## 验证

实际成功命令：

```text
gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.IndependentWorkDisplayTest --offline --console=plain --no-daemon --max-workers=1 --project-cache-dir .local/u04-independent-sound-run01/project-cache-display
```

结果为 2 tests、0 failures、0 errors、0 skipped，退出码 0，XML `display-only.xml` SHA-256 `6275d309c8474d7e847f89a61e1ec5748e5caaf5061b48c18e5f368b1c4a06d9`。原始日志 SHA-256 `ea0c97be66f9af86038cad95dd8a8b70525e8827ddf2162fbb9049ce8e1301f6`，输入记录 SHA-256 `36a2a4f30065be3d3cbe18cc9c655520613870fe10ab469df2d693db0990bbbe`。完整输入、命令和源码哈希见 `.local/u04-independent-sound-run01/verification.json`。

首次组合命令同时执行既有 `IndependentSoundReadinessTest`，3 项中 1 项因 `SQLiteCantOpenDatabaseException` 失败，退出码 1；日志 SHA-256 `815a64dd696447c528662a6d9d16772afbfde4b2b84bcb7e4a963ee0bca93315`，没有把该失败归因于本切片，也没有机械重复它。新增显示测试的独立复验已通过。

## 边界与未验

这是合成 Robolectric UI L1，只证明独立页显示层。没有构建/安装 APK、启动 ADB/模拟器、运行真实 Activity、连接 HTTP/MySQL、执行 Chrome/Python/迁移或现场声卡验收；旧设备用例、旧 run03、tar 审计和唯一证据均未重跑或覆盖。`localDeletionAllowed=false`、取消的换机承接/多跳/接收确认/激活链、D16/D17/D18 及第 8 节闸门仍未完成。该切片不计 U04/D17 整体或最终候选通过。

## 开发交付与收尾更正

开发提交 `a2591fd35f01ec8f1675a6e7a7c76965dcdba732` 已推至 `origin/codex/development`，随后 `git ls-remote` 核验相同；main未变。回执见同根 `git-delivery.json`。收尾接管的全部223条路径/哈希、patch及环境在 `.local/goal-20261019-resume/sound-closeout-takeover03/`；提交后脏路径减少是本切片提交结果，不代表继承WIP已验收。

以上“没有启动ADB”仅指显示测试过程：收尾只读 `adb devices -l` 检查曾自动启动ADB daemon，实际PID2436监听5037，USB M71可见，无模拟器。没有安装、运行设备测试或操作设备数据。MySQL80只观察系统服务与监听，UUID未知，未连接或停止。

清理两个本切片Gradle项目缓存的命令被自动审批在执行前拒绝，返回 `blocked by policy`，未提供详细原因；目录保留、未重试、实际释放0字节。路径、余量与结果见 `sound-closeout-takeover03/cleanup-rejected.json`。旧APK、tar、日志和唯一证据保持。
