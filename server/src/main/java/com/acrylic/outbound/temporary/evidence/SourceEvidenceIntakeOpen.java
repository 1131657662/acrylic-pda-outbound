package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;

public final class SourceEvidenceIntakeOpen {
    private static final Set<String> FIELDS=Set.of("operation","manifestId","manifestVersion","snapshotId",
        "source","sourceId","submissionId","workRound");
    private SourceEvidenceIntakeOpen() { }

    public static Parsed parse(byte[] requestBytes,UUID expectedSubmission,long expectedRound) {
        require(requestBytes!=null && requestBytes.length>0 && requestBytes.length<=262144,"OPEN_REQUEST_SIZE");
        var request=SourceEvidenceIntakeReplay.canonicalize(requestBytes);
        require(request.bytes().length<=262144,"OPEN_REQUEST_SIZE");
        var body=SubmissionJson.parse(request.bytes());fields(body);
        require("OPEN".equals(text(body,"operation")),"OPEN_OPERATION");
        require("TEMP".equals(text(body,"source")),"OPEN_SOURCE");
        var submission=uuid(body,"submissionId");require(submission.equals(expectedSubmission),"OPEN_SUBMISSION");
        long round=number(body,"workRound");require(round==expectedRound,"OPEN_WORK_ROUND");
        return new Parsed(request.bytes(),request.sha256(),text(body,"manifestId"),number(body,"manifestVersion"),
            text(body,"snapshotId"),text(body,"sourceId"),submission,round);
    }

    private static void fields(JsonNode body) {
        require(body.isObject(),"OPEN_OBJECT");var actual=new java.util.HashSet<String>();
        body.fieldNames().forEachRemaining(actual::add);require(actual.equals(FIELDS),"OPEN_FIELDS");
    }
    private static String text(JsonNode body,String key) {
        var value=body.get(key);require(value!=null && value.isTextual() && !value.textValue().isBlank(),"OPEN_"+key.toUpperCase());
        return value.textValue();
    }
    private static long number(JsonNode body,String key) {
        var value=body.get(key);require(value!=null && value.isIntegralNumber() && value.canConvertToLong(),"OPEN_"+key.toUpperCase());
        long number=value.longValue();require(number>0,"OPEN_"+key.toUpperCase());return number;
    }
    private static UUID uuid(JsonNode body,String key) {
        try { UUID value=UUID.fromString(text(body,key));require(value.toString().equals(text(body,key)),"OPEN_"+key.toUpperCase());return value; }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("OPEN_"+key.toUpperCase(),invalid); }
    }

    private static void require(boolean valid,String code) { if(!valid)throw new IllegalArgumentException(code); }

    public record Parsed(byte[] canonicalBytes,String requestSha256,String manifestId,long manifestVersion,
                          String snapshotId,String sourceId,UUID submissionId,long workRound) {
        public Parsed { canonicalBytes=canonicalBytes.clone(); }
        @Override public byte[] canonicalBytes() { return canonicalBytes.clone(); }
    }
}
