package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.jobs.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExportRendererProcess {
    private final String home;private final String browsers;private final long timeout;
    public ExportRendererProcess(@Value("${outbound.exports.renderer-home:}") String home,
            @Value("${outbound.exports.browsers:}") String browsers,@Value("${outbound.exports.timeout-seconds:180}") long timeout) {
        this.home=home;this.browsers=browsers;this.timeout=Math.clamp(timeout,10,600);
    }
    public void requireAvailable(ExportModels.Format format) {
        if(home.isBlank() || !Files.isDirectory(Path.of(home,"lib"))
                || (format==ExportModels.Format.PDF && (browsers.isBlank() || !Files.isDirectory(Path.of(browsers)))))
            throw new BusinessException(503,"EXPORT_UNAVAILABLE","报表生成服务尚未配置，请联系管理员");
    }
    public void render(BackgroundJobExecution execution,Path snapshot,Path output,ExportModels.Format format) throws Exception {
        requireAvailable(format);Path directory=output.getParent();Files.createDirectories(directory.resolve("tmp"));
        var builder=new ProcessBuilder(command(snapshot,output,format));builder.directory(directory.toFile());
        configureEnvironment(builder,directory);builder.redirectErrorStream(true).redirectOutput(directory.resolve("renderer.log").toFile());
        Process process=builder.start();var children=new HashSet<ProcessHandle>();
        Thread shutdown=new Thread(()->stop(process,children),"r08-renderer-stop");Runtime.getRuntime().addShutdownHook(shutdown);
        try { await(process,children,execution); }
        finally { stop(process,children);Runtime.getRuntime().removeShutdownHook(shutdown); }
        if(process.exitValue()!=0) throw new JobFailure("EXPORT_RENDER_FAILED",true);
    }
    private List<String> command(Path snapshot,Path output,ExportModels.Format format) {
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        return List.of(Path.of(System.getProperty("java.home"),"bin",executable).toString(),"-Xmx512m","-Dfile.encoding=UTF-8",
                "-Djava.io.tmpdir="+output.getParent().resolve("tmp"),"-cp",Path.of(home,"lib").toAbsolutePath()+java.io.File.separator+"*",
                "com.acrylic.outbound.reports.ReportCli",snapshot.toString(),format.rendererFormat,output.toString());
    }
    private void configureEnvironment(ProcessBuilder builder,Path directory) {
        var inherited=new HashMap<>(builder.environment());builder.environment().clear();
        for(String key:List.of("SystemRoot","WINDIR","PATH","HOME","USERPROFILE","LOCALAPPDATA"))
            if(inherited.containsKey(key)) builder.environment().put(key,inherited.get(key));
        builder.environment().put("TEMP",directory.resolve("tmp").toString());
        builder.environment().put("TMP",directory.resolve("tmp").toString());
        builder.environment().put("PLAYWRIGHT_BROWSERS_PATH",browsers);
        builder.environment().put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1");
    }
    private void await(Process process,Set<ProcessHandle> children,BackgroundJobExecution execution) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(timeout).toNanos();
        while(true) {
            synchronized(children) { process.descendants().forEach(children::add); }
            if(process.waitFor(2,TimeUnit.SECONDS)) return;
            if(System.nanoTime()>deadline) throw new JobFailure("EXPORT_RENDER_TIMEOUT",true);
            execution.renew();
        }
    }
    private static void stop(Process process,Set<ProcessHandle> children) {
        synchronized(children) {
            process.descendants().forEach(children::add);
            children.forEach(child->{if(child.isAlive()) child.destroyForcibly();});
        }
        if(process.isAlive()) process.destroyForcibly();
    }
}
