package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="R08_PREVIEW_SEED",matches="true")
class R08PreviewSeedIT {
    static {
        String url=System.getenv("OUTBOUND_DB_URL");
        if(url==null || !url.startsWith("jdbc:mysql://127.0.0.1:23308/acrylic_r08_preview"))
            throw new IllegalStateException("Preview seed requires the dedicated R08 preview database");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void seedSyntheticFormalOrdersThroughExistingApi() throws Exception {
        Path target=Path.of(System.getenv("OUTBOUND_STORAGE"),"preview.json");
        assertThat(target).doesNotExist();
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var api=new PublicationDriver(mvc,json,identity);
        var pc=new PcFixture(api,false,8,8);pc.start();for(int i=0;i<24;i++) pc.add(i);pc.submit("");
        var pdaIdentity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var pda=new SubmissionFixture(new PublicationDriver(mvc,json,pdaIdentity),jdbc,false,false);
        pda.addAcknowledgedFailures(2);pda.submit();
        json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(),Map.of("username",identity.user().username(),
                "password","Test-password-123","warehouse",identity.warehouse().toString(),"order",pc.order,
                "barcodes",pc.barcodes,"pdaUsername",pdaIdentity.user().username(),"pdaWarehouse",pdaIdentity.warehouse().toString(),"pdaOrder",pda.order));
    }
}
