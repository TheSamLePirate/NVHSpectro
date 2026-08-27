# AGENTS.md — Operating rules for the NVH Spectro AAA refactor

You are working on a **professional NVH (noise/vibration/harshness) measurement
instrument** used in real vehicle testing at Vibratec. The numbers this app
displays and prints on customer PDFs must be *right*. Measurement and kinematic
precision outrank UI polish, velocity, and cleverness — in that order.

## 1. Read before you touch anything

Documents that govern all work on this codebase:

1. **`V13.1-audit.md`** — 74 findings (C1…C17, D1…D9, A, L, P, U, S, V, G, B
   series) with file:line evidence. **`audit-gps.md`** — GPS-01…GPS-15 on the
   speed chain. Both are frozen historical baselines; finding IDs are the
   project's shared vocabulary — use them in commits, tests, and comments.
2. **`V13.1-AAA-plan.md`** and **`plan-gps.md`** — the phase plans (0…5,
   GPS-0…GPS-6). Every change must belong to a plan step. Work in phase order;
   P0/P1-class correctness findings always preempt features.
3. **`AAA-TRACKING.md`** — live execution log: what is done, commit hashes,
   deviations, gate evidence. **Update it in the same session as the work it
   describes.** `history-understanding.md` explains how the defects got here —
   read it to avoid repeating the process failures it documents.
4. **`doc/ARCHITECTURE_AND_DSP_METHODS.md`** — what the code actually does now,
   including its limits and the speed→order error budget. Kept current, not
   stale: update it in the same session as any architecture, DSP-constant or
   limit-of-use change.

Current state: **Phases 0–4 complete, GPS-0–GPS-4 complete** (branch
`aaa/phase0`). Next: **Phase 5** (performance, endurance, metrological field
validation, release engineering) and **GPS-5** (the field campaign that freezes
every provisional estimator constant). Gates 0–4 and GPS-0–4 passed on
emulator; the per-phase hardware follow-ups are listed in the tracking file.

## 2. Build, test, verify — the commands that work on this machine

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
# local.properties: sdk.dir=/opt/homebrew/share/android-commandlinetools

./gradlew :core:test :app:testDebugUnitTest   # 202 tests incl. the golden DSP snapshot
./gradlew :core:koverVerify                   # :core line coverage ≥ 90 % — armed in CI
./gradlew :app:lintDebug                      # zero errors, NO baseline — keep it that way
./gradlew :app:assembleDebug :app:assembleRelease   # release = minified; must always build
./ci/checks.sh                                # repo/correctness gates (same script CI runs)

# Style (pinned CLIs; CI downloads the same versions — see .github/workflows/ci.yml):
.tools/ktlint --baseline=config/ktlint-baseline.xml \
  "app/src/main/**/*.kt" "app/src/test/**/*.kt" "core/src/**/*.kt"
.tools/detekt-cli-1.23.8/bin/detekt-cli --input app/src,core/src --build-upon-default-config \
  --config config/detekt/detekt.yml --baseline config/detekt-baseline.xml
```

Arm every gate the way CI does: `ARM_SAMPLE_RATE_GATE=1 ./ci/checks.sh`.

Emulators: `NVH_Pixel_7_API_37` (host GPU, real virtual mic — prefer it) and
`NVH_API_37_compact`. The compact AVD regularly half-boots (`sys.boot_completed`
= 1 while `pm` is dead) and produces **emulator-infrastructure ANRs** under
swiftshader that are not app defects (traces show the RenderThread blocked in
`qemu_pipe_read`). Recovery: `adb emu kill; pkill -f qemu-system;` then relaunch
with `-no-snapshot-load -no-boot-anim -gpu swiftshader_indirect -no-audio` and
poll `adb shell pm list packages` (~30 s). "Pixel Launcher isn't responding"
dialogs are the emulator's, not the app's.

## 3. Non-negotiable rules

**Verification**
- **Green unit tests are necessary, never sufficient.** This project's history
  proves green tests coexisted with crashes and layout bugs. Any change with
  user-visible or device-dependent behavior gets an **on-device (emulator or
  phone) run before it is called done.** Phase gates list the exact checklists;
  record what was actually verified — with the evidence — in the tracking file.
- Run tests + lint + `ci/checks.sh` **before every commit**, not at the end of
  a session.
- The FFT **golden snapshot** (`app/src/test/resources/golden/`) is the DSP
  refactor net. If a deliberate DSP change alters it, regenerate it **in the
  same commit**, with the analytical justification in the commit message.
- **Pinned characterization tests** (names starting `pinned_`, carrying a
  finding ID) freeze known-defective behavior. When you fix that finding,
  replace its pinned test with a fixed-behaviour test **in the same commit** —
  never delete one to make a build pass.
- Prefer a **structural gate over a promise**. Phase 4's own experience: the
  new Compose-text gate found nine hardcoded strings a manual sweep had missed,
  and the palette contrast test found five contrast defects while it was being
  written. If you fix a class of defect, add the gate that stops it returning.

**Commits**
- Small, single-purpose, conventional-style messages
  (`fix:`/`feat:`/`refactor:`/`test:`/`build:`/`ci:`/`docs:`/`style:`),
  referencing the plan step and finding IDs, e.g. `[C1, plan 1.1]`.
  Explain *why the old behaviour was wrong*, not just what changed.
- **Never** commit: binaries (`.apk`, `.aab`), build output, `.kotlin/`,
  one-off scripts, IDE machine state, customer documents. `ci/checks.sh`
  enforces this — do not weaken it.
- Work on a phase branch (`aaa/phaseN`); `master` stays shippable.
- Version numbers change **only** in `app/build.gradle.kts`
  (`appVersionName`/`appVersionCode`). Nowhere else, ever [B1].

**Editing discipline**
- **Edit files directly with proper tooling. Never generate regex/sed patch
  scripts to modify source.** The 89 deleted `fix*.py` scripts and the
  structural damage they caused (audit A4, A5) are why this rule exists.
  (Whitespace-only bulk reindents verified by `git diff -w` being empty, and
  mechanical literal→`stringResource` substitutions verified by a compile, are
  the sanctioned exceptions — always followed by a build.)
- ktlint/detekt baselines are **line-position-sensitive** (ktlint) and
  **signature-sensitive** (detekt): whenever you edit a heavily-baselined file,
  regenerate both baselines in the same commit. Never add new violations of
  your own — new code must be clean without baseline growth. Baselines have
  only ever gone down: ktlint 3,081 → 497, detekt 845 → 500.
- **Style checks run LAST, after any `ktlint --format`.** A reformat changes
  detekt signatures, so a detekt pass that ran before the format proves
  nothing. The first remote CI run failed exactly this way (commit `a6b8b46`).
  Order before every commit: format → ktlint → detekt → tests → checks.
- Keep new logic in small, single-purpose classes. Pure computation belongs in
  `:core` where it can be tested in seconds.

**Measurement integrity**
- No new literal `44100` (or any magic sample rate/threshold) — thread real
  values, name real constants. Gate armed.
- **Never label a metric with a standards name (ECMA, ISO) the code does not
  actually implement** [D1/D5]. The emergence index is an in-house method and
  says so on the PDF; it must keep saying so.
- No sentinel values in arithmetic paths [D2]; no clock mixing — interval math
  uses monotonic time (`elapsedRealtimeNanos`), never `loc.time` or
  `System.currentTimeMillis()` deltas [G1].
- An estimate without a known uncertainty is `DEGRADED`, never assumed precise.
  Expired estimates drive nothing: the UI shows `--`, not a frozen number.
- **Every estimator constant is PROVISIONAL until Gate GPS-5.** Do not present
  any of them as validated, and do not claim precision beyond collected proof.

**Concurrency & Android**
- No DSP, file I/O, or bitmap work on the main thread — anything heavier than
  a StateFlow write is dispatched [C4, C6, C16].
- Every coroutine that outlives a function call is held in a `Job` and
  cancelled by an owner; every `AudioRecord`/`MediaPlayer` has a release path
  [C5, L1].
- The mic and the GNSS receiver run **only in LIVE mode and only while their
  permission is held** — `LiveViewModel.applyResourcePolicy` is the single
  place that decides. Do not add new always-on resource usage [C7, U1].

**UI rules added in Phase 4 — all gate-enforced**
- Colours come from `theme/Color.kt` tokens. No `Color(0x…)` anywhere else.
  Every semantic hue exists as a dark *container* and a light *accent*; adding
  a token means adding its contrast assertion to `PaletteContrastTest`.
- User-facing text lives in `strings.xml` and is read with `stringResource`.
  Resource reads happen in composition — hoist them out of callbacks and draw
  scopes (lint `LocalContextGetResourceValueCall` is an error).
- Canvas geometry and text come from `PlotDimens`/`PlotGeometry` in dp/sp — no
  raw pixel literals, and touch handling must read the same geometry the draw
  pass does [U3]. Overlays place themselves through the shared zoom transform
  [U4].
- Interactive controls are ≥ 48 dp and carry a spoken label. Colour is never
  the only channel for a measurement state.
- No side effects or clock reads in composition [U2]. No WebView / JS surface,
  and no INTERNET permission — both gated.

## 4. Danger zones (verified traps in the current code)

| Area | Trap |
|---|---|
| `FFTProcessor` | Stateful (integration, shock detector) — **one instance per stream**; the constraint is documented in its header but not enforced by the type |
| GNSS constants | Every `KalmanSpeedEstimator.Config` value is provisional; changing one silently changes what the app calls a valid speed |
| `V1000` uncertainty | NOT modelled in the order-search budget — tire radius and ratio rounding can dominate the GNSS term. Never present the confidence band as the total error |
| Emergence index | Tone is amplitude-calibrated, noise is treated as a PSD without ENBW correction ⇒ ~1.8 dB fixed bias. Fine as a detection score, not as a normative quantity |
| `MutableList.removeLast()` | Binds to an API-35-only method on this toolchain → `NoSuchMethodError` on older devices. Use `removeAt(lastIndex)`. Lint catches it — never baseline a NewApi error |
| Emulator audio/GPS | The compact AVD reports `VOICE_RECOGNITION` and a fake fix; **dBFS values and speed accuracy on an emulator are not evidence** |
| Baselines | Regenerating without reading the diff can silently absorb a real new violation — always check the count moved the way you expected |

## 5. Definition of done, per change

1. Belongs to a plan step; commit message says which.
2. Unit tests green, including goldens; new behavior has a test **named after
   the finding ID** it closes (e.g. `c17_wavMode_speedRangesUseTimelineMapper`).
3. `lintDebug` zero errors; ktlint/detekt green against baselines;
   `ARM_SAMPLE_RATE_GATE=1 ci/checks.sh` passes.
4. `assembleRelease` (minified) builds.
5. Device-dependent behavior verified on emulator/phone; what was verified is
   stated in the tracking file, with the evidence (logcat line, appops state,
   screenshot description) — not just "checked".
6. `AAA-TRACKING.md` updated (step, commit hash, deviations).
7. If the change closes an audit finding: note it in tracking; the audit
   documents themselves stay frozen as the historical baseline.
8. If it changes architecture, a DSP constant, or a limit of use:
   `doc/ARCHITECTURE_AND_DSP_METHODS.md` updated in the same session.

## 6. Documentation duties

- `README.md` and `doc/ARCHITECTURE_AND_DSP_METHODS.md` were rewritten from the
  real code (2026-08-27) and are now **current**. Keep them that way: user-visible
  behaviour changes (latency, metric names, capture source, limits of use) are
  reflected in the same phase.
- Deviations from the written plan are not failures — they are decisions. Record
  each one with its rationale in the tracking table (DEV-nn) rather than
  silently diverging.
- Claims are proportional to evidence. "Verified on emulator" and "verified on
  hardware" are different statements; write the one that is true.
