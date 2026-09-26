package com.acrylic.outbound.exports;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import static org.assertj.core.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class ExportWindowsJobTest {
    private Path root;
    private Process owner;
    private final List<ProcessHandle> descendants=new ArrayList<>();

    @BeforeEach void retainedDirectory() throws Exception {
        Path retained=Path.of("../.local/windows-export-jobs").toAbsolutePath().normalize();
        Files.createDirectories(retained);root=Files.createTempDirectory(retained,"case-");
    }

    @AfterEach void cleanup() throws Exception {
        if(owner!=null && owner.isAlive()) { owner.destroyForcibly();owner.waitFor(5,TimeUnit.SECONDS); }
        for(var process:descendants)if(process.isAlive())process.destroyForcibly();
    }

    @Test void holderDeathKillsRendererAndGrandchild() throws Exception {
        String[] ready=start("owner");assertThat(ready[0]).isEqualTo("READY");
        assertThat(descendants).hasSize(2).allMatch(ProcessHandle::isAlive);
        owner.destroyForcibly();assertThat(owner.waitFor(5,TimeUnit.SECONDS)).isTrue();
        assertAllExited();
    }

    @Test void normalContainerDrainReturnsOnlyAfterEntireJobExits() throws Exception {
        start("owner");owner.getOutputStream().write(1);owner.getOutputStream().flush();
        assertThat(owner.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(owner.exitValue()).isZero();
        assertThat(descendants).noneMatch(ProcessHandle::isAlive);
    }

    @Test void holderDeathBeforeAssignmentLeavesNoWritingChild() throws Exception {
        String[] ready=start("before-assign");assertThat(ready[0]).isEqualTo("WAITING");
        assertThat(root.resolve("child-started")).doesNotExist();
        owner.destroyForcibly();assertThat(owner.waitFor(5,TimeUnit.SECONDS)).isTrue();
        assertAllExited();assertThat(root.resolve("child-started")).doesNotExist();
    }

    @Test void holderDeathAfterAssignmentBeforeAdmissionKillsWaitingChild() throws Exception {
        start("assigned-wait");owner.destroyForcibly();assertThat(owner.waitFor(5,TimeUnit.SECONDS)).isTrue();
        assertAllExited();assertThat(root.resolve("child-started")).doesNotExist();
    }

    private String[] start(String mode) throws Exception {
        owner=new ProcessBuilder(ExportJobProcess.command(mode,root)).redirectError(root.resolve("owner.err").toFile()).start();
        String line;
        try(var pool=Executors.newSingleThreadExecutor()) {
            var ready=pool.submit(()->new BufferedReader(new InputStreamReader(owner.getInputStream())).readLine());
            try { line=ready.get(15,TimeUnit.SECONDS); }
            catch(Exception error) { owner.destroyForcibly();throw error; }
        }
        assertThat(line).as("Owner handshake; retained stderr").isNotNull();
        Files.writeString(root.resolve("handshake.txt"),line+"\n");String[] fields=line.split(" ");
        for(int i=1;i<fields.length;i++)descendants.add(ProcessHandle.of(Long.parseLong(fields[i])).orElseThrow());
        return fields;
    }

    private void assertAllExited() throws Exception {
        for(var process:descendants)if(process.isAlive())process.onExit().get(5,TimeUnit.SECONDS);
        assertThat(descendants).noneMatch(ProcessHandle::isAlive);
    }
}
