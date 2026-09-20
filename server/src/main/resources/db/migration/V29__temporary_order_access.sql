SET SESSION lock_wait_timeout = 10;

ALTER TABLE device ADD COLUMN temporary_order_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_device_temporary_order CHECK(temporary_order_enabled IN (0,1));
ALTER TABLE device_command_receipt ADD COLUMN temporary_order_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_device_receipt_temporary_order CHECK(temporary_order_enabled IN (0,1));

INSERT INTO permission VALUES
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000043'),'temporary_order:manage','管理临时订单人员与设备准入'),
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000044'),'temporary_order:create','创建临时订单'),
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000045'),'temporary_order:confirm','确认临时订单出库'),
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000046'),'temporary_order:link','关联临时订单与正式订单');
INSERT INTO role VALUES
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000060'),'TEMPORARY_ORDER_CREATOR','临时订单建单人员'),
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000061'),'TEMPORARY_ORDER_CONFIRMER','临时订单确认人员'),
    (UUID_TO_BIN('01994300-0000-7000-8000-000000000062'),'TEMPORARY_ORDER_LINKER','临时订单关联人员');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE (r.code='WAREHOUSE_ADMIN' AND p.code='temporary_order:manage')
       OR (r.code='TEMPORARY_ORDER_CREATOR' AND p.code='temporary_order:create')
       OR (r.code='TEMPORARY_ORDER_CONFIRMER' AND p.code='temporary_order:confirm')
       OR (r.code='TEMPORARY_ORDER_LINKER' AND p.code='temporary_order:link');
