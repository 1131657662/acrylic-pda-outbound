# E03 Android supervisor read-only rescue (2026-10-12)

Input: `D:/acrylic-pda-outbound`, `codex/development`,
`2e1e8532f642ce7d7134c378386c363c459f177b`. Version facts: see the generated
dashboard block. All inherited paths/bytes, remote refs, processes, listeners
and disk space are preserved in `.local/goal-20261011/e03-supervisor-android-baseline-01/`.
This chat is the sole source writer. No original database, production device,
deployment, release signing or cancelled device-continuation flow is in scope.

## Decision and acceptance before implementation

P01 consumer choice: keep supervisor credentials in a separate authenticated,
encrypted vault. Provision after online authentication of the delegated rescuer
on the original registered device. The trust root comes from that authenticated
session, never from the pasted material. Bind rescuer, original operator,
warehouse, original device/key and exact nonempty order scope. A separate local
password verifier with durable attempt limits permits later read-only recovery
after logout. Retain the independently provisioned verification key and time
anchor so logout does not destroy historical verification material.

Alternatives rejected: depending on a fresh login for each offline read defeats
P01; accepting a key or a typed operator name from the imported file would let
the file assign itself authority. Persisting a login token is unnecessary.

Signed revocation import requires no live session because it only removes
rights. Verify exact claims, original authorization hash, grant and complete
identity before committing; retain the original authorization and revocation.
Local revocation is also terminal for that grant. Reimport cannot resurrect it.
Every snapshot, generation, package recovery and target copy rechecks the grant.
Finite grants use the provisioned trusted time anchor and fail closed on reboot,
clock drift or expiry; explicitly non-expiring grants remain original-only when
current time is unavailable. No scanning/submission/release/stock/deletion right.

Exit checks: strict protocol negatives; durable password/revocation/reopen and
failure-write behavior; own-credential regression; scoped Room/file consumers;
actual UI/import/export and restart on an isolated emulator, APK/lint, and an
independent PC read of the emitted package. Synthetic signatures/identities and
emulator evidence are labelled; they do not establish original-person/physical
PDA or production approval. Server request/Web provisioning and remaining O02
format/scale/fault combinations are separate outstanding consumers.

Status: this scoped Android consumer is verified; complete O02 remains open.

## Directed checks and review

- `e03-supervisor-android-01/`: 46 Android tests, zero failure/error/skip,
  source unchanged. Protocol, expiry, password attempt penalties, durable scope,
  revocation/reopen and existing own snapshot/file consumers.
- Review found that provisioning needed a live specialized permission check,
  rather than only a generic logged-in device check. Added the default-closed
  `/pda/rescue-grants/{id}` read route: designated rescuer, original device,
  warehouse, registered key and `rescue:read` are rechecked. PDA login remains a
  separately assigned role permission; rescue rights do not confer login or scan.
  Android compares the returned original signature, rejects expired/revoked
  authority and imports a known signed revocation before stopping.
- `e03-supervisor-android-02/`: 46 tests, zero failure/error/skip, source unchanged
  after strict JSON verification and the online consumer change.
- `e03-server-mysql-1373/`: eight tests, zero failure/error/skip, source unchanged.
  Seven real HTTP checks cover exact materials, original operator/other device
  rejection, dynamic recipient permission removal, original-key concurrency,
  disabled-device revocation and final SQL rollback; one MockMvc/MySQL default
  check includes the new route. Fresh isolated instance, dump/hash and
  authenticated shutdown with no task listeners. No schema migration added.
- `e03-supervisor-regression-01/`: 91 Android tests, zero failure/error/skip,
  source unchanged. Includes existing own rescue, format/Room/files, scan
  adjustment, order progress and scanning engines. Strict signed duplicate
  fields/trailing documents/types and oversize scope are also exercised.

- `e03-supervisor-apk-01/`: debug app/test APK, lint and source hashes pass.
  Lint has zero errors and 302 existing warnings. App SHA256
  `2BEC8A3EB19F547159AB5AD0F11C9EF56852047EDAAAF98E0EBB690C34198825`,
  test APK `F66C604FD0FF121AE19185A094873884757DD6522230D37A5CE833A502635591`.
  Both installed successfully on emulator-5564, whose original Keystore/Room
  data were retained. No physical device was used.
- `e03-supervisor-server-unit-01/`: ordinary `test bootJar` runs 4315 tests,
  zero failure/error, one existing Windows symbolic-link skip, source unchanged.
  These ordinary tests exclude integration tests.
- `e03-supervisor-device-1377/`, `e03-supervisor-phase-01/` through `05/`:
  five separate native instrumentation processes pass. Original emulator key
  signs the request, the server preserves its original-key retry, Web Session
  and CSRF approval creates a scoped grant, and the designated rescuer uses
  the actual import dialog before logout. After service shutdown a new process
  unlocks only the authorized original TEMP order and emits its encrypted
  frozen package. Offline signed revocation import and another new process
  retain the denial. Server business-table checksums remain unchanged.
  Database dump/hash and authenticated shutdown pass; task ports are empty.
- `e03-supervisor-pc-01/`: binary retrieval SHA256
  `FCB691B16A8BF4864B7CB3FE29693F411FD291BE41CD2A4473E415ED5F09F0E4`
  matches the native export receipt. The existing PC verifier uses the
  independently preserved registration material from `e03-device-provision-01`,
  verifies source/signature/frozen bytes, preserves the original operator rather
  than attributing scans to the rescuer, and converts with full independent
  cell/type checks. Five saved workbook previews were visually inspected.
  Three original scans include one VOID and two ACTIVE, total 601500 grams;
  all fifteen audit events remain. Business receipt verification is separately
  false; package verification does not approve or release an order.

## Environment failures preserved

The source dump is the successful synthetic 1366 input, restored into fresh
isolated targets. Failed targets are not reused. Each distinct fingerprint is
count one, not a reset of any inherited UI/product failure:

- 1374: application-user restore could not recreate the historical trigger
  DEFINER without administrative privilege. Preserved dump/hash/datadir/binlog.
  Fix: validate isolated datadir/schema and restore with its isolated admin;
  create the original definer as a locked account limited to the new schema.
- 1375: PowerShell's automatic `$input` shadowed the fixture variable, breaking
  the input push. Preserved failed database/logs; renamed to `$rescueInput`.
- 1376: adb copied the input to `/data/local/tmp` but Windows PowerShell treated
  successful stderr progress as a terminating native error before the private
  copy/service start. Preserved original files, dump/hash and authenticated
  shutdown; business checksums unchanged, task ports empty. Fix: capture stderr
  without terminating on progress and separately require the native exit code.
  Fresh 1377 then completes all phases. No original or production database used.

## Review, scope and continuation

Author self-review covered trust provenance, strict JSON, exact source/order
scope, persistent attempt limits, reboot/time/expiry, identity/permission
rechecks and terminal revocation. PC tools are independent consumers, not an
independent person review. All product source and relevant inherited protocol
edits are preserved in `e03-supervisor-review-checkpoint-01/` with full dirty
bytes/hashes/diffs, remote, processes, listeners and C/D free bytes.

Native Android request setup in the device test is a test consumer, not the
missing durable original-person request product page. Web supervisory product
approval/revocation, that page and the remaining O02 combinations remain open.
Next: implement the original-person durable request product page, then the
Web approval/revocation consumer and FORMAL/scale/file-fault combinations.
E04–E09, D04/D05/D08/D09/D11–D18, O03–O05 and deferred U01/U02 remain open;
the full goal remains active. No main merge, production gate, signing,
deployment or evidence deletion. Version facts remain in the dashboard only.
