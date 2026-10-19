# D07/R09原占用与权限语义前置

本批输入`cdca9f14445c047af3653b25f8b35c92b0f4f1a8`，树D:/acrylic-pda-outbound、分支codex/development，单助手作者/自审。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。仅原资料只读语义接口，不计D07/D13/D14整体或总目标通过。

## 实现与退出标准

`RecoverySourceOccupancy`先用历史公钥验证第一轮原凭据及ABORTED正文/LOCAL冻结正文，再核独立pin绑定的占用快照。ACTIVE原scan按原顺序逐行匹配capture/bale/barcode/cycle/orderScanSeq；VOID无占用行，缺失/重复/额外/错序/异文拒绝。REVIEW_RESERVED绑定原submission/order，TEMP另绑定scope/warehouse；TEMP SOURCE_RESERVED绑定快照指定set ID，submission/order显式null。原库存须存在、未扣账、未阻断。全部恢复、续作、实物处置、PC、TEMP续作与激活owner列必须完整且显式null，ABORT不意味着释放。

`RecoverySourcePermission`公钥重验原offline credential，精确原bytes/hash/source/payload绑定，历史窗口连续、有序、不重叠，采用[from,through)。每次原确认和保存都须在原凭据iat/exp及有效权限窗口内，包括未保存的业务拒绝确认；人员启用、设备ACTIVE、原仓库权限、签名userAuthVersion/deviceVersion、pda:login/scan以及TEMP create/submit/设备准入一致。原事件后停用不抹合法保存，历史验证不恢复今日资格。

两个接口拒绝高轮、交接/续作/恢复引用和处置链，尚未实现的成功/更正等占用语义保持拒绝。资料本身不证明其独立保管来源；调用者必须先通过原manifest独立pin和库外控制读取。无Spring bean、HTTP路由、数据库写入、迁移、契约、Android/Web或签发变化，ordinaryWritesAllowed与localDeletionAllowed仍false；实际恢复写者仍须锁定重验库存和控制，不把历史proof当放行权限。

## 当次验证

五个Java新增文件使用实际库外文件/控制reader和内存开发RSA夹具。定向01为67/0/0/0；受影响Recovery/Draft/Evidence回归02为2881/1/0/1、exit1/sourceUnchanged，高轮拒绝前解析夹具正文造成错误码不符，已提前轮次闸门。03为2881/0/0/1；核真实表结构后补遗漏PC/实物凭据/TEMP高轮owner列，最终04为2886/0/0/1、116份XML、exit0/sourceUnchanged。Windows符号链接skip未执行；普通test不含IT。所有原日志/XML/命令/源码前后hash在`.local/d01-d18-20261008/r09-source-state-*`，失败02保留。

新只读工具消费者盘点1151保留的原凭据、历史消费者报告、迁移/最终表快照及dump，五份原件前后hash一致，02 exit0。01把native表状态base64直接列出，02解码标签，原01保留。结果在`.local/d01-d18-20261009/retained-state-1151-01/02/`，脚本`ReadRetainedState.ps1`；只确认物料结构和版本字段存在，不再跑已验验签器或旧专项。缺独立pin的原权限区间/故障前占用manifest，报告originalStateProof=false，不能由最终表状态推断原事实或制作正向证明。不接数据库/私钥，不重跑1150/1151/UI-06或1100–1102。

作者设计/自审在`.local/d01-d18-20261009/source-state-design.md`及`source-state-self-review.md`；独立工具不等于独立人员审查。本批无新HTTP/MySQL/客户端/模拟器/APK/真机或部署证据，原失败库/原log/XML/dump/binlog保持。

## Git、环境与接续

接续完整快照`source-state-resume-01`核27条全部dirty副本/hash/diff及22条继承保护，原FORMAL final非看板dirty逐hash一致、HEAD是祖先；实际开发HEAD/远端一致。看板旧生成块已刷新并Check，替换前旧结论在该快照board.md。唯一写者仍本聊天，无在途页面/模拟器/隔离MySQL专项；既有3306/13316/ADB不操作。新增TEMP/TMP/Java临时/可配构建缓存及证据仍D盘；完整C/D磁盘、进程/监听/全部dirty及实际范围提交回执见本批precommit/postcommit快照。无产物清理，本机证据未异地备份。

下一最小子项：原FORMAL/TEMP准备库存/可用周期/计划与原凭据摘要绑定，不能只有占用和权限就证明原作业；同时设计全新隔离原资料采集与独立pin保存，再接跨截点真实恢复/闭会话事务/故障围栏至接收最终提交。高轮原激活/ACK/累计基线、成功/取消/更正重建、产品入口和D08–D18仍缺。D04候选排除/D05取消释放缺政策、原资料/旧BCD/526及历史失败/恢复限制/C01–C08保持，不转成现场待办。

生产默认/签名和七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。总目标active、未完成。

本批9条明确路径已提交推送`0a9529b8c7819f9332f23018026ea1d8e573cf15`，实际远端一致，commit/push原回执在`source-state-precommit-02`。提交后24条全部dirty副本/hash/环境在`source-state-postcommit-03`，22条继承hash不变；C/D余量82565529600/99238531072字节。随后只补本页/看板实际Git回执，无业务输入变化，不重跑已验范围。
