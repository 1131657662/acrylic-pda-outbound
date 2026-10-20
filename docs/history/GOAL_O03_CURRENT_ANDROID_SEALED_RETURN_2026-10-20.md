# O03 当前 Android 封存电脑返还文件消费者（2026-10-20）

本轮针对输入提交 `a092a465c0d8798d02eb9d156637edb577758c3c`，补电脑一致性修复后的 Android 主机消费者证据。沿用既有 O03 协议和历史原件，不重做中转业务，不将历史成功升级为现时业务状态。证据根为 `.local/goal-20261020-resume/o03-android-sealed-consumer01/`。

## 缺口与改动

前轮电脑 `transfer` 离线回放已发布逐字返还文件，但该轮明确没有调用 Android 验签消费者。本轮将该输出作为外部封存输入，实际执行产品 `RelayReceiptVerifier` 和 `ReceiptVerifier`；添加 9 项主机测试及只读向量加载器。测试向量由 `PDA_RELAY_SEALED_VECTOR` 显式提供，目录和变量登记为 Gradle 测试输入，防止更换输入后误用 UP-TO-DATE 结果。普通测试没有封存材料时明确 skip，不能当作本项通过。

生产 Kotlin、服务端、契约、迁移和生产开关均未修改。仅 Android 测试及测试输入登记变化；既有原机文件选择、救援权限、原清单回查及 Room 持久保存不由本次主机测试替代。

## 固定输入与信任来源

- 原 ZIP：`.local/goal-20261013/o03-python-http-loss-01/journal/original.zip`，SHA-256 `2b1adca2ac197c6447a942d1ca4052edd2f70d650bda367d5c4498dd08033f4f`。
- 前轮实际电脑输出：`o03-result-binding01/sealed-replay-journal/01a13493-c6a2-73c6-800a-f4788f8a122d.pda-receipt.json`，与原下载逐字一致，SHA-256 `ac3d73ea35f35b0e96ab75dccd85b5c00f85e1ba05176a13e7ae2f9b2c652363`。
- 包外公钥：`.local/e2a-cross-20260925-1501/private/server-public.der`，SHA-256 `52b9156aa36db7db068451d3434e93559dd1993e150b0cd2dcec544f0e8fe9f9`。定位依据为原 `o03-runtime-01/database-identity.txt` 和 `o03-python-http-01/run.py`，不是从待验文件自报公钥建立信任。按既有 `OfflineSigner` 的公开指纹算法确定 kid，核对后验签。
- 原 ZIP 中只读提取 `order.json`、`submission.json`、`device-public-key.der`；原 ZIP 无 `receipt.json`，没有补造包内回执。原处理响应和原下载仅排版不同。
- 原来源为历史合成隔离成功批次；本轮读取原字节，不称原物理 PDA 或真实用户输入。

首次只读探测误选隔离 1504 公钥而得到 `InvalidSignature`；随后依据上述原运行记录定位 1501。错误输入、公钥 SHA、异常及正确定位依据保存在 `key-resolution.md`，不是业务失败、不是新签名或重签。未读取或复制私钥、token、密码。所有输入和源码逐 SHA 见 `consumer-inputs.json`、`consumer-inputs-final.json`。

## 验证

命令参数见 `gradle-command.json`，环境为 `PDA_RELAY_SEALED_VECTOR=<本轮vector绝对路径>`，TEMP/TMP 与 Java 临时目录仅任务子进程指向本轮 D 盘目录。调用：

```text
gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.relay.RelaySealedReturnTest --tests com.acrylic.outbound.pda.relay.RelayReceiptVerifierTest --offline --no-daemon --console=plain
```

`gradle01` 为首次 13 项通过；补输入登记并移除未用 import 后以 `gradle-final02` 复验：13 tests / 0 failures / 0 errors / 0 skipped，Gradle 退出 0。其中新封存消费者 9 项、既有验签边界 4 项；实际计数及逐 XML 哈希见 `test-result-final02.json`。测试包括：

1. 原电脑返还文件通过传递签名与原成功业务签名，状态由现有消费者判为 SYNCED，数量和整数克与原冻结正文一致。
2. 原处理响应与下载的全部 JSON 内容一致，原下载字节保留。
3. 错预置根、异人/机/仓/单、原正文加空格、改业务汇总、改删除边界及重复字段均拒绝。
4. 已有相同成功终态可幂等消费，待审回退及不同成功终态拒绝。
5. 每项结束复读向量，原字节未改；原件和全部继承路径另有逐 SHA 守恒检查。

## 分层、并发与保全

作者与自审均为本助手；Android 主机产品消费者是不同实现的文件消费证据，不是独立人员审查。没有本轮 HTTP、MySQL、Room 写入、APK 构建、设备业务、系统 OpenDocument、外置介质或生产签名。没有查询后续更正、撤回或净值；`currentBusinessStateVerified=false`、`releaseAllowed=false`、`localDeletionAllowed=false`。

另用户会话独立预检工作树及其主树报告保留，未代提交或操作；本轮重型任务启动前实查无 Java/Gradle 在途。共享看板修改前比较 SHA 并保存原件。原 APK/tar/身份/Keystore/签名与失败证据不变；本轮无隔离库或可回收大产物，清理释放 0，不重复计历史清理量。

本报告仅补 O03 当前主机有界组合，不关闭 O03、D12/D13、D18 或总目标第 8 节。Android 原机持久文件消费、原身份、非空可信保留、完整 UI、必要真实 HTTP/MySQL/非空迁移及最终候选组合仍依实际缺口保留。O02 主机 SQLite CANTOPEN 无新假设不重跑；取消链、旧 run03、原库/现网、正式签名、main、部署和 Release 继续关闭。总目标 active、未完成。
