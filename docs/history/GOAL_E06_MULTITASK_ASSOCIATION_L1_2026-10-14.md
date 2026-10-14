# E06多任务人工关联服务L1开发备份

输入`e4c430f7c35130cdf98a80303e928c0e62e65316`，固定主树、单助手单写者。采用[最小设计](GOAL_E06_MULTITASK_ASSOCIATION_DESIGN_2026-10-14.md)：独立主管一次显式选择完整任务，共同记录一个无正式作业目标；原任务、实物和冲突依据分别保留。追加不可变成员台账、交叉防重、逐产品合计和精确短装批准；不产生提交、占用、记账、放行或删除。专用开关默认关闭，旧单任务格式及原键可读。版本事实只见看板生成块。

L1影响行：`RelayAssociation{Groups,GroupPolicy,GroupStore,Plan,Policy,Store}`/`RelayAssociations`、成员迁移、关联契约及策略测试 → 服务协议/事务/权限/聚合/迁移/原键读取 → 定向单测、bootJar、契约、真实HTTP/MySQL正向/双权限/跨仓/自审/重复/短装/并发/异文/末SQL/锁后撤权/default-off/新JVM及固定钥消费。Android与网页未修改，复用输入`e4c430f7c35130cdf98a80303e928c0e62e65316`的[实物验收](GOAL_E06_PHYSICAL_ACCEPTANCE_2026-10-14.md)与[增量网页L1](GOAL_E06_CONFLICT_EXTENSION_WEB_L1_2026-10-14.md)对应原采集和单任务入口；这些不证明新增多任务网页或E06组合通过。

本机证据前缀：`A=D:/acrylic-pda-outbound/.local/goal-20261013/e06-conflict-audit-1537`；`B=D:/acrylic-pda-outbound/.local/goal-20261014`。

| 验证 | 原证据 | 结果与边界 |
| --- | --- | --- |
| 定向单测/构建 | `B/e06-multitask-unit01` | 206项，零失败/错误/跳过，bootJar成功；源字节无漂移 |
| 非空/default-off | `A/prepare01` | 保全原输入169表、59非空表严格守恒，仅Flyway改变；11个新增空表。新多任务意图409关闭且零业务写 |
| 真实正向与事务 | `A/enabled-resume02` | 双任务成功；同键并发仅一份主记录/成员/审计/幂等增量；原键与两原件GET一致；异文/成员复用/双权限/跨仓/原采集者/错上下文/错实物/缺短装批准拒绝。末条幂等SQL故障全部业务表回滚 |
| 撤权 | `A/lock01` | 实际MySQL LOCK WAIT后停用actor，HTTP401 SESSION_REVOKED；恢复原键一致。仅核明WEB_LOGIN审计增量，原业务事实保持 |
| 独立算法/重启 | `A/restart-independent01` | 新JVM多任务默认关闭：原键与各成员可读，新键409关闭。Python使用固定外部公钥RSA核签、原Android包/合成第二任务包/实际附件逐字下载核对，全业务表不变 |
| 补充反例 | `A/extra04` | 成员UPDATE/DELETE触发器拒绝；合计超目标、目标产品不符均409，每次严格全表守恒 |
| 输入绑定/作者自审 | `A/binding01` | 2418源输入与单测前hash匹配；部署JAR、关联class及全部迁移字节一致。锁/权限复核、完整成员绑定、防重/聚合、不可变台账、原key/default-off、末SQL及业务能力边界自审完成 |
| 契约 | `B/e06-multitask-contract01.log` | 实际退出零 |

部署JAR SHA256：`981b5c301a07f72763816a010a840b495e0a10acb5b5d7551d58f9f9033b5337`；外部公钥SHA256：`5f17c0234838910f45049ad288e4caeb93c3e7da944ccf776d69cecf2554f559`。独立算法仍由同一作者运行，不称独立人员审查。第二任务、主管身份、库存和实物观察/签名明确合成，不冒充原PDA现场事实。

失败保全：`enabled01`夹具自成员预期409但实际404，先核原baseline守恒再修预期续验；lock脚本导入旧编号被已有目录防覆盖拒绝，SQL/HTTP尚未执行，指纹见`B/e06-multitask-lock-fixture-failure01.json`，原日志保持；健康等待误用了不存在入口，未业务写。`extra01–03`分别1136列数、1452先设revision外键、1054旧列名；SQL原参数/stdout/stderr/退出码、源DDL及事务未提交/守恒证据全部保留。累计三次不清零，extra04依据完整DDL/显式列/外键顺序新证据修正后有效通过，不将旧失败倒推为通过。旧CLI/UI/native/恢复暂缓计数保持。

1537用途结束后`B/preserve-1537-e06-multitask-service-l1-01`完成dump/hash、认证停机、19332/19436零监听；347文件长度/SHA不变，220适用文件NTFS压缩，D盘增加206954496字节，原datadir/dump/binlog及日志零删除。保留预览1494/1519。收尾C/D可用47087218688/17101631488字节，重型动作前复测。

这是服务L1开发备份；多任务网页未实现，E06受影响L2/最终L3未执行，不能由旧切片拼接为通过。七类生产决定、TEMP边界、`localDeletionAllowed=false`及D16标准保持。下一步网页显式逐成员核对/合计/原键恢复与Chrome成功路径，再E06组合、E07–E09及所有剩余D/O/U闸门，总目标active未完成。
