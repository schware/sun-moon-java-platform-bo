package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.config.SecurityConfig;
import com.sunmoon.bo.domain.commoncode.CommonCode;
import com.sunmoon.bo.domain.commoncode.CommonCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@code SecurityConfig}, so these exercise the actual
 * filter chain and {@code @PreAuthorize} rules rather than a controller
 * with authorization switched off.
 */
@WebMvcTest(CommonCodeController.class)
@Import(SecurityConfig.class)
class CommonCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonCodeService service;

    @Test
    void rejectsAnonymousWith401() throws Exception {
        mockMvc.perform(get("/api/common-code")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "DEVICE_VIEW")
    void rejectsAWrongScreensPermissionWith403() throws Exception {
        mockMvc.perform(get("/api/common-code")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_VIEW")
    void listsForAnOperatorWithViewOnThatScreen() throws Exception {
        when(service.list(nullable(String.class)))
                .thenReturn(List.of(new CommonCode("ORDER_STATUS", "PENDING", "대기", 1, true)));

        mockMvc.perform(get("/api/common-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PENDING"))
                .andExpect(jsonPath("$[0].name").value("대기"));
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_VIEW")
    void viewDoesNotImplyCreate() throws Exception {
        mockMvc.perform(post("/api/common-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G\",\"code\":\"C\",\"name\":\"N\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_CREATE")
    void createsForAnOperatorWithCreate() throws Exception {
        when(service.create(any(CommonCode.class))).thenReturn(true);

        mockMvc.perform(post("/api/common-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G\",\"code\":\"C\",\"name\":\"N\",\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_CREATE")
    void reportsADuplicateAs409() throws Exception {
        when(service.create(any(CommonCode.class))).thenReturn(false);

        mockMvc.perform(post("/api/common-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G\",\"code\":\"C\",\"name\":\"N\",\"sortOrder\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminBypassesScreenPermissions() throws Exception {
        when(service.list(nullable(String.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/common-code")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_CREATE")
    void rejectsAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/api/common-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G\",\"code\":\"C\",\"name\":\"N\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMMON_CODE_CREATE")
    void rejectsAnInvalidBody() throws Exception {
        mockMvc.perform(post("/api/common-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"\",\"code\":\"C\",\"name\":\"N\",\"sortOrder\":1}"))
                .andExpect(status().isBadRequest());
    }
}
