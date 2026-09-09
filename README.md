# sun-moon-java-platform-bo

The **BO** (Back Office) service — part of the `sun-moon-java-platform`
family alongside
[Order](https://github.com/schware/sun-moon-java-platform-order),
[KDS](https://github.com/schware/sun-moon-java-platform-kds) and
[Delivery](https://github.com/schware/sun-moon-java-platform-delivery),
tied together by the
[umbrella repo](https://github.com/schware/sun-moon-java-platform) via git
submodules.

Operator accounts, 공통코드, and device master data. Spring Boot 3.3.4 on
embedded Jetty, PostgreSQL via `JdbcTemplate`, one Docker container, port
**8080**, context-path **`/bo`**.

Replaces
[sun-moon-platform-bo-netty](https://github.com/schware/sun-moon-platform-bo-netty)
(archived) — a Spring-free Netty implementation of the same screens. Why
that was rewritten rather than kept: see the umbrella repo's ADR-0004. The
short version is that BO is administration, not workload; it has no
throughput requirement that would pay for hand-rolling session auth,
BCrypt and per-action authorization, and it was the only service in this
family not built like the others.

## API

All paths are under the `/bo` context-path.

| | Endpoint | Permission |
|---|---|---|
| | `GET /auth/csrf` | — (call this first) |
| | `POST /auth/login` | — |
| | `POST /auth/logout` | authenticated |
| | `GET /auth/me` | authenticated |
| 조회 | `GET /common-code` (`?group=`) | `COMMON_CODE_VIEW` |
| 신규 | `POST /common-code` | `COMMON_CODE_CREATE` |
| 저장 | `PUT /common-code` | `COMMON_CODE_SAVE` |
| 삭제 | `DELETE /common-code?group=&code=` | `COMMON_CODE_DELETE` |
| 조회 | `GET /devices` (`?type=`) | `DEVICE_VIEW` |
| 신규 | `POST /devices` | `DEVICE_CREATE` |
| 저장 | `PUT /devices` | `DEVICE_SAVE` |
| 삭제 | `DELETE /devices?deviceId=` | `DEVICE_DELETE` |

Swagger UI at `/bo/swagger-ui/index.html`; `/` redirects there.

## Authorization — three tiers

1. **전체 관리자** — `operators.super_admin`, becomes `ROLE_SUPER_ADMIN`,
   passes every check.
2. **화면별** — an operator has a row in `operator_screen_permissions` for
   that `Screen`.
3. **화면별 조회 / 신규 / 저장 / 삭제** — the four boolean columns on that
   row, each becoming a `SCREEN_ACTION` authority.

Every controller method states its requirement inline
(`@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_SAVE')")`),
so a method that isn't permission-checked shows up as a missing
annotation rather than as an absence somewhere else.

`create` and `save` are separate operations, never an upsert, because
신규 and 저장 are separate permissions.

## Sessions, not JWT

Server-side sessions in an `HttpOnly` + `SameSite=Lax` cookie scoped to
`/bo`; passwords BCrypt at cost 12. An admin panel wants a compromised or
offboarded operator revoked *immediately* — one record — and JWT's
stateless advantage buys nothing for a service that runs as one instance.

CSRF protection is on, with the token in a readable `XSRF-TOKEN` cookie.

## Trying it with curl

```bash
BO=http://localhost:8080/bo

# 1. get a CSRF token (also sets the cookie jar)
TOKEN=$(curl -s -c jar.txt $BO/auth/csrf | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 2. log in (form-encoded — this is Spring Security's own filter)
curl -s -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $TOKEN" \
  -d "username=admin&password=..." $BO/auth/login

# 3. the session cookie is now in the jar
curl -s -b jar.txt $BO/auth/me
curl -s -b jar.txt $BO/common-code
```

Writes need the header on every call; re-read `$BO/auth/csrf` after login,
because the token rotates.

## Configuration

| Variable | Default | |
|---|---|---|
| `BO_ADMIN_USERNAME` / `BO_ADMIN_PASSWORD` | — | seeds the first 전체 관리자, only when no operator exists |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/bo_service` | |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `sunmoon` / `sunmoon_dev_pw` | |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `false` | must be `true` behind TLS |

The seed never invents a default password: with the variables unset it
logs a warning and creates nothing.

## Database

```bash
sudo -u postgres createdb -O sunmoon bo_service
```

`schema.sql` runs on startup (`spring.sql.init.mode: always`), same as the
sibling services. Plain relational columns rather than the JSONB shape
Order and Delivery use — their data was designed for a document store,
BO's is the opposite. Per-service choice, same reasoning as KDS on Redis;
see the umbrella repo's ADR-0001.

## Exposure

BO is **LAN-only** for now. Note that Docker's published (`-p`) ports
bypass UFW, so run this the way the family runs — `--network host` — and
scope the firewall rule to the subnet:

```bash
sudo ufw allow from 192.168.0.0/24 to any port 8080 proto tcp
```

## Not built yet

- **Operator management screen.** The `OPERATOR` screen exists as an enum
  value with no endpoints behind it, so tiers 2 and 3 are enforced but not
  yet administrable — permissions can only be set by SQL or the startup
  seed.
- **A Frontend.** BO is an API; the screens above are curl calls today.
- **Distributed sessions.** Jetty's in-memory session store means one
  instance and a logout for everyone on each deploy. Spring Session +
  Redis is the upgrade, and Redis is already on the server.
