# R06 设备非空请求与回执迁移验收（2026-10-01）

输入 `D:/acrylic-pda-outbound` / `codex/development` / `787f494adf453aee53bd82e0c82859f4a776ee94` 加本批三份新增 androidTest。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)，[方案](AUTONOMOUS_PLAN_2026-10-01_DRAFT_RECEIPT_MIGRATION.md)先落盘后实施。七项原有改动按 before/protected.json 保护，未纳入提交。

## 结论

**设备迁移断言与宿主独立数据库读回通过。** `receipt-migration-3` 两个验收方法各 `OK (1 test)`、`numtests=1`；另一个目录前置方法通过但不计验收。迁移前 PID=2863，force-stop 后 PID=3483。包装器 exit=1/result=FAILED，原因是收尾瞬间 emulator 进程仍在；保留原记录，后续只读核实进程自行退出、ADB 无模拟器，未重跑测试。`post-teardown/supplement.json` 分开记录断言 PASS、宿主 PASS、原包装器 FAILED、环境已收尾。

本增量补齐 [A1.1 原验收](R06_A1_1_CODEX_2026-09-30.md) §7.3 中“device 请求/回执为空”的限定缺口；原 G4 轮本身仍是空请求/回执，历史记录不改写。不是 A1.2/A1.3 或 R06 整体完成。

| 独立库 | 产品生成请求 | 已验证非空回执 | 迁移与恢复检查 |
| --- | --- | --- | --- |
| 部分回执 | 11 | 1 | ACTION_REQUIRED、autoRetry=false；完整性检查返回 READY/1，队列保持暂停 |
| 完整回执 | 11 | 11 | ORIGINALS_STORED、autoRetry=false；完整性检查仍为完成/11，无补发 |

每库的停止、原件、签名、传输计划、请求、回执、队列七类表均非空；两个库分别比较 **51 张既有业务表的全行类型和值**。排除 SQLite 内部表、Android 元数据、Room identity 与该步新增绑定表；新增绑定表单独断言为空。每列保留 null、整数、浮点、文本与 BLOB，行排序比较；FK 检查干净。宿主 Python sqlite3 另从四份 database-before/after.db 只读检查 integrity/FK、全行一致、回执 BLOB 摘要、公钥非空、原键与条数，未使用设备 JSON 作唯一判据。

宿主派生快照摘要：部分 `54de2ae2acc7adef8b94a5e9b1e7a0af43a022ccd5c0a27beab45f5633833b69`；完整 `1489c7dd571b68e1a1c530685d8ae9f0551197f2aae9f36a0a2528d86e880ccb`。每条回执字节长度/SHA 见 `host-audit.json`。

## 实施与证据性质

新增 `DraftMigrationSnapshot.kt`（typed 全行编码）、`DraftMigrationFiles.kt`（隔离库转换/Room 迁移/副本）、`LocalDraftReceiptMigrationDeviceTest.kt`（两阶段验收）。复用既有 R06A11 fixture，未改产品、既有测试、迁移、ConfirmedV1Adapter、默认开关或契约。

- 每个新库用 UUID 命名；测试只碰自己的隔离库。前置重复 prepare，断言原计划/请求/签名次数不变；旧设备数据库与旧失败轮保留。
- 停止/传输计划/请求/回执接收与验签经过产品逻辑；会话、临时授权 fixture、瞬态签名者和耐久对端为合成测试边界。没有真实 HTTP、真实来源身份或设备 Keystore 声明。
- 用当前产品生成数据后，仅移除该步新增绑定表、恢复前一导出结构的 Room identity，形成受控旧结构库；新进程由真实生产 Migration 经 Room 打开与 schema 校验。**不是旧 APK 生成的历史安装库，不是跨 APK 升级验收**。
- phase2 只持公钥，旧签名私钥不存在；产品 checked 验证原回执，reconcile(null) 不启用暂停队列；签名调用=0，传输边界调用=0，peer 轨迹不变。没有调用 resume 来恢复发送，也不外推后台 worker 或真实网络 POST 审计。
- 同一助手作者/自审/执行；宿主独立字节读取不是独立人员审查。源码/APK/脚本摘要固定在 offline-4/artifacts.json，收尾再次逐项核对。

## 失败与离线验证

证据根 `.local/autonomous-20261001/draft-receipt-migration/`，下表磁盘均为 D 可用字节，失败证据全部保留。

| 步骤/目录 | 实际结果 | 磁盘前 → 后 |
| --- | --- | --- |
| offline | 新测试错误使用 RoomDatabase.use，编译失败，未上设备；failed-source 保全后整文件修正关闭方式 | 32024825856 → 32024817664 |
| offline-2 | staging 与测试 APK 成功；既有迁移/队列/身份三类单测 **16/0/0/0**，脚本 parseErrors=0 | 32024776704 → 32022896640 |
| receipt-migration-1 | 就绪命令不被 AVD 支持，180 秒有界只读轮询超时，**instrumentation 0 次** | 32022896640 → 31999705088 |
| offline-3 | 改用 dumpsys user/当前 user/包路径/runner 精确就绪门后编译和脚本解析通过；单测复用 offline-2，未重跑 | 31999692800 → 31999668224 |
| receipt-migration-2 | setup 通过；beforeMigration **1 test / 1 failure**。部分库已建；完整库传入非法 SyncRun 批量参数，被 1..20 构造器守卫拒绝，未进入迁移验收 | 31999660032 → 31985709056 |
| offline-4 | 仅新增 fixture 改用默认 SyncRun；APK 编译/解析通过；16 项单测为先前未变产品输入结果，未声称重跑 | 31985680384 → 31985614848 |
| receipt-migration-3 | 两个验收方法/两个库断言通过；退出竞态使包装器 FAILED，未覆盖其原结果 | 31985606656 → 31944609792 |
| post-teardown / closeout | 原始 instrumentation 复核、宿主四库只读审计、无 AVD/设备一致、受保护摘要/实测输入复核通过；没有重跑测试 | 收尾 31944601600 |

用户允许同类前置修正，故修正不支持的就绪命令与非法测试参数；未放宽产品守卫，未重跑真正迁移断言失败来刷绿。后续 runner 应在 emu kill 后有界等待 launcher 与 qemu 均退出；只等待退出，不再次 instrumentation。本批成功断言的原包装器失败仍计入失败台账。

## 环境、安全与未验

- 总磁盘起止 **32024870912 → 31944601600**，均高于 20 GiB。所有新旧 .local 数据保留，本批无 MySQL attempt/datadir 清理。
- 每轮真机 `22D2910042` 前后枚举行一致；只执行 `adb devices -l`，没有任何以真机为目标的命令。所有定向操作为 `-s emulator-5554`，`ANDROID_SERIAL` 同值；一次一个 PdaDownloadVerify。总收尾清单仅真机，无 AVD、无测试执行器；原 mysqld 未动。
- 三份新增源码以外无代码改动；七项保护文件逐项 SHA 相同。编译和相关 16 项单测不等于完整 Android 全套/lint、服务端 IT 或真机验收。
- 实际旧安装升级、真机重启/断电、真实扫码停止竞态、真实配对/登录/Keystore、实际 HTTP 丢回执、长期留存及现场原资料仍未验。A1.2/A1.3、R06-BIND-PAGE、R07-P08 和其余停报/业务未决项不由本增量解决。
- `localDeletionAllowed=false`，开关默认不变；未部署、未接原库/现网、未合 main、未打标签。提交推送仅源码/文档；.local/APK 仍是本机证据。实际 Git SHA 与远端回执见 commit-review/git-final.json，不追逐文档自身提交 SHA。
