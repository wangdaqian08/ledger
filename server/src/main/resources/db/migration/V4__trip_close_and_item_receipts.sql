-- Ending a trip (spec §3 · §5): the spending record closes while settling stays open, and the
-- moment recorded here is what starts the 14-day retention clock on receipt images.
--
-- V1 shipped `archived_at` as a sketch that nothing ever read or wrote — every deployed row
-- holds NULL — so the column is renamed to what the feature actually means rather than left
-- behind as a second, dead name for the same idea.
ALTER TABLE trips RENAME COLUMN archived_at TO closed_at;

-- One receipt image per expense: where the bytes live and who put them there, never the bytes
-- themselves. The composite foreign key follows the house rule — a receipt on trip A cannot
-- point at an expense on trip B. Deleting the expense cascades the row, and the transaction
-- doing it deletes the object (ReceiptService); fourteen days after a trip's closed_at, the
-- retention sweep deletes both.
CREATE TABLE item_receipts (
    trip_id             uuid        NOT NULL,
    item_id             uuid        PRIMARY KEY,
    object_name         text        NOT NULL,               -- tripId/itemId/version in the store
    content_type        text        NOT NULL,
    size_bytes          bigint      NOT NULL,
    uploaded_by_user_id uuid        NOT NULL REFERENCES users (id),
    uploaded_at         timestamptz NOT NULL DEFAULT now(),
    revision            bigint      NOT NULL DEFAULT 0,     -- optimistic lock: racing replaces
                                                            -- cannot both land (see ItemEntity)

    CONSTRAINT item_receipts_item_fk FOREIGN KEY (trip_id, item_id)
        REFERENCES items (trip_id, id) ON DELETE CASCADE,
    CONSTRAINT item_receipts_object_unique UNIQUE (object_name),
    CONSTRAINT item_receipts_size_positive CHECK (size_bytes > 0)
);

-- The retention sweep and the trip payload both read by trip; item lookups ride the primary key.
CREATE INDEX item_receipts_trip_idx ON item_receipts (trip_id);
