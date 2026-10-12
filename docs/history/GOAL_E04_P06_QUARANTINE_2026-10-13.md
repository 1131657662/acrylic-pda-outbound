# 库存实物解隔离开发切片

主树D:/acrylic-pda-outbound，codex/development，输入HEAD/开发远端9daf4871c82434c9c671c41865f479e24d3d0a0c；版本事实见看板生成块。单助手、单写者，不接原库/现网、不合main、不发布。已关闭TEMP且无历史账务的首切片实现、自审和定向验收通过；其余来源、D09/D10与总目标未完成。交付SHA和实际推送回执见quarantine-delivery-01/git-delivery.json及看板。

## 采用的默认与来源差异

普通解隔离采用发现→实际实物核验附件→独立主管批准，由既有更正命令事务执行。上游在库资料、原隔离占用、原结果/实际账务和实物分别固定，案件结案不能代替业务批准。只删除被精确证明的QUARANTINED占用；原处置记录、原回执、出库周期、库存账和账务历史保留，追加唯一解除关联及条码状态同步。可用性由正常库存规则重新计算，不签发作业资格。

源码确认三种来源：FORMAL的physical_disposition_evidence、TEMP的temporary_physical_disposition/item、PC的pc_disposition及原计划。TEMP可核原命令完整签名、审计/事件和原引用；FORMAL/PC旧审批不具有同一种签名，须分别核其原恢复/审批/应用证据，不伪造TEMP回执或以新fixture冒充原事实。

先实现已签名关闭的TEMP来源：它可能没有正式订单，因此reconciliation_request的order_id允许实际为空，签名回执保留null；独立quarantine_origin绑定原处置、包及原TEMP回执。其余来源及未关闭TEMP的解除消费者仍属D09开发缺口，后续独立补齐。采用分切片理由是避免将未知信任来源或仍在作业的来源直接套入库存释放。

## 最小实现与退出标准

1. 新命令RECONCILE_QUARANTINE_DISCOVER/SUBMIT/APPROVE，沿原Session/CSRF、仓库、恢复代次、原键、签名和外层事务；生产默认不开放。发现/提交需outbound:correct与inventory:reconcile，批准需correction:approve，禁止发现人/凭据提交人自批。
2. 原CLOSE_DISPOSITION签名通过既有完整核验器；原仓库/来源/批次/证据集/核验/manifest/逐包QUARANTINE与实际记录一致，来源保持关闭。原件缺失或坏签名即拒绝。
3. 固定有效上游快照、库存版本及全部owner；账必须NOT_DEDUCTED、无blocked_reason。首个切片只接受没有历史dispatch/ledger的包，历史退货贡献需另行核完整原贡献图，不用净和为零冒充真实反向关系。批准锁内再核，同一发现摘要与实物提交摘要均须保持；缺上游、漂移、其他来源占用、账务冲突均拒绝。
4. 原发现人提交三天内实物在库、已解除隔离条件的核验时间/地点/依据及实际附件；主管可读原附件及原隔离签名。批准保留周期/结果/库存账及原处置，删除精确原隔离owner，追加不可改写的解除关联。旧RECONCILE_ADVANCE禁止推进此业务案件。
5. 实际HTTP/MySQL、产品双角色、无权/跨仓/自批、附件/漂移、并发原键/异文、末SQL回滚、丢响应/新JVM、适用原非空与原回执字节保持、固定公钥独立消费、受影响组合及窄屏可达均须实际验证；不能以准备或定向单元测试记通过。

原P05未上传冻结来源含实际模拟器Keystore完整停止/关闭及QUARANTINED包，优先在保全副本上消费原事实。原库/datadir/binlog/Keystore不写、不删除。新增产物仅D盘，重型操作前登记C/D余量与预计增长，所有失败保留。

## 接续输入

返库切片交付后全脏快照为.local/goal-20261013/returned-stock-resume-06，61路径原字节/hash/diff、远端、33树、进程/监听和磁盘；继承基线仅看板变化。采样C/D余量69700624384/24055406592字节，快照占用778240字节，无清理。所有本批隔离HTTP/DB及预览端口均空，共享MySQL/ADB和原AVD身份保留；其他项目聊天notLoaded，无其他活动写者。

本段是设计时的接续依据，后续实际证据见下文。首切片以外的解隔离、其他差异、D09/D10整体及总目标仍未完成；已取消换机进度承接不恢复，localDeletionAllowed=false保持。

## 开发验证进展与保全

新增TEMP发现/提交/批准、独立候选与案件分页、原隔离签名回执精确字节下载及Web双角色原键恢复。界面可核原隔离/关闭人/证据集/原签名摘要及实际实物附件；无正式订单保持null。自审发现隔离案件可落入旧库存账列表及上下文，已按案件类型严格排除；原来源/精确owner/无历史贡献限制保持，不把候选当可信来源。

- quarantine-unit-04：138项定向、bootJar通过，输入无漂移；不是全仓最终回归。
- quarantine-history-09/1445：原P05非空副本升级/重复/validate及旧列守恒，实际HTTP原签名/原字节/实物附件/独立审批/撤权/自批拒绝/上游与owner漂移/末SQL回滚/原键并发异文通过；SQL rollback-only夹具52候选、53案件分页无重漏，两类游标独立且伪造案件不可读可信上下文。夹具不计真实来源。
- quarantine-browser-03/1443：Chrome两项0失败/skip/flaky，真实原P05來源消费→实际附件→独立批准→丢响应重载原键恢复通过；390宽原事实/附件/原因/footer可达，无横溢，图片及原HTTP字节保留。其后增加原隔离回执下载及拆分读取/命令/状态，须最终浏览器复验。
- quarantine-web-final-03：最终hook拆分输入build/contract、7审查单测、6原件消费者通过，输入无漂移；最终Chrome补验见下文。
- quarantine-independent-02/1443：独立Node固定公钥指纹，三原更正及三内嵌TEMP关闭签名/摘要/正文和SQL守恒通过；没有新签原来源。quarantine-restart-consumer-02：真正新JVM GET/POST均返回同一原审批字节。

失败原件完整保留：history01/1436新ObjectMapper不支持JDBC日期；history02/1437协议canonical拒绝JDBC二进制，改已有typed SQL编码方式；history03/1438自批拒绝期望409误写，服务实际403正确；history05/1440超期拒绝期望400误写，实际409；history06/1441坏附件引用期望409误写，实际400。各处均按源码/实际错误码新证据修正，没有降低业务拒绝；history04/1439、07/1442、08/1444和09通过各自输入。

browser01隐藏radio被可见span拦截，尚未业务POST，改点击可见标签；browser02因执行者过早停测试服务而连接拒绝，不计产品通过；browser03服务就绪后通过。独立SQL01把BINARY hash当JSON字符串，修只读HEX后independent02通过；newJVM consumer01启动未就绪连接拒绝，等实际就绪后二次原字节通过。失败脚本/日志/截图/SQL、datadir/binlog/dump不删除，计数不因attempt或换会话清零。

quarantine-resume-01保存91路径原字节/hash/diff/远端/33树/进程/磁盘，61继承仅看板变化。1436–1445均已dump/hash/认证停机，1446仅新原资料副本供最终产品复验；原P05 dump/storage/Keystore只读保持。产物仅D盘，近采样C/D余量69682257920/20885262336字节，无清理，后续重型操作继续实测。

## 最终产品、组合与自审

- quarantine-browser-04/1446：最终Web两项Chrome实测0失败/skip/flaky，源输入无漂移；原来源签名回执下载SHA与原响应一致、实际附件原字节、双角色、自批入口隐藏、批准丢响应及重载原键GET恢复通过。mobile-source/mobile-approval/resolved-page图片实际查看，390宽原事实/回执与附件读取/原因/footer可达，无横溢。
- quarantine-independent-03/1446：独立Node固定公钥指纹消费三原更正命令/回执、三内嵌原TEMP签名及正文摘要；SQL验证唯一解除、零owner、无新增dispatch/ledger、原库存账与周期保持、条码同步存在。工具消费者不等于独立人员审核。
- quarantine-restart-consumer-03/1446：真正停止旧JVM并启动新JVM后，审批GET/同原键POST及原隔离回执下载均返回原字节。runtime04/05与unit04产物JAR哈希一致，实际就绪后执行。
- quarantine-http-combination01/1447：新隔离库实际更正账务/撤销/导出、缺账逐笔补扣及52返库候选分页/合成候选拒绝组合，1项0失败/error/skip，输入无漂移；不重跑P04/P05已验主体。
- 原历史history09覆盖三命令正向、真实附件/签名/引用、跨仓/自批/撤权/超期/坏摘要/上游与owner漂移、末SQL回滚、原键并发与异文拒绝。分页rollback-only合成夹具仅验证有界独立游标，不能计可信业务来源。

作者自审核原CLOSE_DISPOSITION完整验签/原响应摘要、业务引用与审计事件，库存上游、精确owner、独立实物及真实账务分域固定；E/G身份写锁先于业务锁，发现/提交/批准均在外层事务核代次/身份和原摘要。原键响应不因后续案件状态重签，尾审计失败全部回滚。旧RECONCILE_ADVANCE、旧库存账列表及旧上下文不能越过解隔离案件；新增恢复表仍维持未知范围阻断。Web未决原意图先耐久保存且与旧账务页面共用锁，人员/仓库变化拒绝晚响应；下载先核原摘要。

quarantine-delivery-01保存明确提交路径、源码/契约/测试哈希、逐份证据输入适用性和自审；unit04、history09、组合01的服务源码无漂移，Web-final03与browser04的Web/契约均无漂移。quarantine-resume-02/03及交付快照保存全部脏原字节/hash/diff；62路径返库基线包括看板，除看板外61继承不变，不混入Android/UI、旧换机、历史工具或本机请求。

1446/1447均在退出产品/组合后核身份、dump/hash及认证停机，保全为returned-stock-preserve-1446/1447-01；任务端口已停止，共享3306/ADB5037、原AVD5562/5564/Keystore保留。每次重型操作实测C/D余量及预计增长，新增jar/库/日志/dump/截图和子进程临时仅D盘；最终准确余量见交付快照，无清理、无C盘新副本。所有失败库/datadir/binlog、HTTP、XML、图片和原资料保持。

下一动作先实现FORMAL/PC隔离源的各自原审批/应用/恢复证据消费，并补未关闭TEMP及历史退货贡献的精确核对；不能拿首切片或候选列表计整体完成。随后E05净结果/原件中转、E06独立采集、E07恢复/外置副本、E08清理/辅助UI和E09最终候选继续，历史暂缓项不清零；生产开关及localDeletionAllowed=false保持。
