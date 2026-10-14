# E06人工关联消费完整冲突决定：服务端L1开发备份

[设计](GOAL_E06_CONFLICT_ASSOCIATION_DESIGN_2026-10-14.md)以 `b1e53def98138cb225b7b62ef68721ddc40d5beb` 为输入；固定主树/单助手。版本事实见看板生成块，非E06/D/O/U收口或最终候选。新增可选`conflictDecisionId`显式引用完整决定，旧无冲突请求和原签名回读格式兼容；仅原台账关联和不可变外键引用，不形成正式submission/库存/账务/新增采集/放行/删除能力。

关联前两个专用开关均须显式启用；锁后双权限、目标锁及原键优先沿原链。原决定选定截点必须逐值等于完整实时截点，重核所有原任务/实物/原采集者分离与包周期声明；结束分歧、新增原件、过期实物、漏方、库存阻断保留。回读核引用表与原请求一致并重核原决定、原实物签名及附件。引用不可UPDATE/DELETE，默认生产边界不变。

`RelayAssociations/RelayAssociationPolicy/RelayAssociationConflict*、引用迁移和关联契约 → 显式冲突消费责任链 → 173项定向/bootJar、真实HTTP/MySQL正向/默认/权限/并发/末SQL/锁后撤权/不可变、固定外部钥/新JVM/业务守恒 → Android/网页未受影响，复用17a45e5ab480617392f872e1a8430d44c7acd977人工关联网页L1及实物绑定证据，理由为未改客户端入口；新网页入口另须真实Chrome。L2迁移冻结组合及最终L3继续。`

本机 `A=.local/goal-20261013/e06-conflict-association-audit-1529`，`B=.local/goal-20261014`。

| 验证 | 实际证据 |
| --- | --- |
| 定向/构建/契约 | `.local/goal-20261013/e06-conflict-unit-association01`：173项零失败/错误/跳过，sourceUnchanged=true，bootJar成功；`B/e06-conflict-association-contract01.log`通过。随后仅补可选响应conflictEvidence契约行，contract02实际通过；binding01逐字节证明2431项冻结输入除该行外未变，不机械重构建 |
| 非空/default-off | 从1528保全dump/hash及原private/storage恢复新1529，无重种原件。`restore-run01`严格原全量非空事实；`disabled`原表仅Flyway变化/新引用表COUNT零，真实关联关闭409零写；`conflict-disabled`关联开/冲突关时真实409 INDEPENDENT_CONFLICT_DISABLED且零写 |
| 正向/并发/末SQL/异文 | `enabled-resume`沿原可信决定及双方实物，尾幂等SQL500全部回滚，同一key首次双请求200且结果相同；原键/任务/ID回读，冲突原件完全相同，异文409。仅一关联/一引用/一幂等/一审计，库存/占用/原冲突决定/提交/结果/账务逐值不变 |
| 权限/引用/自审拒绝 | `enabled`原HTTP及`fixture-failure02/fingerprint.json`：缺任一权限/跨仓、缺决定、未知决定、跨原件实物引用拒绝；`extra01`原采集者补测试双权限仍403、原角色恢复。实时目标版本变化拒绝，原键返回原意图 |
| 不可变/锁等待 | `extra01`引用表UPDATE/DELETE真实触发器拒绝；`lock01`实际LOCK WAIT后撤权401，恢复身份后原键不变，stdout/stderr/exit和每条SQL保存；原审计逐行不变，只一已知登录新增 |
| 固定外部钥/新JVM | `independent02`及`new-jvm01`Python从1514固定外部公钥验关联→冲突→双方实物签名/完整摘要/原件与附件字节；新JVM原记录完全相同。仅独立工具算法，不称独立人员/原PDA现场 |
| 输入/作者自审 | `binding01`源码冻结输入、编译类、迁移及JAR字节一致，SHA256 `061506001eccb8da9d3c985189deec985512c098e31d4af7fa50ac70f11167a2`；严格原审计多重集不变、新增一关联事件。锁/权限、显式引用/全部方和周期、旧原键/格式、事务尾写及能力false自审，非独立人员审查 |

原Android包、签名与原字节保持；原冲突决定的另一设备、库存、目标、主管和实物为明确合成隔离输入。不可据此冒充原物理设备或现场可信事实。

失败原证据保留：`enabled-run01.log`夹具误期望跨原件409，实际先因实物引用不属于该原件返回404；全部请求拒绝且未进入尾SQL/正向。`fixture-failure01-run.log`补证误用权限码FORBIDDEN，原为PERMISSION_DENIED；修后读取同一全表事实，不重新采集，`fixture-failure02`逐行证明只有已知登录/Session，所有业务事实一致，随后只续尾SQL/并发/成功。`independent01-run.log`把实物回执当关联回执断言newCollectionAuthorized，验签后KeyError；代码/原响应证明实物契约无该字段，只GET无业务写，修后独立02有效消费。原失败不记通过、不清零旧根因计数。旧CLI/旧UI/恢复暂缓及1522/1523未判继续保留。

1529完成后dump/hash/认证停机、零监听/消费者适用压缩，保全`B/preserve-1529-e06-conflict-association-l1-01`，原库/失败库/证据零删除，预览与1519保持。明确路径提交/推送与全部继承WIP快照见`B/e06-conflict-association-delivery01`。下一冲突网页/关联产品入口，再短装/多任务与增量责任链、E06组合、E07–E09及所有剩余闸门；总目标仍active且未完成。
