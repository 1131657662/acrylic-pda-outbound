<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）

规则更新时间：2026-09-22（Asia/Shanghai）。本次仅恢复默认调度与模型配置；下列业务、环境与执行者信息除明确标注外沿用19:48断点，未复验远端、监听或测试。本区结束即停止读取。

- **默认模式已恢复**：取消固定主协调/三执行者、禁止主协调开发及强制转派。当前助手可在已核写权内直接完成工作；未经用户要求不主动创建子代理。成本角色已移出加载目录，固定子代理模型/推理强度/并发数已移除；本机全局模型恢复调整前备份的 `gpt-6-astra-pro / xhigh`。配置解析与差异已核，现有会话实际模型未声称已热切换；重新启动 Codex 后继续。此条优先于下文及其他文档中的旧编排规则。
- **本次核对**：主树 `codex/development` / `93bae37aa399598be15d1fd82a3c551f70497892`，origin仍原仓库。修改前仅原 `docs/out.xlsx` 未跟踪；本次另修改 AGENTS、Git工作流、看板并新增历史快照，未提交/推送。任务列表显示旧总调度与部署任务均 systemError，不将旧代理身份视为活跃；本任务仅配置/规则写权，不接管业务、DB、重型槽或部署。
- **历史保留**：恢复前完整当前区见 [默认恢复前快照](history/ORCHESTRATION_STATE_before_default_restore_2026-09-22.md)。原业务准备、作者、独立、组合结果及失败/未验继续如下；下一次业务接续先核写权和实际环境，沿原树/提交继续。

### 当前目标与下一步

- 继续R06 E2a非空V34→V35验收：固定生产fa7981d不变，最新整合候选568683c。准备、作者、独立、组合证据分列，不称E2a整体完成。
- **当前优先级调整**：部署旧执行者反复空Payload且全部停写，总调度将已确认可用的launcher_finish从E2a冻结转派原sixth-wave-integration部署runtime；已收到精确交接并完成官方Chromium安装/副本1..34全success核验；现在配置/启动副本runtime与真实导出。e2a_migration只保存管理断点后冻结。
- E2a暂停新业务执行；后续先补/核初始化二次中断后进程退出确认与启动前实际环境/证据目录；按槽排期聚焦护栏测试，再分配19329进行target34→seed→before→target35→28案negative→preservation。失败库、marker、文件/日志保留，禁止reset/repair/删证据/重复seed。
- 独立test部署优先继续副本API/Worker/三格式真实HTTP、原列逐值与文件保留验收，再按用户已有授权切test。E2a不占远端部署/DB写权。

### Git、原树与执行权

- 主目录 `D:/acrylic-pda-outbound` / `codex/development`：最近已推管理SHA `48873f7ae3053629af6a4907f6b9ccf796fe95fc`，本轮仅主协调看板待执行者窄保存；保存后用Git核新SHA。origin仍既有项目远端。原未跟踪docs/out.xlsx保留。
- 唯一总调度 `01a0c7c8-b72e-7980-91a3-3e594373319f`，只维护五管理；旧01a0c70f已交接停写，旧BCD不恢复，无新增app任务/树。执行树根D:/acrylic-pda-worktrees，同名codex短分支。

| 职责 / 执行者 | 原树 | 本地与远端已核SHA | 当前状态 |
| --- | --- | --- | --- |
| W1 `/root/e2a_migration` | r06-e2a-foundation | 319305f741112d92323aec1ac29af1d2e53fc284 | 种子静态完成；现只读消费者，业务写权冻结；仅原.local-e2a-vector.log未跟踪 |
| W2 `/root/launcher_finish` | r06-e2a-migration | 568683c1a1f8257ad0596a654c6b3558c767ed95 | 整合/启动器修正已推、树干净；作者窄编译通过，当前冻结待排期 |
| W3当前冻结 | r06-e2a-protocol | 79cad44199c5e30c1f9d92f3537b9c17cca8f734 | 4control+24negative已推；仅原bridge-classes未跟踪 |

- migration普通merge为47ef1a3（foundation）和5beea79（protocol），两最终源SHA均ancestor；生产E2aProtocol/V35与fa7981d diff为空。07cab59/799f49f旧失败历史已由b3fe56d+3aa624d保留，不amend/reset/force。
- 两E2a执行者曾429 retry中断，18:54沿原身份MESSAGE+followup续接；旧negative_matrix/seed_finish等停。当前最多2E2a+1部署，阶段结束冻结业务写权，管理保存由执行者明确路径执行。

### 准备、作者、独立与组合证据

- **准备完成**：foundation0ef7f29修receipt别名/requireOne、SCAN/真实cycle、集合计数；319305f返回本次精确fixture IDs、第二合成用户、CREATE_NEW e2a.csv/真实SHA。protocol79cad441修15字段构造、事务外异常存证、28案文档；缺第二用户/scope则准备失败。
- **作者/当前编译**：568683c的server compileTestJava通过，主协调实读migration/.local/compileTestJava-20260922-191827.stdout.txt，BUILD SUCCESSFUL，4 tasks/1 executed/3 up-to-date；191705前一版编译也成功（3 executed），后因源码小修重编。未跑本轮JUnit/DB/HTTP。
- **独立/静态**：e2a_migration审protocol79cad441+foundation319305f未发现确定SQL字段绑定阻断（其foundation部分只计作者自查）。4合法引用形状与24负向错误映射静态一致；实际错误码仍需MySQL验证。
- **独立/启动器**：ee12eba/c6bcb9c审c5462e9；复审9be99b8发现CRLF hash阻断与stopped顺序误报；568683c增量只读复核确认这两项已修。primary/suppressed和owned process清理已补，初始化二次中断残余限制保留。
- **资源证据准备**：V35 Git blob cdfd21cddc0b16284b388026b6d0c3da53878498与fa7981d一致。Windows raw SHA DF180DAC681899C21BE94820267208C29091F39ABB4C2ABABB205DD2F97C9515；仅CRLF→LF后canonical CE433B38BDC06A2050C3E4A6C4BA413C8FF57E55EDC1477A65D78667F30F420C。运行时验证唯一classpath资源，CREATE_NEW v35-resource.json记录URL/双hash/固定生产SHA；当前未启动，JSON不是已生成动态证据。
- **历史窄测**：c5462e9环境护栏8项、ebbaa63路径helper2项XML均0失败/错误/跳过，仅对应旧版本；协议fa7981d作者9与8241c53独立25桥接为历史通过，未重跑。旧完整服务器139/140（SkuParserTest缺外部样本）保留。
- **组合/未验**：Git已整合且编译通过，不等于组合行为通过；未跑本次护栏JUnit、真实非空迁移、28案SQL矩阵、行/DDL/Flyway/文件保留、MySQL启停、真实HTTP/设备。每案只比数据库行快照（排除Flyway），迁移前后另比旧表/DDL/Flyway/文件，不能混计。

### 当前限制与失败保留

- 初始化清理在二次wait期间再次InterruptedException时force后未再次等待确认；可能遗留未确认初始化进程。动态前需补强或明确检查/证据，不能把server=null且端口空闲写成进程必然退出。
- 旧启动器aea2c5d仍覆盖主异常/清理不足、9be99b8资源hash/marker阻断均保留为失败候选；568683c仅静态+作者编译消除已知主问题，未动态验证。
- CREATE_NEW资源/marker残留会阻断重跑；保留原文件与失败库，后续由总调度明确续接命名/环境，不删除或覆盖。phase/精确全链SHA证据、Flyway success JDBC编码、路径/权限与实际生命周期继续验。
- cross-scope矩阵只替换scope验证父FK，未覆盖object/set/verification全部跨scope组合；字节摘要内容一致性、对象kind、route、可信签名属writer/协议语义，DDL通过不替代。合成SCAN复用STOP对象仅结构代表；e2a.csv为合成导入文件，未宣称来源业务证据可信。
- 旧向量6beb4f6、桥接适配、字体SocketTimeout、工具502/503/429/空payload/Windows失联分别保留；工具故障不计产品测试失败。部分提交正文含字面\n、0ef7f29标题格式不符，保留历史不amend，后续用真实body文件纠正格式。
- 原身份/可信facts、异常回滚后另读、旧seed401/keyring/权限、真实HTTP、Android code20/Room17耐久、R05关键未验、R09/现场/审批均未被本轮消除。正常确认空理由、localDeletionAllowed=false保持。

### 环境、槽与独立test部署

- 19:21本机只读13316/19321–19329无监听；E2a唯一拟用127.0.0.1:19329 / acrylic_outbound_e2a_migrate_20260921 / e2a_migrate_20260921，migration树.local/mysql19329/data、private-files、signing；数据库槽未分配。
- 部署任务 `01a0c853-feef-7d40-a819-a8591b3278f7` 用户已明确授权test；原deploy_transfer及deploy_runtime均空Payload失联已停，现唯一部署执行者转为本总调度/root/launcher_finish；部署任务协调负责精确交接和验收，总调度中继，禁止再唤醒旧部署代理。全部远端部署/重型DB写权持续归该任务；19:16临借本机Gradle完成后19:20明确归还，E2a当前无重型槽。
- 部署业务固定 `386739cfbe450e0cb58937b0745c6a74bf4c7297` / API0.30.0 / V34，原sixth-wave-integration；三部署文件此前未提交，工具修复与业务SHA分列。排除E2a/V35、不合main、不动生产、不提前改PDA/APK清单。
- live目标114.67.203.242 / outbound-test.nbsmzwl.cn / acrylic-outbound-test及worker / acrylic_outbound_test：最近确认current=r05-sync-20260919-01、API PID2930319 active、worker inactive/PID0、V23/失败0、PDA code16；尚未切换。
- 副本唯一分配schema acrylic_outbound_v34_verify_20260922、账号outbound_v34_verify_20260922@localhost、回环18088、/var/lib/acrylic-outbound-v34-verify-20260922/{files,signing}、units acrylic-outbound-v34-verify-{api,worker}-20260922。执行者报告从不存在创建；旧R05副本/18087保留。
- **部署准备/独立核**：包/opt/acrylic-outbound-test/candidates/v34-386739c.zip大小506820083，SHA256 2A9904D98AB02C676A40AAF98888B9E4424D34728C32B5097FA62FE2B12779D5本远端一致；94hash通过，JAR34迁移/max34/noV35、业务diff386739c为空。Linux激活8/8；checkpoint原CRLF导致pipefail失败，临时LF副本7/7，已派独立-02修复包，不覆盖初版，修正候选结果待报。
- **部署副本**：作者报告恢复私有23文件/签名并V23→V34 apply11；部署主协调独立读v34-migration.stdout确认validate34/apply11/正常关闭。1..34全success为作者核；原列逐值/文件完整保留仍待核。下一副本API/Worker、Playwright1.55渲染、原WEB订单三格式真实HTTP与143启停；未切test。
- 部署证据根远端candidate/v34-386739c/deployment-evidence/，verify_backend_release、test_backend_activation、test_service_checkpoint及normalized stdout、v34-migration.stdout。Windows symlink权限/browser下载超时/429与传输中452MB hash不同阶段读数保留，最终包hash已匹配；移交先核旧子进程退出。

- **部署已执行补证**：launcher_finish实核副本101表、Flyway1..34全success1。官方Chromium140.0.7339.16/build1187、FFMPEG1011、Headless1187已装DATA/browsers；日志deployment-evidence/playwright-install-chromium.stdout SHA c34d65582b097577e8421a27c538d8c94726ddf3211c5d23225d8511546c50ac。
- **独立原值保留**：部署协调仅只读核备份/opt/backups/acrylic-outbound-test/20260921T201503Z的84表/66非空/782原行，逐原列BINARY null-safe各恰匹配1；missingOrChanged/nonUnique均空。仅Flyway+11、permission+4、role+3、role_permission+4，其余80原表行数不变；tar原3业务文件+2签名hash5/5。原工具chunk c267d4/60d75f在部署任务；历史restore唯一来源命令未找回，不能扩大为唯一来源链已证明。首次解析误假设显式列名被拒，补CREATE列映射后一次SQL通过，无新库/写数据。
- **恢复路径失败与修复**：原所谓23文件实为6顶层业务+错误整包套层；files/etc含旧敏感配置，files/var含套层业务。已保留字节移入DATA/quarantine-config-20260922，部署协调独立核0700 root:root、服务账号不能遍历、files仅6业务；不输出secret、不删除证据。首次移动hash比较因绝对路径变化失败保留，作者归一相对路径核验待最终报告。签名独立signing保持。副本units曾停为inactive/PID0，隔离后允许继续副本启动；live未切。
- **当前副本运行权**：只有/root/launcher_finish可写sixth-wave-integration与已分配test副本。原三部署文件未提交保留；独立-02候选尚待正式LF/权限/manifest修正。仅部署runtime可生成/etc/acrylic-outbound-v34-verify-20260922/runtime.env（私有）；仅无消费者副本账号可重设随机口令，live账户不动。副本端口18088、API/worker两个renderer变量、SuccessExitStatus143、worker2G/Tasks512。原WEB完成单/仓库已交接，三格式/143/正式切换尚未回报通过。
### 接续入口与边界

- W2专项docs/parallel/R06_E2A_MIGRATION/{README,ACCEPTANCE_STAGES}.md已更新整合边界；W1 MIGRATION_LAUNCHER_REVIEW.md、W3 V35_NEGATIVE_MATRIX.md定向读。旧文档章节不覆盖顶部。
- 正确构建入口gradlew -p server；本地字体foundation/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf的hash为2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b，使用r08FontFile绝对路径，不绕检查。
- docs/out.xlsx原SHA256 C3BABA70BC900975503457C5468363DE91417F156F2441ED71A3B7BE5E8FB6C4保持。原资料/密钥/本机配置/数据库/构建产物不入Git。
- V1–V34不改，V35未在E2a动态；HTTP接收/writer/文件事务/K/七类生产handler/POST/决定/放行关闭。E2a不部署、不合main；独立test仅按已授权范围执行。下一会话沿本队列核实际Git/写权，不重做完成工作。
<!-- CURRENT_STATE_END -->