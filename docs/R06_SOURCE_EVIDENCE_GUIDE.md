# R06 E1 原来源证据读取

日期：2026-09-20。本文对应固定基线 `6852da9fe5a02833eca22735a0d193cbfc64b346` 的独立 E1 工作树和短期分支 `codex/r06-source-evidence`。本批只读查询既有 TEMP 来源、控制、上传声明、已收正文、原存储回执和已知审计/占用事实；不接收新证据，不调用 `accept`、`finish`、M2a 详情来补正文，不正式化订单，不发 FH 号，不创建 assignment，不放行、不续扫、不释放、不删除。

## 拟注册入口

根 OpenAPI 索引和契约版本由整合执行者统一注册；本批只保存两个独立片段：`contracts/temporary-source-evidence-paths.yaml` 和 `contracts/temporary-source-evidence-schemas.yaml`。

- `GET /api/v1/temporary-source-evidence/{submissionId}`：查询参数 `warehouseId`、`creatorDeviceId`、`clientOrderId`、`workRound`。每次先用当前登录身份检查 `temporary_order:confirm` 和仓库范围，再以 `submissionId + 原 TEMP 二元 source + workRound` 精确选择。`canonicalOrderId`、订单、orderNo、assignment、slot 可以为 null；不从车号或临时编号推断正式订单。
- `GET /api/v1/temporary-source-evidence/{submissionId}/payload`：同一鉴权和来源选择后，读取已持久化正文原字节。最大 32MiB；先核数据库摘要、大小、固定 `submissions/{sha}.json` 引用、文件大小和 SHA-256，再核正文的 submission/control/source。不存在正文 404，私有文件缺失 409，损坏或控制不符 409。响应是原 UTF-8 JSON 字节，设置 `no-store`，不返回服务器路径。
- `GET /api/v1/temporary-source-evidence/{submissionId}/chunks/{index}`：同一鉴权和来源选择后，读取声明 upload 已收到的原块。索引 0 至 127，单块最大 256KiB，尾块按声明总大小校验；不把块当作已接收正文。缺块 404，越界 400，文件缺失/摘要不符 409。

详情响应只给摘要、计数、文件核验和缺口，不内嵌正文或全部块；生成详情时会校验已收正文及所有保留块，若块已收齐则校验拼接后的完整摘要。单次声明上限 32MiB，本轮没有完成大体量压测。`declaredUpload` 单独返回客户端声明摘要/字节/块数/已收块；`receivedPayload` 只反映 `submission_payload` 已验收正文，不能用 upload 声明摘要替代。`terminalProof.originalStoredReceiptUtf8` 是数据库 `receipt_text` 原文本，不是完整 HTTP 外层响应；原文 SHA 和 signedReceipt 派生字段单独列出，未知历史 key 明确 `UNVERIFIABLE_KEY`，不维护历史密钥环、不改写事实或放行。

## 返回边界

`provenance` 交叉核对原仓库、原操作员、原设备、原资格、授权 epoch 和有效期；`control` 保留原 submission、round、sourceRecoveryEpoch、原 freezeDevice、assignment/continuation null 事实；`mapping` 返回当前 V33 canonical/order/slot 值，空时明确 null。`physical` 只计云端已知 submitted captures 和 bale commitments，始终 `completeness=UNKNOWN_OR_PARTIAL`、`complete=false`、`vehicleEmptyProven=false`；零条不能表示车辆为空。`audit` 只给原设备/代次下已知事件数和 checkpoint continuous-through 前缀，始终不声称设备或来源完整。

`currentAssessment` 另列查询时服务端 epoch/mode、读者权限版本、当前 submission、缺口及 `writeAllowed`、`writeConfirmationAvailable`、`releaseAllowed`、`mayResumeScanning`、`stockReleaseAllowed`、`localDeletionAllowed`、`evidenceUploadAllowed`，全部为 false。恢复代次变化或服务非 NORMAL 只增加当前评估缺口，仍按日常已授权只读范围返回事实，不打开灾后白名单。

## 场景和证据

新建独立合成 MySQL `127.0.0.1:19319/r06_source_evidence`，专用 datadir、账号、签名密钥和空库均已核对；没有启动 HTTP，R05/R06/R09 原环境未触碰。

| 场景 | 实际断言 |
| --- | --- |
| A 无 upload、无正文先 ABORT | canonical/order/slot/assignment 为空；原回执字节和签名保留，payload 404；查询前后业务表/私有文件不变 |
| B 部分 chunk 后 ABORT | declared hash/已收块可读，received hash/bytes 仍 null；块原字节可读，缺块 404，payload 404，取消回执的 hash/null 不变 |
| C 完整正文后 ABORT | 原正文、PDA_TEMP canonical/slot、已知占用可读；原签名回执不重写；所有执行能力仍 false |
| 其他 | 收齐块但不 finalize 仍不是 accepted payload；正常 REVIEW_REQUIRED 可读但没有终态回执；跨仓、撤权、会话过期、错误 source/round、非法块索引和缺失来源拒绝且无写入 |
| 完整性 | 正文/块损坏、文件缺失及控制错绑返回 409；伪造签名保留原回执并标记验证失败/缺口，执行能力仍为 false；不修复、不重建文件 |

实际验证：`.local/e1-20260920/first-integrationTest` 保存 26 个 MySQL/MockMvc 用例（5 A/B/C 读取、16 权限/身份/恢复/错误参数、5 损坏/篡改），全部 0 失败/错误/跳过；`first-test` 保存 8 个专项单元，全部通过；同一运行完成 `bootJar`，55 秒，首轮未发生测试或构建失败。JAR SHA-256 为 `bad73b8c15e7d1a8773c197aca1b1387d0d956d512d5e2adcdfeddad3c51b06b`。独立片段临时组合现有 OpenAPI 后通过 116 路径解析校验，原索引文件未改；日志及摘要保存于同一忽略目录。查询前后使用业务表 CHECKSUM 和私有文件 SHA 清单核对，排除 Spring Session 会话维护表；没有把会话维护误报为业务写入。

W1 对 `review-baseline-1.json` 的 19 文件原始 SHA、读取链、鉴权、REPEATABLE READ 只读事务、文件/控制核验及上述 XML/构建/契约证据完成独立只读审查，无阻断项；审查没有另行启动数据库、HTTP 或重跑测试。审查后只补充本文的收口说明，源码、测试和契约片段保持冻结。专用 MySQL 的 datadir、数据库、账号、端口和父子进程已复核，仅对 `127.0.0.1:19319` 执行正常 SQL SHUTDOWN 并验证监听关闭；专用库、密钥、私有文件和证据全部保留，原环境保持不变。

本批没有合法 REJECT 夹具，因此不声明 REJECT 流程通过；没有真实 HTTP、设备、前端调用或现场验证，没有并发双事务快照专项或大体量压测。当前成果是固定基线下的原来源证据读取，不能视为完整灾后恢复、云端/实物事实完整性、写决定或放行验收。

白名单修改为新 `server/src/main/java/com/acrylic/outbound/temporary/evidence/`、唯一 `TemporarySourceEvidence*` 专项测试、两个契约片段及本文。没有修改已有 access/store/auth、Android/Web、迁移、版本、总 OpenAPI 索引、R09 或管理文件。
