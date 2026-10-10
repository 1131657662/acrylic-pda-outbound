package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Constrains the actual development Android identity without rewriting its roles, key or credential versions. */
public final class RecoveryReceivedCheckpointEnrollment {
    public record Begin(RecoveryDeviceRegistrationCapture.Database database, RecoveryDeviceRegistrationCapture.Observation registration,
            RecoveryPermissionBoundary.Request expected, RecoveryPermissionBoundary.Observation boundary, byte[] origin, Path originals) { }

    public static Begin prepare(JdbcTemplate jdbc, Path attempt) throws Exception {
        var input=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/android-http-input.json")));
        var session=input.path("session");
        var db=new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid",String.class)),
            jdbc.queryForObject("SELECT DATABASE()",String.class),text(session,"serverRecoveryEpoch"));
        var id=new RecoverySourceIdentity("TEMP",uuid(session,"warehouseId"),UUID.randomUUID(),UUID.randomUUID(),uuid(session,"deviceId"),
            uuid(session,"userId"),uuid(input.path("receipt"),"authorizationId"),null,uuid(session,"deviceId"),UUID.randomUUID(),db.sourceRecoveryEpoch(),1,0,1,0);
        var registration=new RecoveryDeviceRegistrationCapture(jdbc.getDataSource()).capture(new RecoveryDeviceRegistrationCapture.Request(db,id.warehouseId(),id.deviceId()));
        var prep=new RecoveryTemporaryPreparationCapture(jdbc.getDataSource()).capture(new RecoveryTemporaryPreparationCapture.Request(db,id,input.path("activationCommand").toString()));
        dump(attempt,db.schema(),List.of("--no-data"),"checkpoint-schema.sql","private/root-client.cnf");
        triggers(jdbc,attempt);
        constrain(jdbc,attempt,db.schema());
        var expected=new RecoveryPermissionBoundary.Request(db,id.operatorId(),id.deviceId());
        var begin=new RecoveryPermissionBoundary(jdbc.getDataSource(),attempt).captureControlled(expected);
        Path originals=Files.createDirectory(attempt.getParent().resolve("received-checkpoint-originals-"+System.getenv("E2A_CROSS_RUN")));
        Files.write(originals.resolve(registration.originalSha256()+".json"),registration.originalBytes());
        Files.write(originals.resolve(sha256(prep.originalOrigin())+".bin"),prep.originalOrigin());
        Files.write(attempt.resolve("checkpoint-permission-begin.json"),begin.originalBytes());
        var snapshot=new com.acrylic.outbound.e2averify.E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")).capture();
        Files.writeString(attempt.resolve("checkpoint-snapshot.json"),snapshot.toPrettyString());
        dump(attempt,db.schema(),List.of("--no-create-info"),"checkpoint-data.sql","client.cnf");
        var after=new RecoveryPermissionBoundary(jdbc.getDataSource(),attempt).captureControlled(expected);
        assertThat(readCanonical(after.originalBytes()).path("binlogPosition")).isEqualTo(readCanonical(begin.originalBytes()).path("binlogPosition"));
        try(var output=Files.newOutputStream(attempt.resolve("checkpoint.sql"),StandardOpenOption.CREATE_NEW)) {
            Files.copy(attempt.resolve("checkpoint-schema.sql"),output); Files.copy(attempt.resolve("checkpoint-data.sql"),output);
        }
        Files.writeString(attempt.resolve("checkpoint.sha256"),sha256(Files.readAllBytes(attempt.resolve("checkpoint.sql"))));
        return new Begin(db,registration,expected,begin,prep.originalOrigin(),originals);
    }

    private static void triggers(JdbcTemplate jdbc,Path attempt) throws Exception {
        var rows=SubmissionJson.object().putArray("triggers");
        jdbc.query("SELECT TRIGGER_NAME,EVENT_MANIPULATION,ACTION_STATEMENT,ACTION_TIMING,ACTION_ORDER,DEFINER,SQL_MODE,CHARACTER_SET_CLIENT,COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME",
            rs -> { var row=rows.addObject(); for(int column=1;column<=rs.getMetaData().getColumnCount();column++) row.put(rs.getMetaData().getColumnLabel(column),rs.getString(column)); });
        var value=SubmissionJson.object(); value.set("triggers",rows);
        Files.writeString(attempt.resolve("checkpoint-triggers.json"),SubmissionJson.canonical(value));
    }
    private static void constrain(JdbcTemplate jdbc,Path attempt,String schema) throws Exception {
        String account=jdbc.queryForObject("SELECT CURRENT_USER()",String.class);
        require(schema.matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,3}") && account.equals(schema.replace("acrylic_","")+"@127.0.0.1"),"CHECKPOINT_TEST_ACCOUNT");
        String target="'"+account.substring(0,account.indexOf('@'))+"'@'127.0.0.1'";
        Path sql=attempt.resolve("private/checkpoint-constrain.sql");
        Files.writeString(sql,"REVOKE ALL PRIVILEGES ON "+schema+".* FROM "+target+";\nGRANT SELECT,INSERT,UPDATE,DELETE ON "+schema+".* TO "+target
            +";\nGRANT SELECT(User,Host,account_locked) ON mysql.user TO "+target+";\nGRANT SELECT ON mysql.role_edges TO "+target
            +";\nALTER USER 'root'@'localhost' ACCOUNT LOCK;\n",StandardOpenOption.CREATE_NEW);
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","--defaults-file="+attempt.resolve("private/root-client.cnf"))
            .redirectInput(sql.toFile()).redirectOutput(attempt.resolve("private/checkpoint-constrain.out").toFile())
            .redirectError(attempt.resolve("private/checkpoint-constrain.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }

    private static void dump(Path attempt,String schema,List<String> options,String name,String config) throws Exception {
        var args=new ArrayList<>(List.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe","--defaults-file="+attempt.resolve(config),
            "--single-transaction","--no-tablespaces","--set-gtid-purged=OFF","--skip-routines","--skip-events",
            options.contains("--no-data")?"--triggers":"--skip-triggers"));
        args.addAll(options); args.add(schema);
        var process=new ProcessBuilder(args).redirectOutput(attempt.resolve(name).toFile()).redirectError(attempt.resolve(name+".err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }
}
