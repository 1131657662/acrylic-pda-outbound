# 更正查询与原回执恢复验收（2026-10-01）

输入与已核远端 `a9e926ad3b9e5d1c5993a39c55d9d5f3a534284a`，树 D:/acrylic-pda-outbound，codex/development。单助手实现/自审/执行；依据[自主方案](AUTONOMOUS_PLAN_2026-10-01_CORRECTION_READS.md)和[接口提案](../parallel/R07/INTERFACE_PROPOSAL.md)。版本事实见[看板](../ORCHESTRATION_STATUS.md)。

## 结论与实现

**本批新增只读查询闭环通过**：GET提供CREATE/文档命令expected和验签原CREATE正文；原请求人按原键取回精确原响应，权限/仓库隔离，坏件失败关闭。GET既不执行命令，也不重新签名。新增5份主代码、5份测试；旧产品/测试/迁移和默认开关不变。

查询只在既有commands显式开关下注册，默认关闭。身份来自既有Web/PDA鉴权；所有查询为只读一致快照。原回执核规范原字节、两类SHA、行身份/时间/expected/原命令仓库、RS256/issuer/audience/公钥及能力位。文档原文与冻结文档摘要/申请人/订单/仓库绑定。具体路径和404语义见[接口说明](../parallel/R07/CORRECTION_READS.md)。

## 本轮真实验证

| 执行 | 结果 | 范围 |
| --- | --- | --- |
| offline-1 | 主/test编译通过，脚本parse0 | 首次主代码离线检查；不是业务验收 |
| unit-1 | 2套件/19项/0失败/0错误/0跳过 | 原字节、配置历史公钥轮换、未知钥、6种行身份错绑、坏摘要/命令/expected/时间/状态/能力/签名/非规范编码；默认与显式开关 |
| full-offline-1 | **140 suites/2893 tests/0 failures/0 errors/1既有skip** | 最终源码编译和全服务端单测；既有白名单/断言未改，脚本parse0，完整源码hash落盘 |
| attempt810 | **1/0/0/0，BUILD SUCCESSFUL 33s** | 全新隔离MySQL、真实Web HTTP、上下文关闭重建后重新登录取回原件；无自动重跑 |

810业务轨迹共21次请求：19 GET、2 POST，10次200、4次403、7次404。Web会话/CSRF是真HTTP；成功原来源是测试服务层合成前置。核心组合为GET订单expected→POST CREATE→主管GET文档原文/expected→POST APPROVE→各自GET原回执；所有CREATE/APPROVE expected都用查询响应覆盖测试前置值。独立JCA公钥验签；JSON规范化工具仍复用产品，不称全协议独立实现。

查询覆盖缺权限、跨仓、同仓其他申请人、主管不能取他人CREATE回执、建档人不能取审批回执、错命令类型、缺失原键/文档。4组查询前后对15张业务表全量行比较，保留类型/null/字节，原件查询没有增加文档/回执/账务/审计/幂等/结果/占用或outbox；完整最终快照保存。Spring会话访问元数据属于HTTP基础设施，不在业务表不变声明中。

完整Boot上下文重启、重新登录后，CREATE与APPROVE原键GET逐字节相同，且业务表仍不变。原来源/原出库/原账务/原结果保全。没有用GET代替POST执行，也没有把查不到原键当作业务失败终态。

## 证据、磁盘与真机

证据根 `.local/autonomous-20261001/correction-reads/`，真实库 `.local/e2a-cross-20260925-810/`。离线源码hash/XML、HTTP每次原响应、http.tsv、业务快照、原回执、restart marker、dump及完整ZIP保留。实际提交/推送SHA见本记录Git历史和 `commit-review/git-final.json`，不预写推送成功。

- D字节：本批前置31797604352；810前31741145088；运行/停库保全前31523565568；ZIP保全清理后31724699648。均高于20GiB。
- 810认证shutdown；292文件逐项从ZIP回读SHA相等后只清该新datadir。ZIP SHA256 `29448F609C8768F8EE32353919C27D33456C3582D911121DEA762DCC08F7C759`。历史attempt与所有失败原证据保留。
- 真机22D2910042仅枚举，前置/执行前后/收尾快照一致；目标设备命令0，AVD0。19330/19434释放，系统MySQL未动。
- 八项保护路径（含旧A1.1工作区换行状态）及历史迁移摘要不变。未提交这些既有改动。

## 缺陷、未验与接续

补扣前检查发现[更正账务实际贡献缺口](R07_CORRECTION_ACCOUNTING_GAP_2026-10-01.md)：现有Approve无条件以结果差额写更正账，未按原扣账决定零流水或逐笔反向；本轮是静态定位，未运行新反例、未修旧算法。按活动目标记录跳过，补扣/库存差异审批闭环仍未完成；本查询组合通过不扩大其账务正确性结论。

原分页/P08/抬头撤销缺陷、REJECT/排除写权限、高轮次/TEMP跨来源、故障换机/范围恢复继续保留。旧政策页“无主管/outbox/附件”等状态已有较新实现，本轮按源码与新验收使用，不恢复旧待办。

PDA Bearer/原设备绑定实跑、权限撤销竞态、独立JVM/断电、损坏原件真库注入、完整列表/明细账务预览/净结果API、前端、现场和长时留存未验或未实现。WITHDRAW/RECONCILE原键GET代码通用分支未各自真HTTP专项。配置历史公钥的验签是单测证据，不冒称真服务换钥验证。

下一独立工作优先补原机可用交接/受控更正的客户端可消费查询与页面、原键耐久恢复；需要修改已停报产品算法的子项继续单列。整体goal active；默认全false，localDeletionAllowed=false，不部署、不接原库/现网、不合main、不打标签，不进R08/R09/R10。
