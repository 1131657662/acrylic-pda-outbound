package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Full logical dump into a guarded empty schema, optionally on another dedicated test instance. */
final class E2aArchiveSnapshotRestore {
    private final E2aCrossSourceProfile profile;private final JdbcTemplate source;
    private final E2aArchiveRestoreTarget destination;
    final Path restored;final String url;
    E2aArchiveSnapshotRestore(E2aCrossSourceProfile profile,JdbcTemplate source) {
        this(profile,source,new E2aArchiveRestoreTarget(profile));
    }
    E2aArchiveSnapshotRestore(E2aCrossSourceProfile profile,JdbcTemplate source,E2aArchiveRestoreTarget destination) {
        this.profile=profile;this.source=source;restored=profile.attempt.resolve("restored");
        this.destination=destination;url=destination.url;
        assertThat(Files.exists(restored)).isFalse();
        try {
            Files.createDirectory(restored);Files.createDirectory(restored.resolve("config"));
            Files.createDirectory(restored.resolve("storage"));
        } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    JdbcTemplate restore() throws Exception {
        var target=new JdbcTemplate(new DriverManagerDataSource(url,destination.user,destination.password));
        identity(target);assertThat(target.queryForList("SHOW TABLES")).isEmpty();
        try(var files=Files.list(restored.resolve("storage"))){assertThat(files.count()).isZero();}
        var before=snapshot(source,profile.attempt.resolve("storage"));
        Path dump=profile.attempt.resolve("private/archive-snapshot.sql");
        var args=command("mysqldump");args.addAll(List.of("--single-transaction","--skip-lock-tables","--no-tablespaces","--set-gtid-purged=OFF",
            "--hex-blob","--skip-comments","--skip-add-drop-table","--order-by-primary","--complete-insert","--skip-extended-insert",profile.database));
        run(args,dump,null,"dump");assertThat(Files.size(dump)).isPositive();
        copyStorage(before.path("files"));
        for(String name:List.of("server-private.der","server-public.der"))Files.copy(profile.attempt.resolve("private/"+name),restored.resolve("config/"+name));
        assertThat(snapshot(source,profile.attempt.resolve("storage"))).isEqualTo(before);
        identity(target);assertThat(target.queryForList("SHOW TABLES")).isEmpty();
        args=command("mysql");args.addAll(List.of("--binary-mode","--default-character-set=utf8mb4",destination.database));
        run(args,null,dump,"restore");assertThat(snapshot(target,restored.resolve("storage"))).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("private/archive-snapshot-facts.json"),before.toPrettyString());
        Files.writeString(profile.attempt.resolve("archive-restore-summary.json"),"{\"tables\":"+before.path("tables").size()+",\"files\":"+before.path("files").size()+",\"status\":\"PASS\"}");
        return target;
    }

    ObjectNode snapshot(JdbcTemplate jdbc,Path storage) throws Exception {
        var result=new E2aMigrationSnapshot(jdbc,profile.attempt,storage).capture();result.remove("database");
        var schema=(ObjectNode)result.path("schema");var names=new ArrayList<String>();schema.fieldNames().forEachRemaining(names::add);
        for(String table:names)schema.put(table,schema.path(table).asText().replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_"," COLLATE utf8mb4_"));
        return result;
    }

    private void identity(JdbcTemplate jdbc) {
        destination.requireIdentity(jdbc);
    }

    private void copyStorage(com.fasterxml.jackson.databind.JsonNode manifest) throws Exception {
        Path storage=restored.resolve("storage");
        var names=manifest.fieldNames();
        while(names.hasNext()) {
            String name=names.next();Path target=storage.resolve(name).normalize();
            assertThat(target.startsWith(storage)).isTrue();Files.createDirectories(target.getParent());
            Files.copy(profile.attempt.resolve("storage").resolve(name),target);
        }
    }

    private List<String> command(String executable) {
        Path attempt=executable.equals("mysqldump")?profile.attempt:destination.attempt;
        int port=executable.equals("mysqldump")?E2aTestPorts.database():destination.port;
        return new ArrayList<>(List.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/"+executable+".exe",
            "--defaults-file="+attempt.resolve("private/client.cnf"),"--protocol=TCP","--host=127.0.0.1","--port=" + port));
    }
    private void run(List<String> command,Path output,Path input,String label) throws Exception {
        var builder=new ProcessBuilder(command);builder.environment().remove("MYSQL_PWD");
        Path login=profile.attempt.resolve("private/disabled-login-file");assertThat(Files.exists(login)).isFalse();
        builder.environment().put("MYSQL_TEST_LOGIN_FILE",login.toString());
        builder.redirectError(profile.attempt.resolve("private/"+label+".err.log").toFile());
        builder.redirectOutput((output==null?profile.attempt.resolve("private/"+label+".out.log"):output).toFile());
        if(input!=null)builder.redirectInput(input.toFile());
        var process=builder.start();assertThat(process.waitFor(60,TimeUnit.SECONDS)).as(label+" process completed").isTrue();
        assertThat(process.exitValue()).as(label+" exit; see protected log").isZero();
    }
}
