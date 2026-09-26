# R06 TEMP 内部成功与归档链验收

日期：2026-09-25。作者实现、自审及隔离环境写者均为当前助手；没有独立人员复审。前九步持续目标 active。本次覆盖首轮原映射成功、服务端三份耐久原件、Room23归档消费，以及同一Android实际冻结到成功/归档的组合。不是生产确认、R06整体完成或上线。

## 范围与实现

- 服务端新增 `TemporaryArchiveEvidence`、`TemporaryArchiveService`，均无生产Bean/Controller接线。现有FORMAL归档服务保持TEMP拒绝；不修改正式版归档V1，新增内部TEMP归档V2契约见 `docs/parallel/R06_M2B/TEMPORARY_ARCHIVE_V2.md`。
- 归档要求SUCCEEDED、TEMP首轮原映射，原正文hash/control、原人员/仓/目标与唯一真实NORMAL_CONFIRM决定一致。沿 `CommandReceiptIntegrity` 核原请求/签名、历史身份/映射、决定事件、审计、短装引用及结果快照/业务成功回执。INTERNAL_TEST_ONLY、只有批准或伪造数据库状态不能代替真实确认。
- 原冻结正文、完整成功回执规范字节、确认命令完整原响应字节分别按SHA-256保存并重新确认耐久；证明绑定三文件摘要/大小、S/source/资格/原人机仓/目标/计划/轮次/映射/代次、完成时间、至少五年留存，`localDeletionAllowed=false`。已有证明后的任一文件缺失/损坏拒绝，不静默重建或重签；初次事务失败允许保留不可放行的孤立原件。
- 服务端独立RC事务沿既有PDA身份/恢复屏障及C/O/S锁取得原来源，提交后返回。自审补了“不得加入调用者未提交事务”的显式保护；不以已签名但未提交行冒充归档成功。签名失败/插入后抛错回滚证明，原成功和账务不重做。
- Room **22→23** 新增归档原请求、RAW响应、已接受证明三表。联网前固定原请求和由本机原资格/冻结/已验成功得到的上下文；RAW先提交再验签；首份证明不可覆盖，无效/冲突原件保留。历史读取重核原成功及归档签名，当前本机原件变化即拒绝。
- Android严格核UTF-8/JSON、重复字段/尾随内容、规范Base64URL、JWT算法/用途/kid/签名、完整字段集合、原身份/摘要/字节数/代次/映射/留存和能力。归档不更新原提交、draft、取消意图或guard，不提供删除能力。产品UI/worker成功与归档接线仍未开放。
- 服务端schema仍V36；现有 `submission_archive` 可以存V2证明，未新增服务端迁移。Android代码仍code20，仅调试构建，不表示R05原机升级已验。

## 当次验证

证据根 `outputs/r06-e2a-takeover-20260925-01a0d612/`：

| 层次 | 实际结果与证据 |
| --- | --- |
| 作者服务端全套 | 本次重新运行，77 XML/491项：490通过、1 Windows符号链接条件跳过、0失败/错误。`server-archive-full.log`、`server-archive-full-results/`、对应JSON；不是引用旧491结果 |
| 作者Android全套 | 94 XML/359项：356通过、3专用HTTP条件跳过、0失败/错误。`android-archive-full.log`、`android-archive-full-results/`、对应JSON |
| 非空迁移 | Room22→23保留已接受成功及原RAW/正文/guard，不倒填归档；全部旧版本非空迁移接续23并通过 |
| APK/lint | 同次assembleDebug、lintDebug通过；lint185 Warning/0 Error，新归档类无报项。`android-archive-lint.xml`、`android-archive-apk.json` |
| 独立MySQL/跨端 | attempt33服务端IT1/1，Android6/6；attempt31初始和32扩展也通过，33包含最终外层事务保护。XML与失败/通过原件均保留于各attempt |
| 自审/静态 | 两树diff --check通过，收口源文件hash变化0；`android-archive-hashes.json`、`server-archive-hashes.json`。无独立人员复审 |

最终调试APK SHA256：`658912667728C7AD64C08F581B70A909D5BD83D673A954272E9089ECF40B6D1F`。未安装、未发布。

attempt33目录：`D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-33`；服务端XML SHA256 `97FBBFE8CB160C7BC612B993D023EA18E9A9CCC4725B7A5911FFC4A7AC1BE43B`。31–33索引见 `android-archive-attempt-results.json`。

## 组合证据与边界

1. 独立ACL/datadir/schema/user/19330，非空V34 seed→V36且binlog开启；身份/准备及资格使用合成MockMvc夹具，密钥合成。没有操作共享3306、原库或现网。
2. Android使用真实Room及生产扫码/冻结/上传代码生成同一原S/正文/计划/原STOP签名；两个产品各扫一包，经真实TCP提交进入待审。原设备签名器只在隔离测试中用原登记软件密钥代替Keystore，签名次数1。
3. 确认与归档经测试专用127.0.0.1:19435适配器调用真实内部服务/MySQL事务；适配器只存在test源码和E2A隔离runner。正常提交、成功GET及迟到ABORT使用实际19434应用HTTP端点。不是生产确认POST开放。
4. 成功GET首次完整响应在保存前主动丢弃；同S重读后RAW保存、重开Room再验签。实际成功后的原取消POST返回同一成功签名回执，已接受原成功指针不改。原本机取消意图仍留存，没有把取消等同释放。
5. 归档首次POST提交后主动丢回执；Room重开后原请求字节不变，重放取得同一证明，RAW保存后再重开。注入本地最后VERIFIED写入失败，验证证明行回滚而RAW保留，再原请求/原响应复验成功。
6. 服务端逐项缺失及篡改正文/成功回执/确认命令归档文件、篡改证明字段和原命令事件、错摘要、非成功状态、其他人机仓、恢复屏障/代次变化均拒绝；签名失败/插入后失败无证明行。恢复原件后原证明字节保持，原S/order/slot/决定/库存/结果/dispatch/ledger/outbox状态未重做。
7. Android消费18个服务端测试signer重新签名的错误归档证明（不是业务决定），覆盖仓/原人/设备/资格/目标/轮次/映射/代次/摘要/大小/留存/删除能力等；错key、未签名修改、重复键、尾随JSON拒绝，坏原件保留且不能替换已接受证明。
8. 实际应用 `POST .../archive-confirmation` 对TEMP仍返回 `TEMPORARY_CONFIRMATION_REQUIRED`，有真实HTTP断言。只读的当前产品状态依然保守；原draft FROZEN、全部原guard及原冻结正文保持。

这批是应用级丢回执、Room关闭重开与真实本地TCP/MySQL组合，不是OS杀进程、断电、真实外网故障、模拟器/真机或五年等待。Windows文件force已走现有实现；设备和文件系统断电耐久仍未验。三个普通全套条件跳过的HTTP测试分别为旧E2a、成功消费者、归档组合；本项归档在attempt33实际通过，前两个沿对应历史验收引用。

## Git、环境与下一断点

- 主树 `D:/acrylic-pda-outbound`：`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`：`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。origin均GitHub 1131657662/acrylic-pda-outbound。
- 完整未提交/未跟踪快照 `R06_TEMPORARY_ARCHIVE_WORKTREE_STATUS_2026-09-25.md`。既有他项文件/out.xlsx/失败库保留；无提交/推送/合并/标签/部署，服务端最新改动未整合主树。旧任务未恢复，无子代理、新任务或工作树。
- 31–33均认证shutdown；19330/19434/19435无监听，无Java/Gradle/adb/emulator在途。共享3306/PID5448（父4412）未动；重型槽空闲，下次run从34检查分配。旧29缓存头、25权限/27固定48h约束及更早失败证据均保留。
- 下一项：正式建单、READY修订、发布和PC嵌套发布的成功TEMP反向候选提示。提示供人工核对，不自动阻断/合并，不回写成功TEMP关联；有实际作业的重复保留两侧证据进入R07。非零候选排除/拒绝权限尚未定，不能擅自授予；其他独立工作继续。
- 仍未完：TEMP产品成功/归档接线、非零候选/关联/mapping2+、更高轮次、拒绝/实物处置/续作、外部独立命令消费者真实businessDecision、R09对新增V2三文件/决定引用的完整恢复检查、R07–R09其他独立项。E2a未冻结/整单零包/未决输入及其他未提交来源恢复亦未完。
- 人工/现场/真实keyring/独立人员/真机Keystore/OS死亡与断电/真实网络/长时/生产权限与审批逐项未验，R05 code20/Room17及R09历史限制不变。`localDeletionAllowed=false`，七类生产handler/POST/放行/关闭能力仍关闭。
