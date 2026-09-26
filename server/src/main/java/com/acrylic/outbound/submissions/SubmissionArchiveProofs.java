package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionArchiveProofs {
    private final OfflineSigner signer;
    public SubmissionArchiveProofs(OfflineSigner signer) { this.signer=signer; }
    public ObjectNode sign(ObjectNode archive) {
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-archive");
        claims.set("archive",archive.deepCopy());return archive.put("signedArchive",signer.sign(claims).token());
    }
    public void verify(JsonNode value,String field,String audience,String claim) {
        try {
            String token=text(value,field);require(token.length()<=1048576,"签名过大");
            var parts=token.split("\\.",-1);require(parts.length==3,"签名格式无效");
            var decoder=Base64.getUrlDecoder();var header=parse(decoder.decode(parts[0]));
            String kid=text(header,"kid");var key=signer.publicKeyFor(kid);
            require(key!=null && text(header,"alg").equals("RS256") && text(header,"typ").equals("JWT")
                && kid.equals(key.keyId()) && "RS256".equals(key.algorithm()),"签名公钥不匹配");
            var publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(publicKey);
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));require(verifier.verify(decoder.decode(parts[2])),"签名无效");
            var claims=parse(decoder.decode(parts[1]));
            require(text(claims,"iss").equals("acrylic-outbound") && text(claims,"aud").equals(audience),"签名用途不匹配");
            var unsigned=(ObjectNode)value.deepCopy();unsigned.remove(field);equal(claims.get(claim),unsigned,"签名内容不一致");
        } catch(GeneralSecurityException | RuntimeException error) { throw unavailable(); }
    }
    public static BusinessException unavailable() {
        return new BusinessException(503,"ARCHIVE_EVIDENCE_UNAVAILABLE","云端归档证据暂不可核验，本机原记录必须保留，请联系管理员核对");
    }
}
