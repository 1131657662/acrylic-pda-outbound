# 恢复态本人读取原交接正文

此接缝复用产品 GET `/api/v1/pda/handovers/{caseId}/manifest` 与 `recovery-package`。只读消费已经存在且精确核验的指定接收者准入、限定资格、PREPARE 和本人真实 LOGIN 会话，不创建缺失审计或登记，也不授予确认、激活、首轮、扫描、释放或删除能力。普通交接继续按原资格核验。

## 服务端固定输入

默认 `outbound.handover.recovered-download.enabled=false`。隔离验证显式启用时，同时需要既有 `outbound.handover.commands.enabled=true`，以及以下服务端配置：

- `outbound.handover.recovered-download.input`：绝对本地输入文件路径。
- `outbound.handover.recovered-download.input-sha256`：独立保管并从配置提供的原文件 SHA-256。

客户端只提交案件 ID 与有效 Bearer。请求中的路径、摘要、公钥、permit 或控制头不会替代服务端信任；恢复适配存在时，案件或身份不匹配直接拒绝，不回落普通路径。打开开关但缺少固定配置时启动失败。输入及所有引用材料保持在原恢复集与控制权威之外；路径重定向、别名、链接、未知字段、重复字段、尾数据及容量溢出拒绝。

固定输入的 purpose 为 `RECOVERED_TEMP_DOWNLOAD_INPUT`，保存案件 ID，以及各文件的绝对路径和独立摘要：完整 closed/native 原归档、原 FAULT_FENCE 命令、运维身份/批准、接收者准入、限定资格和本人会话。另绑定原双端编号快照、原验签公钥、scope COMMIT 根及独立锁 identity。它只包装已有原件，不是新的业务恢复包，不能从相邻文件的自报摘要建立可信根。

`RecoveryRecipientDownloadInput` 每次使用同一应用 DataSource 重载 retained-only 证明。缺少已保存的设备、资格、会话或审计直接拒绝；服务不会顺带重建它们。closed lease/journal、身份/仓库/设备锁序及原全表/raw/文件守恒保持，在读取事务内与 beforeCommit 再核固定输入、原件和实时身份。失败不返回可交付正文。

## 返回与兼容

成功响应保留 `no-store`、`nosniff` 和准确字节长度，不使用 Envelope 重编码。manifest 保存原 FAULT_FENCE、APPROVE、PREPARE 签名回执、准备摘要、原逐包清单及本人会话摘要。preparedTicket 中的 `temporaryQualification` 为限定恢复描述，其结构见契约文件；原 `sourceRecoveryEpoch` 与目标 `control.transportRecoveryEpoch` 分开。

recovery-package 返回原冻结正文逐 byte 不变的副本，保留原 ID、来源代次、映射和采集顺序。重复/丢响应重读和新 JVM 重载都不会签发新材料或登录。旧普通资格和票据不能因下载而获得激活资格；客户端需要显式支持限定恢复描述并耐久暂存后才能进入后续确认。

撤销 Bearer、仓库授权失效、指定身份/会话不符、权限撤销、固定输入损坏或并发 lease 冲突均拒绝。实际错误沿产品原 Envelope；恢复控制拒绝保持关闭。生产默认、签名、普通 TEMP/NORMAL 和 `localDeletionAllowed=false` 边界不变。

## 验证入口

专项入口为 `E2aReceivedDownloadHttpIT.downloadsOriginalThroughConfiguredProductRoutes`；必须在跨树分配后的全新隔离目标重建原来源，保全/失败库不得复用。它消费产品 controller/filter/configured loader，保存逐 byte 响应、响应头、完整读取前后快照、拒绝原件与新 JVM PID/退出码。额外会话只是明确标识的合成拒绝探针；正向使用本人真实 LOGIN 原 Bearer。

独立 Node 消费者核原正文、RS256 回执、原 DB 命令/响应摘要、配置引用、HTTP 原件和全表不变；工具独立性不代现场/独立人员。专项、普通回归、Android/Room/APK、模拟器和真机分别计证，实际范围见验收索引。版本事实只见主看板生成事实块。
