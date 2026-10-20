# D13 历史非空应用原件 admitted 只读消费

## 范围与结论

本片沿输入 `9dedd915af4e3d94c6ba212751c2b5fee7711c4c` 补前片明确未覆盖的非空 `formal_disposition_application` / `signedApplication` 分支。只修改验证测试和历史源 allowlist 运行器，产品、效果门、SQL 分类器、权限和生产配置均未修改。

新隔离 run1947 实际 IT 1项、0失败、0错误、0跳过，运行器与 Gradle exit0。非空应用表恰1行，原回执字节 SHA 与表内摘要一致，JSON 与原证明中 application.receipt 一致。原证明32933字节在两次应用上下文间重启前后逐字核同。只记合成历史输入的有界 L1，不计完整D13、L2/L3、实际最终候选或总目标第8节。

## 输入与保全

- 工作目录 `D:/acrylic-pda-outbound`；分支 `codex/development`。完整42展开继承文件原字节、SHA、Git/远端/写权/环境在 `.local/goal-20261021-resume/d13-formal-application-retained01/` 的 baseline/wip-before/tracked-before/environment-before。唯一写者核实后事实脚本 Write/Check exit0。
- 历史来源说明：[未终结FORMAL实物应用历史报告](GOAL_E04_P06_OPEN_FORMAL_QUARANTINE_2026-10-13.md)。只消费已有1488原件，不运行旧续作或接收者链造新输入。
- dump `.local/goal-20261013/returned-stock-preserve-1488-01/database.sql`，2001442字节，SHA `359e5d27d1f75c9e5ced5db8f35470eb835cdc901ac5358ab6b12b55e0689d5c`。原二进制使用 mysql --binary-mode --default-character-set=utf8mb4 导入新schema，不重编码、不更改原字节。保留原DEFINER的合成账户锁定，仅授本schema权限。
- `.local/e2a-cross-20260925-1488/storage/` 8文件、历史开发公钥与原证明逐SHA复制；不复制原私钥，不重签旧事实。原证明 SHA `5e2bf0a34844264a7b89049c6d7f2a7f28ca515a0ff405d311ab003674da1f23`；源文件收口逐SHA核同。源清单在run1947/historical-source.json。
- 修改前 implementation-before.zip/json；文档旧区 docs-before-closeout.zip/json；可重建输入 reconstruction-inputs.zip/json；旧IT XML previous-it-xml.zip。新attempt不覆盖旧产物。

## 实际验证

命令：`C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_formal_history.py --run 1947 --port 19377 --http 19434 --source-run 1488`。内层 Gradle 命令保存在run1947/gradle-command.json。全部新日志、DB、临时与证据在D盘.local。

- 14次实际TCP请求：四业务GET成功200，两CSRF GET和两登录POST成功200，撤原checker角色后两个GET 403，撤原仓库关系后两个GET 404，损坏复制的payload后两个GET 409。无业务POST；错误体含对应业务码，且不包含signedApplication或原证明。
- 每次拒绝前后全非会话DB及storage不变；原角色/仓库关系finally逐行恢复，复制payload先保存原字节及摘要，finally逐字恢复。三次恢复后的178表与8文件逐项核同；整轮只增加两条原checker USER/WEB_LOGIN审计，原审计和其余表守恒。会话两表显式排除，不声称登录无写入。
- 两次下载均逐字等于原证明，同时等于context中的原依据。应用回执payload/dispositions绑定、签名及三false能力由原产品读取验证；测试补查独立应用行的原bytes/receipt_sha256/payload_sha256。
- 两次上下文均实际等候liveRoots=0并checkEffect；独立后继输出STARTED。无跨进程API/worker部署扩展。
- test-started/test-completed均存在；XML SHA `f830b62c93a367b7dcf6054bd736f4fc353e5f3e03912c7dbc7649d529e97f92`。verification-summary.json记录独立离线复核，run-evidence-manifest.json逐输出SHA（清单SHA `e8166c5e817197a393901c2acc3aaaaa7034fcd61bcc22876356bd98453c460e`）。
- 本片无新失败重跑。前片失败与skip原件保持。普通回归引用前片310 suites/4804项、0失败/错误/19skip，未声称本片重跑；两份全输入清单逐SHA比较证实产品/资源/普通测试/构建输入未改，仅两IT文件和运行器变化，依据在ordinary-reuse.json。

## 停机、清理与边界

新实例datadir/schema/user/端口/binlog启动前后实查；原未知3306/33060不连接不停。run1947 final dump/shutdown exit0，实际进程退出、19377/19434无监听。PDA仅用绝对ADB路径枚举M71，没有操作设备、模拟器或Keystore。

本轮347个可重建datadir/binlog文件、227842322逻辑字节已存逐SHA、命令、身份/停机和重建输入；明确PowerShell清理被自动策略拒绝，未执行、未换工具、释放0字节，全部保持。上一片被拒绝的目标未重试。清理计划/拒绝说明在cleanup-plan-new-run1947.json和cleanup-blocked-new-run1947.json。最终磁盘与进程/监听以environment-final.json为准。

生产开关、正式签名、原库/现网、main、部署/Release持续关闭，localDeletionAllowed=false。不恢复换机进度承接、多跳、接收者确认/激活链，不启动旧run03。原资料、原身份、签名/回执、唯一证据、最终APK与继承WIP全部保留。

## 未完成与下一步

非空应用原件读取这个明确缺口已补，但不代表完整解除隔离命令链或D13整体完成。原身份/可信事实、原机文件消费、UI/现场、独立部署API-worker、非空迁移及最终候选跨步组合仍按总目标矩阵逐项未验。下一片从实际未覆盖且有可追溯原输入的独立消费者或D13/D14截点缺口选择，不重复本片、不以旧链生成替代事实。总目标保持active、未完成。
