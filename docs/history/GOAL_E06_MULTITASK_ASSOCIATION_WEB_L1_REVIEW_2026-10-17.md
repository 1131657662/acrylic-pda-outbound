# E06 多任务关联网页 L1 接续复核（2026-10-17）

本页是 `GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_2026-10-14.md` 的接续复核，不把历史 L1 扩大为 E06 L2 或 D18 L3。复核由当前唯一写者完成，未启动数据库、HTTP、Chrome 或产品构建。

## 输入与证据

- 历史业务输入绑定 `91fe25d8bb83fccfd0b42f428bd734f825750f51`；`e06-multitask-input-binding02.json` 记录 2418 个服务输入未变及 JAR SHA `981b5c301a07f72763816a010a840b495e0a10acb5b5d7551d58f9f9033b5337`。
- `e06-multitask-web-input01.log`、`typecheck02.log`、`build02.log`、`contract01.log`、`consumers03.log`、`old-consumers02.log`、`chrome01.log`、`finish01.log` 均存在；原报告记录各命令退出零，分别覆盖输入、类型、构建、契约、13/16 项消费者、1 个 Chrome 场景和全表事实。
- `e06-multitask-web-evidence-review02/facts-review.json` 离线重读 180 张表，确认一主关联、一成员、一幂等和两条操作事件，旧业务行保留，三次真实 200 响应的 key/body/epoch 相同。
- `preserve-1538-e06-multitask-web-l1-01` 重新核对 347 个文件的长度/SHA、dump SHA `8a652d08ac17a8bbad5f530cb495fa79ec688e0a255c59eecfcec6899fd65aec`、停机退出和零监听；复核结果写入 `.local/goal-20261017-resume/resume-20261017-0710/e06-web-review01.json`。

## 当前输入漂移

相对历史输入，当前工作区有 10 个服务源码/测试文件和 2 个浏览器输入条目发生漂移（包括保全目录中的历史 JAR与追加的截图调用）。这些路径属于后续开发或历史证据保全，不覆盖原报告绑定；当前工作区未以此复核宣称 E06 组合通过。原 Chrome 结果中去掉截图调用后的重构脚本仍与实际场景 SHA 一致，漂移和同作者复核均保留。

首轮只读复核脚本因 PowerShell 短函数名与 `Get-History` 别名冲突失败，未写入产品、数据库或证据；失败指纹保留，改名后的复核成功。`typecheck02.log` 本身只有命令输出，退出零依据来自原报告和接续输入，不由缺少尾部文字臆造。

## 结论与边界

历史 E06 多任务网页 L1 报告及其输入、消费者、Chrome、全表和 1538 保全证据完整，可作为同一输入的 L1 开发备份复用。当前输入已漂移，未新增业务链；E06 L2、D18 L3、现场/真机和总目标仍未完成。下一步先按看板执行 D13 受影响组合，再推进剩余 D/E/O/U 和最终候选回归。
