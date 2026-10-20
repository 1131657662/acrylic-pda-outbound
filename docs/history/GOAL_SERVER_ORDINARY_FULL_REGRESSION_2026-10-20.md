# 服务端普通测试完整回归（2026-10-20）

## 结论与实际输入

输入 `codex/development` / `295aadc93b32f665c26914aa6c3752ce788d2310` 加输入清单中保留的 32 条继承/看板 WIP，不是干净树或最终部署候选。全部 5865 个实际文件的路径与 SHA、原字节 WIP 压缩包、tracked patch、远端回执、命令和环境见 `.local/goal-20261020-resume/server-full-regression01/` 的 `input-manifest.json`、`dirty-input-bytes.zip`、`tracked-input.patch`、`environment-before.json`。

本轮完整执行现有服务端普通 `test`，310 个 suite，XML 汇总 **4775 tests / 4756 passed / 0 failures / 0 errors / 19 skipped**，Gradle exit 0，用时日志为 7m 28s。19 个 skip 是未执行，不能计通过；包含参数化模板被条件关闭的记录，未展开参数不虚计为已跑用例。

此结果消除本轮输入上的普通测试失败，不能取代专项 `*IT`、真实数据库/HTTP/客户端业务组合、D18、第8节或部署闸门。生产默认、权限、原身份/原资料及删除边界没有据此放行。

## 命令与前置

```text
D:/acrylic-pda-outbound/gradlew.bat -p server recoveryRekeyClasspath test --offline --no-daemon --console=plain --max-workers=1 -Pr08FontFile=D:/acrylic-pda-outbound/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf
```

- 使用已有 JDK、共享离线依赖和既有原样本。`docs/sku.txt` SHA `3b6b8902184718c6714fd1ea00f87cd30c8d8527e744dea93c7e16e319495833`，未用替代样本。字体经已有任务核对，不跳过 prepareFont。字体/构建/依赖具体 SHA 在输入清单，不在报告手写版本事实。
- 先执行既有 `recoveryRekeyClasspath`，普通 test 沿 [D13修复](GOAL_D13_ORPHAN_OWNER_LIFECYCLE_TEST_REPAIR_2026-10-20.md)依赖既有 control classpath。没有手造运行时清单或跳过失败类。所有普通 suite 由同一次 test 执行，未用定向 XML 替换。
- TEMP/TMP 与 JAVA_TOOL_OPTIONS 的 java.io.tmpdir 仅本次子进程指向证据根 D 盘 tmp；一名主树写者、一次重型构建，运行前实查无 Java/Gradle。另一预检会话在主树运行结束后开始独立隔离树构建，不代停或代整合。
- 首次 PowerShell 启动器将 wrapper 路径解析为 `.gradlew.bat`，命令未找到，Gradle 没有启动；保留 `launcher01-failure.txt`。修正为实际绝对 wrapper 路径后，参数不变，只执行上述一次有效全量回归。

## 跳过逐类解释

| 类 | XML skip记录 | 具体条件与本轮限制 |
| --- | ---: | --- |
| SkuParserParityVectorTest | 1 | 未设置 `SKU_PARITY_OUTPUT`。原始 sku 普通解析已跑，但未在本轮生成 Android 交叉运行时向量 |
| RelayIndependentClosureTest | 3 | 未设置 `E06_CLOSURE_ARCHIVE`，没有为本轮配置明确原始 Android 闭合包 |
| RelayIndependentCollectionTest | 6 | 同一显式闭合包条件未配置；不凭默认目录拼造输入 |
| RelayIndependentOriginalTest | 8 | 未设置 `E06_REVIEW_ORIGINALS`；本类还需匹配 `E06_REVIEW_ARCHIVE`。8条含2个参数化模板，未展开不算成功 |
| SourceEvidenceIntakeFilesTest | 1 | Windows 符号链接权限不可用，既有假设终止该项；其余同类用例按本轮 XML，不能据此声称符号链接正向已验证 |

来源行号/原码 SHA 见 `conditional-test-source-map.json`，实际 19 条方法/模板名称见 `test-result-full01.json`。历史 E06 或 Linux 结果保持原输入与层级，不替代本轮 skip，也不为消除 skip 修改条件。

## 失败修复关联与守恒

- [D04审计前缀修复](GOAL_D04_AUDIT_STREAMING_TEST_REPAIR_2026-10-20.md)与 [D13孤儿owner修复](GOAL_D13_ORPHAN_OWNER_LIFECYCLE_TEST_REPAIR_2026-10-20.md)的类已纳入本轮普通 test；原失败日志/XML仍保留，不改写[初轮部署预检](DEPLOY_PREFLIGHT_2026-10-20.md)的失败事实。
- 收口逐 SHA 比对全部输入，唯看板因登记本轮在途状态变化；源码、构建、依赖、原始样本等其余输入不变。31 条非看板继承改动逐 SHA 不变，没有暂存或提交它们。
- 原始 XML 在任何后续构建前逐字复制到 `xml-full01/`；完整 XML/binary/HTML 封存为 `test-reports-full01.zip`，SHA `0864b1a17547af7e51d2d7dbae07f1864c3919aeca229700ce558922199fc202`。`test-result-full01.json` SHA `c3d0ee59bc53b7954bfd9c33ad670d43d45575e6c0af4c72d7aa9f0302d23712`，含每 suite 原始计数与哈希、skip和输入漂移。
- 作者和自审为本助手，无独立人员验收。没有为本轮启动业务服务、隔离 MySQL 或模拟器，没有操作设备业务/正式签名/原库现网或远端部署。普通测试内部运行器与合成文件不冒充业务 HTTP/MySQL 验收。

## 收口与下一步

本次仅新增证据报告和受影响索引，标“开发备份”；完整环境/进程、全WIP收口清单与输出哈希见本任务 `environment-closeout.json`、`final-manifest.json`、`closeout.md`。清理仅按已授权明确范围、无消费者、可重建标准执行，实际字节/余量回执单列；原失败日志/XML、原身份/Keystore、原资料/签名、最终APK及继承源码不动。

下一项优先按已有原件路径/SHA核对条件消费者的合法输入，能复用则新目录定向执行；不能取得匹配材料则如实保留对应未验并推进其他独立事项。部署工具链整改由另一会话独立负责，本轮不合入其隔离WIP、不用其未结束结果代证明。

总目标 active、未完成，实际最终候选上的 D01–D18、O/U、第8节仍须逐门补齐。生产决定、正式签名、原库/现网、main、部署和Release保持关闭，`localDeletionAllowed=false`，不恢复换机承接、多跳、接收者确认/激活链，不启动旧run03。
