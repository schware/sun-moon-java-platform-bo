package com.sunmoon.bo;

import com.sunmoon.bo.domain.operator.Operator;
import com.sunmoon.bo.domain.operator.OperatorRepository;
import com.sunmoon.bo.domain.operator.OperatorScreenPermission;
import com.sunmoon.bo.domain.operator.Screen;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the whole application the way the README's curl sequence does —
 * fetch a CSRF token, log in through Spring Security's own filter, then
 * use the session — so that sequence is a tested claim rather than a
 * plausible one.
 *
 * <p>No database: the operator lookup is mocked at the repository port and
 * schema initialisation is off, which is enough because nothing in this
 * flow reads a table other than {@code operators}.
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
class LoginFlowTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private OperatorRepository operatorRepository;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedOperator() {
        Operator operator = new Operator(
                1L, USERNAME, passwordEncoder.encode(PASSWORD), "Super Admin", false, true,
                List.of(new OperatorScreenPermission(Screen.DEVICE, true, false, false, false)));
        when(operatorRepository.existsAny()).thenReturn(true);
        when(operatorRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(operatorRepository.findByUsername(USERNAME)).thenReturn(Optional.of(operator));
    }

    @Test
    void csrfTokenIsAvailableWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void logsInAndThenAnswersMeFromTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf csrf = fetchCsrf(session);

        mockMvc.perform(post("/api/auth/login").session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.superAdmin").value(false))
                .andExpect(jsonPath("$.authorities[0]").value("DEVICE_VIEW"));
    }

    @Test
    void theSessionCarriesOnlyThePermissionsThatOperatorHas() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/auth/login").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("username", USERNAME).param("password", PASSWORD))
                .andExpect(status().isOk());

        // DEVICE_VIEW was granted; nothing on the common-code screen was.
        mockMvc.perform(get("/api/devices").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/common-code").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void aWrongPasswordIs401AndLeavesNoSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf csrf = fetchCsrf(session);

        mockMvc.perform(post("/api/auth/login").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("username", USERNAME).param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutInvalidatesTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/auth/login").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("username", USERNAME).param("password", PASSWORD))
                .andExpect(status().isOk());

        // The token rotates on login, which is why the README says to
        // re-read /auth/csrf afterwards.
        Csrf afterLogin = fetchCsrf(session);
        mockMvc.perform(post("/api/auth/logout").session(session)
                        .cookie(afterLogin.cookie()).header("X-XSRF-TOKEN", afterLogin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedOut").value(true));

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsReachableWithoutAuthenticationSoAProbeCanReadIt() throws Exception {
        // Deliberately not asserting 200: there is no database here, so the
        // DB health indicator reports DOWN and the endpoint answers 503.
        // That it answers at all is the claim — a probe must not be turned
        // away at the security filter.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();

        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new IllegalStateException("/auth/csrf did not set the XSRF-TOKEN cookie");
        }
        return new Csrf(cookie, cookie.getValue());
    }

    private record Csrf(Cookie cookie, String token) {
    }
}
