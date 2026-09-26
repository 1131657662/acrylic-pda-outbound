package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.TemporaryArchiveDriver;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;

/** Isolated test-only HTTP adapter. No Spring production route or operation registration. */
final class TemporaryArchiveHttpAdapter implements AutoCloseable {
    private final HttpServer server;private final TemporaryArchiveDriver driver;private final WebApplicationContext context;private final Path attempt;
    TemporaryArchiveHttpAdapter(WebApplicationContext context,Path attempt) throws IOException {
        if(!"true".equals(System.getenv("E2A_CROSS_VERIFY")))throw new IllegalStateException("Isolated runner required");
        this.context=context;this.attempt=attempt;driver=new TemporaryArchiveDriver(context);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",19435),0);server.createContext("/internal/",this::handle);server.start();
    }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            if(!exchange.getRequestMethod().equals("POST"))throw new BusinessException(405,"METHOD_NOT_ALLOWED","test adapter POST only");
            String authorization=exchange.getRequestHeaders().getFirst("Authorization");
            if(authorization==null || !authorization.startsWith("Bearer "))throw new BusinessException(401,"UNAUTHENTICATED","test session required");
            byte[] raw=exchange.getRequestBody().readNBytes(2049);if(raw.length>2048)throw new BusinessException(413,"REQUEST_TOO_LARGE","test request too large");
            var body=SubmissionJson.parse(raw);String token=authorization.substring(7);JsonNode result;
            if(exchange.getRequestURI().getPath().equals("/internal/confirm"))result=driver.confirm(UUID.fromString(body.path("submissionId").asText()),token);
            else if(exchange.getRequestURI().getPath().startsWith("/internal/archive/")) {
                result=driver.archive(UUID.fromString(exchange.getRequestURI().getPath().substring("/internal/archive/".length())),token,body);
                TemporaryArchiveVectors.write(context,attempt,result);
            } else throw new BusinessException(404,"NOT_FOUND","unknown test operation");
            var envelope=SubmissionJson.object().put("code","OK");envelope.set("data",result);send(exchange,200,envelope);
        } catch(BusinessException error) { send(exchange,error.status(),SubmissionJson.object().put("code",error.code()).put("message",error.getMessage())); }
        catch(Exception error) { send(exchange,500,SubmissionJson.object().put("code","INTERNAL_TEST_FAILURE").put("message",error.getClass().getSimpleName())); }
        finally { exchange.close(); }
    }
    private void send(HttpExchange exchange,int status,JsonNode response) throws IOException {
        byte[] bytes=SubmissionJson.canonical(response).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { server.stop(0); }
}
