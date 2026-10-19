# U04 独立采集文本按变化更新 L1（2026-10-19）

独立页通过 `IndependentWorkActivity` 收集引擎状态；队列每次增减都会调用 `IndependentWorkDisplay.render`。显示器此前每次对摘要、计划、原任务、候选、定位和明细赋值，包括内容没有变化的区域，未满足U04避免连续扫码刷新整页的要求。

## 有界设计与标准

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 `a2591fd35f01ec8f1675a6e7a7c76965dcdba732`。按总目标第3节与AGENTS已获自主裁定授权，在既有显示器中比较文本内容，仅内容变化时赋值；控件、引擎状态、队列、持久化和权限不改。采用本类私有TextView扩展，供九个相同目的的文本区调用，不引入跨页面缓存、基类或共享架构。

验收标准：64次队列变化只改反馈区，原任务/计划/已保存明细保持；实际保存后数量、重量、最近明细及反馈均更新；切到另一页准确替换明细；声效与保存文案的既有两项显示回归继续通过。测试观察真实TextView文本通知与最终内容，不从实现私有缓存取值。仅证明显示层，不声称设备帧率或吞吐改善。

全部继承路径与SHA、patch、实际环境见 `.local/u04-independent-render-run01/takeover.json`、`environment-before.json`。替换前看板见 [前断点](ORCHESTRATION_STATE_BEFORE_U04_INDEPENDENT_RENDER_20261019.md)。测试使用新D盘构建目录及独立日志，既有APK/tar/日志不覆盖。

## 验证与边界

命令由同根 `run-test.ps1` 运行：`gradlew.bat -p android -I <本证据根>/isolate-build.gradle :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.IndependentWorkRenderTest --tests com.acrylic.outbound.pda.ui.IndependentWorkDisplayTest --offline --console=plain --no-daemon --max-workers=1 --project-cache-dir <本证据根>/project-cache`。TEMP/TMP/Java临时目录和编译输出均在该D盘根，复用已安装共享依赖；未运行assemble任务。

| 阶段 | 实际结果 | XML SHA-256 |
| --- | --- | --- |
| 预修复 | 新增3项全部按预期失败：64次队列变化令九个区域各收到64次通知；保存和分页同样改写全部区域。既有声效显示2项通过；退出1 | `before-IndependentWorkRenderTest.xml`：`096caf6916cee3a1acffccb5d3f6b61fb5346d2ccd5e67f27838b858b031d070` |
| 修复后 | 新增3项与声效显示2项全部通过，0失败/错误/跳过，退出0。队列仅反馈区64次；保存只更新摘要/反馈/明细；分页只更新明细 | `after-IndependentWorkRenderTest.xml`：`d17d4c12048dfcb1148e190bf183d320007e441ac3b2fa2fb58cbd11e4429965`；声效XML：`efdb0c90c04f3026d01dfc611548a11d43763f9c5a6afba58312c93474d0582e` |

预修复输入/日志SHA为 `860f51134678984b88e17d7087a7ae08f75e53cf0114e000aa33517b1159f927` / `ac84005a912df1f132b37aee8db8e7e4c34e7e158bc965b08aaccd5f77c8ef5e`；修复后输入/日志为 `7c171b2058aacf9407d45ae79db70ef943c74ccd849c7a103444bc1941b902ef` / `b36d8859bd7b1c3d03f69246c9cc660b2c9a6564fc0a8ef4e54463d893a7ea96`。显示器源码SHA `2ced1a03bb26fdb935a3026e89f8f8ef2f880c6b129e9d5eacf9e8362b9dda78`；测试源码SHA `b36112b0b31b69bcec663e8abf74bff01b3e186cc647358bf1d493c67631ddc5`。

作者自审：内容比较直接读取TextView，不另缓存任务或业务状态，候选清空/错误变化仍可赋值；不触碰可编辑输入和按钮资格，不引入动画或网络。显示器49行、新测试91行，`git diff --check`通过。仍每次计算显示字符串，未证明CPU、分配量或帧耗时改善；后续实际候选设备性能仍须独立验收。不是独立人员审查。

构建输出留给下一项独立作业页布局验证复用，尚有消费者，不清理；声效切片被拒缓存不重试。测试日志/XML、继承改动及原证据保留。切片开发提交、最终路径清单/哈希与环境将在同根 `git-delivery.json`、`closeout.json` 登记，开发备份不等于统一最终候选。

本项只覆盖合成Robolectric显示L1；未运行Activity、构建/安装APK、连接HTTP/MySQL、Chrome、Python或迁移验收。不会以此代替U04/D17整体、D16、D18或第8节最终候选闸门。取消链、旧run03、原库/现网、正式签名、部署/Release/main及 `localDeletionAllowed=false` 边界保持。
