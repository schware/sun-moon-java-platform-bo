package com.sunmoon.bo.domain.operator;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two guards that keep an admin console from locking everyone out of
 * itself. Worth testing directly: both are conditions nobody will hit until
 * the one time it matters, and the seeder only rescues an <em>empty</em>
 * operators table.
 */
class OperatorServiceTest {

    private final StubRepository repository = new StubRepository();
    private final OperatorService service = new OperatorService(repository, new PlainEncoder());

    private static OperatorRequest request(String username, boolean superAdmin, boolean active) {
        return new OperatorRequest(null, username, "password123", username, superAdmin, active, List.of());
    }

    @Test
    void refusesToDemoteTheLastSuperAdmin() {
        long id = repository.seed("admin", true, true);

        OperatorService.Result result = service.save(id, request("admin", false, true));

        assertThat(result.outcome()).isEqualTo(OperatorService.Outcome.LAST_SUPER_ADMIN);
        assertThat(repository.rows.get(id).superAdmin()).isTrue();
    }

    @Test
    void refusesToDeactivateTheLastSuperAdmin() {
        long id = repository.seed("admin", true, true);

        assertThat(service.save(id, request("admin", true, false)).outcome())
                .isEqualTo(OperatorService.Outcome.LAST_SUPER_ADMIN);
    }

    @Test
    void allowsDemotingOneOfTwo() {
        long first = repository.seed("admin", true, true);
        repository.seed("admin2", true, true);

        assertThat(service.save(first, request("admin", false, true)).outcome())
                .isEqualTo(OperatorService.Outcome.OK);
        assertThat(repository.rows.get(first).superAdmin()).isFalse();
    }

    @Test
    void refusesToDeleteTheAccountYouAreSignedInAs() {
        long id = repository.seed("admin", true, true);
        repository.seed("admin2", true, true);

        // Not the last admin — the objection is the live session, which
        // would outlive the row and make every later click look like a bug.
        assertThat(service.delete(id, "admin")).isEqualTo(OperatorService.Outcome.SELF);
        assertThat(repository.rows).containsKey(id);
    }

    @Test
    void refusesToDeleteTheLastSuperAdmin() {
        long id = repository.seed("admin", true, true);

        assertThat(service.delete(id, "someone-else")).isEqualTo(OperatorService.Outcome.LAST_SUPER_ADMIN);
    }

    @Test
    void anInactiveSuperAdminDoesNotCountAsOne() {
        repository.seed("retired", true, false);
        long id = repository.seed("admin", true, true);

        // The retired account cannot sign in, so it cannot rescue anyone.
        assertThat(service.delete(id, "someone-else")).isEqualTo(OperatorService.Outcome.LAST_SUPER_ADMIN);
    }

    @Test
    void refusesANewOperatorWithNoPassword() {
        OperatorRequest noPassword =
                new OperatorRequest(null, "newbie", "  ", "신입", false, true, List.of());

        assertThat(service.create(noPassword).outcome())
                .isEqualTo(OperatorService.Outcome.PASSWORD_REQUIRED);
    }

    @Test
    void refusesADuplicateUsername() {
        repository.seed("admin", true, true);

        assertThat(service.create(request("admin", false, true)).outcome())
                .isEqualTo(OperatorService.Outcome.DUPLICATE_USERNAME);
    }

    @Test
    void keepsTheExistingPasswordWhenTheFieldIsLeftBlank() {
        long id = repository.seed("staff", false, true);
        String before = repository.rows.get(id).passwordHash();

        service.save(id, new OperatorRequest(id, "staff", "", "직원", false, true, List.of()));

        assertThat(repository.rows.get(id).passwordHash()).isEqualTo(before);
    }

    @Test
    void neverExposesThePasswordHash() {
        long id = repository.seed("staff", false, true);

        OperatorSummary summary = service.read(id).orElseThrow();

        assertThat(summary.username()).isEqualTo("staff");
        // OperatorSummary has no such component — this is the type system
        // doing the work, and the assertion is here to say so out loud.
        assertThat(OperatorSummary.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password"));
    }

    private static final class PlainEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }

    private static final class StubRepository implements OperatorRepository {
        private final Map<Long, Operator> rows = new LinkedHashMap<>();
        private final Map<Long, List<OperatorScreenPermission>> permissions = new LinkedHashMap<>();
        private long nextId = 1;

        long seed(String username, boolean superAdmin, boolean active) {
            long id = nextId++;
            rows.put(id, new Operator(id, username, "hash:seeded", username, superAdmin, active, List.of()));
            return id;
        }

        @Override
        public Optional<Operator> findByUsername(String username) {
            return rows.values().stream().filter(o -> o.username().equals(username)).findFirst();
        }

        @Override
        public boolean existsAny() {
            return !rows.isEmpty();
        }

        @Override
        public Operator create(String username, String passwordHash, String displayName, boolean superAdmin) {
            long id = nextId++;
            Operator created = new Operator(id, username, passwordHash, displayName, superAdmin, true, List.of());
            rows.put(id, created);
            return created;
        }

        @Override
        public List<Operator> findAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public Optional<Operator> find(long id) {
            return Optional.ofNullable(rows.get(id)).map(o -> new Operator(
                    o.id(), o.username(), o.passwordHash(), o.displayName(), o.superAdmin(), o.active(),
                    permissions.getOrDefault(id, List.of())));
        }

        @Override
        public boolean update(long id, String displayName, boolean superAdmin, boolean active) {
            Operator existing = rows.get(id);
            if (existing == null) {
                return false;
            }
            rows.put(id, new Operator(id, existing.username(), existing.passwordHash(), displayName,
                    superAdmin, active, List.of()));
            return true;
        }

        @Override
        public boolean updatePassword(long id, String passwordHash) {
            Operator existing = rows.get(id);
            if (existing == null) {
                return false;
            }
            rows.put(id, new Operator(id, existing.username(), passwordHash, existing.displayName(),
                    existing.superAdmin(), existing.active(), List.of()));
            return true;
        }

        @Override
        public void replacePermissions(long id, List<OperatorScreenPermission> granted) {
            permissions.put(id, List.copyOf(granted));
        }

        @Override
        public boolean delete(long id) {
            return rows.remove(id) != null;
        }

        @Override
        public int countActiveSuperAdmins() {
            return (int) rows.values().stream().filter(o -> o.superAdmin() && o.active()).count();
        }
    }
}
