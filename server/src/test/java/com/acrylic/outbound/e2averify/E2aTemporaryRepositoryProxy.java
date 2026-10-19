package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Original batch GET only; upstream success bytes are preserved before one downstream loss. */
final class E2aTemporaryRepositoryProxy implements AutoCloseable {
    private final HttpServer server;
    private final Path attempt;
    private final String phase,path;
    private final List<ObjectNode> requests=new ArrayList<>();
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private int lost;

    E2aTemporaryRepositoryProxy(Path attempt,String phase) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        this.attempt=attempt;this.phase=phase;
        path="/api/v1/pda/submissions/"+SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-original.json")))
            .path("submissionId").asText();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",E2aTestPorts.http()+1),0);
        server.createContext("/",this::forward);server.start();
    }

    private void forward(HttpExchange exchange) {
        try {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().toString()).isEqualTo(path);
            assertThat(exchange.getRequestBody().readAllBytes()).isEmpty();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+E2aTestPorts.http()+path))
                .timeout(java.time.Duration.ofSeconds(90));
            for(String header:List.of("Authorization","X-Server-Recovery-Epoch"))
                request.header(header,Objects.requireNonNull(exchange.getRequestHeaders().getFirst(header)));
            respond(exchange,client.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray()));
        } catch(Throwable error) {
            try { Files.writeString(attempt.resolve("temp-proxy-"+phase+"-error.txt"),error.toString(),StandardOpenOption.CREATE_NEW); }
            catch(Exception ignored) { }
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange,HttpResponse<byte[]> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");
        String file="temp-proxy-"+phase+"-"+requests.size()+".bin";
        Files.write(attempt.resolve(file),response.body(),StandardOpenOption.CREATE_NEW);
        boolean drop=phase.equals("source")&&lost==0;
        requests.add(SubmissionJson.object().put("method",exchange.getRequestMethod()).put("path",path)
            .put("status",response.statusCode()).put("bodyFile",file).put("dropped",drop));
        if(drop) { lost++;exchange.close();return; }
        response.headers().map().forEach((key,values)->{
            if(!Set.of("content-length","transfer-encoding","connection").contains(key.toLowerCase(Locale.ROOT)))
                exchange.getResponseHeaders().put(key,values);
        });
        exchange.sendResponseHeaders(response.statusCode(),response.body().length);
        try(var out=exchange.getResponseBody()) { out.write(response.body()); }
        exchange.close();
    }

    @Override public void close() throws Exception {
        server.stop(0);
        Files.writeString(attempt.resolve("temp-proxy-"+phase+"-requests.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requests),StandardOpenOption.CREATE_NEW);
        assertThat(Files.exists(attempt.resolve("temp-proxy-"+phase+"-error.txt"))).isFalse();
        assertThat(lost).isEqualTo(phase.equals("source")?1:0);
        assertThat(requests).hasSize(phase.equals("source")?2:1);
    }
}
