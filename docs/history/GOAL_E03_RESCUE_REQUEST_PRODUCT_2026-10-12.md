# E03 original-person durable rescue request (2026-10-12)

Input: D:/acrylic-pda-outbound, codex/development,
`4c839324161bd306df0239ed9aed0b77d50cd55f`. Version facts: dashboard only.
Existing inherited changes are protected; this chat is the only writer.

## Decision and exit checks before implementation

P01 product consumer uses the existing signed request protocol. Select one
original order owned by the authenticated person/device/warehouse. Designate
the warehouse-provided, already authorized rescuer ID; the server independently
checks that person's dedicated permission. A typed ID only proposes a request,
it never authenticates the original operator or grants rescue access.

Persist the exact original text, native device signature, original request key,
device-key binding and recovery epoch before any HTTP POST. A separate encrypted
no-backup journal survives process restart/logout without retaining a login
token or password. Retry the original bytes/key; retain the original response.
One unresolved request per original order prevents a lost response from causing
a replacement request. Changed recipient/reason requires first resolving it.
Current session identity/key must match before every network call and after its
response. A different person cannot see/send another person's journal entries.

The page shows saved intent, server receipt and last fetched approval state
separately. It can refresh the original request and expose its ID/material for
supervisory handling. Last known approval is labelled as such, never presented
as knowledge of an unseen server revocation. It does not unlock rescue reads,
scan, submit, release, change order state or delete records.

Rejected alternatives: an in-memory key loses recovery after death; creating a
new key on retry breaks idempotency; letting pasted identity authorize reads
breaks P01. A background automatic grant workflow would replace human approval
and is unnecessary. Default server gate stays closed.

Exit: persistent original-key/body/signature and scoped journal tests, write
failure prevents network, wrong receipt/material rejects without replacing
originals, identity/key changes reject, lost response/reopen retry reuses exact
bytes. Actual product page submits on original emulator Keystore to isolated
HTTP, process restart retrieves the same receipt and last-known material.
APK/lint and affected Android regression; original failure evidence retained.
Web supervisory page and FORMAL/scale/file-fault O02 combinations are separate.

Status: this original-person request consumer is verified; complete O02 and
the overall goal remain open.

## Implementation and actual verification

Separate `RescueRequestDocument`, encrypted vault, store and repository preserve
native intent and receipt, with an actual non-exported request activity reached
from data preservation. The store binds source/order, actor, device key and
epoch; retries preserve exact bytes. Invalid receipt/digest/material, failed
storage and replacement of known grant/revocation reject. On leaving the page
its in-memory summaries are cleared; operation generations prevent an old
asynchronous result from repopulating a new identity's view.

- `e03-request-android-01/`: 57 directed tests, zero failures/errors/skips,
  source unchanged. Ten request protocol/journal tests plus own/supervisor,
  Room/file/format consumers. These use synthetic identities and EC keys.
- `e03-request-regression-01/` and `02/`: 101 tests each, zero failures/errors/
  skips, source unchanged; the second covers final lifecycle/identity fixes.
  Includes affected rescue/scan-adjustment/scan-engine/order-progress consumers.
- `e03-request-apk-01/`, `02/`, `03/`: app/test APK and lint pass on fixed
  inputs. Final `03/` retains app SHA256
  `6BBF0339251903DEDF2AED3D3958DC618B0F0D4645FBB8F71B83D55A4DBDD369`,
  test APK `3FD4CD245DBC96F902819B33E86341B2FECBA5022F3B3ECA43B4A79894D9B635`.
  Lint zero errors/321 warnings. Both installed on original emulator-5564
  without deleting its original Keystore/Room; no physical device operation.
- Fresh `e03-supervisor-device-1378/` restores successful synthetic 1366 input;
  no failed/original database reuse. The service uses the committed server
  JAR with an explicit isolated rescue flag. `e03-request-phase-02/` to `10/`
  each executes one native test: navigation, actual page save-before-send,
  new-process POST, new-process original receipt retry/query, signed approval
  material, another user's rejection, final-input POST/query and stopped-service
  failure preservation. All pass. The same original emulator device signs its
  actual request, rather than using a host synthetic signature.
- Database evidence proves zero requests after local preparation, then exactly
  one request and one scope row after multiple new-process retries. Web Session/
  CSRF host test consumer approves the same request; Android verifies and
  displays the actual original server signature. Approval does not unlock
  scanning/submission/deletion. All original business-table checksums unchanged.
- Dump/hash/authenticated shutdown pass, no task ports listening. Final source,
  command, native logs and private receipts remain in their evidence directories.
  The original isolated server input JAR is the preceding committed slice;
  no server behavior/contract/schema changed in this request-page slice.

## Failure, review and remaining work

`e03-request-phase-01/` native navigation passed, but evidence collection failed
because it expected a request directory before any request had been saved.
Fingerprint: optional directory missing treated as terminating native stderr,
one occurrence. Preserve original log and do not count that phase as delivery.
Fix the optional collector and require native test success independently;
fresh `02/` then passes. No inherited failure counter was reset.

Author self-review covered identity/key/session transitions, native signature,
durability before network, unchanged originals, exact receipt/scope/material,
terminal revocation and stale lifecycle output. Five distinct consumers and
negative identity/offline cases use the actual Android product. The HTTP
approval action remains a labelled host test consumer, not the outstanding
Web product page or physical supervisor approval.

Inherited paths remain protected. No new migration, original database, release
signing, main merge, tag/Release/deploy, deletion or production gate opening.
Next: Web supervisory read/approve/revoke with durable original commands,
then FORMAL/scale/file-fault O02 combinations. E04–E09 and the unclosed D/O/U
standards remain required; this scope is not total-goal completion.
