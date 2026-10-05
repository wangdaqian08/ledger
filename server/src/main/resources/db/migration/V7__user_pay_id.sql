-- A PayID per person (spec §5): what the "By minimum transfer" list shows beside each recipient so
-- the person paying can copy it into their banking app. The app never sends money to it and never
-- checks its format — it is text, shown exactly as entered.
--
-- On `users`, not `trip_members`. One person has one PayID whichever trip they are paying on, so
-- storing it per seat would ask them to type it again on every trip and let two copies drift
-- apart. A placeholder seat nobody has claimed has no account, so it cannot have one; and only the
-- account's owner may write it (PUT /api/me/pay-id takes no user id), which a per-seat column
-- would have blurred into "the trip creator edits the roster".
--
-- pay_id_updated_at is when it last actually changed — never touched by sign-in, never by saving
-- the same value again. Under the `name-signin` profile anybody can sign in as anybody, so an
-- impersonator could swap a PayID; other members see an "updated recently" badge for a week after
-- a change, and this column is what that badge reads.
ALTER TABLE users
    ADD COLUMN pay_id            text,
    ADD COLUMN pay_id_updated_at timestamptz;

-- The service trims and turns blank into NULL before writing, so an empty or whitespace-only
-- PayID never reaches here; this is the backstop, with the same 256 the API refuses beyond.
ALTER TABLE users
    ADD CONSTRAINT users_pay_id_length
        CHECK (pay_id IS NULL OR char_length(pay_id) BETWEEN 1 AND 256);
