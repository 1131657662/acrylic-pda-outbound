# E03 Web supervisory rescue consumer (2026-10-12)

Input: D:/acrylic-pda-outbound, codex/development,
`09e4da04526b7023f37c5007c4df3319946f9f3a`. Version facts: dashboard only.
Single writer; inherited dirty changes and prior failure evidence protected.

## Decision and exit checks

Use the existing rescue read/approve/revoke routes and existing Web Session,
CSRF and warehouse permission. Supervisor opens the original-person-provided
request ID, reviews original device/person/rescuer/order scope and reason.
Approval explicitly selects a finite ISO timestamp or permanent original-only
read. Default finite mode requires an entered timestamp; permanent selection
is explicit and separately described. Original operator cannot self-approve.
Revocation retains original signed authority/material and works for a disabled
original device according to the service contract.

Persist owner/request/action/key/epoch/exact body before POST, use browser locks,
and reject failed storage before sending. Reload restores the original intent
with inputs locked; response loss retries exact bytes/key rather than reissuing
authority. Known original receipt cannot be replaced. Read results and download
are validated against original scope; downloading signed material does not
provision access or approve vehicle release. All actual actions remain explicit
human product controls; isolated browser tests use labelled synthetic actors.

A by-ID page avoids an unnecessary new search/list API. The typed ID locates a
request; server permission and warehouse checks remain authoritative. No PDA
Bearer is copied. Session/warehouse changes clear in-memory materials, and
owner checks before POST/after asynchronous responses stop stale consumers.

Exit: strict schema/local persistence negatives; Web type/build/contract checks;
actual browser+HTTP lost-response/reload/exact retry for approval/revocation,
storage failure no POST, permission/warehouse and self-approval boundary, exact
downloaded signatures and one server grant/revocation; original tables unchanged.
APK/native signature trust consumers remain the preceding accepted slices.

Self-review decision: reject a past finite timestamp before the first save.
An original intent that receives the authoritative `RESCUE_EXPIRY_INVALID`
response may be explicitly archived, with readback, before another key is
prepared. Unknown/permission/network failures never release that intent.
Original success receipts and revocation grant IDs are immutable; a revoke
POST must match the currently read request and original authorization.
The finite time check is a usability check; service time remains authoritative.
Inherited native fixture output is preserved in `e03-web-native-before-01`.

Unit runner01 failed before tests because Node strip-only mode cannot load
the existing API constructor parameter properties. Different runner failure,
count1; original log retained. Transform-types mode02 runs the actual modules:
21 tests pass, no failure/skip. No application change for that runner issue.
Typecheck01/02/03, build01 and contract01 pass; browser/final regression pending.
Fresh1379 native prepare/new-process POST passed. Browser fixture1379 failed
before tests on an assumed `warehouse.created_at` column (actual schema has
none), fingerprint count1; no browser approval took place. Actual schema read
provides the new fix. Database1379 dumped/hashed and authenticated shutdown;
business checksums unchanged. Preserve failed seed/log/native bytes. Fresh1380
will repeat with a newly signed native request; never reuse the failed DB.
1380 verify01: type/build/21-unit/contract pass with unchanged source; browser
2 pass/2 fail, no skips. Failures are two distinct fixture fingerprints, each1:
Element Plus radio's decorative span intercepts a click on its hidden native
input; async Web Lock archive assertion ran before the resulting UI settled.
Use the real visible radio label and await the fresh-approval UI before checking
the archive. No application change for those failures. Logs/context/screenshots
and original native request retained; fresh1381 will rerun the fixed fixture.

## Accepted slice and evidence

Fresh1381 uses the unchanged accepted request APK and original registered
emulator Keystore. Native prepare (web05) and new-process POST (web06) each
pass without reseeding scans or overwriting previous encrypted journal entries.
Fixture adds explicitly synthetic self-supervisor, unprivileged operator and
second-warehouse memberships; this does not assert production authorization.

`e03-web-1381/verify-01`: type/build, 21 unit tests, OpenAPI validation and four
real Chrome/HTTP product tests pass, zero failures/skips, source-before/after
identical. Covers stored intent before network; actual server expiry rejection
and explicit archive/re-entry; approval/revoke successful server response lost
with `fetch` then `abort`; three reloads and exact key/body/epoch retry; corrupt
revoke grant prevents POST; storage failure zero POST; past input zero POST;
self-approval, cross-warehouse and unprivileged product/API denial. The page
busy state clears and enables subsequent read/download. No Browser/API mocks
substitute for successful supervisor POSTs. Downloaded JSON retains original
native request, authorization and revocation; terminal authority is not replaced.
Each final browser case also preserves its scoped command storage separately.

Independent Node consumer03 verifies the real download against package-external
device registration and fixed server public key: native ECDSA domain-separated
request plus RS256 authorization/revocation, exact person/device/warehouse/key/
order scope, permanent read-only capabilities, original authority hash and
matching terminal revoke. Consumers01/02 failed on two distinct fixture
assumptions (missing request domain prefix; assumed ES256 instead of configured
RS256), count1 each. Original logs retained; read actual protocols before fixing.
Read-only SQL01 queried the wrong idempotency table name; retained, count1;
corrected SQL02 shows one request/scope/grant/revocation and one durable key
for each request/approve/revoke. These failed inspection tools did not mutate
the successful DB or count as product passes.

`e03-web-1381/verify-01/revoked-page.png` was independently rendered and visually
checked: original scope/status/download and read-only limitations visible.
This is ordinary desktop-width coverage, not U04 full layout acceptance.
Instances1379/1380/1381 preserve dumps/hash/binlog, business-table checksums
unchanged and authenticated shutdown successful; task19330/19434/5173 stopped.
Raw logs/XML/context/screenshots and secrets stay in D:/acrylic-pda-outbound/
`.local/goal-20261011`, outside Git. No prior evidence deleted.

Web supervisor slice accepted; FORMAL/scale/file-fault O02 and E04–E09/remaining
D/O/U still required. No final integration candidate or production deployment;
goal remains active. Default gates, cancelled handover scope and
`localDeletionAllowed=false` remain. Next: native formal offline product,
consistent synthetic scale and file/process interruption, then E04.
