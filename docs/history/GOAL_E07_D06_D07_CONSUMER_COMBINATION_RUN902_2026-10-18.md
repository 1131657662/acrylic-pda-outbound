# E07/D06-D07 撤回重发消费者组合（run902）

本切片在全新隔离 attempt 上补齐坏机撤回、重新发布和新设备从头作业的 Android、真实
HTTP/MySQL、Chrome 与 Python 消费者组合。输入中的人员、设备、仓库、订单、签名和浏览器
会话均为合成隔离身份；本报告不恢复换机进度承接、多跳或接收者确认/激活链。

## 输入与执行

- 命令：`powershell -NoProfile -ExecutionPolicy Bypass -File tools/e07-d06-d07-emulator-run.ps1 -Run 902 -MysqlPort 19760 -HttpPort 19761 -WebPort 19762 -EmulatorPort 5612 -Avd Pixel_6`
- attempt：`.local/e2a-cross-20260925-902/`；消费者证据：`.local/goal-20261011/fault-restart-consumer-902/`。
- 输入 `private/input.json` SHA-256：`1DCBD850259C87CF56AA8ED978B1F655EFDF77E95B47FE059D34C6539E821CC1`。
- 运行输入 HEAD：`5d44a6efe1eb22c576ce4d4c7a35154af03988a5`；没有连接原库/现网或 USB M71。
- MySQL 使用隔离账号和非空迁移；服务端、Vite、Pixel_6 emulator 均在新端口启动，结束后认证停机。

## 实际结果

| 层 | 结果 | 证据 |
| --- | --- | --- |
| 服务端 fixture/HTTP/MySQL | `FaultRestartConsumerFixtureIT` `tests=1, failures=0, errors=0, skipped=0`；生成 3 个消费者、2 个扫描项 | `xml/fixture.xml`、`private/input.json` |
| Python | 只读消费者 `consumerCount=3`、`scanCount=2`，输入读取前后 SHA 一致 | `private/python-result.json` |
| Android | 四条 instrumentation 各 `OK (1 test)`：新设备从头领取/下载/扫码/冻结/成功；强停后保留成功；原机撤回消费；新进程重放撤回 | `android-1..4-*.log`、`private/android-*.json` |
| Chrome | `expected=2, skipped=0, unexpected=0, flaky=0`；覆盖丢响应后重试/刷新、重发，以及本地存储失败时不发送撤回 | `playwright.json`、`private/web-result.json`、Playwright trace |
| 非空数据库/停机 | `final-database.sql` 722,299 bytes，180 个 `CREATE TABLE`、55 个 `INSERT`；MySQL 由隔离账号认证停机，19760/19761/19762/5612/5613 收口无监听 | `final-database.sql`、`authenticated-shutdown.log`、`environment-after.json` |

Android 结果保持 `SYNCED`、`WITHDRAWN/RECALLED` 和 `localDeletionAllowed=false`。Vite 日志保留一条
`ResizeObserver loop completed with undelivered notifications` 控制台错误；Playwright 两项均通过，
没有业务失败或数据库写入事实混淆。

## 代码与输入绑定

| 路径 | SHA-256 | 影响层 |
| --- | --- | --- |
| `tools/e07-d06-d07-emulator-run.ps1` | `3EA8183BC84C94DDB30495A2F651DA630818A86593A8FDBE33664246CD444C52` | 隔离运行器 |
| `tools/fault-restart-input-consumer.py` | `ECBA27BEB80D4CF5060243C4C539EAA0AF144F004A21DDE2052CFD32A7226ADE` | Python 只读消费者 |
| `server/src/test/java/com/acrylic/outbound/inventory/FaultRestartConsumerFixtureIT.java` | `21FAF0EC39861522D9C38F1A93BCC0559684EB4C21AA94907D686744B2D08A9F` | 真实隔离 HTTP/MySQL fixture |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/FaultRestartDeviceTest.kt` | `B8CC61251B75F2C5515067144E498DFD764639601228E868A66A465BB646414D` | Android 产品消费者 |
| `web/tests/fault-restart.spec.ts` | `6732670727E863D3C3643D50BBCD2D485ED565603D90994C7D3F5836CE29820C` | Chrome 消费者 |
| `.local/goal-20261011/fault-restart-consumer-902/xml/fixture.xml` | `C19932C4AD92E5AFD64F0D11DE4611200EB6181B11F3994A1B961DDE39F9B654` | fixture 回执 |
| `.local/goal-20261011/fault-restart-consumer-902/playwright.json` | `803DA2BD8965C2FFA38635686E3AE8F1F7AF857B0B5BA06BFC820671AE350099` | Chrome 回执 |
| `.local/goal-20261011/fault-restart-consumer-902/final-database.sql` | `86271982D9D55FAC1909233C40149E750855967B8C31AF16EF1550708FBF2718` | 非空数据库事实 |

## 判定与边界

run902 是一份新的 Android/HTTP/MySQL/Chrome/Python 隔离开发候选，补齐 run745 未覆盖的
消费者组合。由于输入身份、订单、设备、密钥、数据库和浏览器均为合成事实，仍不计 D06/D07
L2、E07/D14/D17 整体、E06/E08 L2、O05、D18 L3、第 8 节闸门或总目标完成。原 PDA、现场
实物/回执、真实身份与统一最终候选仍需单列；`localDeletionAllowed=false`、生产默认关闭和
取消范围边界保持不变。

## 提交前记录

- 改动路径：本次仅新增本报告、历史索引、进度索引和看板当前区；没有产品源码或生产配置改动。
- 必跑清单：run902 fixture、隔离 HTTP/MySQL、非空迁移、Android emulator、Python、Chrome、认证停机；另行执行 `git diff --check` 与 `tools/version_facts.ps1 -Check`。
- 复用依据：run744 的同一消费者入口和 SHA 绑定；run902 使用新 attempt、输入、端口和数据库，不拼接旧业务通过结论。
