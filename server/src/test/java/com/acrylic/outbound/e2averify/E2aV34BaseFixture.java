package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Creates only synthetic V1-V34 facts required by the E2a seed. */
final class E2aV34BaseFixture {
    private E2aV34BaseFixture() { }

    static Map<String, String> ensure(JdbcTemplate jdbc, Path storage) throws Exception {
        String user = uuid(), mismatchUser = uuid(), warehouse = uuid(), device = uuid(), product = uuid(), importId = uuid(), snapshot = uuid(), bale = uuid();
        String policy = uuid(), order = uuid(), revision = uuid(), publication = uuid(), assignment = uuid(), authorization = uuid();
        byte[] csv = "delivery_code,model,count\nE2A,E2A-MODEL,1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path csvFile = storage.resolve("e2a.csv");
        Files.createDirectories(storage);
        Files.write(csvFile, csv, java.nio.file.StandardOpenOption.CREATE_NEW);
        String csvSha = sha(csv);
        jdbc.update("INSERT INTO warehouse(id,code,name,mapping_revision) VALUES(UUID_TO_BIN(?),?,?,0)", warehouse,"E2A-"+warehouse.substring(0,8),"E2a Synthetic Warehouse");
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(UUID_TO_BIN(?),?,?,?,UTC_TIMESTAMP(3))",user,"e2a-"+user,"E2a Synthetic User","synthetic");
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(UUID_TO_BIN(?),?,?,?,UTC_TIMESTAMP(3))",mismatchUser,"e2a-mismatch-"+mismatchUser,"E2a Synthetic Mismatch User","synthetic");
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",user,warehouse);
        jdbc.update("INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?, 'ACTIVE',1,UUID_TO_BIN(?),UTC_TIMESTAMP(3))",device,warehouse,"E2A-DEVICE-"+device.substring(0,8),"E2a Device","synthetic","a".repeat(64),user);
        jdbc.update("INSERT INTO product(id,model,name) VALUES(UUID_TO_BIN(?),'E2A-MODEL','E2a Product')",product);
        jdbc.update("INSERT INTO inventory_import(id,warehouse_id,original_filename,file_key,file_sha256,parser_version,status,total_rows,valid_rows,error_rows,duplicate_rows,ignored_rows,product_count,total_weight_g,created_by,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'e2a.csv','e2a.csv',?,'e2a','READY_FOR_REVIEW',1,1,0,0,0,1,1000,UUID_TO_BIN(?),UTC_TIMESTAMP(3))",importId,warehouse,csvSha,user);
        jdbc.update("INSERT INTO delivery_mapping(id,warehouse_id,delivery_code,version,product_id,is_active,enabled,source_import_id,created_by,created_at,reason) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'E2A',1,UUID_TO_BIN(?),1,1,UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3),'synthetic')",uuid(),warehouse,product,importId,user);
        String mapping = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM delivery_mapping WHERE warehouse_id=UUID_TO_BIN(?)",String.class,warehouse);
        jdbc.update("INSERT INTO inventory_snapshot(id,warehouse_id,version,import_id,status,row_count,total_weight_g,mapping_revision,manifest_sha256,published_by,published_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),'ACTIVE',1,1000,1,REPEAT('c',64),UUID_TO_BIN(?),UTC_TIMESTAMP(3))",snapshot,warehouse,importId,user);
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),1,UTC_TIMESTAMP(3))",bale,"E2A"+bale.replace("-","").substring(0,8),warehouse);
        jdbc.update("INSERT INTO inventory_snapshot_bale(snapshot_id,bale_id,delivery_code,product_id,model_snapshot,name_snapshot,weight_g,raw_weight) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'E2A',UUID_TO_BIN(?),'E2A-MODEL','E2a Product',1000,'1000')",snapshot,bale,product);
        jdbc.update("INSERT INTO inventory_position(bale_id,latest_snapshot_id,last_seen_snapshot_id,upstream_present,book_status,row_version) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'NOT_DEDUCTED',1)",bale,snapshot,snapshot);
        jdbc.update("UPDATE warehouse SET active_inventory_snapshot_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",snapshot,warehouse);
        jdbc.update("INSERT INTO policy_version(id,warehouse_id,version,auto_deduct_inventory,schema_version,offline_hours,short_shipment_policy,concurrency_mode,scan_mode,reason,published_by,published_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),1,0,1,48,'SUPERVISOR_APPROVAL','PHYSICAL_SEPARATION','AUTO_SCAN_MANUAL_CONFIRM','synthetic',UUID_TO_BIN(?),UTC_TIMESTAMP(3))",policy,warehouse,user);
        jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,row_version,created_by,created_at,updated_at,assignment_epoch) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'WEB','ASSIGNED',NULL,1,UUID_TO_BIN(?),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),1)",order,warehouse,"E2A-ORDER-"+order.substring(0,8),user);
        jdbc.update("INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),1,'E2A-VEHICLE','', 'synthetic',1,'synthetic',UUID_TO_BIN(?),UTC_TIMESTAMP(3))",revision,order,warehouse,snapshot,user);
        jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=UUID_TO_BIN(?)", order);
        jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) VALUES(UUID_TO_BIN(?),'E2A',UUID_TO_BIN(?),UUID_TO_BIN(?),'E2A-MODEL','E2a Product',1)",revision,mapping,product);
        jdbc.update("INSERT INTO order_publication(id,warehouse_id,order_id,revision,row_version,policy_id,auto_deduct_inventory,recovery_epoch,snapshot_json,reason,published_by,published_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,UUID_TO_BIN(?),0,1,'{}','synthetic',UUID_TO_BIN(?),UTC_TIMESTAMP(3))",publication,warehouse,order,policy,user);
        jdbc.update("INSERT INTO order_assignment(id,warehouse_id,order_id,revision,publication_id,device_id,operator_id,epoch,recovery_epoch,inventory_snapshot_id,policy_id,plan_sha256,inventory_sha256,start_order_scan_seq,allocation_version,status,area_note,issued_at,expires_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,UUID_TO_BIN(?),UUID_TO_BIN(?),REPEAT('d',64),REPEAT('e',64),1,0,'ACTIVE','synthetic',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY))",assignment,warehouse,order,publication,device,user,snapshot,policy);
        jdbc.update("INSERT INTO temporary_authorization(id,bundle_id,warehouse_id,device_id,user_id,authorization_epoch,user_auth_version,device_version,recovery_epoch,inventory_snapshot_id,mapping_revision,policy_id,issued_at,expires_at,key_id,signed_token,manifest_text,manifest_sha256,signed_manifest) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,1,1,UUID_TO_BIN(?),1,UUID_TO_BIN(?),UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 172800 SECOND),'e2a','synthetic','{}',REPEAT('f',64),'synthetic')",authorization,uuid(),warehouse,device,user,snapshot,policy);
        String scope = uuid(), submission = uuid(), client = uuid();
        jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",device,client,authorization);
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,creator_device_id,client_order_id,canonical_order_id,mapping_version,current_submission_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),NULL,1,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,NULL)",scope,warehouse,device,client,order);
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,temporary_authorization_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,receipt_text,created_at,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),NULL,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,'REVIEW_REQUIRED',NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",submission,scope,authorization,device,user);
        jdbc.update("UPDATE submission_scope SET current_submission_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",submission,scope);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("warehouseId", warehouse); result.put("orderId", order); result.put("deviceId", device);
        result.put("operatorId", user); result.put("mismatchUserId", mismatchUser); result.put("assignmentId", assignment);
        result.put("authorizationId", authorization); result.put("baleId", bale); result.put("csvFile", csvFile.toString());
        result.put("csvSha256", csvSha);
        return result;
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String sha(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
