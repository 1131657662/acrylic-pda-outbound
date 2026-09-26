# R06 其他未提交来源事实验收

## 完成标准

- 沿既有 E2a 原件、页、STOP、VERIFY 入口读取其他 FORMAL/TEMP 来源的原 CAPTURE/SCAN 字节；不得以页声明、自带身份或新造提交替代原事实。
- 已提交来源保持原提交 payload 精确核验。未提交 fallback 只在原 ID/设备序号没有已提交归属时使用；摘要不符或跨仓已提交记录不得降级绕过。
- 对照原 authorization（含原续期版本）、设备、人员、仓库、来源、snapshot/recovery epoch；原签名及准备包摘要可核验。原期限用于核对原操作，不要求历史授权当前仍有效。
- 审计为停止高水位以内的连续可信前缀；原输入、状态、确认/保存/撤销次序可核验，待确认状态不伪造完成。原库存包/产品/重量/周期对照固定准备资料。
- 其他来源记录仅解释全设备前缀，不进入当前 TEMP 的库存锁/包占用集合。缺件、伪造、换授权、跨仓、遗漏已保存 scan、超出审计前缀等均拒绝，原资料保留。
- 按本批输入执行单元反例、独立非空 MySQL V34→V37 和真实 HTTP；当前来源与其他来源分离有数据库断言。自审问题修复后复验，不把条件 skip 或历史报告计作本批通过。

## 范围限制

其他未提交 TEMP 完整计划没有出现在当前协议快照中，本项只验证其原来源/原库存/记录状态；不声称已审核该来源计划、实际装车或可确认。当前 TEMP 自身仍走既有冻结 payload 完整核验。跨来源继续作业与 R07 交接按后续契约补齐。生产七类决定入口、放行/关闭/删除边界不变，localDeletionAllowed=false。

## 实现与验证

实现/复验完成于 2026-09-26。文件及 evidence 目录沿本批开始日期保留，不改历史 attempt 名称。

- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95` + 当前差异；主树 `codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。未提交、推送、整合、发布或部署。
- `UnsubmittedSourceObjects/Preparation/Authority/Audits/Records/Transitions/Facts` 拆分原字节、原凭据、审计前缀及记录状态验证；`SourceEvidenceIntakeOtherFacts/BusinessFacts` 接入。原已提交路径先查原ID/设备序号归属，禁止摘要/跨仓冲突降级为未提交。
- 使用原 authorization ID 查询，RSA 验签信任配置公钥；原准备包压缩/解压长度及双摘要、固定身份和库存明细一致。历史过期授权允许解释原有效期内操作，不以当前续期覆盖。每次核验仅缓存该批原准备包，避免逐包重复解压。
- 自审补强手输等待/自动确认路径、错误原因与原审计一致、TEMP库存索引/同仓一致；只读取 STOP 高水位以前的完整审计。待确认输入保留原状态，COMMITTED不可漏scan，VOID必须有原后续撤销事件。
- 定向单测后完整服务端最终 **87XML / 614项 = 613pass、1符号链接条件skip、0failure/error**。30项新增测试；该skip不计通过。
- **attempt67**：独立非空 V34 seed→V37 MySQL；IT **1/1，无skip**。真实HTTP上传11条审计，OPEN/RAW/PAGE/STOP/VERIFY及原键/原响应重放；20个原条目含已提交来源、未提交FORMAL/TEMP、WAITING_CONFIRM。原过期FORMAL凭据、存在无效新续期仍用原凭据通过；坏签名、有效签名错仓、坏准备分片、坏原对象、已提交归属冲突拒绝。实际DB事务验证省略原scan、截短审计、孤立scan拒绝。
- DB断言只有当前TEMP自身包产生SOURCE_RESERVED，其他来源包无K；其他来源输入没有生成submitted_capture；原件仍保留，PARTIAL和全部禁止能力保持。
- **attempt64失败**：fixture将正式plan分片插入TEMP准备表，MySQL CHECK拒绝；修正为TEMP分片。**attempt65失败**：跨仓反例直接改原资格仓库被复合FK拒绝；改用真实重签但错仓绑定并先跑正向基线。两次失败库/日志保留，均认证shutdown。66为自审补强前通过，不替代67。

## 证据与限制

- 公共本机证据 `outputs/r06-product-20260925-01a0d8fa/unsubmitted-*`：编译、定向/完整日志、87XML、摘要、attempt64–67结果/关闭身份、两树全部dirty路径。
- 最终1344文件源码manifest SHA256 `e5e47bbc876e2c200fc39589ce6e19216f2f2151aba1ca21fe6963d02de77c14`；包含服务端/报告源码资源测试、Android源码schema/构建描述和运行器。历史缺项manifest不倒填。完整全套后没有业务源码/fixture漂移。
- 私有实例资料仍在服务端 `.local/e2a-cross-20260925-64` 至 `67`；全部认证shutdown，共享3306未动。失败资料不删除，不将凭据/私钥复制到公共证据或Git。
- 作者/自审同一助手；本阶段真实HTTP客户端为Java测试，独立实例指隔离非空数据库，不称独立人员/跨语言消费者。原授权、设备和记录为明确合成fixture；真实PDA、Keystore、现场、外网/断电及长期均未验。
- Android生产/测试本阶段无改动，未新跑Android/APK/lint；引用Room27/attempt63仅作历史。未完整覆盖FORMAL跨来源续作审计，严格拒绝不一致来源，后续R07契约接入。未提交TEMP原计划不齐，不声称此来源整体可确认。
