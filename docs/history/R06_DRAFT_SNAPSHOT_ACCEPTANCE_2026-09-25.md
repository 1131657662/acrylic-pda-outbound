# R06 未提交草稿原件结构闭合验收（2026-09-25）

本项接Room25原机签名，目标是核验原件集合确实匹配签名根、数量和全设备截点。不是服务器耐久接收或业务VERIFIED_COMPLETE。

## 实现、自审与边界

服务端新增LocalDraftStopSnapshotProtocol/Raw/Coverage，逐件核规范UTF-8/JSON（32MiB单件）、唯一kind/id、连续ordinal、原件SHA及canonical frame，流式重算128MiB/25万条限额下的root。核CAPTURE→可选SCAN→AUDIT→ARCHIVE顺序、全设备连续捕获/审计、本草稿nextScanSeq、COMMITTED与SCAN对应、SCAN/条码/周期/SAVED引用、唯一最后LOCAL_EVIDENCE_STOP及原evidenceId。

本来源和其他来源计数分离；其他来源没有因被包含在高水位而变成本草稿或K。归档核原proof/key规范字节、设备/仓库/epoch/段/recordId结构，但信任历史公钥及归档数据库原件仍须后续核验。准备资格真实性和其他来源业务事实同样未由本纯函数证明。

自审修复：非SAVED的TEMP审计补同来源/人员capture关联；FORMAL续作可以在新assignment/人员下引用旧capture，结构层保留此合法形态，后续必须用可信谱系核验。没有改旧E2a信任协议，没有生产决定/占用/放行能力。

## 当次实际验证

- Android6个原Room合成向量：空草稿、未决手输、ACTIVE、VOID加未决、其他TEMP来源、原审计归档，6/6；全套406项403pass/3专用HTTPskip，101 XML。
- 服务端首轮52/52，关联补强后53/53，最终549项548pass/1符号链接skip，80 XML。新结构核验27/27、原新签名协议28/28。
- 负例含重签坏集合、缺件/多件/乱序/重复/原件篡改/新摘要旧根/捕获和审计缺口/错停止事件/原机/资格/快照/来源/SCAN和SAVED/归档跨仓/非规范字节。合成FORMAL分支检验不当作实际FORMAL身份或HTTP证据。
- 当前助手作者/自审，无独立人员。Java消费Android原Room导出的无私钥向量为跨语言验证。
- 当次无新增失败。早前失败不删除。不把上项/历史报告当本次报告。
- Android生产输入未变，APK/lint引用签名子项（APK 3c417a206e448605d8af63ae61de820420e6f62fe16ca5385d8af772bab0cb9c），本项未重复构建。未运行MySQL/HTTP/Web/Chrome/真机。

## 证据

outputs/r06-e2a-takeover-20260925-01a0d612/android-draft-snapshot-focused1.log及XML、android-draft-snapshot-full.log及XML、server-draft-snapshot-focused1/2.log及XML、server-draft-snapshot-full.log及XML、draft-snapshot-vectors/。同六向量放server树test resources/e2a/local-draft-snapshots，无私钥或真实资料。

1291文件draft-snapshot-source-manifest.json SHA256 7bad5b34f5a6f7d4f6d2e7266dbe581fee2cf069de1412b73093ab4b1150e4ac。完整未提交清单见R06_DRAFT_SNAPSHOT_WORKTREE_STATUS_2026-09-25.md。

## 下一断点

可信准备/历史签名公钥、其他来源事实与原件数据库独立接收、真实HTTP、原键/丢回执/重启和产品同写者队列继续开发。真实PDA/Keystore/断电/现场/长时/审批仍未验。七类生产决定和TEMP生产归档/放行关闭，localDeletionAllowed=false。目标active，未提交/推送/整合/main/部署。
