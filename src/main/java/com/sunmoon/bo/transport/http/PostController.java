package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.board.Post;
import com.sunmoon.bo.domain.board.PostRequest;
import com.sunmoon.bo.domain.board.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 공지사항 board. Same three permission tiers as every other screen, and
 * the same rule that each method states its requirement inline.
 *
 * <p>Unlike Common Code and Device, this screen keys on a numeric id in
 * the path rather than in the query string. Those two carry their keys in
 * the query string because the Netty router this service replaced had no
 * path variables; that constraint is gone, so new screens use the shape
 * Spring makes natural.
 */
@RestController
@RequestMapping("/api/board")
@Tag(name = "Board", description = "운영자 공지사항")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('BOARD_VIEW')")
    @Operation(summary = "조회", description = "Lists notices, newest first.")
    public List<Post> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('BOARD_VIEW')")
    @Operation(summary = "단건 조회")
    @ApiResponse(responseCode = "404", description = "No such post")
    public ResponseEntity<?> read(@PathVariable long id) {
        return service.read(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('BOARD_CREATE')")
    @Operation(summary = "신규", description = "The author is taken from the session, not the request body.")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<Post> create(@Valid @RequestBody PostRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('BOARD_SAVE')")
    @Operation(summary = "저장", description = "Updates title and content. Never changes the author or the created time.")
    @ApiResponse(responseCode = "404", description = "No such post")
    public ResponseEntity<?> save(@PathVariable long id, @Valid @RequestBody PostRequest request) {
        return service.save(id, request)
                ? ResponseEntity.ok(service.read(id).orElseThrow())
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('BOARD_DELETE')")
    @Operation(summary = "삭제")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such post")
    public ResponseEntity<?> delete(@PathVariable long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id));
    }

    private static Map<String, String> notFound(long id) {
        return Map.of("error", "No such post: " + id);
    }
}
