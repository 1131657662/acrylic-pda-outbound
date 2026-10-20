# O02 FORMAL历史成功原回执离线核验（2026-10-20）

## 范围与输入

本切片补电脑工具对FORMAL成功原回执的可选离线核验。输入树为D:/acrylic-pda-outbound、codex/development，输入HEAD为aac19af56b9a6184224cb21e3065d606dd46c5d9。总目标active、未完成，不将本切片记为O02整体、D18或第8节通过。

已批准的“全部/有效/已撤销/主管保留”筛选项按磁盘既有实现及原报告复用，本轮不重做字号、SAF或默认关闭用例。FORMAL/TEMP真实SAF已有历史证据；O02主机SQLite CANTOPEN没有新运行器假设，继续暂缓，不以本次电脑端成功替代失败修复。

全部新证据位于`.local/goal-20261020-resume/o02-native-file-consumer01/`：最小范围在scope.md；继承32路径及SHA在input-checkpoint.json，原字节在inherited-bytes.zip；拟编辑原件及索引分别保存在original-bytes.zip、indices-before.zip。输入和审阅见original-consumer-inputs.json、edit-input-manifest.json、review.json。

## 实现与安全边界

新增receipt_signature.py、receipt.py、receipt_cli.py及合成夹具/三测试模块；README增加可选命令。旧verify/cli/copy/取回默认逻辑未改，仍报告businessReceiptVerified=false。命令不连接网络、不写数据库、不修改包或原件。

可选命令要求包外预先核验的原登记范围，以及包外RS256服务端公钥JSON（algorithm/keyId/publicKey）。只接受固定算法、严格无填充Base64URL、无重复JSON属性和固定iss/aud/claims/header字段，不进行包内自报钥或网络钥发现。RSA至少2048位；响应与签名receipt逐JSON类型一致，避免false与0混同。

验签后绑定原冻结字节SHA、原批次/订单/设备/轮次/恢复代次/来源、原人仓捕获范围、逐包身份/出库周期/序号、主管处置原件、有效数量及分产品整数克。原VOID和处置行保留但不重复计有效贡献；签名零贡献产品沿既有Android语义保留。不能凭回执开放扫码、释放占用或本地删除。

报告固定verificationScope=HISTORICAL_FORMAL_SUCCESS_RECEIPT；businessReceiptVerified=true仅证明封存成功原回执。currentBusinessStateVerified=false、releaseAllowed=false、localDeletionAllowed=false保持；不查询后续更正、撤回、最新净值或现时终态。TEMP/INDEPENDENT、非成功回执及不能匹配原人仓范围的输入不在本切片覆盖内。

## 当次验证

最终定向命令（临时目录仅在本任务子进程指向D盘）：

```text
C:/Python314/python.exe -m unittest tools.pda_rescue.test_receipt tools.pda_rescue.test_receipt_cli tools.pda_rescue.test_receipt_disposition -v
```

receipt-tests-final03.log：30项、0失败、0错误、0跳过，退出0。覆盖合成RSA/EC正向、原默认未核验、缺登记/错钥/弱钥/算法及用途错配、重复属性/签名编码/响应篡改、跨上下文/人仓、逐包身份/周期、错误产品/重复产品、原处置正向及篡改、零贡献产品、口令错误与执行中原件变化拒绝。合成钥按测试独立生成，不使用原设备私钥。

实际CLI在最终产品代码上消费历史封存FORMAL模拟器原包：

```text
C:/Python314/python.exe -m tools.pda_rescue.receipt_cli .local/goal-20261011/e03-formal-pc-02/target.pdarescue --trust .local/goal-20261011/e03-formal-environment-1383/external-trust.json --receipt-key .local/goal-20261020-resume/o02-native-file-consumer01/receipt-public-key.json
```

Windows真实PTY另行输入既有合成包口令，original-cli-final02-exit.txt为0。该原包来自历史已登记模拟器Keystore和当时真实产品/隔离服务成功批次，本轮没有重注册、重签或生成替代原包。登记信任沿历史隔离数据库外部查询材料；服务端公钥来自原`.local/it-keys/server-public.der`，keyId依OfflineSigner对包外DER的Base64摘要规则推导，未从待验包抄取建立信任。

original-cli-final02.json报告：3 captures、3 scans、1 VOID、2有效、12 events、601500整数克，历史成功原回执核验true；现时状态、放行和删除均false。加密原包SHA为5aa6a54afb69c995db35b012016f85bc7a06071d12cbdcd06a4eee1ffe0adefd；原冻结正文SHA为271f53a819dffe5cdaf6ca1baa80abee9256ae32a7d47d89def896fdeff5c3c2；原回执SHA为7f7596d4e253ce7e0bbea4f4296221c4b09a3065342a2c43a5b2b791c566865e。全部九项原消费输入及32继承路径在文档收口前逐SHA保持。

作者、自审和命令操作均为当前助手；Python cryptography为独立工具消费者，不冒充独立人员审查。原冻结字节和回执还与历史before文件逐字核对；输出哈希、实际环境与提交回执见本地最终清单。

## 失败、未验与下一步

初始红灯因实现模块尚不存在而加载失败，保留red-test.log，不计业务测试。首个非交互管道CLI尝试被Windows getpass.getwch等待控制台输入阻塞；核实命令行后只停止本次子进程，退出和日志保留。随后明确使用真实PTY输入，最终退出0。未修改口令机制来绕过输入，不将最初管道失败改成通过。

自审补全包身份/出库周期、捕获人仓、签名逐类型比较及密文/明文哈希区分，最终测试及实际CLI均针对修正代码执行。后续仅增加测试覆盖，不再改动产品行为。未重跑已有54项只读向量矩阵；本次新增受影响模块有完整定向覆盖，不冒充全量Python/Android/服务端回归。

本轮未构建APK、启动模拟器/业务HTTP/MySQL、操作USB业务、连接原库/现网或使用正式签名。未触碰原资料、Keystore、原签名回执、旧tar/APK及继承源码。清理释放0字节；小型证据保留，历史自动清理拒绝不换方式重试。

仍未验：TEMP成功原回执电脑核验、现时业务结果/净值查询、原身份实体PDA、外置介质可取回、完整页面/实体键、O02 Android主机失败修复及实际最终候选组合。下一有界动作优先核对TEMP成功回执已有原件与映射目标，再补仅历史原回执的电脑消费者；不借此恢复已取消换机或接收者确认链。

生产开关、七类生产决定、TEMP生产归档、原库/现网、正式签名、main、部署和Release保持关闭，localDeletionAllowed=false。开发备份提交不计整体验收通过。
