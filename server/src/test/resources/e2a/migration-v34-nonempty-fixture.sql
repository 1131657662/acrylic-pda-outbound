-- Prepared target-34 fixture only. Execute through the guarded runner after scheduling.
-- It intentionally uses synthetic IDs and must never be applied to an existing database.
SET SESSION lock_wait_timeout=10;
SET @e2a_fixture_note = 'target34-nonempty-pending-guarded-execution';
-- S1 fixture requirements: TEMP source rows for mapped, mapping_version=0, and ABORT;
-- one existing command receipt; one SOURCE_RESERVED bale commitment; private-file
-- bytes and original response bytes are snapshotted before target 35.
-- Concrete inserts belong to the target-34 seed implementation after W1 SHA review.
