# 正式单明细订单号与领取前真实预览修复

## 范围与来源

M71原发现引用.local/pda-staging-test-config-20261020/order-run01/REPORT.md、EVIDENCE_INDEX.md及coordination-20261021/DEVELOPMENT_HANDOFF.md。原真机失败/限定通过、同步404/409和FLAG_SECURE证据限制保持；本片不操作M71、原单、会话或原tar，不追认真机整改通过。原件含身份信息不提交。

输入提交94a67f4d3e08cd3a92bfdb47c32d58d2ce13cccc，仅叠加overlay-paths.json明确19路径；全部继承WIP排除。证据根.local/goal-20261021-resume/m71-order-ui01。原字节/SHA在preimage.zip/json和additional-preimage.zip/json，隔离source.zip/final-overlay-manifest.json与aux-inputs.json保留。

## 改动

正式明细由assignmentId/orderId关联prepared_order原下载计划，再校验原分配和计划hash后读取orderNo；不迁移表、不按车号或内部ID编造、不写扫描记录。缺订单号明确显示缺失并提示保留资料核对，关联/hash异常拒绝读取。WorkDetailsText统一渲染真实订单号，Room重开后仍读原计划。

领取保持现有对话框，先真实GET claim-preview；加载中、读取失败、缺仓库/产品或计划量不一致禁用确认。显示订单号、车号、仓库、总计划量及最多五类产品摘要（其余明确计数）；取消不创建领取意图，不POST。确认后沿既有claim路径再次核服务器版本/状态并用原幂等意图发POST；重复点击只调用一次。

服务端只读预览保留原扁平头字段，追加真实仓库和原发布版本产品lines；保留pda:assign、仓库隔离、READY核验，原领取写入和版本资格不改。补齐原先缺失的预览OpenAPI入口和字段，契约事实见看板生成块。旧test服务未部署新响应前，新包可能明确阻断缺失预览信息，不能声称服务已生效；本次无部署授权。

## 实际验证

- android01：编译完成，19测试/5失败/0错误/0跳过，exit1。五项原Room打开失败，长临时路径；预览对话框4项和卡片6项通过，原日志/XML保留。
- android02：相同产品源、短D盘临时目录，四类19/0/0/0、exit0；原XML在original-xml-before-boundary.zip及xml-summary.json。覆盖原Room重开订单号、缺失/错配、页面摘要、取消、网络失败、重复点击、原分页等。
- android03：追加网络边界测试，四类17/0/0/0、exit0；本轮XML已归档android03/original-xml.zip，哈希和逐suite原件见xml-archive.json。真实MockWebServer回环GET不产生claim_intent；确认发送版本复核GET、设备GET及单次带幂等键POST；版本/仓库错配只GET并拒绝。是客户端边界替身，不冒充真实服务端HTTP组合。
- server01：新隔离库7项/4失败，exit1，领取因未配隔离测试签名返回SIGNING_KEY_UNAVAILABLE；预览/无权边界已执行，不修改产品默认。
- server02：新隔离库，运行器误用PEM而产品要求DER，7项/4失败、exit1；原结果及configuration-error.md保留，未改在途密钥。
- server03：新空隔离库、独立随机合成DER签名身份，真实MySQL+MockMvc七项7/0/0/0、exit0。预览含真实仓库/原产品且分配仍0；确认后原订单号/车号/计划量一致，已领取冲突、外仓拒绝、原键恢复和签名回归通过。不是TCP或真机验收。

三新库均身份核验、dump并认证停机，19401/19402/19403无监听，原失败保留。全部输入输出SHA见validation-summary.json及最终manifest；新测试身份只在.local，不是正式签名或原身份。

## 未验与后续

33份非看板继承SHA保持；原最终两APK和M71安装包未覆盖。新增凭据交互源码审查已接看板，独立跟踪于GOAL_PDA_CREDENTIAL_UX_INTAKE_2026-10-21.md，本片未实现、不计验收。下一步固定本提交干净归档统一构建staging APK及独立server产物；不得把APK当服务端修复。M71整改结果、真实HTTP组合、完整D/O/U及第8节另验，总目标active未完成。

原库/现网、正式签名、main、生产开关、部署、Release、APK上传与更新页/清单保持关闭，localDeletionAllowed=false。本片未清理，释放0；既有自动审批拒绝的同清理目标不换包装重试。
