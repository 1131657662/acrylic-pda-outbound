<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：STOP 签名/配对来源前置核对，第六步未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 业务/隔离环境；主树 `D:/acrylic-pda-outbound` 仅管理/历史。未启用旧代理或并发写者，重型槽空闲；原库、现网、设备、正式部署与审批均未触及。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，约 94 项未提交/未跟踪；本轮新增 `SourceEvidenceIntakePairedKeys`、配对实库 IT 并修改只读前置门槛，既有 V35/V36/PAGE/STOP 脏改保留，完整清单以该树 `git status --short` 为准。主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原有 Android/server/管理改动及 `docs/out.xlsx` 保留，只新增本轮管理记录；均未提交、推送、合并、发布或部署。
- 作者/本轮实库：全新隔离 `.local/e2a-v36-stop-attempt1` 的 19330、专用空 schema 上，非空 V34→V35→V36 后实际 STOP 原文件先落盘、JDBC 对象与签名 PARTIAL 原回执、同键同文/异文回放、对象插入后事务回滚通过；IT 1/1，XML SHA-256 `9E3125923B8CFABF18E3E156028EA5506272BEA09EA202762EC7032B9C30564C`。事后 SQL 为 4 对象、2 回执、36 Flyway 行，回滚后孤儿文件保留、DB 引用不保留；这是内部 JDBC 写者，非生产 STOP 服务或独立消费者，详情见 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 作者/本轮代码与自审：只读前置门槛现从已存 OPEN 原回执重读并比对原字节，使用服务器选取的已完成配对且与当前设备登记一致的 P-256 公钥重解析/验签 STOP 原请求；不接受调用者构造的解析结果或任意 key。仍核 STOP 原回执精确对象引用、耐久文件、PAGE 根及每条原始文件。跨源、变 OPEN、坏签名/公钥、断引用、丢文件和缺页失败关闭。聚焦测试及最新完整后端 `gradlew -p server test --no-daemon` 为 56 XML、382 测试/0 失败/0 错误/1 Windows symlink 跳过；`git diff --check` 通过。它没有生产 VERIFY/set/GET/HTTP 调用者、不产生可信状态；无本轮独立消费者、前置门槛实库、Android/APK 组合验收。
- 作者/本轮实库：全新 `.local/e2a-v36-key-attempt1` 的专用空 schema 经非空 V34→V35→V36 后，以合成已完成配对行测试 JDBC key 读取、未配对/跨仓拒绝和设备 key hash 临时篡改回滚；IT 1/1、XML SHA-256 `874095F4988C40EF981B7E526C31CEC2A48CEC40F80D9B168ECF2C8BD0BD0AFB`，事后 36 Flyway、1 配对、0 intake。此合成行不等于真实配对审批或历史公钥验证，也未实库调用完整 preflight，证据见 E2a `ACCEPTANCE_STAGES.md`。
- 失败/修复：前轮 STOP IT 任务排除与事后 SQL 表名、前置测试 `IOException` 均已留档。此轮配对 IT 首次 `compileTestJava` 因 AssertJ 重载推断失败，显式 byte[] 后编译、实库 IT 和全套通过；失败未冒充测试通过。两套 one-shot marker、私有日志与 datadir 保留。
- 限制：配对公钥门槛仅接受仍与当前设备登记一致的已完成配对，不是完整历史 keyring；旧直接登记、换 key 或未知历史 key 继续不可核验。原机重启耐久、FORMAL/TEMP 全序与明细语义、逐包 B/K、set/真实 VERIFY/GET/currentEffect、真实配对审批路径、HTTP/Android 仍未验/未开发；旧 V34/PAGE/STOP 隔离证据只按旧范围引用。原字节可读不等于业务事实可信。人工、长时/真机/现场可后延并标未验，R05 整体、code20/Room17、R09 限制不变。
- 环境：配对 IT 专用 19330 mysqld PID 14492/11668 经进程/目录/端口身份核对后认证优雅关停，19330 无监听；先前 STOP attempt1 PID 17584/13100 亦已关停。共享 3306 仍 PID 5448 未操作；各 attempt 原证据/失败保留。重型槽空闲；七类生产决定 handler/POST、放行关闭，`localDeletionAllowed=false`，不合 `main`、不部署。
- 下一断点：沿原树完善旧直接登记/换 key 的可信历史来源及逐条原始记录和全设备审计连续性，之后在新隔离 profile 完整消费 preflight 并实现受控 set/VERIFY、逐包 B/K、GET/HTTP/Android。第六步自审完整前不进入 R07。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_paired_key_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
