package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

@Repository
public class TemporaryDuplicateQueries {
    private final NamedParameterJdbcTemplate jdbc;private final JsonText json;
    public TemporaryDuplicateQueries(NamedParameterJdbcTemplate jdbc,JsonText json) { this.jdbc=jdbc;this.json=json; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(o.id),o.order_no,o.status,o.current_revision,o.row_version,BIN_TO_UUID(r.id),
          r.vehicle_no,r.container_no,o.created_at,o.updated_at,BIN_TO_UUID(r.inventory_snapshot_id),r.mapping_revision,
          EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id),
          EXISTS(SELECT 1 FROM pc_work w WHERE w.order_id=o.id),
          EXISTS(SELECT 1 FROM submission_scope c INNER JOIN submission s ON s.scope_id=c.id
            WHERE c.source_order_id=o.id OR c.canonical_order_id=o.id),
          EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id)
        FROM outbound_order o INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
        WHERE o.warehouse_id=:warehouse AND o.source='WEB' AND o.created_at>=:start AND o.created_at<:end
          AND (BINARY r.vehicle_no=BINARY :vehicle OR EXISTS(SELECT 1 FROM order_line l WHERE l.revision_id=r.id AND l.product_id IN (:products)))
        """;
    MapSqlParameterSource parameters(UUID warehouse,Range range,com.fasterxml.jackson.databind.JsonNode context) {
        var products=new ArrayList<byte[]>();context.path("products").forEach(p->products.add(Ids.bytes(UUID.fromString(p.path("productId").asText()))));
        return new MapSqlParameterSource("warehouse",Ids.bytes(warehouse)).addValue("start",Timestamp.from(range.start()))
            .addValue("end",Timestamp.from(range.end())).addValue("vehicle",context.path("vehicleNo").asText()).addValue("products",products);
    }
    Snapshot snapshot(MapSqlParameterSource params) {
        var digest=digest();long[] count={0};
        jdbc.query(SELECT+" ORDER BY o.created_at DESC,o.id DESC",params,(RowCallbackHandler)rs->{
            digest.update(json.encode(head(rs)).getBytes(StandardCharsets.UTF_8));digest.update((byte)'\n');count[0]++;
        });
        return new Snapshot(count[0],HexFormat.of().formatHex(digest.digest()));
    }
    List<Head> page(MapSqlParameterSource params,TemporaryDuplicateCursor cursor,int limit) {
        String after="";
        if(cursor!=null) {
            after=" AND (o.created_at<:beforeTime OR (o.created_at=:beforeTime AND o.id<:beforeId))";
            params.addValue("beforeTime",Timestamp.from(cursor.createdAt())).addValue("beforeId",Ids.bytes(cursor.order()));
            if(jdbc.query(SELECT+" AND o.id=:beforeId AND o.created_at=:beforeTime",params,(rs,n)->head(rs)).isEmpty())
                throw new BusinessException(400,"INVALID_CURSOR","候选分页锚点不属于本次查询，请重新查询");
        }
        params.addValue("limit",limit+1);
        return jdbc.query(SELECT+after+" ORDER BY o.created_at DESC,o.id DESC LIMIT :limit",params,(rs,n)->head(rs));
    }
    Map<String,List<Product>> products(List<Head> heads) {
        var result=new HashMap<String,List<Product>>();if(heads.isEmpty())return result;
        var ids=heads.stream().map(h->Ids.bytes(UUID.fromString(h.revisionId()))).toList();
        jdbc.query("""
            SELECT BIN_TO_UUID(revision_id),delivery_code,BIN_TO_UUID(product_id),model_snapshot,name_snapshot,planned_count
            FROM order_line WHERE revision_id IN (:ids) ORDER BY revision_id,delivery_code
            """,new MapSqlParameterSource("ids",ids),(RowCallbackHandler)rs->result.computeIfAbsent(rs.getString(1),k->new ArrayList<>())
                .add(new Product(rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6))));
        return result;
    }
    private Head head(ResultSet rs) throws SQLException {
        return new Head(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getString(6),
            rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant(),rs.getTimestamp(10).toInstant(),rs.getString(11),rs.getString(12),
            rs.getBoolean(13),rs.getBoolean(14),rs.getBoolean(15),rs.getBoolean(16));
    }
    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
