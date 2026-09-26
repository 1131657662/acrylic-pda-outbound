# R06/E2a 正文前及部分上传后取消的独立证据验收

2026-09-25，当前会话 `01a0d612-1e51-78c2-9eca-f15671339987` 当前助手实施、测试和自审。独立环境表示专用非空 MySQL，不表示独立人员审查。持续目标仍 active；R06 和前九步未完成。

## 基线与完成标准

- 主树 `D:/acrylic-pda-outbound`：`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。
- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`：`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。
- 没有提交、推送、合并、标签、APK构建/安装或部署。最新服务端实现尚在服务端树，未整合到主树。完整未提交/未跟踪清单见 `R06_E2A_ABORT_WORKTREE_STATUS_2026-09-25.md`；本次业务文件哈希见本机 `outputs/r06-e2a-takeover-20260925-01a0d612/main-abort-hashes.json`、`server-abort-hashes.json`。
- 验收标准：已有真实本地冻结、原机原人同仓的 ABORTED 批次在没有 `submission_payload` 时，可核验同来源独立冻结证据并保守占用本来源包；保留取消回执、received/declared 区别、空 canonical 和部分块，不创建业务正文、submitted_capture、正式单或成功事实；缺件/跨来源/伪造失败封闭；原键/回执/STOP重启恢复及既有已收正文/跨来源路径回归。

## 实际实现

- `SourceEvidenceIntakeBusinessFacts` 明确分支：有业务正文时保持原 `submitted_capture` / 原正文核对；确无正文时进入 `SourceEvidenceIntakeLocalPayload`。本 TEMP 与全设备其他来源仍分开，只本 TEMP 的 ACTIVE SCAN进入B/K校验和占用。
- 独立冻结读取器要求原批次 ABORTED、原 canonical 为空、当前原S/round、同原人员/设备/仓库，并验原取消签名的 `payloadPresent=false`、hash=null事实。恰好一个 manifest引用的 `STOP/LOCAL_FROZEN_PAYLOAD/FROZEN` 对象绑定原S和STOP审计截点；核对象原字节/摘要、control/source、已有分块声明摘要。
- 复用既有 `TemporarySubmissionPlan`、`TemporarySubmissionEvidence`、`TemporarySubmissionSnapshot` 校验原资格、计划、明细、原库存/可用基线及全量冻结事实。逐条匹配服务器已收原审计，正文事件必须早于STOP；原捕获ID或设备序号不得已被submitted_capture占用。没有调用 accept/finalize/formalization，不写业务正文。
- 自审发现 STOP 审计此前只核原人/设备/资格，未核具体批次和临时单；新增 `detail == submissionId` 和原source.clientOrderId匹配，测试覆盖另一批次/另一临时单拒绝。
- 不改数据库迁移编号、API字段、Android生产代码、Room21或code20。本次Android只扩展真实HTTP测试夹具，覆盖多页/大量无效输入。所有回执能力false，verification始终PARTIAL。

## 当次验证与失败原件

服务端各 attempt 均位于 `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-N`，专用ACL、独立账号/密码/datadir；认证校验19330、schema/user/datadir，binlog保持开启，V34非空seed升级V36，HTTP19434。每次强制执行且检查本轮completed marker，结束均认证shutdown，原目录保留。

| 验证 | 实际结果 | 说明 |
| --- | --- | --- |
| attempt11 正文前ABORT红测 | 服务端/Android均1失败 | Android取消后到VERIFY因业务正文不存在失败，复现原缺口 |
| attempt12 初版修复 | 服务端/Android各1/1通过 | 独立冻结核验、原ABORT保留、SOURCE_RESERVED |
| 聚焦编译第二轮 | compileTestJava失败 | 测试批量调整误将event变量写到temporary场景；原日志 `server-abort-focused2.log`保留，修后聚焦通过 |
| attempt13 最终来源绑定及负例 | 服务端/Android各1/1通过 | 正文前ABORT，缺/重复对象、跨S/仓、错STOP截点、伪造人/重量/审计、捕获已归属、包cycle/状态变化拒绝；负例为真实MySQL事务内调用/回滚，不冒称每例真实HTTP |
| 服务端完整作者回归 | 76 XML / 490项，489通过、1跳过，0失败/错误 | `server-abort-full.log`、`server-abort-full-results`；跳过Windows符号链接项，生产代码此后未变化 |
| attempt14 部分分块后ABORT | 服务端/Android失败 | 测试仍固定30轮，较大集合未走到STOP，丢响应次数实际3/预期5；不是生产队列故障 |
| attempt15 多页续传 | Android1/1通过，服务端IT失败 | 服务端验收夹具仍假定四份回执，实际12份；未作为完整通过证据 |
| attempt16 部分分块后ABORT最终验收 | 服务端/Android各1/1通过 | 256条INVALID输入保留，共257 CAPTURE、1 SCAN、776 AUDIT、1独立冻结对象，1035 RAW、9 PAGE、12份原回执逐原键字节一致；分块声明仍在、仅1块262144字节，业务正文仍空、原取消回执不变 |
| attempt17 已有跨来源真实HTTP回归 | 1/1通过 | 原事实缺失/伪造及RAW门禁负例、他源不占K，OPEN/RAW/PAGE/STOP/VERIFY及原键重放 |
| attempt18 已收正文Android真实HTTP回归 | 服务端/Android各1/1通过 | 正常REVIEW_REQUIRED来源、严格STOP绑定、五阶段丢响应与Room重开、逐原键回执相等 |
| 两树diff --check | 通过 | `main-abort-diff-check.log`、`server-abort-diff-check.log`；仅行尾转换提示 |

原请求在发送前已由Room耐久；五阶段丢响应通过服务器完成请求后客户端测试边界抛IOException模拟，每次重开Room、原STOP签名仅一次。不是OS杀进程、真机Keystore、真实断网/断电。准备使用MockMvc与合成用户/测试EC密钥，冻结/取消/分块/审计/证据恢复使用Android生产路径和真实HTTP。

attempt16服务器XML SHA256：`11591127F3B196868EB910F483F36EA62E9DA8194525B5EFF507CFCA3A67C3F9`；Android XML：`0664B123679CC327A5A4CFEA56B397B78A692691D3FDA9A72E2A2838C6B73F9F`。

attempt17服务器XML：`2D970F2C884D7DB82FCFBBC52098FAB3CBFE74BFCD0559849E520EAE1DA38B5E`。attempt18服务器XML：`7CDF164CEEA7A18859EA2BCD12AE20046A1A5F28937C95C0AC569191E55B9C96`；Android XML：`19C1ECD326AEB764AD9F5211EBB2BA9005D8665145AD521C6C16A2E7B8C24271`。全部attempt结果索引：本机 `server-abort-attempt-results.json`。

## 限制和下一断点

- 这只关闭“已有完整本地冻结、服务器未收正文”的E2a恢复子项。未冻结/零有效包/仍有未决输入的停止材料、其他未提交来源混入全设备前缀、真实历史keyring部署等仍未验/未实现，不由本项泛化为E2a整体完成。
- 普通正常确认可空理由、localDeletionAllowed=false、七类生产决定handler/POST/放行/关闭能力不变。物理处置、释放、续作、成功最终记账没有由本项开放。
- Android全套338项、APK/lint属于前一Room21子项继承证据，本轮未重跑或重建APK。新增真实HTTP测试各轮实际执行。无独立人员审查、模拟器、真机、现场、长时或生产验证。
- 旧attempt1–10及早期审计竞态红测等均按Room21及此前验收记录保留，不无痕替换。
- 当前无Java/Gradle/adb/emulator在途，19330/19434/19435无监听；共享3306/PID5448未操作，重型槽空闲。
- 下一沿R06已有内部命令台账推进确认、拒绝处置和成功链；先明确完整证据与候选/映射失效依赖，未定排除/拒绝权限不自行赋予。R07–R09仍按依赖接续；不重做R01–R05，不新建代理/任务/工作树。当前助手仍为两树唯一写者。
