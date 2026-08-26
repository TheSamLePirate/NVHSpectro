# AAA Refactor — Execution Tracking

Live log of the `V13.1-AAA-plan.md` execution. One section per phase: steps,
commits, verification evidence, and every deviation from the written plan.
Update this file **in the same session** as the work it describes.

**Branch:** `aaa/phase0` (from `master` @ `4518ec6`) · **Status: Phases 0 AND 1 COMPLETE — Gates 0 & 1 passed on emulator** (2026-08-26)

---

## Phase 0 — Foundation & safety net

### Steps executed

| Step | What was done | Commit |
|---|---|---|
| — | Governing docs committed (audit, plan, history reconstruction) | `64eb308` |
| 0.1 | Purged from tracking: 91 patch scripts (`.py`/`.ps1`), 6 root APKs, `.kotlin/` logs+sessions, 65 extracted slide PNGs, `report_mode_screen_copy.kt`, machine-specific `.idea` files; `W2628_EOL_Result.pptx` untracked but **left on disk** pending clearance. `.gitignore` hardened (`build/`, `*.apk`, `*.aab`, `.kotlin/`, `*.pptx`, `.tools/`, …). gradlew executable bit restored (needed by CI) | `804f70f` |
| 0.2 | Deleted dead template module (`ui/main/*`, `Navigation.kt`, `NavigationKeys.kt`, `data/DataRepository.kt`, both template test files); dropped Navigation 3 deps + version-catalog entries. kotlinx-serialization plugin kept (Phase 3 uses it) | `98d2d19` |
| 0.3 | Version single-sourced: `appVersionName = "13.2.0"` / `appVersionCode = 13` in `app/build.gradle.kts` drive versionName/versionCode/APK name; `buildConfig = true`; `InfoDialog` displays `BuildConfig.VERSION_NAME` | `8f4f73c` |
| 0.4 | ProGuard: keep `org.jtransforms.**` (old rule targeted the nonexistent `com.github.wendykierp.jtransforms` — kept nothing); dropped pointless `@Composable` rule. Verified via minified `assembleRelease` | `6e3b3e6` |
| 0.6 | Characterization harness — 24 tests: FFT amplitude calibration (0/−20/−60 dBFS, analytic); **pinned** defect tests carrying finding IDs (D3 first-frame squelch, D7 sub-30 Hz mask, D9 scalloping −1.42 dB, C11 comma-decimal parsing); golden full-spectrum snapshot (seed 42, tol 1e-9) at `app/src/test/resources/golden/`; kinematics vs hand-computed 205/55R16 constants; WAV round-trip + independent byte-level header check | `666b60d` |
| 0.7 | Reformat-only: fixed misleading indentation (`MainViewModel` isFrozen block + closers, `MainScreen` report-mode blocks). Proof of zero semantic change: `git diff -w` empty; full test suite incl. golden unchanged | `f32a18e` |
| 0.5 | CI: `.github/workflows/ci.yml` (hygiene gates → ktlint → detekt → unit tests → lint → debug+minified release assemble → APK artifact); `ci/checks.sh` gates (no tracked binaries/scripts/build state, gradlew exec bit, version single-sourcing; literal-`44100` gate present but **disarmed** until Phase 1 — reports count, currently **28**); ktlint 1.5.0 + detekt 1.23.8 as pinned CLIs with committed baselines | `c4bfd57` |
| 0.8 | `FieldLocationLogger` (debug builds only): every raw fix batched to CSV under app external files — `elapsedRealtimeNanos`, UTC time, provider, lat/lon/alt, Doppler speed, `speedAccuracy`, horizontal accuracy, bearing — the Phase 2 estimator tuning data (G1–G4). Own daemon thread, fail-once-then-silent, absent from release | `ff12b67` |
| — | Unplanned crash fix (see DEV-2) | `821f273` |
| — | `AGENTS.md` rewritten as the refactor's operating manual; this tracking file added | (this commit) |

### Gate 0 verification (2026-08-26, emulator NVH_API_37_compact, API 37)

- ✅ **Minified release** (`APP_NVH_Spectro_v13.2.0-release-unsigned.apk`, ad-hoc debug-keystore signature) installs; `dumpsys package` reports `versionCode=13 versionName=13.2.0`.
- ✅ **30 s live session**: process alive, **zero** FATAL/AndroidRuntime/ANR entries in logcat. Screenshot evidence: live spectrogram scrolling (silent under emulator `-no-audio`), frequency axis + cursor working, GPS card green with emulator's fake fix (0.6 km/h), telemetry graph plotting, all controls rendered.
- ✅ **About dialog shows `v13.2.0`** from `BuildConfig` inside the minified build — single-sourcing verified end-to-end, and R8 with the corrected keep rules runs the full JTransforms FFT pipeline.
- ✅ Local CI equivalence: `ci/checks.sh`, ktlint, detekt, `testDebugUnitTest` (24/24), `lintDebug` (zero errors, **no baseline**), `assembleDebug`, `assembleRelease` — all green, re-verified after the OS-update restart.
- ⏳ First **remote** GitHub Actions run occurs on next push (nothing pushed; commits are local per instruction).

### Deviations from plan

| ID | Deviation | Rationale / follow-up |
|---|---|---|
| DEV-1 | Work committed on branch `aaa/phase0`, not `master` | Default-branch protection policy; merging to `master` is the owner's call. Plan assumed direct commits |
| DEV-2 | **Unplanned bug fix**: `MutableList.removeLast()` → `removeAt(lastIndex)` at 4 hot-path sites (`821f273`) | Newly-armed lint found NewApi **errors**: on this toolchain `removeLast()` binds to the Java 21 SequencedCollection method (API 35+) → `NoSuchMethodError` crash on any device below Android 15 within seconds of live use. A latent crash the audit missed; fixing beat baselining. Validates the entire Phase 0 premise |
| DEV-3 | Step order: 0.6 (tests) executed before 0.5 (CI) | CI's first commit already runs real tests. No downside |
| DEV-4 | ktlint/detekt via **pinned CLIs** (1.5.0 / 1.23.8), not Gradle plugins | Avoids plugin-compat risk against AGP 9.0.1/Kotlin 2.3.20; CI downloads identical versions. Baselines: ktlint 3,081 / detekt 845 pre-existing findings frozen |
| DEV-5 | Style baselines regenerated after 0.7/0.8 (in `ff12b67`) | Line-position-sensitive baselines invalidate when baselined files are edited. Rule codified in AGENTS.md: regenerate in the same commit as edits to baselined files |
| DEV-6 | Android lint kept at **zero errors with no baseline** | Plan anticipated a lint baseline; after DEV-2 none was needed. Stricter than planned — keep it that way |
| DEV-7 | Gate 0 device run on **emulator**, not a physical phone | No device attached this session. Emulator limits noted (silent mic, fake GPS): dBFS values are not evidence there. **Follow-up: repeat the 30 s live check on a physical phone at next opportunity** |
| DEV-8 | Machine-local enablers: `local.properties` created (gitignored), release APK signed ad hoc with the debug keystore | No signing config committed — release signing strategy is plan step 5.5 |
| DEV-9 | Historical APKs deleted from the working tree | Recoverable from git history until decision D1 (history rewrite) executes |

### Notes for Phase 1

- Arm the sample-rate gate by uncommenting `ARM_SAMPLE_RATE_GATE: "1"` in `ci.yml` as part of plan step 1.1 (current literal count: 28).
- The pinned tests to update alongside their fixes: `pinned_frenchDecimalComma_splitsIntoTwoOrders` (C11 → plan 1.8), `pinned_binsBelow30Hz_forcedToMinus120` (D7 → Phase 3), `pinned_firstTtnrFrame_alwaysSquelchedAsShock` (D3 → Phase 3), `pinned_halfBinTone_showsHannScallopingLoss` (D9 → Phase 3).
- Emulator quirk log: booted clean on first try this session (no half-boot recovery needed).

---

## Phase 1 — Measurement correctness

### Steps executed (2026-08-26, same session as Phase 0)

| Step | What was done | Commit |
|---|---|---|
| 1.1 | **[C1]** `AudioConfig` created (the only file allowed a literal sample rate); `FFTProcessor(fftSize, sampleRateHz)` per instance; `computeTTNR` uses the instance rate; required (defaultless) `sampleRate` params on `SpectrogramCanvas`/`TelemetryGraph`; `MainScreen.analysisSampleRate` feeds every surface; SettingsDialog DSP table, PDF (duration derived from bin count), ReportModeScreen, biquads + filtered-WAV header, exportData, WAV sweep nyquist (the 6-lines-below bug), `validateCurrentOrder` all consume the source rate; WAV stepping uses `WAV_FFT_SIZE`. **CI sample-rate gate ARMED** (0 literals outside AudioConfig). Test `c1_fortyEightKhzStream_usesItsOwnBinGrid` | `c06b83f` |
| 1.2 | **[C2]** `WavDataReader` rewritten as a real RIFF chunk walker: LIST/fact/bext tolerated, fmt parsed (incl. extensible GUID), stereo downmixed with frame-counted duration, 24-bit/float/non-PCM rejected via typed `WavReadResult` with French messages; exact-size streamed buffers (53 MB blind alloc gone); new dismissable analysis-notice banner in MainScreen. Tests `c2_*` (7) | `57b6472` |
| 1.3 | **[C3]** `durationMs = min(mediaDuration, analyzedDuration)`; video extractor reports extracted (not container) duration; truncation banner ("Analyse limitée aux 5 premières minutes"); playback pauses at the analyzed end | `681ce77` |
| 1.4 | **[C17]** Pure `TimelineMapper` (mapIndex + timeToIndex, exact identity for the live 1:1 case) adopted by `validateCurrentOrder` (the PDF-corrupting bug), both WAV-sweep mappings, and `processWavFrameAt`. Tests `c17_*` | `d08fb49` |
| 1.5 | **[C16]** WAV/URI loads parse on `Dispatchers.IO` behind the progress overlay, with a load-generation guard; load and video-extraction failures surface in the banner; extraction progress message added | `384a7aa` |
| 1.6 | **[C8/C9]** Capture source UNPROCESSED (device-advertised) else VOICE_RECOGNITION, recorded into export metadata (`captureSource` field); `AudioRecord` init/busy fully guarded → typed `AudioCaptureException` → banner (was an app crash); short-read fill loop (stale-tail fix); read-error backoff then clean stop; dead fields removed | `f069c26` |
| 1.7 | **[C4/S3]** `RecordingStore`: MediaStore.Downloads writes (IS_PENDING pattern) on IO with legacy pre-29 fallback; save success/error banners, failed saves keep PCM + reopen dialog; millisecond-suffixed names; `WavSelectionDialog` lists via MediaStore+legacy merge off-main (was filesystem-in-composition); selection loads by URI incl. telemetry sidecar; `WavAudioWriter.writePcmToStream` added; dead `loadWavFile(File)` deleted | `09a1ac5` |
| 1.8 | **[C14]** min/max dB clamped ≥5 dB apart; **[C13]** FFT-size changes ignored outside LIVE (no more wiped WAV spectrogram); **[C11]** `toFlexibleDoubleOrNull` (comma decimals), Decimal keyboards + red invalid state on kinematics fields, digit-comma-digit decimal rule in `parsedTargetOrders`; pinned C11 test replaced by `c11_*` fixed-behavior tests in the same commit | `3a64bca` |

### Gate 1 verification (2026-08-26, emulator NVH_API_37_compact, minified release v13.2.0)

- ✅ **48 kHz reference** (4 kHz tone, 10 s): axis renders the 48 kHz grid (7987/5990/3993/1996 labels), the tone line sits exactly ON the 3993/4 kHz gridline (old code would have drawn it ~8 % low at 3675 Hz), frequency cursor on the tone reads **3991.0 Hz** — within Δf/2 = 11.7 Hz. Duration 00:10, playhead synced.
- ✅ **Stereo import** (440 Hz L=R, 6 s, 44.1 kHz): duration reads **00:06** (old parser: 12 s), tone at the correct 440 Hz height, playback clean — downmix + frame-counted duration verified.
- ✅ **Record → save → list → reload round trip**: 6 s live recording saved via MediaStore (`Essai_…_21h36m23s001.wav` + `_telemetrie.json` visible in `content://media/external/downloads`, millisecond suffix ✓), ✅ success banner shown, picker lists the entry with "Audio + Télémétrie GPS", reload restores audio + telemetry speed curve.
- ✅ Zero FATAL/ANR across the whole session (record, mode switches, 3 file loads, playback).
- ✅ All local gates green throughout: 40 unit tests, lint 0 errors, ktlint/detekt vs regenerated baselines, `ARM_SAMPLE_RATE_GATE=1 ci/checks.sh`, minified release assembly.

### Deviations / pending items from the Gate 1 checklist

| ID | Item | Status |
|---|---|---|
| DEV-10 | PDF frequency-axis surface not exercised on device (report-mode UI flow not automated) | Covered by code path (sampleRate parameter) + c1 unit tests; verify visually during the next real report export |
| DEV-11 | Android 10 (API 29) save/reload check | No API-29 AVD on this machine; MediaStore path is the API-29+ codepath and was verified on API 37. **Run once on an Android 10 device when available** |
| DEV-12 | 10-min video playhead check | No long-video asset on the emulator; C3 min-clamp is unit-logic + code-reviewed. **Check with a real >5-min video on next field use** |
| DEV-13 | Mic-busy-during-phone-call check | Not simulatable on this emulator; the C9 guard path is code-complete (typed exception → banner). **Verify on a physical phone** |
| DEV-14 | Observed during Gate 1: WAV spectrogram appears black until the first interaction/playback tick after load | Pre-existing U2 render-lag quirk (bitmap painted in LaunchedEffect, no invalidation) — NOT a Phase 1 regression; fixed properly by plan 3.5 (`SpectrogramImageProducer`) |

### Notes for Phase 2

- `_analysisNotice` is now the single user-facing message channel — Phase 2's capture-engine errors should reuse it (until plan 4.7 builds the full error surface).
- `AudioRepository` now takes a `Context`; the Phase 2 `CaptureEngine` refactor starts from a guarded, source-selected base.
- Emulator reports VOICE_RECOGNITION (fallback path exercised); UNPROCESSED path needs a physical device to observe.

