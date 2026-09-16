package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.config.SecurityConfig;
import com.sunmoon.bo.domain.menu.Menu;
import com.sunmoon.bo.domain.menu.MenuPrice;
import com.sunmoon.bo.domain.menu.MenuService;
import com.sunmoon.bo.domain.menu.MenuSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuController.class)
@Import(SecurityConfig.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuService service;

    @Test
    @WithMockUser(authorities = "MENU_VIEW")
    void listsMenusWithTodaysPrice() throws Exception {
        when(service.list()).thenReturn(List.of(
                new MenuSummary("menu-01", "1메뉴", null, null, null, true, 8, new BigDecimal("6000"))));

        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].menuCode").value("menu-01"))
                .andExpect(jsonPath("$[0].currentPrice").value(6000));
    }

    @Test
    @WithMockUser(authorities = "MENU_VIEW")
    void createNeedsItsOwnPermission() throws Exception {
        mockMvc.perform(post("/api/menus").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuCode\":\"menu-01\",\"name\":\"1메뉴\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "MENU_SAVE")
    void reportsASaveOfAMissingMenuAs404() throws Exception {
        when(service.save(any(Menu.class))).thenReturn(false);

        mockMvc.perform(put("/api/menus").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuCode\":\"nope\",\"name\":\"N\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "MENU_DELETE")
    void deletesByQueryStringKey() throws Exception {
        when(service.delete(anyString())).thenReturn(true);

        mockMvc.perform(delete("/api/menus").with(csrf()).param("menuCode", "menu-01"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "MENU_SAVE")
    void rejectsAnOverlappingPriceRowAs409() throws Exception {
        when(service.addPrice(eq("menu-01"), any(MenuPrice.class)))
                .thenReturn(MenuService.PriceAddResult.OVERLAPS_EXISTING);

        mockMvc.perform(post("/api/menus/menu-01/prices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":6000,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "MENU_SAVE")
    void deletesAPriceRow() throws Exception {
        when(service.deletePrice(eq("menu-01"), anyLong())).thenReturn(true);

        mockMvc.perform(delete("/api/menus/menu-01/prices/5").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "MENU_VIEW")
    void reportsNoPriceForThatDateAs404() throws Exception {
        when(service.effectivePrice(eq("menu-01"), any(LocalDate.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/menus/menu-01/price").param("date", "2020-01-01"))
                .andExpect(status().isNotFound());
    }
}
