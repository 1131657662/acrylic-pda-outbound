package com.acrylic.outbound.exports;

import com.acrylic.outbound.jobs.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportRendererProcessTest {
    Path directory;
    private BackgroundJobExecution execution;private Path output;private ExportRendererProcess process;
    @BeforeEach void sleepingRenderer() throws Exception {
        Path retained=Path.of("../.local/export-process-drain").toAbsolutePath().normalize();
        Files.createDirectories(retained);directory=Files.createTempDirectory(retained,"case-");
        Path classes=directory.resolve("classes");Files.createDirectories(classes);Files.createDirectories(directory.resolve("lib"));
        Path source=directory.resolve("ReportCli.java");
        Files.writeString(source,"""
            package com.acrylic.outbound.reports;
            public class ReportCli {
                static void publish(String name) throws Exception {
                    java.nio.file.Path target=java.nio.file.Path.of(name);java.nio.file.Path temp=java.nio.file.Path.of(name+".tmp");
                    java.nio.file.Files.writeString(temp,Long.toString(ProcessHandle.current().pid()));
                    java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
                public static void main(String[] args) throws Exception {
                    if(args.length>0 && args[0].equals("--supervised")) {
                        if(!java.util.Arrays.equals(System.in.readNBytes(6),"START\\n".getBytes()))throw new IllegalStateException("Not supervised");
                        args=java.util.Arrays.copyOfRange(args,1,args.length);
                    }
                    if(args.length==1) {
                        publish("child-pid");
                        System.out.println("READY");System.out.flush();Thread.sleep(60000);return;
                    }
                    String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
                    Process child=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),
                        "com.acrylic.outbound.reports.ReportCli","leaf").start();
                    if(!new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream())).readLine().equals("READY"))
                        throw new IllegalStateException("Child did not start");
                    publish("pid");
                    Thread.sleep(60000);
                }
            }
            """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",classes.toString(),source.toString())).isZero();
        try(var jar=new JarOutputStream(Files.newOutputStream(directory.resolve("lib/fixture.jar")))) {
            jar.putNextEntry(new JarEntry("com/acrylic/outbound/reports/ReportCli.class"));
            Files.copy(classes.resolve("com/acrylic/outbound/reports/ReportCli.class"),jar);jar.closeEntry();
        }
        execution=mock(BackgroundJobExecution.class);output=directory.resolve("attempt/report.xlsx");
        var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var handoffs=new com.acrylic.outbound.recovery.RecoveryProcessHandoff(
            beans.getBeanProvider(com.acrylic.outbound.recovery.RecoveryOperationRegistry.class),
            beans.getBeanProvider(com.acrylic.outbound.recovery.RecoveryEffectGate.class));
        process=new ExportRendererProcess(directory.toString(),directory.toString(),10,handoffs);
    }
    private void render() throws Exception { process.render(execution,directory.resolve("snapshot.json"),output,ExportModels.Format.XLSX); }
    /**
     * Reads a published renderer pid. The fixture publishes atomically, but this still waits for non-blank content
     * instead of mere existence: {@code Files.writeString} creates the file before writing it, so a full-suite run
     * under load used to observe the empty create window and fail with {@code NumberFormatException: For input string: ""}.
     */
    private long pid(String name) throws Exception {
        Path file=directory.resolve("attempt/"+name);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(true) {
            try {
                if(Files.exists(file)) {
                    String text=Files.readString(file).trim();
                    if(!text.isEmpty()) return Long.parseLong(text);
                }
            } catch(java.io.IOException retry) { /* Retried inside the same bound; never parsed half a line. */ }
            assertThat(System.nanoTime()).as(name+" pid must be published complete").isLessThan(until);
            Thread.sleep(20);
        }
    }
    private void assertStopped() throws Exception {
        for(String name:java.util.List.of("pid","child-pid"))
            assertThat(ProcessHandle.of(pid(name)).map(ProcessHandle::isAlive).orElse(false)).as(name).isFalse();
        assertThat(output).doesNotExist();
    }
    @Test void lostLeaseTerminatesRendererProcess() throws Exception {
        doThrow(new JobFailure("LEASE_LOST",false)).when(execution).renew();
        assertThatThrownBy(this::render).isInstanceOf(JobFailure.class).hasMessage("LEASE_LOST");assertStopped();
    }
    @Test void timeoutTerminatesRendererProcess() throws Exception {
        assertThatThrownBy(this::render).isInstanceOf(JobFailure.class).hasMessage("EXPORT_RENDER_TIMEOUT");assertStopped();
    }
    @Test void interruptionTerminatesRendererProcess() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> running=pool.submit(()->{try { render(); } catch(Exception error) { throw new RuntimeException(error); }});
            pid("pid"); // Waits for a complete published pid, so the interrupt lands on a running renderer.
            running.cancel(true);
        } finally { pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
        assertStopped();
    }
}
