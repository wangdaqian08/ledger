-- Optimistic-lock versions for two operations that already assume one.
--
-- restore() clears deleted_at and flushes, catching an optimistic failure to answer 404 when the
-- nightly sweep destroyed the row first. Without a version column that UPDATE hits zero rows
-- silently and restore falsely reports success — the trip is gone but the creator is told it is
-- back.
--
-- claim() sets a member's user_id. When two *different* people open the same free seat through one
-- share link at the same moment, both pass the "is it claimed?" read and both writes land: the
-- second silently steals the seat while the first is told they joined. The unique index on
-- (trip_id, user_id) stops one person taking two seats; it cannot stop two people overwriting one.
--
-- A version column on each table turns both races into the 409/404 the code already intends.

ALTER TABLE trips
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE trip_members
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
