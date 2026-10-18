# E08/O02 physical Android SAF and USB readback candidate (2026-10-18)

This slice validates the Android system file channel on the connected M71 device. It uses a
debug-only test activity and a synthetic encrypted-package byte vector; it does not claim a
real PDA, production identity, external backup medium, MTP/OTG, or business receipt.

## Scope and implementation

- Added `src/debug`-only `PhysicalSafCopyActivity`, which opens the real Android
  `ACTION_CREATE_DOCUMENT` picker and invokes the production `RescueFileTransfer` byte-copy and
  target readback primitive. The activity is not present in staging or release builds.
- Added one physical instrumentation test that creates an isolated 96,257-byte synthetic source,
  starts the target debug activity, waits for the user-selected `content://` target, and asserts
  the source remains byte-identical after the device-side readback.
- Evidence directory: `.local/goal-20261018-resume/physical-saf-run07/`. It contains the final
  instrumentation log, device result/summary, source and host readback bytes, target SHA and size,
  APK/test APK hashes, device facts and USB pull log. Failed runner attempts remain in run01 through
  run06 and were not reused as pass evidence.

## Final run

The final commands were:

```text
D:/acrylic-pda-outbound/gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
D:/acrylic-pda-outbound/gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.rescue.RescueFileTransferTest :app:lintDebug --no-daemon
adb -s 22D2910042 shell am instrument -w -r -e physicalSaf true -e class com.acrylic.outbound.pda.PhysicalSafCopyDeviceTest com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s 22D2910042 pull /sdcard/Download/pda-physical-saf-test.pdarescue physical-saf-run07/host-readback.pdarescue
```

The build, targeted unit test and debug lint completed successfully. The physical instrumentation
reported `OK (1 test)` and the shell command exited zero. The device was M71 (`hardware=mt8768`,
Android 9, 480x800) with serial `22D2910042`. DocumentsUI selected the `Download` provider and
returned `content://com.android.providers.downloads.documents/document/2`.

The device-side source, device-reported target digest and USB `adb pull` host digest all resolve to
`052B3FC5598BC05A6D1FF3CB4436F664C474D085F4D06582F1EE6BA6AA4156C6`; both source and target are
96,257 bytes. The target was written and read back through DocumentsUI before the activity reported
success. `device-summary.json` intentionally records `externalStorageVerified=false`,
`businessReceiptVerified=false`, `usbHostReadback=false` and `localDeletionAllowed=false`.

## Failures retained

- run01 stopped before the source parent directory existed (`FileNotFoundException`).
- run02 resolved the test activity in the wrong package after relative Manifest merging.
- run03 reached the test APK activity but `ActivityScenario` rejected its different process.
- run04 used `startActivitySync` with the same cross-process restriction.
- run05 still built the old component package; run06 completed the copy but
  `ActivityScenario.close()` hit a post-finish framework NPE. The run06 target bytes and log are
  retained; run07 removed that test-only target before the final run and records the pre-cleanup SHA.

No failed attempt entered a business workflow or changed a database. After the final evidence was
pulled, the explicitly named synthetic file in `/sdcard/Download/` and the app-private
`files/physical-saf` test directory were removed; the target SHA, cleanup checks and disk snapshot
are recorded in run07.

## Boundaries

This is a physical-device Android SAF plus USB ADB byte-readback candidate for O02. ADB transport is
not MTP, OTG or proof of an externally stored medium. The vector is synthetic, the debug activity is
test-only, and no real PDA/Keystore, business snapshot, Excel installation, second device, field
receipt, external-storage custody, or production authority was involved. It therefore does not close
O02/O05, D11/D12/D14/D15/D16/D17, E06/E07/E08 L2, any section-8 gate, or D18 L3.

## Submission record

- Code paths: `android/app/src/debug/AndroidManifest.xml`,
  `android/app/src/debug/java/com/acrylic/outbound/pda/PhysicalSafCopyActivity.kt`, and
  `android/app/src/androidTest/java/com/acrylic/outbound/pda/PhysicalSafCopyDeviceTest.kt`.
- APK SHA-256: debug app `144EBF6C3C49485CF34AB0DF585D4D23B16C1E19BAC2B91DBD15FD5753F7D356`;
  debug instrumentation `529876F098AC7125C469ECDDEBDEAEF176993DB34AFBEC224CCFD9EC1250F4F8`.
- Directly affected layers: Android debug-only test harness and Android physical file-channel
  evidence. Existing production rescue code and inherited WIP were not rewritten.
- Reuse basis: existing `RescueFileTransfer` implementation and its unit tests; no prior physical
  SAF result was reused as a pass.

The candidate is retained as development evidence only. Production defaults, external custody,
business receipt verification and deletion authority remain closed.
