-- Three gaps V1 left open, found in review. V1 has already run everywhere and is not edited.

-- One person holds one slot per trip. The service checks this before writing, but a check is not
-- a guarantee: two claim requests racing (the same invite link open on two devices) both read
-- "no slot yet" and both write. Spec §4 calls holding two slots the failure that matters — the
-- same person owing and being owed as two different people while the balances still sum to zero.
-- Partial, because unclaimed members all carry NULL and NULLs must not collide.
CREATE UNIQUE INDEX trip_members_user_trip_unique
    ON trip_members (trip_id, user_id) WHERE user_id IS NOT NULL;

-- An item's people list has an order, and until now nothing stored it. The engine breaks
-- remainder ties by position, so "who carries the spare cent" was riding on the order Postgres
-- happened to return rows in — stable for a small append-only heap in practice, but a plan
-- change (such as the index below) or a rewritten page could move a cent between two people with
-- no write in between. Derived numbers must be a function of the data alone. The position is the
-- index in the people list as the client sent it, which is also what makes the SplitBar preview
-- and the saved split agree.
ALTER TABLE item_shares ADD COLUMN position smallint;
UPDATE item_shares SET position = numbered.rn
FROM (
    SELECT item_id, member_id, row_number() OVER (PARTITION BY item_id ORDER BY member_id) - 1 AS rn
    FROM item_shares
) AS numbered
WHERE item_shares.item_id = numbered.item_id AND item_shares.member_id = numbered.member_id;
ALTER TABLE item_shares ALTER COLUMN position SET NOT NULL;
ALTER TABLE item_shares ADD CONSTRAINT item_shares_position_unique UNIQUE (item_id, position);

-- Loading a trip reads its shares by trip_id — the app's hottest query — and no index led with
-- it: the primary key starts at item_id and item_shares_member_idx at member_id, so the read was
-- a sequential scan over every trip's shares. items, trip_members and paybacks all had theirs.
CREATE INDEX item_shares_trip_idx ON item_shares (trip_id);
