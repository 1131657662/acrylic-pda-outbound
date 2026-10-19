# E08/D17 下一独立消费者探测（2026-10-18）

本切片在 `D:/acrylic-pda-outbound`、`codex/development`、HEAD
`c5c8f9e5851746f71aacf8a856e3cf23bf365146` 上执行。目标是复用已保存的原件字节，验证
原件中转的独立回执消费与 fail-closed 回归边界；没有产品源码、数据库、服务、设备或
生产配置改动。

## 输入与命令

- 原件目录：`.local/goal-20261013/e06-export-computer-03/verified-original/`。
  该目录只读消费，未覆盖；其中 `independent-source.json`、原 SKU 字节、原清单、公钥和
  原事件文件均保持原 SHA。
- 原始 `.pdarescue`：`.local/goal-20261013/e06-export-computer-03/actual-saf-copy.pdarescue`。
  文件头为 `PDARESCUE1`，是加密外置包，不能直接作为 `RelayArchive.read` 的明文 ZIP。
- 派生测试 ZIP：`.local/goal-20261018-resume/next-independent-probe01/derived-relay-archive.zip`。
  仅从上述目录复制允许成员后重新打包，未改变成员字节；SHA-256
  `27F014C186DB606BDE4600DEDB15835ECAE644A6781A4309505316A5A179DE26`。
- 明文 ZIP 消费命令：
  `E06_REVIEW_ORIGINALS=<verified-original> E06_REVIEW_ARCHIVE=<derived-relay-archive.zip> .\gradlew.bat -p server test --tests com.acrylic.outbound.relay.RelayIndependentOriginalTest --tests com.acrylic.outbound.relay.RelayIndependentCollectionTest --no-daemon --console=plain`
- 完整 closure 复验命令改用已保存的同批输入 `.local/goal-20261013/e06-closure-computer-02/actual-original.zip`，其 SHA-256 为
  `1577709AFAC180638244EA37623E13D63A57A2F73C520DF595A7DF6BA9C0C8E6`；同批
  `verified-original/independent-state.json` SHA-256 为
  `A2FA48618E00085B60EBD08F2C0DA2F29895B0B99858B1CEA618DEDA1E8459AB`。

## 结果

- `RelayIndependentOriginalTest`：14 项通过，包含原身份/SKU 字节守恒、FORMAL/秘密字段/复用材料/缺成员反例，以及明文 ZIP 的签名原件消费。
- 使用同批 closure 输入复验后，`RelayIndependentClosureTest` 3 项、`RelayIndependentCollectionTest` 6 项全部通过；覆盖完整 collection prefix、缺审计前缀、变更计划、原 SKU/重量/原输入及重复计划反例。
- Android `RelayReceiptVerifierTest`：4 项通过，覆盖登记信任根、旧/异源终态、错误根和未签名变更拒绝。
- 初始 Android 组合回归另有 6 项已知 `SQLiteCantOpenDatabaseException`（`TemporaryDetailsReaderTest`、`RecoveredHandoverCardReaderTest`）；与既有三次同根因一致，按规则暂缓，不再机械重跑。完整日志保存在 `android-host-tests.log`。

## 失败保全与范围

- 直接把加密 `.pdarescue` 传给明文 ZIP 测试导致 `原ZIP格式无效`；失败日志
  `server-original-tests.log` 保留。该失败是输入形态错配，不是 `RelayArchive` 产品回归。
- 使用没有 closure 元数据的导出目录派生 ZIP 运行 `RelayIndependentClosureTest`，3 项按预期暴露
  缺少 closure 的输入边界；日志 `server-derived-tests.log` 保留。未生成合成 closure。
- 运行目录 `.local/goal-20261018-resume/next-independent-probe01/` 保留命令、退出码、派生成员清单、日志、测试 XML、环境快照和前置工作树快照。
- 本切片不计 O02/O03 整体、D17、E06 L2、D18 L3、第8节闸门或总目标完成；没有真实 PDA/现场身份、外置保管回执、真实服务组合或 closure 输入。`localDeletionAllowed=false`、取消的换机/确认/激活链及全部生产默认关闭边界保持。

