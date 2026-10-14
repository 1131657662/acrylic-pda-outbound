# E06跨机实物冲突：服务端首切片开发备份

输入HEAD `17a45e5ab480617392f872e1a8430d44c7acd977`，固定主树/单助手。[最小设计](GOAL_E06_CONFLICT_DESIGN_2026-10-14.md)限定主管把已实际逐项核对的重码归于选定原任务。版本事实只见看板生成块。仅L1，不计E06、O04或D步骤/最终候选完成；网页、关联应用、短装/多任务及增量冲突责任链仍待实现验收。

专用入口默认关闭；主管须同仓双权限并独立于各方原采集者。完整可信结束原件、完整实时截点、全部重叠任务实物回执不可缺；选定方LOADED，其余同码UNLOADED且有理由。未找到、结束分歧、未可信原件、漏方和库存阻断继续拒绝。只新增不可变决定/引用/仓库包周期归属声明；不改SCAN/VOID/结束、库存/占用/订单/提交/结果/账务，不授关联/新增采集/放行/删除。原键优先恢复、异文拒绝，唯一包周期声明阻止新键覆盖原决定。

## L1设计与输入复用

`RelayConflict*/RelayConflicts、新增冲突台账迁移、independent-conflict契约及接线 → 协议/权限/事务/签名/引用 → 定向单测、真实HTTP/MySQL正向及默认关闭/权限/并发/异文/末SQL/锁后撤权/不可变/固定外部钥消费 → Android与网页未受影响，复用实物与人工关联网页验收各自绑定输入及17a45e5ab480617392f872e1a8430d44c7acd977；理由为未修改这些客户端路径。本次重新消费实际原包/实物/附件。`

本机 `A=.local/goal-20261013/e06-conflict-audit-1528`，`B=.local/goal-20261014`。服务/契约2426项输入与定向测试冻结输入全匹配，运行JAR、编译类及迁移字节绑定见`A/binding01`；作者自审不是独立人员审查。JAR SHA256 `9b68f8ae4825ad1055a1cfe8026f19b7955f482478f932a40446c40655b1ca21`。

| 验证 | 实际结果/证据 |
| --- | --- |
| 定向单测/构建/契约 | `.local/goal-20261013/e06-conflict-unit-01`：162项、零失败/错误/跳过，sourceUnchanged=true；bootJar成功。`B/e06-conflict-contract01.log`契约通过。构建后JAR复制错误仅包装，见下方原失败与补充 |
| 非空/default-off | `A/restore-run01`、`disabled`：同算法JDBC严格旧表守恒、仅Flyway变化，新增表全部独立COUNT零；真实关闭409，业务零写。是本单元证据，L2在迁移冻结后仍需组合 |
| 正向/首次并发/原键 | `A/prepare-02`全部可信完整原件及双方实物；`enabled-resume`两首次并发返回同一决定，原键/按ID回读一致。只增加一决定、两引用、两包周期声明、一幂等；库存/占用/提交/账务等全量表不变。`binding01`逐行证明原审计不变，仅两登录/一决定审计 |
| 默认/权限/异文/漏方 | `disabled/default-off.json`与`enabled/refusals.json`：缺任一权限、跨仓、旧摘要、漏方、另一方仍LOADED拒绝；`enabled-resume/conservation.json`原键异文及新键覆盖拒绝 |
| 末SQL回滚 | `enabled/tail-rollback.json`：最后幂等INSERT真实500，决定/引用/声明及全部业务事实回滚；沿同一key `534f54c2-ea22-4484-be07-25dd9c612489`首次并发成功，不重种数据 |
| 原采集者/不可变 | `extra01`HTTP与原SQL：临时补双权限的原采集者仍403；三表UPDATE/DELETE六次实际拒绝，原角色恢复。`extra01-supplement`严格逐行补证仅一原采集者登录审计新增，所有原业务事实/原决定不变 |
| 实际锁等待后撤权 | `lock01`原stdin/stdout/stderr/exit及actual-lock-wait.tsv：真实LOCK WAIT后撤权，HTTP401 SESSION_REVOKED，身份恢复后原键回读。`lock01-supplement`逐行补证仅一主管登录审计，其余业务事实全守恒 |
| 固定外部钥/原字节/新JVM | `independent01`、`new-jvm01`：Python消费者从1514独立固定公钥验RSA、声明/上下文摘要，原Android包与明确合成另一设备原件、双方实物签名/实际附件逐字节/摘要一致；新JVM回读完全相同，console使用唯一日志 |
| 实时实物/库存阻断 | `live01`：新实物上下文下NOT_FOUND、选定方UNLOADED及实时库存阻断分别按实际消息拒绝；临时库存还原、原键仍返回原决定。原审计逐行不变，只四附件/四实物审计及对应台账/幂等/Session新增，其余业务表守恒 |

原Android包保留实际原字节与签名；第二设备钥/包、库存、目标、主管及实物观察明确为合成隔离输入，不能冒充原物理PDA、上游资料或现场事实。Python是独立算法工具消费者，仍由同一作者运行。L2受影响组合、网页产品链、全链L3和现场项未被本切片替代。

## 失败与保全

- 定向测试/bootJar成功后包装复制不存在的JAR文件名，原日志/退出保留；依据该次成功构建复制实际文件并登记哈希，`jar-copy-supplement.json`说明，没有重复构建或倒推包装成功。
- `prepare-run01.log`在API监听前首个CSRF连接拒绝，未种子；`prepare/fixture-startup-fingerprint.json`保全。中间健康探测用受保护入口返回401，不计200；随后唯一目录prepare-02实际成功。
- `enabled-run01.log`尾SQL回滚已有效，首次并发调用前函数名遮蔽concurrent模块，AttributeError；`enabled/fixture-concurrency-fingerprint.json`说明原回滚有效且并发未请求。改名race后沿原键续验，原失败不记通过。
- extra01/lock01守恒断言遗漏新登录审计；原失败日志与全量facts保留。补证读取原多重集，原审计行全不变，分别只有一已知身份WEB_LOGIN；六次SQL拒绝、401及原键回读分别绑定原HTTP/SQL。未重复业务请求，没有把未知变化放进宽松白名单。此类夹具根因承接人工关联旧失败计数；已有具体逐行事实解释并收口，不清零或声称原断言通过。一次诊断输出误以UTF8显示二进制ID后碰Windows GBK编码错误，仅工具回显保留，未另存本机原日志，不影响数据库/业务结论。
- 旧CLI潜在读取根因连续三次、1522/1523旧事实未判与旧暂缓项继续保全，不由本轮JDBC通过反推。

作者自审核对默认关闭、锁后双权限、全部可信结束与实物引用、完整任务覆盖/每包归属、库存阻断、唯一包周期、原键顺序、事务最后写入、回执/关系/附件重核、容量上限及生产能力false；实际证据见`A/binding01/result.json`。1528收口dump/hash/认证停机及无消费者适用压缩见`B/preserve-1528-e06-conflict-l1-01`，预览/1519保留，失败库和原证据零删除。Git回执、明确路径与全部继承改动快照见`B/e06-conflict-delivery01`。

下一步接冲突网页和关联消费、短装/多任务与增量责任链，完成E06组合，再E07–E09及第8节全部剩余闸门；总目标active且未完成。
