-- Two edges the review found, both fixed at the schema because that is where they are enforced.

-- Optimistic locking on an expense. Two edits made against the same starting state must not both
-- land: without a version column the last writer silently wins, and because a patch replaces the
-- whole people list wholesale, the loser's list would vanish with no error — the exact stale-number
-- failure the derive-on-read design exists to avoid. Hibernate manages the value; the default only
-- backfills the rows that already exist.
ALTER TABLE items
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Weight zero is a real input the engine, the browser's split port and the pinned split vectors all
-- honour: a person on the bill for the record who owes nothing on it (`[1, 0, 1] -> [50, 0, 50]`).
-- The old CHECK was the one layer refusing it. A negative weight is still nonsense and stays out.
ALTER TABLE item_shares
    DROP CONSTRAINT item_shares_weight_positive;
ALTER TABLE item_shares
    ADD CONSTRAINT item_shares_weight_non_negative CHECK (weight IS NULL OR weight >= 0);
