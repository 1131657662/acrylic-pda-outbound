package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.parser.SkuParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SkuParserTest {
    private final SkuParser parser = new SkuParser();

    @Test void suppliedFilePreservesAll12639Bales() throws Exception {
        var preview = parser.parse(Files.readAllBytes(Path.of("../docs/sku.txt")));
        assertThat(preview.status()).isEqualTo("READY_FOR_REVIEW");
        assertThat(preview.totalRows()).isEqualTo(12639);
        assertThat(preview.validRows()).isEqualTo(12639);
        assertThat(preview.productCount()).isEqualTo(145);
        assertThat(preview.totalWeightGrams()).isEqualTo("3801362000");
        var bale = preview.rows().stream().filter(row -> row.parsedFields().barcode().equals("003B0051601")).findFirst().orElseThrow();
        assertThat(bale.parsedFields().model()).isEqualTo("H815B 1.7 RE 38 300");
        assertThat(bale.parsedFields().deliveryCode()).isEqualTo("003");
    }

    @ParameterizedTest @ValueSource(strings={"15.5", "389.5", "0.001", "300.500"})
    void positiveWeightsHaveNoArbitraryRangeBlock(String weight) {
        var preview = parser.parse(SkuFixture.row("003B0051601", weight));
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.rows().getFirst().parsedFields().rawWeight()).isEqualTo(weight);
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "300.5001", "1e3", "NaN", "300,5", ".5"})
    void unsupportedWeightsBlockWithoutRounding(String weight) {
        var preview = parser.parse(SkuFixture.row("003B0051601", weight));
        assertThat(preview.status()).isEqualTo("BLOCKED");
        assertThat(preview.validRows()).isZero();
    }

    @Test void identicalDuplicatesRequireExplicitDeduplication() {
        byte[] row = SkuFixture.row("003B0051601", "305");
        var preview = parser.parse(SkuFixture.concat(row, row));
        assertThat(preview.status()).isEqualTo("NEEDS_DEDUP_CONFIRMATION");
        assertThat(preview.duplicateRows()).isEqualTo(1);
        assertThat(preview.totalWeightGrams()).isEqualTo("305000");
    }

    @Test void conflictingBarcodeInvalidatesBothOccurrences() {
        var preview = parser.parse(SkuFixture.concat(SkuFixture.row("003B0051601", "305"),
            SkuFixture.row("003B0051601", "306")));
        assertThat(preview.errorRows()).isEqualTo(2);
        assertThat(preview.rows()).allMatch(row -> row.errorCode().equals("DUPLICATE_CONFLICT"));
    }

    @Test void mappingConflictInvalidatesAllRelatedRows() {
        var preview = parser.parse(SkuFixture.concat(SkuFixture.row("003B0051601", "305"),
            SkuFixture.row("DIFFERENT MODEL", "003B0051602", "306")));
        assertThat(preview.errorRows()).isEqualTo(2);
        assertThat(preview.rows()).allMatch(row -> row.errorCode().equals("MAPPING_CONFLICT"));
    }

    @Test void collectsBadLineWithoutPublishingValidSubset() {
        var preview = parser.parse(SkuFixture.concat("bad\r\n".getBytes(), SkuFixture.row("003B0051601", "305.5")));
        assertThat(preview.status()).isEqualTo("BLOCKED");
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.rows().get(1).lineNo()).isEqualTo(2);
    }

    @Test void rejectsMalformedEncoding() {
        byte[] bytes = SkuFixture.row("003B0051601", "305");
        bytes[33] = (byte) 0xff;
        assertThat(parser.parse(bytes).rows().getFirst().errorCode()).isEqualTo("INVALID_ENCODING");
    }

    @Test void bomBlankLinesAndMissingFinalNewlineKeepSourceLineNumbers() {
        byte[] row = Arrays.copyOf(SkuFixture.row("003B0051601", "305"), 57);
        var preview = parser.parse(SkuFixture.concat(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,13,10}, row));
        assertThat(preview.bomRemoved()).isTrue();
        assertThat(preview.ignoredRows()).isEqualTo(1);
        assertThat(preview.rows().getFirst().lineNo()).isEqualTo(2);
    }

    @Test void malformedLongLineHasBoundedPreviewWithOriginalFileRetainedByUploadLayer() {
        byte[] bytes = new byte[100000];
        Arrays.fill(bytes, (byte)'A');
        var preview = parser.parse(bytes);
        assertThat(preview.rows().getFirst().rawText()).hasSize(1024);
        assertThat(preview.status()).isEqualTo("BLOCKED");
    }

    @Test void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> parser.parse(new byte[0])).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parser.parse(new byte[SkuParser.MAX_BYTES + 1])).isInstanceOf(BusinessException.class);
    }
}
