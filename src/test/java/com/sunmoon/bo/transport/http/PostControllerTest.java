package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.config.SecurityConfig;
import com.sunmoon.bo.domain.board.Post;
import com.sunmoon.bo.domain.board.PostRequest;
import com.sunmoon.bo.domain.board.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    private static final Instant WHEN = Instant.parse("2026-09-09T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService service;

    @Test
    void rejectsAnonymousWith401() throws Exception {
        mockMvc.perform(get("/api/board")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "BOARD_VIEW")
    void listsNotices() throws Exception {
        when(service.list()).thenReturn(List.of(
                new Post(1L, "점검 안내", "오늘 22시부터", "admin", WHEN, WHEN)));

        mockMvc.perform(get("/api/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("점검 안내"))
                .andExpect(jsonPath("$[0].author").value("admin"));
    }

    @Test
    @WithMockUser(authorities = "BOARD_VIEW")
    void viewDoesNotImplyCreate() throws Exception {
        mockMvc.perform(post("/api/board").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * The one that matters: a client must not be able to name its own
     * author. The controller takes it from the authentication instead.
     */
    @Test
    @WithMockUser(username = "hong", authorities = "BOARD_CREATE")
    void takesTheAuthorFromTheSessionNotTheBody() throws Exception {
        when(service.create(any(PostRequest.class), anyString()))
                .thenReturn(new Post(1L, "t", "c", "hong", WHEN, WHEN));

        mockMvc.perform(post("/api/board").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"author\":\"someone-else\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("hong"));

        verify(service).create(any(PostRequest.class), org.mockito.ArgumentMatchers.eq("hong"));
    }

    @Test
    @WithMockUser(authorities = "BOARD_SAVE")
    void reportsASaveOfAMissingPostAs404() throws Exception {
        when(service.save(anyLong(), any(PostRequest.class))).thenReturn(false);

        mockMvc.perform(put("/api/board/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "BOARD_SAVE")
    void savesAnExistingPost() throws Exception {
        when(service.save(anyLong(), any(PostRequest.class))).thenReturn(true);
        when(service.read(1L)).thenReturn(Optional.of(new Post(1L, "t2", "c2", "admin", WHEN, WHEN)));

        mockMvc.perform(put("/api/board/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t2\",\"content\":\"c2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("t2"));
    }

    @Test
    @WithMockUser(authorities = "BOARD_DELETE")
    void deletesByPathId() throws Exception {
        when(service.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/board/1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "BOARD_CREATE")
    void rejectsABlankTitle() throws Exception {
        mockMvc.perform(post("/api/board").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest());
    }
}
