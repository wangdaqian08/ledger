-- The data model from docs/specs/2026-07-31-ledger-design.md §5.
--
-- The rule that shapes every table here: no computed share amount is ever stored. `owed`, `net`
-- and ALL_SQUARE are derived on read by the engine, so correcting an item's people list can never
-- leave a stale total behind. `weight` and `exact_amount_minor` are *inputs* to that derivation,
-- not results of it.
--
-- All money is BIGINT minor units. There is no numeric/decimal/float column in this file and
-- adding one is a bug.

CREATE TABLE users (
    id            uuid        PRIMARY KEY,
    provider      text        NOT NULL,
    subject       text        NOT NULL,
    email         text        NOT NULL,
    display_name  text        NOT NULL,
    photo_url     text,
    created_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT users_provider_subject_unique UNIQUE (provider, subject)
);

CREATE TABLE trips (
    id                 uuid        PRIMARY KEY,
    name               text        NOT NULL,
    icon               text        NOT NULL,               -- a Lucide slug: plane, house, coffee
    hue                smallint    NOT NULL,               -- 1..8, the disc colour on GroupCard
    currency_code      char(3)     NOT NULL,               -- ISO-4217; one per trip
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    starts_on          date,
    ends_on            date,
    created_at         timestamptz NOT NULL DEFAULT now(),
    archived_at        timestamptz,

    CONSTRAINT trips_hue_range CHECK (hue BETWEEN 1 AND 8),
    CONSTRAINT trips_dates_ordered CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

-- user_id stays NULL until the member claims their name through the share link (§4, claim flow).
-- Friends who never sign in still work — the payer ticks them off directly.
CREATE TABLE trip_members (
    id           uuid        PRIMARY KEY,
    trip_id      uuid        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    display_name text        NOT NULL,
    person_hue   smallint    NOT NULL,                     -- 1..8, round-robin, never changes
    user_id      uuid        REFERENCES users (id),
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT trip_members_name_unique UNIQUE (trip_id, display_name),
    CONSTRAINT trip_members_hue_range CHECK (person_hue BETWEEN 1 AND 8)
);

-- trip_id NULL = one of the eight built-ins, shared by every trip. Non-null = added by a member
-- of that trip and scoped to it.
CREATE TABLE categories (
    id         uuid     PRIMARY KEY,
    trip_id    uuid     REFERENCES trips (id) ON DELETE CASCADE,
    key        text     NOT NULL,
    name_en    text     NOT NULL,
    name_zh    text     NOT NULL,
    icon       text     NOT NULL,                          -- a Lucide slug. No emoji, ever.
    hue        smallint NOT NULL,
    sort_order smallint NOT NULL,

    CONSTRAINT categories_hue_range CHECK (hue BETWEEN 1 AND 8)
);

-- Two partial indexes rather than one UNIQUE (trip_id, key): in Postgres NULLs do not collide,
-- so a plain unique constraint would let the built-in keys be inserted twice.
CREATE UNIQUE INDEX categories_builtin_key_unique ON categories (key) WHERE trip_id IS NULL;
CREATE UNIQUE INDEX categories_custom_key_unique ON categories (trip_id, key) WHERE trip_id IS NOT NULL;

CREATE TABLE items (
    id                 uuid        PRIMARY KEY,
    trip_id            uuid        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    title              text        NOT NULL,
    category_id        uuid        NOT NULL REFERENCES categories (id),
    amount_minor       bigint      NOT NULL,
    split_rule         text        NOT NULL,
    payer_member_id    uuid        NOT NULL REFERENCES trip_members (id),
    spent_on           date        NOT NULL,
    note               text,
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT items_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT items_split_rule_known CHECK (split_rule IN ('EQUAL', 'WEIGHTED', 'EXACT'))
);

CREATE INDEX items_trip_idx ON items (trip_id);

-- The people list, and the whole point of the design: editing these rows is what fixes the hotel
-- case. Which of weight / exact_amount_minor is populated depends on the parent item's
-- split_rule, which is a cross-table rule and so lives in the service, not in a CHECK here.
CREATE TABLE item_shares (
    item_id            uuid   NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    member_id          uuid   NOT NULL REFERENCES trip_members (id),
    weight             int,
    exact_amount_minor bigint,

    PRIMARY KEY (item_id, member_id),
    CONSTRAINT item_shares_weight_positive CHECK (weight IS NULL OR weight > 0),
    CONSTRAINT item_shares_exact_non_negative CHECK (exact_amount_minor IS NULL OR exact_amount_minor >= 0)
);

-- item_id NULL = a trip-level settlement from the Settle-up screen (§7a). Same table, same state
-- machine, same approval rule — which is what stops an item repayment and a trip-level settlement
-- between the same two people from both subtracting.
--
-- Deleting an item deletes its payback claims with it, matching the bin button on
-- ExpenseDetailSheet. Trip-level settlements have no item and so survive.
CREATE TABLE paybacks (
    id                  uuid        PRIMARY KEY,
    trip_id             uuid        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    item_id             uuid        REFERENCES items (id) ON DELETE CASCADE,
    from_member_id      uuid        NOT NULL REFERENCES trip_members (id),
    to_member_id        uuid        NOT NULL REFERENCES trip_members (id),
    amount_minor        bigint      NOT NULL,
    paid_on             date        NOT NULL,
    proof_object_name   text,
    note                text,
    status              text        NOT NULL,
    created_by_user_id  uuid        NOT NULL REFERENCES users (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    reviewed_by_user_id uuid        REFERENCES users (id),
    reviewed_at         timestamptz,
    reject_reason       text,

    CONSTRAINT paybacks_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT paybacks_not_self CHECK (from_member_id <> to_member_id),
    CONSTRAINT paybacks_status_known CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT paybacks_rejection_has_reason CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);

CREATE INDEX paybacks_trip_idx ON paybacks (trip_id);
CREATE INDEX paybacks_item_idx ON paybacks (item_id) WHERE item_id IS NOT NULL;

-- The eight built-ins, matching Tally's canonical Lucide glyphs (§5). A trip member can add more,
-- scoped to their trip.
INSERT INTO categories (id, trip_id, key, name_en, name_zh, icon, hue, sort_order) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'food',      'Food',      '餐饮', 'utensils',         1, 1),
    ('00000000-0000-0000-0000-000000000002', NULL, 'drinks',    'Drinks',    '酒水', 'beer',             2, 2),
    ('00000000-0000-0000-0000-000000000003', NULL, 'transport', 'Transport', '交通', 'car-front',        3, 3),
    ('00000000-0000-0000-0000-000000000004', NULL, 'stay',      'Stay',      '住宿', 'bed-double',       4, 4),
    ('00000000-0000-0000-0000-000000000005', NULL, 'groceries', 'Groceries', '超市', 'shopping-basket',  5, 5),
    ('00000000-0000-0000-0000-000000000006', NULL, 'fun',       'Fun',       '娱乐', 'ticket',           6, 6),
    ('00000000-0000-0000-0000-000000000007', NULL, 'home',      'Home',      '居家', 'house',            7, 7),
    ('00000000-0000-0000-0000-000000000008', NULL, 'other',     'Other',     '其他', 'circle-dashed',    8, 8);

-- Spring Session JDBC, copied verbatim from schema-postgresql.sql in spring-session-jdbc 4.1.0.
-- It lives here rather than being auto-created because Flyway owns every table in this database;
-- spring.session.jdbc.initialize-schema is set to `never` for the same reason. If Spring Session
-- is upgraded, diff that file against this block.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
