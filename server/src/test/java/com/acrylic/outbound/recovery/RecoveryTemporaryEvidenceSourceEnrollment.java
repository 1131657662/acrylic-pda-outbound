package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit DML-only original account enrollment for a new isolated source. The root CLI exits before boundary capture. */
final class RecoveryTemporaryEvidenceSourceEnrollment {
    static void enroll(JdbcTemplate jdbc,Path attempt,RecoveryTemporaryEvidenceDatabaseSeed fixture) throws Exception {
        var id=fixture.initial.expected(); String key=Base64.getEncoder().encodeToString(fixture.evidence.key.getPublic().getEncoded());
        jdbc.update("INSERT INTO user_role(user_id,role_id) SELECT UUID_TO_BIN(?),id FROM role WHERE code IN ('WAREHOUSE_ADMIN','TEMPORARY_ORDER_CREATOR')",
            id.operatorId().toString());
        jdbc.update("UPDATE app_user SET auth_version=3 WHERE id=UUID_TO_BIN(?)",id.operatorId().toString());
        jdbc.update("UPDATE device SET public_key=?,key_sha256=?,temporary_order_enabled=1,row_version=7 WHERE id=UUID_TO_BIN(?)",key,
            com.acrylic.outbound.devices.DeviceCrypto.sha(key),id.deviceId().toString());
        String schema=jdbc.queryForObject("SELECT DATABASE()",String.class), account=jdbc.queryForObject("SELECT CURRENT_USER()",String.class);
        require(schema.matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,3}") && account.equals(schema.replace("acrylic_","")+"@127.0.0.1"),"EVIDENCE_TEST_ACCOUNT");
        String target="'"+account.substring(0,account.indexOf('@'))+"'@'127.0.0.1'";
        schema(attempt,schema);
        Path input=attempt.resolve("private/evidence-constrain.sql");
        Files.writeString(input,"REVOKE ALL PRIVILEGES ON "+schema+".* FROM "+target+";\nGRANT SELECT,INSERT,UPDATE,DELETE ON "+schema+".* TO "+target
            +";\nGRANT SELECT(User,Host,account_locked) ON mysql.user TO "+target+";\nGRANT SELECT ON mysql.role_edges TO "+target
            +";\nALTER USER 'root'@'localhost' ACCOUNT LOCK;\n",StandardOpenOption.CREATE_NEW);
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","--defaults-file="+attempt.resolve("private/root-client.cnf"),
            "--protocol=TCP","--host=127.0.0.1","--port=19330").redirectInput(input.toFile())
            .redirectOutput(attempt.resolve("private/evidence-constrain.out").toFile()).redirectError(attempt.resolve("private/evidence-constrain.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }
    private static void schema(Path attempt,String schema) throws Exception {
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe","--defaults-file="+attempt.resolve("private/root-client.cnf"),
            "--no-data","--no-tablespaces","--set-gtid-purged=OFF","--routines","--events","--triggers",schema)
            .redirectOutput(attempt.resolve("evidence-original-schema.sql").toFile()).redirectError(attempt.resolve("private/evidence-schema.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }
    static RecoveryPermissionContinuity.Request retain(Path attempt,RecoveryPermissionBoundary.Request expected,
            RecoveryPermissionBoundary.Observation begin,RecoveryPermissionBoundary.Observation end) throws Exception {
        var value=readCanonical(end.originalBytes()); byte[] log;
        try(var input=Files.newInputStream(attempt.resolve(text(value,"binlogFile")))) { log=input.readNBytes(Math.toIntExact(revision(value,"binlogPosition"))); }
        Files.write(attempt.resolve("evidence-permission-begin.json"),begin.originalBytes()); Files.write(attempt.resolve("evidence-permission-end.json"),end.originalBytes());
        Files.write(attempt.resolve("evidence-permission-binlog.bin"),log);
        return new RecoveryPermissionContinuity.Request(expected,begin.originalBytes(),begin.originalSha256(),end.originalBytes(),end.originalSha256(),log,sha256(log));
    }
}
