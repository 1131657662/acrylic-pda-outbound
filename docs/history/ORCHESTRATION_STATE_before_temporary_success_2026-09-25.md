<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-25 首轮正常确认/独立短装内部子项通过；前九步目标active

- 目标/写权：当前会话 `01a0d612-1e51-78c2-9eca-f15671339987` 助手独占两树及专用隔离环境继续R01–R09可独立事项。旧同目标任务notLoaded，无旧写者；没有恢复总调度/代理或创建新任务/树。
- Git主树：`D:/acrylic-pda-outbound`，`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树：`D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。origin均GitHub 1131657662/acrylic-pda-outbound。无提交/推送/合并/标签/部署，最新服务端尚未整合主树。
- 未提交：完整路径见 `docs/history/R06_TEMPORARY_CONFIRMATION_WORKTREE_STATUS_2026-09-25.md`；既有业务改动/out.xlsx/崩溃日志/失败目录均保留。本批主树仅管理记录，服务端代码/测试/契约/脚本指纹见证据目录 `server-confirm-hashes.json`；长期AGENTS规则不变。
- 继承已验：E2a全设备高水位与当前TEMP/其他已收来源事实分离；RAW原字节/原键；Room20/21原请求与原STOP签名耐久、五阶段丢回执重开、VERIFY前审计截点；正文前/部分分块ABORT证据；共同TEMP固定原资格政策记账。这些历史证据按各验收范围引用，不冒充本轮Android重跑；E2a仍PARTIAL。
- 本轮实际实现：已有正文、首轮原映射PDA_TEMP待审/FROZEN目标、原控制/资格/计划/输入事件/库存实绩重核。正常confirm允许空理由；短装独立review批准不记账，原操作者自批拒绝。原48小时内保存不因今日单纯过期作废，其他身份/权限/准入/代次变化失败封闭。
- 候选/决定：锁内服务器全查询既有默认范围（受理日上海时区±7日、同车或产品重叠正式单），规范JSON摘要与原正文/仓/S绑定；不把该技术范围当全部重复证明。非零候选拒绝，未实现排除。短装批准绑定原目标/轮次/正文/计划/候选及最近决定版本，当前批准者撤权后不用于新确认。
- 原件/事务：真实内部决定RECORDED与旧INTERNAL_TEST_ONLY并存；原键回放优先于新写检查。请求/回执/决定/审计与成功结果/库存/槽/同步同事务。历史验证核原正文/产品/候选上下文/身份/映射/签名/成功结果与短装引用；新currentEffect只列当前变化，历史成功COMMITTED不可撤销，全部能力仍false。
- 作者验证：聚焦及全套实际通过；77 XML/491项，490通过、1 Windows符号链接条件跳过、0失败/错误。`outputs/r06-e2a-takeover-20260925-01a0d612/server-confirm-full.log`与`server-confirm-full-results`；两树diff --check通过，收口代码hash变化0。作者自审，无独立人员复审。
- 独立环境/组合：attempt28 1/1通过，独立ACL/datadir/schema/user/19330、非空V34 seed→V36、binlog；合成身份和密钥，准备MockMvc，TEMP正文/成功提交GET/命令详情GET真实TCP HTTP，真实决定内部服务+MySQL事务。无生产确认POST，不冒称Android或独立消费者验证。
- 原子性/并发：成功签名/命令签名/末尾命令审计FK失败完整原状态回滚；5组真实连接79/80 RC竞争（同键、异文、不同键同版本、正式create+publish、UserService撤权）保存实际G锁等待及提交/回滚，不宣称单独W锁动态隔离。hash/kid/事件/审计/结果篡改拒绝，旧原件保持。
- 证据：`R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md`；attempt28 XML SHA256 `E335E29C045B0F814A373A13684AF443F6D758C8F2707312793199170136B568`；`server-confirm-attempt-results.json`及attempt28下`confirmation-race-*.json`。替换前当前区保存在`ORCHESTRATION_STATE_before_temporary_confirmation_2026-09-25.md`。
- 失败保留：attempt25夹具缺独立confirm权限；26初始通过；27晚确认夹具单改到期违反固定48小时CHECK，改注入资格评估时钟而不改原资格/签名/约束；28扩展最终通过。真实48小时等待未验。此前accounting19–22及ABORT/Room21失败原件继续保留。
- 环境/重型槽：attempt25–28均核身份后认证shutdown；19330/19434/19435无监听，无Java/Gradle/adb/emulator在途，重型槽空闲。共享3306/PID5448（父4412）未操作，原库/现网未触。
- 边界/未验：本批只收口内部首轮无未决候选确认与独立短装子项，不称M2b/R06整体完成。候选排除/权限、非零候选、LINK/mapping2+、TEMP更高轮次、拒绝/实物处置/续作、正式单侧成功TEMP反向提示、Android成功验签/归档、产品确认入口未完。外部独立原命令消费者尚不接受真实businessDecision。
- 长期未验：E2a未冻结/零有效包/未决输入及其他尚未提交来源完整恢复；真实历史keyring、独立人员、模拟器/真机Keystore、OS进程死亡/断电/真实网络/长时、人工/现场/生产权限/审批、R05 code20/Room17与R09限制保持。localDeletionAllowed=false，七类生产handler/POST/放行/关闭能力不开放。
- 下一断点：沿Android原冻结正文/原签名/耐久结构接内部TEMP成功消费者与实际跨端样例，再完成R06其余确认/处置链及R07–R09可独立事项；未定排除/拒绝权限不授予。不重做R01–R05有效项，不合main、不部署。
<!-- CURRENT_STATE_END -->
