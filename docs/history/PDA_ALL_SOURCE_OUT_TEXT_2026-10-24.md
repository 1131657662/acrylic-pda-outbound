# TEMP / 独立 SKU 简单 out.txt：有界组合验证

本报告只记录简单文本来源扩展。总目标保持 active、未完成；不代表 O02/U03、D/O/U 整体或第8节最终候选通过。作者、修复者和自审均为本助手；工具消费者不是独立人员审查。

## 输入与保全

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`；输入 HEAD `8ea4edb10cad431156223e3f453cb56cedc3a61b`，接管时实际远端核同，main 未动。
- 证据根 `.local/rescue-all-source-text-20261024/run01/`。`baseline03`、`before-proof-tightening`、`before-android-fix02`、`review01` 保存 dirty 原字节、SHA、patch、环境和后续编辑前原件；22 条非看板继承逐 SHA 保持且不暂存。看板是共享混合 WIP，不整文件提交。
- 服务端 `compile02/source-inputs.json` 记录 2774 输入、110 个依赖 JAR；Android `android-source-manifest02.json` 记录 6083 文件，`android-overlay02.json` 明列 15 项覆盖，其中 3 项为已在 HEAD 的测试兼容修正。冻结输入排除无关继承 WIP；`android-semantic-differences04.json` 与收尾逐 SHA 核查解释基线差异。
- 版本事实只见看板生成块；实际 APK 构建覆盖参数在 `android02/command.json`，不与源码默认值混称。

## 行为与兼容

- 专用耐久姓名显示证明：`aud=acrylic-pda-operator-name-v1`、`purpose=READONLY_OPERATOR_NAME`，绑定 TEMP/INDEPENDENT、原 authorization/credential ID、人机仓、原作业 token UTF-8 SHA 和原发行时刻。姓名仅取发行事务内的 `app_user.display_name`；无 scope/exp，不授作业、提交、确认、续期或删除能力。
- 两类 receipt 的可选 `operatorNameProof` 与 nullable 列随原事务保存；原键、GET 和应用重启返回原证明，改名不重签。非空迁移保留旧列，旧行保持 null，不按新姓名回填。
- Android TEMP 沿原 request/receipt，独立任务沿 vault 原 receipt → 原 request 读取，无 Room 迁移。严格核字段、用途、签名、可信原公钥、token 摘要和身份/时间绑定；缺证明仍可按原规则高级保全，但简单文本拒绝；非空坏证明拒绝。
- TEMP 同机 R06 原前缀按各段授权映射，拒绝重复授权的冲突证明；采集核原操作员/来源设备，不能借用另一身份姓名。没有恢复已取消换机链。
- 保持五列无表头、GBK/TAB/CRLF、原重量/北京时间、原扫码顺序、ACTIVE/处置排除、原只读 grant、写后回读及取消/失败规则。独立任务仍待对账，不变成正式出库。

## 实际执行

| 证据 | 结果与范围 |
| --- | --- |
| `compile01` / `compile02` | 服务端编译成功，最终输入/依赖无漂移；不是完整普通测试或 IT |
| `server01` | 3 个普通测试通过；1 个专项因夹具 LocalDateTime 序列化失败，未进入业务发行；失败保留 |
| `server02` | 4 started / 4 succeeded / 0 failed / 0 skipped：3 个普通测试 + 1 个真实 HTTP/MySQL 专项；不是4个 IT |
| `android01` | 31 项、5 失败：4 项新增 Room 测试主线程访问、1 项注销夹具缺响应；修测试调度/离线前置，未放宽产品 Room 限制 |
| `--freeze/` | 旧模块无 main guard，被错误导入后额外复跑旧输入，31 项/5 失败；属运行器错误，不属修正候选。原目录保留，不再用 `android_next.py` 或导入 `android.py` |
| `android02` | 同次 7 类/32 项，0 失败/错误/跳过；TEMP/独立真实 HTTP 原响应 → 真实 Room → 简单文本/写后回读，含到期/撤销/重开、缺旧证明、伪造与身份损坏；FORMAL 文本/UI、资格存储回归 |
| `android-related01` | 10 类/54 项、2 失败；两个向量落盘环境变量未设置，栈在 fixture 的 `checkNotNull(System.getenv(...))`；原日志/XML保留 |
| `android-related02` | 同一候选补齐向量目录后，10 类/54 项，0 失败/错误/跳过，无输入漂移。覆盖 Room/FORMAL 事务、独立任务/并发/门控、原件快照、闭合/替换/引擎、TEMP 页面高级导出 |
| `android02` lint / assemble | exit 0；lint 566 warning、0 error；不是全量测试或设备业务验收 |

真实 HTTP 专项在新一次性库验证：存量非空迁移旧列守恒/新列 null、两类发行、改名后原键/并发/GET/重启原响应、审计末 SQL 失败整事务回滚、无授权401/原键异文409、姓名不可用时 proof=null。合成身份与隔离等价签名不代表原身份或生产供应。

Android 直接消费 `1851/private/name-proof-temp-vector.json` 与 `name-proof-independent-vector.json` 原响应。相关旧 R06 回归另用已有双段 fixture，路径/SHA 在 `android-related02/command.json`，不混称同一个新 HTTP 输入。

## APK与未验

- 新候选 `outputs/pda/Acrylic-PDA-staging-all-source-out-text-20261024.apk`，14524380 字节，SHA256 `7710794e4dc0f4c3fd7fcebb95eb4ee161acc7bf43ba16fb7d16f87da497653d`；从已验构建复制到新文件名，见 `review01/result.json`。
- `apksigner` 核新旧 APK 均成功、同既有开发证书，没有使用正式签名。旧 `Acrylic-PDA-staging-out-text-entry-20261023.apk` 与 Debug Keystore 原 SHA 保持。新 APK 未安装真机/模拟器，服务端未部署。
- 实核单写者；ADB 只枚举。系统3306/33060未认证，不连接/停止；33318由 OS 命令行核属其他项目，不操作。一次性1850/1851已认证停机，清理前重核进程/监听，清后禁止复用。
- 原操作员/本人救援凭据、真实任务/完整现场点按、USB/外置介质、四字号/实体键、规模与长时未在该候选验证。缺原姓名证明的旧资格不补填。历史 Android 全量41项失败/native crash未解，子集不替代全量。
- 自审核用途分离、原事务、旧行不回填、严格绑定、无原作业 token 外泄、默认开关不变。生产/TEMP归档、正式签名、原库/现网、main、部署/Release关闭，`localDeletionAllowed=false`；取消承接、多跳、接收者确认/激活及旧run03不恢复。

## 清理与开发备份

清理15处已结束合成库数据/binlog/tmp及本片构建输出、项目缓存和编译classes/tmp；登记9552文件/635338261逻辑字节（约606 MiB）。D余量33139486720→33802526720字节，窗口净增663040000字节另列，不将其他活动和分配粒度差异冒充精确释放。Windows PowerShell长路径失败、原清单漏两项长路径和补录哈希/PowerShell7收口均保留。只清已认证停机合成数据与可重建临时产物；两库禁止重启复用。

清单/摘要/输入依赖与命令/结果/哈希在 `cleanup01/`；提交回执见 `closeout-submit01/`，远端事实以完成回执及看板为准。冻结输入、原签名响应、失败/成功日志/XML、最终 APK、继承 WIP 和原身份保留，本机私有材料没有因此异地备份。清理和开发备份不计验收。

接续先读看板生成事实及本报告；真实外部待验与其他开发缺口仍按剩余计划、总目标第8节逐项核对，不能由局部通过宣布完成。
