# 未终结FORMAL实物应用与解隔离

输入主树D:/acrylic-pda-outbound、codex/development、19d5e98dfc6530c293af5ac98018056f09982ec9。单助手/单写者；版本事实仅见看板生成块。原库/现网、main及发布部署不操作，生产默认和localDeletionAllowed=false保持。

## 自主设计及退出标准

原实物隔离在同原机续作提交接收的事务内已应用，其他保留包可因短装进入待审核，故隔离包不能仅因其他包尚未最终出库而永久不能核对。现有应用证明只接受最终成功或停止签名；待审核响应没有原签名，不得临时补造为旧“成功回执”。按P06/P08采用独立的实物应用签名：在合法新提交原事务内固定原正文、原ID/人员/机/仓库/代次及原处置集合，实际应用与签名持久化同生共灭。原终态签名路径继续兼容；旧未终结输入缺此原件保持事实不足，迁移不回填旧签名。

采用新不可变应用原件表，不重写submission.receipt_text，也不扩出库/扫码/删除能力。解隔离只验证原STOP、实物批准、同原机续作批准/激活及该应用原件，只移除对应隔离owner，其他保留包与原批次保持待处理。应用原件不随后续审核改变，案件原依据保持可下载。拒绝缺签名、跨包/跨提交/正文/处置摘要漂移、未实际应用及其他owner；不将数据库关联本身冒充可信签名。

应用原件写入与签名沿用corrections.commands的默认关闭开关；隔离验证显式启用，内部通过不开放生产证据能力。旧终态仍用其原签名，新增表不为旧输入代签。

退出：真实HTTP从原停止/批准/激活到应用待审核，再实物附件及独立解隔离；持久应用原件末SQL失败和重试、处置/出库表守恒、原键并发/异文、自批/越权/损坏拒绝及后续审核原依据保持。普通定向、原非空迁移、Web契约/实际双角色/原件/窄屏/丢响应、新JVM及受影响提交/账务组合分别留证。合成隔离身份不称原设备/现场，未验不计通过；D09/D10和总目标未完成。

新增证据、隔离库和子进程临时均D盘.local；重型前核C/D与增长。全脏断点quarantine-resume-open-formal-baseline01保护61继承路径；旧失败库/签名/XML和AVD身份继续保留。

## 实现与自审

实际应用后以原submission为唯一键固定原正文/处置摘要、原人/机/仓库/scope、assignment/continuation/轮次/恢复代次、应用ID集合和UTC应用时刻。表及原字节禁止更新/删除，迁移无历史回填。记录器在已有事务内运行，尾SQL失败回滚实际处置、占用及提交正文；关闭生产开关时不读库、不签名、不写原件。

FORMAL消费者先验应用原件哈希、固定信任根签名、正文及处置集合、全部身份关联、精确实物应用集合和数据库应用时刻；旧无应用原件只消费真实终态签名。后续有终态回执时也重验签名、状态、原提交/订单及正文摘要，不以有效但无关签名代替本批次。只删除对应隔离owner，原批准/停止/正文/处置/结果/库存账/周期不变；其余保留包继续按原批次处理。

Web严格兼容原终态和新独立应用两种形状，解释实物应用与后续批次的关系，完整原件沿同案件权限下载。契约随实现同步；没有打开生产入口、改变删除能力或恢复换机承接。作者自审和固定公钥独立消费者分别记录，未使用独立人员或真机作为本轮证据。

## 实际验证与输入

证据根D:/acrylic-pda-outbound/.local/goal-20261013/；各运行器保存命令、输入manifest、原日志/XML、退出码及C/D余量。

| 层 | 证据 | 结果与适用范围 |
| --- | --- | --- |
| 普通定向/构建 | open-formal-quarantine-unit-final04 | 275项、0失败/错误/skip，bootJar通过，最终测试与产品输入固定 |
| 最终真实HTTP | open-formal-quarantine-http-final03、1488 | 1项、0失败/错误/skip且源码无漂移；实际STOP/批准/激活/应用待审核、应用原件末SQL回滚与原键重试、不可变守卫/正文漂移、独立实物附件、自批/跨仓/并发/异文/尾SQL拒绝、解除后其他包守恒及后续审批；有效但无关终态签名三字段替换拒绝 |
| 固定公钥/SQL独立消费 | open-formal-quarantine-independent-final03 | 原命令/回执精确字节及哈希、实际应用数据库原字节、四类签名、原正文前缀和处置集合核验；库存、账务与原处置守恒 |
| Web构建/契约/消费者 | formal-quarantine-web-final-open-formal02 | build/contract、7审查及4消费者通过；实际Java新原件及损坏/错关联反例 |
| Chrome产品 | formal-quarantine-browser-open-formal02、1489 | 2项、0失败/skip/flaky，源码无漂移；独立双角色、真实附件/原件下载、390宽、存储失败不发请求、主管批准丢响应后重载原键恢复；截图已查看 |
| 浏览器库独立消费 | open-formal-quarantine-independent-browser02 | 固定公钥、应用数据库精确字节、原批准/正文/摘要；原批次待审核、其他包REVIEW_RESERVED、零出库/账务，解除仅对应owner |
| 新JVM | formal-quarantine-restart-open-formal-final03、open-formal-browser-restart-browser02 | 1488与1489新JVM原批准GET/原键POST、原FORMAL依据下载精确字节保持 |
| 原FORMAL非空 | open-formal-quarantine-history-final01、1490 | 原1463 dump与存储哈希守恒，重复迁移/validate通过、全部旧表投影保持，新应用表零行、没有补签 |
| 原非空HTTP消费者 | open-formal-quarantine-history-download-final01 | 新JVM消费1463原批准GET/原键POST及原FORMAL依据下载；与原浏览器批准和原依据SHA256逐文件精确相等，非新种子替代 |
| 受影响账务组合 | open-formal-quarantine-http-combination01、1491 | 1项、0失败/错误/skip，真实更正/撤销/固定导出、缺账逐笔补扣和返库分页组合通过，源码无漂移 |
| FORMAL终态组合 | open-formal-quarantine-http-closed01、1492 | 1项、0失败/错误/skip，实际原停止/批准/续作/成功后的解隔离、权限/篡改/并发/尾SQL及原事实守恒通过，源码无漂移 |

最终Git回执随本切片收口补记。历史非空仅覆盖原FORMAL与相邻提交投影，不称全部生产兼容；本轮无新增APK、模拟器安装、真机或部署。

## 失败保全与适用限制

- 裸compileJava初次误用不存在的scope访问器，已改实际submission读取；原工具输出保留，不计测试通过。
- HTTP-browser01/1486测试1项自身通过，但启动后新增历史IT使源码门失败；source-drift-audit.json证明仅新增测试且原输入无字节变化，仍不冒称该轮完整通过。浏览器open-formal01在主管批准前失败：测试仍强制旧终态signedReceipt，实际新原件为signedApplication；原库/回执/截图保留，消费者修复后在新1489通过。
- HTTP01/1485及unit01早于生产默认开关修复；HTTP-final02/1487早于终态关联和应用时刻自审修复。保留全部日志/库及通过范围，不代替最终输入。
- 旧TEMP/PC/FORMAL、历史贡献及此前失败不重写；原资料/旧BCD、1043/A1.4暂缓及UI两项连续失败继续单列。旧未终结来源没有实际签名时保持事实不足；不能用迁移或隔离合成签名补造原事实。
- D09/D10及总目标未整体完成；E05净结果与原件中转、E06–E09、O/U全部必做继续，localDeletionAllowed=false与生产开关不放宽。

## 磁盘与断点

本轮起始C/D约60.89/27.17GiB，收口约60.87/24.51GiB；新增库/证据/临时均D盘.local，重型前以各disks-before登记余量与≤3GiB增长估计。未清理库、失败资料、活动AVD身份或唯一证据。1486–1492均保全，完成库dump/hash认证停机；1489浏览器后再启动原身份作新JVM/独立消费，随收口dump/hash认证停机。19330/19434/19435/5174空，无重型在途。

全脏快照quarantine-resume-open-formal-resume01和final02含全部原字节/hash/diff、进程/监听/磁盘/远端，61继承仅看板变化。旧看板归档ORCHESTRATION_STATE_before_open_formal_resume_2026-10-13.md；明确交付路径与最终输入/自审另存open-formal-quarantine-delivery-01。源码备份不表示.local库/密钥/原件已异地备份。
