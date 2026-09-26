package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DraftEvidenceWire {
    private DraftEvidenceWire() { }
    static JsonNode parse(byte[] bytes, Set<String> fields) {
        try {
            require(Arrays.equals(bytes,E2aProtocol.canonical(bytes)));
            var value=SubmissionJson.parse(bytes); require(value.isObject() && value.size()==fields.size());
            value.fieldNames().forEachRemaining(name->require(fields.contains(name))); return value;
        } catch(RuntimeException invalid) { throw invalid(); }
    }
    static byte[] bytes(JsonNode node) { return SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8); }
    static String sha(byte[] bytes) { return ImportFiles.sha256(bytes); }
    static void require(boolean valid) { if(!valid)throw invalid(); }
    static BusinessException invalid() { return new BusinessException(409,"DRAFT_EVIDENCE_CONFLICT","原停止资料或原请求不可核验，请保留本机原件"); }
    static BusinessException missing() { return new BusinessException(404,"DRAFT_EVIDENCE_NOT_FOUND","原停止资料不存在或无权访问"); }
    static BusinessException unavailable() { return new BusinessException(409,"DRAFT_EVIDENCE_UNAVAILABLE","原停止文件暂不可核验，请保留原件"); }
}
