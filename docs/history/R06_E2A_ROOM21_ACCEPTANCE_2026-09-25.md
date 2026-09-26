# R06/E2a Room21原回执与真实HTTP组合验收

2026-09-25，当前会话 `01a0d612-1e51-78c2-9eca-f15671339987` 的当前助手实施、作者测试和自审。没有独立审查者；“独立环境”仅指专用非空MySQL，并不表示独立人员验收。持续目标保持 active，R06及前九步未完成。

## 基线与完成标准

- 主树 `D:/acrylic-pda-outbound`，`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。
- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。
- 本子项没有提交、推送、合并、标签、安装或部署。两树业务仍含未提交改动；完整路径见 [工作树快照](R06_E2A_ROOM21_WORKTREE_STATUS_2026-09-25.md)，业务文件SHA见本机 evidence 目录的 `main-room21-hashes.json` / `server-room21-hashes.json`。管理文档最终更新晚于该哈希快照。
- 完成标准：Room20非空升级保留请求且不补造回执；原字节先存后验签，逐项绑定原请求/来源/集合；发送前持久未知状态；五阶段丢回执后重开Room保留原键/正文/设备签名；真实HTTP服务与独立非空MySQL接受实际Android冻结材料并保留原回执字节；不开放任何放行/本地删除能力。

## 实现与自审修复

- Room21新增 `local_e2a_receipt` / `local_e2a_queue`；20→21迁移不造原键、不为历史数据补回执、不自动排队。
- `LocalE2aRecoveryRepository` / `E2aReceiptStore` 在发送前写 `RESULT_UNKNOWN`，收到后先存 `RECEIVED` 原字节，再以可信公钥验原签名及来源，只有通过后才写 `VERIFIED`。错误原件保留且停止；重启对已有已验回执再核验，不能靠状态字符串跳过。
- OPEN/PAGE/STOP/VERIFY先按原键GET原回执，确认为不存在后才POST原正文；RAW重放原对象ID和原始字节。只有RAW重放一次，业务回执丢失可直接GET恢复。Wi-Fi队列、原单入口和启动后队列恢复已接。
- `PdaNetwork.original` 保留UTF-8原字节，限制响应大小/type/no-store，禁止底层自动重试POST；原会话/恢复代次与权限版本每次重查。
- 自审发现审计后台同步与证据VERIFY竞态：原实现可能在STOP审计尚未上传时直接VERIFY，后续不可自动恢复。新增 `auditReady`：VERIFY前核对同设备同恢复代次确认截点；未达STOP时调用已有 `AuditRepository.syncAudit`，不持Room事务请求网络；仍未齐时 `WAITING_AUDIT` 留队；缺审计权限则 `ACTION_REQUIRED`，不借恢复权限绕过。
- 真实组合新增 `E2aAndroidPreparation`、`E2aAndroidHttpIT`、`E2aAndroidProcess`、Android `E2aRealHttpConnection` / `E2aRealHttpRecoveryTest`。准备阶段使用实际服务的MockMvc签名材料；冻结、提交、审计、OPEN/RAW/PAGE/STOP/VERIFY由Android生产Room/Retrofit/OkHttp路径访问真实本机HTTP。用户/设备为合成，Keystore边界为原登记测试EC密钥，不称真实设备验证。
- 独立测试脚本增加Android模式及强制执行/本轮标记校验，避免Gradle缓存结果误算本轮验收。凭据/测试私钥仅保存在专用ACL保护的ignored attempt/private目录，未进入源码或公开证据。

## 当次验证

证据根目录：主树 `outputs/r06-e2a-takeover-20260925-01a0d612/`。

| 验证 | 实际结果 | 证据/范围 |
| --- | --- | --- |
| 继承Room21作者全套 | 85 XML / 335项，0失败/错误/跳过 | `android-room21-full-results`，本轮已核对；不是最终审计顺序修复后的结果 |
| 真实服务器原回执离线Android验签 | 2/2通过 | `android-e2a-http-contract-results`，cross attempt6导出；不当作Android实际HTTP |
| 新审计顺序负例 | 修复前2/2失败 | `android-audit-order-red-results`，证明VERIFY抢先及缺权限未停 |
| 审计顺序修复聚焦 | 3 XML / 11项，0失败/错误 | `android-audit-order-focused-results`，含迁移/网络/原请求续传 |
| 最终完整Android回归 | 86 XML / 338项，337通过、1条件跳过、0失败/错误 | `android-room21-final-results`；跳过项是需要专用MySQL的真实HTTP测试，已在attempt10单独实际执行通过 |
| 最终APK/静态检查 | assembleDebug、lintDebug通过；lint 0 errors / 185 warnings | `android-room21-final.log` / `android-room21-final-lint.txt`；警告未全部消除，不称零警告 |
| 最终真实HTTP组合 | 服务端IT 1/1 + Android测试 1/1通过，均0跳过 | 服务端 `.local/e2a-cross-20260925-10/`；独立V34非空seed→V36，binlog保持开启 |

attempt10移除了测试预先审计上传，实际由恢复流程在VERIFY前补传。五个阶段在服务器完成真实请求后，连接测试边界抛IOException模拟“客户端未收到回执”；每次关闭并重开真实Room数据库，原键/请求集合不变，设备签名只调用一次；数据库原响应BLOB与Android已存回执字节逐一相等。服务器提交仍 `REVIEW_REQUIRED`，verification仍 `PARTIAL`。这不是操作系统杀进程、Wi-Fi切换、模拟器或硬断电实验。

- attempt10服务端XML SHA256：`357E601BBFA01BD45D7C1FB26571C8A617308B5738DCBF6AC97FFA633CA9FFA9`。
- attempt10 Android XML SHA256：`8E5A119022D66C1FCF2D8D710FC7C60639A6A0006DF75E81CA2710537F73132F`。
- 最终debug APK：`outputs/r06-e2a-takeover-20260925-01a0d612/android-room21-final-debug.apk`，SHA256 `E9B30D90A3BFFDA1999098E13C4E8CC0C9501CFB5698AB62A38F59682871F90C`，apksigner verify通过。package `com.acrylic.outbound.pda.debug` / versionCode20 / versionName `0.15.0-r06-submission` / Room21，仅本地开发产物；未安装、未发版，不能与旧code20/Room17验收APK混同。
- 本轮只修改服务端验证夹具与runner，没有新增服务端业务修复，因此既有490项是此前RAW服务端子项结果，不包装为本轮重跑全套。

## 失败及限制保留

- Room21首次离线契约测试缺Robolectric runner，`JSONObject` mocked方法失败；原XML `android-e2a-http-contract-failed-results`保留，随后2/2通过。
- cross attempt7新MockMvc准备夹具漏 `RequestIdFilter`，审计 `request_id` 非空约束导致500；补齐过滤器后attempt8原链组合通过。两库/日志都保留，均认证停库。
- cross attempt9实际 `integrationTest UP-TO-DATE`，无本轮started/completed marker，复制XML时间来自attempt8；本目录虽旧runner退出0，明确判定为**未执行/无有效本轮证据**。强制执行与marker保护后attempt10通过。不覆盖attempt9旧输出。
- 最初编译命令指向服务端不存在的outputs目录，日志重定向失败，编译未启动；改到已有主树evidence目录后两端编译通过。
- 未做独立人员审查、模拟器、真机Keystore、真实Wi-Fi/进程死亡/断电、长时间测试、历史真实keyring部署、生产库迁移、现网验证。现有真实HTTP是本机隔离环境及合成身份。
- C-ABORT无正文及其他尚未闭合来源分支不得据本项标为完成；本文只验有实际Android冻结并提交的来源链。确认/短装/排除/关联/拒绝处置/续作/最终成功与R07–R09继续核对开发。
- `localDeletionAllowed=false`、所有接收回执能力false、七类生产决定handler/POST/放行保持关闭。未合main、未部署，前九步目标不标完成。

## 环境与下一断点

attempt10结束前认证核对 `@@datadir`、19330、schema/user、binlog及trigger设置后mysqladmin shutdown；19330/19434/19435均无监听，Java/Gradle无在途。共享3306/PID5448未操作。重型槽空闲，两树仍由当前助手唯一写入，原崩溃日志、out.xlsx及其他未提交改动保留。

下一从已有 `R06_M2B_IMPLEMENTATION_PLAN.md`、`R06_C1_LEDGER/HANDOFF.md`、`R06_LEDGER_RUNTIME/IMPLEMENTATION.md` 与最新源码核对确认/处置/成功链的内部可实现子项。未定拒绝/排除写权限等业务政策不自行授予；内部可验证实现与生产入口开放分开，不能因E2a局部通过扩大权限。R07–R09按实际依赖继续，不重做R01–R05已有效完成项。
