# E2a W2 Migration Acceptance Stages

2026-09-24 signed archive and PAGE audit-coverage increment (author,
uncommitted): `ARCHIVE/AUDIT_ARCHIVE` now reads original canonical proof bytes,
matches the archive's stable device/epoch/afterSeq key and indexed server
proof, verifies the server RSA archive signature and original durable payload,
then compares every archived event with the server's FORMAL/TEMP audit row,
including original authorization and digest. A missing historical signing
key/file remains `ARCHIVE_EVIDENCE_UNAVAILABLE` (503), distinct from a claim
or stored-content conflict (409); no current-key fallback is trusted as a
historical keyring. The v1 page item key/digest/throughSeq convention is in
`docs/parallel/R06_E2A/INTERFACE_CARD.md`. The STOP/VERIFY read preflight
now refuses complete pages unless ordered AUDIT records plus verified archive
intervals cover each device sequence 1..signed `auditThroughSeq` exactly once.
Missing, overlapping, future, foreign or beyond-STOP intervals fail closed.
This coverage does not prove capture high-watermark or Android stop durability.

Focused archive, signed-file, PAGE entrypoint, gap/overlap and preflight tests
passed; after the final content change the full backend suite was 62 XML,
412 tests, 0 failures/errors, 1 Windows symlink skip. The earlier focused
run failed two preflight tests because its mocks still reported false audit
coverage; corrected mock facts passed. The first full run failed one synthetic
PAGE fixture without complete audit coverage; corrected fixture passed. A
compile check mistakenly used the standard `test` task with an `IT` filter;
compilation succeeded but Gradle found no IT there, so it is not an IT pass.

Fresh `.local/e2a-v36-audit-attempt6` ran the opt-in archive IT once on a
nonempty V34 -> V35/V36 isolated database (1 test, 0 failures/errors/skips;
XML SHA-256 `FD1D7E645A6504C7F91580CED9C6FCFD3AF2B77E7D578D700E4C69A1D1A17CB6`).
Its generated RSA signature and durable archive file were checked through
the real reader against one persisted archive index and two same-device
FORMAL/TEMP event rows; a substituted proof was rejected. Post-test SQL:
36 successful Flyway migrations/0 failed, 2 audit rows (seq 1..2), 1 archive
row (0..2). Dedicated 19330 PID 25880 was identity checked and authenticated
shut down; shared 3306/PID 5448 remained untouched. The one-shot datadir,
private keys, markers and logs remain retained. This fixture is synthetic;
it does not cover old rotated keys, full STOP/PAGE/VERIFY combination, true
Android capture watermark, HTTP or device paths, or production V35 binlog
trigger privileges. No trusted set, decision, release or deletion was enabled.

2026-09-24 audit raw item, original authority and signed STOP endpoint
increment (author, uncommitted): PAGE closure now re-reads each `AUDIT /
AUDIT_RECORDS` raw object and checks its original canonical bytes, byte length,
digest, ID and device sequence against the stored event, refusing claims beyond
the signed STOP audit cutoff. The device prefix checks each event's original
FORMAL assignment or TEMP authorization through `PdaAuditOrigins`, including
the stored source recovery epoch. The STOP/VERIFY read preflight additionally
requires the *last* event at the signed `auditThroughSeq` to be the named TEMP
`STOP` event for the original operator and authorization. Foreign ID/source,
operator, type, missing endpoint or a different cutoff fails closed. The
latest full backend suite (after endpoint changes) is 58 XML, 397 tests,
0 failures/errors, 1 Windows symlink skip; `git diff --check` has no errors.

The intermediate raw/authority candidate ran once in fresh isolated
`.local/e2a-v36-audit-attempt4` (1/1; XML SHA-256
`BCDCC0F165AC4E83892A0D5DF164DFA6A0D40BC7A2385071A793F4C001A177ED`).
The subsequent endpoint candidate ran once in a distinct empty-schema
`.local/e2a-v36-audit-attempt5` (1/1; XML SHA-256
`E501DE33F37196BA994C2142A956B7CA50230F3EFDFFDE73381200AFDBEA0769`).
Each nonempty V34 -> V35/V36 test had 36 successful Flyway rows, 0 failed
migrations and 2 same-device FORMAL/TEMP audit rows, including rollback checks.
The attempt4/5 dedicated 19330 PIDs 23908/8168 were identity checked and
authenticated-shut down; 19330 is no longer listening. Only these isolated
instances disabled binlog. Shared 3306/PID 5448 was untouched. Preserve all
one-shot datadirs, markers, private credentials and logs; neither attempt
can be rerun or equated to production migration authorization. The first
attempt4 SQL capture command had a nonexistent table and wrote no evidence;
the corrected SQL and XML were captured before shutdown. A preliminary
MySQL client path comparison also misread escaped batch output before the
one-shot test; raw-output identity and empty schema were checked before run.

This increment does **not** bind every PAGE AUDIT/ARCHIVE entry to the full
prefix, verify a signed archive object against its server archive index and
file, prove Android durable stop/high watermark or historical keyring, or
persist a trusted set/VERIFY/GET result. No independent consumer or full
service HTTP/Android test was run; no production decision/release/cleanup
capacity was opened. The earlier audit attempt1/2/3 and key attempt2
limitations and failures below remain unchanged.

2026-09-24 full-device audit-prefix increment (author, uncommitted): the
read-only STOP/VERIFY preflight now scans `pda_audit_event` in bounded pages
from device sequence 1 through the signed STOP `auditThroughSeq`. It rejects
gaps, order changes, foreign device/warehouse/operator/original source,
future recovery epoch and mismatched canonical payload digests, and checks
conflicting IDs/sequences across the existing `submitted_event` ledger.
FORMAL and TEMP share the one device sequence; `MAX(seq)`, a count or a
checkpoint is not used as proof. Five focused audit-prefix tests and the
preflight tests passed; the latest full backend suite is 57 XML, 391 tests,
0 failures/errors, 1 Windows symlink skip. `git diff --check` passed.

Fresh, separate `.local/e2a-v36-audit-attempt1` verified the earlier
prefix-only version in a nonempty V34 -> V35/V36 isolated database (1/1,
XML SHA-256 `682B2073251559A8FA997C41EF00E9A081BD942D21FB99B05BE320C9FC418F19`).
After self-review added the cross-ledger conflict check, a **new**
`.local/e2a-v36-audit-attempt2` ran the current candidate 1/1 with XML
SHA-256 `A2ACD7FCD3F3780DA2851D7C00A7E15AAD8FD58376B1EDF68BA73866FA3D605D`.
Post-test SQL: 36 successful Flyway rows, 0 failed migrations, 2 audit
events (1 FORMAL, 1 TEMP) after digest/gap mutations were rolled back.
Its dedicated 19330 PID 13824 was identity-checked and authenticated-shut
down; attempt1 PID 19076 was likewise stopped. Shared 3306 was untouched.
Both isolated profiles disabled binlog only for their local test instance;
neither proves production V35 trigger privileges. R09 was notified only
after the last full Gradle test ended and 19330 was confirmed unbound.

This proves the existing server audit rows' contiguous prefix and their
cross-ledger identity **only**. It does not independently bind every PAGE
AUDIT/ARCHIVE raw object or signed archive segment to that prefix, prove
STOP's own event at its endpoint, establish historical device keys or record
semantics/B-K facts, persist set/VERIFY, or test the complete preflight in
MySQL/HTTP/Android. It creates no trusted result, release or production
decision. The one-shot databases and original markers/logs remain retained.

2026-09-24 STOP writer/key provenance self-review (author, uncommitted):
the internal STOP service now parses the original signed request inside a
read transaction with the same server-selected `SourceEvidenceIntakeDeviceKeys`
source used by the verification preflight. It keeps the active device check
and compares that current registration to the selected key. The write
transaction reparses under its source/identity locks, so a changed key or
pairing between the first parse and the durable receipt fails closed. Focused
service/key tests and the full backend suite passed: 56 XML, 385 tests,
0 failures/errors, 1 Windows symlink skip; `git diff --check` passed.
The first focused run failed only because its old assertion expected
`STOP_SIGNATURE` instead of the new early `E2A_STOP_DEVICE_KEY_CHANGED`;
the assertion was corrected and an untrusted-key negative case added before
the passing focused and full runs. This is an author/unit result, not a new
JDBC/full-service, independent, HTTP, device or production acceptance. The
previous attempt2 failure and attempt3 isolated JDBC result below retain
their original scope; no database was started this increment.

2026-09-24 device-key source correction (author, uncommitted):
`SourceEvidenceIntakeDeviceKeys` now accepts a current P-256 device key from
the admin-controlled direct registration as well as a completed pairing whose
key and digest still match that registration. The read-only STOP preflight
selects this server-side key and reparses the original signature; it never
accepts a caller-selected key. Focused tests passed, and the latest full
backend suite has 56 XML files, 384 tests, 0 failures/errors and 1 Windows
symlink skip. A fresh isolated `.local/e2a-v36-key-attempt3` with binlog off
passed the one-shot `E2aV36DeviceKeyIT`: 1 test, 0 failures/errors/skips;
XML SHA-256 `8AFF8AA5432F93A7C0784C6B7F0A47ADAACEAA68BFC31800A82530ABB6979662`.
Post-test SQL: 36 successful Flyway rows, 1 synthetic completed pairing,
0 intake receipts, 0 failed migrations. Dedicated 19330 PID 2504 was
authenticated and gracefully shut down; shared 3306 was untouched.

Failed evidence retained: attempt2 first launch split the Windows MySQL
`basedir` argument and exited; its corrected launch then rejected root TCP,
so an isolated `init-file` created the dedicated account before any test.
The consumed attempt2 one-shot later failed V35 with MySQL error 1419 because
binlog was enabled for trigger creation; XML SHA-256
`82487D653132D8FCF9A089573FC9B119420CB2FF88DA6F741406F9764F69408A`.
It was not reset or rerun, and its dedicated PID 3884 was shut down. The
attempt3 profile, datadir, markers, private SQL and logs remain separately
preserved. Direct registration is an admin-controlled current-key source,
not a historical keyring. Rotated old keys, original audit continuity,
real approval and full stored-root preflight, VERIFY/set, B/K, HTTP and device
flows remain unverified or unimplemented; R06 is not complete.

2026-09-24 STOP provenance preflight increment (author, uncommitted): the
read-only gate now reloads the persisted OPEN, compares its exact canonical
bytes, and re-parses the original STOP request with a server-selected P-256
key instead of accepting a caller-fabricated parsed STOP or key object.
`SourceEvidenceIntakePairedKeys` requires a completed, approved pairing row
bound to the same warehouse/device and the still-identical registered
device public key/hash; unknown, corrupt, unpaired or rotated keys fail
closed. It intentionally is **not** a full historical keyring; pre-existing
directly registered devices and rotated/unknown old keys cannot pass this
gate until their trusted provenance is implemented or independently
established. No trusted VERIFY/set/decision is written, and there is no
production caller. Focused tests cover signature tamper, wrong/absent key,
OPEN replacement and foreign/malformed pairing. The full backend suite
passed: 56 XML, 382 tests/0 failures/0 errors/1 Windows symlink skip;
`git diff --check` passed. The new opt-in isolated key IT used a fresh
`.local/e2a-v36-key-attempt1` nonempty V34 -> V35/V36 fixture and inserted
a **synthetic** completed pairing after replacing the synthetic fixture
device key. It checked missing pairing rejection, exact registered key,
foreign warehouse rejection and rolled-back key-hash tampering: 1 test,
0 failures/errors/skips, XML SHA-256
`874095F4988C40EF981B7E526C31CEC2A48CEC40F80D9B168ECF2C8BD0BD0AFB`.
The first `compileTestJava` failed on AssertJ overload inference; explicit
byte-array locals fixed it before any test database access. Post-test SQL
had 36 Flyway rows, 1 pairing row and 0 intake receipts. One-shot markers,
datadir and private logs remain. Dedicated 19330 PID 14492/11668 was
authenticated and gracefully shut down, leaving 19330 unbound; shared
3306/PID 5448 was not operated. This proves the JDBC query/constraints for
the synthetic shape, **not** the production pairing approval flow, key
rotation/history, full preflight on MySQL, original audit semantics, HTTP
or Android. Earlier STOP/PAGE acceptance remains scoped to its own code.

2026-09-24 PAGE raw-object closure increment (author, uncommitted):
the read-only PAGE-root inspector now follows each staged item to its
original raw object within the same source binding, compares immutable
raw-object ID, SHA-256 and byte count to the page claim, and rehashes the
durable raw file. Previously matching PAGE/stage rows alone could survive
raw-object or file loss. Missing/mismatched original references and damaged
files now fail closed; two new negative tests cover disappearance after
staging. The focused tests and full backend suite passed: 55 XML,
373 tests/0 failures/0 errors/1 Windows symlink skip; `git diff --check`
passed. This checks byte integrity, **not** original record semantics,
FORMAL/TEMP sequence/audit completeness, historical key trust, or a
production VERIFY caller. No new DB, HTTP or Android result is claimed.

2026-09-24 read-only VERIFY preflight increment (author, uncommitted):
`SourceEvidenceIntakeVerifyPreflight` requires an already-held source-lock
transaction and an already parsed STOP. It binds the STOP original-key
replay to its exact receipt object reference, immutable STOP object and
hashed durable proof file, then requires the existing PAGE-root closure to
report zero missing pages. Cross-source context, broken STOP references,
missing proof files and missing pages fail closed. It returns no verified
status and creates no set/verification/decision. Its caller must still
establish a trusted historical device key, original STOP and audit/item
continuity before any later VERIFY writer can promote evidence; no such
writer/HTTP route exists. Focused tests first failed compilation because a
checked IOException was omitted from the test method, then passed after
the correction. Full backend suite: 55 XML, 371 tests/0 failures/0 errors,
1 Windows symlink skip; `git diff --check` passed. No independent consumer,
fresh MySQL run of this preflight, HTTP or Android verification is claimed.

2026-09-24 STOP one-shot JDBC writer acceptance (author, uncommitted): isolated
19330 attempt1 passed 1 opt-in integration test, 0 failures/errors/skips.
XML SHA-256 9E3125923B8CFABF18E3E156028EA5506272BEA09EA202762EC7032B9C30564C.
Nonempty V34 -> V35/V36; durable STOP file, object, signed PARTIAL original
response, same/different bytes replay, and post-object transaction rollback were
checked. JDBC then held 4 objects, 2 receipts, 36 Flyway rows. Durable
unreferenced rollback file was preserved. Markers/datadir remain; dedicated PID
17584/13100 shut down and 19330 is unbound. Shared 3306 was untouched.
The first test command failed without DB access because *IT is excluded from
the unit-test task; integrationTest passed. The first post-test SQL query used
a wrong receipt table name, corrected for the counts above. This checks an
internal JDBC writer, not the production STOP service, HTTP, historical keys,
set, VERIFY, Android or independent consumption.

2026-09-24 PAGE-root closure increment (author, uncommitted): added a
read-only `SourceEvidenceIntakePageClosure` under an existing transaction.
It rehashes the STOP manifest bytes, checks every stored PAGE file's
canonical bytes, page root, manifest/snapshot identity and predecessor root,
then compares staged item identities/counts and full manifest/stage totals.
Missing pages are returned explicitly; malformed or missing stored files,
wrong roots, broken links, extra/stale staged rows and wrong totals fail
closed. It does **not** trust the claim's raw records, audit continuity,
device STOP durability, historical key or bale/commitment facts; an empty
missing-page list is not `VERIFIED_COMPLETE_AT_STOP`. There is no production
VERIFY/set/GET caller yet. Nine new focused tests cover missing/complete,
nonempty staged entries, tampering and file errors. First focused run failed
four tests because the fixture stored unsorted JSON (the production PAGE
writer stores canonical bytes); after correcting the test fixture and
classifying malformed persisted JSON as an integrity error, focused tests
and the full backend suite passed: 54 XML, 363 tests/0 failures/0 errors/
1 Windows symlink skip. `git diff --check` passed. No new isolated DB,
HTTP, Android or independent consumer result is claimed.

2026-09-24 STOP durable-intake increment (author, uncommitted): internal
`SourceEvidenceIntakeStopService` now selects the current server-known P-256
device key, binds the original OPEN/source/epoch and requires current recovery
permission. It stores canonical STOP proof bytes before the short transaction,
then locks/rechecks source, permission, device key and original key before
inserting only an immutable `STOP_PROOF` object and signed `PARTIAL` intake
receipt. Natural-key collision rechecks after rollback; object-kind and
NULL set/verification are checked on replay. STOP request is capped at the
V35 canonical-request column limit (262144 bytes), and a disabled device
cannot provide a new trusted key. Repository, codec, parser and service
tests cover source and file binding, tamper, signed closed capabilities,
original-key replay, collision, permission rejection and wrong key; focused
STOP tests pass. `gradlew -p server test --no-daemon` produced 53 XML,
353 tests/0 failures/0 errors/1 Windows symlink skip, and `git diff --check`
passed. Earlier in this increment a test import failed compilation and the
first service test used a non-spy file dependency; both were fixed and
rerun. No fresh real-MySQL STOP service run or independent consumer exists.
No page-root closure, trusted historical keyring, set, VERIFY, B/K,
GET/currentEffect, HTTP, Android or final release is claimed. Previous
attempt1/2 failures and attempt3/4 evidence remain unchanged.

2026-09-24 STOP parser increment (author, uncommitted): the strict six-field
STOP request now binds a derived manifest/set pair and the signed P-256 STOP
core to a server-supplied original OPEN, warehouse/operator/device/epoch and
trusted public key. It returns cloned canonical evidence bytes only. Five
focused parser tests passed after a first test compilation failure (lambda
variable reuse) was corrected; the latest full server suite is 49 XML,
334 tests/0 failures/0 errors/1 Windows symlink skip after the following
compatibility self-review, with `git diff --check` passing. The STOP parser
initially required a UUID stopEventId beyond the existing non-empty string
protocol; that overrestriction was removed and a signed non-UUID event case
was added. Six focused STOP parser tests now pass. This is NOT keyring lookup,
STOP object/set insertion, page-root
verification against stored pages, durable Android stop, or VERIFY. No
`VERIFIED_COMPLETE_AT_STOP`/B-K/decision/release ability was added.

2026-09-24 follow-up real-file writer check (author, isolated attempt4):
fresh V34 fixture -> V35 -> corrected V36 then stages a canonical PAGE item
through `SourceEvidenceIntakePageItemWriter` against JDBC and a truly stored
raw file and page file. Its immutable index survives a same-page consistency
read. A different OPEN/page with the same source record key fails with
`E2A_PAGE_ITEM_DUPLICATE`, rolls back the new page object, and leaves the
durable (unreferenced) page file intact. Corrected V36 constraints still
pass. Authenticated shutdown/restart preserved Flyway 34/35/36, 2 stage rows,
4 intake receipts, 7 V34/new objects, and the OPEN discriminator FK, then
authenticated shutdown left 19330 unbound. One opt-in IT passed. XML SHA-256
`2A6B0C73440B909BEBFBFA0D1A5CFA7524937E2540BE1D056F4776C81B190790`,
identical before/after SQL SHA-256
`9DBBFD08B0F1777A60A9BC41F76EAA2860A3C60C6774BBCF434D7322E2EC9D22`;
evidence under `.local/e2a-v36-attempt4/private/`. The broader 328-test
server unit suite passed (0 failures/errors, 1 Windows symlink skip);
`git diff --check` passed. This is repository/writer integration, not the
authenticated PAGE service, original receipt replay, concurrent arrival,
HTTP or device acceptance. No production decision or release capability.

2026-09-24 V36 item-stage author run (uncommitted, isolated 19330):
attempt1 failed at the Gradle working-directory preflight before any DB
connection or marker; its XML/log/datadir remain in `.local/e2a-v36-attempt1/`
and the exact owned listener was authenticated-shut down. Fresh attempt2
successfully migrated a non-empty V34 seed through V35/V36 and checked
uniqueness, source FKs, triggers, rollback, old rows/file and restart
persistence. Self-review then found the V36 stage FK did not actually require
the referenced receipt to be OPEN. Its uncommitted DDL was corrected with a
constant OPEN discriminator in the composite FK; attempt2 remains historical
under its original migration checksum and is not a final V36 acceptance.

Fresh attempt3 migrated non-empty V34 -> V35 -> corrected V36, checked a
same-source PAGE receipt cannot impersonate OPEN, cross-source raw and missing
OPEN references fail, duplicate record/object keys fail, UPDATE/DELETE are
rejected, transaction rollback retains one committed stage row, and old
evidence rows/file remain. The corrected OPEN discriminator FK and successful
Flyway 34/35/36 entries survived authenticated shutdown/restart. One opt-in
IT passed, with no 19330 listener afterward. Evidence is under
`.local/e2a-v36-attempt3/private/`: XML SHA-256
`C8DFF97116868A72322ED0F4577C75E9627659C8FDE2FDF5C726B411AAB68D54`,
matching before/after restart SQL SHA-256
`8AB4BE57A24338DF99F37D07A06A0E89CA876D67A17B5B3818D6498F59CA5B9B`;
corrected V36 resource SHA-256
`D796A1109930E2E800769C2B30E0FDD61DAFE0F4BDB56164D95BFA383D33C9ED`.
These are author DDL/fixture tests only: stage fixture rows do not represent
real uploaded PAGE/raw files, no actual PAGE service/HTTP/Android consumer,
concurrency, crash fsync or independent audit is claimed. Earlier failures
and profiles were not reset or overwritten.

2026-09-23 subsequent independent evidence audit (read-only, no second
migration): attempt8 XML/before/after/negative SHA-256 values matched the
record below. The XML reports 1 test, 0 failures/errors/skips; all 28 cases
are present (4 accepted controls rolled back, 24 expected SQL rejections).
All existing table and schema snapshots, the four private file snapshots,
and the 34 pre-V35 Flyway entries matched before/after; only the new intake
receipt table and successful V35 history entry were added. This checks the
stored evidence, not a live independent database consumer or HTTP route.

The original ignored `docs/sku.txt` was copied byte-for-byte from the main
worktree into this worktree as a read-only local test input; both SHA-256
values are `3B6B8902184718C6714FD1EA00F87CD30C8D8527E744DEA93C7E16E319495833`.
`gradlew -p server test --no-daemon` then passed 177/177 (23 JUnit XML files,
0 failures/errors/skips). Log:
`.local/e2a-server-unit-suite-sku-original-20260923.stdout.txt` SHA-256
`AAF2019C9F03ECDA076CBF08095B2EE64D48FED31924AF4DDCA8E499013F8B1D`.
The preceding missing-file failure remains recorded below. No opt-in IT was
rerun, no database was started, and no independent writer or combined
HTTP/Android/device/production acceptance is claimed.

2026-09-23 latest (author, uncommitted): user assigned project development
and isolated database/operations responsibility directly to this assistant.
For the dedicated 19329 instance only, attempt7 used a fresh guarded profile,
kept binary logging enabled and enabled `log_bin_trust_function_creators` on
that isolated mysqld process; no SUPER or shared/production change was made.
Its preflight recorded 8.0.46, logBin=1, trust=1, schema TRIGGER=1,
global SUPER=0. V34 non-empty seed, V35, 28 negative cases and before/after
preservation reached the target marker, but the 5-second listener identity
probe failed during automatic cleanup; the test failed and left 19329 open.
After exact PID 3224/datadir/init-file verification, the dedicated account
authenticated-shut it down separately (mysqladmin exit 0, no listener).
The attempt7 marker remains `stop-failed`, and its failure XML, data, logs,
private credentials and evidence are preserved, not rewritten as success.

After increasing only the exact process identity probe wait to 30 seconds,
fresh attempt8 completed the same guarded V34→V35 flow, 28 negative cases,
before/after preservation, `started/initialized/stopped/completed` marker
and authenticated shutdown with no 19329 listener. Its opt-in IT is
1/0 failures/0 errors/0 skips; focused E2a unit tests are 35/0/0/0.
Evidence: `.local/evidence-e2a-attempt8/integrationTest-result.xml` SHA-256
`1F0C57135B44C1AAD570E014882C10C11B6996E87B808400C7928CFA71D32178`,
`before-v35.json` `6C9A1A4C4FFCEBD83FCAFCF6862198E405E880994AE225CCE18ADDA4E55DCAEB`,
`after-v35.json` `04755C4D1803FD27F0D3721D83BC08ADA3F96F0D0EE8AD166D72F8FDA0EB77AD`,
`v35-negative.json` `F649F7FCACBB4B4B61882D27EE208B1DBEC2345441E447B810E26BB0D54737F8`.
Attempt7 failure XML SHA-256 `A86F9B5C3B7A5AD63B4BCCA576C46F07DF8689FD2F1A13A92D9CD614D7FE1315`.
`bootJar` built, but the broader server `test` task failed 1/177 because
the existing `SkuParserTest` requires absent `../docs/sku.txt`; do not count
the broad suite as passed or replace the missing real input with synthetic
data. No independent consumer, real HTTP/Android/device or production
deployment has been validated.

2026-09-23 earlier preflight stage (author, uncommitted): the opt-in runner checks the
dedicated account and schema, MySQL version, `@@GLOBAL.log_bin`,
`@@GLOBAL.log_bin_trust_function_creators`, schema `TRIGGER` and global
`SUPER` grants before target-34 Flyway or synthetic seed. It writes a
CREATE_NEW `trigger-preflight.json` under the attempt's private evidence
directory with the read-only conditions and a blocked/preconditions-met
result; it does not change any MySQL setting or privilege. This is only
a technical precondition, not deployment approval. The existing attempt6
profile remains spent and was not rerun. Focused E2a author tests now pass
35/35 (including seven new preflight tests); no full build, independent
consumer, dynamic V35, negative matrix or before/after comparison was run.
The exact production-compatible trigger/binlog policy still needs an
authorized decision before allocating attempt7 and testing it on a fresh
profile. The read-only requirements and execution gate are recorded in
`TRIGGER_DEPLOYMENT_DECISION.md`; no target-instance readings or approval
have been supplied. Prior failures and directories remain untouched.

2026-09-23 earlier launcher stage (author, uncommitted): the isolated one-shot launcher
uses a private `--init-file` with a generated 64-hex credential, verifies the
direct child listener identity, and authenticated-shuts down attempts 4–6.
Focused tests (bootstrap, cleanup, identity, ACL, environment, evidence SQL
binding and snapshots) passed 28/28 after the last source change. Attempts
4–6 each ran once with distinct schema/account/datadir/storage/signing/private/
evidence/marker paths; none is reusable. Attempt4 reached V34 then failed in
the synthetic order fixture's cyclic FK insert (fixed with NULL → revision →
UPDATE). Attempt5 reached the synthetic evidence set, then failed because
the fixture had eight placeholders for nine bound values (fixed, with a
three-mode binding test). Attempt6 successfully seeded non-empty V34 and
wrote `.local/evidence-e2a-attempt6/before-v35.json`, but V35 failed at
`CREATE TRIGGER` line 60 with MySQL 1419: binary logging enabled and the
dedicated account lacks SUPER. All three markers record `failed` then
`stopped`; 19329 had no listener afterward. Distinct stdout, XML, datadir,
bootstrap and shutdown logs remain under each `.local/*attempt*/` path;
attempt4/5/6 XML SHA-256 respectively `A4E0CE881352073B3ED8043BF6931CE045881A98BAA97C815B7D3BF6A2228676`,
`F772794710F760ED47A179E4549DAAE88100282CFC726265A5F233318E9E1437`,
`97F79156DDC1896AAA8099666E48BE86A7EEF2CCE8046DF2BEB8D7CC2249A0A8`.
No attempt reached V35 negative cases or before/after preservation comparison.
The local V35 privilege/binlog blocker needs an explicit deployment-compatibility
decision before a fresh attempt; do not silently disable binlog, grant SUPER,
modify the fixed V35 resource, restart a failed datadir, or claim S1–S4 passed.

2026-09-23 09:01: Three distinct one-shot profiles failed before V34 seed.
Attempt1 failed on inherited PowerShell modules before MySQL initialization;
attempt2 initialized MySQL but failed on a second ACL write (now guarded by
idempotent single-user ACL verification); attempt3 initialized and started
MySQL but rejected its child listener PID, then left that child listening.
After exact PID/datadir/listener verification, only child PID 23260 was stopped
without authenticated graceful shutdown; its datadir may require recovery.
Attempt2/3 XML was copied to each distinct `.local/evidence-e2a-attempt*/`
before Gradle reused the test path. Attempt1 XML was overwritten, while its
stdout and marker remain. All three profiles and evidence stay untouched.
No V34 seed, V35 migration, 28-case negative result, or preservation comparison
passed. Do not run attempt4 until the test-only bootstrap and child-process
ownership/shutdown design has been reviewed and verified.

2026-09-23 handoff: the exact production/V35 `fa7981d`, foundation `319305f`,
and protocol `79cad441` remain integrated at branch HEAD `568683c`. The
uncommitted launcher cleanup and Windows PowerShell child-environment fix
passed 11 focused tests (3 launcher, 8 environment). One opt-in dynamic run
failed before MySQL initialization at the private-directory ACL guard because
the child inherited an incompatible `PSModulePath`. The failure marker,
resource hash, empty datadir, stdout, and XML are preserved under `.local/`;
19329 is stopped. No V34 seed, V35 migration, negative matrix, or preservation
comparison passed. Do not rerun the one-shot path or erase this evidence;
assign a fresh, exact guarded namespace before S1–S4. The earlier preparation
status and deployment heavy-slot assignment below are historical.

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

Also verify that `PAGE` and `STOP` object kind, manifest identity, and the
fixed `routeScope` are writer/protocol checks unless W1 adds explicit DDL
constraints. The DDL cannot constrain an object's `kind` through the existing
composite foreign key. STOP may be durably recorded while pages are missing,
with the intake remaining `PARTIAL`; missing pages must prevent set closure and
complete VERIFY, rather than being treated as an invalid network ordering.

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
- invalid route, status, operation, digest, and over-limit bytes (route and
  protocol-shape failures are writer checks unless represented in DDL).

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
pass claim. The executable runner and SQL files are deliberately inert until
the full profile guard succeeds; the runner exits before any datasource or
Flyway setup and reports that dynamic execution is unscheduled.

The full preflight must match the exact JDBC URL, schema, account, port,
datadir, storage directory, and both signing files. It must canonicalize every
configured path and reject a symlink/junction that resolves outside this
worktree. It must reject reset, repair, seed deletion, or any database action
until the preflight succeeds; a boolean port-free input alone is insufficient.
