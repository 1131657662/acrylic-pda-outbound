package com.acrylic.outbound.recovery;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.EnumSet;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

/** Actual product entry components on a test-only loopback servlet; halt affects only this JVM. */
public final class RequestCloseFenceChild {
    public static void main(String[] args) throws Exception {
        Path config = Path.of(args[0]), home = Path.of(args[1]);
        EffectRealEnvironment.requireEnvironment();
        var authority = RecoveryRetirementProcess.authority(config);
        var gate = RecoveryEffectGate.enabled(authority);
        if (args[2].equals("successor")) {
            Files.writeString(home.resolve("successor-started.marker"), "unexpected startup", StandardOpenOption.CREATE_NEW);
            authority.retire();
            return;
        }
        var registry = registry(config, authority);
        var physical = new CompletionFaultDataSource(home);
        var admitted = new RecoveryRequestAdmittedDataSource(physical, registry, gate);
        var server = factory(home).getWebServer(context -> install(context,
            new Probe(new ProbeInput(home, args[2], args[3]), new Entry(physical, admitted, registry))));
        server.start();
        Files.writeString(home.resolve("ready.json"), "{\"pid\":" + ProcessHandle.current().pid()
            + ",\"port\":" + server.getPort() + "}", StandardOpenOption.CREATE_NEW);
        Thread.sleep(120_000);
        server.stop();
        throw new AssertionError("HTTP completion probe did not halt");
    }

    private static RecoveryOperationRegistry registry(Path config, RecoveryFenceAuthority authority) throws Exception {
        var value = RecoveryControlHistory.read(config);
        return new RecoveryOperationRegistry(RecoveryRetirementProcess.location(value),
            Path.of(value.path("checkpoint").asText()), new RecoveryOperationRegistry.LeaseChannel() {
                public java.util.UUID publish() throws java.sql.SQLException { return authority.registerOperation(); }
                public boolean retire(java.util.UUID id) throws java.sql.SQLException { return authority.retireOperation(id); }
                public void unknown() { authority.fenceUnknown(); }
                public void fenceLocal() { authority.fenceLocal(); }
            });
    }

    private static TomcatServletWebServerFactory factory(Path home) throws Exception {
        var factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(java.net.InetAddress.getByName("127.0.0.1"));
        factory.setBaseDirectory(Files.createDirectory(home.resolve("tomcat")).toFile());
        return factory;
    }

    private static void install(ServletContext context, Probe probe) {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var responses = new com.acrylic.outbound.common.ApiResponses(null, json);
        var filter = context.addFilter("productRecoveryAdmission",
            new RecoveryRequestAdmissionFilter(probe.registry, responses));
        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC), false, "/*");
        context.addListener(new ServletRequestListener() {
            @Override public void requestDestroyed(ServletRequestEvent event) {
                try { Files.writeString(probe.home.resolve("destroyed.tsv"), "roots=" + probe.registry.liveRoots() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
        });
        // Servlet request listeners retire in reverse registration order; observe after the product listener.
        context.addListener(new RecoveryRequestRootListener(probe.registry));
        context.addServlet("completionProbe", probe).addMapping("/*");
    }

    private record ProbeInput(Path home, String fault, String table) { }
    private record Entry(CompletionFaultDataSource physical, RecoveryRequestAdmittedDataSource admitted,
                         RecoveryOperationRegistry registry) { }

    private static final class Probe extends HttpServlet {
        private final Path home;
        private final String fault, table;
        private final CompletionFaultDataSource physical;
        private final RecoveryRequestAdmittedDataSource admitted;
        private final RecoveryOperationRegistry registry;

        Probe(ProbeInput input, Entry entry) {
            if (!input.table().matches("hc_[a-f0-9]{32}")) throw new IllegalArgumentException("test table");
            home = input.home(); fault = input.fault(); table = input.table();
            physical = entry.physical(); admitted = entry.admitted(); registry = entry.registry();
        }

        @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            try {
                if (request.getRequestURI().equals("/normal")) normal(response);
                else if (request.getRequestURI().equals("/fault")) unknown();
                else response.sendError(404);
            } catch (Exception failure) { throw new ServletException(failure); }
        }

        private void normal(HttpServletResponse response) throws Exception {
            int id = Files.exists(home.resolve("first-normal.marker")) ? 2 : 1;
            try (var connection = admitted.getConnection()) {
                connection.setAutoCommit(false);
                insert(connection, id);
                connection.commit();
            }
            if (id == 1) Files.writeString(home.resolve("first-normal.marker"), "1", StandardOpenOption.CREATE_NEW);
            response.getWriter().write("committed=" + id);
        }

        private void unknown() throws Exception {
            physical.arm(fault);
            Connection connection = admitted.getConnection();
            connection.setAutoCommit(false);
            insert(connection, 3);
            var root = registry.live(registry.bound(Thread.currentThread()));
            var prepared = RecoveryControlFixture.JSON.createObjectNode()
                .put("operation", root.token().operationId().toString()).put("lease", root.leaseId().toString())
                .put("phase", root.phase().name()).put("roots", registry.liveRoots()).put("children", root.openSubOperations());
            Files.writeString(home.resolve("prepared.json"), prepared.toString(), StandardOpenOption.CREATE_NEW);
            awaitRelease();
            try { connection.close(); }
            catch (Throwable failure) {
                Files.writeString(home.resolve("caught-close.marker"), failure.toString(), StandardOpenOption.CREATE_NEW);
            }
            Files.writeString(home.resolve("continued-after-close.marker"), "unexpected continuation", StandardOpenOption.CREATE_NEW);
            try (var next = admitted.getConnection()) { insert(next, 4); }
        }

        private void insert(Connection connection, int id) throws Exception {
            try (var statement = connection.prepareStatement("INSERT INTO " + table + " (id) VALUES (?)")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        }

        private void awaitRelease() throws Exception {
            long limit = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while (!Files.exists(home.resolve("go"))) {
                if (System.nanoTime() > limit) throw new AssertionError("parent release timeout");
                Thread.sleep(20);
            }
        }
    }
}
