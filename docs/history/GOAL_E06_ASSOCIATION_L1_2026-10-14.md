# E06人工关联：服务端首切片开发备份

范围为[方案](GOAL_E06_ASSOCIATION_DESIGN_2026-10-14.md)的完整单任务人工关联，版本事实只见看板生成块。输入HEAD `dd22120e22ede5981f80e1aaa8934aedde2b066c`；仅L1，不计E06、D/O/U整体或最终候选完成。网页产品入口、跨机冲突处置正向、多任务与短装适用路径仍待实现/验收。

专用开关默认关闭，沿既有恢复门、身份/仓库与目标订单锁。主管须同仓双权限且不同于原采集者，绑定完整候选逐项排除、原实物签名/附件、原任务与结束原件；重核库存/映射/目标无既有作业、车号/产品/包数/快照。仅新增不可变关联记录和签名，保持原来源、正式单、库存、占用、提交、结果与账务；不授新增采集/放行/删除。一个原设备/任务与一个目标分别唯一，重复导出可按任务读原关联；原键优先恢复、异文拒绝。跨机重码仍阻断，不把拒绝计冲突处置通过。

## L1输入及实际验证

`server/relay/RelayAssociation*、新增台账迁移、contracts/independent-association.yaml及openapi → L1协议/权限/事务 → 定向测试/真实HTTP/MySQL/固定钥独立消费者 → 实物原件/附件算法未改变，复用实物验收页绑定4fead0c7abc1ef77e6ecff70dd5df6e44b98e5d5的原输入；本次重新消费实际检查和附件。Android/网页无改动，复用各原证据，不能计本次客户端验证；L2与最终L3待执行。`

本机证据前缀 `A=.local/goal-20261013/e06-association-audit-1526`，`B=.local/goal-20261014`。

| 验证 | 实际证据与结论 |
| --- | --- |
| 定向单测/构建 | `.local/goal-20261013/e06-association-unit-03`：137项零失败/错误/跳过，sourceUnchanged=true，bootJar成功；含关联策略/签名与受影响relay/identity范围。unit02也实际通过，随后仅增加目标实时映射核对，因此以unit03为最终输入 |
| 契约 | `B/e06-association-contract01.log`：OpenAPI解析/引用通过 |
| 非空存量/default-off | `A/restore-run01`及`A/disabled`：保全1514非空输入，JDBC旧业务表守恒，仅Flyway变化；新增表空。真实409关闭且关联零写。这是首切片定向证据，不取代迁移冻结后的L2非空组合 |
| 正向/并发/原键 | `A/enabled-resume`：完整上下文→实际附件/实物签名→显式选单和逐项排除→末SQL500回滚→同一原键首次并发成功且结果一致→原键/按任务/按ID回读→原包字节一致；`tail-rollback.json`的key与successful一致 |
| 权限/异文 | `A/enabled/refusals.json`：双权限缺一、跨仓、自检、旧摘要、漏排除实际拒绝；临时授予原采集者测试角色后自检403，原角色完全恢复。`A/enabled-resume/different-content.json`为原键异文409 |
| 守恒 | `A/enabled-resume/conservation.json`：只关联/幂等/审计/Session变化，库存/占用/正式订单/提交/result/ledger等逐值不变；`facts-2`证明末SQL回滚后同一基线守恒 |
| 实际锁等待后撤权 | `A/lock/actual-lock-wait.tsv`、原交互stdin/stdout/exit及HTTP：实际LOCK WAIT，撤权后401 SESSION_REVOKED，恢复身份后原键回读；原关联及其他业务表不变。新增一条WEB_LOGIN已独立逐行核对，见`A/lock-conservation-supplement.json` |
| 固定钥/新JVM | `A/independent`、`new-jvm-independent`：Python实际核RSA签名、外部公钥与1514固定钥字节一致、关联/实物上下文与原包/实际附件摘要；新JVM读回完全一致 |
| 实时库存/原记录 | `A/post-boundaries`：目标变化及库存阻断拒绝，原键仍返回原意图；数据库UPDATE/DELETE不可变触发器实际拒绝并保留原SQL错误 |
| 跨机冲突拒绝 | `A/peer-refusal`：明确合成的未可信另一设备原件实际接收，重码可见；新上下文及新实物检查后关联409，原关联/原键保持。仅拒绝，冲突决定正向仍缺 |

原Android结束包保留原签名/原身份；本次库存/目标/主管/实物与另一设备包为明确合成隔离输入，不冒充上游资料、原物理PDA或现场事实。原独立任务计划与短装历史不改写，关联到合成目标的完整实际计划不代表原任务已正式成功。

## 失败、自审与限制

- unit01误用FORMAL而订单实际来源为WEB，137项中2项失败；运行期间又改输入，sourceUnchanged=false，整轮不计通过。原XML/log/result保留，修正后冻结unit02/03实际复验，不倒推首轮。
- 1525首次恢复预条件把空数据库交给严格非空JDBC消费者，JVM查询exit0/库存零，wrapper拒绝属夹具；第二入口遗漏原字符集参数，dump line45 CLI exit1且已部分读入。未把部分数据库当可续验，完整dump/hash/认证停机/压缩保全于`B/preserve-1525-e06-association-restore-failed01`，新1526沿修正参数成功。首次Python原stderr被PowerShell中断重定向未保存，此缺口不补造；JDBC原输出/退出码保留。
- enabled尾SQL夹具使用复合触发器而未设delimiter，SQL1064且未调用关联业务；保留原SQL/HTTP及指纹，只在同副本原实物记录后续验，不重复种子。`A/fixture-resume-conservation-supplement.json`逐行证明原审计不变，新增五条全为WEB_LOGIN，其余业务事实完全一致。
- lock首个守恒断言未列新登录审计，原失败保留。补比较所有原审计行、只允许一条明确WEB_LOGIN后通过，不用宽松白名单解释未知业务变化。
- API环境模板在新JVM重启时复用了console输出文件名，之前API console日志被覆盖，无法恢复；原HTTP/SQL/锁回执仍保留。限制登记`A/logging-limitation.json`，后续使用唯一输出或重启前副本，不称被覆盖日志已保留。
- 作者自审核对实际WEB枚举、锁后双权限、完整候选/无作业、实时映射、同任务唯一、原键优先、末SQL原子性、签名/引用/附件、读回原截点及资源上限；不是独立人员审查。旧CLI潜在根因连续三次、1522/1523旧业务事实仍未判，保持保全。

1526完成后dump/hash/认证停机与无消费者压缩，保全见`B/preserve-1526-e06-association-l1-01`；失败库/原日志/原资料不删除，预览与1519保持。Git实际回执及全部继承改动快照见`B/e06-association-delivery01`。下一步接人工关联网页原键产品链，再实现专用跨机冲突处置及组合验证；E07–E09与全部剩余闸门继续。
