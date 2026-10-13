package com.acrylic.outbound.exports;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.exports.ExportModels.*;

class ExportVersionCompatibilityTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void omittedVersionKeepsOriginalRequestAtVersionOne() throws Exception {
        var command=json.readValue("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}",Command.class);
        assertThat(command.version()).isEqualTo(1L);
        assertThat(new Command(Format.PDF,"ORIGINAL_CONFIRMED",null).version()).isEqualTo(1L);
    }

    @Test void explicitCorrectedVersionSurvivesRequestSerialization() throws Exception {
        var command=new Command(Format.XLSX,"CORRECTED",null,2L);
        assertThat(json.readValue(json.writeValueAsString(command),Command.class)).isEqualTo(command);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(command)).isEmpty();
            assertThat(factory.getValidator().validate(new Command(Format.XLSX,"CORRECTED",null,0L))).isNotEmpty();
            assertThat(factory.getValidator().validate(new Command(Format.XLSX,"OTHER",null,2L))).isNotEmpty();
        }
    }

    @Test void oldStoredPayloadWithoutVersionStillMeansVersionOne() throws Exception {
        var payload=json.readValue("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}",Payload.class);
        assertThat(payload.version()).isEqualTo(1L);
        assertThat(payload.members()).isEmpty();
    }

    @Test void correctedStoredPayloadKeepsTheSelectedVersion() throws Exception {
        var payload=json.readValue("{\"format\":\"PDF\",\"basis\":\"CORRECTED\",\"resultVersion\":2}",Payload.class);
        assertThat(json.readValue(json.writeValueAsString(payload),Payload.class).version()).isEqualTo(2L);
    }
}
