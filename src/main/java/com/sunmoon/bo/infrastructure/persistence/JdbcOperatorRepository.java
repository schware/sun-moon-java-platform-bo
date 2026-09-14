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

    private static final String COLUMNS = "id, username, password_hash, display_name, super_admin, active";

    private static final org.springframework.jdbc.core.RowMapper<Operator> MAPPER = (rs, rowNum) -> new Operator(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getBoolean("super_admin"),
            rs.getBoolean("active"),
            List.of());

    @Override
    public Optional<Operator> findByUsername(String username) {
        return withPermissions(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM operators WHERE username = ?", MAPPER, username));
    }

    @Override
    public Optional<Operator> find(long id) {
        return withPermissions(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM operators WHERE id = ?", MAPPER, id));
    }

    @Override
    public List<Operator> findAll() {
        // One permissions query per operator. That is an N+1, and it is the
        // right call at this size: BO has a handful of accounts, and the
        // alternative — a join, then regrouping in Java — is more code to
        // get wrong for a screen opened a few times a week.
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM operators ORDER BY username", MAPPER)
                .stream()
                .map(operator -> attach(operator, findPermissions(operator.id())))
                .toList();
    }

    private Optional<Operator> withPermissions(List<Operator> found) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // Permissions are a second query rather than a join: an operator has
        // at most one row per screen, and a join would multiply the operator
        // columns across them for no gain at this size.
        Operator operator = found.get(0);
        return Optional.of(attach(operator, findPermissions(operator.id())));
    }

    private static Operator attach(Operator operator, List<OperatorScreenPermission> permissions) {
        return new Operator(operator.id(), operator.username(), operator.passwordHash(),
                operator.displayName(), operator.superAdmin(), operator.active(), permissions);
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

    @Override
    public boolean update(long id, String displayName, boolean superAdmin, boolean active) {
        return jdbcTemplate.update(
                "UPDATE operators SET display_name = ?, super_admin = ?, active = ? WHERE id = ?",
                displayName, superAdmin, active, id) > 0;
    }

    @Override
    public boolean updatePassword(long id, String passwordHash) {
        return jdbcTemplate.update(
                "UPDATE operators SET password_hash = ? WHERE id = ?", passwordHash, id) > 0;
    }

    @Override
    public void replacePermissions(long id, List<OperatorScreenPermission> permissions) {
        jdbcTemplate.update("DELETE FROM operator_screen_permissions WHERE operator_id = ?", id);
        for (OperatorScreenPermission permission : permissions) {
            // A row granting nothing is the same fact as no row, and keeping
            // it would make the table grow one entry per screen per operator
            // for no reading anyone does.
            if (!permission.canView() && !permission.canCreate()
                    && !permission.canSave() && !permission.canDelete()) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO operator_screen_permissions "
                            + "(operator_id, screen, can_view, can_create, can_save, can_delete) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    id, permission.screen().name(), permission.canView(), permission.canCreate(),
                    permission.canSave(), permission.canDelete());
        }
    }

    @Override
    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM operators WHERE id = ?", id) > 0;
    }

    @Override
    public int countActiveSuperAdmins() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operators WHERE super_admin = TRUE AND active = TRUE", Integer.class);
        return count == null ? 0 : count;
    }
}
