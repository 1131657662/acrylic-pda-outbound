# O02 TEMP首轮历史成功原文件离线核验（2026-10-20）

## 范围与决定

本切片补电脑工具对TEMP首轮历史成功原响应的只读核验。输入树D:/acrylic-pda-outbound、codex/development，输入HEAD为5be26893b313cf883bcebc38f070c8eafcde0a34。总目标active、未完成，不计完整O02、统一L2/L3、D18或总目标第8节通过。已批准筛选及治理批次沿原成果复用，不重复实施。

依总目标第3.1节自主授权裁定：已有TEMP成功原冻结正文、原HTTP响应及独立封存映射事实，但没有该成功批次的对应应急包。采用独立raw-file命令，消费现有原文件和包外预先核验的范围/公钥；不生成替代包、不将E03未成功冻结的TEMP包改称成功。相比扩大既有应急包命令或重跑原用例，这保留证据层级且不改变旧命令含义。

本轮证据根为`.local/goal-20261020-resume/o02-temp-receipt01/`。32条继承WIP清单及原SHA见input-checkpoint.json，原字节见inherited-bytes.zip，改前看板见board-before.md；本轮已有源码和拟编辑索引/README的原件另存continued-input-bytes.zip及continued-input-manifest.json。最终清单、命令和环境分别见final-manifest.json、commands.md、environment-closeout.json。

## 实现与信任边界

新增temporary_receipt.py、temporary_receipt_cli.py、合成测试夹具及三测试模块；README增加独立命令。复用既有严格JSON、固定RS256公钥及kid/iss/aud/签名校验；外层receipt须与签名内容逐类型一致。FORMAL命令、默认verify/cli/copy/取回均未改，不隐式升级它们的结论。

四个只读输入为原冻结正文、含code/data的原HTTP响应、包外原范围JSON、包外服务端公钥JSON。范围精确包含submissionId、payloadSha256、creatorDeviceId、clientOrderId、authorizationId、operatorId、warehouseId、orderId。UUID和摘要须规范，映射目标不得从待验回执抄取，公钥不得从待验输入自报建立信任。命令无网络/数据库操作，读取限额后核验，输出前重读四项确认未变；错误仅输出类型，不回显原内容。

绑定原正文SHA、来源、批次、原资格、人机、首轮、恢复代次、输入序号/高水位、保存记录对应及ACTIVE/VOID；未决输入、缺失保存记录、错映射、跨身份、多轮、交接或主管处置拒绝。分产品必须包含原计划中的零贡献项，以十进制整数克守恒，计划上限沿原首轮契约。仓库由包外认证封存事实提供，原冻结正文没有warehouse字段，本工具不自行认证上下文提供者、不重验完整授权/审计事件链、不查询后续映射或当前库存。

固定结果范围HISTORICAL_TEMP_FIRST_ROUND_SUCCESS_RECEIPT。businessReceiptVerified=true仅表示历史原回执与包外固定范围一致；packageIntegrityVerified=false、currentBusinessStateVerified=false、releaseAllowed=false、localDeletionAllowed=false始终保持。不能由此认定当前有效出库、应急包完整性、实物放行或删除资格。

## 实际验证

定向回归：

```text
C:/Python314/python.exe -m unittest tools.pda_rescue.test_temporary_receipt tools.pda_rescue.test_temporary_receipt_boundaries tools.pda_rescue.test_temporary_receipt_cli tools.pda_rescue.test_receipt tools.pda_rescue.test_receipt_cli tools.pda_rescue.test_receipt_disposition -v
```

tests-final05.log：66项、0失败、0错误、0跳过，退出0。其中TEMP36项、既有FORMAL30项；包含实际CLI子进程、错钥/重复JSON/超限/无敏感回显/四输入中途变化、原范围/映射/签名、零贡献/VOID、序号缺口/缺保存/未决输入/计划超限。合成RSA与UUID仅作测试，不冒充原身份。未重跑旧54向量矩阵，不冒充Python全量、Android或服务端回归。

最终实际CLI：

```text
C:/Python314/python.exe -m tools.pda_rescue.temporary_receipt_cli .local/goal-20261020-resume/o02-temp-receipt01/original-payload.json .local/e2a-cross-20260925-743/source-success-response.bin --context .local/goal-20261020-resume/o02-temp-receipt01/external-context.json --receipt-key .local/goal-20261020-resume/o02-temp-receipt01/receipt-public-key.json
```

actual-cli-final02.exit为0。封存历史run743来自模拟器原产品/隔离HTTP成功用例，2条ACTIVE、产品003/571、300500+301000=601500克。本轮只消费封存字节，没有重跑历史业务、重注册设备或重签回执。原报告为[run743](GOAL_E06_D12_TEMP_REPOSITORY_RUN743_2026-10-18.md)，历史75项结果只作原范围引用，不计本轮新增测试。

原正文从android-original.json中原payload字符串以UTF-8逐字导出，SHA为0c3db9aed6de5bf8eecf921876e2637e687a321c1555b838b50ffbf98d13f4ca；原HTTP响应SHA为f1e5e4f4a07cb19925f57a0fabb4af4304100c783e4a1d703d51b489dfac3b66。独立范围从source-before-product.json的submission→submission_scope→temporary_authorization及temporary_source_mapping提取，核人机仓和映射一致；不是从回执复制目标。服务端公钥仅提取原private/temp-repository-input.json的signingKey，并与原private/server-public.der逐字比较；不复制或输出其余token/私钥。DER摘要为b33b5eac56fa56c3551b94814860f4d37efd7ee3c6521240d6460d44e5ad200f。

5项封存输入前后SHA不变；31条非看板继承路径逐SHA保护，看板只作授权断点更新。作者、自审、命令操作为当前助手，Python cryptography是工具消费者，不冒充独立人员审查。6个新代码文件均不超过200行，函数体不超过30行，详review-final.json。

## 失败与未验

tests-red01是实现模块未建立的初始红灯；准备阶段曾把16字节UUID当UTF-8解码失败，恢复上下文时另有表结构读取TypeError，均为只读准备/探查错误，不改原件。prepare_originals.py修正后prepare02退出0，派生文件仅写本轮新路径。初始准备错误的原终端信息只作诊断记录，不声称另存了不存在的原日志。

tests-final02因手工将既有test_receipt_disposition多写一个s而导入失败，原日志/退出码保留；正确集合tests-final03为62项通过。自审对照原服务端首轮规则发现缺失保存记录、序号缺口、未决输入、计划上限四处误接受，review-red04实际4失败保留；补检查后tests-final05为66项通过，最终原CLI在修正代码复验。失败不改写为通过。

未验：对应成功应急包完整性、当前业务终态/净值/后续映射、TEMP续作/处置、原身份实体PDA、外置介质、完整UI/实体键、O02 Android主机SQLite CANTOPEN修复及最终候选组合。本轮未构建APK、启动模拟器/业务HTTP/MySQL、执行设备业务、连接原库/现网或使用正式签名。旧APK/tar/签名原件及继承源码保持。

本轮清理释放0字节；小型日志保留，未创建隔离库或大构建产物，既有自动清理拒绝不换包装重试。下一有界动作先读取O02现时结果/净值查询的现有消费者及可信输入，分清历史回执与当前结果；缺材料则只暂缓对应输入，不重跑CANTOPEN失败类或已完成字号/SAF。生产决定、原库/现网、正式签名、main、部署和Release持续关闭，localDeletionAllowed=false。开发备份提交不计整体验收。
