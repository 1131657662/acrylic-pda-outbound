# E08/O02 computer verification and XLSX conversion candidate (2026-10-18)

This slice exercised the existing offline rescue consumer against the Android-generated
synthetic package. It validates computer-side source trust, safe spreadsheet conversion,
saved-workbook readback, and incomplete-output retention. It does not claim a real PDA,
production identity, external media, business receipt, or an O02/O05 L2/L3 result.

## Input and ownership

- Tree: `D:/acrylic-pda-outbound`, branch `codex/development`, input HEAD
  `1b76a5bf7f0ba36e04290bad3a37949afde4e2fc`.
- Package: `.local/goal-20261018-resume/o02-cross-runtime/synthetic.pdarescue`,
  SHA-256 `758B6713E08DBE3E2EA0F86C8B3A8A1732FEE48AD2ED3260D0B8F36CFB082C41`.
- Package-external trust file: `.local/goal-20261018-resume/o02-cross-runtime/synthetic-trust.json`,
  SHA-256 `6387094124B84E8DBFEEBE5F82B6877A52A4A68E81473FEBB55DCF4737120CC6`.
- Output and detailed hashes: `.local/goal-20261018-resume/o02-xlsx-run08/`.
- No HTTP service, MySQL instance, emulator, or product database was started by this slice.

## Direct validation

The `tools.pda_rescue` modules decrypted the package with the supplied synthetic password,
required the package-external trust file, and called the same `publish(..., convert)` path used
by `tools.pda_rescue.cli --xlsx`. The output directory was new and was published only after
conversion, literal-text repair, and the bundled `openpyxl` value check completed.

- `verified-xlsx/rescue.xlsx` was exported and reopened with `openpyxl`.
- All five sheets (`核对汇总`, `扫码原顺序`, `产品核对`, `采集未决与失败`, `原审计`) matched
  `rescue.xlsx.expected.json` cell-for-cell.
- Formula cells: `0`; source trust: `true`; business receipt verification: `false`.
- Five rendered preview PNGs were produced and the sampled `采集未决与失败` preview was
  readable with the original identifiers and statuses visible.
- Workbook SHA-256:
  `dab1943579639a44395597ceac356341a10edf83d67a0e64f369bb398d873ac3`.
- Source package and verified output remain unchanged; the output contains no credentials or
  authority to submit, release inventory, or delete local data.

## Failure boundaries retained

The first attempt through the interactive CLI waited for Windows console `getpass` when a
password was piped. It was stopped before product code or output publication and is recorded in
`o02-xlsx-run08/runner-failure.json`. A separate conversion-injection run used an unavailable
Node executable. It raised `RescuePublishError`, left the destination absent, and retained one
`pda-rescue-partial-*` directory containing `verified.json` but no workbook. The failure record
is `o02-xlsx-run08/failure-injection.json`.

## Scope boundary

This is a synthetic computer-consumer candidate. It does not exercise a real Excel installation,
MTP/OTG or an externally stored medium, a real PDA/Keystore, a second device, live HTTP/MySQL,
non-empty migration composition, business receipt verification, or field recovery. The existing
`externalStorageVerified=false`, `businessReceiptVerified=false`, and
`localDeletionAllowed=false` boundaries remain in force. O02/O05, D11/D12/D14/D15/D17, E06/E07/E08
L2, D18 L3, and the section-8 gates remain incomplete.

## Submission record

- Directly affected layer: `tools/pda_rescue` computer consumer and its existing XLSX worker;
  no source code was changed in this slice.
- Evidence: `.local/goal-20261018-resume/o02-xlsx-run08/verification.json`,
  `failure-injection.json`, `runner-failure.json`, and `input.json`.
- Required checks: direct module conversion, workbook independent readback, rendered preview
  inspection, failure-retention injection, and `tools/version_facts.ps1 -Check`.
- Reuse basis: the Android/Python cross-runtime input and hashes in
  `GOAL_E08_O02_O05_CROSS_RUNTIME_2026-10-18.md`; this report adds a new actual XLSX export
  and readback rather than promoting the earlier candidate to a final result.
