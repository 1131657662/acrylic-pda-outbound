package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.inventory.parser.SkuParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private final Access access;
    private final ServerState state;
    private final SkuParser parser;
    private final ImportFiles files;
    private final ImportWriter writer;
    public ImportService(Access access, ServerState state, SkuParser parser, ImportFiles files, ImportWriter writer) {
        this.access = access; this.state = state; this.parser = parser; this.files = files; this.writer = writer;
    }

    public UUID upload(Upload input, MultipartFile multipart) throws IOException {
        var actor = access.require("inventory:import", input.warehouse());
        state.requireWritable(input.epoch());
        if (!input.key().matches("[A-Za-z0-9._:-]{16,80}"))
            throw new BusinessException(400, "INVALID_IDEMPOTENCY_KEY", "幂等键须为16至80位ASCII标识");
        byte[] bytes;
        try (var stream = multipart.getInputStream()) { bytes = stream.readNBytes(SkuParser.MAX_BYTES + 1); }
        var preview = parser.parse(bytes);
        var stored = files.store(bytes);
        String digest = ImportFiles.sha256((input.warehouse() + "|" + stored.sha256() + "|" + preview.parserVersion())
            .getBytes(StandardCharsets.UTF_8));
        return writer.save(new ImportWriter.Prepared(input.warehouse(), actor.id(), input.key(), digest,
            filename(multipart.getOriginalFilename()), stored, preview, input.requestId(), input.epoch()));
    }

    private String filename(String name) {
        if (name == null || name.isBlank()) return "sku.txt";
        String safe = name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return safe.substring(0, Math.min(safe.length(), 200));
    }
    public record Upload(UUID warehouse, String key, String epoch, String requestId) { }
}
