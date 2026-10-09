# D07/R09 原TEMP准备与ACK采集

本步收口首轮原TEMP资格下的五分片、原签名token/manifest/ACK采集与离线独立pin消费。版本只读[看板事实块](../ORCHESTRATION_STATUS.md)，非D07/R09整体或实际业务恢复完成。

## 输入与实现

输入5500a78d37a5a6fbffe05c9d78475f0d14959b1c、codex/development、D:/acrylic-pda-outbound。原占用观察已提交推送，original-occupancy-postcommit-06保存26dirty/22继承保护和环境；唯一活动写者本聊天，其他项目聊天notLoaded，无代理。新增三个产品Java、四个测试Java，调整三个共享测试夹具以支持实际新库ID；无迁移/契约/客户端/HTTP/生产接线变化。

RecoveryTemporaryPreparationCapture显式NORMAL原实例/schema/epoch、独立只读repeatable-read事务和实际UTC时间；按原qualification读temporary_authorization的原仓/用户/设备/epoch、signed_token、manifest_text/摘要/signed_manifest、五份compressed原bytes和temporary_preparation_ack原signed_ack/时间/command_sha256。source中的scope/submission/水位是独立expected输入，本步不从资格表生成完整来源或历史权限事实。

原ACK command必须显式提供；按TemporaryCommands.Ack声明順序和JsonText原digest核对。数据库只保存毫秒activated_at，不能反推原纳秒command，缺失/错摘要拒绝。manifest/token/签名字符串及gzip bytes保持；外层origin和ACK response包装由原字段序列化，不宣称取得数据库未保存的HTTP原响应整字节。无重新签发或重新gzip。

metadata canonical与origin原材料分离保存，库外显式独立pin读取；恢复消费者核原实例/源/expected水位/notAfter/origin原hash，与同一source pin的origin精确bytes一致后，复用既有原准备/基线/TemporaryActivation消费者，核三签名、五分片、原正文与ACK窗口，结束重读控制/两份文件。返回不赋ordinaryWrites或删除权，localDeletionAllowed=false。

有界接口：原command≤16384字符，manifest≤1048576字符，各token/signed_ACK≤131072字符；每compressed≤16777216字节且五份累计≤20971520字节，最终origin≤33554432字节。超界拒绝，不代实际规模归档或完整R09。原观察时点不是commit边界或连续权限窗口。

## 验证与保全

- 定向01：71/0/0/0、4XML、exit0/sourceUnchanged，原准备/ACK/native组合和新独立pin读；随后新增repin缺分片/坏分片/缺ACK/坏ACK反例在最终受影响回归执行。
- 分配1160被保守跨树目录守卫拒绝：既有`.local/fence-11608541087698854356`匹配编号子串。作者随后调用运行器也因已分配目录不存在立即失败；未provision/启动MySQL或运行业务测试，原目录保持，不把编号拒绝计成数据库失败或验证通过。重新33树核对分配1161。
- 新1161真实MySQL integrationTest：1/0/0/0、1XML、exit0/sourceUnchanged。非空原表/文件、实际UTC实例读取、五原压缩分片和三原签名保持，采集/错实例/原command降毫秒/嵌入事务/恢复模式反例前后全部表和文件一致。离线原pin与完整原准备/ACK消费者通过；合成原ID/开发RSA，不代原设备或实际恢复。
- 独立Node1161-01：exit0、8输入hash不变，独立核metadata canonical末LF/pin/origin摘要、原token/manifest/ACK三RSA、原纳秒command声明顺序hash与毫秒重构摘要不等、五份原compressed/gzip/raw hash及数据库原逐列、采集前/后/最终全表/文件一致。不接DB/私钥，工具消费者不代独立人员或现场。
- 最终受影响普通server回归02：3539/0/0/1、168XML、exit0/sourceUnchanged，选择recovery/temporary/evidence/devices/pairing，非全部普通test、非IT；符号链接skip未执行。新观察测试15项实际执行；1159原占用及1161实际原字节未机械重跑，不外推相邻未验项。

证据：`.local/d01-d18-20261008/r09-temp-preparation-capture-targeted-01/`、`r09-temp-preparation-mysql-1161/`、`r09-temp-preparation-capture-regression-02/`；原库/字节在`.local/e2a-cross-20260925-1161/`。分配/运行器副本、命令/输入hash/原XML/log/dump/retention在`.local/d01-d18-20261009/temp-preparation-mysql-1161/`；独立脚本verify-temp-preparation.cjs、command回执与temp-preparation-consumer-1161-01分列。设计/作者自审在同日.local；实际运行及独立消费不互代。

1161 dump SHA256：1AB6B0F2F0F4CD8E610CF68AE0DE30774CBF0A000905FBEDD480215E38A3885F；独立重算、认证停机/原PID退出、19330/19434监听空，原datadir/binlog/log/XML保持，既有3306/13316/ADB不操作。新增产物与TEMP/Java临时继续D盘，本机资料未异地备份。

## 尚未完成与下一动作

原TEMP准备/ACK可取原字段接口不补1151原FORMAL准备缺失，也不证明权限连续窗口。原SOURCE_RESERVED/未收正文/无canonical占用、FORMAL原准备/ACK采集、原续期撤销、高轮/混合/跨源仍缺；不能用观察或恢复库当前权限补历史，operation_event事务内时间不当commit时点。

继续未收正文来源占用与连续权限依据；然后全新隔离实际截点恢复、原ID/正文/序号/回执闭事务重建、D07主管围栏/接收激活/最终提交/尾SQL回滚/重启。只读前置不代实际恢复，仍有可推进开发就继续。D06/D07整体、D08–D18及第8节未完成，D04/D05政策缺口、原资料/旧BCD/526/历史失败/恢复限制和C01–C08保留。

不重跑1150/1151/UI-06或1100–1102，不复用失败库，不删除原件；生产默认和签名/七类生产决定关闭、localDeletionAllowed=false，不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
