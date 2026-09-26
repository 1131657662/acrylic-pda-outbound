# E2a W1 interface card (V35 intake foundation)

This card fixes the internal wire and schema seam for W2/W3 review. It does not
open HTTP routes, receive files, write K, or enable a business decision.

## Wire constants

* `schemaVersion=1`, `routeScope=r06.e2a.evidence.v1`
* JWT issuer `acrylic-outbound`, audience `acrylic-r06-e2a-intake-v1`
* RSA receipt signatures use `RS256` with base64url segments (no padding).
* STOP signatures use the explicit algorithm
  `ECDSA_P256_SHA256_DER` (P-256 `SHA256withECDSA`); this is deliberately not
  JOSE `ES256` raw `r||s`. Signature bytes are standard ASN.1 DER and are
  base64 (not base64url).
* Public keys are base64 X.509 SubjectPublicKeyInfo DER. `keyId` is lowercase
  SHA-256 of those DER bytes.
* Hashes are lowercase ASCII SHA-256. Domain prefixes are UTF-8 bytes ending
  in `\n`: `ACRYLIC-R06/E2A/PAGE/1`, `.../MANIFEST/1`, and `.../SET/1`.

## Canonical objects

Canonical JSON sorts object keys lexically, preserves array order, rejects
duplicate keys, BOM, malformed UTF-8, lone surrogates, floating numbers and
integers outside the safe range. Hash input is the UTF-8 canonical bytes.

`pageCore` contains only manifest identity, snapshot, page counters,
`prevPageHash`, and ordered item references. `pageHash` is derived and is never
written into `pageCore`.

Each v1 item reference has exactly `itemKind`, `originalRecordId`,
`objectKind`, `originalObjectId`, `itemBytesSha256`, `byteCount`,
`originalDeviceSeq`, `recordSha256`, `recordStatus`, `baleId`,
`originalCycle`, and `captureId`. The two IDs identify one original record and
one separately uploaded immutable raw object; one raw object is referenced by
at most one item in a source round. `itemKind` is one of V34's
`CAPTURE/SCAN/AUDIT/STOP/ARCHIVE`, while `objectKind` is one of
`LOCAL_FROZEN_PAYLOAD/CAPTURE_RECORDS/AUDIT_RECORDS/AUDIT_ARCHIVE`.
`itemBytesSha256` and `byteCount` must match the raw object's original bytes;
For an `ARCHIVE/AUDIT_ARCHIVE` item, the raw bytes are the canonical server-signed
audit archive proof, `originalDeviceSeq` is its `throughSeq`, and
`recordSha256` is SHA-256 of the canonical proof including `signedArchive`.
Because the archive table has no UUID, its `originalRecordId` is the UUIDv3
name of UTF-8 `acrylic-pda-audit-archive/{originDeviceId}/{recoveryEpoch}/{afterSeq}`
(Java `UUID.nameUUIDFromBytes`, lowercase canonical UUID). This binds the
original database key, not an arbitrary client UUID or just a mutable proof
digest. Its signed proof must also match the original server archive row and
durable payload file; every event must match the server device sequence.
`recordSha256`, device sequence, status, and scan fields remain claims until
independently checked against those bytes and server facts. SCAN requires
canonical `baleId`, positive `originalCycle`, and canonical `captureId`;
all three are null for other kinds. IDs are lowercase canonical UUIDs,
hashes lowercase 64-digit hex, integers JSON-safe, and `recordStatus` is
nonblank printable text of at most 40 UTF-16 code units. Within a page,
`(itemKind, originalRecordId)` and `originalObjectId` are unique;
`itemCount` is the number of references and `byteCount` their exact summed
raw lengths, capped at 128 MiB. PAGE acceptance remains `PARTIAL` and never
trusts a record or grants occupancy solely from a claimed reference.

`manifestCore` contains manifest identity, source/round, page count, ordered
page roots, entry/byte totals and `endPageNo`. It excludes `setHash`,
`manifestBytesHash`, STOP, signatures and verification. `manifestBytesHash` and
`setHash` are derived independently from the same canonical core with their
respective domain prefix.

`STOP_PROOF.core` binds source identity, source/round, original operator,
device/epoch, stop event/high-watermark/audit sequence and the derived
manifest/set hashes. The EC signature covers only `domain || canonical(core)`.

The internal STOP parser accepts exactly `operation=STOP`, `openOriginalKey`,
`manifestCore`, derived `manifestBytesHash`, derived `setHash`, and `stopProof`.
It requires a server-selected trusted P-256 public key, the original OPEN
manifest/snapshot/source identity, and current server-observed operator,
device, warehouse and recovery epoch. It only validates and returns canonical
bytes; it does not register a device key, assert uploaded page completeness,
insert a STOP object/set, establish trusted item facts, or release stock.

The internal wire shape is fixed as follows: IDs (`warehouseId`, `sourceId`,
`submissionId`, operator/device/event and manifest IDs) are non-empty JSON
strings; `sourceId` identifies the existing TEMP source/authorization and is
paired with `submissionId` and `workRound` (the V34 scope/S context); versions,
rounds, audit/stop counters and page counters are JSON safe integers (no
strings, booleans, floats or overflow); hashes and key IDs are lowercase
64-character hexadecimal strings. `source` remains the source kind (`TEMP`)
and does not replace the source/S identifiers.

## Intake receipt seam

V35 adds an INSERT-only `source_evidence_intake_receipt` keyed by
`(principal_id, route_scope, original_key)`. It stores canonical request and
original response bytes plus hashes, RSA `kid`/signed JWT, operation/status,
source context and nullable set/object/verification references. Receipt to set
and verification references use the existing V34 composite parents so scope,
submission and work round cannot be bypassed by NULLs.

Reference nullability follows lifecycle: OPEN has no object/set/verification;
PAGE and STOP carry exactly one object and no set/verification; VERIFY carries
set plus verification and no object. Composite FK columns remain all-context
bound, so NULL cannot bypass a stage CHECK. No source item/page columns are
copied into the receipt. There is no UPDATE/DELETE path; current effect is a
separate read/append concern.

The intake order is `OPEN -> PAGE* -> STOP -> VERIFY`: every PAGE and STOP
must reference the manifest identity returned by OPEN, and STOP records an
independent durable object even when pages are still incomplete. VERIFY is
accepted only after STOP and the page set close successfully. Missing or late
pages remain `PARTIAL`; they cannot be used to skip STOP or to create a
`VERIFIED_COMPLETE_AT_STOP` verification.

## Limits and capabilities

Page and manifest bytes are capped at 256 KiB, object bytes at 32 MiB, sets at
4096 pages and 128 MiB raw bytes. `PARTIAL` never implies a complete trusted
set or release; it may retain a specific package only after independent server
facts and B/K lock checks prove that package's original qualification, capture,
cycle and current bale. The receipt itself never grants occupancy.
`UNVERIFIABLE` never grants occupancy. Every generated capability is false, including
`localDeletionAllowed`.

Trusted facts are server-observed source, identity, device, recovery-epoch and
B/K context facts compared as a complete object; client-declared lists,
quantities, order IDs or re-signed receipts are never trusted facts by
themselves. `verifyRsaReceipt` proves only cryptographic receipt integrity;
`verifyTrustedRsaReceipt` additionally compares the signed receipt object with
a non-empty complete facts object supplied by the server-side caller. This
primitive does not define or persist that facts schema, permissions, keyring,
or database context; a caller must supply the full context and reject missing
fields before treating the result as trusted.

## Negative invariants

Reject mixed manifests, duplicate or out-of-order pages, incorrect previous
hashes, hash cycles, unknown historical keys, EC actor/device/epoch mismatch,
and RSA receipts re-signed with mismatched facts. The wire rules above are independent of controllers; the RAW transport binding follows.
## RAW HTTP transport (2026-09-25)

`POST /api/v1/pda/temporary-source-evidence/raw` consumes `application/octet-stream`.
Query: `warehouseId`, `submissionId`, `workRound`, `openOriginalKey`, `originalObjectId`,
`kind`, `byteCount`, `bytesSha256`; bearer plus `X-Server-Recovery-Epoch` are required.
The body is the original immutable object bytes, streamed with a 32 MiB object limit.
Same bound original ID/kind/bytes replay returns the same stored object metadata;
different bytes conflict. Missing OPEN, changed actor/source/warehouse/round/recovery epoch,
invalid size/hash or truncated/long body fails closed. No raw object read endpoint is required:
a lost storage acknowledgement is recovered by replaying the exact original bytes and ID.
The response is an ordinary API envelope containing `STORED` metadata and
`localDeletionAllowed=false`, with no internal file path or signed business receipt.
Clients must still verify the separately signed PAGE/STOP/VERIFY receipts.
