# R06 Android TEMP 成功原回执内部消费者验收

日期：2026-09-25。当前助手独占两棵既有树及专用隔离环境，作者实现及自审，无独立人员复审。前九步持续目标 active；本次只收口首轮 TEMP 成功原回执的严格消费和耐久保存，不表示产品确认、归档闭环、R06 或上线完成。

## 实现及完成标准

- `TemporarySuccessVerifier/Json/Products`：HTTP 原响应严格 UTF-8/JSON 解析，拒绝重复键、尾随内容、宽松语法、非整数；保留原字节，允许服务器正常字段顺序。JWT 限长、规范无填充 Base64URL、严格 header/claims，沿 RS256/issuer/audience/kid/2048-bit RSA 验签；响应与签名逐字段规范比较。
- 绑定原 S、TEMP source、creator/freeze device、workRound1、原 epoch、完整冻结正文 hash；核原目标（已有待审回执时）、COMPLETED/resultVersion1/receiptVersion1、固定扣账政策、有效扫描首尾/数量/重量/高水位。计划中的每个产品必须出现，包括某产品为零；多项、重复、少项、字段类型及能力异常均拒绝。整单无有效包拒绝。
- 原完整正文 hash 同时绑定资格/计划/原来源；本批不是新的当前仓权限判定。签名中没有新增 warehouse/operator 字段，不能把旧签名虚构成这些字段的单独证明。
- Room **21→22** 新增 `temporary_success_response` 与 `temporary_success_evidence`：原响应按 S+SHA256 追加、先事务保存再验签；首份已接受成功原件/公钥/摘要不可由后续响应覆盖。不同 HTTP envelope 可指向相同原签名回执。失败原件及冲突原件均保留；读取已接受证据仍重新核摘要、原正文及签名。
- `TemporarySuccessRepository` 仅内部历史消费者，未接 UI/后台同步。它不更新原提交、draft、取消意图或 guard，不授予放行、续扫或删除能力。原 `TemporarySubmissionReceipt` 产品成功入口继续关闭；晚到取消意图与历史成功证据并存，已存 ABORTED/REJECTED 终态冲突拒绝。
- 自审修复普通提交 GET 的组合 `Cache-Control` 兼容：`PdaNetwork.original` 解析指令并要求确有 `no-store`；无 `no-store` 仍拒绝。额外核已接受公钥元数据，篡改不能被后续历史读取忽略。

## 当次验证

证据根：`outputs/r06-e2a-takeover-20260925-01a0d612/`。

1. 作者编译 `android-success-compile.log` 通过。新增聚焦消费者最终 **14/14** 通过，XML保存在独立 attempt30；涵盖正常及零产品服务端成功、44个逐项重新签名语义负例、错key/外层篡改/重复字段/尾随内容/类型异常、修改原资格来源、原件重开、相同回执不同信封、合法签名冲突、原字节/公钥/摘要篡改及最后写入失败全事务回滚。
2. Android全套当次实际运行：**91 XML、353项，351通过、2条件跳过、0失败/错误**。`android-success-full.log`、`android-success-full-results/`、`android-success-full-results.json`。两个专用HTTP用例在普通全套中条件跳过；本项HTTP用例在attempt30实际通过，旧E2a五阶段HTTP沿Room21验收历史引用，不冒称本批重跑。
3. 同次 `assembleDebug`、`lintDebug` 通过；lint 185项 Warning、0 Error，新 TemporarySuccess 文件无 lint 报项。证据 `android-success-lint.xml`。APK code20/Room22，仅本机调试产物，SHA256 `9ACD0498C6C5907E6AE195A9BF6618FED58B9A4AB8FBAE1368C286BFEB8AF6A5`，详情 `android-success-apk.json`；未安装、未发布。
4. 非空Room21→22保存真实本地冻结正文、guard、原STOP、E2a全部请求/原键和已存RAW回执；不倒填成功。既有非空旧版本升级测试接续到22并全套通过。
5. 独立非空MySQL **attempt30服务端IT 1/1、Android14/14**。专用ACL/datadir/schema/user/19330，非空V34 seed→V36、binlog。准备身份及资格仍用合成MockMvc；TEMP正文及成功GET用真实TCP HTTP，正常/短装决定由显式内部服务和真实MySQL事务执行，生产operation Bean/确认POST没有开放。
6. JVM Android独立读取真实HTTP成功原字节：首份完整响应在应用保存前主动丢弃，重开Room后同S GET；第二份RAW保存后再重开，验签及持久成功后再次重开复核。这里是明确的应用级丢回执和Room重开，不声称真实断网、OS杀进程或断电。
7. 跨语言正例由服务端实际内部决定生成，不由Android自签；负例明确为服务端测试 signer 重新签名的语义错误数据，非业务决定。存储夹具导入服务端合成冻结原正文，并借用本地fixture关系建行；不能把它说成“同一Android原机实际冻结→内部确认”的全链组合，该组合仍未验。
8. 服务端本批只新增IT/向量导出及runner模式，无生产Java变更；已重编译并运行上述IT，未重跑前批491项服务端全套。之前491结果仅历史引用。两树diff --check通过；`android-success-hashes.json`/`server-success-hashes.json`，收口代码指纹变化0。

## 失败原件与环境

- attempt29：服务端IT 1失败，Android11项中10通过/1失败；失败为普通GET组合缓存头被原读取器过严拒绝。修正缓存指令解析并增加正负测试，未改服务端响应或降低签名检查。
- attempt30：最终双端通过。目录 `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-30`；服务端 XML SHA256 `406026C7BFE2B7C988BFBB0B427808B18A8056EED545CEBBAF31F9B923C115D2`。`android-success-attempt-results.json` 汇总29/30；两目录和失败日志均保留。
- 29/30均核身份后认证shutdown；19330/19434/19435无监听，无Java/Gradle/adb/emulator在途。共享3306/PID5448（父4412）未操作。重型槽空闲；下次独立run从31检查分配，不复用。

## Git、边界与下一项

- 主树 `D:/acrylic-pda-outbound`，`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。origin均GitHub 1131657662/acrylic-pda-outbound。
- 全部未提交/未跟踪文件见 `R06_TEMPORARY_SUCCESS_WORKTREE_STATUS_2026-09-25.md`；既有修改/out.xlsx/失败库保留。未提交/推送/合并/标签/部署，最新服务端未整合主树。
- 下一项：TEMP服务器耐久归档证明及Android消费，再补同一Android原机冻结至成功的组合；现有FORMAL归档不可直接解禁，因其assignment/order/source假设不适用于TEMP。之后继续R06候选/关联/拒绝处置/续作/反向提示和R07–R09可独立项。
- 人工、独立人员、历史真实keyring、模拟器/真机Keystore、OS进程死亡/断电/真实网络/长时、生产权限/审批仍未验。E2a未冻结/整单零包/未决输入/其他尚未提交来源恢复仍未完；R05 code20/Room17与R09限制保持。`localDeletionAllowed=false`，七类生产决定handler/POST/放行/关闭能力仍关闭。
