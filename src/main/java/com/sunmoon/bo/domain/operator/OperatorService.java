package com.sunmoon.bo.domain.operator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 운영자 관리 — the screen that finally makes the three permission tiers
 * reachable. They have been enforced since BO's first commit; until now
 * the only way to grant one was an INSERT by hand, which is why the deploy
 * server has exactly one account.
 *
 * <p>Two rules here exist to stop an admin console locking everyone out of
 * itself, and both are enforced in the service rather than in the UI:
 *
 * <ol>
 *   <li>The last 전체 관리자 cannot be deleted, demoted or deactivated.
 *       {@link com.sunmoon.bo.config.AdminSeeder} only seeds when the
 *       table is <em>empty</em>, so an operators table holding nothing but
 *       ordinary accounts is unrecoverable without database access.</li>
 *   <li>Nobody can delete the account they are signed in as. Not because
 *       it is unrecoverable — someone else may well be an admin — but
 *       because the session outlives the row and every subsequent click
 *       fails in a way that looks like BO is broken.</li>
 * </ol>
 */
@Service
public class OperatorService {

    /** What happened, so the controller can pick a status code without re-deriving it. */
    public enum Outcome {
        OK,
        NOT_FOUND,
        DUPLICATE_USERNAME,
        PASSWORD_REQUIRED,
        LAST_SUPER_ADMIN,
        SELF
    }

    public record Result(Outcome outcome, OperatorSummary operator) {
        static Result of(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    private final OperatorRepository repository;
    private final PasswordEncoder passwordEncoder;

    public OperatorService(OperatorRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<OperatorSummary> list() {
        return repository.findAll().stream().map(OperatorSummary::of).toList();
    }

    public Optional<OperatorSummary> read(long id) {
        return repository.find(id).map(OperatorSummary::of);
    }

    @Transactional
    public Result create(OperatorRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            return Result.of(Outcome.PASSWORD_REQUIRED);
        }
        if (repository.findByUsername(request.username()).isPresent()) {
            return Result.of(Outcome.DUPLICATE_USERNAME);
        }

        Operator created = repository.create(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.wantsSuperAdmin());
        repository.replacePermissions(created.id(), request.toPermissions());
        // Re-read rather than trusting what create() returned: the
        // permissions were written afterwards, and a screen that shows the
        // pre-write state right after saving is its own small bug.
        return repository.find(created.id())
                .map(operator -> new Result(Outcome.OK, OperatorSummary.of(operator)))
                .orElseGet(() -> Result.of(Outcome.NOT_FOUND));
    }

    @Transactional
    public Result save(long id, OperatorRequest request) {
        Optional<Operator> existing = repository.find(id);
        if (existing.isEmpty()) {
            return Result.of(Outcome.NOT_FOUND);
        }

        Operator operator = existing.get();
        boolean losesAdmin = operator.superAdmin() && operator.active()
                && (!request.wantsSuperAdmin() || !request.wantsActive());
        if (losesAdmin && repository.countActiveSuperAdmins() <= 1) {
            return Result.of(Outcome.LAST_SUPER_ADMIN);
        }

        repository.update(id, request.displayName(), request.wantsSuperAdmin(), request.wantsActive());
        repository.replacePermissions(id, request.toPermissions());
        // Blank means "leave it": editing someone's permissions should not
        // require knowing or resetting their password.
        if (request.password() != null && !request.password().isBlank()) {
            repository.updatePassword(id, passwordEncoder.encode(request.password()));
        }

        return repository.find(id)
                .map(saved -> new Result(Outcome.OK, OperatorSummary.of(saved)))
                .orElseGet(() -> Result.of(Outcome.NOT_FOUND));
    }

    @Transactional
    public Outcome delete(long id, String currentUsername) {
        Optional<Operator> existing = repository.find(id);
        if (existing.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        Operator operator = existing.get();
        if (operator.username().equals(currentUsername)) {
            return Outcome.SELF;
        }
        if (operator.superAdmin() && operator.active() && repository.countActiveSuperAdmins() <= 1) {
            return Outcome.LAST_SUPER_ADMIN;
        }

        // Permissions first: they reference the operator row, and leaving
        // them would fail the delete on the foreign key.
        repository.replacePermissions(id, List.of());
        repository.delete(id);
        return Outcome.OK;
    }
}
