# E05/O03：受控原件中转切片验收

输入树D:/acrylic-pda-outbound、codex/development，基线57d6302f5e005c614c1e84d43e25ecc8fd4a1b74，唯一写者本聊天01a1341e-abd2-75f3-a023-1b9cf6c64c00。版本事实仅见看板生成块；最终提交与实际远端见.local/goal-20261013/o03-delivery-01。沿[设计与退出标准](GOAL_E05_O03_DECISIONS_2026-10-13.md)完成本切片，不计D11–D15或D18整体完成。

## 行为与边界

电脑经办人以自己的Web Session/CSRF和同仓rescue:relay权限接收逐字原包，分别审计原采集人和经办人。服务端有界核验ZIP、UTF-8、清单签名、登记钥、原人/机/仓/冻结正文及恢复代次；先查原ID，只有可信冻结批次进入已有提交事务。未冻结、未知登记钥和独立来源只保全证据；TEMP仍待独立审核。已撤回/重发旧领取拒绝，原人/设备停用、撤权与恢复围栏保持。

原结果文件绑定原清单、ZIP摘要、正文、人机仓和代次，保留原业务回执，追加签名传递文件。原Android只接受本机原导出清单及预置可信根，核原业务签名后通过已有消费者保存；不覆盖不同终态。生产六入口默认关闭，localDeletionAllowed=false，不授新扫码、库存释放、业务归档或实物放行权。

## 实际验收

证据根均为.local/goal-20261013；命令、输入哈希、日志/XML及退出码保存在各目录。

| 证据目录 | 当次结果及适用范围 |
| --- | --- |
| o03-unit-06 | 42项定向服务测试与bootJar通过；严格原包、身份绑定、契约字段及规范请求摘要。原编译失败/并行Gradle输出锁失败保留，不计当次通过 |
| o03-http-06 / 隔离1504 | 一个实际HTTP/MySQL组合IT通过：新FORMAL中转、原PDA/PC并发、同键/异文、末SQL回滚、TEMP待审核、未知钥/未冻结、跨仓/Bearer、恢复代次、锁后撤权、撤回重发旧资格；并验证中转新结果可导出、最新净值/容量/原结果及扣账/更正/撤销/新周期组合 |
| o03-web-contract-01 | 前端契约检查通过；本切片电脑入口为Python工具，不宣称新增Web中转页面 |
| o03-python-final02 | 20项耐久中转与原件核验检查通过；向量明确沿o03-python-03原合成输入，不称本轮重新生成或原设备事实。final01漏传目录变量、20项运行器错误保留 |
| o03-python-http-loss01 | 真实服务三处响应丢失、四个独立Python进程，原receive/process键及ZIP不变；结果未完整消费前不发布成功文件，最终原结果逐字一致、固定包外公钥验证及13表checksum守恒。来源是既有合成成功批次 |
| o03-android-02 / o03-android-regression-01 | 10项验签/清单定向与最终78项rescue/relay/submission受影响回归分别通过，零失败/error/skip；错误根、异人机仓单/正文摘要、旧结果和本机原包损坏反例。最终回归生产输入无漂移 |
| o03-product-build-01 / o03-frozen-build-01 | 产品APK、instrumentation APK及lint通过；后者只新增未上传原批次仪表测试并构建。APK哈希见设备各阶段apk-inputs.json及交付目录，不用模拟器通过冒充真机 |
| o03-native-environment-1502 | 原非空FORMAL资料副本实际恢复迁移，14张表旧列原字节前后守恒。原注册钥/冻结成功回执保留，合成中转经办身份单独标记 |
| o03-native-pc-01 / native-export-01 / native-import-01 / native-restart-01 | 六默认关闭入口拒绝；原模拟器Keystore产品导出已成功批次、电脑独立登录/登记钥/固定服务公钥核验、实际OpenDocument返还消费及强停新进程回读。原业务回执逐字不变，不作为新提交正向 |
| o03-native-frozen-setup-02 / o03-frozen-capture-01 / frozen-export-01 / frozen-export-files-01 / frozen-pc-01 | 原库存行保持后追加新样本，实际原机领取/下载/扫码/冻结但不上传；产品离线包经电脑核验并走原提交链首次SUCCEEDED，形成2包605000整数克及唯一结果。原登记设备钥沿用，未更换Keystore、未复制PDA Bearer |
| o03-frozen-import-01 / frozen-restart-01 | 实际系统OpenDocument选择电脑返还文件，原机验签消费首次成功回执；强停后新进程仍显示原成功并保持逐字冻结正文/原结果 |
| o03-native-frozen-jvm-03 / frozen-pc-canonical-01 / native-canonical-jvm-01 | 自审发现Map.of迭代顺序导致跨JVM原键摘要变义，改为固定规范JSON；仅对已知两类原命令形状有界识别旧摘要，拒绝任一值变化，原记录不重写。旧原键在新JVM复用及11表守恒通过；规范键再经另一新JVM复用、12表含请求checksum守恒通过，签名文件逐字保持 |

## 自审、失败与保存

作者自审覆盖原包上限/路径/重复成员/CRC与原摘要、登记钥与包内钥信任分离、人员/设备锁及锁后授权复核、原资格撤回、已有提交链原子性、追加结果/审计、电脑目录耐久键、Android本机清单/预置根/终态保护与删除边界。契约早期错误Envelope引用及字段结构已经修正，并由当次契约测试和检查复验。没有独立人员审查，电脑工具/固定钥核验属于独立消费者证据。

HTTP01 RequestIdFilter夹具、unit03编译、Python02缺向量、native恢复夹具误用updated_at、新样本setup01误用条码、HTTP05缺XLSX头部断言及跨JVM旧摘要失败均保留；后续通过目录各自固定输入，不抹除原失败。1504收尾脚本缺admin.cnf及一次PowerShell短选项解析错误另存诊断，核库UUID/datadir后认证停机；dump/hash及失败日志保留。1502原非空副本与失败1503也已dump保全停机。

全脏原字节/hash/diff在o03-resume-04及交付前快照；原Android数据库原字节在o03-native-evidence-final01。继承Android/UI、旧已取消换机、AGENTS外部追加、历史/工具/本机请求及README归档入口不混提交；交付明确路径与受影响输入映射见o03-delivery-01。新增产物及子进程TEMP/TMP/Java临时目录均在D盘；本切片不清理原件、失败库、日志、AVD或Keystore。磁盘余量以交付目录disks.json为准。

## 剩余范围

O03本切片已达到记录的开发退出标准；原物理PDA、真实网络/介质、原生Excel及生产审批仍按C档保留。E06独立sku采集、E07恢复/外置副本、E08留存/UI遗漏、E09最终候选及全部未验D/O/U继续。U01/U02和旧D04/恢复暂缓计数继承，不恢复已取消换机进度承接；总目标保持active，未完成。
