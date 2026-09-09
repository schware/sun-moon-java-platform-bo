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
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    location VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

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

-- 대/중/소 size tiers deliberately excluded for now. image_url is a plain
-- link, not a stored file — there is no file/object storage in this
-- family yet, and building one is out of scope for a first pass.
CREATE TABLE IF NOT EXISTS menus (
    menu_code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

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
