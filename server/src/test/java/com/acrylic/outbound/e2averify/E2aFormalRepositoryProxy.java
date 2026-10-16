package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Loopback transport fault only: upstream public HTTP commits before one success response is lost. */
final class E2aFormalRepositoryProxy implements AutoCloseable {
    private final HttpServer server;
    private final Path attempt;
    private final String phase;
    private final boolean loseSuccess;
    private final List<ObjectNode> requests=new ArrayList<>();
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private int lost;

    E2aFormalRepositoryProxy(Path attempt,String phase,boolean loseSuccess) throws Exception {
        this.attempt=attempt;this.phase=phase;this.loseSuccess=loseSuccess;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",E2aTestPorts.http()+1),0);
        server.createContext("/api/v1/",this::forward);server.start();
    }

    private void forward(HttpExchange exchange) {
        try {
            String path=exchange.getRequestURI().getRawPath(),method=exchange.getRequestMethod();
            assertThat(exchange.getRequestURI().getRawQuery()).isNull();
            assertThat(path).startsWith("/api/v1/pda/");assertThat(method).isIn("GET","POST");
            byte[] body=exchange.getRequestBody().readAllBytes();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+E2aTestPorts.http()+path))
                .timeout(java.time.Duration.ofSeconds(90));
            for(String header:List.of("Authorization","X-Server-Recovery-Epoch","Content-Type"))
                if(exchange.getRequestHeaders().getFirst(header)!=null)request.header(header,exchange.getRequestHeaders().getFirst(header));
            request.method(method,body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
            respond(exchange,path,method,response);
        } catch(Throwable error) {
            try { Files.writeString(attempt.resolve("proxy-"+phase+"-error.txt"),error.toString(),StandardOpenOption.CREATE_NEW); }
            catch(Exception ignored) { }
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange,String path,String method,HttpResponse<byte[]> response) throws Exception {
        String file="proxy-"+phase+"-"+requests.size()+".bin";
        Files.write(attempt.resolve(file),response.body(),StandardOpenOption.CREATE_NEW);
        boolean drop=loseSuccess&&lost==0&&method.equals("POST")&&path.matches("/api/v1/pda/orders/[^/]+/submissions")&&response.statusCode()==200;
        var row=SubmissionJson.object().put("method",method).put("path",path).put("status",response.statusCode())
            .put("bodyFile",file).put("dropped",drop);requests.add(row);
        if(drop) {
            assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");
            lost++;exchange.close();return;
        }
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
        Files.writeString(attempt.resolve("proxy-"+phase+"-requests.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requests),StandardOpenOption.CREATE_NEW);
        assertThat(lost).isEqualTo(loseSuccess?1:0);
        long posts=requests.stream().filter(r->r.path("method").asText().equals("POST")&&
            r.path("path").asText().matches("/api/v1/pda/orders/[^/]+/submissions")).count();
        assertThat(posts).isEqualTo(loseSuccess?1:0);
    }
}
