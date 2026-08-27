# AGENTS.md — Operating rules for the NVH Spectro AAA refactor

You are working on a **professional NVH (noise/vibration/harshness) measurement
instrument** used in real vehicle testing at Vibratec. The numbers this app
displays and prints on customer PDFs must be *right*. Measurement and kinematic
precision outrank UI polish, velocity, and cleverness — in that order.

## 1. Read before you touch anything

Three documents govern all work on this codebase, in this order:

1. **`V13.1-audit.md`** — 74 findings (C1…C17, D1…D9, A, L, P, U, S, V, G, B
   series) with file:line evidence. Finding IDs are the project's shared
   vocabulary; use them in commits, tests, and comments.
2. **`V13.1-AAA-plan.md`** — the phase plan (P0…P5). Every change must belong
   to a plan step. Work in phase order; P0/P1-class correctness findings
   always preempt features.
3. **`AAA-TRACKING.md`** — live execution log: what is done, commit hashes,
   deviations. **Update it in the same session as the work it describes.**
   `history-understanding.md` explains how the defects got here — read it to
   avoid repeating the process failures it documents.

Current state: **Phase 0 complete** (branch `aaa/phase0`). Next: Phase 1
(measurement correctness) per the plan.

## 2. Build, test, verify — the commands that work on this machine

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
# local.properties: sdk.dir=/opt/homebrew/share/android-commandlinetools

./gradlew :app:testDebugUnitTest          # characterization suite (must stay green)
./gradlew :app:lintDebug                  # zero errors, NO baseline — keep it that way
./gradlew :app:assembleDebug :app:assembleRelease   # release = minified; must always build
./ci/checks.sh                            # repo hygiene gates (same script CI runs)

# Style (pinned CLIs; CI downloads the same versions — see .github/workflows/ci.yml):
.tools/ktlint --baseline=config/ktlint-baseline.xml "app/src/main/**/*.kt" "app/src/test/**/*.kt"
.tools/detekt-cli-1.23.8/bin/detekt-cli --input app/src,core/src --build-upon-default-config \
  --config config/detekt/detekt.yml --baseline config/detekt-baseline.xml
```

Emulator (`NVH_API_37_compact`) regularly half-boots — `sys.boot_completed`=1
while `pm` is dead. Recovery: `adb emu kill; pkill -f qemu-system;` then
relaunch with `-no-snapshot-load -no-boot-anim -gpu swiftshader_indirect
-no-audio` and poll `adb shell pm list packages` (~30 s). "Pixel Launcher
isn't responding" dialogs are the emulator's, not the app's.

## 3. Non-negotiable rules

**Verification**
- **Green unit tests are necessary, never sufficient.** This project's history
  proves green tests coexisted with crashes and layout bugs. Any change with
  user-visible or device-dependent behavior gets an **on-device (emulator or
  phone) run before it is called done.** Phase gates in the plan list the
  exact device checklists.
- Run tests + lint + `ci/checks.sh` **before every commit**, not at the end of
  a session.
- The FFT **golden snapshot** (`app/src/test/resources/golden/`) is the DSP
  refactor net. If a deliberate DSP change alters it, regenerate it **in the
  same commit**, with the analytical justification in the commit message.
- **Pinned characterization tests** (names starting `pinned_`, carrying a
  finding ID) freeze known-defective behavior. When you fix that finding,
  update its pinned test **in the same commit** — never delete one to make a
  build pass.

**Commits**
- Small, single-purpose, conventional-style messages
  (`fix:`/`feat:`/`refactor:`/`test:`/`build:`/`ci:`/`docs:`/`style:`),
  referencing the plan step and finding IDs, e.g. `[C1, plan 1.1]`.
  July 2026 history is the good example; the August catch-all commits are the
  anti-example.
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
  (Whitespace-only bulk reindents verified by `git diff -w` being empty are
  the one sanctioned sed use.)
- ktlint/detekt baselines are **line-position-sensitive** (ktlint) and
  **signature-sensitive** (detekt): whenever you edit a heavily-baselined
  file, regenerate both baselines in the same commit (commands in §2 plus
  `--create-baseline` for detekt). Never add new violations of your own —
  new code must be clean without baseline growth.
- **Style checks run LAST, after any `ktlint --format`.** A reformat changes
  detekt signatures, so a detekt pass that ran before the format proves
  nothing. The first remote CI run failed exactly this way (LiveViewModel
  reformat after the last local detekt pass — commit `a6b8b46` fixed it).
  Order before every commit: format → ktlint → detekt → tests → checks.
- Keep new code out of the god files: new logic goes in new, small,
  single-purpose classes (the plan's Phase 3 target structure), not appended
  to `MainViewModel.kt`.

**Measurement integrity**
- No new literal `44100` (or any magic sample rate/threshold) — thread real
  values, name real constants. The CI gate arms fully in Phase 1.
- Never label a metric with a standards name (ECMA, ISO) the code does not
  actually implement [D1].
- No new sentinel values in arithmetic paths [D2]; no clock mixing — interval
  math uses monotonic time (`elapsedRealtimeNanos`), never `loc.time` or
  `System.currentTimeMillis()` deltas across sources [G1].

**Concurrency & Android**
- No DSP, file I/O, or bitmap work on the main thread — anything heavier than
  a StateFlow write is dispatched [C4, C6, C16].
- Every coroutine that outlives a function call is held in a `Job` and
  cancelled by an owner; every `AudioRecord`/`MediaPlayer` has an owner with a
  release path [C5, L1].
- The microphone runs only in LIVE mode once Phase 2 lands; do not add new
  always-on resource usage [C7].
- Canvas geometry in dp/sp via a shared geometry object — no new raw-pixel
  literals [U3]. No side effects or clock reads in composition [U2].

## 4. Danger zones (verified traps in the current code)

| Area | Trap |
|---|---|
| `FFTProcessor` | Stateful (EMA, shock detector) — one instance per stream; first frame is always squelched [D3]; shares no state across live/WAV safely |
| `MainViewModel` live path | Runs on Main; consumers leak on restart until Phase 2 [C5, C6]. Do not add work here |
| History lists | Newest-first in live mode, chronological in WAV — index-space and mirroring bugs live here [C17, U9, U10] until Phase 3 normalizes |
| `WavDataReader` | Canonical-header-only; stereo/24-bit files are silently misparsed until Phase 1 [C2] |
| Numeric text fields | `toDoubleOrNull() ?: default` swallows French comma decimals [C11] |
| `MutableList.removeLast()` | Binds to API-35-only method on this toolchain → `NoSuchMethodError` on older devices. Use `removeAt(lastIndex)`. Lint catches it — never baseline a NewApi error |
| Emulator audio | Reports `VOICE_RECOGNITION`, not `UNPROCESSED`; silent mic under `-no-audio` — dBFS values on emulator are not evidence |

## 5. Definition of done, per change

1. Belongs to a plan step; commit message says which.
2. Unit tests green, including goldens; new behavior has a test **named after
   the finding ID** it closes (e.g. `c17_wavMode_speedRangesUseTimelineMapper`).
3. `lintDebug` zero errors; ktlint/detekt green against baselines; `ci/checks.sh` passes.
4. `assembleRelease` (minified) builds.
5. Device-dependent behavior verified on emulator/phone; what was verified is
   stated in the tracking file.
6. `AAA-TRACKING.md` updated (step, commit hash, deviations).
7. If the change closes an audit finding: note it in tracking; the audit
   document itself stays frozen as the historical baseline.

## 6. Documentation duties

- `doc/ARCHITECTURE_AND_DSP_METHODS.md` is **stale (v10)**; it is rewritten in
  plan step 5.4. Until then, do not extend it — extend `AAA-TRACKING.md`.
- User-visible behavior changes (latency, metric names, capture source) must
  be reflected in the README in the same phase.
