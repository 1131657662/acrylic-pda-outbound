package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Disconnects a real HTTP socket after success headers, before consuming any receipt body bytes. */
final class RecoveryConfirmationHttpLostResponseChecks {
    static void verify(Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,byte[] expected) throws Exception {
        byte[] body=RecoveryReceivedConfirmationChecks.encode(request.command());
        String headers="POST /api/v1/pda"+request.path()+" HTTP/1.1\r\nHost: 127.0.0.1:19434\r\n"
            +"Authorization: Bearer "+request.token()+"\r\nContent-Type: application/json\r\n"
            +"X-Server-Recovery-Epoch: "+text(request.command(),"transportRecoveryEpoch")+"\r\n"
            +"Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n";
        String responseHeaders;
        try(var socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",19434),5000); socket.setSoTimeout(45000);
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().write(body);
            socket.getOutputStream().flush(); responseHeaders=readHeaders(socket);
            assertThat(responseHeaders).startsWith("HTTP/1.1 200 ");
        }
        var replay=RecoveryReceivedConfirmationHttpChecks.post(request,request.command(),text(request.command(),"transportRecoveryEpoch"));
        assertThat(replay.statusCode()).isEqualTo(200); assertThat(replay.body()).isEqualTo(expected);
        Files.write(attempt.resolve("confirmation-http-lost-response.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("actualSocketClosedAfterSuccessHeaders",true).put("receiptBodyBytesRead",0).put("sameCommandSha256",sha256(body))
            .put("sameKeyRetryReceiptSha256",sha256(replay.body())).put("firstCreationResponseLost",false)));
    }
    private static String readHeaders(Socket socket) throws Exception {
        var bytes=new ByteArrayOutputStream(); int tail=0;
        while(bytes.size()<16384) {
            int value=socket.getInputStream().read(); if(value<0) throw new java.io.IOException("HTTP headers incomplete");
            bytes.write(value); tail=(tail<<8)|value;
            if(tail==0x0d0a0d0a) return bytes.toString(StandardCharsets.US_ASCII);
        }
        throw new java.io.IOException("HTTP headers exceed bound");
    }
    private RecoveryConfirmationHttpLostResponseChecks() { }
}
