package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class UserPasswordPolicyIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords; @Autowired IdentityStore identities;
    private PublicationDriver web; private UserManagementDriver users;

    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        users=new UserManagementDriver(web);
    }

    static Stream<String> acceptedPasswords() {
        return Stream.of("scan2026","87654321","abcdefgh","仓库密码现场使用","a".repeat(72),"密".repeat(24));
    }

    @ParameterizedTest @MethodSource("acceptedPasswords")
    void acceptsPasswordsWithoutCompositionRequirementsForCreateAndReset(String password) throws Exception {
        var body=users.createBody().put("password",password);
        String id=users.create(body).get("userId").asText();
        var person=identities.loadUserByUsername(body.get("username").asText());
        assertThat(passwords.matches(password,person.getPassword())).isTrue();

        var reset=json.createObjectNode().put("expectedVersion","1").put("password",password).put("reason","验证密码边界");
        web.data(users.command("/"+id+"/password",reset,UUID.randomUUID().toString()));
        assertThat(users.item(id).get("rowVersion").asText()).isEqualTo("2");
        assertThat(passwords.matches(password,identities.loadUserByUsername(person.username()).getPassword())).isTrue();
    }

    static Stream<String> rejectedPasswords() {
        return Stream.of("","short7x","密码不足七个字","        ","a".repeat(73),"密".repeat(25));
    }

    @ParameterizedTest @MethodSource("rejectedPasswords")
    void invalidPasswordsDoNotCreateOrResetUsers(String password) throws Exception {
        var body=users.createBody();String id=users.create(body).get("userId").asText();
        var invalidCreate=users.createBody().put("password",password);
        web.send(users.command("",invalidCreate,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(web.data(get("/api/v1/users").param("username",invalidCreate.get("username").asText())).get("total").asInt()).isZero();

        var reset=json.createObjectNode().put("expectedVersion","1").put("password",password).put("reason","验证拒绝后保留原密码");
        web.send(users.command("/"+id+"/password",reset,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(users.item(id).get("rowVersion").asText()).isEqualTo("1");
        assertThat(passwords.matches(body.get("password").asText(),identities.loadUserByUsername(body.get("username").asText()).getPassword())).isTrue();
    }

    @Test void eightCharacterResetWorksForWebAndPdaAndRevokesOldSessions() throws Exception {
        var body=users.createBody();String id=users.create(body).get("userId").asText();
        String username=body.get("username").asText();String previous=body.get("password").asText();
        var person=identities.loadUserByUsername(username);
        var pda=new PdaTestDriver(new PublicationDriver(mvc,json,new IdentityFixture(person,web.identity().warehouse())));
        var device=new PdaTestDriver(web).register();var oldTokens=pda.login(device);
        var oldWeb=mvc.perform(post("/api/v1/auth/web/login").with(csrf()).param("username",username).param("password",previous))
            .andExpect(status().isOk()).andReturn().getResponse().getCookies();

        var reset=json.createObjectNode().put("expectedVersion","1").put("password","scan2026").put("reason","简化PDA密码");
        web.data(users.command("/"+id+"/password",reset,UUID.randomUUID().toString()));

        mvc.perform(get("/api/v1/me").cookie(oldWeb)).andExpect(status().isUnauthorized());
        pda.send(get("/api/v1/pda/devices/me"),oldTokens).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/web/login").with(csrf()).param("username",username).param("password",previous))
            .andExpect(status().isUnauthorized());
        mvc.perform(pda.command("/auth/login",pda.loginBody(device))).andExpect(status().isUnauthorized());
        var currentWeb=mvc.perform(post("/api/v1/auth/web/login").with(csrf()).param("username",username).param("password","scan2026"))
            .andExpect(status().isOk()).andReturn().getResponse().getCookies();
        mvc.perform(get("/api/v1/me").cookie(currentWeb)).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));
        var currentTokens=pda.data(pda.command("/auth/login",pda.loginBody(device).put("password","scan2026")));
        pda.send(get("/api/v1/pda/devices/me"),currentTokens).andExpect(status().isOk());
    }
}
