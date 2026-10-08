# D07 TEMP 故障可信归档链开发记录

本记录只覆盖 NORMAL、未回退且已有 canonical 的 TEMP 来源。原提交须已实际 ABORTED，故障前已独立归档的 E2A 截点、原公钥、完整原文/页/审计可逐值核验。主管同意沿已确认 R07 政策实现；原 E2A STOP 是历史证据，不要求故障机生成新的 handover STOP/ACK，不伪造原机回执。

本范围仍开发中，不计 D07 整体或 D01–D18 总目标通过。FORMAL、无可信归档截点的代传、跨截点 R09 重建、故障产品操作页面及附件适配均是开发缺口。D06 整体、D04 候选排除与 D05 取消释放的政策阻断继续单列，不能转入现场清单。

## 输入与实现

- 主树 `D:/acrylic-pda-outbound`，`codex/development`，输入 HEAD/实际远端 `75024cb86a7217dbe51e69f3f94399e7f322d701` 加本批明确路径；本聊天单助手、唯一写者，其他项目聊天 notLoaded。
- 接续52条 dirty 的完整副本/hash/diff、Git、全部进程/监听和磁盘在 `.local/d01-d18-20261008/d07-resume-03/`；此前21条与 `d07-resume-02` 一致，19条继承路径未改，原 `d06-stop-close-20261008` 三项 hash 不变。
- 后续68条完整 dirty 副本/hash/diff 和环境在 `d07-resume-04/`；19条继承及原停止断点三项逐hash仍一致。C/D可用80742965248/22567567360字节；既有3306/13316/ADB/19387 Harness不操作，无模拟器。
- 设计 `.local/d01-d18-20261008/d07-design.md`，作者自审 `d07-self-review-01.md`；版本及迁移总事实只见看板生成事实块，迁移编号已跨33树核对。
- 并发后作者自审 `d07-self-review-02.md`，源码范围清单 `d07-scope-01.txt`；单作者自审不冒充独立人员。阶段保存仍明确开发中。
- `HandoverFaultInput/Archive/Store/Operation/Configuration` 与 `HandoverTemporaryArchive`：严格输入、独立有 `order:handover` 的 Web 主管、断电收回保管/实物位置/零未决输入、已归档验证时间不晚于控制时间。失联可能仍作业、缺钥/原文/页/审计/截点、错高水位拒绝。
- 故障操作沿原命令内核及锁序：实际 ABORTED 重验、原设备 DISABLED/会话撤销/版本推进、不可变围栏、FAULT_FENCED/COMPLETE、原请求/主管签名/审计/outbox 同事务；不释放占用、不删除、不记账。数据库独立阻止围栏更改、普通再启用/改钥；普通启用 API 返回 `DEVICE_FAULT_FENCED`。
- 审批/准备/确认/激活及历史前驱重验故障签名和原件；manifest 使用 `stopKind=FAULT_FENCE` 与 `faultReceipt`，不混入 `stopReceipt`，不宣称 DEVICE_ACKED。Android 使用主管签名域/身份分离，保留原 ACK 分支及五项 false 能力。

## 实际验证与失败

所有新产物、隔离库/datadir/dump/binlog、HTTP 原件、Room 和 XML 放 D 盘项目 `.local/`。单重型 Gradle，复用 `.local/build-cache/gradle`，子进程 TEMP/TMP/Java 临时目录在 D 盘。

| 证据目录/编号 | 当次结果与范围 |
| --- | --- |
| `d07-compile-01`、`d07-android-compile-01` | 编译通过；均0业务测试，不代验收 |
| `d07-unit-01` | 600/1/0/1：负高水位已拒绝，但拒绝文案不符；修本模块错误码映射 |
| `d07-unit-02` | 15/0/0/0，仅新故障输入/默认门，不替代全回归 |
| `d07-compile-02` | 跨包类/方法访问失败，0测试；已修 |
| `d07-testclasses-01` / `02` | 缺 `E2aAndroidPreparation` import 失败，0测试；修后编译 exit0，0业务测试 |
| `allocation-1126/1127.json` | 早期扫描误命中 hash/UUID，未建库；后续按实际 attempt 路径分配，原失败记录保留 |
| `d07-fault-1128` | 分配记录缺 `nextRun` 字段，provision 建库前退出；不复用目录 |
| 1129 / `d07-fault-test-1129` | 1/1/0/0、源码无漂移：申请后真实中止，围栏仍拿旧 NONTERMINAL 比较，反例前被拒。修为锁内重验实际 ABORTED 并同事务推进此维度；库已 dump/hash/认证停机/原 PID 退出 |
| 1130 / `d07-fault-test-1130` | 1/0/0/0、源码无漂移：原 Android 归档 → 真实 HTTP 中止/申请 → 独立主管故障围栏/审批 → 接收 Android 准备/暂存/本人确认/激活；4份接收 Android XML 各1/0/0/0。未到最终提交 |
| 1131 / `d07-fault-test-1131` | 1/0/0/0、exit0、源码无漂移：真实非空升级、故障反例、末审计 SQL 回滚、普通启用/数据库保护 → 接收 Android 最终提交/主管记账/重启成功消费；7份接收 Android XML 各1/0/0/0 |
| 1132 / `d07-fault-test-1132` | 1/0/0/0、exit0、源码无漂移：1131范围再验，新增两个真实HTTP同键请求在共享写门前同时等待，提交后返回同原字节且唯一围栏；7份接收Android XML各1/0/0/0 |
| `d07-android-fault-unit-01` | 2/0/0/0、源码无漂移：生成主管签名与真实 Room，合法原件暂存；自批/原机身份/无权限/虚构 ACK/失联/未决输入/高水位不符不落阶段 |
| `d07-contract-01.log` | OpenAPI 解析及引用校验 exit0；不替代运行行为 |
| `d07-server-regression-01` | 普通服务端3379/0/0/1、exit0/sourceUnchanged；Windows符号链接条件skip未执行，普通test不含IT |
| `d07-android-regression-01` | 受影响Android516/0/0/10、exit0/sourceUnchanged；10类HTTP条件skip未执行，明细见inspection.json。默认APK/assemble/lint通过，lint0 errors/221提示 |

早期误设新 Gradle 缓存导致 wrapper 下载，两次中断 exit1、0业务测试，目录保留；其后找到并复用已有缓存，没有重复安装。

1131 故障末审计注入明确发生在设备围栏与主管原签名命令写入后，精确 `SQLSTATE=45000` / `code=1644`；全耐久表含 device/PDA session 逐字节回滚（仅排除 Web session 框架到期元数据）。最终账务注入沿真实完成记录最后写入，精确 SQL 失败、全业务/续作表无半结果。缺坏正文/页/STOP、缺可信钥、错来源/集合/高水位、未来或早于归档截点、原申请人自批、撤权、失联/在线及未决输入反例均实际 HTTP 拒绝。原设备 bearer 随会话撤销失效。

## 独立消费与保全

- `.local/d01-d18-20261008/verify-d07-fault.cjs`：独立 Node RSA 验签、canonical/原件摘要、主管与原操作人/申请人分离、false 能力及原前缀；1130/1131 日志分别为 `d07-node-1130.log` / `d07-node-1131.log`。
- `verify-d07-room.py`：只读 SQLite integrity/FK、故障 manifest/原字节、本人确认与激活尾写回滚、原前缀/guards、最终成功唯一耐久行；日志 `d07-room-1130.log` / `d07-room-1131.log`。1130 没有最终成功消费，只有1131证明该部分。
- 1132两消费者exit0，见 `d07-consumers-1132.json` / `d07-node-1132.log` / `d07-room-1132.log`。后续Node补从耐久表快照解码原canonical命令、独立计算摘要并绑定签名receipt/原请求/不可变围栏及原响应字节，`d07-node-command-1132.log` exit0。
- 原完整日志/XML/hash/命令在对应 `d07-fault-test-*`，DB/HTTP/Room原件在 `.local/e2a-cross-20260925-<编号>/`；每轮认证停机、dump及hash、启动 PID 退出和空监听回执在 `d07-fault-<编号>/retention.json`。失败库不复用。
- 身份/密钥为隔离生成，Android 是 Robolectric/native Room 与真实 localhost HTTP，消费者是独立工具而非独立人员；不代表原身份/Keystore、真机、现场物理故障或实物控制已验。
- 当次默认APK保存 `.local/d01-d18-20261008/d07-android-regression-01/app-debug.apk`，SHA-256 `76BCCA48EEAC96EABE86A94E008EFB8481589C3C7FFED9C3F6B3D8B8CAF976F4`；本机开发构建未发布，不代表正式签名。原XML/日志/命令与源码前后hash同目录保留。

## 未完与接续

本子项定向、真实HTTP/数据库/Android组合、独立工具、自审及受影响回归已登记，按明确路径保存开发进度。故障产品操作/凭据页面、附件、FORMAL、无可信归档及跨截点 R09 恢复继续逐项实现验证；随后执行 D08–D18 及第8节全部闸门。

生产开关/签名能力、七类生产决定和 `localDeletionAllowed=false` 保持。不接原库/现网、不合 main、不发布部署、标签/Release 或正式签名。原资料/旧 BCD/历史失败及恢复限制继续保留，源码开发备份不代表私有证据已异地备份。
