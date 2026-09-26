package com.acrylic.outbound.reports;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportSupervisionTest {
    @Test void missingPartialOrWrongAdmissionCannotReachSnapshotOrOutput() throws Exception {
        Path root=Path.of("build/tmp/supervision-cases").toAbsolutePath();Files.createDirectories(root);
        Path retained=Files.createTempDirectory(root,"case-");
        for(String input:new String[]{"","START","WRONG\n"}) {
            synchronized(System.class) {
                InputStream original=System.in;
                try {
                    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)));
                    IOException error=assertThrows(IOException.class,()->ReportCli.main(new String[]{"--supervised",
                        retained.resolve("missing.json").toString(),"xlsx",retained.resolve("result.xlsx").toString()}));
                    assertEquals("Renderer supervision was not established",error.getMessage());
                    assertFalse(Files.exists(retained.resolve("result.xlsx")));
                } finally { System.setIn(original); }
            }
        }
    }
}
