# R06 未冻结草稿本机停止存储验收

2026-09-25；本机存储基础子项完成，持续目标 active。当前助手实现、自审及运行隔离验证，无独立人员复审。未冻结来源的产品入口、原机签名、服务器接收仍未完成，不称完整E2a完成或已上线。

## 标准与实际实现

对原身份下尚无业务提交的 DRAFT/SCANNING，允许零包、未完整计划、已到期原资格、本单未决或未确认失败，保留已耐久原件；同一 Room事务写 LOCAL_EVIDENCE_STOP、全设备捕获/审计边界、元数据与原始快照、根摘要和 EVIDENCE_STOPPED屏障。全部失败须回滚，重启按原身份/原件重验，不补造停止，不放行、不释放占用、不改变输入状态。

新增8个 LocalDraftStop*生产文件，职责为 Room24表与分片读取、原来源元数据、原始快照、摘要、当前来源比对、完整性校验和事务入口。快照覆盖全设备 FORMAL/TEMP记录以及已有归档签名/公钥，保留 INVALID/VOID/BUSINESS_REJECTED/DISCARDED/未决；当前来源变化拒绝，其他来源在截点后合法变化不改写快照。

TemporaryWorkStore与TemporaryScanDao同时检查停止表和原停止审计，不能靠改回SCANNING绕开停止。Room23→24仅新增两表，旧表不重写；11份既有迁移测试期望最终版本更新为24。Android code20不变。协议说明见 ../parallel/R06_M2B/LOCAL_DRAFT_STOP.md。

本地 evidenceId 不是 submissionId，没有伪造业务冻结正文、STOP_PROOF、服务器原回执或证据集。根摘要仅是本机完整性检验，尚不具备此新场景的设备签名真实性。没有产品按钮或后台调用；后续产品接线必须在同一应用写者队列先耐久保存已接收的内存输入，再停止。

## 当次验证与自审

- 最终定向23/23通过：空草稿、零包、过期资格、RECEIVED/WAITING_CONFIRM/CONFIRM_REQUESTED原样保留、未确认INVALID/BUSINESS_REJECTED、VOID/DISCARDED、守卫不释放、禁止重启/结单、原前缀缺段、坏原件/缺页/改输入、丢屏障、改标签、跨设备、其他来源后续变化、归档原件/坏签名回滚，以及非空Room23→24不补造停止。
- 自审核对本机事务边界、原身份/原资格核验、全设备序列、本来源原件比较、归档原件、资源限额、异常回滚及新状态写屏障。各生产文件低于200行，复用既有原记录序列化；没有增加通用模式开关或改变业务结单规则。
- Android全套390项：387通过、3专用HTTP条件skip，99份XML；APK构建通过，lint0错误/0fatal/186既有警告。
- attempt54新专用非空MySQL：V34 seed→V36、独立datadir/schema/user/ACL、binlog、19330，真实回环HTTP服务端IT1/1、Android归档选择6/6无skip。实际Room24冻结→真实上传→内部确认→丢成功回执/重开→产品RAW/验签/SYNCED→内部归档与重放；生产TEMP归档仍拒绝。这是既有冻结链兼容性回归，不是新未冻结草稿的HTTP验收。
- 本项无服务端源码修改，未重跑完整服务端/Web/Chrome。R08的493通过/1skip及报表123项为上项历史，1049文件manifest仍一致。

## 失败及限制作原记录保留

focused1 9/9通过；focused2 18项16通过/2失败：测试删除原输入被真实FK阻止、FORMAL与TEMP夹具原设备不同却错断言同设备2条；修为验证FK保护和排除另一设备。focused3 19项18通过/1失败：新来源夹具切换后错误读取旧内存catalog；修为原来源有包、新来源零包，核全设备前缀及旧来源合法后续撤销。focused4为最终23项通过，旧失败XML/log保留。

新功能没有HTTP入口、原机签名或生产消费；新状态产品展示/操作入口尚未接线。真实PDA/Keystore、OS死亡/断电、长时、现场和生产审批未验。全套3项条件skip不写成全部HTTP通过，attempt54只覆盖其中归档产品链；没有重复跑其余两条专用HTTP。独立读取路径/隔离数据库均由当前助手执行，不是假称独立人员。

## 证据与版本

本机证据根 outputs/r06-e2a-takeover-20260925-01a0d612/：

- android-draft-stop-focused1/2/3/4.log、focused2/3-failures.xml、focused-xml/；full.log、full-xml/、lint.xml。
- android-draft-stop.apk SHA256 82564dde0cb1bca0c680dea3871a16d6d073919e88bb8cb32797d075d2251eec。
- android-draft-stop-source-manifest.json：全套时456个源码/资源/合成输入文件，SHA256 a4aea82a2c8ec4cbce059bd9438a6022c24d7afa5339aafee6ce988dd6d2fb7e。
- attempt54重新生成了 server/build/pda-temporary-archive-fixture.json，其他455文件仍与全套manifest一致；android-draft-stop-http-manifest.json保存HTTP后456文件，不用新fixture倒称之前全套重跑。所有业务源码不变。
- draft-stop-attempt54-*：服务端XML SHA256 1cbf36e31fbf755cd38ab81f237aa944bda2781a78d85b1b1aadb181d7b521b2，Android三份XML共6项；完成标记、结果及非私密日志保存。private中的合成密钥/会话文件不复制或提交。

主树 D:/acrylic-pda-outbound，codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c；服务端树 D:/acrylic-pda-worktrees/r06-e2a-migration，codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95。两树origin均https://github.com/1131657662/acrylic-pda-outbound.git。全部未提交与未跟踪见 R06_LOCAL_DRAFT_STOP_WORKTREE_STATUS_2026-09-25.md。无提交/推送/整合/main/部署。

54已认证shutdown；测试后无Java/Gradle/Python/adb/emulator及19330/19434/19435/19436监听，共享3306/PID5448父4412未动。下次run从55核目录分配。旧当前区已存 ORCHESTRATION_STATE_before_local_draft_stop_2026-09-25.md。

下一断点：未冻结/零包/未决输入的原机证明与服务器独立接收，沿新本机原件设计真实来源绑定，不能事后补造原停止事务签名，也不能把无正文ABORT的S当已冻结正文。随后完成同写者产品队列、原键/请求耐久和丢回执重启组合。其他R06非零候选/映射/高轮次、R07及R09独立事项继续；七类生产决定、TEMP生产归档、放行、清理边界保持，localDeletionAllowed=false。
