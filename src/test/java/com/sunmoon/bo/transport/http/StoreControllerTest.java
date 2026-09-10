package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.config.SecurityConfig;
import com.sunmoon.bo.domain.store.Store;
import com.sunmoon.bo.domain.store.StoreService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
@Import(SecurityConfig.class)
class StoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoreService service;

    @Test
    @WithMockUser(authorities = "STORE_VIEW")
    void listsStores() throws Exception {
        when(service.list()).thenReturn(List.of(
                new Store("store-01", "1매장", "02-000-0000", "서울", true)));

        mockMvc.perform(get("/api/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storeId").value("store-01"))
                .andExpect(jsonPath("$[0].name").value("1매장"));
    }

    @Test
    @WithMockUser(authorities = "STORE_VIEW")
    void createNeedsItsOwnPermission() throws Exception {
        mockMvc.perform(post("/api/stores").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"store-01\",\"name\":\"1매장\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "STORE_CREATE")
    void reportsADuplicateStoreAs409() throws Exception {
        when(service.create(any(Store.class))).thenReturn(false);

        mockMvc.perform(post("/api/stores").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"store-01\",\"name\":\"1매장\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "STORE_SAVE")
    void reportsASaveOfAMissingStoreAs404() throws Exception {
        when(service.save(any(Store.class))).thenReturn(false);

        mockMvc.perform(put("/api/stores").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"nope\",\"name\":\"N\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "STORE_DELETE")
    void deletesByQueryStringKey() throws Exception {
        when(service.delete(anyString())).thenReturn(true);

        mockMvc.perform(delete("/api/stores").with(csrf()).param("storeId", "store-01"))
                .andExpect(status().isNoContent());
    }
}
