package com.sunmoon.bo.domain.board;

import java.time.Instant;

/**
 * A notice on the operator board. {@code author} is taken from the
 * authenticated session, never from the request body — a client that could
 * name its own author could post as someone else.
 */
public record Post(Long id, String title, String content, String author, Instant createdAt, Instant updatedAt) {
}
