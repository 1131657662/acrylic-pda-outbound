# D13 历史 FORMAL 原依据 admitted TCP/P5 只读消费

## 结论与范围

在全新隔离实例恢复已保全的合成历史终态，只消费既有案件的 context 和 original-receipt GET，不执行发现、提交、审批或旧 STOP/续作/接收者确认/激活链。最终 run1946 实际 IT 1 项，0 失败、0 错误、0 跳过，运行器 exit0；同 JVM 应用上下文重启后再次逐字读取，P5 独立后继进程输出 STARTED。仅本片合成历史原依据读取的有界 L1，不是 D13 整体、原身份/现场、完整恢复、实际最终候选或总目标第8节通过。总目标保持 active、未完成。版本事实见看板事实块。

输入 HEAD 为 `68c4c5a473bd0e02d7f4eb3f720c2c5b12d256f8` 加本片明确改动及受保护继承 WIP。运行时输入、原字节补丁和全部文件 SHA 在每个 attempt 的 inputs.json / tracked.patch / status.txt；初始 42 文件快照和 dirty-before.zip 在证据根，除看板外 41 份继承文件 SHA 在本片实现与验证结束时全同。

## 输入与保护

- 证据根：`.local/goal-20261021-resume/d13-quarantine-read-audit-20261021-025358/`。
- 原 dump：`.local/goal-20261013/returned-stock-preserve-1462-01/database.sql`，1988652 bytes，SHA256 `39c03b4b314f340a72bb84b2400d53d9fe2d5a1a26730bf66c5c4abf42b20d68`，原文件未改变。它含原生 `_binary` 字节，不经解码重编码；使用 `--binary-mode --default-character-set=utf8mb4` 原字节导入新分配的隔离 schema。无 CREATE DATABASE / USE 指令；保留原 DEFINER，在新实例内创建锁定的对应合成账户，权限只授本次 schema。
- 原 storage 8 文件、browser-input、原案件 restart-input、32893 字节 original-formal-proof 和原开发公钥均逐 SHA 复制。historical-source.json 保存源/目标/长度/SHA。原私钥不复制、不用于新签；只生成新的开发签名对，并配置既有历史公钥验证器。无正式签名。
- 历史案件已经 RESOLVED，读取原 caseId 的 APPROVE context GET；命令名称仅为既有读授权参数，不执行 APPROVE POST。不得为通过测试倒改为 QUARANTINED 或重建旧链。
- product-before-formal-read-fix.zip、各 runner/test-before ZIP、docs-before-closeout.zip 保存改动前原字节；reconstruction-inputs.zip 和 manifest 保存源码/锁/配置等可重建输入。原资料、原身份、Keystore、回执/签名、唯一证据、最终 APK、取消链及继承改动保持。

## 实际发现与修复

真实 admitted GET 首先被有限 SQL 效果门拒绝：FORMAL 查询用了未分类的 LOWER；同一路径后续泛型 queryForList 会使用 getObject，也不兼容该门。将十六进制小写规范化移到 Java Locale.ROOT，单列 JSON 用 String.class，FORMAL 证明所需多列按既有 getString/getLong/getBytes 显式读取。权限、签名及哈希/引用绑定校验不放松；效果门、SQL 分类器、生产配置及业务命令未改。

修改仅限 FormalQuarantineOrigin、FormalQuarantineRecovery、FormalQuarantineApplication、FormalQuarantineApproval、FormalQuarantineAppliedReceipt 和 QuarantineFacts 的读取机制。FORMAL 历史原件签名仍由产品验签器验证；原签名内容、原命令哈希、实物证据和应用/终结回执绑定仍按原机制检查。

## 真实结果与失败

| attempt | 结果 | 解释与处置 |
| --- | --- | --- |
| 1941 / DB19371 | 导入失败，未启动 IT | 客户端 auto 字符集对原生二进制 UUID 误解释反斜杠；保留原输入/命令/错误/最终 dump。 |
| 1942 / DB19372 | 导入失败，未启动 IT | binary 字符集已过二进制插入，但 DELIMITER 客户端指令失败；未改写原 dump。 |
| 1943 / DB19373 | IT 1/1失败/0错误/0跳过 | 显式 utf8mb4 导入成功；真实 context GET500，LOWER 被效果门拒绝。 |
| 1944 / DB19374 | IT 1/1失败/0错误/0跳过 | 实现修复后，DISCOVER 对已终结案件返回404；是测试入口假设错误，不倒改原状态。 |
| 1945 / DB19375 | IT 1/1失败/0错误/0跳过 | 原 caseId context/下载在重启前后均200、原字节核同、逐次读取守恒；全局断言误把两条 WEB_LOGIN 审计当业务变化。 |
| 1946 / DB19376 | IT 1/0失败/0错误/0跳过；exit0 | 显式断言仅新增两条同原 checker 的 USER/WEB_LOGIN 审计；全部其他表及原审计、storage 守恒。P5 独立后继 STARTED。 |

六轮都使用全新 attempt，不复用失败库/证据。专用 HTTP19434 每轮由运行器确认释放；每轮认证最终 dump、shutdown exit0、进程退出及 DB 端口无监听记录在 identity-final / shutdown / mysql-stopped。系统3306身份未知，不连接、不停止；旧原实例不启动。

最终 8 个 TCP：4 个业务 GET、2 个 CSRF GET、2 个登录 POST，全部200；无业务 POST。两个 original-receipt 各32893字节与保留原件逐字一致，且等于 context 中 originalReceipt。两组读取前后178个非会话表和8个文件逐项相等；整轮只增加两条经用户/类型/详情核对的登录审计，原审计未删除/改写，其他表包含身份、权限、原账、结果、周期、实物处置、案件和签名回执保持。会话表是显式排除项，不将登录会话新增宣称无写入。

最终 XML SHA256：`138b9a517e888a1baa17b8e94c258ed6b6b255560a2328a8763b98daa5bc604c`。原件下载、HTTP 元数据、完整类型化数据库快照、storage SHA 和 P5 输出均在 `.local/e2a-cross-20260925-1946/`，汇总见证据根 bounded-result.json。

## 回归、限制与接续

普通回归实际 exit0：310 suites、4804项，4785通过、0失败、0错误、19跳过。19项为外部SKU向量1、closure原件9、review原件8和Windows符号链接1，逐项见证据根 `ordinary/result.json` / XML；不改写skip、不替代IT或业务验收。旧普通结果已另存 previous-results.zip。

本轮原历史输入没有 formal_disposition_application 行，实际走原终结回执分支，不能据此声称独立 signedApplication 行非空分支覆盖；其 typed mapper 编译与普通回归不替代相应真实输入。未运行设备/UI、原身份/现场、独立部署 API-worker、解除隔离业务完整链或总目标最终组合。不执行旧 FormalQuarantineHttpSource 来制造取消链。下一步仅在有明确未覆盖分支和可追溯原输入时接续，缺输入保留未验，不机械重复本片。

## 收口清理与持续边界

清理计划仅包括本片六轮认证停机、无监听、无消费者的18个 data/binlog 目标（1727文件，1293596265逻辑字节），逐文件SHA/命令/输入/最终dump和datadir身份另存。自动执行策略拒绝明确 PowerShell Remove-Item 操作，未执行删除、未绕行，实际释放0字节，目标原样保留；见 cleanup-plan.json / cleanup-blocked.json。

不恢复换机进度承接、多跳、接收者确认/激活；七类生产决定/TEMP生产归档 fail-closed、localDeletionAllowed=false。原库/现网、正式签名、main、部署/Release保持关闭；开发备份、治理与清理不计验收。
