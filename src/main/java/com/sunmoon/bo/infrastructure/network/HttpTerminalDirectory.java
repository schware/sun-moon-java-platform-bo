package com.sunmoon.bo.infrastructure.network;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.terminal.ConnectedTerminal;
import com.sunmoon.bo.domain.terminal.TerminalDirectory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * {@code GET /terminals} on the Device Server.
 *
 * <p>That endpoint is unauthenticated, which is only tolerable because it
 * listens on the LAN — the same named gap its own javadoc records. BO
 * calling it adds no exposure: BO is LAN-only too, and the operator
 * reading the result went through a login to get here.
 */
@Component
public class HttpTerminalDirectory implements TerminalDirectory {

    private static final ParameterizedTypeReference<TerminalsResponse> RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public HttpTerminalDirectory(@Qualifier("deviceServerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public List<ConnectedTerminal> connected() {
        try {
            TerminalsResponse body = client.get().uri("/terminals").retrieve().body(RESPONSE);
            return body == null || body.terminals() == null ? List.of() : body.terminals();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("Device Server", e);
        }
    }

    /** The Device Server wraps the list in {@code {count, terminals}}. */
    private record TerminalsResponse(int count, List<ConnectedTerminal> terminals) {
    }
}
