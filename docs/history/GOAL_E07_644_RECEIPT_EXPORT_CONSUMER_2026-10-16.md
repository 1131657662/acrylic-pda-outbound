# E07 644 原回执与固定版本导出独立消费

补齐[644受影响组合](GOAL_E07_COMBINATION_644_ACCEPTANCE_2026-10-16.md)原回执/Excel的独立Python核对。没有启动或连接数据库，没有重跑644业务。此结果仅补该历史切片的消费者证据，不计E07 L2、D14/D15整体、D18 L3或总目标完成。

## 输入和影响范围

固定树`D:/acrylic-pda-outbound`、`codex/development`，HEAD/远端`faf6017bed949b91a8f1af65b01617df567602b9`；唯一写者本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`。清理聊天notLoaded、既有complete-released回执保留，进度聊天idle。证据根`.local/goal-20261016/e07-644-consumer01/`，input01/02全量5344项相同，SHA `6990f4a56c364b6947af2de5567087c355b48dcd70e374cf5a7c85901f10de0b`；旧看板、全部Git差异/未跟踪原件、写权和环境保存。

| 改动路径 → 影响层 | 本次实际必跑 | 复用依据 |
| --- | --- | --- |
| 本地`consume.py`、`export_consumer.py`、`receipt_links.py` → 独立只读算法 | 原RSA、声明/外层逐值、原payload摘要/顺序、固定版本Excel/精确重量/审批关联、全目录哈希 | 直接读取已停机`.local/e2a-cross-20260925-644/`原件，源公钥SHA绑定，不生成签名或新资料 |
| 报告/索引/看板 → 追溯 | 输入无漂移、原件双次清单、历史命令/日志/停机记录SHA核对 | 原644报告记载的真实HTTP/MySQL仅作历史引用，不当本轮执行或最终候选 |

## 实际结果与自审

- bundled Python执行`consume.py`，命令、stdout/stderr及exit0见`consumer01/02.log`与`.exit`。初轮已通过；自审新增原payload与签名决定到Excel的交叉绑定后第二轮通过，原脚本另存`consume-before-cross-bind01.py`，两份结果均保留。全部操作只读原件；openpyxl缺默认style提示保留，未保存或改写工作簿。
- 原XML确认1 test、0失败/错误/跳过；启动/完成标记及认证停机身份相符，身份字节SHA `d6f9372628d224f6f7075bd5b4ebc09de1b66f0d3c54b908ee2c53254e3e788f`。精确绑定644 datadir、数据库与端口；原runtime-after记录exit0/零监听，当次19350/19434/19435/19436亦无监听。
- 原公钥SHA `5c01dde6eee2c9e5b00689f92bb18e3e8bc511f90deaa4d02c58d377508c2240`。92个唯一签名全部RS256验签并严格核issuer/audience和签名正文与外层逐值一致：70更正回执、11 TEMP决定、11内嵌成功回执；全部出现的capabilities为false。该钥为原隔离测试钥，不代表现场原PDA或生产钥。
- 已扣库存/未扣库存各三份Excel，固定结果版本1/2/9；每份7页且无公式。核原条码/产品代码/设备和capture/dispatch文本ID、原采集顺序/重量文本、整数克、有效明细、汇总和审批依据。原重量601500g；版本2为601520g，版本9撤回后601500g，调整分别0/1/8。不同版本原扫描逐值不变，最终有效明细等于原版本。
- 原stored submission逐字摘要匹配签名成功回执，原扫描capture/序号/条码/代码/重量与导出相同；版本2审批及版本9撤回确认的签名决定与导出净值一致，审批人与撤回确认人不同，审批引用与原line匹配。未保存的canonical更正命令全文不补造，不声称新验证其命令摘要。
- 取消与实物退库各保留`CYCLE_REOPENING_EXISTS`原拒绝正文，未将其计为新的HTTP执行。PDF、私有配置、datadir和binlog等只作哈希守恒，不声称本轮解析PDF/恢复数据库或全表事实。
- 完整644目录526文件、241738205 bytes，初轮前后及第二轮前后路径/长度/SHA相同；清单SHA `c051af1a1bd174f0be2d6d64f61edf3e521151ac87c0a57bb9cc50ebe5f8191f`。原历史断点的看板归档/命令/日志/runtime四项SHA亦核对不变，见`review01.json`。

消费者代码SHA：`consume.py`为`d25219d92c6eeb583974b0054a657fbe637e252cf348f6a4b639d113ea1db10e`，Excel算法为`4dbb24c0f0a5bc75796d331b64b554ec2bf533a5834fc50fdb857492c904969f`，交叉绑定为`7ee3567a75d0b4a16a6923d6392cd953cb4b1b40a9ba15e45a2fbbdd42c7662b`。作者/自审为同一助手；独立算法不等于独立人员。

## 限制与接续

未获得644完整全表快照/dump恢复输入，因此不追认该部分；E07完整Android/Chrome/原模拟器恢复组合仍未完成。643及其他历史失败/暂缓原件保留。无本批服务/库/重型任务，既有共享mysqld未连接/复用/停止，原TEMP AVD保留。input02 C/D余量83,037,458,432/32,992,894,976 bytes，新证据在D盘无清理。

按明确文档路径提交推送开发备份，全部继承改动继续保留。下一步按D13–D17验收单元核对E07尚缺的真实恢复/客户端组合和D16正常带防重资格依赖，不删除guard制造资格；1540浏览器审批阻断不绕过。生产七类能力、TEMP归档、`localDeletionAllowed=false`及原库/main/部署/发布边界保持，总目标active。
