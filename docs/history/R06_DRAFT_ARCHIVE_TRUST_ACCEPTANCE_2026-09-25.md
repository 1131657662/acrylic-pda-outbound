# R06 草稿停止中的历史审计归档信任

日期2026-09-25；当前单助手开发/自审，Android→Java为独立语言消费/真实HTTP组合。

## 完成标准与实现

V37原件集合中的ARCHIVE不再只检查外形。VERIFY和原VERIFY回执读取均通过`DraftEvidenceArchives`：上传公钥必须等于服务端配置的可信当前/历史公钥；原凭据等于服务端持久索引；原签名用途/设备/仓库/五年期限/禁止删除和原文件均通过既有`AuditArchiveReader`；归档内每条原事件须逐字节等于停止集合内对应AUDIT。

缺历史公钥、换公钥、缺索引、合法签名的跨仓凭据、快照事件替换、归档文件损坏均失败关闭。无迁移/协议/Room/code版本变化，不把上传公钥自证当信任根，不改变原件/回执，也不授业务能力。业务事实状态仍NOT_EVALUATED；其他未提交来源逐包业务事实是下一子项。

自审确认原件完整根/顺序校验仍保留，增加归档检查不替代它；全部读回重新核验，不能仅凭早先ORIGINALS_STORED状态返回成功。

## 本批验证与失败

- 定向服务端5XML/35项全过，包含新增7项真实RSA历史钥/文件/快照反例。
- attempt62失败：实际Android测试连接未调用响应恢复代次观察，AuditArchiveRepository正确拒绝“恢复代次尚未观察确认”。修复仅测试连接，用生产RecoveryProtection.observe处理真实响应；未绕过门禁。失败库/日志保留，已认证shutdown。
- attempt63：新独立非空V34 seed→V37 MySQL19330、真实HTTP19434；服务端IT1/1、Android1/1无skip。实际上传审计→取得真实归档→Room27产品停止→14原请求（含1归档），OPEN/ITEM/VERIFY各丢1回执，重开同键、只签一次、不重POST；换人切回仍暂停。
- 同场服务端重启并换新RSA钥、显式配置旧公钥：原准备/旧归档/旧回执仍核验；破坏原快照文件或归档文件时GET原VERIFY回执409，恢复原字节后返回原回执；DB不可变约束、恢复模式和无S/K/出库事实新增继续通过。
- 最终服务端85XML/584项：583pass、1符号链接条件skip、0失败/错误。Android111XML/444项：440pass、4专用HTTPskip；APK/lint任务通过，0error/fatal、189warning。APK是源码未变的UP-TO-DATE产物，SHA256仍`15ee10c18eeeacd695592608d33ca03e032f624717ec380c2ca3be2b40c50590`。不把条件跳过或未重构建写成新端到端通过。
- 本批未重跑其他三条Android HTTP链、Web、浏览器、模拟器或真机。实际原机Keystore/OS死亡/现场/长时、历史keyring生产部署及跨截点恢复未验。

## 输入和证据

两树分支/HEAD保持`codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`与`codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`；未提交/推送/整合/main/发布/部署。

`outputs/r06-product-20260925-01a0d8fa/archive-*`保存日志/XML/lint/两树路径快照；源码manifest1359文件 SHA256 `39e091687e8d8a7c862f59ca34630641d2626bc01af50308252d9b9a3423c236`，全套后复核无漂移。完整原件/私密资料留服务端`.local/e2a-cross-20260925-62/63`，认证shutdown；私钥/凭据不复制普通证据或Git。共享3306 PID5448未动。下一隔离run从64核目录分配。

保留localDeletionAllowed=false、七类生产决定/放行/关闭及TEMP生产归档边界；本批真实审计归档是既有隔离测试接口能力，不开放TEMP业务归档生产开关。
