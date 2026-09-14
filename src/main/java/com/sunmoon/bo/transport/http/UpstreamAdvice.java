package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 502 rather than 500 for the two screens that read another service, and
 * a message that names which one.
 *
 * <p>The distinction matters to whoever is looking at the screen: a 500
 * says "BO is broken, call whoever runs BO", a 502 naming Order says
 * "Order is down, and BO is fine". Getting that wrong costs an hour of
 * looking in the wrong place — which has already happened once in this
 * family, when a silently disabled event publisher looked like a terminal
 * bug.
 */
@RestControllerAdvice
public class UpstreamAdvice {

    private static final Logger log = LoggerFactory.getLogger(UpstreamAdvice.class);

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<Map<String, String>> onUnavailable(UpstreamUnavailableException e) {
        // The cause carries the connect/read failure; the response must not.
        log.warn("Upstream unavailable: {}", e.upstream(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage(), "upstream", e.upstream()));
    }
}
