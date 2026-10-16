# D15 原非空恢复副本程序回退读取兼容

新→旧→新三个实际JAR进程已在同一恢复副本完成真实HTTP读取，业务响应、原行、结构、trigger及私有文件保持，仅新增明确的合成查询身份、登录审计与会话。本页只收口有界程序回退读取切片，属于组合开发备份，非D15整体、E07整体L2、D18 L3或总目标完成。版本事实见看板生成块。

## 输入与影响

- 固定树`D:/acrylic-pda-outbound`、分支`codex/development`，输入HEAD/实核远端`960932691d5f561ed7492fd3c3eb8f2fd33fc3fa`。本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`独占源码/看板写权；清理聊天已结束并释放窗口，实际列表idle，无子代理。
- 全部tracked/untracked路径、长度/SHA、binary diff、未跟踪副本、worktrees、旧看板、写权和进程/监听/磁盘见`.local/goal-20261016-resume/input16.json`及同号附件，5397项，清单SHA`b0e0dc8f1dd9b82f2357bb80f3a8635c5bffb01929c650ecf4569261afcce482`。逐项核对仅既有未跟踪夹具`d15_program_rollback_fixture.py`被本批继续实现，其余继承输入未变。
- 旧JAR`.local/goal-20261013/e06-conflict-audit-1535/server.jar`，280719856 bytes，SHA`adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1`，来源见[冲突扩展服务L1](GOAL_E06_CONFLICT_EXTENSION_L1_2026-10-14.md)。新JAR`server/build/libs/acrylic-outbound-server-0.1.0.jar`，280775384 bytes，SHA`e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba`。每次启动保留精确路径、参数、PID、监听PID、日志及哈希；未重建产品。
- 原667完整SQL SHA`fa024e4683d9471fd329107614993d4bec0368a6c919ae333f9bb363a8bcca30`，来源见[产品提交仓库恢复组合](GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md)。604文件/261439064 bytes清单SHA`180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3`，前后逐件保持；未连接/重启667。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `docs/parallel/R09/tests/d15_program_rollback_fixture.py`、`d15_rollback_sql.py` | 原SQL/文件恢复、全表事实、合成查询身份 | 原字节导入、禁止复用、身份核对、HEX逐值/DDL/原trigger与文件守恒 | 原667 dump/604清单SHA见上；其余Android/Web/迁移输入未改，按input16逐项SHA和原报告复用 |
| 同目录`d15_rollback_runtime.py`、`d15_rollback_http.py`、`run_d15_program_rollback.py` | 新旧程序/真实HTTP/进程隔离 | 新→旧→新，每段CSRF/登录/401及五读取、服务模式、独立JVM/监听PID、认证停机排空 | 两JAR SHA见上；仅原FORMAL读取，不扩写为多任务/新格式兼容 |
| 同目录`d15_rollback_compare.py`、`verify_d15_program_rollback.py` | 离线SQL/HTTP消费与允许增量 | 原业务行不变，三条WEB_LOGIN，每段一个本身份会话/安全上下文、原会话保留，dump表/行，HTTP原文摘要 | `program-rollback-run04-input/manifest.json`固定实际运行六模块SHA；后补会话精确断言仅离线回读 |

七工具最终路径/长度/SHA见`program-rollback-tools-final01.json`，SHA`5835dd869af7a67348b464aa0dd76c38b98911f15d698f4be83af882c7c7c906`。实际环境Python 3.14.4、已有bcrypt 5.0.0、已安装JDK路径见命令；未安装依赖。文件/函数单职责限制、SQL标识符白名单、原生客户端尾哨兵、禁外部MySQL登录配置/代理、专用ACL/随机凭据及不输出密码已自审。无产品、Android、Web或迁移改动，无新APK/Chrome/普通全回归结论。

## 实际业务结果

证据根`A=.local/goal-20261016-resume/`、`B=.local/e2a-cross-20260925-673/`。命令`python docs/parallel/R09/tests/run_d15_program_rollback.py`，`A/program-rollback-run04.log` exit0；main驱动，非JUnit/XML。

1. 新673/DB19383/HTTP19449（预留19450–19451），分配前路径不存在且端口未占用。schema/账号后缀673，datadir/端口/数据库/CURRENT_USER/binlog/server UUID每段前后固定。原SQL含原DEFINER，隔离restore账号单独拥有SET_USER_ID；应用账号无该权限，原DEFINER账号在副本锁定并仅授本schema权限。原SQL未改写。
2. 原恢复180表/322行，含迁移历史；7存储文件、2开发验签DER及原HTTP输入共10文件保持。仅新增合成Web查询用户/角色/权限关联/用户角色/仓库授权共5行，精确HEX行保留，不冒充原PDA或主管身份。
3. 三个直接JDK独立进程new01/old01/new02，每段未登录订单详情401、CSRF、真实登录200及订单列表/详情/revisions/publications/订单exports列表五个200，共24次HTTP。服务模式非UNAVAILABLE，列表/详情含原订单ID，修订/发布非空，三段业务data逐值相同。合成用户exports列表为空，不声称原用户导出下载或旧worker兼容。
4. 每段177表逐值相同，仅operation_event新增本合成用户一条WEB_LOGIN、spring_session新增一会话及spring_session_attributes新增其SPRING_SECURITY_CONTEXT；原审计/会话保持。最终180表/336行。DDL/原trigger保持，仅operation_event下一自增值按登录推进，其他表计数不归一化。Flyway关闭，无迁移/事实转换。
5. HTTP完成后定向终止专属JVM并核端口排空，监听PID与直接JDK进程PID相同；下一JVM只在端口关闭后启动。Windows使用TerminateProcess，不声称优雅停机或Linux项目启停。worker/引导/会话清扫和生产能力保持关闭。
6. 完整dump SHA`98e8836988a337dc9d936423935d0b1b5efa8d2994b2859208aa15bf849f90b4`，离线解析180个CREATE与336行。`python docs/parallel/R09/tests/verify_d15_program_rollback.py`的consumer02 exit0，SHA`6f0b57f0a1b1af7485526dff57c260967f97013c4f7d616f6928f835f4cd8f74`，核SQL事实、每段会话归属、HTTP状态/摘要与原667文件。消费者是同作者独立算法，不称独立人员。

## 失败、保全及边界

- 671导入原trigger缺SET_USER_ID，SQL已部分提交；完整dump/认证停机、19381零监听，不复用。196文件/189329718 bytes清单SHA`2bfa23581238e7c9d9312484e3764d4d0c0dbb292e4c5d2c75c8a4ca784796d3`。与669历史同类失误计数保留；有明确补权限依据才分配672。
- 672导入及第一段八HTTP完成，系统java快捷入口派生子JVM，父停后HTTP子进程仍监听，排空断言失败；MySQL已dump/认证停机。核父PID/JAR/本轮路径/端口后定向停止子JVM23324。路径双反斜杠导致首次严等检查拒绝，未停他人进程。420文件/223599555 bytes清单SHA`0d10dc894914910b636dce3e82e3c3cd6c5f8c7824cbcfc255da52ce36c0b781`。672不复用，673改直接JDK及监听PID检查。
- run03被既有目录禁止复用入口拒绝，零新业务运行；编号文字替换误触JAR SHA数字，静态自审核出并在run04前修正，未带错SHA启动。所有原输入/日志/失败指纹保留。
- consumer01已核完三段守恒/会话/HTTP，随后dump正则漏列名列表exit1；consumer02仅修原文解析再离线消费通过，无数据库/HTTP重跑。原结果保留。
- 673完整dump/认证停机，485文件/225893307 bytes双次核同，清单SHA`dc1dc6d46352cec396828c5c77aeb7069c4e3b8311ce8d481c73244da53d68c5`。671/672/673端口零监听，无本批Java/MySQL残留；共享mysqld4632/5440仅观察不连接/停止，原AVD/ADB保留。所有失败datadir、dump/binlog、HTTP、私有材料/原日志保留，无清理。
- 最终环境采样C/D余量82826579968/44549193728 bytes。新证据/DB/tmp均D盘，仅子进程设置TEMP/TMP。七类生产决定、TEMP边界及localDeletionAllowed=false保持；不接原库/现网、不合main、不发布部署、不用正式签名。

仅指定两份程序本体在指定原FORMAL事实的读取回退兼容通过。旧程序写入/worker、新多任务/重锚格式、Linux项目服务启停及完整升级回退组合未验；D15整体、D16正常合法退出、E06/E07/E08剩余L2、E09实际候选、D18最终L3继续。下一步核E06受影响组合缺口及既有Chrome审批阻断，仅有新依据才补验，不换包装绕过。
