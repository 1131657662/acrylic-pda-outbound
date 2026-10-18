# E07/D06-D07 故障撤回 fail-closed 隔离消费者组合（run913，2026-10-18）

本批验证刚收口的 Web 故障撤回显式隔离开关没有破坏现行撤回/重新发布消费者路径。输入、人员、设备、仓库、签名和数据库均为新建合成隔离事实；R07 已取消的换机进度承接、多跳及接收者确认/激活链没有恢复。该批不满足原 PDA、原身份或现场回执条件，不计 D06/D07 L2、E07 整体、D17、E06 L2、D18 L3 或总目标。

## 输入与执行

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`，运行前 HEAD 与远端一致；继承脏项未暂存、还原或重排。
- 命令：`powershell -NoProfile -File tools/e07-d06-d07-emulator-run.ps1 -Run 913 -MysqlPort 19770 -HttpPort 19771 -WebPort 19772 -EmulatorPort 5618 -Avd Pixel_6`。
- 证据目录：`.local/goal-20261011/fault-restart-consumer-913/`；隔离数据库与 userdata 位于 `.local/e2a-cross-20260925-913/`。
- 运行器显式设置 `VITE_FAULT_WITHDRAWAL_ENABLED=true`，只用于本轮隔离 Web 消费者；默认构建仍保持关闭，见 `GOAL_E07_D06_D07_WEB_FAIL_CLOSED_GATE_RUN01_2026-10-18.md`。
- 输入 `private/input.json` SHA-256：`53C067CDFB862CFD0F6EF8A85748371DE9B8D91C74C8A1D23F3BC40E7BA96D07`。

## 实际结果

| 层 | 结果 | 证据 |
| --- | --- | --- |
| 服务端 fixture/HTTP/MySQL | `FaultRestartConsumerFixtureIT` `tests=1, failures=0, errors=0, skipped=0`；真实隔离数据库完成非空迁移与消费者输入生成 | `xml/fixture.xml`、`fixture.log`、`final-database.sql` |
| Python | 只读消费者 `consumerCount=3`、`scanCount=2`，输入读取前后 SHA 一致 | `private/python-result.json`、`python-consumer.log` |
| Android | 四条 instrumentation 均有 `OK (1 test)`：新设备从头作业、强停保留、撤回消费和新进程恢复 | `android-1..4-*.log`、`private/android-*.json` |
| Chrome | `expected=2, skipped=0, unexpected=0, flaky=0`；覆盖丢响应后刷新/原键重试、重发和浏览器存储失败不发送撤回 | `playwright.json`、`playwright/*/trace.zip`、`private/web-result.json` |
| 数据库与停机 | 末次 dump 非空；隔离账号认证停机成功，19770/19771/19772/5618/5619 已无监听 | `final-database.sql`、`authenticated-shutdown.log`、`environment-after.json`、`processes-after.json` |

Chrome 日志保留一条 Vite `ResizeObserver loop completed with undelivered notifications` 控制台错误；Playwright 两项业务测试均通过，未发现业务失败或输入混淆。Android 结果保留 `localDeletionAllowed=false`。

## 关键绑定

| 产物 | SHA-256 |
| --- | --- |
| `apk/app-debug.apk` | `D3BF52BEC1EA5BE4B8ADE03CEF3A882E4BFE0C688A5771C6AA86B47399F4C84E` |
| `apk/app-debug-androidTest.apk` | `3097CC2367756997AA17DD3E407ED22048DD02C5CD503685C7FAE57476453561` |
| `playwright.json` | `0668C3118502FB9724D9640DD74D58D4F62693B53558AF977C6BD900AEA19853` |
| `private/web-result.json` | `3AF03BC36781B9AE8371FA43D9B8545B71A13CB46957C708670154F2CABF300F` |
| `private/python-result.json` | `9E24CB24600F45E0993719720365FDB548E973C8F168C23A2270F2377790C41E` |
| `final-database.sql` | `27909D3260264097D44EBDBD5866B58214AB84BFF7D5FDA0AAC5ACB71552C292` |

源码入口绑定：`web/src/orders/OrderFaultWithdrawal.vue`、`web/src/orders/fault-withdrawal-feature.ts` 和 `tools/e07-d06-d07-emulator-run.ps1` 的运行前 SHA 记录在 `.local/goal-20261018-resume/takeover-current/status-before-run913.txt` 同批工作树中；本批没有产品源码改动。

## 边界与下一步

- 本批是新的 Android/HTTP/MySQL/Chrome/Python 隔离消费者组合，证明显式隔离开关下的现行故障撤回路径可运行；合成身份不能替代原 PDA、原身份、现场实物或业务回执，因此不计 D06/D07 L2。
- 默认关闭构建、权限、服务端事务和 `localDeletionAllowed=false` 未放宽；没有连接原库/现网、使用正式签名、部署、Release 或合并 `main`。
- 共享 3306/33318/5173、ADB server 和 USB M71 只观察未停止；run-local MySQL、Spring、Vite、emulator 均已认证停机/退出。
- 继承 WIP、失败 XML/日志、handoff-final01 快照和已取消 recovered-confirmation 源码继续保全；不启动旧 run03。下一步仍是具备可信原身份/现场输入后再补 L2，或推进不依赖该条件的独立 O/U 缺口。
- 收口时核查 run-local datadir/storage/userdata 约 222 MB、证据目录约 41 MB；按持续清理授权尝试删除，Windows 执行策略拒绝递归删除，未绕过，释放 0 bytes，目录保留并登记在本批断点。

## 提交前记录

- 改动路径：本批仅新增本报告、历史索引、进度索引和看板断点；影响层为证据/文档，不修改业务源码或生产配置。
- 必跑清单：新隔离非空迁移、真实 HTTP/MySQL fixture、四条 Android instrumentation、Python 只读消费者、Chrome 两项、认证停机；另行执行 `git diff --check` 与 `tools/version_facts.ps1 -Check`。
- 复用依据：fail-closed 入口改动及运行器显式隔离开关的当前工作树 SHA；run913 使用新 attempt、新输入、新数据库和新端口，不拼接旧业务通过结论。
