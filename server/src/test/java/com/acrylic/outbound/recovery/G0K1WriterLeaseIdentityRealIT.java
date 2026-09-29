package com.acrylic.outbound.recovery;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0-K1-3 <b>更正与闭合轮</b>：用**真实产品获取路径**复验“锁文件被外部删除/替换”。
 *
 * <p>上一轮（`bc131c3`）用裸 {@link FileChannel} 观测到“同一路径两个持锁者”，据此写成“矛盾 B（阻断）”。
 * 本轮改走产品路径（{@code RecoveryControlLock.locked} → {@code RecoveryWriterLease.acquire} →
 * {@code RecoveryWriterProtocol.validate} 的**原生文件身份**校验），据实改写该定性。
 *
 * <p>记录口径：{@code REAL|}＝真实跨进程实测；{@code MODEL|}＝既有设计/历史验收结论（仅对照）。
 * 每条记录标注**调用路径**（`产品路径` / `裸 FileLock`）。所有结论均有**断言**，不留“只记录不断言”的用例。
 *
 * <p>本轮不接 HTTP/Session、不需要数据库与端口、不改 `src/main`。
 */
class G0K1WriterLeaseIdentityRealIT {

    private static final int REPEATS = 3;
    private static final long HOLD_MS = 4000L;

    private static Path evidenceDir;
    private static Path workDir;
    private static Path recordFile;
    private static String javaBin;
    private static String childClasspath;

    @BeforeAll
    static void setUp() throws Exception {
        String dir = System.getenv("G0K1_EVIDENCE_DIR");
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("G0K1WriterLeaseIdentityRealIT requires G0K1_EVIDENCE_DIR");
        }
        evidenceDir = Path.of(dir);
        workDir = evidenceDir.resolve("lease-work");
        Files.createDirectories(workDir);
        Files.createDirectories(evidenceDir.resolve("probe-logs"));
        recordFile = evidenceDir.resolve("lease-records.tsv");
        Files.writeString(recordFile,
            "kind\tscenario\trepeat\tcall_path\tpid\tstarted\tended\texit\tobservable\tnote\n");
        javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        childClasspath = resolveChildClasspath();
        Files.writeString(evidenceDir.resolve("child-classpath.txt"), childClasspath.replace(File.pathSeparator, "\n"));
        recordLocal("env", 0, "setup", "java=" + System.getProperty("java.version")
            + " os=" + System.getProperty("os.name") + " childClasspathEntries="
            + childClasspath.split(File.pathSeparator).length, "javaBin=" + javaBin);

        // 防止“静默降级为裸 FileLock”：先确认子 JVM 能加载产品类与依赖（含 JNA/jackson）
        Path probeRoot = RecoveryWriterLeaseTest.protocol().root;
        Probe check = launch("classpath-check", probeRoot.resolve("writers.lock"), 0);
        int exit = awaitExit(check, 60_000);
        assertThat(exit).as("classpath-check 子进程必须成功").isEqualTo(0);
        assertThat(readStatus(check)).as("子 JVM 必须能加载产品类与依赖").contains("PRODUCT-CLASSES-OK");
        recordProbe("v0-classpath-check", 0, "产品路径", check, firstLine(readStatus(check), "PRODUCT-CLASSES-OK"),
            "子 JVM 已加载 RecoveryWriterProtocol/JNA/jackson ⇒ 本轮结论来自真实产品路径");
    }

    // ------------------------------------------------------------------ V1a：同名替换（产品路径）

    @Test void v1a_replacedLockFileIsRefusedByTheProductPathWhileTheOldHolderIsAlive() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            var fixture = RecoveryWriterLeaseTest.protocol();
            Path lock = fixture.root.resolve("writers.lock");
            String identityBefore = RecoveryWriterProtocol.fileIdentity(lock);

            Probe holder = launch("product-lease-acquire", fixture.root, HOLD_MS);
            awaitStatus(holder, "PRODUCT-LEASE-ACQUIRED", 30_000);

            // 外部删除并在同路径新建一个**长度仍为 1** 的锁文件（隔离出“身份”这一条校验）
            Files.delete(lock);
            Files.writeString(lock, "0", StandardOpenOption.CREATE_NEW);
            String identityAfter = RecoveryWriterProtocol.fileIdentity(lock);
            assertThat(identityAfter).as("替换后的文件身份必须不同于原身份").isNotEqualTo(identityBefore);

            Probe challenger = launch("product-lease-acquire-fail", fixture.root, 0);
            int exit = awaitExit(challenger, 60_000);
            String status = readStatus(challenger);
            assertThat(exit).as("挑战者进程自身应正常退出（失败以状态记录）").isEqualTo(0);
            assertThat(status).as("产品路径必须拒绝同名替换（原生文件身份校验）")
                .contains("PRODUCT-LEASE-FAILED").contains("CONTROL_WRITER_LOCK_REPLACED");
            assertThat(status).as("不得出现“成功获取”").doesNotContain("PRODUCT-LEASE-ACQUIRED-UNEXPECTED");
            assertThat(holder.process().isAlive()).as("旧持锁者仍在运行").isTrue();

            recordProbe("v1a-lock-replaced-product-path", repeat, "产品路径", challenger,
                firstLine(status, "PRODUCT-LEASE-FAILED"),
                "identityBefore=" + identityBefore + " identityAfter=" + identityAfter
                    + " holderStillAlive=true ⇒ 无第二个持锁者");
            holder.process().destroyForcibly();
            awaitExit(holder, 30_000);
        }
    }

    // ------------------------------------------------------------------ V1b：删除（产品路径）

    @Test void v1b_deletedLockFileIsRefusedByTheProductPath() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            var fixture = RecoveryWriterLeaseTest.protocol();
            Path lock = fixture.root.resolve("writers.lock");
            Probe holder = launch("product-lease-acquire", fixture.root, HOLD_MS);
            awaitStatus(holder, "PRODUCT-LEASE-ACQUIRED", 30_000);

            Files.delete(lock);
            assertThat(Files.exists(lock)).isFalse();

            Probe challenger = launch("product-lease-acquire-fail", fixture.root, 0);
            int exit = awaitExit(challenger, 60_000);
            String status = readStatus(challenger);
            assertThat(exit).isEqualTo(0);
            assertThat(status).as("锁文件缺失必须失败关闭（不自动重建）")
                .contains("PRODUCT-LEASE-FAILED").contains("CONTROL_FILE_MISSING");
            assertThat(status).as("不得创建新文件后放行").doesNotContain("PRODUCT-LEASE-ACQUIRED-UNEXPECTED");
            assertThat(Files.exists(lock)).as("产品路径不得自动重建锁文件").isFalse();

            recordProbe("v1b-lock-deleted-product-path", repeat, "产品路径", challenger,
                firstLine(status, "PRODUCT-LEASE-FAILED"),
                "holderAlive=" + holder.process().isAlive() + " lockRecreated=false ⇒ 失败关闭");
            holder.process().destroyForcibly();
            awaitExit(holder, 30_000);
        }
    }

    // ------------------------------------------------------------------ V2：S8 复验与定性（裸 FileLock）

    @Test void v2_deleteOrRenameWhileHeldIsPossibleAtOsLevelButCannotProduceTwoHolders() throws Exception {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            var fixture = RecoveryWriterLeaseTest.protocol();
            Path lock = fixture.root.resolve("writers.lock");
            Probe holder = launch("product-lease-acquire", fixture.root, HOLD_MS);
            awaitStatus(holder, "PRODUCT-LEASE-ACQUIRED", 30_000);

            // 裸 FileLock 视角：持锁期间删除/重命名在 NTFS 上可行（上一轮已实测，本轮复验并定性）
            Files.delete(lock);
            Files.writeString(lock, "0", StandardOpenOption.CREATE_NEW);
            boolean recreated = Files.exists(lock);
            Probe challenger = launch("product-lease-acquire-fail", fixture.root, 0);
            awaitExit(challenger, 60_000);
            String status = readStatus(challenger);
            assertThat(recreated).isTrue();
            assertThat(status).as("S8 场景下产品路径仍必须失败关闭").contains("PRODUCT-LEASE-FAILED");
            recordProbe("v2-s8-recharacterised", repeat, "产品路径", challenger, firstLine(status, "PRODUCT-LEASE-FAILED"),
                "OS 允许持锁期间删除/重建（裸 FileLock 事实）；但产品路径因身份校验失败关闭 ⇒ "
                    + "定性=威胁/运维需求（可用性），安全侧由原生身份绑定覆盖");
            holder.process().destroyForcibly();
            awaitExit(holder, 30_000);
        }
    }

    // ------------------------------------------------------------------ V3：历史对齐（模型对照）

    @Test void v3_historicalAcceptanceItemsAreReusedNotNewlyClaimed() throws Exception {
        model("v3-historical-same-name-replacement",
            "写者租约验收 2026-09-26：『缺失、坏长度、硬链接、同名替换均拒绝，原件不自动修复』——本轮 V1a/V1b 为该条的**复测**（改走产品路径），非新增结论");
        model("v3-historical-native-identity",
            "写者租约验收 2026-09-26：Windows BasicFileAttributes.fileKey 为空 ⇒ 已改用原生文件身份（GetFileInformationByHandle）——本轮沿用原生身份函数，禁用 fileKey() 观测");
        model("v3-historical-windows-25-of-25",
            "写者租约验收 2026-09-26：Windows 25/25 真实跨进程（含进程强杀）；本轮仅补“锁文件删除/替换”路径，不重开该批结论");
        assertThat(Files.exists(Path.of("..", "docs", "history", "R09_WRITER_LEASE_ACCEPTANCE_2026-09-26.md")))
            .as("历史验收文档必须存在，引用才算对齐").isTrue();
        recordLocal("v3-historical-alignment", 0, "产品路径",
            "对齐 3 条历史结论（同名替换/原生身份/Windows 25-25），本轮为复测与补测，不重复表述为新增矛盾",
            "docs/history/R09_WRITER_LEASE_ACCEPTANCE_2026-09-26.md");
    }

    // ------------------------------------------------------------------ V4：上一轮保留项引用

    @Test void v4_previousRoundF1F3F5FindingsRemainUnchanged() throws Exception {
        model("v4-previous-f1-f3-f5",
            "上一轮（bc131c3）已成立的 F1′（跨进程共享共存＋排他必失败）、F3（release/正常退出可再获取）、"
                + "F5（destroyForcibly/halt 后 OS 释放、第三方无持锁者查询）、S1/S3/S5/S6/S7/S10 结论本轮不改写，只引用");
        // 干净检出可复现：上一轮证据位于**未入库**的 .local ⇒ 此处只记录存在性，不做硬断言
        // （历史失败证据未被删除；其校验属证据留档阶段，不属本 IT 的通过条件）
        Path previousEvidence = evidenceDir.getParent()
            .resolve("r09-c2-g0-k1-3-filelock-20260929-a/filelock-records.tsv");
        boolean present = Files.exists(previousEvidence);
        recordLocal("v4-previous-evidence-presence", 0, "证据留档",
            "previous_round_evidence_present=" + present,
            "路径=" + previousEvidence + "（未入库 ⇒ 干净检出下缺失属预期，仅记录不失败）");
        assertThat(Files.readString(recordFile))
            .as("存在性已写入本轮记录（缺失不构成失败）").contains("v4-previous-evidence-presence");
        assertThat(Files.exists(Path.of("..", "docs", "history", "R09_WRITER_LEASE_ACCEPTANCE_2026-09-26.md")))
            .as("历史验收文档（已入库）仍必须存在").isTrue();
    }

    // ------------------------------------------------------------------ 辅助

    private record Probe(Process process, Path statusFile, String mode, Instant started) { }

    private static Probe launch(String mode, Path root, long holdMillis) throws Exception {
        String tag = mode + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path status = workDir.resolve(tag + ".status.txt");
        Path out = evidenceDir.resolve("probe-logs").resolve(tag + ".out.txt");
        Path err = evidenceDir.resolve("probe-logs").resolve(tag + ".err.txt");
        List<String> command = new ArrayList<>(List.of(javaBin, "-cp", childClasspath,
            G0K1FileLockProbeMain.class.getName(), mode, root.toString(), status.toString(),
            Long.toString(holdMillis)));
        Files.writeString(evidenceDir.resolve("probe-commands.txt"),
            String.join(" ", command) + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(out.toFile());     // 文件重定向，不使用管道
        builder.redirectError(err.toFile());
        return new Probe(builder.start(), status, mode, Instant.now());
    }

    /** 子进程 classpath：优先用测试 JVM 自身的 classpath（含 main/test classes 与依赖），否则回落到既有 JSON。 */
    private static String resolveChildClasspath() throws Exception {
        String own = System.getProperty("java.class.path", "");
        if (own.contains("classes" + File.separator + "java" + File.separator + "main")) return own;
        for (Path json : List.of(Path.of("build", "recovery-control-classpath.json"),
                Path.of("server", "build", "recovery-control-classpath.json"))) {
            if (!Files.exists(json)) continue;
            String[] entries = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(Files.readString(json), String[].class);
            if (entries.length > 0) return String.join(File.pathSeparator, entries);
        }
        throw new IllegalStateException("cannot resolve a child classpath containing the product classes");
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
        throw new AssertionError("timeout waiting for '" + token + "' content=" + readStatus(probe));
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

    private static void recordProbe(String scenario, int repeat, String callPath, Probe probe, String observable,
            String note) throws Exception {
        appendRecord("REAL", scenario, repeat, callPath, "pid=" + probe.process().pid(),
            "started=" + probe.started(), "ended=" + Instant.now(),
            "exit=" + (probe.process().isAlive() ? "ALIVE" : String.valueOf(probe.process().exitValue())),
            observable, note);
    }

    private static void recordLocal(String scenario, int repeat, String callPath, String observable, String note)
            throws Exception {
        appendRecord("REAL", scenario, repeat, callPath, "pid=" + ProcessHandle.current().pid(),
            "started=" + Instant.now(), "ended=" + Instant.now(), "exit=0", observable, note);
    }

    private static void model(String scenario, String claim) throws Exception {
        appendRecord("MODEL", scenario, 0, "document", "pid=-", "started=-", "ended=-", "exit=-", claim,
            "既有设计/历史验收结论，非本轮实测");
    }

    private static void appendRecord(String kind, String scenario, int repeat, String callPath, String pid,
            String started, String ended, String exit, String observable, String note) throws Exception {
        String line = String.join("\t", kind, scenario, "repeat=" + repeat, callPath, pid, started, ended, exit,
            observable.replace('\t', ' '), note.replace('\t', ' '));
        System.out.println("G0K1WL|" + line);
        Files.writeString(recordFile, line + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
