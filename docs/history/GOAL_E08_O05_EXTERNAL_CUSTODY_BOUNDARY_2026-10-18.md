# E08/O05 外置保管边界复核（2026-10-18）

本切片只复核 O05 外置副本/保管条件，不把同机文件操作升级为外部备份通过。输入和
现有产品证据均保留，未启动服务、数据库或模拟器，未写入业务数据。

## 逐路径复核

| 路径 | 范围结论 | 实际事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `.local/goal-20261018-resume/physical-saf-run07/device-summary.json` | O02 物理 SAF 候选仍有效；O05 外置保管不成立 | M71 的 DocumentsUI `Download` URI 读回成功；`externalStorageVerified=false`、`usbHostReadback=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false` | 保留为设备端字节回读证据；等待真实受控外置介质和业务回执，不改生产权限 | Android SAF/`RescueFileTransfer` 读回、SHA/长度守恒；不计 O05 L2/L3 |
| `.local/goal-20261018-resume/physical-saf-run07/host-readback.pdarescue` | 同机 USB ADB 回读，不是外部介质 | 主机从 M71 拉取到项目 D 盘，SHA `052B3FC5598BC05A6D1FF3CB4436F664C474D085F4D06582F1EE6BA6AA4156C6`；来源为合成包 | 保留原字节和哈希；不得称 MTP/OTG、异地或独立保管 | 物理设备回读与输入字节一致性；不替代 O05 外部 custody |
| `.local/goal-20261018-resume/o02-cross-runtime/result.json` | O02/O05 同机复制候选有效但未闭合 | 加密副本与副本外摘要取回通过；`externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false` | 保留复制/取回拒绝边界；不得以同机目录证明丢失主机后的可取回性 | Android/Python 解密验签、复制、完整性和拒绝场景；不计 O05 整体 |
| `.local/goal-20261018-resume/o02-xlsx-run08/verification.json` | 电脑核验/XLSX 候选有效但不扩张 O05 | 五表逐值回读、零公式和 PNG 预览通过；来源信任为合成输入，业务回执未验证 | 保留 workbook、失败半目录和 CLI 阻断记录；不把 XLSX 当业务交付或外置副本 | Python 电脑消费者、失败无半成品；不计真实 Excel、外置介质或 D18 L3 |
| `docs/history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER05.md` | 取消范围边界仍有效 | recovered-confirmation worker/dialog/fixture/device-test/bridge 不应作为 O05 接收确认/激活链；只读 display/reader 可作 R09 候选 | 不恢复换机进度承接、多跳或接收者确认；沿 R09 只读恢复和 O05 副本边界推进 | 默认关闭构建/lint、只读输入守恒；不运行旧 run03 |

## 缺条件与拒绝依据

1. 当前没有用户可控制、与项目主机独立的 MTP/OTG/移动磁盘、第二设备或异地存储；M71 的
   ADB transport 和主机 D 盘属于同一开发环境。
2. 输入是 debug-only 合成救援包，没有原 PDA 身份、原设备密钥、现场业务回执或外部保管
   接收回执。因此不能证明主机损失后的独立取回，也不能为 D16 清理或 O05 生产归档提供资格。
3. `localDeletionAllowed=false`、生产默认关闭和原库/现网边界保持不变；本切片没有删除、覆盖
   或改写原资料、身份、唯一证据和失败输入。

## 重新入队条件

O05 下一次候选必须在新的编号和隔离路径中绑定真实外控介质或独立保管方、复制前后 SHA、
取回/损坏拒绝、保管与恢复回执，并在实际 Android、HTTP/MySQL、Chrome、Python、非空迁移和
恢复组合中复核。缺任何一项时保持拒绝并保全，不把本报告标为 O05 通过。

## 验证与保全

- 只读核对：`device-summary.json`、`o02-cross-runtime/result.json`、`o02-xlsx-run08/verification.json`、
  `physical-saf-run07/host-readback.pdarescue`；原文件和失败半目录未改动。
- 现场状态：M71 `22D2910042` 仍在线；没有停止共享 MySQL、ADB、Chrome、Node/Python 或其他监听。
- 该报告是 O05 边界审计开发备份，不计 O02/O05 完整范围、D11/D12/D14/D15/D17、E06/E07/E08 L2、
  第 8 节闸门或 D18 L3。
