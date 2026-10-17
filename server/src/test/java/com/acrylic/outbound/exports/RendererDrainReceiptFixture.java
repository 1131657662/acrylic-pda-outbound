package com.acrylic.outbound.exports;

import com.acrylic.outbound.recovery.RecoveryProcessHandoff;
import java.nio.file.*;
import java.util.Set;
import java.util.jar.*;
import javax.tools.ToolProvider;

/** A real synthetic renderer and an explicit lost termination receipt at the OS boundary. */
public final class RendererDrainReceiptFixture {
    public static void prepare(Path home) throws Exception {
        Files.createDirectories(home.resolve("classes")); Files.createDirectories(home.resolve("lib"));
        Path source = home.resolve("ReportCli.java");
        Files.writeString(source, """
            package com.acrylic.outbound.reports;
            public final class ReportCli {
                public static void main(String[] args) throws Exception {
                    if (args[0].equals("--supervised")) {
                        if (!java.util.Arrays.equals(System.in.readNBytes(6), "START\\n".getBytes()))
                            throw new IllegalStateException("Not supervised");
                        args = java.util.Arrays.copyOfRange(args, 1, args.length);
                    }
                    java.nio.file.Path output = java.nio.file.Path.of(args[2]);
                    java.nio.file.Files.writeString(output.resolveSibling("renderer-started"),
                        Long.toString(ProcessHandle.current().pid()));
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                    while (!java.nio.file.Files.exists(output.resolveSibling("renderer-release"))) {
                        if (System.nanoTime() > deadline) throw new IllegalStateException("Fixture timeout");
                        Thread.sleep(20);
                    }
                    java.nio.file.Files.writeString(output, "Synthetic bytes; never a published report");
                }
            }
            """, StandardOpenOption.CREATE_NEW);
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", home.resolve("classes").toString(),
                source.toString()) != 0) throw new AssertionError("Fixture compile failed");
        try (var jar = new JarOutputStream(Files.newOutputStream(home.resolve("lib/fixture.jar")))) {
            jar.putNextEntry(new JarEntry("com/acrylic/outbound/reports/ReportCli.class"));
            Files.copy(home.resolve("classes/com/acrylic/outbound/reports/ReportCli.class"), jar); jar.closeEntry();
        }
    }

    public static ExportRendererProcess lostReceipt(Path home, RecoveryProcessHandoff handoffs) {
        return new ExportRendererProcess(home.toString(), home.toString(), 30, handoffs) {
            @Override void drain(Process process, Set<ProcessHandle> children, ExportWindowsJob job) {
                super.drain(process, children, job);
                try {
                    Files.writeString(home.resolve("termination-observed.txt"), "pid=" + process.pid()
                        + "|alive=" + process.isAlive() + "|childrenAlive=" + children.stream().filter(ProcessHandle::isAlive).count()
                        + "|MODEL=lost receipt after real native drain", StandardOpenOption.CREATE_NEW);
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
                throw new IllegalStateException("EXPORT_PROCESS_DRAIN_INCOMPLETE");
            }
        };
    }
}
