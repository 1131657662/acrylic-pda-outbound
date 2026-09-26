# R06 真实决定独立协议消费者（限定内部范围）

本项在已有首轮内部确认与短装批准基础上，新增 Python 独立消费者；不改生产 handler、API、迁移或 Android。持续目标仍 active，未上线。

## 验收范围与实际实现

- 首映射 `mappingVersion=1`、首轮 `workRound=1`、完整默认候选为零的正常确认、独立短装批准、批准后确认。非零候选、未来映射/轮次明确拒绝，另列后续。
- 原命令、原冻结正文、执行前数据库列投影、原 actor 权限/仓库/角色及决定结果引用分别输入。Python 不调用生产 `CommandFacts`、`CommandIdentity`、codec 或产品/候选计算器，也不将完整生产回执复制为预期。
- Python 独立重建上下文、产品（ACTIVE/VOID/零实绩项）、数量/重量/短装数、北京时间接收日±7日候选窗、空候选 fingerprint/context hash、command/receipt 域摘要。数据库导出先保留同仓所有 WEB 当前计划，再由 Python 独立筛窗和车号/产品交集。
- 严格核原 UTF-8/hash、RS256/kid/issuer/audience、内外签名一致、完整 wire。NORMAL_CONFIRM 核另一路真实 PDA HTTP 成功原回执，并独立计算成功内容；服务端生成的完成时间只作执行前后时间窗验证，不宣称能够从原正文推导时间。
- 短装必须原操作员以外的主管；确认绑定完整原批准回执、原件 hash、上一决定版本、同正文/来源/候选，及批准人在确认前的有效权限/身份。currentEffect 不能改变原决定或授予能力。
- 所有生产能力继续 false，`localDeletionAllowed=false`；无新迁移（仍 V36）。旧 C1b 固定版本 `source_manifest.json`、向量和 FINAL_REVIEW 均未改写。

## 当次验证

1. 基础定向 Python 产品/候选测试：6/6，通过。
2. 服务端 compileTestJava：通过，日志 `outputs/r06-e2a-takeover-20260925-01a0d612/server-decision-compile.log`。
3. 最终 attempt43：专用 ACL/datadir/schema/user、19330 非空 MySQL，V34 seed→V36，binlog 开启；实际内部业务事务，真实 cookie/CSRF 登录和命令原件/detail GET、真实 PDA 成功 GET。原键重复执行保持逐字节原响应。IT **1/1**，其内独立 Python **46/46，无跳过**。
4. 正向 4 份原决定：正常、短装批准、批准后确认及另一来源独立批准。30 项合法重签语义错误分别计为测试；另有嵌套成功同步重签/改 HTTP、自批、错引用、有效跨来源批准、过期/撤权批准、错原始字节/历史 key/域、currentEffect 等子场景。子场景不另加到 46 分母。
5. 当前助手自审并修复空理由归一化；两树 diff --check、Python文件/函数大小检查通过。无独立人员复审；“独立”指消费者计算方法及隔离环境。

仅新增消费者/测试及工具开关，未改生产实现。服务端上项 494（493通过、1 Windows条件skip）、Web构建/Chrome、Android/Room23全套和APK/lint均为历史证据，本项未重跑，也不宣称本项全栈全套通过。

## 失败与证据

- attempt42：Java业务/HTTP已执行，Python42项中1失败、2错误（同一非短装原理由null被错误保留为null，实际协议为""）；不能写成通过。修复后保留原证据消费正向3份，再在新库attempt43全量复跑。
- attempt42与43均认证shutdown；失败数据库、原件、日志保留。不得复用这两个attempt。
- attempt43路径：`D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-43`，`decision-vectors.json`为合成身份下真实事务/HTTP产物，不是生产来源或独立人工授权。
- 最终XML SHA256：`496d1f61f436f563b50ef4b82ec4bf7a76fa8c77fdf05deb22b5ce49accf2eeb`；vectors SHA256：`50798aa487945a8a66aed7e0cf741ef2e3eda4b3fc83a48ab98f708176de7800`。
- 主树证据目录保存 `decision-attempt42-*`、`decision-attempt43-*` 和 `decision-source-manifest.json`（763文件，原始文件SHA256）。后者绑定未提交源码，不冒称HEAD已包含修改。private密码/私钥不复制。

## Git、后续与未验

主树 `codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`。未提交/未跟踪全路径见同目录 WORKTREE_STATUS。没有提交、推送、整合、main合入或部署。

下一接 R09 TEMP归档V2三文件及确认/短装决定引用恢复核验。非零候选决定消费者、产品成功/归档入口、映射2+、高轮次、排除/拒绝/处置续作、R07–R09其他可独立项仍未完成。真实历史keyring、独立人员、真实设备/OS死亡断电、长时、现场及审批仍未验；排除/拒绝权限政策未定，不擅自决定。
