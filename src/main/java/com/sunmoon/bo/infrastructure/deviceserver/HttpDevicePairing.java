package com.sunmoon.bo.infrastructure.deviceserver;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DevicePairing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Device Server 의 /pos/pairing-codes, /pos/devices/revoke, /pos/devices (관리 토큰). */
@Component
public class HttpDevicePairing implements DevicePairing {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROWS =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public HttpDevicePairing(@Qualifier("deviceServerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> issueCode(Device device, String issuedBy) {
        Map<String, Object> body = identity(device);
        body.put("kdsRole", device.kdsRole());
        body.put("issuedBy", issuedBy);
        return post("/pos/pairing-codes", body);
    }

    @Override
    public Map<String, Object> revoke(Device device) {
        return post("/pos/devices/revoke", identity(device));
    }

    @Override
    public Map<String, Object> setKioskPin(Device device, String pin, String setBy) {
        Map<String, Object> body = identity(device);
        body.put("pin", pin);
        body.put("setBy", setBy);
        return post("/pos/devices/kiosk-pin", body);
    }

    @Override
    public List<Map<String, Object>> devices(String storeId) {
        try {
            List<Map<String, Object>> rows = client.get()
                    .uri(uri -> {
                        uri.path("/pos/devices");
                        if (storeId != null && !storeId.isBlank()) {
                            uri.queryParam("storeId", storeId);
                        }
                        return uri.build();
                    })
                    .retrieve().body(ROWS);
            return rows == null ? List.of() : rows;
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("POS-SERVER", e);
        }
    }

    private static Map<String, Object> identity(Device device) {
        Map<String, Object> body = new HashMap<>();
        body.put("storeId", device.storeId());
        body.put("deviceId", device.deviceId());
        body.put("deviceType", device.terminalType());
        return body;
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            Map<String, Object> answer = client.post().uri(path).body(body).retrieve().body(MAP);
            return answer == null ? Map.of() : answer;
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("POS-SERVER", e);
        }
    }
}
