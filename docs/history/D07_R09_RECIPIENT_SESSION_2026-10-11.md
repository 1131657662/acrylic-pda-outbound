# D07/R09 恢复指定接收者本人会话准入

本项输入为主树codex/development、a45cddbe348ca4ad3de172c357ccba69db696c32及本聊天范围改动。单助手唯一写者；提交清单和精确Git/dirty/环境见.local/d01-d18-20261010/received-session-commit-paths.json及received-session-precommit/postcommit-01。原总目标D01–D18及第8节保持未完成，版本事实只见主看板生成事实块。

## 实现与可信边界

- 复用既有PDA真实HTTP挑战、原接收用户保留的密码哈希、指定新接收机P-256持钥证明、PdaSessions及Bearer；没有修改凭据或插入fixture会话。原来源1233、原机禁用、原正文/source代次与映射保留，新机普通TEMP仍关闭。
- 独立运维pin/RS256将实际session、LOGIN挑战/签名、登录审计、新机last_seen_at精确绑定新资格、准备回执、控制和指定双方。声明不保存密码、原始bearer或refresh，仅保留数据库已有token hash；原token仅在本机private文件中供本次跨JVM验证。
- 使用实际authenticate得到的本人PdaPrincipal，事务内及beforeCommit实时recheck；沿原closed operation/journal、同DataSource和身份锁序。不可变准入审计要求恢复态、已准备指定双方、新资格/真实会话/挑战的FK及有效窗，UPDATE/DELETE拒绝；retained重载不能创建缺失准入。
- 先核签名raw精确原列，再按既有confidential规则重建遮盖行，或核完整columns/count/digest，精确逆投影一个登录效果。通用Base64解码器未放宽；会话及PREPARE组合使用相同的raw逆投影。原设备准入、准备签名、全部原表/文件及raw beforeCommit比较保持，没有排除整表。
- 准入仅为限定scope/qualification的DOWNLOAD/CONFIRM依据，不产生ACTIVATE、首轮、扫码、普通TEMP/NORMAL或删除许可。独立运维/主管身份与新机密钥为开发材料，不代表现场身份；准入成功为实际HTTP认证接应用服务消费，尚无该业务的正向HTTP下载/确认或Android产品入口组合。

## 当次证据

最终1314实际MySQL专项1测试、0失败/错误/跳过、exit0/sourceUnchanged。同键首次竞争结果COMMITTED/CONTROL_COORDINATOR_BUSY，唯一审计行；原键重放、有效异签冲突、不可变审计，错pin/范围/代次/指定用户/实际session、缺PDA/误用Web身份、过期和缺失准入重载均拒绝。最后准入SQL及跨仓原行、人员版本、token hash、新机时间、撤销会话、独立签名材料尾变均回滚；原表、文件、迁移历史不变。

新JVM27672/原18800只重载原pin、签名字节和private原token；没有新登录/签名/登记、DB不变。最终Node消费者02核18输入、151表、8反例，独立复核RS256/P-256、LOGIN新增行/时间及准入唯一行、原表守恒、实际HTTP认证和跨JVM证据；工具不代独立人员审查。

投影单元6测试全过；受影响普通recovery/handover回归3323测试、117XML、0失败/错误/跳过、exit0/sourceUnchanged，不称全server或全部IT。各2731项输入运行时不变；普通回归后只调整三个专项辅助测试，由1314实际编译执行，产品及普通测试输入一致，不机械重跑。收口再次核对应报告、消费者原件、dump、原1233固定两端SHA和1293 UNKNOWN围栏，见received-session-close-verification.json。

证据入口均为本机D:/acrylic-pda-outbound：

- .local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1314/及received-session-rows-unit-01/、received-session-regression-01/保存命令、输入hash/diff、日志/XML/退出码。
- .local/e2a-cross-20260925-1314/保存登录前后/准入前后全表与文件、实际HTTP、安全摘要及跨JVM回执；private原token/密钥不上传。
- .local/d01-d18-20261010/received-session-consumer-02/、received-session-self-review.md、received-session-design.md保存独立工具与作者自审；前看板见[准入开发前](ORCHESTRATION_STATE_before_receiving_session_2026-10-11.md)及[收口前](ORCHESTRATION_STATE_before_session_close_2026-10-11.md)。

## 失败、保全与接续

1308凭据遮盖格被当Base64的失败、1310同名bytes歧义编译失败、1311非public测试辅助入口编译失败、1312PREPARE组合摘要接缝失败均保留原报告、XML/log/datadir/dump/binlog；compile-01/02保留且0测试。1313是补撤销/缺失重载反例前的中间输入，消费者01保留。各库dump成功、认证shutdown=0、PID退出、任务监听空，全部停机保全不复用；原1233只读，1293 UNKNOWN原哈希保持，1150/1151/UI-06及1100–1102未重跑。消费者01组合shell尾部读取不存在的可选failure.txt返回非零，Node自身result为exit0；消费者02显式保存进程退出码0。

29继承非看板路径、69历史比较、22原STOP保护不漂移；新日志/隔离库/TEMP/Java/cache在D盘，本项无清理。源码推送不代表.local私有材料或继承未跟踪文件已异地备份，不合main、不发布部署或正式签名；生产默认/签名和七类生产决定能力、localDeletionAllowed=false保持。

下一项接同一限定本人身份下载原manifest/正文、Android耐久暂存/确认，再实现新资格激活与最终提交。既有普通manifest/confirmation消费者仍假设旧TEMP资格，必须加有精确效果边界的恢复适配，不能修改原正文或使旧票据可激活。丢token后的新本人登录/替换还未实现；保留旧签名会话事实，不能以任意重发/重选绕过守恒，不能把当前拒绝当完整链完成。D06/D07其他缺口、D08–D18及总闸门继续，未定业务政策和现场限制保持单列。
