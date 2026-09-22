# E2a W2 Migration Acceptance Stages

This is a preparation record only. It does not create a database, start port
19329, run Flyway, or claim V35 behavior has passed.

## Static stage S0

Consume the exact W1 V35 SHA and inspect the rendered DDL. Verify the V34
parent keys, immutable triggers, byte limits, natural-key uniqueness, and the
operation reference matrix:

| Operation | object | set | verification |
| --- | --- | --- | --- |
| OPEN | NULL | NULL | NULL |
| PAGE | present | NULL | NULL |
| STOP | present | NULL | NULL |
| VERIFY | NULL | present | present |

Also verify that `PAGE` and `STOP` object kind, manifest identity, and
PAGE-before-STOP-before-VERIFY are writer checks. The DDL cannot constrain an
object's `kind` through the existing composite foreign key.

## Non-empty V34 stage S1

Use a new, isolated V34 target-34 database and a synthetic non-empty fixture
containing TEMP sources with the existing S/round, mapping version 0 and a
mapped source, an ABORT source, one command receipt, and a `SOURCE_RESERVED`
representative. Capture before-migration rows by primary key, all nullable
source columns, canonical/original response bytes and SHA-256, plus referenced
private-file hashes. Keep the fixture and snapshot immutable; do not reuse an
old seed or delete a failed database.

## V35 negative stage S2

After S1 snapshots are complete and the exact W1 SHA is approved, apply only
target V35 in the isolated namespace. Each case runs in its own transaction and
records the database error and post-rollback row count:

- partial object/set/verification reference combinations;
- PAGE, STOP, and VERIFY reference-shape mismatches;
- cross-scope, cross-submission, and cross-round composite references;
- duplicate natural keys with changed request bytes;
- UPDATE and DELETE attempts against immutable receipts;
- invalid route, status, operation, digest, and over-limit bytes.

Natural-key same-byte replay, original-response replay, file durability and
`currentEffect` are writer/transaction acceptance and must remain separate;
SQL uniqueness alone is not evidence for those behaviors.

## Preserve and stop stage S4

Compare every S1 row and file hash after migration, including the original
receipt bytes and NULL semantics. Record the guarded datadir, storage and
signing paths, migration version, and a normal 19329 shutdown with zero
remaining listeners. Keep the database, logs, private files, key material and
all failed negative evidence. Do not touch 13316 or 19321-19328.

## Current boundary

S0 can begin only after W1 provides the exact V35 SHA. S1-S4 are pending
coordination and environment approval. No dynamic result in this file is a
pass claim.
