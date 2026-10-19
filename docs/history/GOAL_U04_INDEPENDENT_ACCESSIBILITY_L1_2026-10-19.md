# U04 独立采集队列计数无障碍隔离 L1（2026-10-19）

本切片修复独立采集页把高频队列计数写入 `polite` 操作反馈区的问题。计数变化不应反复打断“已保存/操作错误”等反馈，且队列信息仍须可见。

## 范围与标准

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `971f66eedaf9bd5050b7bf7f9e790c66121d327c`。本切片只改独立页显示器、布局和两个 Robolectric 显示测试；不改引擎、Room、权限、网络、迁移或业务链。

验收标准：队列从 1 到 64 连续变化时，队列文字更新到 64；队列控件可见且 `accessibilityLiveRegion=none`；保存/操作反馈保持原文并继续使用 `polite`；保存后的任务、行明细和计数显示不被队列更新清空。测试只观察公开 TextView 状态和通知次数。

## 实现

- `IndependentWorkDisplay` 新增独立 `workQueue` 文本更新；`workMessage` 只承载操作消息与声音错误。
- `activity_independent_work.xml` 为 `workQueue` 设置独立控件、默认隐藏和 `accessibilityLiveRegion="none"`。
- `IndependentWorkDisplayTest` 覆盖 64 次计数变化、可见性、live region 和操作反馈稳定性。
- `IndependentWorkRenderTest` 将通知计数和保存/明细回归绑定到独立队列控件。

## 验证

| 阶段 | 结果 |
| --- | --- |
| 首次运行 | 退出 1；显示测试 3 项中 1 项因测试先把空控件当作稳定反馈而失败；Render 3 项和 FirstScreen 4 项通过。Activity 6 项中 2 项命中既有 `SQLiteCantOpenDatabaseException`，未把它们改写为本切片结果。 |
| 第二次运行 | 显示测试仍因同一测试夹具时序假设失败；未机械重跑 Activity 套件。 |
| 最终运行 `after03` | 退出 0；`IndependentWorkDisplayTest` 3/0/0/0、`IndependentWorkRenderTest` 3/0/0/0、`IndependentFirstScreenTest` 4/0/0/0，共 10 项通过、0 失败、0 错误、0 跳过。 |

最终运行器为 `.local/u04-independent-accessibility-run01/run-after03.ps1`，命令和依赖输入见 `after03-input.json`；隔离 `build-after03`、`project-cache-after03` 和 `temp-after03` 均位于 D 盘，未使用默认 `android/app/build`。`after03.log` 末尾为 `BUILD SUCCESSFUL`。`git diff --check` 未报告空白错误。

证据哈希：

- `after03-input.json`: `ab8f4fc1b2cf0c7f538370cb6eb945399128a8a104874d1c69fd8173b66d3f49`
- `after03.log`: `f9245a1c749fee99e947d4b57cf1badd47c919e8a8f5958fded8d37dcda89ce1`
- Display XML: `59a6bf2d7ff261fff3e0d942ace28fd4e8610c63da8aef527abb1f7b28f88082`
- Render XML: `aa9fb538f6c551f3f560f0f2659657ca1f3c33b5076f8e395f8eaeddaa4587cc`
- FirstScreen XML: `643479c59e16196e1c36138f3c646de4a514b9b58199cc07500bf625270cc785`
- 最终四个范围文件 SHA-256：`IndependentWorkDisplay.kt` `26ea14c09858db23a9dd1ad3c0db7b5f4d0b7c3562572b0bf2c24c4c14459fb7`；`activity_independent_work.xml` `03912a0785457a9009f8dab503c7c7e34c8f4796f346b9e7b59f2c1bd5540682`；`IndependentWorkDisplayTest.kt` `94536a02a8fd42c6667673f7227985bbbe47fb08e9017f8a4de1e8a2b97395c0`；`IndependentWorkRenderTest.kt` `0dfd29f07bb7ed2c4f5bb54fe88defcf162bca72d248eab41e5071ffc4dab90c`。

## 继承保护与清理

接管清单 `.local/u04-independent-accessibility-run01/takeover.json` 的 218 项继承路径已重新计算：217 项 SHA 未变，唯一变化为 `docs/ORCHESTRATION_STATUS.md` 的事实块刷新；本切片四个源码/测试文件不在继承清单中。写报告前采样工作树为 222 项（未含本报告与归档），收尾全集见 `closeout.json`，继承 WIP 不整体暂存、还原或删除。

本切片六个可重建目录均保留：`build` 3205 文件/51,423,416 字节，`build-after03` 3200/51,280,519，`project-cache` 12/3,113,977，`project-cache-after03` 12/3,147,805，`temp` 7/7,032,903，`temp-after03` 5/4,194,014。清理命令在审批执行前以 `blocked by policy` 被拒，命令未执行，未生成删除前后回执，实际释放 0 字节；拒绝事实见同根 `cleanup-rejected.json`。不重试删除，不覆盖 APK、tar、日志或唯一证据。

## 环境与边界

Fresh 采样确认 MySQL80 父/子进程 4288/5188、3306/33060，配置指向系统 `my.ini`/datadir；`auto.cnf` 读取被拒，UUID 未知，未连接或停止系统库。ADB 2436/5037 只读枚举 M71 `22D2910042`，没有模拟器进程或设备数据操作。仅记录 debug keystore SHA `0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb`，未使用正式签名。

作者自审确认新增控件只承载显示，不改变队列计数、保存顺序或业务资格；未引入动画、网络或共享抽象。该证据是合成 Robolectric UI L1，不是独立人员审查，也未证明原生 TalkBack、IME、M71 真机、真实 HTTP/MySQL、Chrome/Python、U04/D17 整体、D16、D18 或第 8 节最终候选。`localDeletionAllowed=false`、取消的换机承接/多跳/接收者确认链及旧 run03 禁止范围保持；总目标继续 active。
