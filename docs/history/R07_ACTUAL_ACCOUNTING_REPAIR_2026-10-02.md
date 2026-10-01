# R07 实际账务贡献修复验收（2026-10-02）

## 问题与实现

旧审批以结果数量和重量推断扣库，未扣库订单会生成虚构账务；重量增减符号、原扣库归零和连续更正后的撤回也不能证明逐笔恢复。

审批读取并锁定原 inventory 与 correction 贡献链。未出现账务行才视为未扣库；非空链即使净零也必须匹配有效结果。重量调整记 before−target，归零逐笔反向原贡献。撤回沿不可变引用链找到该更正的现有代表，再逐笔恢复。结果版本、effective scan 与账务独立冻结；同值 ABA、重复引用及贡献不匹配均拒绝并回滚。

迁移只增加原扣库反向及撤回恢复引用、外键与唯一/形状约束，不回填或改写历史账。迁移事实见看板生成块。零账务更正仍可导出；撤回导出同时证明原回执、结果和逐笔引用。更正 outbox 写入订单 status，供同步消费者读取。

## 验证与证据

输入主树 D:/acrylic-pda-outbound、codex/development、基础 HEAD bfcf471b58e2e2ac024c4538d871a51bfa0065e7。本批提交SHA见[自主日报](AUTONOMOUS_PROGRESS_2026-10-02.md)，全部差异/写者/监听/分配见 .local/autonomous-20261002/accounting-repair/resume-eleventh/，最终范围与暂存快照见 accounting-repair/closeout/。

| 实际运行 | 结果与范围 |
| --- | --- |
| offline-full-11 | 编译产品/测试、148套件2956项，零失败/错误，1既有符号链接跳过；源码哈希保存 |
| 832 E2aCorrectionAccountingHttpIT | 1项通过：扣库/未扣库、增减重、归零、多次更正与撤回、独立包撤回、手工贡献；冲突/完全冲销/ABA拒绝及SQL守恒；更正与撤回走真实HTTP |
| 833 E2aCorrectionAccountingMigrationIT | 1项通过：非空旧schema升级、旧列/历史守恒、重复迁移、精确FK/唯一/check/append-only约束 |
| 834 E2aSupervisorCorrectionHttpIT | 1项通过：迁移后的主管分离、审批签名、拒绝与原键重放HTTP回归 |
| 835 E2aSupervisorWithdrawalHttpIT | 1项通过：原审批人发起、原申请人确认、恢复与原键HTTP回归 |
| 836 E2aCorrectionIdentityWithdrawalIT | 1项通过：身份撤回回归 |
| 839 E2aWithdrawnCorrectionExportIT | 1项通过：更正/撤回HTTP、Web登录、恢复结果XLSX/PDF下载、历史更正结果及原确认结果导出 |

成功隔离项零失败/错误/跳过，原库、dump、日志和签名回执均保留并认证停机。832–836索引：accounting-repair/isolated-suite-832/results.json；839结果：.local/e2a-cross-20260925-839/result.json。839下载与SQL事实：storage/withdrawn-correction/；marker、dump摘要与停机证据位于该attempt根目录。

完整离线full-9/10是相同产品逻辑、导出夹具修复前的历史通过，不替代full-11。完全冲销反例先红后绿。830 renderer配置503、831确认凭据409、837隔离SUPERVISOR缺pda:login导致401、838隔离HTTP/Secure cookie导致Web登录403，全部保留。仅补测试前置与cookie启动参数，生产配置和产品导出校验未放宽。full-2中断、full-3编码、full-5编译失败亦保留。

## 自审与限制

同一助手实现、自审和运行，不称独立消费者。TEMP确认前置是测试内完整服务组装及合成可信身份；更正、撤回和839导出是隔离真实HTTP，不替代PDA原身份、现场或生产。预存E2aCorrectionChainIT反例差异与PDA文档未纳入本批提交；新夹具所用帮助方法已在基础HEAD存在。

生产默认开关不变，CommandOperation未因测试而开放。HEADER撤回、历史无引用REVERSAL、旧更正outbox、CYCLE_REOPENED未覆盖；完整服务端integrationTest尚无可解读全量通过。独立JVM/OS死亡、断电/长时、真实PDA/Keystore、完整UI、原资料/旧BCD、526及R09恢复限制继续单列。只收本批账务缺陷范围，不代表R07整体完成。

839保全后D盘可用24,966,389,760 bytes，后续每个新任务复核阈值；证据删除0。未合main、打标签、Release、部署或接原库/现网。
