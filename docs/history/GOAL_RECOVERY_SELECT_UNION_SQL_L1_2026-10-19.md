# 恢复 SQL effect gate UNION ALL 单测验收（2026-10-19）

## 结论

在输入 HEAD `3036aefd99fd5a385ca5ad88da45284ea073a8fb` 上，定向运行 `RecoverySelectSqlTest` 通过：43 项，失败 0、错误 0、跳过 0，Gradle 退出 0。当前未提交的恢复 SELECT parser 改动允许受限 `UNION ALL` 分支继续按 `SOURCE_READ` 检查 effect authority；lease fence 生效后，语句在触达 JDBC 前拒绝。普通 `UNION`、含危险函数/写语句、缺分支或尾随非法语法均在 JDBC 前拒绝，嵌套/长度/分支上限反例也被拒绝。

这是本地单元测试，JDBC 使用 mock，没有连接 MySQL、HTTP、Web 或设备。它只验证 SQL 分类器及 effect gate 单元行为，不证明真实 MySQL 执行、恢复业务链、D13/D14/D17、最终候选 L2/L3 或总目标完成。生产决定能力、TEMP 生产归档和 `localDeletionAllowed=false` 未改变；接收确认/激活取消链未恢复。

## 输入与命令

- 验证输入：`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 与 `origin/codex/development` 一致。
- 受影响代码：`server/src/main/java/com/acrylic/outbound/recovery/RecoverySelectSql.java`，SHA-256 `d74074ad285be5d6176555aacd6533ce0a1ad5116c66505ec5f5e067fa04e274`；测试：`server/src/test/java/com/acrylic/outbound/recovery/RecoverySelectSqlTest.java`，SHA-256 `46880b896d142673c63f1e88c2e4d158d5a49faff5103d395ff0620397fc3eee`。这两处改动在本次测试前已存在；本次只验证，没有编辑源码或测试。
- 全工作区 238 条 dirty path/status/SHA 清单位于 `.local/goal-20261019-resume/recovery-select-union01/input-manifest.json`，SHA-256 `12f0183db27d08fb365abf567e3a6ba3abe2770cc18f26cbff77f34940467834`。清单同时固定 579 个 parser、测试及构建输入；执行前后受保护输入零漂移。
- 同包源/测试差异快照 `.local/goal-20261019-resume/recovery-select-union01/recovery-sql.diff` SHA-256 `6921ceb230f4b3d916788fdd1f2e806e60264f4d5da0b24695572dcb9e019519`；命令记录 SHA-256 `9db19e355d9fb2d84ac6987aa3745cd54b4f61a60efae4219b4d2a4b5206686e`。

实际在仓库根目录设置任务级临时目录后执行：

```powershell
$env:TEMP = 'D:\acrylic-pda-outbound\.local\goal-20261019-resume\recovery-select-union01\tmp'
$env:TMP = 'D:\acrylic-pda-outbound\.local\goal-20261019-resume\recovery-select-union01\tmp'
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir=D:/acrylic-pda-outbound/.local/goal-20261019-resume/recovery-select-union01/tmp'
& .\gradlew.bat -p server test --tests com.acrylic.outbound.recovery.RecoverySelectSqlTest --no-daemon --offline --console=plain --max-workers=1 --project-cache-dir .local/goal-20261019-resume/recovery-select-union01/project-cache 2>&1 | Tee-Object -FilePath .local/goal-20261019-resume/recovery-select-union01/gradle-test.log
$exitCode = $LASTEXITCODE
```

## 证据与限制

- 原始 Gradle 日志 `.local/goal-20261019-resume/recovery-select-union01/gradle-test.log` SHA-256 `c80f144108f91924e663042fc775f087c2deff265e61701369ced5f43f66f275`；JUnit XML `server/build/test-results/test/TEST-com.acrylic.outbound.recovery.RecoverySelectSqlTest.xml` SHA-256 `90f8138fecc54ff727c73c158b18b1c415cc8a9404a0c3004bc56e1166af0b06`。XML 已按相同字节另存同证据根的 `junit-original/`，后续构建不能覆盖该原件。
- 退出码/计数与输出绑定见 `execution.json` SHA-256 `0b43391cfad297c8f79e8ceda44d36008aa5f42d272ff41816a76e5dc4312745`；运行后环境快照 `post-environment.json` SHA-256 `030c3d56d1e55562a767489055ccb01b1b436e664ccbc555b1a70fddb4febc01`，均位于同一 `.local` 目录。
- 构建按既有 `report-rendering` 前置任务下载固定字体，最终 SHA-256 为 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`，与脚本内校验值一致。它不涉及项目服务或业务数据。
- 运行后无 Java/Gradle/模拟器进程；系统 MySQL80 仍监听 3306/33060，配置 datadir 为 `C:/ProgramData/MySQL/MySQL Server 8.0/Data`，本项未连接且 UUID 未确认。ADB 5037 仅用于枚举到 M71 `22D2910042`，未操作设备。Debug Keystore SHA-256 `0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb`；未使用正式签名。
- 专用 tmp/project-cache 共 15 文件、3,238,884 字节。清理前的执行工具自动策略返回 `blocked by policy`，删除未执行、未换接口重试；释放字节为 0。路径、逐文件 SHA、构建依赖锁及结果见 `cleanup-rejected.json`，SHA-256 `0806e64255f3f9ff05088728aa90c6d045e53e24ea7e9130af22fecf12d935d1`。D 盘测试前 44,794,265,600、测试后 44,742,025,216 可用字节是环境采样，不计为清理收益。

后续仍按第7节逐项验证实际受影响入口；不能把此单测或历史 U04 L1 拼成 D17/D18 或第8节通过。
