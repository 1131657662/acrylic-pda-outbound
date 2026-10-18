# 开发看板

<!-- CURRENT_STATE_START -->
[Version facts: see board generated block; original bytes in the evidence board-before.md.]

总目标 D01–D18、§8、O01–O05/U01–U04 与 UI 重设计保持 active、未完成。局部 L1、开发备份或暂缓均不计完整 L2/L3。R07 取消的换机承接、多跳、接收者确认/激活链不恢复。

## 精确输入、写权与保全
- 固定树 `D:/acrylic-pda-outbound`、`codex/development`；本轮全量改动见 `.local/goal-20261018-resume/o01-filter-run01/dirty-candidate01.json`；提交后精确 HEAD/远端见 `git-postcommit.json`，本轮输入为 `dd624a5f7a21b87a58ff6a08755c5eef450db5f4`。
- 唯一源码写者仍为本聊天 `01a147c5-0495-7703-ba98-2e7637cbc619`，登记见本轮 `writer-resume.json`；无其他活动源码写者/子代理，无在途构建或设备测试。
- 202 条继承脏项已逐路径 SHA/zip/patch 保全；复核除看板外均未改变。原 handoff-final01、run03 失败、原身份、未跟踪资料及取消范围源码继续保护，不还原/重排/覆盖，不用 `git add .`。
- 本轮全量输入/证据/环境根：`.local/goal-20261018-resume/o01-filter-run01/`；`input.json`、`dirty-input.zip`、`tracked-input.patch`、`handoff-bindings.json` 与 `source-bindings-final.json`，最终快照/绑定见 `dirty-candidate01.*`、`tracked-candidate01.patch`、`evidence-index-final01.json`。提交后 Git 回执另见 `git-postcommit.json`。
- 替换前当前区已归档 `history/ORCHESTRATION_STATE_BEFORE_O01_FILTER_2026-10-19.md`，原字节另保存在本轮 `board-before.md`，前序所有失败及范围限制沿归档保留。

## 本轮有界结果
- [O01/U04 原记录筛选与大字确认 L1](history/GOAL_O01_U04_FILTER_L1_2026-10-19.md)：原序号精确/产品字面筛选，20 条有界窗口，VOID/原序号保留，非法条件重建继续阻断；正式/TEMP 实际产品页及数据库摘要守恒通过。
- 最终 filter/layout 6 项、Debug/AndroidTest 构建与 lint 通过；第一轮 20 项中 18 通过、2 个既有 `WorkAdjustmentTest` SQLite 开库失败保留，不写全绿、不机械重跑。
- 独立隔离 run914 真实 HTTP/MySQL 签名准备及原生设备输入；最终 100%/130%/150%/200% 字号各通过。200% 旧确认按钮裁切已改可滚动正文，两按钮完整可见；空原因无写入，有原因仅增一条 VOID、原 27 capture 不变、原序号及本机防重规则一致，重建后禁用。
- 正式单经真实 HTTP 发布/领取/准备、writer 保存，筛选不改全表，冻结未上传后不可调整且正文守恒。均为隔离合成输入；仅本子项 L1 开发备份，不计 O01/U04 完整、D17、E06 L2 或 D18 L3。
- 早期 scrollTo 系统栏裁切、swipe 系统 Home、旧 200% 对话框失败、fixture 包装器误码与首次重型任务短暂重叠均留原件；最终事实见专项报告，不声称全程调度合规。
- 开发备份 21aaabd2 的提交前事实 Check 因文档行尾转换失败，串行 shell 未停止；已保全并补记，后续逐命令检查退出码。修正结果见本轮 `facts-correction-results.json`，不改写产品验证。

## 前序候选与未验
- 取消范围逐路径审计见 takeover05/08/10 报告；recovered-confirmation worker/dialog/fixture/bridge 无生产调用，R09 只读 display/reader 保留。旧 run03 与主机 fixture 同根因暂缓不重启。
- E07/D06-D07 run744/745/902/913 的撤回重发、非空迁移、Android/Chrome/Python 与恢复结果，及 E08 run903/904/911、U02/U03 的局部候选沿归档和 `DEVELOPMENT_PROGRESS.md` 索引；合成事实不补记完整 L2/L3。
- O02/O05 的 M71 SAF/ADB、电脑 XLSX、安全包跨运行消费与同机副本仅按各报告范围复用；外控介质、独立保管与真实业务回执仍缺，不能因同机文件或 USB 主机回读解除删除边界。
- D11/D13/D14/D15 只有明确范围的局部验收；D16 缺可信合法退出依据，O01–O05/U01–U04 余项、原资料/旧 BCD、统一最终候选和 D18 全链 L3 均未满足。

## 环境、空间与下一步
- 本轮 HTTP 19781、MySQL 19780/`acrylic_e2a_cross_20260925_914` 和 `Filter914_Pixel6`/5622 已停；`stop-result.json`、数据库身份/唯一 dump、最终设备 tar/Room/签名与 APK 哈希保留。19780/19781/5622/5623 已清零；本轮编译 daemon 经身份核实后停止。
- 共享/外部 3306/13316/13317/33318/5173、ADB 5037 和 USB M71 仅观察未操作。实际进程/监听见本轮 `processes-final-stopped.json`、`listeners-final-stopped.json`；不连接原库/现网。
- 本轮清理前已保存输入/依赖/结果/目标哈希与全脏快照；清理测试 datadir、AVD RAM/缓存命令在启动前被策略拒绝（blocked by policy），零删除/释放 0 bytes，未绕过。`cleanup-before.json`、`cleanup-rejected.json` 保留；userdata/Keystore、唯一 dump、APK、源码和失败证据均在。
- C/D/E 余量实测为 75,385,659,392 / 12,967,706,624 / 195,634,057,216 bytes，见 `disks-final.json`；新增产物均项目 D 盘，本轮无 C 盘副本。
- 七类生产决定、TEMP 生产归档、原库/现网、正式签名、main、部署和 Release 保持关闭；`localDeletionAllowed=false`，D16 标准不降低。
- 下一步先审计 U04 原明细状态筛选实现/证据缺口，再选可独立有界工作；沿保留设备续验时禁止使用旧启动器 `-wipe-data`。总目标继续，不以此切片收口结束。

<!-- CURRENT_STATE_END -->

> 长期规则见 AGENTS.md；版本事实只读生成块；历史从 docs/history/README.md 检索。
