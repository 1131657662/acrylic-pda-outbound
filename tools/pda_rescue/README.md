# PDA应急包电脑核验

本目录提供离线核验/表格转换，以及另行明确执行的受控原件中转。离线`cli`不连接服务器；中转`relay`使用电脑经办人自己的网页登录和专用仓库权限，只处理可信原设备已冻结批次。实物放行仍按原流程核验。Android从“数据”或原单明细进入应急导出；范围验收与未验项见[开发验收索引](../../docs/DEVELOPMENT_PROGRESS.md)。

断网前，原操作员应在联网状态通过“联网预置或撤销本人救援凭据”预置本地只读凭据。跨人救援使用原操作员申请、主管审批的指定原人/机/仓库/订单材料，由获授权救援人联网核验并预置；材料缺失不能临时自填身份。已预置后可在注销、资格到期或重启后核验原件，只读资格不授扫码或放行权；有限期授权仍须可信核时，离线不能假称已获知服务端新撤权。

核验只读范围→选原单→读取一致快照→用另行保管的口令生成加密包→选择保存目标。页面区分本机包生成、目标写入回读与电脑副本核验；进程中断后重新核验身份并恢复原私有包，重新复制/回读。半文件、未完成日志或旧目标的成功不能替代新目标验证。正式/临时单未冻结也可导出，存在原冻结批次时保留原正文/回执，不补造批次、不修改本机业务状态。

独立采集原任务也可从原任务页面进入只读导出。包保留原GB18030资料、启用决定、固定计划、原输入/SCAN/VOID与审计；只输出原采集凭据ID、摘要和安全字段，采集token不随包传递。已结束包另核完整原前缀和原设备结束签名；旧未结束包仍只作证据。电脑核验登记设备来源和原件一致性后，仍明确显示待主管对账、服务端原采集授权尚未核验。主管须另行核对原授权和实物并人工关联；独立包不能导入正式/临时中转回执，也不能自动提交、记账或放行。

先将完整应急包复制到受控电脑，保留原文件；解密口令由救援人员另行保管，不能随介质或聊天传递。电脑需要Python和`cryptography`；Excel转换还需要既有Artifact Tool Node运行库、`openpyxl`和Pillow。项目开发使用已安装运行库，不需要重新安装副本。

从项目根运行（示例路径均为占位）：

```powershell
python -m tools.pda_rescue.cli D:/受控资料/原包.pdarescue --trust D:/受控资料/登记信任.json --require-trusted --out D:/受控资料/核验副本 --xlsx
```

Android可为本次原订单生成独立解密材料。可将`--secret-file D:/受控资料/解密材料.json`加到同一离线命令，命令仍先用原包内密钥验签、执行原事实校验和包外登记信任，再核对材料与`order.json`原字节摘要及来源/订单/设备/操作员/仓库范围。该材料只提供解密口令，不证明来源、身份或业务授权；材料与加密包分开保管，避免放入命令行、聊天或日志。未指定时仍由终端隐藏提示手工输入。该选项只用于离线核验命令，不用于原件中转、回执导入或其他命令。

工具单独提示输入应急包口令。输出目录必须是新路径。`--xlsx`省略时只写核验原件和报告；省略`--out`时只核验，不写解密后的业务资料。转换失败会保留`pda-rescue-partial-*`目录和日志，不把它命名为完整副本，不能将半成品当作成功结果。

`--trust`必须由包外已核验的设备登记材料提供，不得从本包抄公钥并当作可信来源。结构如下，标识和公钥均须替换为真实登记范围：

```json
{"设备标识":{"publicKeyDerBase64":"登记公钥DER的Base64","operatorIds":["原操作员标识"],"warehouseIds":["获授权仓库标识"]}}
```

没有可信登记材料时可以省略信任参数做完整性核验，但报告明确显示来源未核验；这不能证明是谁、哪台登记设备采集，也不能据此记账。报告中的`businessReceiptVerified=false`表示本工具未验证业务回执，不代表回执失败或出库成功。

### FORMAL历史成功原回执的可选核验

若包内保留FORMAL原冻结正文与成功回执，可另行执行以下离线命令：

```powershell
python -m tools.pda_rescue.receipt_cli D:/受控资料/原包.pdarescue --trust D:/受控资料/登记信任.json --receipt-key D:/受控资料/服务端可信公钥.json
```

服务端公钥必须来自包外预先核验的可信材料，JSON仅含`keyId`、`algorithm`（固定`RS256`）、`publicKey`（X.509 DER公钥的Base64）；不接受包内自报公钥建立信任。旧历史钥应从受控只读keyring取得，不能用新钥替换原回执。命令只输出核验报告，不写业务记录；口令由终端另行提示。

成功报告的`businessReceiptVerified=true`仅表示封存FORMAL成功原回执签名、原冻结字节与原人机仓来源、批次、有效序号、分产品数量/整数克一致，范围固定为`HISTORICAL_FORMAL_SUCCESS_RECEIPT`。它不查询后续更正、撤回、净值或现时终态；`currentBusinessStateVerified=false`、`releaseAllowed=false`、`localDeletionAllowed=false`始终保持。TEMP/独立来源、非成功回执不在此命令覆盖范围。原`cli`、加密副本和取回命令仍报告业务回执未核验，不被本次可选结果隐式升级。

### TEMP首轮历史成功原文件的可选核验

另有首轮TEMP原冻结正文和含`code/data`的原HTTP成功响应时，可使用独立命令：

```powershell
python -m tools.pda_rescue.temporary_receipt_cli D:/受控资料/原冻结正文.json D:/受控资料/原HTTP响应.bin --context D:/受控资料/包外原范围.json --receipt-key D:/受控资料/服务端可信公钥.json
```

`--context`必须在核验前从包外可信原材料确定，仅含`submissionId`、`payloadSha256`、`creatorDeviceId`、`clientOrderId`、`authorizationId`、`operatorId`、`warehouseId`和`orderId`。标识须为规范UUID，摘要为原正文未改写字节的SHA-256。`orderId`须来自另行核验的原TEMP映射目标，不得从待验回执抄取。仓库来自包外认证范围，原正文没有仓库字段；本命令不能自行认证上下文的提供者或查询映射。公钥格式及包外信任要求同上，不提供密码、token或私钥。

命令只读四项输入并输出报告，结束前复读确认字节未变。核验固定签名、原范围、无缺口输入和终态、保存记录对应、ACTIVE/VOID及含零贡献计划产品的数量/整数克。只接受首轮成功；不接受续作、交接或主管处置输入。结果范围固定为`HISTORICAL_TEMP_FIRST_ROUND_SUCCESS_RECEIPT`，`businessReceiptVerified=true`只证明该历史原回执与包外固定范围一致；`packageIntegrityVerified=false`、`currentBusinessStateVerified=false`、`releaseAllowed=false`、`localDeletionAllowed=false`。该命令不验证应急包或原授权完整内容，不恢复取消链，也不改变其他核验/中转命令的结论。

Excel提供核对汇总、原扫码顺序、产品核对、未决/失败输入及原审计。条码、产品代码、原序号和时间保持文本；重量以整数克核对，超出Excel精确整数范围时保留十进制文本。公式前缀始终作为文本，控制字符显示转义，原NDJSON保留原文。原ACTIVE行及主管处置原件同时保留，已处置贡献不算有效装载；没有冻结正文时不会补造提交批次。

PNG只展示前16行，长文本可截显示；完整值在Excel和原件中。正常、零有效、空记录及已处置样例均使用合成输入验证。真实原设备、原生Excel/打印、USB/MTP/OTG、异地介质仍需对应现场验收。

开发配置可通过`PDA_RESCUE_NODE`指定已安装Node程序，通过`PDA_RESCUE_NODE_MODULES`指定既有依赖目录，`PDA_RESCUE_FONT`指定预览字体。环境变量不包含救援或解密凭据。

## 受控原件中转

生产默认关闭。经授权启用后，具备同仓`rescue:relay`权限的经办人可从项目根运行：

```powershell
python -m tools.pda_rescue.relay D:/受控资料/原包.pdarescue --server https://服务源 --warehouse 仓库UUID --journal D:/受控资料/本次中转 --process-frozen
```

口令和网页登录密码由终端分别提示，不写入中转目录，不复制PDA Bearer。省略`--process-frozen`只接收原件并查询状态；未冻结、独立来源或未知登记钥不能创建正式提交。服务端重新核验登记公钥、原人/机/仓及资格和恢复代次，先查原提交ID，走既有提交事务；已撤回旧资格拒绝，TEMP仍须独立审核。

独立采集原包使用`--review-independent`接收并核原授权，经办人须同时有同仓`rescue:relay`与`temporary_order:confirm`权限。服务器从原凭据ID/摘要、人机仓、原版本/代次/期限及固定签发公钥核历史签发；缺原件、摘要冲突、签名无效或历史钥缺失明确保持未核验。历史授权核验、现时主体状态和完整结束边界分开显示，过期或后续撤权不消灭原件。只有原登记设备可信且完整原前缀、计划、逐包SKU、可信采样与唯一末事件的结束签名全部通过，`completeBoundaryVerified`才为true；未结束原件为false。审阅不授权新增采集，实物核对和人工关联继续待办；始终不创建正式提交、库存占用、记账、放行或删除。该选项与`--process-frozen`互斥，重试复用原包和同一耐久目录。

原包、接收键和处理键先耐久保存；响应丢失时复用原包和同一`--journal`目录重试。原键不能换包或改服务源/仓库，不把接收成功当出库成功。成功后返回的`.pda-receipt.json`通过Android原单应急页“导入电脑返还的原批次签名回执”选择，按本机原清单、正文、人机仓、预置可信根与原业务回执严格验签；冲突文件保全，不能覆盖已有不同终态。`localDeletionAllowed=false`，电脑或原机均不因此获得删除或实物放行权限。

电脑端在发布返还文件前，严格比较处理响应与下载文件的全部JSON字段和值，含结果ID、业务内容、签名及布尔类型；排版差异允许，原下载字节原样保存。若不一致，下载内容保存为耐久目录中的`result-conflict-<SHA256>.bin`，不发布完整`.pda-receipt.json`，不将该冲突推进为已保存结果，重试继续使用原处理键。冲突文件不能作为成功回执使用。该检查只证明同次响应与下载一致，不独立验证服务端签名或现时净结果；原机仍须使用预置可信钥验签。
