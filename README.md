# sun-moon-java-platform-bo

The **BO** (Back Office) service — part of the `sun-moon-java-platform`
family alongside
[Order](https://github.com/schware/sun-moon-java-platform-order),
[KDS](https://github.com/schware/sun-moon-java-platform-kds) and
[Delivery](https://github.com/schware/sun-moon-java-platform-delivery),
tied together by the
[umbrella repo](https://github.com/schware/sun-moon-java-platform) via git
submodules.

Operator accounts, 공지사항, 공통코드, 매장·메뉴·장비 기준 정보, and the two
read-only screens that look outward — 매출 조회 and 단말 현황 — an API
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
| `/bo/terminals` | 단말 현황 |
| `/bo/sales` | 매출 조회 |
| `/bo/operators` | 운영자 |

Every one of these needs a matching forward in `WebConfig` — the screens
are client-routed, so without it a refresh on that path is a real request
for a file that does not exist. `/menus` and `/stores` were missing for
five days and 404'd on refresh, which is the kind of thing nobody reports
because navigating to them worked fine.

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
| 조회 | `GET /api/terminals` | `TERMINAL_VIEW` |
| 조회 | `GET /api/sales?from=&to=&storeId=` | `SALES_VIEW` |
| 조회 | `GET /api/sales/orders?businessDate=&storeId=` | `SALES_VIEW` |
| 조회 | `GET /api/operators` | `OPERATOR_VIEW` |
| 조회 | `GET /api/operators/screens` (권한 매트릭스의 축) | `OPERATOR_VIEW` |
| 신규 | `POST /api/operators` | `OPERATOR_CREATE` |
| 저장 | `PUT /api/operators/{id}` | `OPERATOR_SAVE` |
| 삭제 | `DELETE /api/operators/{id}` | `OPERATOR_DELETE` |

**`SALES` and `TERMINAL` have only `VIEW`.** Neither has a write endpoint
and neither should get one: 매출 is the result of what happened at a
terminal, and a connection is a fact the Device Server holds. An admin
console able to edit either would make it impossible to say which side is
true. The permission matrix greys those three columns rather than offering
a permission that could only ever be inert.

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

**운영자 관리** (`/bo/operators`) is where tiers 2 and 3 are actually
granted, as a 화면 × 행위 matrix. It arrived on 2026-09-15; before that the
model was enforced from the first commit but the only way to grant
anything was an INSERT by hand, which is why the deploy server has exactly
one account. The screen posts the whole matrix and the server replaces the
set — merging would let an unticked box keep the permission it was meant
to remove.

Two rules stop the console locking everyone out of itself, both in the
service rather than the UI:

- **The last 전체 관리자** cannot be deleted, demoted or deactivated.
  `AdminSeeder` only seeds when the operators table is *empty*, so a table
  holding nothing but ordinary accounts needs database access to recover.
- **You cannot delete the account you are signed in as.** Not because it
  is unrecoverable — someone else may well be an admin — but because the
  session outlives the row and every later click then fails in a way that
  looks like BO is broken.

Password hashes never leave the server: the controller returns an
`OperatorSummary`, a separate record with no such component, rather than
an `@JsonIgnore` someone can forget to keep.

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
| `ORDER_SERVICE_URL` | `http://localhost:8083/order` | 매출 조회가 읽는 곳 |
| `DEVICE_SERVER_URL` | `http://localhost:8087` | 단말 현황이 읽는 곳 |

The seed never invents a default password: with the variables unset it
logs a warning and creates nothing.

The two URLs default to the deploy server's layout, where every container
runs `--network host` — hence localhost, and hence Order carrying its own
`/order` context path. Both clients time out in seconds rather than
hanging: an operator screen that hangs is indistinguishable from a broken
one, and nothing BO asks either service is on a write path.

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

## The two screens that read other services

BO owns 기준 정보. It does not own 매출 and it does not own who is
connected — those are facts that settle in Order and in the Device Server,
and BO asks for them on every page load rather than keeping a copy. A
screen showing a stale duplicate of either would be worse than one that
says the upstream is unreachable.

**매출 조회** reads `GET /sales` on Order, which groups by 영업일자 ×
매장. 영업일자 is not the calendar date — a shop trading past midnight is
still on yesterday's business day (umbrella `docs/adr/0006`). 금액은
수락된 주문만 셉니다; 거절과 미응답은 매출이 아니지만 그날 무슨 일이
있었는지는 알아야 하므로 건수로 따로 보입니다. BO adds exactly one thing
Order cannot know: which shop `store-07` is.

**단말 현황** reads `GET /terminals` on the Device Server and lines it up
against the 장비 master. Two disagreements are the point of the screen:

| | |
|---|---|
| 등록됐는데 접속 없음 | a counter with no screen up — that shop takes no orders |
| 접속했는데 등록 없음 | a screen that connected as an id nobody entered in 장비 |

The second exists because the Device Server checks only that a device id
is *well-formed* (`AcceptKnownFormatDirectory`), not that BO knows it.
This screen is currently the only place that gap is visible.

Matching needs (매장, 단말기번호, 종류), because every branch has a
`pos-01` and one store's POS and KDS can both be device id `01`. That is
why `devices` gained a `store_id` in 2026-09-15 — without it there was no
way to line the two halves up at all.

**A dead upstream is a 502 naming the service, never an empty table.**
"어제 매출이 0원" and "매출을 물어볼 수 없었다" are different facts, and
acting on the first while the second is true is the whole problem — this
family has already lost an hour to a silently disabled event publisher
that looked like a terminal bug.

## Not built yet

- **Distributed sessions.** Jetty's in-memory session store means one
  instance and a logout for everyone on each deploy. Spring Session +
  Redis is the upgrade, and Redis is already on the server.
- **매장의 개점/마감 상태.** The 매장 screen holds 기준 정보 only. Whether
  a store is currently open and which 영업일자 it is on is operational
  state owned by the Order service — it stamps a 매출일자 on every order
  and mints the 거래번호 at acceptance, so a network hop to BO inside a
  sale would be the wrong shape. POS opens and closes the store through
  the Device Server; BO reads the *result* of that back on 매출 조회 (an
  order's 매출일자), but there is still no screen showing which shops are
  trading right now. Order already answers it — `GET /business-days`.
- **Device Server checking handshakes against this list.** 단말 현황 now
  makes an unregistered terminal visible, but nothing refuses it. The port
  is already there (`DeviceDirectory`); what is missing is a decision about
  service-to-service credentials, not code.
- **매장별 메뉴.** Menus are not yet tied to stores, so every store
  offers the same list.
- **대/중/소 메뉴 옵션.** Menus are single, size-less items for now.
- **Stored menu images.** `imageUrl` is a plain link — there is no
  file/object storage in this family yet, so an operator hosts the image
  somewhere else and pastes the URL.
