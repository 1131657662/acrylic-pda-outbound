package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceQueries.*;

@Component
public class SourceEvidenceFiles {
    private final Path root;
    public SourceEvidenceFiles(@Value("${outbound.storage}") String directory) { root=Path.of(directory).toAbsolutePath().normalize().resolve("submissions"); }
    byte[] read(FileRecord record,int maximum) {
        if(record==null)throw SourceEvidenceAccess.missing();
        if(!record.sha256().matches("[a-f0-9]{64}") || record.bytes()<1 || record.bytes()>maximum
            || !record.privateKey().equals("submissions/"+record.sha256()+".json"))throw SourceEvidenceAccess.corrupt();
        var path=root.resolve(record.sha256()+".json");
        try {
            if(Files.isSymbolicLink(root) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw unavailable();
            if(Files.size(path)!=record.bytes())throw SourceEvidenceAccess.corrupt();
            try(var input=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                byte[] value=input.readNBytes(maximum+1);
                if(value.length!=record.bytes() || !ImportFiles.sha256(value).equals(record.sha256()))throw SourceEvidenceAccess.corrupt();
                return value;
            }
        } catch(IOException error) { throw unavailable(); }
    }
    private static BusinessException unavailable() { return new BusinessException(409,"SOURCE_EVIDENCE_FILE_UNAVAILABLE","原私有证据文件缺失或不可读，请保留资料并核对"); }
}
