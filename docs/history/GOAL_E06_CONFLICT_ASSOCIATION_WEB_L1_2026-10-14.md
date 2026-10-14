# E06 网页显式引用原冲突决定：L1开发备份

在 `5bb6f8b9afd5f953773b7a8abb032e83a1703512` 上扩展既有人工关联入口，接受可选原冲突决定编号；发送前真实读取原决定并核对完整截点，回读时核对关联→决定→各方实物与原件归属/摘要。无冲突旧正文保持，原键/body/epoch持久恢复保持。网页仅结构/摘要核对，RSA及不可变引用由服务核验；决定与关联仍待对账、未记账/放行，不增加生产能力。

[最小设计](GOAL_E06_CONFLICT_ASSOCIATION_WEB_DESIGN_2026-10-14.md)包含选择依据、分层必跑与复用范围。版本事实见看板生成块。证据根 `A=.local/goal-20261013/e06-conflict-association-audit-1530`，单元/构建根 `U=.local/goal-20261014/e06-conflict-association-web-unit01`。

| 实际证据 | 结果与限制 |
| --- | --- |
| U/old-final.log、conflict-final.log | 旧真实HTTP关联6项、新真实HTTP冲突关联4项全部通过；缺失/无关决定、变更嵌套上下文/各方实物引用/任务、伪能力、原键持久化和身份隔离拒绝 |
| U/build.log、contract.log | 类型/构建和契约校验通过；既有大chunk告警保留，不计业务失败 |
| A/restore-run01 | 新1530从保全1528 dump及原private/storage恢复，原件、原签名、决定不重新生成，不复种1528/1529 |
| A/prepare01 | 真实HTTP读原决定逐值匹配、完整截点匹配，关联与引用表初始零；严格JDBC全表原始事实留存 |
| A/browser01 | 真Chrome/HTTP/MySQL：完整候选分页limit=1；缺冲突编号/缺候选排除均零发送；显式编号后成功；首响应丢失、刷新、双页恢复共3次POST同key/body/epoch且200；空本地存储真实按任务读回。390宽无横向溢出，pending/recovered截图作者目视可读 |
| A/post-browser01/facts-1、post-browser02 | 原始全表多重集核对，仅一关联、一冲突引用、一幂等；原审计逐行不变，恰好两WEB_LOGIN/一relay.independent.associate且全部为指定合成主管；关联原key与引用原decisionId核对。库存/占用/submission/result/ledger及所有其余业务表不变 |

原 `post-browser01.log` 在上述表增量/审计断言通过后读取中文浏览器请求时GBK解码失败；指纹为 `UnicodeDecodeError gbk browser-original-command.json`。明确夹具编码归因，保留日志和完整SQL快照；显式UTF-8并从同一原快照续验剩余key/decision断言，未重跑业务或倒推原断言通过。旧CLI读取与其他暂缓计数保持。

作者自审：严格schema能力均false；指定UUID显式引用、完整上下文摘要与各方实物回执绑定；身份/仓库/原件generation保护、Web Locks原键恢复保持；未知失败不删除/替换原请求；所有新文件/函数遵循职责与长度限制；没有服务/迁移/Android行为修改。原服务关闭开关、双权限/主管分离、同键首次并发/异文、末SQL回滚、真实锁后撤权、固定外部钥和新JVM的证据在[服务L1](GOAL_E06_CONFLICT_ASSOCIATION_L1_2026-10-14.md)，输入未变复用，不称本次重跑。

分层收口行：网页关联及冲突结构路径→L1直接消费者→10项真实回执定向消费、类型/构建/契约、Chrome真实成功/恢复/全表守恒必跑。无关Android/服务/迁移路径未受影响，复用上述页与其固定输入；D级L2、最终L3、冲突登记网页、短装/多任务、完整UI重设计尚未收口。第二设备、实物、库存、目标与主管均为明确合成隔离事实，不能冒充原PDA/现场。

1530用途结束后独立保全见 `.local/goal-20261014/preserve-1530-e06-conflict-association-web-l1-01`：dump/hash、认证停机、零监听消费者、适用NTFS压缩及前后文件长度/hash验证；预览/1519与失败库不动。源码提交不含私有材料、原件/真实附件、数据库和本机请求原件，不称这些已异地备份。
