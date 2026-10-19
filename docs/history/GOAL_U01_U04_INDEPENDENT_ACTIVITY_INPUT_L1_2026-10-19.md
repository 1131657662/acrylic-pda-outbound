# U01/U04 独立采集 Activity 手输输入交接 L1（2026-10-19）

本切片补独立采集页的成功手输与确认区交接。UI规范要求手输键盘不得遮住候选确认和确认键；成功接收后收起次级输入、清除输入焦点并请求隐藏IME，拒收继续保留原输入。收起手输只把候选交给显式确认，不代表耐久保存，也不改变授权、VOID、暂停或封存语义。

## 范围与标准

只修改 `IndependentWorkActivity.kt` 与 `IndependentWorkPanels.kt`，增加实际 `IndependentWorkActivity` 的主机测试。验收覆盖：成功接收后面板/焦点/IME状态交还且仍需显式确认；拒收保留输入；F1、Enter、F2和重复按键不重复保存；IME打开时硬件快捷键不执行业务操作；返回暂停保留未确认原件且不封存。回归独立采集面板与首屏布局。

## 实现与验证

- 成功 `engine.receive(..., "MANUAL")` 后由面板控制器清输入、收起输入区、复位toggle、清焦点并请求隐藏软键盘；拒收路径不触碰编辑区。普通状态渲染仍保留用户未提交的编辑。
- 预修复 `.local/u04-independent-activity-run01/before-result.json` 退出1：6项中1失败，`acceptedManualInputHandsFocusBackBeforeConfirmation` 发现输入面板仍可见；五项其余行为通过。原失败 XML、日志与输入均保留。
- 修复后 `.local/u04-independent-activity-run01/after-result.json` 退出0：实际 `IndependentWorkActivity` + 文件型 Room/native SQLite 的 `IndependentActivityInputTest` 6/0/0/0，既有 `IndependentWorkPanelsTest` 7/0/0/0 与 `IndependentFirstScreenTest` 4/0/0/0，共17/0/0/0。输入/命令/逐个源码与配置SHA在 `after-input.json`；输入SHA `c5d820d248df8355f67e32e61a5ac62669af69f47818cf60eb9f5697aed83bae`，日志SHA `ec45693bbf65018ee6ccc577c38c8fedba8303e21326f5dcbb3a4f0dc1bea7cc`。
- XML SHA：Activity `bb05f700365a1ac3a850e7402da67664cf83ff3df954157d28f0adc15cfae6ea`；面板 `2145179c11ae29b630fd3e3ef2a7720d58e0e31883f9cc9c2f1b8adfacfe0d5f`；首屏 `f39494f5cab49517e58d7d21e365417d1f82f36cc157e0d037fcebf8c5f87d3a`。
- `git diff --check`通过。run-test隔离D盘build/cache/temp，复用既有C盘Gradle依赖；未构建APK、启动模拟器、对设备写入/安装或连接数据库/业务HTTP。
- 收口清理只处理本切片隔离 `build`、`project-cache`、`temp`：3228个可重建文件、逻辑104,507,921字节，目标均删除；D盘可用空间由60,963,651,584增至61,073,866,752字节，C/E未变化。清理前逐文件SHA、输入/依赖SHA、消费者核对及前后余量见 `cleanup-before.json` / `cleanup-after.json`；日志、XML、失败输入、结果、源码、APK/tar和唯一证据保留。

## 输入、环境与边界

固定树 `D:/acrylic-pda-outbound`、`codex/development`，输入HEAD和接管时远端为 `bec1c16c0f1c853b5b990b531636698364cd00b4`。逐路径清单、输入SHA、patch、写者和远端在 `.local/u04-independent-activity-run01/takeover.json`；其217项继承路径在 `.local/u04-independent-activity-run01/inherited-protection-before.json` 核对 `changed=0`。收口的223项完整WIP/哈希、patch和最终环境在 `.local/u04-independent-activity-run01/closeout.json`、`closeout.patch`、`environment-closeout.json`。

作者自审而非独立消费者。口令/任务/身份为测试合成fixture，实际Activity、扫描引擎、文件Room和native SQLite被主机测试消费；Robolectric合成Insets/IME及KeyEvent不等于原生输入法、实体键或设备证据。USB M71仅由ADB只读枚举，未操作设备数据；Debug Keystore哈希保全在环境JSON，未使用正式签名。MySQL80系统服务及3306/33060仅观察；配置库UUID未知，不连接、不停止。

**历史自动审批拒绝仍有效**：声效收尾缓存清理被拒绝，唯一理由为 `blocked by policy`；旧目录保留，释放0字节，未以其他方式重试。证据 `.local/goal-20261019-resume/sound-closeout-takeover03/cleanup-rejected.json`。

本批仅U01/U04/D17键盘交接与确认前Activity主机合成L1；未完成真机/原生IME/实体键、U01/U04/D17整体、E06 L2、D16、D18或总目标第8节验收。没有恢复换机进度承接、多跳、接收者确认/激活链或旧run03；未连接原库/现网，未使用正式签名、部署、Release或合并main，`localDeletionAllowed=false`保持。总目标继续active。
