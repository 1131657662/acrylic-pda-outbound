package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.inventory.RestoredExportWorkerChecks;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** A separate restored service JVM, constrained to this one fresh lab. */
public final class E2aRestoredWorkerProcess {
    public static void main(String[] ignored) throws Exception {
        Path attempt=E2aRestoredWorkerConfig.attempt();
        String database=E2aRestoredWorkerConfig.database()+"_restore";
        String url=E2aRestoredWorkerConfig.databaseUrl(database);
        String user=E2aRestoredWorkerConfig.user(),password=System.getenv("E2A_CROSS_PASSWORD");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        identity(jdbc,attempt,database,user);
        assertThat(Files.exists(attempt.resolve("restored-worker-completed.marker"))).isFalse();
        try(var context=new SpringApplication(OutboundApplication.class).run(options(attempt,url,user,password))) {
            RestoredExportWorkerChecks.consume((WebApplicationContext)context,attempt);
        }
        Files.writeString(attempt.resolve("restored-worker-completed.marker"),"separate JVM: real restored job/renderer/HTTP download\n",
            StandardOpenOption.CREATE_NEW);
    }

    static void identity(JdbcTemplate jdbc,Path attempt,String database,String user) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(database);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()",String.class)).isEqualTo(user+"@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(E2aRestoredWorkerConfig.databasePort());
        assertThat(jdbc.queryForObject("SELECT @@log_bin",Integer.class)).isEqualTo(1);
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir",String.class)).toAbsolutePath().normalize())
            .isEqualTo(attempt.resolve("data"));
    }

    private static String[] options(Path attempt,String url,String user,String password) {
        Path restored=attempt.resolve("restored");
        return new String[]{"--server.address=127.0.0.1","--server.port="+E2aRestoredWorkerConfig.httpPort(),"--spring.flyway.enabled=false",
            "--spring.datasource.url="+url,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
            "--outbound.storage="+restored.resolve("storage"),"--outbound.signing.private-key="+restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+restored.resolve("config/server-public.der"),"--outbound.worker.enabled=false",
            "--outbound.bootstrap.enabled=false","--spring.session.jdbc.cleanup-cron=-","--server.servlet.session.cookie.secure=false"};
    }

    static void run(Path attempt) throws Exception {
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),
            "-Dfile.encoding=UTF-8","-Duser.timezone=UTC","-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"),
            "-cp",System.getProperty("java.class.path"),E2aRestoredWorkerProcess.class.getName());
        var process=new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(attempt.resolve("restored-worker.log").toFile()).start();
        assertThat(process.waitFor(180,TimeUnit.SECONDS)).as("restored service JVM finished").isTrue();
        assertThat(process.exitValue()).as("see restored-worker.log").isZero();
    }
}
