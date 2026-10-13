package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Checks historical issuance only. An expired signed credential never becomes new collection authority. */
@Component
public class IndependentCredentialProof {
    private final OfflineSigner signer;
    public IndependentCredentialProof(OfflineSigner signer) { this.signer=signer; }
    public Result verify(JsonNode original,IndependentCredentialHistory.Record record) {
        if(record==null)return new Result("UNVERIFIED","CREDENTIAL_NOT_FOUND");
        if(!original.equals(record.publicClaims()) || !DeviceCrypto.sha(record.token()).equals(text(original,"credentialSha256")))
            return new Result("UNVERIFIED","CREDENTIAL_CONTENT_MISMATCH");
        try { return signature(original,record.token()); }
        catch(GeneralSecurityException|IllegalArgumentException error) { return new Result("UNVERIFIED","CREDENTIAL_SIGNATURE_INVALID"); }
        catch(BusinessException error) {
            if(error.code().equals("SIGNING_KEY_UNAVAILABLE"))return new Result("UNVERIFIED","HISTORICAL_KEY_UNAVAILABLE");
            if(error.code().equals("INVALID_SUBMISSION"))return new Result("UNVERIFIED","CREDENTIAL_SIGNATURE_INVALID");
            throw error;
        }
    }
    private Result signature(JsonNode original,String token) throws GeneralSecurityException {
        require(token.length()<=16384,"原凭据过大");var parts=token.split("\\.",-1);require(parts.length==3,"原凭据格式不符");
        var header=parse(decode(parts[0]));exact(header,"alg typ kid");
        equal(text(header,"alg"),"RS256","原凭据算法不符");equal(text(header,"typ"),"JWT","原凭据类型不符");
        var key=signer.publicKeyFor(text(header,"kid"));
        if(key==null)return new Result("UNVERIFIED","HISTORICAL_KEY_UNAVAILABLE");
        var verifier=Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey()))));
        verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));require(verifier.verify(decode(parts[2])),"原凭据签名不符");
        claims(original,parse(decode(parts[1])));return new Result("VERIFIED","ORIGINAL_ISSUANCE_VERIFIED");
    }
    private void claims(JsonNode original,JsonNode claims) {
        exact(claims,"iss aud purpose scope credentialId operatorId deviceId warehouseId deviceKeySha256 serverRecoveryEpoch userAuthVersion deviceVersion issuedAt expiresAt capabilities");
        equal(text(claims,"iss"),"acrylic-outbound","原签发方不符");equal(text(claims,"aud"),"acrylic-pda-independent-v1","原用途不符");
        equal(text(claims,"purpose"),"INDEPENDENT_CAPTURE","原用途不符");equal(text(claims,"scope"),"pda:independent","原范围不符");
        var publicClaims=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)publicClaims).remove("credentialSha256");
        publicClaims.fields().forEachRemaining(field->equal(field.getValue(),claims.get(field.getKey()),"原签名声明不符"));
        var capabilities=object().put("mayCollectIndependent",true).put("maySubmit",false).put("mayConfirm",false)
            .put("stockAvailable",false).put("localDeletionAllowed",false);
        equal(claims.path("capabilities"),capabilities,"原能力边界不符");
    }
    private void exact(JsonNode node,String names) {
        require(node.isObject(),"原凭据对象不符");var fields=new HashSet<String>();node.fieldNames().forEachRemaining(fields::add);
        equal(fields,Set.of(names.split(" ")),"原凭据字段不符");
    }
    private byte[] decode(String value) {
        require(value.matches("[A-Za-z0-9_-]+"),"原凭据编码不符");byte[] bytes=Base64.getUrlDecoder().decode(value);
        equal(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),value,"原凭据编码不符");return bytes;
    }
    public record Result(String state,String reason) { }
}
