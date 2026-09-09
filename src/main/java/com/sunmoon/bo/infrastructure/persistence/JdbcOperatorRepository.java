package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.operator.Operator;
import com.sunmoon.bo.domain.operator.OperatorRepository;
import com.sunmoon.bo.domain.operator.OperatorScreenPermission;
import com.sunmoon.bo.domain.operator.Screen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOperatorRepository implements OperatorRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperatorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Operator> findByUsername(String username) {
        List<Operator> found = jdbcTemplate.query(
                "SELECT id, username, password_hash, display_name, super_admin, active "
                        + "FROM operators WHERE username = ?",
                (rs, rowNum) -> new Operator(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getBoolean("super_admin"),
                        rs.getBoolean("active"),
                        List.of()),
                username);

        if (found.isEmpty()) {
            return Optional.empty();
        }
        // Permissions are a second query rather than a join: an operator has
        // at most one row per screen, and a join would multiply the operator
        // columns across them for no gain at this size.
        Operator operator = found.get(0);
        return Optional.of(new Operator(
                operator.id(), operator.username(), operator.passwordHash(), operator.displayName(),
                operator.superAdmin(), operator.active(), findPermissions(operator.id())));
    }

    private List<OperatorScreenPermission> findPermissions(long operatorId) {
        return jdbcTemplate.query(
                "SELECT screen, can_view, can_create, can_save, can_delete "
                        + "FROM operator_screen_permissions WHERE operator_id = ?",
                (rs, rowNum) -> new OperatorScreenPermission(
                        Screen.valueOf(rs.getString("screen")),
                        rs.getBoolean("can_view"),
                        rs.getBoolean("can_create"),
                        rs.getBoolean("can_save"),
                        rs.getBoolean("can_delete")),
                operatorId);
    }

    @Override
    public boolean existsAny() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operators", Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Operator create(String username, String passwordHash, String displayName, boolean superAdmin) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('operators_id_seq')", Long.class);
        jdbcTemplate.update(
                "INSERT INTO operators (id, username, password_hash, display_name, super_admin, active) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE)",
                id, username, passwordHash, displayName, superAdmin);
        return new Operator(id, username, passwordHash, displayName, superAdmin, true, List.of());
    }
}
