# E08/O02/O05 rescue cross-runtime candidate (2026-10-18)

This slice binds the Android read-only rescue format to the independent computer verifier and
the encrypted-copy/retrieval path. It uses a generated synthetic Android package only. It does
not claim a physical PDA, a production identity, an external medium, or the final D18 chain.

## Scope and input

- Tree: `D:/acrylic-pda-outbound`, branch `codex/development`, input HEAD `3825e51ba010d858bc949cd93fe12b2f968f22be`.
- Android vector: `android/app/build/rescue-format-vector/synthetic.pdarescue`, SHA-256
  `758B6713E08DBE3E2EA0F86C8B3A8A1732FEE48AD2ED3260D0B8F36CFB082C41`; registration trust
  file SHA-256 `6387094124B84E8DBFEEBE5F82B6877A52A4A68E81473FEBB55DCF4737120CC6`.
- Cross-runtime output: `.local/goal-20261018-resume/o02-cross-runtime/`; it contains the
  verified read-only files, the encrypted-copy directory, retrieval result and environment
  snapshot. No service or project database was started by this slice.
- Relevant source/input hashes are recorded in `o02-cross-runtime/input-hashes.json`.

## Direct validation

The Android command was:

```text
D:/acrylic-pda-outbound/gradlew.bat -p android testDebugUnitTest --tests com.acrylic.outbound.pda.rescue.RescueFormatTest --tests com.acrylic.outbound.pda.rescue.RescueGeneratedFileTest --tests com.acrylic.outbound.pda.independent.IndependentClosureTest --no-daemon --console=plain
```

It completed with `BUILD SUCCESSFUL`. The three XML reports contain 27 tests, 0 failures,
0 errors and 0 skips: rescue format 8, generated file 6, and independent closure 13. The
tests cover long decimal sequences, formula/control-character escaping, zero/VOID/pending
facts, frozen-body byte preservation, wrong password/ciphertext, corrupt private output,
scope rejection, incomplete generation, and the rule that a read-only package cannot become
submission, inventory release or deletion authority.

The independent computer command ran the seven `tools.pda_rescue` test modules with all
vector directories explicitly bound. It completed 57 tests with 0 failures, 0 errors and
0 skips. This includes package signature/manifest checks, semantic counts and TSV output,
independent-source restrictions, closure signature checks, relay retry byte/key reuse,
encrypted-copy custody and retrieval refusal cases, and XLSX text protection.

The actual cross-runtime operation then decrypted and verified the Android-generated package,
published a new read-only output directory, created an encrypted copy, and retrieved it using
the copy-record SHA held outside the copy directory. The result is recorded in
`o02-cross-runtime/result.json`:

- package: 3,706 bytes; 13 verified output files; `sourceTrusted=true`;
  `businessReceiptVerified=false`;
- semantic summary: 4 captures, 2 scans, 3 events, 1 active, 1 VOID, 1 pending,
  1 failed, 123456 integer grams;
- copy-record SHA-256:
  `0D126E1AA3219E84D3E24CC06D200B920D82117B4228CD8ABCFCE53DF5CA9B60`;
- retrieval: integrity and source trust true, target read-back true,
  `externalStorageVerified=false`, `protectsExportedSnapshotOnly=true`,
  `businessReceiptVerified=false`, `localDeletionAllowed=false`.

## Boundaries and failures

The first Python invocation used `unittest discover` at the package directory and produced
relative-import loader errors; the first Android invocation used a nonexistent wrapper path.
Neither entered product code or wrote business data. The corrected commands above were the
only passing evidence. The failed runner conditions remain recorded in the task transcript and
are not counted as product failures.

The package, trust root and all generated output are synthetic. The slice did not exercise the
Android SAF/USB/MTP/OTG file channel, a real Excel installation, an externally stored medium,
an original PDA key, a second device, a live HTTP/MySQL business flow, or production recovery.
The shared listeners observed after the run are recorded in `o02-cross-runtime/environment-after.json`
and were not stopped. `externalStorageVerified=false` is intentional; an encrypted file on the
same machine is not an O05 external-backup result.

## Submission record

- Paths and layers: Android rescue format/snapshot/export classes (Android local read-only layer);
  `tools/pda_rescue/{verify,integrity,facts,copy}` and tests (computer consumer/O05 copy layer);
  this report, `DEVELOPMENT_PROGRESS.md`, `history/README.md`, and the orchestration board
  (evidence/index layer).
- Required checks: the targeted Android unit command, 57 Python consumer tests, the direct
  cross-runtime package/copy/retrieval operation, `git diff --check`, and
  `tools/version_facts.ps1 -Check` before commit.
- Reuse basis: existing Android rescue contract and generated vector tests, the saved O04
  independent vectors under `.local/goal-20261015/o04-independent-unit-03/vectors`, and the
  prior O05 copy acceptance report. This report does not combine those historical results into
  a final candidate.

## Conclusion

This is a new O02/O05 implementation and consumer candidate on synthetic input. It strengthens
the Android/Python format binding and same-machine encrypted-copy evidence. It does not close
O02/O05 as a whole, D11/D12/D14/D15/D16/D17, E06/E07/E08 L2, any field condition, the section 8
gates, or D18 L3. Production defaults, `localDeletionAllowed=false`, original data boundaries,
formal signing, deployment, release and `main` remain closed.
