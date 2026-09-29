package com.acrylic.outbound.recovery;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0-K1-3 <b>真实跨进程 FileLock / fenced.lock / 进程终止与跨 JVM 可见性验证</b>。
 *
 * <p>每个场景 ≥3 次重复；子进程由 {@link ProcessBuilder} 启动**真实独立 JVM**
 * （{@link G0K1FileLockProbeMain}），stdout/stderr **重定向到文件、不使用管道**。
 * 记录含子进程 PID、JVM 版本、退出码与起止时间戳。
 *
 * <p>报告口径：{@code REAL|}＝真实跨进程实测；{@code MODEL|}＝既有设计/文档结论（仅用于对照，
 * <b>不得</b>并入真实结论）。与设计矛盾者在本轮**只记录、不改设计结论、不修代码**。
 *
 * <p>本轮不需要数据库、不监听端口、不接 HTTP/Session、不触碰 `src/main` 与共享树。
 */
class G0K1RealFileLockIT {

    private static final int REPEATS = 3;
    private static final long HOLD_MS = 2500L;

    private static Path evidenceDir;
    private static Path workDir;
    private static Path recordFile;
    private static String javaBin;
    private static String probeClasspath;

    @BeforeAll
    static void setUp() throws Exception {
        String dir = System.getenv("G0K1_EVIDENCE_DIR");
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("G0K1RealFileLockIT requires G0K1_EVIDENCE_DIR (round evidence dir)");
        }
        evidenceDir = Path.of(dir);
        workDir = evidenceDir.resolve("filelock-work");
        Files.createDirectories(workDir);
        Files.createDirectories(evidenceDir.resolve("probe-logs"));
        recordFile = evidenceDir.resolve("filelock-records.tsv");
        Files.writeString(recordFile, "kind\tscenario\trepeat\tmode\tpid\tstarted\tended\texit\tobservable\tnote\n");
        javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
        // 探针为 JDK-only 类 ⇒ 只需测试类目录即可启动独立 JVM
        probeClasspath = Path.of(G0K1FileLockProbeMain.class.getProtectionDomain().getCodeSource()
            .getLocation().toURI()).toString();
        recordLocal("env", 0, "java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
            + " probeClasspath=" + probeClasspath + " childJvm=independent-process",
            "javaBin=" + javaBin + " evidenceDir=" + evidenceDir);
        model("s2-exclusive", "设计 v5 F1′/L2：writers.lock 用共享 FileLock；排他请求与共享请求互斥");
        model("s3-shared", "设计 v5 F1′：多个 FENCED 进程可同时持有 fenced.lock 共享锁；门的独占 tryLock 必失败");
        model("s4-visibility", "设计 v5 F5：第三方只能通过 tryLock 结果推断持有者，无法直接查询持锁者");
        model("s5-f3", "设计 v5 F3：FileLock.release() 正常返回即确认释放（优于 close()）");
        model("s6-s7-termination", "设计 v5 F5：进程终止 ⇒ OS 释放全部锁；残留锁文件不等于持锁");
        model("s9-stale-file", "设计 v5：锁文件身份由 writers.json 的 fileIdentity 绑定（文件被替换/删除后必须重新校验）");
    }

    // ------------------------------------------------------------------ 场景 1：同 JVM 重叠

    @Test void s1_sameJvmOverlappingRequestThrowsOverlappingFileLockException() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s1-" + repeat + ".lock");
            try (FileChannel first = open(file); FileChannel second = open(file)) {
                FileLock held = first.lock(0L, Long.MAX_VALUE, false);
                String observable;
                String frames = "";
                try {
                    FileLock overlap = second.tryLock(0L, Long.MAX_VALUE, false);
                    observable = overlap == null ? "TRYLOCK-NULL (no overlap signalled)" : "TRYLOCK-ACQUIRED (!!)";
                    if (overlap != null) overlap.release();
                } catch (OverlappingFileLockException thrown) {
                    observable = "OVERLAP " + thrown.getClass().getName();
                    frames = String.join(" <- ", Arrays.stream(thrown.getStackTrace()).limit(4)
                        .map(StackTraceElement::toString).toList());
                }
                assertThat(observable).as("同 JVM 内重叠请求必须被 FileLock 拒绝").startsWith("OVERLAP");
                recordLocal("s1-same-jvm-overlap", repeat, observable,
                    "frames=" + frames + " heldValid=" + held.isValid()
                        + " ⇒ 同 JVM 内 FileLock 不重入、也不互相可见（JVM 级锁表），因此同 JVM 内的互斥必须由内存态承担");
                held.release();
            }
        }
    }

    // ------------------------------------------------------------------ 场景 2/3：跨 JVM 排他与共享

    @Test void s2_crossJvmExclusiveLockIsNotGrantedToASecondProcess() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s2-" + repeat + ".lock");
            Probe holder = launch("hold-exclusive", file, HOLD_MS, null);
            awaitStatus(holder, "LOCKED", 20_000);
            Probe contender = launch("try-exclusive", file, 0, null);
            awaitExit(contender, 20_000);
            String status = readStatus(contender);
            assertThat(status).as("A 持排他锁时 B 的 tryLock 必须返回 null").contains("TRYLOCK-NULL");
            recordProbe("s2-cross-jvm-exclusive", repeat, holder, "holder=LOCKED", "holder holds exclusive");
            recordProbe("s2-cross-jvm-exclusive", repeat, contender, firstLine(status, "TRYLOCK"),
                "tryLock 非阻塞返回 null ⇒ 跨 JVM 排他成立（耗时见 elapsedMs）");
            awaitExit(holder, 20_000);
        }
    }

    @Test void s3_sharedLocksCoexistAcrossProcessesWhileExclusiveIsRefused() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s3-" + repeat + ".lock");
            Probe a = launch("hold-shared", file, HOLD_MS, null);
            awaitStatus(a, "LOCKED", 20_000);
            Probe b = launch("hold-shared", file, HOLD_MS, null);
            awaitStatus(b, "LOCKED", 20_000);
            Probe c = launch("try-exclusive", file, 0, null);
            awaitExit(c, 20_000);
            String statusC = readStatus(c);
            assertThat(statusC).as("两个共享锁在场时排他请求必须失败").contains("TRYLOCK-NULL");
            recordProbe("s3-cross-jvm-shared", repeat, a, firstLine(readStatus(a), "LOCKED"), "A shared lock held");
            recordProbe("s3-cross-jvm-shared", repeat, b, firstLine(readStatus(b), "LOCKED"),
                "B shared lock ALSO granted while A holds ⇒ 共享锁跨进程共存（F1′ 前提成立）");
            recordProbe("s3-cross-jvm-shared", repeat, c, firstLine(statusC, "TRYLOCK"),
                "C exclusive refused ⇒ 门的独占 tryLock 在共享锁在场时必失败");
            awaitExit(a, 20_000);
            awaitExit(b, 20_000);
        }
    }

    // ------------------------------------------------------------------ 场景 4：F5 第三方可见性

    @Test void s4_thirdPartyCanOnlyInferTheHolderFromTryLockResults() throws Exception {
        List<java.lang.reflect.Method> methods = Arrays.stream(FileLock.class.getMethods()).distinct().toList();
        List<String> signatures = methods.stream()
            .map(m -> m.getName() + ":" + m.getReturnType().getSimpleName()).distinct().sorted().toList();
        // JDK 10+ 有 acquiredBy()，但它返回**本 JVM 的 FileChannel**，不是持锁进程/身份
        java.lang.reflect.Method acquiredBy = methods.stream()
            .filter(m -> m.getName().equals("acquiredBy")).findFirst().orElse(null);
        boolean ownerProcessAccessor = methods.stream().anyMatch(m ->
            m.getReturnType().getName().equals("java.lang.ProcessHandle")
                || (m.getReturnType() == long.class && m.getName().toLowerCase(Locale.ROOT).contains("pid"))
                || m.getReturnType().getName().equals("java.lang.Process"));
        assertThat(ownerProcessAccessor).as("FileLock 不提供持锁**进程**查询（无 ProcessHandle/pid 访问器）").isFalse();
        assertThat(acquiredBy).as("acquiredBy() 存在，但其返回类型是本 JVM 的 Channel（不是进程/身份）").isNotNull();
        assertThat(acquiredBy.getReturnType()).isEqualTo(java.nio.channels.Channel.class);

        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s4-" + repeat + ".lock");
            Probe holder = launch("hold-exclusive", file, HOLD_MS, null);
            awaitStatus(holder, "LOCKED", 20_000);
            Probe observer = launch("try-exclusive", file, 0, null);
            awaitExit(observer, 20_000);
            recordProbe("s4-f5-third-party", repeat, observer, firstLine(readStatus(observer), "TRYLOCK"),
                "跨进程观察者只能得到 tryLock 结果；FileLock API=" + signatures
                    + " ⇒ acquiredBy()=本 JVM channel，无法查询持锁进程身份/PID"
                    + "（F5 判定只能依据 tryLock 与租约记录）");
            awaitExit(holder, 20_000);
        }
    }

    // ------------------------------------------------------------------ 场景 5：正常释放（F3）

    @Test void s5_releaseByCloseOrNormalExitMakesTheLockAvailable() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s5-" + repeat + ".lock");
            Probe holder = launch("hold-exclusive", file, 700, null);
            awaitStatus(holder, "LOCKED", 20_000);
            int exit = awaitExit(holder, 20_000);
            Instant holderEnded = Instant.now();
            assertThat(exit).isEqualTo(0);
            assertThat(readStatus(holder)).as("显式 release 路径已执行").contains("RELEASED");

            Probe next = launch("try-exclusive", file, 0, null);
            awaitExit(next, 20_000);
            String status = readStatus(next);
            assertThat(status).as("正常释放后下一个 JVM 可获得锁").contains("TRYLOCK-ACQUIRED");
            recordProbe("s5-normal-release-f3", repeat, next, firstLine(status, "TRYLOCK"),
                "holderReleasedAt=" + holderEnded + " ⇒ close/release 后立即可获得（F3 正常路径）");
        }
    }

    // ------------------------------------------------------------------ 场景 6/7：进程终止（F5）

    @Test void s6_forceKilledProcessReleasesItsLockWhileTheLockFileRemains() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s6-" + repeat + ".lock");
            Probe victim = launch("hold-exclusive", file, 20_000, null);
            awaitStatus(victim, "LOCKED", 20_000);
            victim.process().destroyForcibly();
            Instant killedAt = Instant.now();
            awaitExit(victim, 20_000);

            Probe next = launch("try-exclusive", file, 0, null);
            awaitExit(next, 20_000);
            String status = readStatus(next);
            assertThat(status).as("强杀后 OS 必须释放锁").contains("TRYLOCK-ACQUIRED");
            assertThat(Files.exists(file)).as("锁文件仍然存在（残留文件≠持锁）").isTrue();
            recordProbe("s6-destroy-forcibly", repeat, next, firstLine(status, "TRYLOCK"),
                "killedAt=" + killedAt + " lockFileStillExists=true (stale file ≠ held lock)");
        }
    }

    @Test void s7_runtimeHaltAlsoReleasesTheLockAndLeavesAStaleFile() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s7-" + repeat + ".lock");
            Probe victim = launch("halt-while-locked", file, 0, null);
            awaitStatus(victim, "halting=70", 20_000);
            int exit = awaitExit(victim, 20_000);
            assertThat(exit).as("Runtime.halt(70) 的真实退出码").isEqualTo(70);

            Probe next = launch("try-exclusive", file, 0, null);
            awaitExit(next, 20_000);
            String status = readStatus(next);
            assertThat(status).as("halt 后 OS 必须释放锁").contains("TRYLOCK-ACQUIRED");
            assertThat(Files.exists(file)).isTrue();
            recordProbe("s7-runtime-halt", repeat, next, firstLine(status, "TRYLOCK"),
                "victimExit=70 (shutdown hook bypassed) lockFileStillExists=true");
        }
    }

    // ------------------------------------------------------------------ 场景 8/9：锁文件被删除/重命名

    @Test void s8_deleteOrRenameWhileLockedIsRecordedWithRealWindowsOutcome() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s8-" + repeat + ".lock");
            Probe deleter = launch("delete-while-locked", file, 0, null);
            int exit = awaitExit(deleter, 20_000);
            String status = readStatus(deleter);
            assertThat(exit).as("探针自身不应崩溃，结果写入 status").isEqualTo(0);
            assertThat(status).as("删除结果必须是 OK 或 THREW 之一")
                .matches("(?s).*(DELETE-WHILE-LOCKED-OK|DELETE-WHILE-LOCKED-THREW).*");
            recordProbe("s8a-delete-while-locked", repeat, deleter, firstLineWithPrefix(status, "DELETE-WHILE-LOCKED"),
                "Windows 真实结果（异常类型/消息原样记录）");

            Path renamed = workDir.resolve("s8b-" + repeat + "-renamed.lock");
            Path source = workDir.resolve("s8b-" + repeat + ".lock");
            Probe mover = launch("rename-while-locked", source, 0, renamed);
            int moveExit = awaitExit(mover, 20_000);
            String moveStatus = readStatus(mover);
            assertThat(moveExit).isEqualTo(0);
            assertThat(moveStatus).matches("(?s).*(RENAME-WHILE-LOCKED-OK|RENAME-WHILE-LOCKED-THREW).*");
            recordProbe("s8b-rename-while-locked", repeat, mover, firstLineWithPrefix(moveStatus, "RENAME-WHILE-LOCKED"),
                "Windows 真实结果（重命名语义）");
        }
    }

    @Test void s9_externallyDeletedLockFileBreaksFileIdentityBasedMutualExclusion() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s9-" + repeat + ".lock");
            Probe holder = launch("hold-exclusive", file, 6000, null);
            awaitStatus(holder, "LOCKED", 20_000);
            Object identityBefore = fileKey(file);
            boolean deleted = Files.deleteIfExists(file);
            Object identityAfter = Files.exists(file) ? fileKey(file) : null;

            Probe newcomer = launch("try-exclusive", file, 0, null);
            awaitExit(newcomer, 20_000);
            String status = readStatus(newcomer);
            boolean newcomerAcquired = status.contains("TRYLOCK-ACQUIRED");
            recordProbe("s9-stale-file-identity", repeat, newcomer, firstLine(status, "TRYLOCK"),
                "deleted=" + deleted + " identityBefore=" + identityBefore + " identityAfterDelete=" + identityAfter
                    + " newcomerAcquiredWhileHolderAlive=" + newcomerAcquired
                    + " ⇒ 删除锁文件后新进程获得的是**新文件身份**的锁；旧的持锁者仍在持有旧身份的锁");
            if (newcomerAcquired) {
                recordLocal("s9-contradiction-check", repeat,
                    "CONTRADICTION-CANDIDATE: 同一路径上同时存在两个持锁者（旧身份 + 新身份）",
                    "设计 v5 依赖 writers.json 的 fileIdentity 绑定；本实测说明"
                        + "“只按路径加锁”不足以互斥，必须校验文件身份且禁止删除/替换锁文件");
            }
            awaitExit(holder, 20_000);
        }
    }

    // ------------------------------------------------------------------ 场景 10：同 JVM close 后语义

    @Test void s10_afterChannelCloseTheLockBecomesInvalidAndCanBeReacquired() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Path file = workDir.resolve("s10-" + repeat + ".lock");
            Probe probe = launch("close-then-relock", file, 0, null);
            int exit = awaitExit(probe, 20_000);
            String status = readStatus(probe);
            assertThat(exit).isEqualTo(0);
            assertThat(status).contains("VALID-BEFORE-CLOSE=true").contains("VALID-AFTER-CLOSE=false")
                .contains("RELOCK=ACQUIRED");
            recordProbe("s10-close-then-relock", repeat, probe,
                firstLineWithPrefix(status, "RELOCK"), "isValid 在 close 后立即变 false；同 JVM 可再次加锁");
        }
    }

    // ------------------------------------------------------------------ 辅助

    private record Probe(Process process, Path statusFile, Path stdoutFile, Path stderrFile, Path lockFile,
                         String mode, Instant started) { }

    private static Probe launch(String mode, Path lockFile, long holdMillis, Path extra) throws Exception {
        String tag = mode + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path status = workDir.resolve(tag + ".status.txt");
        Path out = evidenceDir.resolve("probe-logs").resolve(tag + ".out.txt");
        Path err = evidenceDir.resolve("probe-logs").resolve(tag + ".err.txt");
        List<String> command = new ArrayList<>(List.of(javaBin, "-cp", probeClasspath,
            G0K1FileLockProbeMain.class.getName(), mode, lockFile.toString(), status.toString(),
            Long.toString(holdMillis)));
        if (extra != null) command.add(extra.toString());
        Files.writeString(evidenceDir.resolve("probe-commands.txt"),
            String.join(" ", command) + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(out.toFile());     // 文件重定向，不使用管道
        builder.redirectError(err.toFile());
        Process process = builder.start();
        return new Probe(process, status, out, err, lockFile, mode, Instant.now());
    }

    private static FileChannel open(Path path) throws Exception {
        return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private static Object fileKey(Path path) throws Exception {
        return Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
    }

    private static String readStatus(Probe probe) throws Exception {
        return Files.exists(probe.statusFile()) ? Files.readString(probe.statusFile()) : "";
    }

    private static void awaitStatus(Probe probe, String token, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (readStatus(probe).contains(token)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("timeout waiting for '" + token + "' in " + probe.statusFile()
            + " content=" + readStatus(probe));
    }

    private static int awaitExit(Probe probe, long timeoutMs) throws Exception {
        if (!probe.process().waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            probe.process().destroyForcibly();
            throw new AssertionError("probe did not exit: mode=" + probe.mode() + " status=" + readStatus(probe));
        }
        return probe.process().exitValue();
    }

    private static String firstLine(String status, String token) {
        return Arrays.stream(status.split("\\R")).filter(line -> line.contains(token)).findFirst().orElse(status);
    }

    private static String firstLineWithPrefix(String status, String prefix) {
        return Arrays.stream(status.split("\\R")).filter(line -> line.startsWith(prefix)).findFirst().orElse(status);
    }

    /** REAL：真实跨进程实测记录。 */
    private static void recordProbe(String scenario, int repeat, Probe probe, String observable, String note)
            throws Exception {
        appendRecord("REAL", scenario, repeat, probe.mode(), "pid=" + probe.process().pid(),
            "started=" + probe.started(), "ended=" + Instant.now(),
            "exit=" + (probe.process().isAlive() ? "ALIVE" : String.valueOf(probe.process().exitValue())),
            observable, note);
    }

    /** REAL：同 JVM 内实测记录（无子进程）。 */
    private static void recordLocal(String scenario, int repeat, String observable, String note) throws Exception {
        appendRecord("REAL", scenario, repeat, "in-process", "pid=" + ProcessHandle.current().pid(),
            "started=" + Instant.now(), "ended=" + Instant.now(), "exit=0", observable, note);
    }

    /** MODEL：既有设计/文档结论，仅用于对照。 */
    private static void model(String scenario, String claim) throws Exception {
        appendRecord("MODEL", scenario, 0, "document", "pid=-", "started=-", "ended=-", "exit=-", claim,
            "既有设计/文档结论，非本轮实测");
    }

    private static void appendRecord(String kind, String scenario, int repeat, String mode, String pid,
            String started, String ended, String exit, String observable, String note) throws Exception {
        String line = String.join("\t", kind, scenario, "repeat=" + repeat, mode, pid, started, ended, exit,
            observable.replace('\t', ' '), note.replace('\t', ' '));
        System.out.println("G0K1FL|" + line);
        Files.writeString(recordFile, line + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
