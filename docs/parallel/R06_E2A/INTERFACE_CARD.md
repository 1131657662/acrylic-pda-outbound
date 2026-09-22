# E2a W1 interface card (V35 intake foundation)

This card fixes the internal wire and schema seam for W2/W3 review. It does not
open HTTP routes, receive files, write K, or enable a business decision.

## Wire constants

* `schemaVersion=1`, `routeScope=r06.e2a.evidence.v1`
* JWT issuer `acrylic-outbound`, audience `acrylic-r06-e2a-intake-v1`
* RSA receipt signatures use `RS256` with base64url segments (no padding).
* STOP signatures use `ES256`/P-256 `SHA256withECDSA`; signature bytes are
  standard ASN.1 DER and are base64 (not base64url).
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

`manifestCore` contains manifest identity, source/round, page count, ordered
page roots, entry/byte totals and `endPageNo`. It excludes `setHash`,
`manifestBytesHash`, STOP, signatures and verification. `manifestBytesHash` and
`setHash` are derived independently from the same canonical core with their
respective domain prefix.

`STOP_PROOF.core` binds source identity, source/round, original operator,
device/epoch, stop event/high-watermark/audit sequence and the derived
manifest/set hashes. The EC signature covers only `domain || canonical(core)`.

## Intake receipt seam

V35 adds an INSERT-only `source_evidence_intake_receipt` keyed by
`(principal_id, route_scope, original_key)`. It stores canonical request and
original response bytes plus hashes, RSA `kid`/signed JWT, operation/status,
source context and nullable set/object/verification references. Receipt to set
and verification references use the existing V34 composite parents so scope,
submission and work round cannot be bypassed by NULLs.

All nullable references are nullable as a group; a CHECK rejects partial
references. No source item/page columns are copied into the receipt. There is
no UPDATE/DELETE path; current effect is a separate read/append concern.

## Limits and capabilities

Page and manifest bytes are capped at 256 KiB, object bytes at 32 MiB, sets at
4096 pages and 128 MiB raw bytes. `PARTIAL` and `UNVERIFIABLE` never imply
trusted occupancy. Every generated capability is false, including
`localDeletionAllowed`.

## Negative invariants

Reject mixed manifests, duplicate or out-of-order pages, incorrect previous
hashes, hash cycles, unknown historical keys, EC actor/device/epoch mismatch,
and RSA receipts re-signed with mismatched facts. This card intentionally does
not define controller/POST behavior.
