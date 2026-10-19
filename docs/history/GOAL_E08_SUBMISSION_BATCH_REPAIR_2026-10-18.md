# E08 Submission Batch Repair

Date: 2026-10-18

## Scope

The recovered-confirmation/TEMP handover confirmation WIP remains cancelled and was not restored. This slice addresses only the active formal submission chunk path that stalled during the real TCP/P5 candidate.

## Diagnosis

Candidate 716 completed upload, missing-chunk rejection, reverse-order upload, duplicate-chunk idempotency and changed-chunk rejection, but the final request timed out. Candidate 718 and 719 reproduced the same timeout after the first batched lookup change. Their original logs, chunk bodies/responses, XML, datadir and authenticated shutdown evidence remain under `.local/e2a-cross-20260925-716/`, `718/` and `719/`.

The slow path was the final transaction's per-row database work under the P5 JDBC effect guard. Audit facts and submitted captures performed repeated single-row lookups, and each `batchUpdate` still executed one statement per row. The 1100 acknowledged-failure input therefore caused thousands of guarded JDBC executions.

## Change

- `server/src/main/java/com/acrylic/outbound/audit/PdaAuditFacts.java`: added bounded bulk verification. ID checks use indexed `IN` batches; origin-device sequence checks are grouped by device and use indexed sequence `IN` batches. Single-row callers retain the same conflict code and message through `verifyAll`.
- `server/src/main/java/com/acrylic/outbound/submissions/SubmissionEvents.java`: bulk-loads capture facts and event IDs, preserves ID idempotency and device-sequence conflict behavior, and writes captures/events as 200-row multi-value inserts inside the existing transaction.
- `tools/e2a-cross-source-verify.ps1`: added the `-SubmissionChunks` selector so the existing isolated provisioner can run the real chunk transport test directly; the default selector is unchanged.
- Temporary `E2A_DEBUG_FAILURE_SQL` logging was removed from `RecoveryFenceAuthority`; inherited retirement behavior remains untouched.

## Evidence

- Input snapshots before repair edits: `.local/goal-20261018-resume/repair-input01/` through `repair-input06/`.
- Candidate 720: `.local/e2a-cross-20260925-720/`; `test-completed.marker`, `integration-result.xml`, 14 chunk request/response pairs, final status 200, `captureHighWatermark=1102`, test exit 0, authenticated MySQL shutdown and no listener.
- Candidate 721: `.local/e2a-cross-20260925-721/`; 14/14 integration tests, 0 failures, 0 errors, 0 skipped, authenticated MySQL shutdown and no listener.
- Failed candidates are retained and are not reused: 716, 718 and 719.

## Verification

`gradlew.bat -p server compileJava compileTestJava --no-daemon` passed. The 721 run covered concurrent identical submissions, abort/commit terminal outcome, chunk retry and incomplete finalize, multi-megabyte 1100-failure evidence, competing orders, temporary conflict/rollback and idempotency cases. `git diff --check` and `tools/version_facts.ps1 -Check` passed.

This is a local server submission-path repair and validation only. D17 overall, E06 L2, D18 L3, the unified final candidate and the section 8 gates remain open; no release, deployment, main merge, production decision capability or `localDeletionAllowed` policy changed.
