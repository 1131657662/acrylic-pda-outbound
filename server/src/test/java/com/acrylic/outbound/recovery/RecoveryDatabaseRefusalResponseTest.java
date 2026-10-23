package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.*;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryDatabaseRefusalResponseTest {
    @ParameterizedTest @ValueSource(strings = {"servlet", "io", "runtime"})
    void closedBorrowRefusalAnswersWithoutStateAndRetiresTheRequest(String wrapper) throws Exception {
        var fixture = fixture();
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        var response = new MockHttpServletResponse();
        var denied = actualRefusal();
        try {
            fixture.filter.doFilter(request, response, (req, res) -> raise(wrapper, denied));
            assertRefusal(response);
            verifyNoInteractions(fixture.state);
            assertThat(fixture.registry.bound(Thread.currentThread())).isNull();
        } finally { fixture.destroy(request); }
        assertThat(fixture.registry.liveRoots()).isZero();
    }

    @Test void sameMessageAndSqlStateFromAnUnrelatedSqlFailureAreNotReclassified() throws Exception {
        var failure = new CannotGetJdbcConnectionException("ordinary driver failure",
            new SQLNonTransientConnectionException("RECOVERY_DATABASE_ADMISSION_REFUSED:SERVER_MODE", "08004"));
        assertUnchanged(failure, false);
    }

    @Test void aSuppressedRefusalDoesNotReplaceThePrimaryFailure() throws Exception {
        var failure = new IllegalStateException("ordinary failure");
        failure.addSuppressed(actualRefusal());
        assertUnchanged(failure, false);
    }

    @Test void aCommittedResponseKeepsTheOriginalFailure() throws Exception {
        assertUnchanged(new CannotGetJdbcConnectionException("committed", actualRefusal()), true);
    }

    @Test void mvcWrappedClosedBorrowRefusalPrecedesTheGenericErrorAdvice() throws Exception {
        var fixture = fixture();
        var mvc = MockMvcBuilders.standaloneSetup(new RefusingController(actualRefusal()))
            .setControllerAdvice(new RecoveryRefusalAdvice(fixture.responses), new ApiErrors(fixture.responses)).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/probe"))
            .andReturn().getResponse();
        assertRefusal(response);
        verifyNoInteractions(fixture.state);
    }

    private static void assertUnchanged(RuntimeException failure, boolean committed) throws Exception {
        var fixture = fixture();
        var request = new MockHttpServletRequest("GET", "/probe");
        var response = new MockHttpServletResponse();
        if (committed) { response.getWriter().write("already sent"); response.flushBuffer(); }
        try {
            assertThatThrownBy(() -> fixture.filter.doFilter(request, response, (req, res) -> { throw failure; }))
                .isSameAs(failure);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo(committed ? "already sent" : "");
            verifyNoInteractions(fixture.state);
        } finally { fixture.destroy(request); }
        assertThat(fixture.registry.liveRoots()).isZero();
    }

    private static void assertRefusal(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"RECOVERY_DATABASE_ADMISSION_REFUSED\"",
            "\"serverRecoveryEpoch\":\"unknown\"", "\"serviceMode\":\"UNAVAILABLE\"")
            .doesNotContain("AUTHORITY_UNAVAILABLE", "wrapper-secret", "<html");
    }

    private static SQLException actualRefusal() throws Exception {
        var control = RecoveryControlFixture.create();
        Files.move(control.root.resolve("current.json"), control.root.getParent().resolve("retained-current.json"));
        var check = new RecoveryClosedConnectionAdmission(control.location(), control.checkpoint, 1);
        return org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> check.verify(mock(Connection.class)));
    }

    private static void raise(String kind, SQLException denied) throws ServletException, IOException {
        var wrapped = new CannotGetJdbcConnectionException("wrapper-secret", denied);
        switch (kind) {
            case "servlet" -> throw new ServletException(wrapped);
            case "io" -> throw new IOException(wrapped);
            default -> throw wrapped;
        }
    }

    private static Fixture fixture() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);
        var state = mock(ServerState.class);
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var responses = new ApiResponses(state, json);
        return new Fixture(registry, state, responses, new RecoveryRequestAdmissionFilter(registry, responses));
    }

    private record Fixture(RecoveryOperationRegistry registry, ServerState state, ApiResponses responses,
            RecoveryRequestAdmissionFilter filter) {
        void destroy(MockHttpServletRequest request) {
            new RecoveryRequestRootListener(registry)
                .requestDestroyed(new jakarta.servlet.ServletRequestEvent(new MockServletContext(), request));
        }
    }

    @RestController static class RefusingController {
        private final SQLException denied;
        RefusingController(SQLException denied) { this.denied = denied; }
        @GetMapping("/probe") String probe() { throw new CannotGetJdbcConnectionException("wrapper-secret", denied); }
    }
}
