package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.board.Post;
import com.sunmoon.bo.domain.board.PostRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPostRepository implements PostRepository {

    private static final String COLUMNS = "id, title, content, author, created_at, updated_at";
    private static final RowMapper<Post> MAPPER = (rs, rowNum) -> new Post(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("author"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcPostRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Post> findAll() {
        // Newest first: a notice board is read from the top.
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM posts ORDER BY id DESC", MAPPER);
    }

    @Override
    public Optional<Post> find(long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM posts WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public Post insert(String title, String content, String author) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('posts_id_seq')", Long.class);
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO posts (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                id, title, content, author, Timestamp.from(now), Timestamp.from(now));
        return new Post(id, title, content, author, now, now);
    }

    @Override
    public boolean update(long id, String title, String content) {
        // author and created_at are deliberately not updatable: editing a
        // notice should not rewrite who posted it or when.
        return jdbcTemplate.update(
                "UPDATE posts SET title = ?, content = ?, updated_at = ? WHERE id = ?",
                title, content, Timestamp.from(Instant.now()), id) > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM posts WHERE id = ?", id) > 0;
    }
}
