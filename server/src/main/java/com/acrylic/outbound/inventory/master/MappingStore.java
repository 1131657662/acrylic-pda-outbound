package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.Page;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MappingStore {
    private final JdbcTemplate jdbc;
    public MappingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT BIN_TO_UUID(m.id),m.delivery_code,m.version,BIN_TO_UUID(m.product_id),p.model,p.name,
          m.enabled,m.reason,m.is_active,m.created_at FROM delivery_mapping m
          INNER JOIN product p ON p.id=m.product_id WHERE m.warehouse_id=?
        """;

    public List<Mapping> current(UUID warehouse) {
        return jdbc.query(SELECT + " AND m.is_active=1 ORDER BY m.delivery_code", this::map, Ids.bytes(warehouse));
    }
    public Page<Mapping> history(UUID warehouse, String code, long beforeVersion, int size) {
        int limit = Page.checkedLimit(size);
        if (beforeVersion <= 0) throw new com.acrylic.outbound.common.BusinessException(400, "INVALID_CURSOR", "版本游标无效");
        var rows = jdbc.query(SELECT + " AND m.delivery_code=? AND m.version<? ORDER BY m.version DESC LIMIT ?", this::map,
            Ids.bytes(warehouse), code, beforeVersion, limit + 1);
        return Page.from(rows, limit, Mapping::version);
    }
    public Page<Mapping> list(UUID warehouse, String afterCode, int size) {
        int limit = Page.checkedLimit(size);
        return Page.from(jdbc.query(SELECT + " AND m.is_active=1 AND m.delivery_code>? ORDER BY m.delivery_code LIMIT ?",
            this::map, Ids.bytes(warehouse), afterCode, limit + 1), limit, Mapping::deliveryCode);
    }
    public UUID revise(Change change) {
        UUID product = product(change.model(), change.name());
        jdbc.update("UPDATE delivery_mapping SET is_active=0 WHERE warehouse_id=? AND delivery_code=? AND is_active=1",
            Ids.bytes(change.warehouse()), change.code());
        UUID id = Ids.next();
        jdbc.update("""
            INSERT INTO delivery_mapping(id,warehouse_id,delivery_code,version,product_id,is_active,enabled,
              source_import_id,created_by,created_at,reason) VALUES(?,?,?,?,?,1,?,?,?,UTC_TIMESTAMP(3),?)
            """, Ids.bytes(id), Ids.bytes(change.warehouse()), change.code(), change.version(), Ids.bytes(product),
            change.enabled(), change.importId() == null ? null : Ids.bytes(change.importId()), Ids.bytes(change.actor()), change.reason());
        return product;
    }
    public void advance(UUID warehouse) {
        jdbc.update("UPDATE warehouse SET mapping_revision=mapping_revision+1 WHERE id=?", Ids.bytes(warehouse));
    }
    private UUID product(String model, String name) {
        jdbc.update("INSERT INTO product(id,model,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=id", Ids.bytes(Ids.next()), model, name);
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM product WHERE model=? AND name=?", String.class, model, name));
    }
    private Mapping map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Mapping(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getBoolean(7), rs.getString(8), rs.getBoolean(9), rs.getTimestamp(10).toInstant().toString());
    }
    public record Mapping(String id, String deliveryCode, String version, String productId, String model,
                          String name, boolean enabled, String reason, boolean current, String createdAt) { }
    public record Change(UUID warehouse, String code, long version, String model, String name, boolean enabled,
                         UUID importId, UUID actor, String reason) { }
}
