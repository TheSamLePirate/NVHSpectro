# AAA Refactor — Execution Tracking

Live log of the `V13.1-AAA-plan.md` execution. One section per phase: steps,
commits, verification evidence, and every deviation from the written plan.
Update this file **in the same session** as the work it describes.

**Branch:** `aaa/phase0` (from `master` @ `4518ec6`) · **Status: Phases 0–4 COMPLETE; GPS-0 → GPS-4 COMPLETE — Gates 0–4 and GPS-0–GPS-4 passed on emulator (hardware follow-ups listed per phase)** (2026-08-27)

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

---

## Phase 2 — Concurrency, lifecycle & the speed chain

### Steps executed (2026-08-26, same session)

| Step | What was done | Commit |
|---|---|---|
| 2.4a | **[G1,G4]** `AlphaBetaSpeedEstimator` (pure Kotlin): monotonic-nanos intervals only, single-fix outlier coasting w/ two-consecutive acceptance, dropout re-seeding, capped prediction horizon, filtered acceleration by-product. Gains = critically-damped α/β pairing, provisional pending 0.8 drive-log tuning. 10 tests | `cd0261c` |
| 2.1+2.2+2.4b | **[C5,C6,C7,G1–G4,L3,L5]** The live pipeline: `CaptureEngine` (flatMapLatest-owned mic, bounded 64-frame DROP_OLDEST buffer, integrity counters, retryable inner-catch errors), `LiveAnalysisEngine` (ALL DSP state extracted from the VM, synchronized, run on the dedicated `nvh-dsp` thread), `SpeedProvider` (GPS_PROVIDER-first with fused provenance-filtered fallback, monotonic nanos, speed-accuracy LED, per-frame PREDICTED speed). **Deleted:** the 1.2 s delay, gpsHistory cross-clock bracketing, `TelemetryRepository`, the UNLIMITED channel, the per-restart consumer leak. `TelemetryData`/`GpsStatus` moved to `Telemetry.kt` | `acfc331` |
| 2.3 | **[L1,L2,L4,L6,L7]** `PlaybackController` (suspending prepareAsync, explicit original/filtered sources, idempotent release); cancellable filter job with position captured pre-render; stop keeps the player prepared; `resetAnalysisState()` on every source/kinematics/tracked-order transition; `onCleared` releases capture+GPS+player+DSP thread. `LiveAnalysisEngineTest` = the L7 matrix (reset restores first-frame squelch; order-EMA ghosts cleared) | `2d5bcfb` |
| 2.5 | **[C10,D4,D5]** Filters now render ONE filtered PCM feeding BOTH playback and the spectrogram (`processFullWavSpectrogram(analysisData=…)`, `_loadedWavData` stays original); BAND_PASS rebuilt as true 8th-order Butterworth HP×LP cascades; dead wrong `BiQuadFilter.process()` + doubt-monologue deleted (3.8 item pulled forward); `magnitudeAt()` added. `d4_*` analytical response tests (−3.01 dB at cutoff, −48 dB/oct, band edges, notch, DC gain) | `9049ad0` |
| 2.6 | Debug integrity log every 256 frames: produced/consumed/restarts + consumer thread name | `d6cf153` |

Unit tests: **61** total, all green; lint 0 errors; minified release builds; all gates pass with `ARM_SAMPLE_RATE_GATE=1`.

### Gate 2 verification (2026-08-26, emulator NVH_API_37_compact, debug build for logcat/counter evidence)

- ✅ **Single consumer / zero loss**: `LivePipeline: produced=8448 consumed=8448 restarts=… thread=nvh-dsp` after a session including **6 rapid FFT-size changes** through the settings dialog — produced==consumed throughout, app alive, zero FATAL. (Restart count < change count = StateFlow conflation skipping obsolete configs mid-switch — desired semantics.)
- ✅ **DSP off main**: every integrity line reports `thread=nvh-dsp`.
- ✅ **[C7] Mic lifecycle**: appops shows `RECORD_AUDIO (running)` in LIVE; after switching to Analyseur WAV the grant shows a finalized `duration=…` with NO running flag (mic released); back to LIVE → `(running)` again within 4 s.
- ✅ **[G2/G3] SpeedProvider**: pulled field log CSV shows `provider=gps` (raw GPS_PROVIDER subscription), monotonic `elapsedRealtimeNanos` at ~1.02 s intervals, `speedAccMs=0.500` populated — 211 fixes.
- ✅ DSP-table values in settings derive from the threaded rate (43.1 trames/s, Δf 21.5 Hz).

### Phase 2 deviations / pending

| ID | Item | Status |
|---|---|---|
| DEV-15 | Plan steps 2.1/2.2/2.4 landed as ONE commit (`acfc331`) | Splitting would have rewritten the same 150 lines twice through a never-shippable intermediate |
| DEV-16 | G5 (empty full-tracking GnssMeasurements registration) dropped during the 2.4 rewrite — decision D8's recommendation executed early | Trivially restorable; carrying dead battery cost through a rewrite made no sense. Flag to owner |
| DEV-17 | Full `MeasurementSession` class extraction deferred to plan 3.1/3.3 (as the plan itself schedules) | The testable core (engine reset matrix) IS covered by `LiveAnalysisEngineTest` |
| DEV-18 | Gate 2 run on the debug build (counters/logcat need it); release assembly verified by CI tasks + Phase 1 gate | — |
| DEV-19 | LeakCanary not installed → leak check replaced by mode-switch stress + zero-crash + onCleared review | Consider adding LeakCanary (debug-only dep) in Phase 5.1 |
| DEV-20 | No-lag drive verification of the estimator needs a real vehicle | **Field task**: drive with the debug build, compare Théo vs GPS speed response; tune α/β from the collected `field_logs` CSVs |
| DEV-21 | FFT-size stress = 6 changes (not the plan's 10) — dialog automation flakiness under emulator launcher ANRs | Invariant proven (produced==consumed); repeat at will on hardware |

### Notes for Phase 3

- `LiveAnalysisEngine.blendOrderEma` is the seed of the unified `OrderTrackingEngine` (plan 3.2) — the WAV sweep still has its own copy of the detection loop.
- `PlaybackController`, `SpeedProvider`, `CaptureEngine`, `LiveAnalysisEngine` are the extraction pattern Phase 3 continues (`:core` module split).
- The live history lists are still newest-first — plan 3.4's canonical-chronology change is the U9/U10 root fix.

---

## Phase 3 — Architecture extraction & persistence (COMPLETE)

### Steps executed (2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| 3.1 | **[A1]** `:core` module (pure Kotlin/JVM, **zero Android imports** — new armed gate in `ci/checks.sh`): FFTProcessor, LiveAnalysisEngine, AudioConfig, TimelineMapper, AlphaBetaSpeedEstimator, BiQuadFilter, KinematicsConfig+models, Telemetry, NumberParsing. Packages unchanged (no `:app` import churn). SmartTrackedOrder (Compose Color) stays app-side; FilterType split out of AudioFilter. 6 test classes + golden snapshot moved (51 JVM tests, seconds); SynthSignals via test fixtures. Kover wired with the plan-§1 90 % line rule. JTransforms → catalog, :core-only. CI covers both modules | `2013ee1` |
| 3.2 | **[A2, D7]** ONE `OrderTrackingEngine` in `:core` consumed by live path AND WAV sweep (the drifted ~130-line copies deleted); tracked-order search (3 copies) → `searchTrackedOrder`. Every threshold a named constant; deliberate D7 resolutions documented in-code: ±1 (per-frame) vs ±3 (interpolated sweep) radii kept with rationale; sweep center-bin projection unified to rounding. 7 engine tests (`a2_*`, `d7_*`, `l7_*`) | `89b8be3` |
| 3.3 | **[A1, C6-export]** MainViewModel (2,024 lines) **deleted**. `:core MeasurementSession` = shared state machine w/ registered transition hooks + resettables (single L7 enforcement point). `LiveViewModel` (281 code lines), `AnalyzerViewModel` (296, playback driving in `WavPlaybackCoordinator`), `ReportViewModel` (189) share it via `AppGraph`/factory. Pure computation to `:core`: `WavAnalysis` (STFT sweep, speed interpolation, cursor state, order sweep), `SmartPathTracker`, `FilterChain`; `LoadedWavData` moved. `export/` package: `PngExporter` renders on **Default** (was Main), PdfReportGenerator moved from utils/, PDF errors now hit the notice banner | `e479dbd` |
| 3.4 | **[U9, U10]** Canonical **chronological** history (newest LAST) in every mode; draw layer alone knows live scrolls right-to-left. Report-from-live no longer mirrored vs its own axis [U9]; PNG export column-mirror deleted, export time scale follows the actual data [U10]. 6 session tests | `8e612e4` |
| 3.5 | **[P1, P2, U2]** `SpectrogramImageProducer`: full-file bitmaps downsampled to ≤4096 columns (was ~13k), rendered on `Dispatchers.Default` via `produceState`, double-buffered so every data change repaints (kills the mutate-and-pray LaunchedEffect hack AND the DEV-14 "black until interaction" quirk). Spectra stored/crossed as **FloatArray** (DSP stays double; golden untouched); Paints hoisted from the 43 Hz draw loop | `74f9263` |
| 3.6 | **[S1, S2]** `SettingsStore` (DataStore): dB range, FFT size, freq/time windows, detector, full `KinematicsConfig` (`@Serializable`) restored at startup (before observers — defaults can't clobber), written back debounced; `LiveViewModel` follows `session.fftSize` from any writer. `TelemetryCodec`: sidecar **schema v2** (schemaVersion, appVersion, per-sample monotonic `elapsedRealtimeNanos`, altitude, `speedAccuracyMs`) via kotlinx-serialization; v1 sidecars still decode. `TelemetryData` + 2 fields fed by SpeedProvider | `37e3d1a` |
| — | Coverage wave for the 3.3-extracted pipelines (WavAnalysis, SmartPathTracker, FilterChain): **:core 67.7 % → 91.4 %**, `koverVerify` armed in CI (closes DEV-22) | `9b2ebee` |
| 3.7 | **[D2, D3, D6, D9, D7-display]** DSP polish, golden regenerated with analytic justification: `realForward` on preallocated buffers; shock detector rate-based (`258 dB/s` ≡ historical 6 dB @ 43 fps) and **first frame analyzed**; TTNR scale `[0,30]` with plain 0 = none, linear-power integration at honest τ = 52 ms (both processor EMA and engine 0.75/0.25 attack); sub-30 Hz masking → display layer (`AudioConfig.DISPLAY_MIN_FREQ_HZ`, producer/PNG/PDF); `searchTrackedOrder` parabolic amplitude (half-bin worst case +0.32 dB vs −1.42 dB, corrections > analytic 1.8 dB rejected as non-tonal). Pinned D3/D7/D9 tests replaced by fixed-behavior tests in the same commit | `8645105` |
| 3.8 | **[A4, A6, D5]** Purge completed: `CandidateHarmonicTracker`, `isFrequencyAllowed`, PDF's private `getJetColorInt` copy, the dead PDF `"\n"` branch, the unused `AutoResizedText(String,color)` overload, never-mutated `yOffsetAccumulator`, duplicate `vibratec_logo.png`. `ci/checks.sh` **resurrection gate**: purged symbols/files fail the build if they return. Coverage → **93.2 %** | `7199f14` |

### Gate 3 verification (2026-08-27, emulators NVH_API_37_compact + NVH_Pixel_7_API_37, debug)

- ✅ Post-3.3 smoke: boot, 30 s live session, `LivePipeline produced==consumed thread=nvh-dsp`, LIVE→WAV (mic **released**, appops finalized) → LIVE (mic `(running)`, restarts=2), report-mode round trip — zero FATAL.
- ✅ Post-3.4: synthetic 200→4000 Hz sweep WAV loads via SAF; renders **rising left→right** on screen; frozen-view PNG export shows the same orientation (old code mirrored it) with the −10 s→0 s axis spanning the real file duration; export no longer freezes the UI.
- ✅ **5-min WAV memory gate**: 26 MB file → **TOTAL PSS 242 MB / Java heap 161 MB** — under the 256 MB plan-§1 bound (old layout: 211 MB double spectra + 52 MB full-width bitmap alone). Rendered immediately with the correct 0–300 s axis. *Load < 3 s to be timed on real hardware (emulator FFT speed unrepresentative).*
- ✅ **[S1] Process death**: FFT 4096 + Min −94 dBFS set → `am force-stop` → relaunch shows "Min −94" restored and the engine running at the restored size (also survived a full emulator reboot).
- ✅ **Gate-1 regression (48 kHz)**: reference tone renders the 48 kHz grid (7987/5990/3993/1996), tone exactly ON the 3993 gridline — C1 intact through FloatArray/chronological/producer refactors. Stereo covered by `c2_*` unit tests + original Gate 1 (device re-check skipped: tap automation flakiness, format handling unchanged since).
- ✅ **[S2] Record→save→reload round trip** (Pixel 7 AVD, real mic): 8 s recording saved; sidecar on device is **schema v2** (`schemaVersion: 2`, `appVersion: 13.2.0`, `captureSource`, 347 samples 1:1 with frames, per-sample monotonic `elapsedRealtimeNanos` + `speedAccuracyMs`); picker lists it; reload restores audio (real spectral content, 00:08, chronological) + decoded telemetry.
- ✅ Post-3.7 DSP smoke: sweep renders correctly in ABS and TTNR (crisp emergence trace on the new 0-based scale), zero FATAL.
- ⚠️ **Emulator-infrastructure ANRs** (NOT app defects — evidence): 4 ANRs on `NVH_API_37_compact` under swiftshader; traces show main waiting in `HardwareRenderer.setStopped` on the RenderThread, which is blocked in `qemu_pipe_read` inside `eglMakeCurrent` (the emulator's guest→host GPU transport; 45 s kernel time on that thread), with the system launcher ANRing simultaneously. App threads healthy in every trace; zero FATAL all night. Same flows run clean on `NVH_Pixel_7_API_37` with host GPU. **Follow-up: repeat Gate-3 checklist on a physical phone (as every gate ultimately requires).**

### Phase-3 deviations

| ID | Deviation | Rationale |
|---|---|---|
| DEV-22 | Kover 90 % gate wired but `koverVerify` not yet in CI (80.2 % at 3.1, 87.9 % after 3.2) | Gap = dead code dying in 3.8 + data-class boilerplate; arm when green within the phase |
| DEV-23 | A4 dead members (`toggleRecording`/`isRecording`, `toggleDrawingMode`) dropped during 3.3 instead of 3.8 | They were VM members; carrying them through the decomposition made no sense |
| DEV-24 | Pure dialog visibility (audio-mode menu, WAV/video pickers) became local Compose state | It was never ViewModel state; survives nothing it needs to survive |
| DEV-25 | Session (AppGraph) is process-scoped: measurement state now survives activity finish→relaunch | Direction of S1; VMs re-register hooks via unregister-on-clear handles |
| DEV-26 | `session.clearEmergenceReport()` also clears `latestTTNRSpectrum`/tags (slightly broader than the old method) | One reset path [L7]; display-only nuance mid-sweep |
| DEV-27 | Detector beacons in WAV mode now read the LAST frame (was frame 0 after load) | Chronological `.last()` = "most recent frame" semantics; live behavior unchanged |
| DEV-28 | Kover 90 % gate armed mid-phase (after `9b2ebee`), not at 3.1 | Coverage had to be earned first: 80→87.9→67.7 (3.3 extractions landed untested)→91.4→93.2 |
| DEV-29 | 3.7's D2 also clamps the detection floor to 0 dB (old −3..0 dB window dropped) | Analytically invisible: sub-0 dB values were below the 1.0 display-black threshold, masked by the old −3 output gate, and rejected by the engine's `>0` fold — no consumer could observe them |
| DEV-30 | D9 correction lives in `searchTrackedOrder` (the readout feeding reports/graphs), not in raw FFT bins | Raw bins keep physical scalloping by design — documented in `d9_rawBinReadout_hasPhysicalScalloping` |
| DEV-31 | Gate-3 "5-min load < 3 s" and LeakCanary check deferred to hardware / plan 5.1 | Emulator FFT throughput unrepresentative; LeakCanary was already deferred by DEV-19 |
| DEV-32 | `updateKinematicsConfig`/`updateSelectedTrackedOrder` live on AnalyzerViewModel (they own the WAV re-sweep); live path reads config per frame from the session | One owner for the recalc choreography; no cross-VM callback needed |

### Phase-3 exit-gate check (plan §1 Architecture row)

- ✅ `MainViewModel` < 300 lines → **deleted entirely**; largest VM is 296 code lines.
- ✅ Pure-Kotlin `:core` with zero Android imports → armed `ci/checks.sh` gate.
- ✅ One `OrderTrackingEngine` for live + sweep.
- ✅ Dead-code purge + CI resurrection gate; `:core` coverage **93.2 %** with `koverVerify` in CI.

### Notes for Phase 4

- The permission dead-end (U1, plan 4.1) is untouched: `AppNavigation` still parks on "En attente des permissions…" if any permission is denied.
- `session.analysisNotice` is ready to become the SnackbarHost feed (plan 4.7).
- Emergence Report dialog is still unreachable (D6 restore is plan 4.6) — `session.clearEmergenceReport()` is already wired for it.
- The 30 s recording cap constant now lives in `LiveViewModel.MAX_RECORDING_SEC` (S5 single-sourcing partially done; dialog text still hard-codes "00:30").
- PngExporter/PdfReportGenerator share `AudioConfig.DISPLAY_MIN_FREQ_HZ`; 4.5's PDF stamps (date/version/capture source) have `TelemetryCodec`'s appVersion pattern to follow.

---

## Phase 4 — UX, reporting & trust surfaces (COMPLETE)

### Steps executed (2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| 4.3 | **[U5, D4]** Fixed dark instrument theme. `theme/Color.kt` becomes the ONE colour source (M3 scheme + instrument semantics: GNSS status, source modes, emergence severity, canvas scrims, order traces); ~150 scattered hex literals across 17 UI files map onto it; `themes.xml` gets a dark parent + `windowBackground` (kills the white launch flash). Dynamic colour and the system light theme are deliberately dropped. Every semantic hue is split **container** (dark, carries a light label) / **accent** (light, for text on dark) — one value could not do both. Order-trace palette shared with the PDF as ARGB ints [U7 prep]. `PaletteContrastTest` computes WCAG ratios and **found five real defects while being written** (activeContainer 4.34:1, disabledContent 4.19:1, modeLive 4.41:1, recording 4.21:1, amber-on-white-page 1.41:1) — all fixed by changing colours, none by relaxing a bar. New armed gate: no `Color(0x…)` outside theme/Color.kt | `7c42e74` |
| 4.1 | **[U1]** Per-permission degradation. Mic denial is no longer fatal: rationale + "Autoriser" → "Ouvrir les réglages" once Android will not re-ask + a third way through ("Continuer sans micro"), so a recorded session stays analysable. LIVE entry visibly disabled and *says why* when tapped. Without precise location the GNSS LED is replaced by an explicit chip + banner (a red "Signal Perdu" would blame the sky for a permission); coarse-only stays non-metrological [GPS-12]. `LiveViewModel.applyResourcePolicy(mode)` is the single place deciding whether mic/GNSS may run: LIVE mode AND the grant, re-applied on every resume | `4f30344` |
| 4.8 | **[U6, V2, C12, D7]** YouTube deleted — it loaded user URLs into a JS-enabled WebView and analysed nothing; with it went the app's only script surface and (single-option) VideoSelectionDialog. Extractor rewritten: output drained past the input EOS (the tail of every video was being dropped), growable ShortArray instead of ~13 M boxed Shorts, `INFO_OUTPUT_FORMAT_CHANGED` honoured (a resampling decoder was putting the analysis on the wrong frequency grid — C1 through another door), float PCM handled, typed failures. Real extraction progress. Picture re-seeks on drift > 250 ms, not only on play/pause [U6] | `1761e08` |
| 4.5 / 4.6 | **[U7, D1, A4, D5, D6]** Report traceability block: date/time, build, source (with the mic route actually granted [C8]), analysis parameters, and the speed line — causal vs "lissée (RTS)" with the order-confidence k — closing **DEV-43**. Metric renamed everywhere a human reads it to "Indice d'émergence NVH" with an on-page footnote stating it is NOT ECMA-74 conformant [D1/D5]. Order colours unified screen↔PDF. Comment line breaks preserved [A4]. Report header shows `getEffectiveV1000()` [U7]. Emergence Report entry point restored in the GMPe banner with its entry count [D6] | `526b9b1` |
| 4.2 | **[U3, U4]** `PlotDimens` (dp/sp, per density+fontScale) + `:core PlotGeometry` (pure, tested). Both `pointerInput` blocks and the draw pass read the same geometry, so touch and paint cannot drift. Every overlay — playhead, beacons, harmonic tags, H1 — now places itself through the same data→pixel transform the bitmap crop implies, and is clipped when scrolled out of the viewport instead of being pinned to an edge where it labelled the wrong frequency | `a0a9d53` |
| 4.7 / 4.9 | **[V3, U2, U8, P3, P4]** `DiagnosticLog`: local, rotating (2 × 256 kB), own writer thread, disables itself after one failure; every notice is written to it; shared ONLY through an explicit user action via a non-exported FileProvider whose single exposed path is the log directory. KPI throttle moved out of composition into the ViewModel (`displayedOrderDbFS`, sampled) [U2]. Platform SplashScreen replaces the fixed `delay(2000)` [U8]. `windowSoftInputMode` moved onto the activity; orientation lock dropped **and** the layout made to adapt (panes stacked in portrait, side by side in landscape) so removing it is honest. `collectAsState()` → `collectAsStateWithLifecycle()` at all 55 sites [P4] | `daf17fa` |
| 4.4 | **[§12]** ~290 strings externalised (French default), including the notices produced in the ViewModels and data layer; `WavReadResult` refactored to typed reasons so the RIFF walker holds no user text and the test asserts `BITS_UNSUPPORTED` + "24" instead of a French sentence. lint promotes `HardcodedText`/`ContentDescription`/`SetTextI18n`/`StringFormatMatches` to errors; **the new ci/checks.sh Compose-text gate found nine strings the manual sweep missed**. TalkBack labels on every emoji-as-icon control and a spoken summary for the spectrogram canvas (which had no semantics at all). 48 dp targets. GNSS LED and criticality badge gain a SHAPE (●/▲/✕) so colour is never the only channel | `d4ce71e` |

Unit tests: **:app 46 + :core 158**, all green; `:core` coverage ≥ 90 %
(`koverVerify` in CI); lint 0 errors with the four promoted checks and still
**no baseline**; minified release builds. Baselines: ktlint 3,081 → **497**,
detekt 845 → **500**.

### Gate 4 verification (2026-08-27, emulator NVH_Pixel_7_API_37, debug)

- ✅ **[U1] Permission-denial matrix.** Mic revoked + denied twice (USER_FIXED):
  the rationale screen appears with the privacy statement, the primary button
  becomes **"Ouvrir les réglages"**, and **"Continuer sans micro (analyse de
  fichiers)"** enters analyzer-only mode — screenshots. In that mode the mic
  appops entry shows `duration=0` (**not running**), speed reads `--`, and the
  source menu's LIVE entry is greyed **and explains itself** when tapped
  ("🎙️ Mesure en direct indisponible : autorisation micro refusée…").
  Location denied → GNSS chip + banner instead of a red "Signal Perdu".
- ✅ **[U5/D4] Fixed dark theme** end to end: no white launch flash, no
  wallpaper tint, instrument palette on every surface.
- ✅ **[U8] Platform splash** (icon on the dark window) replaces the 2 s wait;
  screenshot captured mid-launch.
- ✅ **[U8] Landscape**: the two panes render **side by side**, the
  spectrogram keeping full height; rotation with no crash.
- ✅ **[§12] Font scale 1.3**: canvas axis text **grows** (proving the sp
  conversion — raw pixels would not have), buttons grow, nothing clips, zero
  FATAL. Two defects it exposed were fixed on the spot: the top-bar title
  wrapped under the logo (now truncates) and the graph's two axis labels could
  overlap in a short pane (floor label dropped below a clearance threshold).
- ✅ **[D5] Metric renamed on screen**: "Émergence NVH" / "Émergence" chips,
  "Dynamique : Min 0 | Max +20 dB (émergence NVH)" — no "TTNR" anywhere a user
  reads.
- ✅ **[§12] Colour is not the only channel**: the GNSS LED shows ● / ✕ with
  its words in both states (screenshots).
- ✅ **[V3] Diagnostics**: the Info dialog reports "480 o" and the on-device
  file contains the session lines **and the exact mic-denial notice triggered
  during the test** — a field failure now leaves a sendable trace.
- ✅ **Phase 1–3 / GPS invariants intact after the rewrite**:
  `LivePipeline produced=6656 consumed=6656 restarts=1 thread=nvh-dsp`, mic
  `(running)` in LIVE, GNSS `ProviderRequest[@0, HIGH_ACCURACY, WorkSource
  com.example.nvhspectro]`, zero FATAL/ANR across the whole session.
- ⏳ **Hardware follow-ups**: a real TalkBack walkthrough (the emulator has no
  screen reader configured — semantics are set and lint's ContentDescription
  is armed, but "TalkBack pass on every screen" needs a device); PDF export
  visually checked on device (closes DEV-10 as well); tablet/large-screen
  layout (landscape phone verified, a real tablet is a different width class);
  small-screen check.

### Phase-4 deviations

| ID | Deviation | Rationale |
|---|---|---|
| DEV-46 | 4.3 also carries a `ktlint --format` reflow of every file it touched | The palette edits reached nearly all UI files and AGENTS.md requires style checks to run last, after any format. Splitting would have meant reformatting the same lines twice. Baselines regenerated in the same commit; ktlint 3,081 → 747 there |
| DEV-47 | `Timber` was NOT added (plan 4.7 names it); `DiagnosticLog` is ~110 lines with no new dependency | A Timber `Tree` would still need the same file-writing code; the finding [V3] is "no logging framework, no user-facing error surface", both closed. Consistent with DEV-39's dependency minimalism |
| DEV-48 | The notice **banner** was kept as the error surface; no SnackbarHost (plan 4.7 names one) | A Snackbar auto-dismisses. For a field instrument a persistent, dismissible banner is strictly better, and Phase 1 already made `analysisNotice` the single message channel. Every notice now also reaches the diagnostic log |
| DEV-49 | lint's `HardcodedText` is armed but proves nothing here | It only inspects XML layouts; this app is Compose-only. The equivalent gate is the new ci/checks.sh Compose-text check — which is what actually caught nine misses |
| DEV-50 | `WavReadResult` changed from carrying `message: String` to `reason: WavReadError + detail` | Required to externalise the import errors; it also makes the test assert a typed reason instead of matching French prose that localisation would break |
| DEV-51 | Emergence-report badge kept its binary ≥ 6 dB rule rather than adopting the 5-step severity ramp used by the 2D graph | A theme/a11y commit must not move a threshold an operator reads as "critical". Unifying the ramp is a measurement decision for the owner |
| DEV-52 | Gate 4 run on emulator API 37 only | Same constraint as DEV-7/11/38. TalkBack, tablet and small-screen checks flagged above |

### Notes for Phase 5

- `doc/ARCHITECTURE_AND_DSP_METHODS.md` is still v10-stale and the README still
  claims accelerometer-derived acceleration — both are plan 5.4's step, now the
  last documentation debt.
- Decision **D9** (`applicationId` still `com.example.nvhspectro`) and **D1**
  (git-history purge) remain open; both are plan 5.5/0.1 items.
- LeakCanary is still not installed (DEV-19/DEV-31) — plan 5.1.
- The 5-min WAV load-time budget and the macrobenchmark need real hardware.
- `ci/checks.sh` now carries eight armed gates (binaries, patch scripts,
  gradlew bit, version single-sourcing, sample rate, `:core` purity, colour
  literals, Compose text, WebView/INTERNET, dead-code resurrection).

---

## Supplemental GNSS/GPS quality audit (2026-08-26) → execution (2026-08-27)

- `audit-gps.md` records a focused measurement-quality audit of the current
  internal-GNSS speed chain, with findings `GPS-01` through `GPS-15`.
- `plan-gps.md` defines corrective phases `GPS-0` through `GPS-5`, plus the
  optional raw-GNSS R&D phase `GPS-6`, with traceability, tests and field gates.

### Phase GPS-0 — Characterization & contracts (COMPLETE, 2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| GPS-0.1 | Pure contracts in `:core/data`: `GnssSpeedSample` (fix + callback BOOTTIME, σv nullable — never 0-as-unknown), `SpeedEstimate` (age, validity, nullable sigmas), `EstimateValidity`, `SampleRejection`, `SpeedSampleSource` | `2b911d3` |
| GPS-0.2 | `SpeedEstimator` interface (`update(sample)`/`estimateAt(time)`/`reset`); `AlphaBetaSpeedEstimator` implements it with **unchanged numerics** (equivalence test); `estimateAt` adds DESCRIPTIVE validity nothing consumed yet — Gate GPS-0's "zero LIVE output change" | `2b911d3` |
| GPS-0.3 | Pinned defect tests `pinned_gps01/02/04/06/08` froze the frozen-stale-speed, unweighted-σ, no-covariance, incoherent-second-outlier and no-reset-on-transition behaviors | `2b911d3` |
| GPS-0.4 | `FieldTraceV2` codec (pure, round-trip-tested, empty-field absence — no NaN sentinels) + `FieldLocationLogger` schema v2: callback delivery time, estimator state/validity/rejection per fix, anonymized install UUID + device model in the header | `2b911d3` (+ header-spacing fix) |
| GPS-0.5 | Units/time bases documented in every touched class header | `2b911d3` |

Gate GPS-0: ✅ all tests green (incl. round-trip), ✅ LIVE outputs unchanged
(equivalence test `gps0_sampleUpdate_matchesLegacyNumericBehavior`), ✅ no
numeric sentinel in the new contracts. (`TelemetryData`'s legacy 0-as-unknown
fields remain until the GPS-4.3 schema work.)

### Phase GPS-1 — P0 integrity & audio time (COMPLETE, 2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| GPS-1.1 | **[GPS-01, GPS-08, GPS-09]** `GnssSpeedSession` (:core, pure): `kinematicSpeedMps()` is the ONLY speed the kinematic chain may consume — null once INVALID (no fix / beyond the 2 s horizon), never a frozen number. Explicit rule: PREDICTED allowed in horizon; DEGRADED allowed until GPS-2 (pre-API-26 has no σv). `SpeedProvider.start()/stop()` reset the session — LIVE re-entry serves nothing before the first fresh fix. `LiveViewModel` suspends tracked-order search + harmonic detection on INVALID. KPI card: GPS/Théo show "--" on NONE/INVALID | `6985bb2` |
| GPS-1.3 | **[GPS-12]** Qualification before the estimator: non-finite/negative speeds, mock fixes (rejected by default, config-allowed for test builds), cached/backlogged fixes (delivery age > 2 s) → typed `SampleRejection`s; σv retained, never substituted by horizontal accuracy | `6985bb2` |
| GPS-1.2 | **[GPS-03]** `CapturedAudioFrame` + pure `AudioFrameClock` (:core): every window carries first/center BOOTTIME; `AudioRepository` anchors on `AudioRecord.getTimestamp(TIMEBASE_BOOTTIME)` (refreshed ~0.7 s, never downgraded), falls back to read-completion clock marked ESTIMATED (logged once); `LiveViewModel` evaluates speed at `frame.centerTimeNanos` via `SpeedProvider.telemetryAt()` | `8219d31` |

Tests: plan §Tests GPS-1 names implemented (`gps01_*`, `gps03_*`, `gps08_*`,
`gps09_*`, `gps12_mockFix_*`, `gps13_*`); pinned_gps01/08 replaced by
fixed-behavior tests in the fixing commits; GPS-02/04/06 pins remain for
GPS-2. :core coverage stayed ≥ 90 % (94.1 % at GPS-0).

### Gate GPS-1 verification (2026-08-27, emulator NVH_Pixel_7_API_37, debug)

- ✅ **Simulated GNSS loss** (location off mid-session): LED → red "Signal
  Perdu", speed → **"-- km/h"** (screenshots); no frozen number anywhere.
- ✅ **LIVE exit → re-entry** (WAV mode round trip): immediately after
  re-entry the card shows "Signal Perdu" + "--" — **no old speed before the
  first fresh fix** [GPS-08]; first new fix restores green "Signal OK" +
  numeric speed within ~5 s. `dumpsys location`: gps `ProviderRequest[OFF]`
  in WAV mode, `[@0, HIGH_ACCURACY, WorkSource com.example.nvhspectro]` in
  LIVE; mic appops finalized in WAV, `(running)` in LIVE [C7 intact].
- ✅ **Zero vs unavailable distinguished**: numeric "0.0 km/h" with a fix
  present vs "--" without (Gate GPS-1 UI item).
- ✅ **Pipeline health**: `LivePipeline produced==consumed thread=nvh-dsp`
  throughout; **zero FATAL**; no "AudioTimestamp unavailable" warning — the
  HARDWARE timestamp path is active on this AVD.
- ✅ **v2 drive trace on device**: header `# nvh-field-trace v2 install=<uuid>
  model=sdk_gphone64_arm64`; rows carry σv=0.5, validity=VALID, empty (null)
  estimator-σ and rejection columns; fixes at sub-second cadence with
  callback−fix delivery latency ~10–25 ms.
- ⏳ **Hardware follow-ups**: API 24 and API 31 device runs (no such AVDs
  here); artificial DSP-backlog pairing check and ESTIMATED-fallback path need
  a physical phone; strict Gate GPS-1 device matrix per plan §5.

### GPS deviations

| ID | Deviation | Rationale |
|---|---|---|
| DEV-33 | `SampleRejection.NAN_SPEED` renamed `NON_FINITE_SPEED` in GPS-1 (covers ±Inf) | Introduced only one commit earlier; never in any shipped trace |
| DEV-34 | Validity thresholds (2 s horizon, 350 ms VALID freshness, 2 s delivery age) are named PROVISIONAL constants | Plan §2: thresholds calibrated on data at Gate GPS-5; GPS-2 replaces freshness with covariance |
| DEV-35 | `TelemetryData.speedValidity` added in-memory only; sidecar export deferred to GPS-4.3 (schema v3) | Changing the sidecar schema piecemeal would burn a version number per phase |
| DEV-36 | TelemetryGraph still plots the diagnostic theoretical speed during INVALID stretches | Full surface alignment (screen/telemetry/PDF same value+status) is GPS-4.3's step by design |
| DEV-37 | `gps12_coarsePermission_disablesMetrologicalSpeed` not implemented as a unit test | Permission state is Android-side; coarse-only permission already yields no GPS_PROVIDER fixes → INVALID (fail-safe). Full provider/permission state handling is GPS-3.2 |
| DEV-38 | Gate GPS-1 run on emulator API 37 only | Same constraint as DEV-7/DEV-11; physical-device matrix flagged above |

### Phase GPS-2 — Probabilistic estimator (COMPLETE, 2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| GPS-2.1 | **[GPS-02, GPS-04, GPS-05]** `KalmanSpeedEstimator` (:core, Double core): x=[v,a], variable dt, R=max(σv,floor)², white-jerk Q(dt), Joseph-form symmetric update. σv finally weights every correction; estimates carry real σv/σa; gains derive from covariances. σv-less fixes get the conservative 2 m/s default → DEGRADED. Validity keys on predicted σv (INVALID > 3, DEGRADED > 1.5 m/s) + the 2 s hard horizon. q=0.5 picked from simulated σ-growth profiles (σv ≈ 1.0 m/s at 1 s, 1.5 at 1.5 s on a healthy 1 Hz stream) | `0eacd04` |
| GPS-2.2 | **[GPS-06]** NIS gate (χ²(1)=9) + candidate reacquisition: rejected fixes leave the state at its last accepted epoch (uncertainty grows normally); reacquisition needs two MUTUALLY COHERENT rejected fixes (implied accel ≤ 12 m/s²), a 4-rejection safety valve, or a >5 s gap re-seed. 45-then-28 m/s multipath pair now rejected outright; NIS recorded per fix (`lastNis`) and logged in the trace (`nis` column; legacy 18-field rows still parse) | `0eacd04` |
| GPS-2.3 | **[GPS-14 estimator-side]** Stationary state with 0.25/0.6 m/s hysteresis publishes an honest 0 near standstill (σ stays truthful); no internal clamp substitutes for validity. σa is now available for GPS-4.3's display gating | `0eacd04` |

`GnssSpeedSession` defaults to the Kalman; the α-β stays as the fixed-gain
A/B baseline for replay tuning (its tests document that role). Pinned
gps02/04/06 replaced by fixed-behavior tests in `KalmanSpeedEstimatorTest`
(plan §Tests GPS-2 matrix: 1/5/10 Hz, ramps ±1, −6 m/s² braking via coherent
reacquisition, stop-and-go, irregular dt, σ 0.05–5 m/s, losses 1–60 s,
no-NaN/PSD invariants, less-noise-than-raw + no-delay gate). 141 :core tests.

### Gate GPS-2 verification (2026-08-27)

- ✅ Unit gates: high-σ fixes weigh less than precise ones; no blind second
  outlier; uncertainty grows during loss; filtered RMSE < raw with zero
  steady-state lag on ramps (2nd-order model) — all in `KalmanSpeedEstimatorTest`.
- ✅ Emulator smoke (Pixel 7 AVD, debug): live chain runs on the Kalman, GPS
  card green, zero FATAL; the v2 trace now carries **estSpeedSigmaMps ≈ 0.452**
  — matching the simulated steady-state σ(0)=0.45 for q=0.5/σv=0.5 exactly —
  plus per-fix `nis` and VALID validity.
- ✅ Parameters recorded as PROVISIONAL (named `Config` fields) until Gate GPS-5.
- ⏳ A/B traces on two chipsets + drive-log replay tuning → field tasks (GPS-5).

### Phase GPS-3 — Acquisition & diagnostics (COMPLETE, 2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| GPS-3.1 | Callbacks on the dedicated `nvh-gnss` HandlerThread (never main); API 31+ subscribes GPS_PROVIDER via an explicit HIGH_ACCURACY / zero-interval / unbatched `LocationRequest` (legacy overload < 31); listener registered even while the provider is disabled | `ba2af36` |
| GPS-3.2 | **[GPS-07, GPS-12]** Provider enable/disable handled mid-session: disable resets the speed session IMMEDIATELY (no ghost values) + user notice; fallback = LocationManager FUSED/NETWORK, INFORMATION_ONLY (never feeds the estimator without GNSS provenance) — **play-services-location dependency deleted** [B4 bonus]; approximate-only permission posts an explicit notice | `ba2af36` |
| GPS-3.3 | `GnssDiagnosticsMonitor` snapshots GnssStatus (visible/used sats, mean used C/N0, constellations, L5-band) into 5 new trace columns — diagnostics only, never a σ substitute | `ba2af36` |
| GPS-3.4 | **[GPS-11]** Full-tracking (API 31+) registered only during an active measurement session AND only when the A/B switch asks (constructor param, default OFF until Gate GPS-5); minimal consumption proves cadence; state in trace header | `ba2af36` |
| GPS-3.5 | Capability matrix (`# caps sdk=… gnssYear=… gnssHw=… rawMeasurements=… fullTracking=…`) as a second trace-header line, round-trip-tested; 18/19-column legacy rows still parse | `ba2af36` |

### Gate GPS-3 verification (2026-08-27, emulator NVH_Pixel_7_API_37, debug)

- ✅ Metrological chain receives only GPS_PROVIDER (`ProviderRequest[@0,
  HIGH_ACCURACY, WorkSource com.example.nvhspectro]` — the explicit API-31+
  request path live on this AVD); fixes flow, Kalman σ in trace, zero FATAL.
- ✅ **Mid-session GPS toggle**: disable → banner "📡 GPS désactivé — vitesse
  GNSS indisponible" + card flips to "Signal Perdu"/"--" immediately (reset,
  not the 5 s LED timeout) — no ghost value (screenshot).
- ✅ Caps header + GnssStatus columns in the on-device trace (`# caps sdk=37
  … gnssYear=2023 gnssHw=Android Studio Emulator GPS rawMeasurements=true`;
  satsVisible=6 per row).
- ✅ **Zero GNSS resources outside LIVE**: in WAV mode `ProviderRequest[OFF]`
  and no nvhspectro GNSS listeners in `dumpsys location`.
- ⏳ Hardware follow-ups: approximate-permission path blocked behind the U1
  permission dead-end until plan 4.1 (SecurityException notice is code-
  complete); full-tracking A/B needs two chipsets + endurance (GPS-5);
  real GnssStatus content (used sats, C/N0) needs a physical phone.

| ID | Deviation | Rationale |
|---|---|---|
| DEV-39 | Fused fallback = LocationManager FUSED_PROVIDER (API 31+) / NETWORK_PROVIDER, not the Google Play fused client; play-services-location removed | Same INFORMATION_ONLY role, one dependency less [B4]; gms client added nothing the framework API lacks here |
| DEV-40 | Full-tracking A/B switch is a constructor parameter (default OFF), not yet a debug UI toggle | The GPS-5 campaign flips it in a debug build; a settings surface for it belongs to Phase 4 UI work if wanted |
| DEV-41 | **First remote CI run failed on detekt** (`updateSettings` LongParameterList): the GPS-3 session ran `ktlint --format` on LiveViewModel AFTER its last local detekt pass, so the reformatted signature no longer matched its baselined entry | Fixed by baseline re-sync `a6b8b46` (net −15 entries, nothing new baselined). Rule added to AGENTS.md: style checks run LAST, after any format — format → ktlint → detekt → tests → checks |

### Phase GPS-4 — Propagation to orders & deferred processing (COMPLETE, 2026-08-27)

| Step | What was done | Commit |
|---|---|---|
| GPS-4.1 | **[GPS-10]** `OrderSearchPolicy` (:core): the plan's error budget (σrpm = σv·1000/V1000, σf(Hn) = n·σrpm/60) with tests reproducing the audit arithmetic exactly (180 rpm / 54 Hz at the worked example). V1000's own uncertainty documented as characterized separately at plan 5.3 | `2f79b0f` |
| GPS-4.2 | **[GPS-10]** Dynamic search half-width k·σf + Δf (k = 2, recorded in exports), BOUNDED by half the adjacent-order spacing — beyond it the tracked order SUSPENDS ("Non identifiable" KPI, per-sample `trackedOrderIdentifiable`). The bound gates the UNCERTAINTY term only (Δf is grid resolution, not a speed problem). σ-unknown (α-β, pre-v3 sidecars) falls back to the historical ±1/±3-bin radii — legacy analyses byte-identical (existing tests pass unchanged). Consumers: live readout, WAV cursor, WAV sweep — one `withTrackedOrderReadout` helper | `2f79b0f` |
| GPS-4.3 | Sidecar **schema v3**: per-sample estimated speed + 1-σ (null = unknown, never 0) + validity + paired audio BOOTTIME; per-document estimator identity with FULL parameter set, capture speed status ("causale"), order-confidence k. v1 AND v2 decode as DEGRADED σ-null ("incertitude inconnue") — also fixes a latent GPS-1 regression (v2 decoded as INVALID → old recordings would have shown "--"). Migration tests v1/v2/v3 | `ca2c153` |
| GPS-4.4 | `RtsSpeedSmoother` (:core): forward Kalman (same Config as LIVE) + backward RTS; offline outliers drop; >5 s gaps split segments. `SpeedReconstruction`: dedups the recorder's frame-rate fix copies (never recycling extrapolated speeds as truth), smooths, evaluates at each sample's audio time; v1 falls back to interpolation. Status label ("lissée (RTS)" / "brute (interpolée)") on AnalyzerViewModel + load notice; PDF stamp is plan 4.5's | `9f35954` |

Replay tests prove the plan's comparison: smoothed RMSE < causal RMSE < raw
RMSE; σ_smoothed ≤ σ_filtered at interior knots; ramp-onset error smaller
smoothed than causal (the future is used); no bleed across a 60 s hole.

### Gate GPS-4 verification (2026-08-27, emulator NVH_Pixel_7_API_37, debug)

- ✅ **Record → save → reload round trip**: 9 s recording saved; on-device
  sidecar is **schema v3** (`speedEstimator: "kalman-va/1 Config(jerkPsd=0.5,
  …)"` — the full parameter set, `speedStatus: "causale"`,
  `orderConfidenceK: 2.0`, 389 samples with per-sample `estSpeedSigmaKmh`
  breathing 1.63↔3.68 km/h between fixes, `validity` PREDICTED↔VALID,
  monotonic `audioTimeNanos`); picker lists it; **reload posts "🛰️ Vitesse
  GNSS : lissée (RTS)"** — the RTS path ran over the real sidecar. Zero FATAL.
- ✅ Confidence band contains the true line / suspension when ambiguous /
  legacy fallback — unit-proven (OrderSearchPolicyTest incl. a true line at
  +2σ captured where the old ±1-bin search missed it; WAV suspension test).
- ⏳ "Non identifiable" KPI needs GMPe + a moving vehicle to show live —
  drive-test task (GPS-5). PDF speed-status stamp + full screen/telemetry/PDF
  alignment land with plan 4.5 (see DEV-43).

| ID | Deviation | Rationale |
|---|---|---|
| DEV-42 | Identifiability bound compares k·σf (not k·σf + Δf) against half the order spacing | Δf is display resolution, present regardless of speed quality; when Δf ≫ h1 the FFT-size choice owns the problem, not the GNSS chain |
| DEV-43 | Gate GPS-4's "écran/télémétrie/PDF même statut" is PARTIAL: screen+telemetry share one TelemetryData; the PDF stamp (status, k, estimator) is plan 4.5's reporting-integrity step | The reporting surface is being rebuilt wholesale in 4.5; stamping the old PDF twice would be churn |
| DEV-44 | Sidecar went v2 → v3 in one step (σ, validity, audio times, estimator, status together) | One version bump instead of one per phase (per DEV-35's intent) |
| DEV-45 | Suspension visual not exercised on emulator | Needs kinematics enabled + real motion; unit-covered on both live and WAV paths |

**Still open (next: GPS-5):** field validation campaign — 3 phones vs ground
truth, biais/MAE/RMSE/P95 + lag by correlation, σ-coverage check,
full-tracking A/B decision, PARAMETER FREEZE (every `Config` constant is
provisional), `doc/VALIDATION.md`. Optional GPS-6 (raw pseudorange-rate R&D)
only after GPS-5 shows remaining need.
