# C1b 首生产交付与作者验证

2026-09-21，W1，独立树 `D:/acrylic-pda-worktrees/r06-ledger-runtime`，分支 `codex/r06-ledger-runtime`，起点 `82a2e13cac306f877ed5e2c887970adf3d8e57f0`。本文件对应首生产提交，提交号以 Git 为准；未合入 development/main、未部署。独立 W2/W3 验收与组合验证尚待固定提交后进行。

## 实现与明确边界

- 内部 `TemporaryCommandService` 持有最外层 REQUIRED/READ_COMMITTED 提交边界；当前身份及原仓读资格后的原键查询、E/G/完整排序 U/W/D/H 锁后的第二次原键查询，都早于新执行权限、mode、transport epoch、expected 和参与者判断。唯一冲突先退出并回滚事务，再以另一次只读事务恢复已提交原键，不能在已失败事务中补查后提交。
- 锁前仅查询现存 S/C 的身份提示，不读取当前 payload/mapping/资格；锁内重新核对提示及来源/expected，不补建 order、assignment、scope 或 submission。原 actor、原操作员、原设备与原资格分别保存。保守原来源当前有效性门槛仅属于 `INTERNAL_TEST_ONLY` 演练，不是未来真实正常确认/处置的通用业务政策。
- request/receipt/event/audit 同事务；原响应 bytes/hash、unsigned hash、固定 RS256/audience/kid、内外签名一致与 request 双向引用每次读取都核验。原映射使用 receipt 引用的不可变 mapping 行，绝不拿当前 mapping/slot 否定原历史。合成参与者仅在测试配置，生产 bean 集为空；没有任何执行 POST、真实七命令决定、记账或放行。
- 四个 Web/PDA GET 均当前身份、本人原键、原仓以及已存族权限或 confirm 历史读资格；`/original` 直接输出原 BLOB，无重新序列化。详情单列 currentEffect，始终 UNKNOWN/applicable=false，列身份、会话、权限、恢复、来源及上下文可见变化，同时注明业务/候选/证据评估未实现，全部能力 false。
- 回执只支持严格 v1 `INTERNAL_TEST_ONLY` / `businessDecision=null`。字段集合、整型版本、UUID/十进制/时间、null 语义及有序唯一权限/角色快照严格验证，拒绝字符串/浮点/溢出版本与合法 RSA 签名下的错误类型。原 PDA 会话核保留 user/device/version/warehouse 和 created≤recordedAt<expires，读取不要求旧会话今天仍有效；原资格的不可变身份/版本另与保留事实交叉核对。
- Web 传输分类、当时完整角色/有效权限集合和具体实时授权，是可信写者在锁内形成的签名断言；现无独立 Web 会话/权限历史表可重新重建。PDA 历史会话稳定事实和期限也不单独证明当时所有授权。未知历史 kid 仍失败关闭，未实现历史 key ring。
- OpenAPI 0.30.0 / 120 paths，Flyway 保持 V34，没有 V35；Android code20/Room17、普通正常确认空 reason 目标及 localDeletionAllowed=false 保持。证据首次写、STOP_PROOF/manifest、E2a、实际命令 handler、HTTP/设备、R09 恢复/fence 均未交付。

## 实际作者验证

| 范围 | 实际结果与证据 |
| --- | --- |
| 新回执单元 | 17 项通过，0失败/错误/跳过；精确字节/真实RSA、错误版本/actor类型、wrong audience/outer、未知kid/Base64URL、防御副本。`.local/evidence/unit-first/` 与最终 `.local/evidence/boundary-final/` XML；同17项不累计 |
| 台账与4GET | 9 项通过，0失败/错误/跳过；两个合成族、原键回放、原字节Web/PDA详情与original、恢复mode/epoch改变后的旧键、异正文冲突、新键旧expected拒绝、签名/审计故障整体回滚、保存EC密钥后的挑战重登、跨actor/撤读权、坏BLOB闭合拒绝。`.local/evidence/it-second/` XML |
| 生产边界 | 2 项通过，0失败/错误/跳过；真实无测试bean应用上下文中参与者集为空，新尝试无request/receipt/event/audit且decision仍0；映射只暴露4GET。`.local/evidence/boundary-final/` XML |
| 构建与契约 | 生产/测试 Java 编译、bootJar通过；SwaggerParser 校验正式 OpenAPI 0.30.0 的120路径通过，`.local/contract-first.log`。MockMvc不是外部真实HTTP |
| 新库 | 新19326精确port/datadir/schema/account护栏，空库V1→V34成功；最终 Flyway 与台账数量、NORMAL/epoch1 记录 `.local/evidence/database-final.tsv`；没有改V34或旧库 |

作者共 **17 项新增单元 / 11 项不同 IT**，不是全仓单元/集成或组合矩阵。未在本作者批重复第五轮旧消费者/非空升级；双连接竞争、第二次查询竞态、唯一键异常后恢复、更多完整性坏关系和跨语言向量交 W2/W3 独立验收，本记录不提前计作通过。

## 失败与修正

首轮9IT全失败，原日志 `.local/it-first.log` 和原XML `.local/evidence/it-first/` 已保留：8项fixture只授WAREHOUSE_ADMIN/CREATOR，未授CONFIRMER，产品正确返回403；另1项SHORT_APPROVE到达新写后暴露 JDBC DATETIME 的 `LocalDateTime` 被强转 `Timestamp`。补明确fixture角色，并把三处SQL时间读取统一支持UTC LocalDateTime/Timestamp后，同一新合成库追加独立来源复跑9项全过；没有删库、repair、清理失败来源或重跑旧断点。

首单元和两次编译均通过。文档补丁首因整段上下文不匹配未应用，随后按准确标题插入；若干探索性不存在路径/Windows glob读取报错，无文件或数据库变更，不作产品验证失败。

## 环境收尾

本批只启动 `127.0.0.1:19326`，数据库 `acrylic_outbound_ledger_20260921`，账号 `outbound_ledger_20260921`，datadir `.local/mysql19326/data`，独立私有文件/开发RSA签名与合成EC私钥。失败日志、XML、所有合成库行/文件/密钥均留在 ignored `.local`，不入 Git。源码LF摘要留在 `.local/evidence/source-final-hashes.json`。

经port/datadir精确护栏后 `mysqladmin shutdown` 正常停止19326，记录 `.local/evidence/stop-19326.txt`，无监听。原13316/19321–19325仍未启动，其它MySQL与W2授权19327、原数据/恢复断点/AVD/M71/现网不动；旧BCD未恢复，审批阻断不重试。
