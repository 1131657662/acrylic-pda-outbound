# 交接 outbox 写后回滚接续方案（2026-10-01）

输入树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 SHA `2068b6952fb7677b8bd8ad610975ba6b82221f80`；版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。接续[自主方案](AUTONOMOUS_PLAN_2026-10-01.md) R1b，上一轮分页/P08 缺陷仍停止；A1.1 不重跑。

依据[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md) A2.3：“出站消息与交接命令同事务/可恢复，有反例”；A1.3：“原请求恢复、不可变回执、占用/账务一致性有反例与组合验证”。已有 `E2aHandoverOutboxIT` 覆盖成功与写前拒绝，未证明实际 outbox 插入后的回滚。旧 `@Primary AuditWriter` 方案触发冻结白名单守卫，失败源码/证据保留，不放宽守卫。

## 本批范围与退出标准

| 子项 | 动作与退出标准 | 证据及依赖 |
| --- | --- | --- |
| R1b-SQL 离线门 | 恢复旧草稿中已校验的 typed 数据快照和合成夹具，以测试库 AFTER INSERT 触发器替代 Java Bean 覆盖；编译、10 项快照测试、冻结白名单守卫和完整服务端单测无新增失败 | XML、源码摘要、parseErrors=0；不改产品、历史迁移/角色/默认开关 |
| R1b-SQL 写后回滚 | 新库内通过真实 command service 达 TARGET_PREPARED；仅本 case 的 HANDOVER_ACTIVATE 审计插入后，触发器验证新资格、旧资格撤销、outbox/回执已存在、轮次已推进、非空占用仍在，随后以专用 SQLSTATE/错误码/消息失败；16 表带类型快照回到写前状态 | 新 attempt、唯一故障消息、before/after 快照；不删除既有守卫，不重跑旧 attempt |
| R1b-SQL 原键恢复 | 移除本测试触发器后同一 commandId/正文成功，原键重放字节一致且无重复写；重启应用上下文后原键与快照仍一致，非空占用保全 | 原命令/原回执、提交快照、重启快照、completed marker；服务层真实 MySQL，不声称 HTTP/PDA/worker/断电已验 |
| 收尾 | 结果明确，失败不自动重跑；新库停机后 dump/完整 ZIP 回读校验再清新 datadir；显式暂存提交推送 | 磁盘/SDK adb/七项保护摘要前后；保留历史 attempt 和全部失败证据 |

测试触发器只存在于按身份核验的新隔离库，不登记为产品迁移。SQL 信号只证明 AFTER INSERT 已到达；不宣称审计行已独立提交。完整快照验证覆盖表行，不要求回滚 MySQL 自增计数。出现既有测试新增失败、真实产品缺陷或政策待决即登记停止该子项，继续其他独立 R06/R07 工作。单助手、不启 AVD；生产及删除边界保持。
