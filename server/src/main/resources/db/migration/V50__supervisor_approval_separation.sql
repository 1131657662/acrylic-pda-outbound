-- R07-5 / A2.1 supervisor approval separation (裁定 2026-09-30)
--
-- 业务裁定（用户 2026-09-30）：
--   换机/更正审批主体 = 新增独立 SUPERVISOR 角色，与 WAREHOUSE_ADMIN **分离**；
--   可批范围 = 更正（correction:approve）+ 换机（order:handover）；
--   范围/额度限制暂不设（不做新数据模型）；代理/升级暂不支持；
--   两个草案权限码（outbound:correct / correction:approve）**定稿**；
--   WAREHOUSE_ADMIN 保留库存管理职责，**不再持有上述审批权**（不并存）。
--
-- 本迁移只做"角色/权限映射与审批门绑定"，**不改业务规则本身**（政策 2026-09-26 已定）。
--
-- 更正内核的进门码已按命令类型分离（裁定 B，2026-09-30）：审批类命令（APPROVE/WITHDRAW）只要求
--   correction:approve，其余更正命令仍要求 outbound:correct；因此"主管只持审批码"即可批单，
--   WAREHOUSE_ADMIN 只持 outbound:correct 也仍能建档。maker-checker 由内核另行强制（建档人不得自批）。
--
-- 一并处理 order:resume（复核结论，理由见 docs/history/R09_C2_A2_1_SUPERVISOR_SEPARATION_*.md）：
--   HandoverCommand.java:78/84 的审批门是 `order:handover` **OR** `order:resume`（V44 时刻意如此）。
--   若只把 order:handover 切给 SUPERVISOR 而 order:resume 仍留在 WAREHOUSE_ADMIN，则该管理员**仍能通过
--   换机审批门**——分离即失效。因此 order:resume 必须一并切给 SUPERVISOR；审批门代码**一行不动**。
--
-- order:recall / order:dispose **留在 WAREHOUSE_ADMIN**：
--   order:dispose 是"批准卸货释放或实物隔离凭据"，属**实物证据动作**，不是"批准恢复"，故不动；
--   order:recall 虽是审批类语义，但把它一并切走会让"召回者 ≠ 续作者"成为新政策，**超出本次裁定**，
--   故本迁移不动它，并作为**待决事项**登记（见记录文档）。
--
-- 影响面：仅 role / role_permission / permission.name 三个表的相关行；**不新增/不删除权限码**，
--         不改表结构，不改默认开关，不涉及业务数据表。
-- 幂等性：role 插入用 NOT EXISTS；绑定用 INSERT IGNORE + DELETE 组合，可安全重放。
-- 失败保护：末尾用"断言失败即除零报错"让迁移真的失败（不用存储过程，避免 DELIMITER 分割问题）。
-- 回滚：见文件末尾。

-- 1) 新增独立 SUPERVISOR 角色（UUID 沿用 V29 的 01994300-…-00000000006x 号段，取未用的 000063）
INSERT INTO role (id,code,name)
SELECT UUID_TO_BIN('01994300-0000-7000-8000-000000000063'),'SUPERVISOR','主管（换机/更正审批）'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code='SUPERVISOR');

-- 2) 审批门切到 SUPERVISOR：从 WAREHOUSE_ADMIN 收回三个审批码
--    2a) order:handover
DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
WHERE r.code='WAREHOUSE_ADMIN' AND p.code='order:handover';
--    2b) order:resume —— 必须一并收回，否则该管理员仍可通过换机审批门（OR 条件）
DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
WHERE r.code='WAREHOUSE_ADMIN' AND p.code='order:resume';
--    2c) correction:approve
DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
WHERE r.code='WAREHOUSE_ADMIN' AND p.code='correction:approve';

-- 3) 授予 SUPERVISOR：order:handover + order:resume + correction:approve
--    （order:resume 的授予使 HandoverCommand 的 OR 门**无需改代码**即可工作）
INSERT IGNORE INTO role_permission (role_id,permission_id)
SELECT r.id,p.id FROM role r CROSS JOIN permission p
WHERE r.code='SUPERVISOR' AND p.code IN ('order:handover','order:resume','correction:approve');

-- 4) 两个草案权限码**定稿**：只登记语义（不改绑定语义、不改码名）
UPDATE permission SET name='更正主管审批（已定稿；审批主体＝SUPERVISOR）'
WHERE code='correction:approve';
UPDATE permission SET name='审批已出库交接（已定稿；审批主体＝SUPERVISOR）'
WHERE code='order:handover';

-- 5) 失败保护：断言 SUPERVISOR 恰好持有 3 个审批码，且 WAREHOUSE_ADMIN 一个都不持有。
--    条件不成立时除零报错，使该迁移失败并回滚（避免"静默 0 行"重演 V44/V45 注释里记录的事故）。
SELECT IF(
    (SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
      WHERE r.code='SUPERVISOR' AND p.code IN ('order:handover','order:resume','correction:approve')) = 3
    AND
    (SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
      WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('order:handover','order:resume','correction:approve')) = 0,
    1,
    1 / 0) AS v50_supervisor_separation_ok;

-- 回滚（人工，需显式执行）：
--   INSERT IGNORE INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
--     WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('order:handover','order:resume','correction:approve');
--   DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE r.code='SUPERVISOR';
--   DELETE FROM role WHERE code='SUPERVISOR';
--   UPDATE permission SET name='更正主管审批（R07-3，草案）' WHERE code='correction:approve';
--   UPDATE permission SET name='审批已出库交接（原机可用/更正前置）' WHERE code='order:handover';
