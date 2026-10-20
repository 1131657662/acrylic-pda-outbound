# 正式单UI修复干净提交统一测试构建

输入为09615d490baab5753621e14903076775059bfb4c的git archive，证据根.local/goal-20261021-resume/unified-candidate01。未叠加33份非看板继承WIP或正式签名配置；source-manifest.json登记全部归档字节和SDK本机路径配置，构建后5936文件核同。源码与报告前片验证见[正式单UI修复](GOAL_PDA_FORMAL_ORDER_PREVIEW_UI_2026-10-21.md)。

## 实际构建与产物

- android01执行assembleStaging，exit0，38任务。实际APK路径android01/outputs/_app/outputs/apk/staging/app-staging.apk，14287274字节，SHA256 e5b85c270919031a54f99b80f769fd0d642c027ecacf8f90604603393df79ed8。
- 原test地址、环境、更新通道/可信公钥从先前获准配置读取，通过构建参数注入，不改源码默认。aapt/apksigner/dexdump核实际APK为staging包、原测试证书、取消三flag均false，配置/签名与输入一致。具体候选内版本及配置只在本地artifact-audit/apk-audit.json登记，版本事实入口见看板生成块。
- server01 bootJar exit1，92编译错误。运行器allprojects仅按p.path设置输出，服务root与included报表root均写outputs/_，导致报表类缺失；首轮日志和错误JAR保留，runner-failure.json登记。未改产品源码。
- server02改为rootProject.name+p.path隔离，新输出bootJar exit0，8任务。实际JAR server02/outputs/acrylic-outbound-server_/libs/acrylic-outbound-server-0.1.0.jar，280812111字节，SHA256 d20c2bac96a84fef475eb58d5b02e4513241b32aa7ca41fa59959e1e0d750715。ZIP核本片预览、D04审计修复及报表依赖实际类存在。

精确命令/参数、环境覆盖、开始结束时间、退出码和日志SHA在各轮command.json/result.json。输入输出总核验见artifact-audit/final-candidate.json；不是一次全部构建全绿，更不是业务验收。最终APK、JAR不覆盖旧产物，原两APK再次核SHA保持。

## 限制与接续

本片只构建，未重新运行业务测试，不能替代前片Android/MockMvc，也未取得TCP组合/M71/现场/恢复/非空迁移或最终D18结果。服务修复须独立server产物，APK不能部署服务；旧test缺预览字段时新APK会阻断，未声称线上生效。无安装/上传/下载页/清单/部署/Release，正式签名、main、原库/现网、生产开关保持关闭，localDeletionAllowed=false。

总目标active未完成。下一有界项为[凭据交互](GOAL_PDA_CREDENTIAL_UX_INTAKE_2026-10-21.md)的主管签名材料受控文件导入、用途/状态说明；安全生成及独立保管/遗失恢复、减少重复输入和分步导出仍须单独实现/验证，不因本片构建计通过。此片清理0字节，先前审批拒绝目标不改包装重试；空间、进程/监听和WIP收口保全在证据根。
