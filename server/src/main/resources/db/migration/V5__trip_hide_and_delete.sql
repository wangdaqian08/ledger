-- Putting a finished trip away, and getting rid of one (spec §3 · §5).
--
-- Two different acts, deliberately two columns rather than one status word. Hiding is a tidy-up:
-- the trip is off every member's home list and otherwise completely normal — it opens by link,
-- settling and approvals carry on, and its balance still counts in your overall position.
-- Deleting is destruction with a stay of execution: gone for everyone at once, restorable by the
-- creator for thirty days, then swept away for good. A single column could not hold both, and a
-- trip can genuinely be hidden *and* deleted — tidied away in July, deleted in August.
ALTER TABLE trips
    ADD COLUMN hidden_at  timestamptz,
    ADD COLUMN deleted_at timestamptz;

-- Hidden can only ever mean "finished and put away". Without this, a live trip could vanish from
-- twelve people's home screens while they were still adding expenses to it, and the only clue
-- would be that their money moved somewhere they could no longer see.
ALTER TABLE trips
    ADD CONSTRAINT trips_hidden_only_when_closed
        CHECK (hidden_at IS NULL OR closed_at IS NOT NULL);

-- The purge sweep's work list: partial, because the rows it wants are the rare ones. Every other
-- read filters `deleted_at IS NULL`, which Postgres answers from the table for lists this small.
CREATE INDEX trips_deleted_idx ON trips (deleted_at) WHERE deleted_at IS NOT NULL;
