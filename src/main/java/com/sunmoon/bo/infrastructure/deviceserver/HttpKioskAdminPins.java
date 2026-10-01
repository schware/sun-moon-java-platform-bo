package com.sunmoon.bo.infrastructure.deviceserver;

import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.KioskAdminPins;
import com.sunmoon.bo.domain.UpstreamUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/** Device Server 의 /pos/devices/kiosk-pin (관리 토큰). */
@Component
public class HttpKioskAdminPins implements KioskAdminPins {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public HttpKioskAdminPins(@Qualifier("deviceServerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> set(Device device, String pin, String setBy) {
        Map<String, Object> body = new HashMap<>();
        body.put("storeId", device.storeId());
        body.put("deviceId", device.deviceId());
        body.put("pin", pin);
        body.put("setBy", setBy);
        try {
            Map<String, Object> answer = client.post().uri("/pos/devices/kiosk-pin").body(body).retrieve().body(MAP);
            return answer == null ? Map.of() : answer;
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("POS-SERVER", e);
        }
    }
}
