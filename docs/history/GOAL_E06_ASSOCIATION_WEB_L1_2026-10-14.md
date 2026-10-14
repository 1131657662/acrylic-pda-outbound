# E06 人工关联网页产品链 L1

输入 `3da3e81acf79730777f6253bf2da980171efccc1`，固定主树开发分支，单助手。沿[网页方案](GOAL_E06_ASSOCIATION_WEB_DESIGN_2026-10-14.md)完成完整单任务关联产品入口；仅L1开发备份，不计E06、完整D/O/U或D18收口。版本事实只见看板生成块。

网页完整分页读取候选，显式选目标、逐项排除其余候选、填写说明并引用实物回执。先真实读取实物依据和检查上下文，再持久保存原key/body/epoch，跨页锁保护发送；刷新、双页和丢响应恢复同一原意图。有既有关联时读原回执，其他读取错误不当成不存在。人员/仓库/原件变化阻止异步发送；回执分别核请求、原上下文、原件、目标和实物引用，网页摘要检查不称独立RSA验签。页面区分原核对截点、实物记录及关联结果，始终待对账、不授记账/放行/删除。

`web/src/independent-review/association-*、AssociationForm及核对页、实物结果文案 → L1候选分页/持久原意图/回执绑定/Chrome入口 → 六项定向单测、类型检查/构建、真实Chrome丢响应/双页/空本地存储读回、严格数据库守恒 → 未受影响服务与契约沿人工关联服务L1输入复用，精确比较见下表；Android与实物写协议未变，复用各已绑定证据。L2与最终L3待实际执行。`

证据前缀：`A=.local/goal-20261013/e06-association-audit-1527`，`B=.local/goal-20261014`。

| 验证 | 结果与证据 |
| --- | --- |
| 定向单测 | `B/e06-association-web-unit02.log`与`unit03.log`各六项通过；后者实际消费本轮Chrome原请求/回执。修改目标/隐藏上下文/实物引用/正文、越权能力字段、排除重复/空理由、本地串身份和持久化回读失败均拒绝 |
| 类型/构建 | `B/e06-association-web-typecheck01.log`和`build01.log`退出零，独立输出`A/web-dist01`；静态预览5175用于本轮，保留5174环境未改 |
| 真实Chrome | `A/browser02/run.log`：一场景通过，零重试；实际HTTP将候选分页限制为一条，完整读取四候选。漏排除理由零POST；首次成功响应丢失，重载后双页恢复，三个POST的key/body/epoch逐字相同且均200；再次重载及全新浏览器上下文读回原关联，不显示新意图入口 |
| 原件与页面 | `browser02/association-http-*.json`保存实际签名回执，`browser-original-command.json`保存原意图；390宽无横溢出，`390-pending.png`/`390-recovered.png`已目视相关面板可读。截图是内部滚动容器所在截点，不冒充整页全内容或完整UI重设计 |
| 守恒 | `A/post-browser02/conservation.json`：仅增加一条关联/一条幂等及对应审计；全部原审计逐行不变，新增事件只WEB_LOGIN与一次relay.independent.associate，其他全量业务事实不变。`successful.json`按任务实际读回与网页原请求/回执一致 |
| 服务复用 | `A/server-reuse-input-comparison01.json`与`web-runtime-inputs01.json`核服务/契约原输入及固定JAR；服务137项、默认关闭/权限、尾SQL回滚、同键并发、异文、锁后撤权、固定钥Python/新JVM沿[服务L1](GOAL_E06_ASSOCIATION_L1_2026-10-14.md)复用，未机械重跑。新增网页不是新的服务协议/迁移；迁移冻结后L2仍须实际执行 |

原Android包/历史签名沿保全输入；主管、库存、正式目标、实物与网页关联决定明确合成，不称原设备现场事实。实物首切片与关联默认开关、七类生产决定/TEMP归档、localDeletionAllowed=false均保持。

失败保留：`unit01.log`的测试加载器误将Zod已有.js后缀追加.ts，修限定后六项通过；`browser01`点击Element Plus底层输入被可见span拦截，尚无关联POST。`fixture-failure03`逐表证明关联零写、幂等不变、原业务事实守恒，仅WEB_LOGIN/Session后在同夹具修标签点击续验，不换库重种。`fixture-failure01/02`新增审计解码的二进制UTF8/误列名错误、`post-browser01`Windows默认GBK读中文原请求错误，其完整事实快照已取得，原日志保留；改为保留raw hex、按实际event_type解码及显式UTF8后有效守恒通过，不算产品失败或倒推旧通过。旧CLI失败计数与1522/1523事实未判限制继续继承。

作者自审覆盖持久化前检查、跨页锁后重读原请求、身份切换防晚发、完整分页/重复游标、只吞明确不存在、原回执摘要与实物上下文、默认能力字段及窄屏可读；不是独立人员审查。既有实物Chrome场景仅断言文案同步，实物写协议及队列未变，本轮不称重跑该上传场景。

1527已dump/hash/认证停机并核零监听，保全`B/preserve-1527-e06-association-web01`；340文件长度/SHA保持，仅压缩213适用文件，D余量增加205086720字节。截点C/D可用52130824192/23243595776字节，无删除；API使用唯一日志名，原失败、dump/datadir/binlog和HTTP/签名全部保留。预览与1519保持。Git回执及继承保护见`B/e06-association-web-delivery01`。

下一专用跨机冲突处置正向、短装/多任务适用路径和E06组合，再E07恢复/外置副本、E08留存/UI及E09候选全部闸门。总目标active且未完成。
