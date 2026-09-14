package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.config.SecurityConfig;
import com.sunmoon.bo.domain.device.ConnectionCheckResult;
import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@Import(SecurityConfig.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService service;

    @Test
    @WithMockUser(authorities = "DEVICE_VIEW")
    void listsDevices() throws Exception {
        when(service.list(nullable(String.class)))
                .thenReturn(List.of(new Device(
                        "kds-01", "주방 1번", "KDS", "1층", "store-01", "KDS", "192.168.0.50", 80, true)));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("kds-01"))
                .andExpect(jsonPath("$[0].name").value("주방 1번"))
                .andExpect(jsonPath("$[0].storeId").value("store-01"));
    }

    @Test
    @WithMockUser(authorities = "DEVICE_VIEW")
    void saveNeedsItsOwnPermission() throws Exception {
        mockMvc.perform(put("/api/devices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"kds-01\",\"name\":\"N\",\"deviceType\":\"KDS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "DEVICE_SAVE")
    void reportsASaveOfAMissingDeviceAs404() throws Exception {
        when(service.save(any(Device.class))).thenReturn(false);

        mockMvc.perform(put("/api/devices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"nope\",\"name\":\"N\",\"deviceType\":\"KDS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "DEVICE_DELETE")
    void deletesByQueryStringKey() throws Exception {
        when(service.delete(anyString())).thenReturn(true);

        mockMvc.perform(delete("/api/devices").with(csrf()).param("deviceId", "kds-01"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "DEVICE_VIEW")
    void reportsWhetherTheRecordedAddressAnswers() throws Exception {
        when(service.checkConnection("kds-01")).thenReturn(Optional.of(ConnectionCheckResult.REACHABLE));

        mockMvc.perform(post("/api/devices/kds-01/connection-check").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("REACHABLE"));
    }

    @Test
    @WithMockUser(authorities = "DEVICE_VIEW")
    void connectionCheckOnAMissingDeviceIs404() throws Exception {
        when(service.checkConnection("nope")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/devices/nope/connection-check").with(csrf()))
                .andExpect(status().isNotFound());
    }
}
