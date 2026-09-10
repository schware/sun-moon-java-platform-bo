# sun-moon-java-platform-bo

The **BO** (Back Office) service — part of the `sun-moon-java-platform`
family alongside
[Order](https://github.com/schware/sun-moon-java-platform-order),
[KDS](https://github.com/schware/sun-moon-java-platform-kds) and
[Delivery](https://github.com/schware/sun-moon-java-platform-delivery),
tied together by the
[umbrella repo](https://github.com/schware/sun-moon-java-platform) via git
submodules.

Operator accounts, 공지사항, 공통코드, and device master data — an API
**and the console that drives it**. Spring Boot 3.3.4 on embedded Jetty,
PostgreSQL via `JdbcTemplate`, a React + TypeScript UI served from the same
jar, one Docker container, port **8080**, context-path **`/bo`**.

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

**Screens** are client-routed paths; **the API** lives under `/api`. They
are separated because `/bo/board` was once both, and refreshing the board
screen returned JSON.

| Screen path | |
|---|---|
| `/bo/` | home — a summary of what this operator may do |
| `/bo/board` | 공지사항 |
| `/bo/common-code` | 공통코드 |
| `/bo/devices` | 장비 |
| `/bo/menus` | 메뉴 |
| `/bo/stores` | 매장 |

| | Endpoint | Permission |
|---|---|---|
| | `GET /api/auth/csrf` | — (call this first) |
| | `POST /api/auth/login` | — |
| | `POST /api/auth/logout` | authenticated |
| | `GET /api/auth/me` | authenticated |
| 조회 | `GET /api/board`, `GET /api/board/{id}` | `BOARD_VIEW` |
| 신규 | `POST /api/board` | `BOARD_CREATE` |
| 저장 | `PUT /api/board/{id}` | `BOARD_SAVE` |
| 삭제 | `DELETE /api/board/{id}` | `BOARD_DELETE` |
| 조회 | `GET /api/common-code` (`?group=`) | `COMMON_CODE_VIEW` |
| 신규 | `POST /api/common-code` | `COMMON_CODE_CREATE` |
| 저장 | `PUT /api/common-code` | `COMMON_CODE_SAVE` |
| 삭제 | `DELETE /api/common-code?group=&code=` | `COMMON_CODE_DELETE` |
| 조회 | `GET /api/devices` (`?type=`) | `DEVICE_VIEW` |
| 신규 | `POST /api/devices` | `DEVICE_CREATE` |
| 저장 | `PUT /api/devices` | `DEVICE_SAVE` |
| 삭제 | `DELETE /api/devices?deviceId=` | `DEVICE_DELETE` |
| 조회 | `GET /api/menus` (오늘 기준 가격 포함) | `MENU_VIEW` |
| 신규 | `POST /api/menus` | `MENU_CREATE` |
| 저장 | `PUT /api/menus` | `MENU_SAVE` |
| 삭제 | `DELETE /api/menus?menuCode=` (가격 이력도 함께 삭제) | `MENU_DELETE` |
| 조회 | `GET /api/menus/{menuCode}/prices` | `MENU_VIEW` |
| 신규 | `POST /api/menus/{menuCode}/prices` (겹치는 기간은 409) | `MENU_SAVE` |
| 삭제 | `DELETE /api/menus/{menuCode}/prices/{id}` | `MENU_SAVE` |
| 조회 | `GET /api/menus/{menuCode}/price?date=` (연동 시 사용할 유효 가격) | `MENU_VIEW` |
| 조회 | `GET /api/stores` | `STORE_VIEW` |
| 신규 | `POST /api/stores` | `STORE_CREATE` |
| 저장 | `PUT /api/stores` | `STORE_SAVE` |
| 삭제 | `DELETE /api/stores?storeId=` | `STORE_DELETE` |

Board keys on a numeric id in the path; Common Code and Device keep their
keys in the query string, a carry-over from the Netty router that had no
path variables. Menu is newer and uses a path variable for `menuCode`
(query string only for delete, matching the others), and price rows nest
under it by their own numeric id.

A menu's price is never a single column on the menu row — it is a
history of `menu_prices` rows, each valid for a date range (`endDate`
null = open-ended, still current). `GET /api/menus/{menuCode}/price?date=`
resolves the one row that covers a given date; that is what "메뉴 연동"
should call rather than reading any single "current price" field, because
there isn't one. Adding a price row that overlaps an existing one for the
same menu is rejected with 409 — there is deliberately no way for two
rows to disagree about what the price was on some date.

Swagger UI at `/bo/swagger-ui/index.html`.

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
API=http://localhost:8080/bo/api

# 1. get a CSRF token (also sets the cookie jar)
TOKEN=$(curl -s -c jar.txt $API/auth/csrf | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 2. log in (form-encoded — this is Spring Security's own filter)
curl -s -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $TOKEN" \
  -d "username=admin&password=..." $API/auth/login

# 3. the session cookie is now in the jar
curl -s -b jar.txt $API/auth/me
curl -s -b jar.txt $API/board
```

Writes need the header on every call; re-read `$API/auth/csrf` after login,
because the token rotates. Send Korean as UTF-8 — a shell that hands curl
CP949 gets a 400, which is a client bug that reads like a server one.

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

## The console

`frontend/` is a Vite + React + TypeScript app whose build output lands in
`src/main/resources/static`, so it ships **inside the jar and on the same
origin as the API**. That is the point: the session cookie and the CSRF
token need no CORS, and BO has no CORS configuration to give them.

```bash
cd frontend
npm install
npm run build     # → ../src/main/resources/static
npm run dev       # proxies /bo/api to a BO already running on :8080
```

You do not need Node to deploy. The Dockerfile builds the frontend in a
`node:20-alpine` stage and copies only the output forward, which is why
the deploy host still has no Node on it.

The left menu lists only screens the operator has some permission on, and
any button that would return 403 is disabled. **That is presentation, not
enforcement** — every controller method still carries its own
`@PreAuthorize`, and the server is what actually refuses.

## Not built yet

- **Operator management screen.** The `OPERATOR` screen exists as an enum
  value with no endpoints behind it, so tiers 2 and 3 are enforced but not
  yet administrable — permissions can only be set by SQL or the startup
  seed. Until that exists, the only account is the seeded super admin, and
  the permission-aware menu has nothing to hide from it.
- **Distributed sessions.** Jetty's in-memory session store means one
  instance and a logout for everyone on each deploy. Spring Session +
  Redis is the upgrade, and Redis is already on the server.
- **매장의 개점/마감 상태.** The 매장 screen holds 기준 정보 only. Whether
  a store is currently open and which 영업일자 it is on is operational
  state owned by the Order service — it stamps a 매출일자 on every order
  and mints the 거래번호 at acceptance, so a network hop to BO inside a
  sale would be the wrong shape. POS opens and closes the store through
  the Device Server; BO will read that state back for display, which is
  not wired yet.
- **매장별 메뉴.** Menus are not yet tied to stores, so every store
  offers the same list.
- **대/중/소 메뉴 옵션.** Menus are single, size-less items for now.
- **Stored menu images.** `imageUrl` is a plain link — there is no
  file/object storage in this family yet, so an operator hosts the image
  somewhere else and pastes the URL.
