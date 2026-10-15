# E07 / O03 原件中转跨截点组合开发备份

固定主树 `D:/acrylic-pda-outbound`、`codex/development`；输入 `2fa5b2ef4f7992a5dae426fee1057483fa431b13`。本聊天唯一写者，清理聊天实际回执已释放且空闲；5,284项继承输入相对624提交后逐字节不变。旧看板、完整Git/hash/补丁/worktree/写权/环境在 `.local/goal-20261015/e07-relay-checkpoint01/resume*`。版本事实见看板生成块。

## 范围与复用

新增 `E2aRelayCheckpointIT.java` 和 `RelayCheckpointScenario.java`；不修改产品、迁移、契约或生产默认。改动路径→影响层→必跑清单、选择理由和逐路径SHA复用依据分别在该目录 `design.md`、`reuse-inputs.json`。沿既有包内FORMAL夹具，以标准隔离运行器追加新测试，不修改1504专用端口入口。

[O03正式报告](GOAL_E05_O03_ACCEPTANCE_2026-10-13.md)及未变源码按上述SHA复用其首次成功、PC/PDA并发、尾SQL回滚、实际锁等待后撤权、跨仓、旧资格与Android原机文件消费。此次新增跨截点实际恢复、原件/原回执消费与缺结果围栏；没有重跑或宣称替代原Android、Chrome、非空迁移整体和最终候选。

## 当次实际结果

新625实例 `.local/e2a-cross-20260925-625`，主schema及 `_missing` / `_restore` 均在同一独占新实例；MySQL19330、HTTP19434，身份、datadir、UUID、配置与端口见 `allocation625.json`、`preserve-625/identity.json`。未复用失败库。

- 编译通过；runner/test退出0，JUnit **1/0/0/0**，业务52.457秒。运行5,286项输入在 `review625.json` 全部未变。
- 合成登记FORMAL输入先接收未冻结原包，再保存非空前缀。之后通过真实HTTP接收冻结原件并首次SUCCEEDED，产生两笔dispatch/ledger；业务及中转2份RSA回执和原ZIP保留。合成身份/登记钥不称原PDA现场事实。
- `_missing` 从原dump实际恢复：后续relay处理及结果查询404；已有未冻结原件仍为EVIDENCE_ONLY、process409，原ZIP可逐字取回，目标订单出库/ledger均0，不补造submission或出库。
- `_restore` 从同一dump重放原ROW binlog到明确终点，179业务表/schema/迁移历史/触发器/storage精确一致。真实HTTP同接收键、同处理键双并发及原PDA原提交重放成功；原结果文件与ZIP逐字节一致，异文409、无中转权403，未冻结仍409。
- default-off重启后，有SUPERVISOR及无SUPERVISOR两主体分别真实POST接收/处理，全部409 `ORIGINAL_RELAY_DISABLED`，遵循原契约，不记控制器不存在或404。三组179业务表拒绝/重放前后守恒，仅允许已有session两列访问/到期时间变化，全部属性及其他表不变。
- 独立Python实际消费5,286输入、三库540表、登记设备EC签名/清单及2份RSA、同键/原PDA/原回执、HTTP错误码与数据引用，全部通过。实际prefix6文件、源/终点副本/重建各8文件长度及hash一致；钥副本同字节，应用仍使用该实例原开发签名路径，不另称历史钥还原正向。
- `preserve625.py` 保存三库540表、全库dump及830个文件hash，前后数据/storage守恒，认证停机、19330零监听；HTTP已关闭。原物料、日志/XML、签名及数据库全部保留。

## 失败、自审与输入

消费者首次把真实恢复登录新增的一条 `WEB_LOGIN` 当作前缀业务漂移；原日志/脚本在 `consumer625-failure01.log` / `consume625-before-prefix-login.py`，逐行诊断在 `consumer625-prefix-diagnosis.json`。确认无删除/覆盖、只一条与原中转主体相符的USER审计；修正为验证精确新增审计并严格比对其余表，复验通过，业务未重跑。一次诊断stdout GBK编码错误另记运行器事实；不清除历史失败。

作者自审见 `review625.md`；Python是独立算法消费者，非另一人员审查。命令、退出码、输入绑定、保全身份及磁盘见同目录；任务TEMP/TMP/Java tmp仅子进程指向D盘 `.local/t625`。

关键SHA256：
- 截点dump：`d08467053168558e37ffb95a6398343d34af810cc1e7140f0960a1cea78e493b`。
- 原binlog：`64138b8ce0244d9a70046a7eeccc8c75e7e77c2ddab8c3b382b658cbb60a7b94`。
- 全库保全dump：`f69bf16e3b6e3836219bb058278fa39002ecb85d2c1286995a821486be6534bc`。
- 独立消费者：`3846df85f5ffb88e69fa4df1d82e32cd44f28c0be3e187ea9f67f884be9e7407`；输入及结果原件另有hash快照。

## 未完成边界

本切片仅O03局部跨截点组合开发备份，**不计E07 L2、D14/D15整体、D18 L3或总目标通过**。O04受控独立来源、外置保管/回取、E07剩余组合、E08/E09及全部D/O/U闸门继续；原模拟器/设备与Chrome历史阻断保留。七类生产能力/TEMP归档及 `localDeletionAllowed=false` 不变；未接原库/现网、未合main、未部署发布或使用正式签名。
