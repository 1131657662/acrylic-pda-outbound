# R06 Android 产品成功原件消费验收

2026-09-25；当前助手实现、自审和隔离组合验证，无独立人员复审。持续目标 active，未上线。

## 实现与验收范围

原上传/刷新/取消路径只接受待审或取消，收到 `SUCCEEDED` 会停在待处理。现在通过 `TemporarySubmissionResponses` 读取原响应，在解析前存入既有 Room22 原响应表，再核恢复代次/模式；可能终态的查询、整包、开始分块、finalize、ABORT响应统一走此路径。原响应表也会保存尚未可信的待审/取消/损坏响应，不据其 RAW 标签赋予成功状态。

`TemporarySubmissionRepository` 仅在严格 `TemporarySuccessRepository` 验签、原冻结来源/产品核对通过后，于 Room 事务内同步接受凭据和 `SYNCED` 显示状态。最终写入失败时两者一起回滚，事先保存的 RAW 保留。已核验成功可在原身份下从本机原件重验，避免再发取消或正文；原取消意图不清除。重新打开页面会复核原件和显示回执，只有标签、缺原件或坏原件不能显示成功。

成功后停止上传自动重试，已解明的迟到取消不再算未知上传；草稿保持 FROZEN，扫描guard、原正文、资格、审计和取消意图全部保留。页面显示“成功原回执已核验 · 保留实物及记录”；恢复模式额外显示待对账/不得放行。没有成功后删库/清理/续扫或生产归档调用。

无新数据库表、Room迁移或API；仍 Android code20/Room23。仅服务端测试IT增加对产品状态的断言，生产Java未改。生产七类决定以及TEMP归档入口继续关闭，`localDeletionAllowed=false`。

## 当次验证

- 首轮定向12项中5失败：1项坏JSON异常属于IOException而被误归为自动网络重试，4项是导入向量夹具重开后误加载另一准备身份。原失败XML/日志保留；修解析异常分类及夹具重开边界后定向12/12通过。随后新增“只有SYNCED标签而无原件”负例。
- 最终 Android全套 **367项：364通过、3专用HTTP条件跳过**。含新产品消费8项，以及原上传/分块/取消、成功/归档验签耐久等既有回归；95份XML保存到 `android-product-success-full-xml/`。
- 新8项覆盖GET成功、原正文POST成功、迟到取消丢回执/Room重开、坏JSON及未签变更、恢复epoch持久阻断、换登录、无凭据假标签、最终事务失败回滚与坏原件重开；子场景不另加分母。
- APK构建、lintDebug通过；lint为0错误、186警告，报告保留，不称零警告。
- 新专用 **attempt48服务端IT1/1，Android6/6无跳过**。非空V34 seed→V36/binlog，真实Android冻结/上传→内部NORMAL_CONFIRM→丢成功读取回执→重开Room→产品RAW/验签/SYNCED→再重开复核→内部归档/丢回执重放。原取消意图保留；实际生产TEMP归档POST仍返回 `TEMPORARY_CONFIRMATION_REQUIRED`。
- 网络使用生产 Retrofit/OkHttp/真实回环HTTP；会话和设备签名边界为合成输入/测试签名器，不是原生Keystore或真机。内部确认和归档适配器仅隔离环境存在。
- 当前助手自审，两树diff检查通过；未重跑服务端旧494全套、Web/Chrome，也未做真机/模拟器页面验收。

## 证据

主树 `outputs/r06-e2a-takeover-20260925-01a0d612/`：

- `android-product-success-focused.log`、`android-product-success-focused-failure.xml`、`android-product-success-focused-fixed.log`；
- `android-product-success-full.log`、`android-product-success-full-xml/`、`android-product-success-lint.xml`；
- `android-product-success.apk` SHA256 `fc92cde122cbbcc9d1fb5ccf8e27434c756102a5199834bc6ec71e1bc37819ca`；
- `product-success-attempt48-*`：服务端XML SHA256 `350ddee14069f286503cdd37d229e195e05e9a0ba0e3786e8ad7f2443d85fbdb`，Android真实HTTP XML SHA256 `6f7096ba5ee0d2bf300f2ccbd9ceacc4ccd1a8dda0636fca3f7d29e69e19d59d`；
- `product-success-source-manifest.json`绑定1164个两树源码/资源文件，SHA256 `98855ae39cf41f642488794ee49bd104c5b51323c770436c770f30648711225e`。

原实验目录 `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-48` 已认证shutdown，全部数据/失败记录保留。private中合成凭据/私钥不复制到普通证据或Git。

## Git、限制和下一断点

主树 `codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`。完整未提交/未跟踪见同目录 WORKTREE_STATUS。没有提交/推送/两树整合/main/部署。测试结束无Java/Gradle/adb/emulator或19330/19434/19435/19436监听；共享3306/PID5448父4412未动。下次独立run从49检查分配。

R06非零候选/映射2+/高轮次、排除/拒绝/实物处置/续作、未冻结/零包/未决输入证据仍未完；排除/拒绝权限政策未定不擅定。生产TEMP归档产品调用依赖仍关闭的入口，不能以内部测试开放。历史keyring、独立人员、真实设备/Keystore/断电/OS死亡、长时、现场和生产权限审批未验。

下一可独立项：核对R08原确认TEMP适配及两树依赖。主树 `ExportFacts.head` 当前仅允许WEB，`PdaExportSource` 固定FORMAL sourceOrderId/原capture.orderId；当前真实TEMP首版成功已具备隔离证据。先沿已有树核对源码/测试与R08契约，实现只读原确认适配和原采集顺序验证；R07更正净值及审批政策不得从原结果猜算。R07设计仅只读参考，其87e1893旧依赖不能作当前事实。
