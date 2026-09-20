# C0 交付与验证记录

2026-09-20，W1。基线 `47dc8f2`，独立分支 `codex/r06-command-vectors`，工作树 `D:/acrylic-pda-worktrees/r06-command-vectors`。本目录已完成离线向量与文档，按总调度提交推送后交W3独立审查；提交状态以Git实际记录为准。新字段、hash域与签名audience均为待后续实现采纳的固定提案，未接生产API、业务写入、迁移或Android。

已准备30条规范化常量、命令/上下文变更、原回执/currentEffect分离及17份实际Java `OfflineSigner`签出的纯合成签名。私钥运行时创建于Java临时目录并在finally删除，Git只保存标明合成的公钥与签名，不读取原业务密钥。16个文件均位于本目录，无服务端测试或生产源码改动。

首轮轻量oracle编译成功，但Windows stdout默认编码导致Python按UTF8读取失败；仅将本目录oracle改为显式UTF8原字节输出后通过，未改生产类。实际验证日志在 ignored `.local/c0-vectors/first-check.log`：112项离线检查通过，ResourceWarning作为错误，1.208秒。规范化以人工固定预期字节及独立Node SHA常量为依据，同时调用当前未修改的Java `SubmissionJson`并与Python实现对照；签名由实际Java类生成、Python cryptography独立核验。未运行Gradle、Spring、数据库或HTTP。

最终补齐完整命令、nullable命令、派生控制和未签原回执字节向量，并明确严格布尔/字段边界。`.local/c0-vectors/final-check.log`记录118项通过，0失败/错误/跳过，1.267秒；ResourceWarning作为错误。测试每次轻量编译并调用当前6个未修改生产类及Java oracle，实际对照生产规范化；固定签名由Java生成后以Python cryptography验证，未重新生成夹具来使测试通过。

提交前主调度指出通用reason非空检查会给NORMAL_CONFIRM增加未确认门槛，已按族拆分：正常确认可为空字符串，其余非空规则仅为本向量子集范围，后续执行者仍按既有规则采纳。新增正常确认空理由向量后，最终 `.local/c0-vectors/final-check-reason.log` 实际119项全通过，0失败/错误/跳过，1.250秒；前述118项作为前一轮记录保留。

静态核对：9份Python均通过AST、200行文件/30行函数限制；4份JSON可解析，实际Markdown链接存在；无私钥标记及NUL字节。提交前范围护栏首次把16文件误写为13，命令在暂存前立即停止，核对精确清单后更正；不是业务验证失败。首轮编码失败与早期112项结果保留，不冒充最终118项。

README明确离线调用者提供的currentWriteAllowed/currentReadAllowed/saved上下文不能上传自授权；生产须服务端现时鉴权和锁内核对。originalOperatorId、权限快照、session/device、decision等目前仅有签名完整性，没有连接可信服务器原事实的语义验证。17个签名样例使用已有canonical，nullable场景只覆盖字段和规范字节；不声称Java生产命令校验、数据库台账/锁、真实丢响应/登录/持久化或Android验收已实现。

中途按主调度只读审查E1冻结19文件，26项IT/8项单元作者证据已核对，无阻断；不混算为C0测试。后续由W3审本批，发现问题由W1处理；第三轮整合与总契约登记由总调度另派。原业务密钥/库、设备、现网和审批边界保持。
