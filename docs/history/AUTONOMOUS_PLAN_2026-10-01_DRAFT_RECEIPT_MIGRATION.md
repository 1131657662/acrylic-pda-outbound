# R06 设备非空请求与回执迁移增量方案

输入：`D:/acrylic-pda-outbound`，`codex/development`，`787f494adf453aee53bd82e0c82859f4a776ee94`。单助手；七项原有改动按 `.local/autonomous-20261001/handover-sql-rollback/before/protected.json` 保护。

依据：[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md) A1.1 要求“非空迁移保留原键/请求/回执”；[已收口设备批次](R06_A1_1_CODEX_2026-09-30.md) §7.3 明确 device 请求/回执表为空。既有 Robolectric 单测不代替本次设备原生 SQLite。优先补这个 R06 可独立缺口；不重跑已批准的 G1/G4/②/⑤，不处理停报缺陷。

## 范围与选择

采用新的两阶段设备测试。分别建立部分回执与全部回执两个 UUID 独立库，经产品停止/计划/接收验证路径生成非空数据；授权、会话、签名者和耐久对端沿既有合成 fixture。将新库转换为导出的前一结构（仅撤销本步新增表、恢复其 Room identity），然后真 force-stop，再以生产 Migration 在新进程打开。与直接 raw SQL 填请求相比，这能同时证明产品生成的字节得到保留；与启动实际旧 APK 相比，证据限定为受控旧结构 fixture，不声称跨 APK 升级。

## 退出标准

1. 上设备前 staging/APK 编译成功，新增脚本 parseErrors=0；相关既有迁移/队列单测无新增失败。脚本拒绝覆盖 attempt，数据库随机命名，前置目录可重复创建，prepare 重复不重复原键/签名。
2. 每库请求、回执响应 BLOB、公钥、停止原件/签名、事件、队列均非空；一个库只含首回执，一个含所有回执。队列明确暂停 autoRetry=false。
3. phase1/phase2 各真跑一个 instrumentation 方法，numtests=1，PID 不同；全业务表 typed 全行快照逐项相同，FK 干净，新增绑定表为空。Room 实际执行迁移且完成 schema 校验。
4. 重开后的产品只读完整性检查可验证原回执；没有旧签名私钥，无新签名、无对端 GET/POST，reconcile 不复活补传。保存迁移前后数据库副本供宿主 sqlite3 独立读回核对。
5. 只启动一个 PdaDownloadVerify AVD；所有定向 adb 用 emulator serial 与 ANDROID_SERIAL。真机仅 adb devices -l 枚举，前后相同；磁盘高于20 GiB。任何失败保全，不把前置/零测试记通过。

## 证据与边界

新证据根 `.local/autonomous-20261001/draft-receipt-migration/`；离线、device attempt、前后环境/受保护摘要、源码/APK 摘要、数据库副本均保留。本批不建 MySQL attempt、不改产品/既有测试/迁移/默认开关。实际旧 APK、现场身份/Keystore、真机重启/断电、真实网络故障、全部迁移跨度均不外推。A1.2/A1.3、R07 附件与差异审批等继续未完成。

按自主授权写方案后立即实现；通过后归档旧看板、更新进度索引/验收/自主报告并明确路径提交推送。版本事实只在看板生成块。
