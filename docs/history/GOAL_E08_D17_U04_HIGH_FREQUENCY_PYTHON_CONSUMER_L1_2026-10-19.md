# E08 / D17 U04 高频 Room Python 消费 L1（2026-10-19）

本轮对 run925 的四个 Android Room 设备 tar 执行独立、只读 Python 消费。消费器没有连接 MySQL、HTTP 或 Android 设备，也没有写入产品数据库；结果仅补齐 U04/D17 的 Python 消费证据，不计 D17 整体、E06 L2、D18 L3、第 8 节闸门或总目标完成。

## 输入与命令

- 工具：`tools/high_frequency_room_consumer.py`，本轮实际消费路径为 `preparation-device.tar`、`home-device.tar`、`scan-device.tar` 和 `save-scan-device.tar`。
- 输出：`python-preparation-consumer.json`、`python-home-consumer.json`、`python-scan-consumer.json`、`python-save-scan-consumer.json`；四个输入 tar 在读取前后 SHA-256 均保持不变。增量绑定见 `evidence-index-python-consumer.json`，原 `evidence-index-final.json` 保持不变。
- 直接受影响验证：Python 解包到临时目录后以 SQLite read-only URI 连接，执行 `integrity_check`、`foreign_key_check`、Room schema 版本、准备请求/五分片/授权摘要、capture/scan/guard 关系、连续设备序号和失败确认状态断言；BLOB 和签名/回执 JSON 只输出长度与 SHA 摘要。

## 结果

| 输入 | SHA-256 | Room | 草稿 / capture / scan / guard | capture 状态 | 取消链表 |
| --- | --- | --- | --- | --- | --- |
| `scan-device.tar` | `7b90fa9bc9b635b8ea7a6ba544f8c923d42c56b8f550431692cef06bf2648b98` | `integrity=ok`、外键 0、schema 40 | 1 / 2 / 1 / 1 | 1 `COMMITTED`、1 `INVALID` 且已确认失败 | submission、handover confirmation、activation、recovered confirmation 均 0 |
| `save-scan-device.tar` | `2dd5c26d34ae8fa479c574480b9d4637ebfc438d3bef016749edac4bd0797f01` | `integrity=ok`、外键 0、schema 40 | 2 / 4 / 2 / 2 | 2 `COMMITTED`、2 `INVALID` 且已确认失败 | submission、handover confirmation、activation、recovered confirmation 均 0 |
| `preparation-device.tar` | `44af4512545774daa80785fa284c9e207f141e4b2d9f9dfacc77133476a4f9ba` | `integrity=ok`、外键 0、schema 40 | 0 / 0 / 0 / 0 | 1 preparation、1 request、5 part chunks | submission、handover confirmation、activation、recovered confirmation 均 0 |
| `home-device.tar` | `53fbca46601bae7f017e48a0a1a53740ab2574a1355e7bc536ca84a779fde09d` | `integrity=ok`、外键 0、schema 40 | 0 / 0 / 0 / 0 | 1 preparation、1 request、5 part chunks | submission、handover confirmation、activation、recovered confirmation 均 0 |

两个输入的 draft 均为 `SCANNING`，保存的 scan 均为 `ACTIVE`；每个保存 scan 只对应一个 committed capture，重复输入只产生 acknowledged `INVALID` capture，guard 唯一且不指向重复 capture。消费结果与 run925 的设备 tar/Room 事实一致。

准备页和首页 tar 均保存同一套非空授权请求、完整五分片和 2 条库存/2 条可用性事实；首页消费只验证 Room 快照，不把导航页面误称为新的业务写入。

## 范围判断与复用

- 仍有效范围：U04 高频离线草稿、保存扫码、重复包拒绝和暂停/恢复后的本地 Room 只读消费。
- 已取消范围：换机进度承接、多跳、接收者确认/激活链；消费器显式断言这些表为 0，没有恢复旧 worker、dialog、fixture 或 bridge。
- 复用依据：Android 入口与输入来自 `history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN925_L1_2026-10-19.md`；工具新增后才执行本次 Python 消费，不能用历史 Python/fixture 结果替代本次输入。
- 处置：保留工具和四个 JSON 输出，作为后续 L2/L3 候选的开发证据；仍需 Chrome、原模拟器恢复、非空迁移组合和统一最终候选，故不得升级为完整闸门。

## 失败与保全

本消费者此前三次失败均无产品写入：SQLite 连接未显式关闭、业务别名/表名映射错误、capture 主键误用 `captureId`。本轮初次准备/home 探查另因 BLOB JSON 序列化和异常路径锁定临时 SQLite 失败，摘要为 `prep-home-inspection-failure.md`；原失败资料继续保留，修正后四项消费均退出 0。敏感签名/回执字段已改为长度+SHA 摘要。临时目录清理前摘要为 `python-temp-cleanup-before.json`；C 盘临时目录及本次 D 盘失败临时目录删除尝试均被执行策略拒绝，未绕过，清理后回执另存 `python-temp-cleanup-after.json`。

结论：这是一次真实 tar 输入上的独立 Python L1 消费通过，不能替代 Android L1、Chrome、真实 HTTP/MySQL、原模拟器恢复或 D18 全链 L3。

## 本轮提交记录

- `tools/high_frequency_room_consumer.py`：Python 只读消费层；必跑语法检查、四个真实设备 tar 消费、输入 SHA 前后不变、Room/关系断言和敏感字段摘要检查；复用输入范围与 Android 事实 `GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN925_L1_2026-10-19.md`。
- `docs/history/GOAL_E08_D17_U04_HIGH_FREQUENCY_PYTHON_CONSUMER_L1_2026-10-19.md`、`docs/DEVELOPMENT_PROGRESS.md`、`docs/ORCHESTRATION_STATUS.md`：验收索引与当前断点；必跑 `version_facts.ps1 -Write/-Check`、当前区行数/字符数和 `git diff --check`；复用 writer/input/环境事实 `high-frequency-python-run01/writer-takeover12.json`。
- `high-frequency-run06/python-*-consumer.json`、`python-temp-cleanup-after.json`、`prep-home-inspection-failure.md`：输入消费、失败保全及清理证据层；必核 SHA、零产品写入、敏感字段不落原文、进程/监听、前后字节和盘余量；复用 `python-temp-cleanup-before.json` 与 run925 设备 tar。
