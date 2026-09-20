# R06-D 临时订单冻结、上传与原批次恢复

日期：2026-09-20。接续[审计批次](R06_TEMPORARY_AUDIT_GUIDE.md)，本批完成稳定TEMP来源冻结、共享提交控制头、取消先到、原ID查询、整包/分块上传、签名取消与本机恢复。**所有临时提交仍停在待独立确认，不得放行**；独立确认、受控关联、拒绝后的实物处置、TEMP最终出库与出库归档留给下一批。

## 实现范围

- Android沿原创建设备＋clientOrderId＋原TEMP资格冻结；保持原计划版本、规范摘要、库存/政策、采集与事件UUID、全设备序号和原出库周期。原资格到期后可冻结已保存的证据，不允许新增扫码。全机未决输入、零包、未确认失败和无原因短装均阻断。关闭扫码入口与接收同锁，页面先停输入并检查队列完成，再事务写入FROZEN事件、唯一正文、摘要及草稿状态；失败全部回滚。
- Room17新增temporary_submission，正文仍使用既有WAL＋synchronous=FULL耐久设置；读取按128KiB分段，避免大正文CursorWindow截断。原批次重试返回同ID与同正文；冻结后不能改计划、扫码或撤销。原本机正式/临时guard继续保留。
- 原人员、原设备和仓库使用有效pda:submit会话上传；原恢复代次固定。查询先于发送，结果未知先落盘；取消意图与原因先持久化，进程重新打开后只恢复原取消，不补发上传。整包限1MiB，较大正文复用262144字节分块，完整正文最多32MiB。人工与Worker共用应用单例互斥，WorkManager按Wi-Fi、退避和原ID持久重试；待审定期查询不等于批准。
- 客户端保存回执前事务复核原请求、登录代次/身份/权限版本与恢复屏障；回执来源、摘要、冻结设备、轮次、恢复代次及正式目标必须一致。ABORTED必须验签，禁止解除实物占用/恢复扫码；未知成功状态不会变成放行。上传或取消结果未决时阻断安装更新。
- V33将共享submission_scope改为FORMAL或TEMP来源二选一，沿temporary_work_origin固定原资格。取消可在正文前创建REGISTERED控制头并生成签名ABORTED，此时不创建正式订单、assignment或伪正文摘要；操作审计保留。当前来源只能有一个首轮提交，换ID、换资格、改正文/分块摘要不能重提。
- 完整上传核对原多产品计划、固定产品/库存、原周期、资格有效期内保存时间、完整终态输入与审计后，才生成唯一PDA_TEMP正式记录及order_submission_slot，编号复用OrderNumbers的FH日期流水。复用共享私有正文存储、采集/事件防重及bale_commitment待审占用，不产生dispatch_record、库存扣账或成功回执。
- 短装、库存/原周期冲突、资格过期、激活未确认、版本变化和准入关闭保留待核对原因。原签名可用基线可能被同周期较新同步状态合法更新，服务端不把旧基线不可用状态直接视为伪造；仍核对原周期并按当前库存/占用分流，任何结果均待独立确认。正式审批和正式成功归档入口拒绝TEMP，不能绕过新权限。

## 版本与兼容

Android **0.15.0-r06-submission/code20/Room17**；契约 **0.25.0/103路径**；Flyway **V33**。旧正式提交正文、源ID和签名回执不补写TEMP字段。V33扩展共享表并允许临时assignment为空，旧后台不可直接在含TEMP数据的库上运行；回退须停写并按备份及兼容方案处理，不删除新事实。Room不提供降级清库。

code20需配套0.25后端使用TEMP提交。旧公网0.18/V23/code16保持，本次APK仅生成，未安装、发布或部署。APK仍为debug包且未配置服务地址，不作为公网升级发布候选。

## 本轮验证

- Android全量主机 **274项通过，0失败/错误/跳过**；冻结回滚、队列/未决输入、短装、资格到期、恢复屏障、身份变化、丢响应、取消重试、原guard、真实签名及大正文分段/分块验证通过。Room16→17保留31张旧表字段/数据、正式冻结及两类占用；历史升级测试同步到17并通过。
- 服务端32项单元通过；178项不同MySQL/MockMvc/服务契约用例最终均通过，按每用例最新结果汇总，0失败/错误/跳过。扩展178项初轮有1项测试误复用正式提交ID提前被400拒绝；仅修正该夹具为新提交ID，随后26项传输/正式回归全部通过，覆盖其余已通过场景，无业务实现变化。
- 真实后端准备资料→Android冻结正文→后端原授权验收通过，服务端真实待审/签名取消回执由Android核验。正文前取消和正文后取消分别覆盖；专用跨端测试可通过R06_ANDROID_BODY_VERIFY=true运行，要求按下面顺序生成同一独立库的夹具。
- 只读复制R06-D审计合成库至独立升级库，V32→V33通过，92张旧表原字段数据摘要保持；原R05/R06库不迁移或重置。
- debug APK、测试APK、后端JAR构建通过；APK v2签名通过；lint **0错误/181警告**；OpenAPI 103路径通过。

失败记录如实保留：首轮11项服务端测试有2项误写占用表名；修正为bale_commitment后通过。扩展36项有1项审计冲突断言误写400，实际正确409，修正后通过。Android首轮测试编译因摘要函数名/恢复字段名写错而未运行，修正后专项及全量通过。一次Python脚本未显式UTF-8导致解码失败，未修改目标文件；一次升级脚本误选当前V33库，被版本断言拦截，未建库或迁移，修正为只读V32来源后通过。双向夹具重复从空Room生成相同设备起始序号，与已上传事实冲突；原云端记录保留，改用新独立准备来源并补采集序号409保护及测试。新增冲突测试首轮复用正式submissionId而提前被400拦截，改为独立ID后断言409通过；失败记录未删除。一次git换行检查临时关闭autocrlf，误把CRLF识别为尾空格；正常仓库配置检查通过，无全库换行改写。全部相关日志/XML保留。

跨端复现顺序：在独立合成库运行TemporarySubmissionContractFixtureIT，生成ignored `server/build/pda-temporary-submission-fixture.json`；Android TemporarySubmissionContractTest消费其中独立preparation并生成`pda-temporary-android-body.json`；保持该独立库，设置R06_ANDROID_BODY_VERIFY=true运行TemporaryAndroidSubmissionIT。重新从空Room生成输入时应先生成新独立准备夹具，不复用已上传设备的初始序号。测试会签发同身份新测试会话，业务验收仍经过真实session recheck；这不是HTTP登录/设备联调。

所有本轮HTTP边界为MockMvc或替身，Room重开不是系统跨进程/断电测试；合成过期不是真实48小时离线。本轮未运行实际HTTP监听、Web浏览器、模拟器、M71或异地网络；Wi-Fi后台Worker尚未设备验收。Web独立待确认入口、超时提醒TEMP展示、重复正式单核对/关联、拒绝处置及最终出库另批实施。

## 本机产物与接续

APK `outputs/pda/Acrylic-PDA-0.15.0-r06-submission-code20-debug.apk`：12,121,922字节，SHA-256 `a3868b8917a9ea79873eac51ec051801f91110479a69f8b09fef019b9bb9bec1`。测试APK `outputs/pda/Acrylic-PDA-0.15.0-r06-submission-code20-androidTest.apk`：2,027,932字节，SHA-256 `0c3767bcfe3cfecefcaf9accf059b8acaf52eab07fb4ad59f6bca85e15969aa4`。未安装。

证据位于ignored `.local/r06d-submit-20260920/`，包括各轮日志/XML、Android全量结果、upgrade-summary.json、APK签名和产物摘要。专用库为`acrylic_outbound_r06d_submit_20260920`及`acrylic_outbound_r06d_submit_upgrade_20260920`；无HTTP迁移进程及测试完成后退出，共享MySQL保留。库、私钥、凭据、APK、签名夹具及原始证据不入Git。原R05归档/同步/续期与RECONCILING断点、R06-B/C/审计资料、AVD/M71、现网及既有审批阻断保持。

下一具体开发项：**R06-D独立确认与处置（M2）**。先实现Web待确认与temporary_order:confirm，核对原资格/当前权限版本、短装审批与库存周期/占用；再接重复正式单人工核对、temporary_order:link受控关联及拒绝后保留/卸货/隔离与原机后续作业，最后复用共享记账并验证签名成功回执及归档。当前批次不能称完整R06已验收。

R09交接边界：R09在独立工作树负责运维工具及恢复控制/证据方案；共享OpenAPI索引、Flyway编号、server_state、认证/API写入口、Job/Worker事务屏障和R06提交记账核心仍由主线维护。稳定提交前R09固定消费87e1893，不复制在途V33/Room17；本批未提供独立灾后恢复API，接口接入保持待检查点。

Git按已有授权仅提交推送本批到codex/development，具体提交以Git记录为准；未合main、打标签、发布或部署。其他会话的路线规划文档改动及docs/out.xlsx保留，不混入本批。
