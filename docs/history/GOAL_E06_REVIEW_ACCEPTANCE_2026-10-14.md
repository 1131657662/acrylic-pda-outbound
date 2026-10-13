# E06独立原件接收与主管核原授权

本切片沿既有受控中转接收实际独立采集原件，新增主管只读历史授权审阅和电脑消费者。设计见[设计记录](GOAL_E06_REVIEW_DESIGN_2026-10-14.md)。服务端核原凭据ID、摘要、人机仓、版本/代次/期限及当前或配置的历史签发公钥；设备登记信任、历史授权和现时主体状态分别显示。过期及后续撤权不消灭历史原件，不返回采集token，不授新采集权，不补造正式submission。

工作树D:/acrylic-pda-outbound、codex/development；输入870f90e2089b1271ed79b7536717fc40e54f38cb。版本事实见看板生成块；本项无Android或数据库迁移改动。生产默认关闭及localDeletionAllowed=false保持；不合main、不接原库/现网、不发布部署。

## 输入和实际验证

证据根为.local/goal-20261013。原件来自e06-export-computer-03中原模拟器SAF实际保存的加密包，使用前轮核验所得逐字ZIP，未重新压缩。ZIP SHA256为3865b4398954b0950a0a3139019a0075d8ade310900a3028865f3f81b89cdd5f。主管身份与异常凭据均为隔离合成输入，不冒充物理PDA、生产身份或实际业务审批。

| 证据 | 实际结果及边界 |
| --- | --- |
| e06-review-resume-02 / 03 | 全脏原字节/hash/diff、实际HEAD/远端、旧释放/暂停、唯一写者、进程监听与磁盘；继承改动按输入比较保全 |
| e06-review-unit-01 / 02 | 首轮及自审后的各52项实际执行，零失败/错误/跳过，bootJar成功、源码不漂移；包含实际Android原件及合成修改反例 |
| e06-review-python-02 / python-final03 | 各38项通过；final03对应参数解析拆分后的最终CLI源码，原日志和退出码保留 |
| e06-review-history-1512 / http-final03 | 从保全的1508非空dump/私有文件/签发材料恢复；实际Android原包接收、原键重试、原字节下载与主管审阅通过；双权限/跨仓/不能业务处理及缺凭据、摘要异文、签名不符、历史钥缺失反例通过 |
| http-final03/conservation.json | 169表中159表逐行哈希不变；变化仅身份/会话、明确合成异常设备/凭据、中转与审计/耐久原键。所有继承原凭据按原ID逐行哈希保持，无原凭据UPDATE或补签 |
| e06-review-runtime-disabled01 | 实际默认配置下七个中转路由均拒绝，ORIGINAL_RELAY_DISABLED |
| e06-review-runtime-restart01 | 新JVM按原journal重读，原回执、历史授权状态及ZIP字节不变；product-cli-final03.log实际最终CLI入口登录真实HTTP复验通过 |
| o03-http-e06-review-regression01 / 1513 | 旧E2aOriginalRelayHttpIT实际执行，零失败/错误/跳过；原冻结中转、撤回旧资格、并发/尾SQL/锁后撤权、净值导出与更正账务组合通过；源码不漂移，不称完整IT |
| e05-preserve-1512-e06-review-final01 / e05-preserve-1513-e06-review-regression01 | 两库dump/hash、认证停机，19331/19434空闲；原datadir/binlog/私有文件保留 |
| e06-review-delivery-01 | 明确路径、最终验证输入映射、作者自审、继承原字节比较及提交/推送回执；final全脏快照保存剩余范围 |

## 自审与失败保全

只接受独立公开原件字段白名单；独立来源不得携带正式冻结正文/回执，其他来源不得混入独立原件。历史凭据先与服务端不可变原记录逐字段/摘要核对，再用固定签发钥验RS256和用途/能力边界，不能用包内设备钥替代签发根。主管审阅要求同仓rescue:relay和temporary_order:confirm双权限。三处原件读取统一默认开关、仓库范围、原ZIP验签，不改变旧冻结处理语义；组合回归补证其受影响路径。

1509恢复夹具遇预置空目录已存在、1510运行器UPDATE原凭据被不可变触发器拒绝、1511守恒夹具误列idempotency_request均保全，不计通过。分别修复目标为空检查、改用新副本新增明确合成异常记录、使用实际api_idempotency_request表后才复验。未绕过触发器或改原凭据。失败库各在e05-preserve-1509-e06-review-restore-failed01、e05-preserve-1510-e06-review-immutable-fixture-failed01、e05-preserve-1511-e06-review-conservation-fixture-failed01中dump/hash停机。

Python01缺继承向量目录及误列不存在模块，stop脚本首次PowerShell筛选写法错误均保留原日志；有明确新依据才修复，计数不因换会话清零。原高频页面三次及其他历史暂缓继续保持，不机械重跑。

交付manifest首轮直接比较大小写不同的十六进制哈希，误报继承漂移；逐路径重核只有已授权更新的看板变化，统一摘要小写后65项继承原字节全部保持。此为记录工具失败，不计产品测试失败或通过。

## 收口和接续

本页只收口接收/核原授权切片。原任务仍是SCANNING截点，不是完整结束，不满足实物核对、候选/人工关联或跨机冲突消费标准；O04/U03、D14和总目标未完成。作者自审不称独立人员审查，未跑完整服务/Android/全部IT、物理PDA、原生Excel或D18。

下一步实现原人原机独立任务完整结束边界：串行队列排空、无未决输入、固定全量原事实/计划/实采/VOID/审计及签名摘要，封存后拒绝新增与改写；只读导出和主管消费者核原边界，随后实物/候选与人工关联。不得将未结束快照直接认作可确认订单。

本项未清理，新日志/数据库/临时目录在D盘，TEMP/TMP/Java临时目录仅对子进程设置。收口前C/D余量56406253568/22969495552字节，最终见交付快照disks.json。预览1494/19330、API19435、worker与Vite5174保留；原AVD/Keystore、共享3306/ADB不动。开发源码推送不包含.local私有原件、密钥、库、APK，不称其已异地备份。
