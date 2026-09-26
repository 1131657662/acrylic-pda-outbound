package com.acrylic.outbound.exports;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Real OS processes only; no product endpoint or fault flag. */
public final class ExportJobProcess {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[1]);
        if(args[0].equals("leaf")) { ready("LEAF "+ProcessHandle.current().pid());System.in.read();return; }
        if(args[0].equals("child")) { child(root);return; }
        try(var job=new ExportWindowsJob()) {
            Process child=new ProcessBuilder(command("child",root)).redirectError(root.resolve("child.err").toFile()).start();
            if(args[0].equals("before-assign")) {
                ready("WAITING "+child.pid());System.in.read();return;
            }
            job.assign(child);
            if(args[0].equals("assigned-wait")) {
                ready("WAITING "+child.pid());System.in.read();return;
            }
            child.getOutputStream().write(1);child.getOutputStream().flush();
            ready(new BufferedReader(new InputStreamReader(child.getInputStream())).readLine());
            System.in.read();job.stop(Duration.ofSeconds(5));
        }
    }

    private static void child(Path root) throws Exception {
        if(System.in.read()!=1)return;
        Files.writeString(root.resolve("child-started"),Long.toString(ProcessHandle.current().pid()));
        Process leaf=new ProcessBuilder(command("leaf",root)).redirectError(root.resolve("leaf.err").toFile()).start();
        String line=new BufferedReader(new InputStreamReader(leaf.getInputStream())).readLine();
        if(line==null || !line.equals("LEAF "+leaf.pid()))throw new IOException("Leaf handshake failed");
        Files.writeString(root.resolve("leaf-started"),Long.toString(leaf.pid()));
        ready("READY "+ProcessHandle.current().pid()+" "+leaf.pid());
        System.in.read();
    }

    static List<String> command(String mode,Path root) {
        var locations=new LinkedHashSet<String>();
        for(Class<?> type:List.of(ExportJobProcess.class,ExportWindowsJob.class,com.sun.jna.Native.class,
                com.sun.jna.platform.win32.Kernel32.class)) {
            try { locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()); }
            catch(java.net.URISyntaxException error) { throw new IllegalStateException(error); }
        }
        return List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",
            String.join(File.pathSeparator,locations),ExportJobProcess.class.getName(),mode,root.toString());
    }

    private static void ready(String line) { System.out.println(line);System.out.flush(); }
}
