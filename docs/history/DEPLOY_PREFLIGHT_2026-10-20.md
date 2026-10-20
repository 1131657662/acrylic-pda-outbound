# 部署预检记录（隔离树）

日期：2026-10-20

## 结论

本轮预检可以在当前会话执行，且没有向主开发树写入源码、没有连接远端 test 服务、没有执行停机、迁移、切换或激活。预检结果为 **阻断**：当前输入没有形成可放行的 test 部署候选。

## 隔离边界

- 输入提交：`69d98046ec14c95a9d71bf350daa5a6417c8d14b`
- detached worktree：`D:/acrylic-pda-outbound/.local/worktrees/deploy-preflight-20261020`
- 预检证据目录：`D:/acrylic-pda-outbound/.local/deploy-preflight-20261020`
- 隔离树在执行前后没有业务源码改动。
- 主开发树由开发会话独立推进；本轮没有对主树执行 checkout、reset、merge、部署或服务操作。主树的版本事实仍以 `docs/ORCHESTRATION_STATUS.md` 生成事实块为准。

## 执行与结果

### 构建

1. `.\gradlew -p server test bootJar --offline --no-daemon --console=plain`
   - 失败原因：Gradle wrapper 的发行包下载遇到连接重置；不是源码断言结果。
2. 同一任务复用本机共享 Gradle 缓存后，`report-rendering:prepareFont` 固定字体下载仍因网络连接重置失败。
3. 跳过字体任务后运行：
   `.\gradlew -p server test bootJar --offline --no-daemon --console=plain -x :report-rendering:prepareFont`
   - 退出码：`1`
   - 汇总：`4764 tests completed, 11 failed, 17 skipped`
   - 当时失败报告：隔离树 `server/build/reports/tests/test/index.html`；后续定向测试会重写此目录，初轮失败 XML 已另行保存。
4. 单独运行 `.\gradlew -p server classes bootJar --offline --no-daemon --console=plain -x :report-rendering:prepareFont`
   - 退出码：`0`
   - 这只证明编译和打包任务可完成，不覆盖失败的全量测试，也不构成部署放行。
   - 生成的 JAR 仅为隔离构建产物，不能作为已验证发布包；其 SHA-256 为 `fea7db54194aa7e32b2aaecf5965955425ca2b95d5043e3b4b42468f7a6aa113`。

### 失败测试分类

- `SkuParserTest.suppliedFilePreservesAll12639Bales`：缺少被 `.gitignore` 排除的授权原始样本 `docs/sku.txt`，抛出 `NoSuchFileException`。不能用替代样本冒充原样本验证。
- `RecoveryLeasePublicationTest` 两项、`RecoveryLeaseVisibilityTest` 一项：租约退休/孤儿统计断言失败。当前证据不能区分源码回归与所需隔离环境未准备完成，暂不放行。
- `RecoveryRekeyCliTest` 六项：缺少测试运行时生成的 `build/recovery-rekey-classpath.json`，均以 `NoSuchFileException` 失败。
- `SourceEvidenceIntakeAuditPrefixTest.conflictingFormalSubmissionDigestFailsClosed`：fail-closed 断言失败，需单独复现和审查。

### 部署工具预检

- `python -m py_compile deploy/test/verify_backend_release.py deploy/test/test_backend_activation.py`：通过。
- `python deploy/test/test_backend_activation.py`：8 项均在 `setUp` 阶段因 Windows `WinError 1314`（当前进程没有创建符号链接权限）报错；没有触达真实 systemd、MySQL 或 HTTP。
- 部署脚本仍包含旧发布口径的硬编码：校验器、激活器、激活测试夹具和 `tools/package_test_release.ps1` 的约束彼此不一致，不能只替换源代码 SHA 就生成当前候选包。具体位置已由 `rg` 记录在本轮终端输出中。
- 没有执行真实远端部署、数据库迁移、备份切换、服务停止/启动、HTTP readiness 或 worker 验证。

## 同轮补充预检与复验

初轮失败没有被删除，也没有被后续定向结果覆盖。以下补验不修改源码、测试或部署脚本：

- 从主树取得 Git 工作流已登记的原始样本，仅在隔离树创建逐字相同的副本；SHA-256 为 `3b6b8902184718c6714fd1ea00f87cd30c8d8527e744dea93c7e16e319495833`。原样本保持不变。
- 通过既有 `r08FontFile` 参数复用已安装字体，核对其 SHA-256 与构建脚本固定摘要一致，正常执行 `prepareFont`，补验不再跳过字体任务。
- 执行既有 `recoveryRekeyClasspath` 任务生成所需 classpath；没有手造测试输入或修改断言。
- 定向命令：`./gradlew.bat -p server recoveryRekeyClasspath bootJar test --tests com.acrylic.outbound.inventory.SkuParserTest --tests com.acrylic.outbound.recovery.RecoveryLeasePublicationTest --tests com.acrylic.outbound.recovery.RecoveryLeaseVisibilityTest --tests com.acrylic.outbound.recovery.RecoveryRekeyCliTest --tests com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditPrefixTest --offline --no-daemon --console=plain -Pr08FontFile=D:/acrylic-pda-outbound/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf`。
- 结果：57 项，53 通过、4 失败、0 跳过，退出码 1。SkuParserTest 20 项和 RecoveryRekeyCliTest 17 项全部通过；另三类测试仍有 2、1、1 项断言失败。没有重新跑完整测试，不能声称完整测试仅剩 4 项失败。
- 正常字体条件下单独执行 `classes bootJar recoveryRekeyClasspath`，退出码 0。JAR SHA-256 为 `8a1e1851324620f2ee51a77457413f9ab79384eab0cc0bcf1346bfbf42417a33`，大小 280805786 字节；这取代前述跳过字体任务产生的临时 JAR，仅用于预检，未发布。
- 在本机既有 Ubuntu WSL 中运行原有 `test_backend_activation.py`，8 项全部通过、退出码 0。使用命令边界 stub，临时目录位于项目 D 盘；这解决 Windows 符号链接测试环境限制，不证明真实 systemd、数据库、readiness 或 worker 可运行。
- 迁移文件编号静态检查无重复、无缺号；未连接数据库或执行迁移，版本事实只见看板生成块。

### 剩余失败的代码核对

- 三项恢复租约断言将 `registerOperation()` 后的 `retire()` 当作立即释放 owner；实际 `RecoveryFenceAuthority.retire()` 在仍有 operation 时只设置 retirementRequested 并返回。这与现有测试预期不一致，需开发会话按已批准的生命周期语义审查，不能单凭本次测试推断真实恢复失效或直接删断言。
- 审计前缀失败测试仍模拟 `JdbcTemplate.queryForList`，而 `PdaAuditFacts.verifyAll()` 使用带 RowCallbackHandler 的 `jdbc.query`。当前 mock 没有将冲突行交给新查询消费者；因此该用例不能证明冲突校验实际失效，也不能作为通过证据。需修复测试覆盖并复验，本轮不改代码。
- 初轮提及的“环境缺失”并非剩余四项的已证根因；以上是源码与测试核对所得的具体不一致，真实数据库行为仍未验。

### 部署包静态阻断

- `deploy/test/verify_backend_release.py:29`、`activate_backend.sh:14`、`test_backend_activation.py:62-66` 与 `tools/package_test_release.ps1:31` 仍固定旧口径，需与看板生成事实块对齐。
- 旧打包器没有生成校验器要求的 `artifactHashes` 和独立 `source-sha256.json`；仅修改版本字段也不足以构成可校验包。
- systemd 单元声明了外置 report-rendering 发行目录和 browsers 路径；旧打包器未装配相应运行时目录。本轮没有构建并验证完整 renderer/browser 部署布局。

## 放行判断与下一步

本轮完成了可在不改代码、不碰远端条件下的构建、前置补齐、失败分类和脚本自测，**没有补齐全部部署闸门，不能放行 test**。固定输入仍是上述提交，不因为开发会话推进就自动扩大已验 SHA。

后续顺序：由开发会话消除测试与实现不一致并完成同一候选的完整回归；统一不可变包、manifest、校验、激活和 renderer/browser 布局；在独立可回退环境验证非空升级、备份还原、API/worker、签名及客户端兼容；取得明确 test 部署授权后才执行真实激活和现场验收。实际激活回执是部署证据，不能把它作为部署前已经完成的条件。

本轮没有修改业务源码或部署脚本，没有提交、推送、合入 main 或部署。本轮核对时主树与远端开发分支已由开发会话推进至 `a092a465c0d8798d02eb9d156637edb577758c3c`；这不是本轮完整候选验证结果。

## 证据与清理

保留目录为 `.local/deploy-preflight-20261020`：原始 Gradle 日志、初轮失败 XML、定向五类 XML、`test-xml-evidence.zip`、`targeted-failure-details.json`、`supplemental-inputs.json`、构建/脚本自测退出码、重建输入清单和 JAR 哈希均已落盘。初轮完整 HTML/XML 中可重建部分随隔离树清理；不再引用已删除 HTML 为现存证据。

隔离 worktree 已从 Git 登记移除，清理前核实 tracked/untracked 状态干净、没有本轮 Java/Gradle 消费者；原始样本、主树与其他会话进程保留。清理前逻辑文件量 441041457 字节；D 盘可用空间从 40761937920 到 41295589376 字节，观测增量 533651456 字节，存在其他活动开发写入，不能将该增量全部归因于本轮。详细见 `cleanup-plan.json`、`cleanup-result.json`。

共享看板和索引采用写前原字节比对及最小增补；本轮仅新增本报告和补充文档入口，不提交其他会话的 WIP。主树继承文件通过哈希清单核对，开发会话新增文件保留。

保留的三份原始日志及 SHA-256：

- `server-test-bootjar.log`：`893b298a8190eb8519625409462283a26d20968e3daf998760cad5290a8eb4cd`
- `server-test-bootjar-shared-cache.log`：`ba3237274541b47dae4ea77b1f0a9ce03356c579d0accbbefb30985681227e02`
- `server-test-bootjar-no-font.log`：`e0743a3bca50f62f15ae309ad87ab4806126d876a63be53d180836a6ab891569`

## 后续处理授权与文档备份

2026-10-20 用户已授权先保存本报告及看板/索引，再按依赖逐项处理断言语义、部署工具及独立真实数据预检；此授权不包含真实 test 部署。三份文档已逐项审阅敏感信息，未发现凭据、私钥、原始业务记录或数据库内容，无需脱敏；本地日志/XML/数据库不纳入 Git。此次提交标注“开发备份”，不表示验收通过。后续整改证据单列，初轮失败保留，不追改为通过。
