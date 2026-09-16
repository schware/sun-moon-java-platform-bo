-- Plain relational columns, not the JSONB shape Order and Delivery use.
-- Those two were designed as document stores before MongoDB turned out to
-- be unusable on this CPU (umbrella docs/adr/0001); BO's data is the
-- opposite - operators own permissions, common codes have a composite key,
-- and every query here filters on a column. Per-service choice, same as
-- KDS using Redis.

CREATE TABLE IF NOT EXISTS operators (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS operator_screen_permissions (
    operator_id BIGINT NOT NULL REFERENCES operators(id),
    screen VARCHAR(32) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT FALSE,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_save BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (operator_id, screen)
);

CREATE TABLE IF NOT EXISTS common_codes (
    group_code VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (group_code, code)
);

-- device_type is expected to name a common_codes row in the DEVICE_TYPE
-- group, but is deliberately not a foreign key: common codes are editable
-- at runtime, and a dangling type should not make a device row unreadable.
-- terminal_type is a separate, narrower column from device_type: it is one
-- of the Device Server's own POS/KDS/DID values for a device that
-- connects there, null for equipment that doesn't. ip_address/port are
-- addresses BO can dial to check "is anything answering" — not the same
-- fact as the Device Server's own live connection registry.
-- store_id is which 매장 the terminal stands in, added 2026-09-15 when
-- 단말 현황 needed to line these rows up against the Device Server's
-- registry — a terminal there is (매장, 단말기번호, 종류), because every
-- branch has a pos-01. Nullable: equipment that never connects has no
-- store, and rows written before this column have none to infer. Not a
-- foreign key to stores, same reasoning as device_type.
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    location VARCHAR(200),
    store_id VARCHAR(64),
    terminal_type VARCHAR(16),
    ip_address VARCHAR(45),
    port INT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE devices ADD COLUMN IF NOT EXISTS terminal_type VARCHAR(16);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS port INT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS store_id VARCHAR(64);

-- 공지사항. author is the operator username as it was at posting time,
-- not a foreign key: a notice should stay readable after its author's
-- account is removed.
CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at DESC);

-- 매장 기준 정보 only. Whether a store is currently 개점 or 마감, and which
-- 영업일자 it is on, is operational state and lives in the Order service —
-- every order stamps a 매출일자 and every acceptance mints a 거래번호 there,
-- and asking BO on each of those would put a network hop inside the sale.
-- BO reads that state back for display.
CREATE TABLE IF NOT EXISTS stores (
    store_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    address VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 대/중/소 size tiers deliberately excluded for now. image_url is a plain
-- link, not a stored file — there is no file/object storage in this
-- family yet, and building one is out of scope for a first pass.
-- kds_device_id is not a foreign key, same reasoning as devices.terminal_type
-- being freeform: not enforced yet, and it isn't what actually routes a
-- real order today anyway — sun-moon-java-platform-channel-order's own
-- catalog carries this independently, because that's what places real
-- orders. This column is for when BO's menu master becomes the real one.
CREATE TABLE IF NOT EXISTS menus (
    menu_code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    description VARCHAR(500),
    kds_device_id VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE menus ADD COLUMN IF NOT EXISTS kds_device_id VARCHAR(64);

-- A menu's price is never a single column — it's whichever row here
-- covers the lookup date. end_date NULL means "open-ended", i.e. still
-- the current price. Overlap between two rows for the same menu is
-- rejected in MenuService, not here: Postgres range-exclusion constraints
-- need btree_gist, which is more machinery than this needs.
CREATE TABLE IF NOT EXISTS menu_prices (
    id BIGSERIAL PRIMARY KEY,
    menu_code VARCHAR(64) NOT NULL REFERENCES menus(menu_code),
    price NUMERIC(10,2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_menu_prices_menu_code ON menu_prices (menu_code, start_date);

-- 매장의 테이블 배치도 (umbrella docs/adr/0007).
--
-- 테이블은 신원도 계산서도 아니고 **배송지**입니다 — "이 메뉴를 저 자리로".
-- 그래서 번호만이 아니라 위치를 들고 있습니다: 언젠가 로봇이 갈 곳입니다.
--
-- 대리 키(id)를 쓰는 이유가 있습니다. 자연 키를 (store_id, table_no)로
-- 잡으면 테이블 번호를 바꾸는 일이 delete+insert가 되고, 그때 qr_code가
-- 같이 날아갑니다. **QR은 인쇄돼서 테이블에 붙어 있어 다시 못 만듭니다.**
-- 대리 키면 번호 변경이 그냥 UPDATE이고 스티커는 그대로 삽니다.
--
-- floor를 처음부터 넣습니다. 나중에 추가하면 배치도를 다시 그려야 합니다.
--
-- pos_x / pos_y 는 추상 격자입니다. 지금은 화면에 그리는 용도뿐이고,
-- 실측이 필요해지면 stores.grid_scale_cm 으로 환산합니다 — 지금부터
-- 미터라고 우기지도, 나중에 환산 불가능하게 만들지도 않는 선입니다.
CREATE TABLE IF NOT EXISTS store_tables (
    id BIGSERIAL PRIMARY KEY,
    store_id VARCHAR(64) NOT NULL,
    floor INT NOT NULL DEFAULT 1,
    table_no VARCHAR(32) NOT NULL,
    display_name VARCHAR(100),
    pos_x INT,
    pos_y INT,
    -- 테이블 QR이 담는 짧은 불투명 코드. 주소에 매장·테이블을 그대로
    -- 적지 않는 것은 보안이 아니라 **간접층** 때문입니다: 자리를 옮기거나
    -- 번호를 바꿔도 DB 한 줄만 고치면 됩니다. 스티커는 못 고칩니다.
    qr_code VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (store_id, floor, table_no),
    UNIQUE (qr_code)
);

CREATE INDEX IF NOT EXISTS idx_store_tables_store ON store_tables (store_id, floor);

-- 격자 한 칸이 실제로 몇 cm인지. 로봇이 좌표를 쓸 때 필요해집니다.
-- 매장마다 다를 수 있어서 매장에 답니다. null이면 "아직 안 쟀다"입니다.
ALTER TABLE stores ADD COLUMN IF NOT EXISTS grid_scale_cm INT;
