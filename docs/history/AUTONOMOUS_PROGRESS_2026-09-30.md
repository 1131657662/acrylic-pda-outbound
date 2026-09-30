# 自主开发进度（2026-09-30 起）

## 基线与方案

实际起点为主树 D:/acrylic-pda-outbound、codex/development，本地/远端同为 4312b6ff1416bc6f472b7c4a8cb5317a73974a1d。A1.1 已在[既定范围](R06_A1_1_CODEX_2026-09-30.md)收口；[旧 R06→R07 停报](R06_R07_PROGRESS_2026-09-30.md)只代表旧授权。新的自主目标已核实，允许新增能力/测试/隔离配置，禁止改变已验业务和生产默认值。

方案见 [AUTONOMOUS_PLAN](AUTONOMOUS_PLAN_2026-09-30.md)：S1 关联回执消费 → S2 来源可信事实 → S3 拒绝/卸货/释放/续作 → S4 R07 实际缺口。版本事实只见[看板](../ORCHESTRATION_STATUS.md)。

| 项 | 实际状态 | 实现提交/证据 |
| --- | --- | --- |
| S1 | 按下列范围验收，已提交推送，远端 SHA 一致 | f892586580423abedbe5b38721cbf89b3e908245；attempt792 |
| S2 | 待核对执行 | 不以既往 SQL/HTTP 局部通过代替本批结果 |
| S3a/S3b | 未启动 | 拒绝、卸货释放、续作按已定政策分批 |
| S4a/S4b | 未启动 | 先消除旧索引与较新源码/证据矛盾 |

## S1 实现、验证与适用范围

- 新增四份 Python 文件：linked_context、linked_effects、linked_consumer、test_linked_decisions；新增四份 Java 测试文件：TemporaryLinkedFixture/Facts/Vectors、E2aTemporaryLinkedVectorsIT。
- 服务端产品、Android、契约、历史迁移、默认开关均零改动。旧 decision_consumer/decision_facts 文件不变；专门反例证实旧消费者仍拒绝 mapping2。
- 真实 TCP HTTP 顺序执行 LINK、NORMAL_CONFIRM、同原请求重放；经独立 cookie HTTP 读原件/详情及 PDA HTTP 读成功回执。数据库事实直接查表，不以 CommandFacts/响应 DTO 充当期望值。
- Python 从原 TEMP 正文及全候选表快照重算唯一候选/摘要，校验仓库、计划、PDA session/权限、映射来源及历史；核对原件哈希/签名域、LINK 指针切换、嵌套成功回执、目标结果/占用/出库/库存账和重放后的无新增效果。
- **当次编译与真实 IT：1 tests / 0 failures / 0 errors / 0 skipped**；**Python 40/40 通过，无跳过**。包含重新签名且签名有效的错误语义、错误目标/映射/数量/身份/能力、未知公钥、原件变化、错误账务及 currentEffect 非原件权威反例。
- 作者与自审为同一助手；“独立消费者”指 Python 不调用 Java 产品业务生成期望值，不冒称独立人员审计。身份、账号、PDA session 和签名物料均为隔离 fixture；不是现场可信身份/真实配对/Keystore 证据。
- 覆盖仅单候选、完整数量、首轮与受控 mapping2。多候选排除、mapping3+、高轮次、短装关联组合、真实并发/故障、Android 安装、真机或生产可信公钥均未验。本批未跑全仓回归/APK/lint、未部署。

## 失败与自主决策

1. 选择补独立消费者，而非重做已有 LINK→确认产品链，依据源码与现有受控关联验收；保留旧消费者的首映射限制。
2. 旧 provisioning 收尾含历史 datadir 清理，因此仅使用本目标 .local 脚本；不调用该收尾、不停原服务。
3. attempt791：PowerShell 未加引号的短参数 -h127.0.0.1 被解析为主机 127，探针失败；MySQL 已正常启动，未建业务 schema、未进业务测试。改用完整长参数。失败记录、日志、磁盘快照保留；已认证 shutdown。
4. attempt792：首次 datadir 比较将正斜线实际值与 Join-Path 反斜线路径比较，误拒。只读确认本 attempt 且仅四个系统库后，在一次性标记、空实例和进程身份守卫下接续首次 schema 创建；没有重复初始化或重跑业务断言。
5. 离线自审修正新消费者自己的 slot 版本预期：关联和成功确认都会各递增一次。未更改产品逻辑；实际 IT/Python 首次运行通过。
6. Windows mysqld 使用监控进程及监听子进程；停库前同时验证父子关系和 @@datadir，使用认证 shutdown，未 force-kill。
7. 本批只清理新建 791/792 的 datadir：791 先压缩并逐文件 SHA-256 回读校验，792 保留完整 SQL dump 及摘要、向量、XML、记录、marker、日志。历史 attempt、失败证据和两份受保护 PDA 文档不动。

## 证据与资源

证据根：.local/autonomous-20260930/，包括 initial、s1-offline-1、s1-closeout、执行脚本、attempt-inventory.json、adb-before.txt。

- .local/e2a-cross-20260925-791/：precondition-failure.md、initialize/mysqld/shutdown 日志、failed-empty-datadir.zip、逐文件/压缩包摘要、datadir-cleanup.json。
- .local/e2a-cross-20260925-792/：precondition-correction.md、integration-test.log、TEST-com.acrylic.outbound.e2averify.E2aTemporaryLinkedVectorsIT.xml、linked-decision-vectors.json、real-records.tsv、python-linked.log、result.json、final-database.sql、dump-sha256.json、marker、datadir-cleanup.json。
- s1-closeout/code-commit.json：实现 SHA 与实际远端相等；staged-code.diff/stat 保存精确提交范围。
- 两份非本会话 PDA 文档 SHA-256 前后一致，未暂存。真机 22D2910042 的 adb devices -l 快照逐字节一致；0 次目标设备命令、0 次 AVD 启动。
- D 盘：起始 33930801152 字节；S1 清理前 33525465088；清理后 33925169152。净变化包含构建/证据/压缩快照，不等同于数据库大小。全程高于 20 GiB。

## 待决策与仍未验

候选排除授权范围无法从政策推出，跳过此项待裁定；不阻止其他子项。FORMAL 来源夹具维持独立时间盒；真机、现场、异地、长时、真实外部/生产条件不模拟验收。R09 锚点保管/轮换等未决边界不因本目标扩大。

A1.2/A1.3/R07 未整体完成；A1.1 现场/真实故障边界、R05 原资料/旧 BCD/C 档、R09 既有恢复/租约/历史 keyring/W-2 等限制继续保留。默认开关 false、localDeletionAllowed=false；未操作原库/现网、main、标签、Release、部署。自主目标继续，下一项为 S2 真实来源事实缺口核对。
