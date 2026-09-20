package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionArchiveEvidence {
    private final SubmissionFiles files;private final SubmissionStore submissions;private final SubmissionArchiveProofs proofs;
    public SubmissionArchiveEvidence(SubmissionFiles files,SubmissionStore submissions,SubmissionArchiveProofs proofs) {
        this.files=files;this.submissions=submissions;this.proofs=proofs;
    }
    public Evidence read(SubmissionStore.Head head,JsonNode request) {
        if(!head.status().equals("SUCCEEDED")) throw new BusinessException(409,"ARCHIVE_NOT_READY","原批次尚未成功确认出库，本机原记录必须保留");
        try {
            String hash=submissions.payloadHash(head.control().id());require(hash!=null && head.receipt()!=null,"缺少原证据");
            byte[] payload=files.confirm(hash);var body=parse(payload);var receipt=parse(head.receipt());
            equal(SubmissionControl.read(head.control().id(),body),head.control(),"正文来源不一致");
            equal(uuid(body,"submissionId"),head.control().id(),"正文批次不一致");
            proofs.verify(receipt,"signedReceipt","acrylic-pda-receipt","receipt");
            validateReceipt(head,receipt,hash);
            var evidence=new Evidence(hash,sha(receipt),payload.length,canonical(receipt).getBytes(StandardCharsets.UTF_8),text(receipt,"completedAt"));
            if(!text(request,"payloadSha256").equals(evidence.payloadHash()) || !text(request,"receiptSha256").equals(evidence.receiptHash()))
                throw new BusinessException(409,"ARCHIVE_CONTENT_MISMATCH","归档摘要与本机原正文或回执不一致，请保留原记录核对");
            return evidence;
        } catch(BusinessException error) {
            if(error.code().equals("ARCHIVE_CONTENT_MISMATCH")) throw error;
            throw SubmissionArchiveProofs.unavailable();
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
    private void validateReceipt(SubmissionStore.Head head,JsonNode receipt,String hash) {
        var c=head.control();equal(uuid(receipt,"submissionId"),c.id(),"回执批次不一致");
        equal(uuid(receipt,"orderId"),c.order(),"回执订单不一致");equal(uuid(receipt,"freezeDeviceId"),c.device(),"回执设备不一致");
        equal(number(receipt,"workRound"),c.round(),"回执轮次不一致");equal(text(receipt,"issuedRecoveryEpoch"),c.epoch(),"回执代次不一致");
        equal(text(receipt,"payloadSha256"),hash,"回执摘要不一致");equal(text(receipt,"status"),"SUCCEEDED","回执状态不一致");
        equal(text(receipt,"orderStatus"),"COMPLETED","回执尚未完成");
        equal(uuid(receipt.get("source"),"sourceOrderId"),c.order(),"回执来源不一致");equal(text(receipt.get("source"),"kind"),"FORMAL","回执来源无效");
    }
    public void persistReceipt(Evidence evidence,boolean existing) {
        try {
            if(!existing) equal(files.store(evidence.receiptBytes()),evidence.receiptHash(),"回执文件摘要不一致");
            require(java.util.Arrays.equals(files.confirm(evidence.receiptHash()),evidence.receiptBytes()),"原回执文件不一致");
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
    public record Evidence(String payloadHash,String receiptHash,int payloadBytes,byte[] receiptBytes,String completedAt) { }
}
