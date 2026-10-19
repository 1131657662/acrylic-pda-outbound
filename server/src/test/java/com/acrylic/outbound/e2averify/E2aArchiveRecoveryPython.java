package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class E2aArchiveRecoveryPython {
    private static final String DEFAULT_PYTHON="C:/Python314/python.exe";
    static void inspect(Path attempt,String side) throws Exception {
        run(attempt,"archive-inspect-"+side,List.of("docs/parallel/R09/ops/temporary_archive_probe.py","--attempt",attempt.toString(),"--side",side));
    }
    static void faults(Path attempt) throws Exception {
        run(attempt,"archive-restore-faults",List.of("-m","unittest","discover","-s","docs/parallel/R09/tests","-p","test_temporary_archive_restore.py","-v"));
    }
    private static void run(Path attempt,String label,List<String> arguments) throws Exception {
        var command=new ArrayList<>(List.of(python(),"-W","error::ResourceWarning"));command.addAll(arguments);
        var builder=new ProcessBuilder(command);builder.directory(Path.of("D:/acrylic-pda-outbound").toFile());
        builder.environment().remove("E2A_CROSS_PASSWORD");builder.environment().put("R09_TEMP_ARCHIVE_ATTEMPT",attempt.toString());
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve(label+".log").toFile());
        var process=builder.start();assertThat(process.waitFor(60,TimeUnit.SECONDS)).as(label+" finished").isTrue();
        assertThat(process.exitValue()).as(label+" exit; see log").isZero();
    }

    private static String python() {
        var configured=System.getenv("R09_PYTHON");
        return configured == null || configured.isBlank() ? DEFAULT_PYTHON : configured;
    }
}
