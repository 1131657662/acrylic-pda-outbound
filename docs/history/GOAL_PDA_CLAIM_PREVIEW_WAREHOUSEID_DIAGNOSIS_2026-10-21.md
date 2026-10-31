# M71 领取前核对缺少 warehouseId 诊断

## 现象与只读核对

用户报告 M71 领取时显示“领取前核对未完成，尚未领取。No value for warehouseId”。设备只读检查显示安装 APK 的 SHA-256 为 `b3d2d1f332c9ec0c7b0d295a98b9e1453d8c03dad409758bbcf6c0e2449c2d09`，与本机既有 `previous-app-staging.apk` 一致。构建审计记录中的另一 staging APK SHA-256 为 `204f758fdd91438215ad69402043ce4785d5b132bfde8e6d4ba62e3ee39b98ad`，设备未安装该字节版本。

`PreparationRepository.preview()` 对 `claim-preview` 响应调用 `getString("warehouseId")`；字段缺失时会抛出该 JSON 异常。`ClaimPreviewDialog` 捕获读取失败后保留“尚未领取”状态且不启用确认按钮，因此不会发送领取 POST。接口契约要求响应含 `warehouseId`，仓库当前服务端实现从已认证的 PDA 仓库上下文生成该字段。

历史统一构建报告已记录 test 服务尚未部署配套预览响应。本次未取得 M71 失败请求的原始 HTTP 响应体，也未读取或调用 test API；筛选后的设备 logcat 未提供相应响应证据。因此“test 服务端响应缺字段/版本落后”是与现象及历史记录吻合的高概率根因，尚未由线上响应直接证实。

## 结论与边界

当前客户端按契约 fail-closed，缺字段会阻止领取。不能通过客户端猜测仓库 ID 来替代服务端预览校验。需先在获准范围内核实失败 GET 的状态码和 JSON 字段；若确认为服务端缺字段，再部署与客户端契约匹配的 test 服务版本并验证预览响应及正常领取。部署该服务版本仍需单独授权。

本次仅作源码、构建审计和设备包元数据的只读核对；未改代码、未安装 APK、未触发业务操作、未访问或部署 test 服务。
