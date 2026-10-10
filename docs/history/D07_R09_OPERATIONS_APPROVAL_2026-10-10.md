# D07/R09独立运维批准核验

沿[原目标](../AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)和[暂停接续](D01_D18_PAUSE_2026-10-10_RECOVERED_FAULT.md)，仅交付独立运维批准的信任、范围与闭事务核验。D01–D18及第8节未完成；两阶段释放、新资格和最终接收提交继续。版本事实见看板生成块。

## 输入与实现

主树D:/acrylic-pda-outbound、codex/development，输入8ee3fe77c4cf1cce69933c60fc43272d50fe1323，实际开发远端相同；main未改。本聊天单助手唯一活动项目写者。received-operations-resume-01保存33条dirty、完整副本/hash/diff/Git/进程/监听/磁盘；前physical-postcommit的继承文件除看板合法刷新外逐hash相同，69历史比较和22原STOP保护通过。

接手三份未编译运维实现后完成RecoveryOperationsAuthority/Signature/Approval及HandoverRecoveredOperationsReview。独立身份与批准文件必须显式pin，外置于恢复集合和控制/closed根；不从envelope学习信任。严格canonical JSON、RS256、RSA强度/DER/base64url、路径/硬链接/大小校验；绑定安装/会话/控制revision及UUID/目标代次/库实例和schema、仓库、原来源scope/submission/device/manifest、原主管回执及完整范围proof。

APPROVE之后重新检查完整原事实和编号历史，生成绑定批准后inventory的固定proof，再签运维批准。事务外预检closed authority；实际服务事务内沿原身份先锁及源/案件锁，复核同DataSource、源和主管proof，beforeCommit重读运维身份与批准文件。遵守既有journal operation token，不降级UNKNOWN或绕过busy。作者自审见.local/d01-d18-20261010/received-operations-self-review.md；不是独立人员审查。

无迁移/公开HTTP路由或生产配置变化；三项能力false，整库保持RECOVERY_PENDING。运维身份/签名和主管实物声明均为开发合成材料，不代原运维身份、现场实物或生产审批。验证对象不是释放permit，未实现PREPARE/DB/COMMIT或本代次资格。

## 实际验证

- received-operations-compile-01及compile-tests-01均exit0；0测试，不计业务通过。
- received-operations-unit-01：信任根/路径/pin/格式/密钥/签名8项全过，无skip、sourceUnchanged。
- 1282中间范围：从只读1233原dump/binlog/文件恢复全新目标，实际MySQL+应用1/0/0/0、exit0/sourceUnchanged；签名/字段/来源/仓库/物料反例、重放和数据库不变。后续产品事务接入不借此结果代验。
- 1288最终范围：同原只读来源恢复新目标，专项1/0/0/0、exit0/sourceUnchanged；主管核对→独立运维批准→实际闭事务重新核对，原键重复通过；11项字段/5项来源、错仓/错pin/物料变化和事务外预检拒绝，批准文件及信任根两项beforeCommit尾变导致实际MySQL写回滚，完整DB/文件前后不变。
- 独立operations-consumer-03：14输入hash不变、RS256/独立根/安装会话与控制/来源/主管/固定proof/批准后编号历史、8反例；physical-consumer-02：22输入/130表成员和原终点、三签名/原清单/精确APPROVE效果、8反例；number-consumer-01：原生mysqlbinlog8输入不变。三者exit0，工具独立消费不代独立人员。
- 1282/1288各自dump、认证停机exit0、原PID退出、19330/19434空。报告位于.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-编号；目标/原件在.local/e2a-cross-20260925-编号；保全在.local/d01-d18-20261010/temporary-evidence-restore-mysql-编号。
- received-operations-regression-01：普通recovery/handover3317项全过、116 XML、0 skip、exit0/sourceUnchanged；不是完整server或IT。最终1288的2664个输入哈希在回归/提交前核对未变，见received-operations-input-verification.json。

## 失败和保全

- 首次1282分配与编译同时启动，被Heavy Gradle守卫拒绝、未建库；编译完成才分配运行。1283/1284被跨树随机目录子串守卫拒绝、未建库；原因与候选清单保留。一次JS传输字符串错误未执行shell，修为原文后继续，未改分配边界。
- 独立operations-consumer-01读取Jackson序列化file URI失败；保留failure，修为fileURLToPath，02对1282通过，03对最终1288通过。未改原批准或证据来配平。
- 1285失败RECOVERY_OPERATIONS_PHYSICAL_BINDING：原APPROVE返回的numberHistory摘要绑定批准前inventory，事务内重新生成绑定批准后inventory；严格比较拒绝。修为运维批准前完整再核对，保留原APPROVE proof并另存批准后proof/编号历史；未忽略字段。1285日志/XML/dump/datadir/binlog与停机证据保留不复用，1288新目标修复通过。

无新增正向业务HTTP/Android、APK、模拟器/真机、正式签名或部署；1233历史不能外推当前终链。1150/1151/UI-06及1100–1102未重跑。原资料/旧BCD/526、历史失败/未验及C01–C08保持。新产物全部D盘，无清理，不把本机私有原件声称异地备份。

## 下一动作

受影响回归、自审、明确路径提交推送后，接库外PREPARE→DB排他锁写对应revision/epoch/范围投影→库外COMMIT→实例每次核双侧。只复用耐久存储原语，不用RecoveryNormalRegistration/Intent作业务安全证明；未知范围继续隔离。之后本代次新资格→接收准备/激活→最终提交，继续D08–D18及总闸门。
