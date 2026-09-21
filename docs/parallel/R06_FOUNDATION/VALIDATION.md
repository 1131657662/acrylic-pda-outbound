# W1 作者验证记录

2026-09-21，分支 `codex/r06-source-foundation`，生产/schema固定 `0311dfaf4255671a605a17aa3defc145df9621a6`。随后补充只修改测试和本目录文档，生产/V34未改变。尚未整合development/main或部署。

## 实际结果

- 56单元通过，0失败/错误/跳过，包括13项新协议边界；最终XML `.local/evidence/unit-final/test/`。
- 29个不同IT通过，0失败/错误/跳过；按suite取最后一次完整结果。来源基础5、来源投影2、来源竞争2、BarcodeSyncIT 6、PcOutboundIT 9、TemporaryAvailabilityIT 5。5+2+2+6+9+5=29，先前首5包含其中，不另累加。
- V34新空合成库迁移、Java编译和bootJar通过；不等于非空升级、HTTP或设备验收。
- 两份真实事务证据在 `.local/evidence/source-concurrency/source-first-true.json` 与 `source-first-false.json`，均READ-COMMITTED、两个不同连接、P_S等待 `user_management_guard` 的 X记录锁。来源先提交时正式服务进入占用冲突且K仍SOURCE_RESERVED；正式先提交时合成来源写者拒绝且K仍DISPATCHED。每包K只有一行、原周期保持，不将G等待称直接K行锁等待。

来源写者仍为明确合成SQL夹具，不是已实现证据API：先建立真实FK父行，以E/G/U/W/B顺序参与共同锁，K变更及BarcodeChanges同一事务。其manifest/signature文件是测试占位值，不计为完整证据验证或生产签发。

投影测试实际调用PC候选/录入MockMvc、TemporaryAvailability、正式PreparationPayloads及真实BarcodeStream增量；无order来源K显示RESERVED、PC录入409且无pc_entry，库存重发后原K/source/cycle保持。旧正常TEMP接收保存SOURCE_REGISTERED→INITIAL_ACCEPTANCE映射、decision0及K永久source_scope/仓；没有命令记录、放行或释放。

## 失败与重跑

| 阶段 | 实际失败及最终处理 |
| --- | --- |
| 首单元 | 56中1缺原sku样本，55通过。授权原样本本机复制后56过；首日志保留，首XML被复跑覆盖，不声称仍有修前XML |
| 首IT命令 | Gradle参数`--tests`误附bootJar，尚未运行测试/迁移；修本机脚本 |
| 初始库V34 | ALTER缺ADD CHECK，5项Context失败；原库失败历史保留 |
| r2库V34 | uq_submission_round与V12已有索引重名，5项Context失败；原库保留 |
| retry03库V34 | 最后CHECK括号多一项，5项Context失败；原库部分DDL与失败历史保留 |
| retry04首5IT | 4过/1新fixture共用TEMP/正式submissionId，被正确400拒绝；换独立正式fixture后5过 |
| 补测首29IT | 24过/5新fixture事务外BarcodeChanges失败；包装同一事务后来源9全部过，另20不重复跑 |

上述每个Context组是一处DDL根因导致启动失败，不能累计为15个不同业务场景。最终静态核对新增约束/索引名与V1–V33不重名、完整括号、匹配复合FK键；检查脚本首因默认GBK读旧脚本失败，改明确UTF-8后通过。字体缓存SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`；曾计划结束本轮字体下载进程，但执行前该进程已自行退出，实际未杀Java进程。

最终汇总 `.local/evidence/final-summary.json` 列每个XML suite及最终测试源码LF SHA-256；首29轮修前fixture未提交且未单独拍源码快照，不能编造其提交SHA。W1首生产提交中的SourceFoundationIT是首5通过版本；后续fixture/投影/并发以补测提交为最终源码。

## 环境与边界

仅本树 `.local/mysql19323/data`，端口19323，MySQL8.0；四个新合成库均保留，前三V34 success=0、retry04 success=1，`.local/evidence/flyway-states.tsv`留证。retry04为独立账号与私有目录；前三失败库绝不repair/清空。收尾mysqladmin经host/port/datadir护栏正常停止实例，`.local/evidence/stop-19323.txt`留证，端口无监听。

W2另测非空V33升级/脏关系/故障及七类旧服务回归，W3另测233离线向量；都不算本作者29IT或本轮组合结果。W3 `dbb0693` 独立只读复审报告在本目录，未重跑其测试。

真正证据接收/完整性、台账写者/原回执查询、LINK/处置/续作/最终成功、签名耐久、HTTP/Android/现场及R09 V34恢复/fence仍未实施或未验。原旧库/恢复断点、设备、现网、旧BCD和审批边界保持。

## 独立复审后的投影断言加强

在已测`27b69a7`之后，仅将SourceFoundationProjectionIT两处宽泛断言改为按目标baleId筛选唯一条目：该条码增量必须RESERVED、该正式准备条目available必须为JSON false，避免无关行满足断言。已静态核对BarcodeSyncService.Event和PreparationPayloads.Availability实际字段；生产/schema仍与`0311dfa`完全相同。

重型验证槽释放后，仅运行`integrationTest --tests '*SourceFoundationProjectionIT'`，加强版2项通过，0失败/错误/跳过，Java测试编译通过；没有重跑其余27项或单元/JAR，仍为29个不同作者IT，不能累加成31或算作组合验证。本次日志`.local/projection-assertions-final.log`与XML`.local/evidence/projection-assertions-final/TEST-com.acrylic.outbound.inventory.SourceFoundationProjectionIT.xml`保留；XML SHA-256为`f608603eae204a635994eb7169f2e2f7f045a27413fba3b53fe55d6311cd5c47`，旧final-summary保留原批次含义。

执行前精确核对19323未监听及本树原datadir/配置，恢复retry04原库、原私有目录和原签名；未重建或重种。收尾再次核对端口/datadir并mysqladmin正常停库，端口无监听，证据`.local/evidence/stop-19323-projection.txt`；四个新合成库与文件继续保留。没有新增失败，先前失败和所有未实施边界继续如上登记。
