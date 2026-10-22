# PDA 领取确认前复核仓库绑定

## 改动

`PreparationRepository.claim()` 在写入或替换本地领取意图前重新请求 `claim-preview`。本次复核必须同时满足 `status=READY`、预览订单ID与当前订单一致、预览 `warehouseId` 与本地已保存会话仓库一致；缺字段、跨仓或状态/订单变化均拒绝继续。预览 GET 与仓库校验本身不写 Room 或发出领取 POST。

服务端读取路径以已认证 `pda:assign` 仓库范围调用订单详情；OpenAPI preview 契约要求 `warehouseId`。既有 `PdaAssignmentIT` 检查预览真实仓库、读取不产生分配、领取后状态冲突及跨仓读取404。本次只读检查源码与契约，未重跑服务端集成测试。

## 定向验证

Android `ClaimPreviewRepositoryTest.claimRecheckRejectsMissingOrForeignWarehouse` 使用成功仓库匹配、缺少仓库和外仓三个输入。最终隔离运行 Gradle exit 0，XML 1 test method / 0 failures / 0 errors / 0 skipped。源、守卫、测试SHA-256分别为：

- `428863817993739e6b6854a100576b9620edf9cf440600d4e541c0ea70e2aa82`
- `107b916242246b1f46e3387b4a96d041b2d2954b4868d88fffa45699571864be`
- `85c9f19feccad6aacee6b66f4add39cb2efcd3a4c608a4e2c3791e7e6234b9fa`

39份Room schema路径/长度/SHA前后相同。详细命令、输入/输出清单和日志见 `.local/goal-20261021-resume/claim-preview-warehouse-guard01/attempt07/closeout.md`。本次重跑复用attempt03的Gradle输出目录，覆盖了第一次通过时的XML字节；首次XML哈希 `6fb32794aa29e615522af6e1bc0b126a72c6c50d213d67f94cc0a51c98781864`、统计1/0/0/0保留为文字，原XML字节未保留。最终XML当前哈希 `af49872e5eb636fac42277ad48f32f05a82d7038344f8263a2d553811e7e3247`。这是证据保存缺口，不将旧XML称为已保留。

## 限制

这是客户端守卫的定向主机单测，不证明M71设备结果、测试服务实际部署版本、真实HTTP组合或领取成功。本次未连接数据库/服务、未操作M71、未部署、未签名、未构建或安装APK。问题诊断与业务范围见 [M71领取预览诊断](GOAL_PDA_CLAIM_PREVIEW_WAREHOUSEID_DIAGNOSIS_2026-10-21.md) 和 [正式订单预览报告](GOAL_PDA_FORMAL_ORDER_PREVIEW_UI_2026-10-21.md)。本报告不构成D/O/U或第8节验收进展。