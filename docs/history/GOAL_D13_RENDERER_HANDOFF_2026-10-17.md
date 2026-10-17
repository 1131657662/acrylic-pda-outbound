# D13 renderer未确认排空围栏切片

状态：run1559的有界renderer/服务端L1及独立消费通过；不计D13整体、L2或D18L3。输入HEAD及实核远端为f3ad4289（完整SHA见本批inputs/git），固定树、唯一写者和版本事实见看板。完整推送后快照inputd13worker-postpush07.json及同号patch/未跟踪副本/写权/环境已保全；SHA323c1e2667da276368db1ef0c467a7e6d8e73c9e360be82bd8d70860b61ff93c。

## 默认、理由与范围

沿既有RecoveryOperationRegistry/RecoveryWriterLease/RecoveryEffectGate/RecoveryFenceAuthority实现有界接线。renderer启动前登记PROCESS_HANDOFF，实际确认退出后才关闭子租约；任何终止未确认先将根置FENCED、毒化并保留L2，再调用既有UNKNOWN围栏序列。若halt返回或未配置跨进程权威，调度finally/close不能把隔离根改为RELEASED或放开锁。该选择防止普通worker错误处理吞掉未确认结果，理由是关闭请求/任务不等于文件写者排空。

不另建权威、不改变默认关闭开关、不新增放行/解除端点。普通fence（已有拒绝路径）与明确quarantine区分；本切片未实现全部根的P5正常退役删除协议、全部文件入口及完整D13。取消的换机承接/多跳/接收者激活不恢复，D16和生产边界保持。

## 可验证标准与影响

| 改动路径 | 影响层 | 必跑 | 复用依据 |
| --- | --- | --- | --- |
| server/src/main/java/com/acrylic/outbound/recovery/RecoveryProcessHandoff.java | 根/进程子租约、效果门 | 默认关闭兼容、无根拒绝、越集拒绝、正常释放、UNKNOWN返回仍保留并阻推进 | 既有权威/锁按本批source-bindings精确路径SHA绑定，不重新实现 |
| server/src/main/java/com/acrylic/outbound/recovery/RecoveryOperationLease.java | 显式隔离根关闭 | 隔离后close不可退根/解锁；普通请求根测试回归 | 未改普通fence语义；先前HTTP/Session报告仅作历史引用 |
| server/src/main/java/com/acrylic/outbound/exports/ExportRendererProcess.java | renderer真实OS边界 | 真实父/子进程丢租约/超时/中断排空；lost-receipt子JVM真实halt70及后继权威拒绝；真实HTTP/MySQL正常XLSX/PDF回归 | run1557输入已发生产品漂移，本次必须新库执行，不能用旧通过替代 |
| 新增或直接修改Java测试/夹具、Python runner | 测试消费者、原生锁、真实SQL/HTTP、文件 | 新目录/端口/非空迁移；新鲜XML、实际文件独立消费、数据库dump及认证停机 | 明确合成CLI/身份/输入；终止回执丢失为MODEL故障，不能冒称真实OS拒绝终止 |

本批源码/测试/运行器逐路径SHA、完整工作树和继承保护另存.local/goal-20261017-resume/d13-renderer-handoff01/及新attempt。独立子JVM只执行本项目合成控制树；真实halt不得落在主助手、Gradle或其他任务。Android/Web产品及契约/迁移无改动，未运行Android/Chrome不记L2/L3。只允许有界L1开发备份。

## 实际运行与独立消费

输入HEAD/实核远端f3ad4289437e8a272c051d7e55160f7b89511c76。run1559新隔离DB19338/HTTP19442，命令：

```text
.local/r09-control-venv/Scripts/python.exe -X utf8 docs/parallel/R09/tests/run_d13_renderer_handoff.py --run 1559 --port 19338 --http 19442
```

runner exit0。普通test共20项（handoff6、真实renderer3、请求根数据源11），integrationTest共2项，全部0失败/错误/跳过；没有声称完整server test通过。真实父/子进程丢租约、超时、中断排空回归通过；无根renderer不创建attempt目录，HTTP根越集拒绝，正常子租约释放，未知围栏回调返回后根仍FENCED/registry1/子操作1、L2已毒化且新根拒绝。该返回分支使用明确的MODEL Authority，不称真实halt失败。

独立子JVM内先创建WORKER_JOB根，再运行真实合成ReportCli。父JVM观察原生writers.lock独占tryLock为null。合成renderer真实排空后，OS边界明确注入“终止回执丢失”错误；生产围栏实际确认F1prime/F2/F3/F4并执行Runtime.halt(70)，子JVM退出70，renderer已停止、错误返回标记不存在。原P5租约和unknown记录匹配operation/process identity；复制原件后，独立父JVM中的后继权威以RECOVERY_LEASE_OUTSTANDING拒绝。该场景不声称OS实际拒绝kill、存在存活写者或业务任务发布已单独实测；触发根为测试显式创建，非生产scheduler创建。正常scheduler端到端则由同批另一IT实际覆盖。

同批真实HTTP/Session→生产非web调度→真实独立JDK renderer→XLSX/PDF→下载回归通过，默认关闭XLSX通过。API/worker为一个IT JVM内独立Spring上下文。401/跨仓404/同键同任务/异文409、根外JDBC拒绝、独立Python持根阻推进、completion末SQL回滚、正常退根与stale HTTP/worker拒绝继续通过。合成TEMP确认只用于准备输入，七类CommandOperation为空，无normal admission。

实际输出在.local/e2a-cross-20260925-1559/。独立工具命令：

```text
C:/Users/qq113/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe -X utf8 docs/parallel/R09/tests/verify_d13_renderer_handoff.py --attempt .local/e2a-cross-20260925-1559 --output .local/goal-20261017-resume/d13-renderer-handoff01/consumer01/result.json
```

消费PASS：37条HTTP原字节、两XLSX各7表/前导零/整数克/原顺序/无公式、两页PDF/无批注，三份业务orders一致；独立核真实SQL三导出任务/一FAILED probe/零probe行、控制回执、子JVM围栏帧、原P5/unknown身份与SHA。PDF两PNG实际看图，无裁切/重叠/缺字。最终dump SHA256 d9db8eed2e97cd0e0fb50b8454388078e75aeb3f0ed6786b90d90ef588987ae8。作者/自审同助手，独立工具不称独立人员。

## 失败、自审与清理

run1558普通19项及正常导出IT通过，native IT在已观察子JVM退出70/renderer停止之后，读取默认编码stderr时MalformedInputException，整轮失败。原stdout/stderr/XML/SQL/HTTP及P5证据保留。只修子JVMstdout/stderr显式UTF-8，不改原字节。后续自审新增无根不落盘回归先实际失败（rootless-red.xml/log），证实tmp mkdir早于子租约；修为先登记，再创建目录；原生终止边界RuntimeException和Error均进入隔离。用新1559完整复验，未复用旧库，失败次数不清零。

run1558未跟踪源码在更改前以原inputs SHA逐项保全（untracked-source），tracked.patch/inputs保留，新增回归前字节按SHA复原验证后存档。run1559完整预跑快照inputd13renderer-before1559-01.json共5550项，SHA329ffdf4716692d1be5d8a58af9500ac1902229b601dc186ab0b4d20e3a8df59；同号patch/未跟踪副本/环境/写权保留。

本切片基线inputd13worker-postpush07.json的5543项逐字核对，只改renderer、根lease和直接renderer测试三个既有路径，继承源码其他字节未动，详见d13-renderer-handoff01/inherited-protection.json。直接/复用32路径的实际SHA见source-bindings.json；独立consumer为运行后新增的消费者，实际读取1559原产物。

1558/1559均认证停机，专属DB/HTTP无监听，项目JVM/renderer退出，无ADB/模拟器；共享MySQL4632/5440只观察。清理前保全逐文件SHA、输入/命令/锁/结果和停机/无消费者，cleanup1558-1559.ps1仅清合成datadir/binlog：逻辑445271853 bytes，D余量56853528576→57307037696 bytes（卷观测增加453509120）；C78769664000/E195634057216 bytes。原SQL/HTTP/XML/失败原日志/开发DER/P5与合成控制树均保留。用户指定run03证据、继承/未提交源码、原资料/身份/回执/唯一证据/最终APK保留。

## 提交范围与接续

| 路径 | 影响层 | 本次必跑与实际结果 | 运行SHA256 |
| --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryProcessHandoff.java` | 产品根/renderer | run1559及consumer01通过，适用边界见上文 | `2a05ef45b6cc2054896de22ecd0913053b8ead7f4c53036ee51bc98469e0f8d5` |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryOperationLease.java` | 产品根/renderer | run1559及consumer01通过，适用边界见上文 | `89c877cd9a04327fca1ca90015de30c2e5b6fd689c8cbbf2dffd056895128617` |
| `server/src/main/java/com/acrylic/outbound/exports/ExportRendererProcess.java` | 产品根/renderer | run1559及consumer01通过，适用边界见上文 | `7a7d5228021e814d9df63127b3d511642197adc15eeb4f26ce128b58c21a9a8f` |
| `server/src/test/java/com/acrylic/outbound/exports/ExportRendererProcessTest.java` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `aa7c13f2c6fa7f449ed4e777b95510058a51cc72855acc507f02784256912a22` |
| `server/src/test/java/com/acrylic/outbound/exports/RendererDrainReceiptFixture.java` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `7b28ccd723c3089f6589e51ed5213df7401bf1aa2469348d2d524d2538a138ce` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryProcessHandoffTest.java` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `74bce92b19fd842a32e3feda040b5eef45868d8a37b0e4da4efda62494085696` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryRendererFenceChild.java` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `b578ff5bbd407c47d90651ba80877361e52e4b483d4d7a10dbcde0aac3013599` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryRendererFenceIT.java` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `842adf79ce783b7265b5aec010aaf5f224043b5989502ada1652918e80023ab9` |
| `docs/parallel/R09/tests/run_d13_renderer_handoff.py` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `315af48d0208053077880c9960d962451003f4052dddb40d1f246e654b183fea` |
| `docs/parallel/R09/tests/verify_d13_renderer_handoff.py` | 定向测试/消费者 | run1559及consumer01通过，适用边界见上文 | `f05c664c74e7b00af3f6ef8d18d4965b49a1ce7c2612b50c0c4ec325a06f3863` |

本报告、D01矩阵、进度/history索引、看板及旧看板归档仅修改验收入口和断点，必跑引用/差异与事实脚本检查；不代替业务验证。完整提交前后inputd13renderer-prepush02.json/inputd13renderer-postpush03.json及同号patch/未跟踪副本/写权/进程/监听/盘余量/remote为精确断点。

后续先核RecoveryOperationLease.close与RecoveryFenceAuthority的P5正常租约退役：现行正常结束根仍留记录直到进程死亡后孤儿扫描，该事实不符合完整退役协议，不能记D13完整通过。完整文件入口、错误根/库/水位及组合、Android/Chrome/L2、统一候选和D18L3仍未完成。D16可信合法退出依据仍缺，不降低标准。生产默认、TEMP归档、七类能力及localDeletionAllowed=false保持；不main/发布/部署/正式签名/原库/现网。
