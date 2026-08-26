# NVH Spectro — History Understanding

**How the V13.1 app came to be: process, requirements, and battles, reconstructed from the git record.**
Source: 49 commits on `master`, 2026-07-25 → 2026-08-25, at commit `4518ec6`. Companion to `V13.1-audit.md` and `V13.1-AAA-plan.md`.

---

## 1. The story in one paragraph

NVH Spectro was built in **one month of nights** by a **single author** — Luigi-BARTH (Louis Barthélemy, the NVH engineer named in the app's About dialog) — using an **AI-agent-driven workflow from the very first hour** (`.agents/AGENTS.md`, containing behavioral rules for the coding agent, is part of the first hour of history). A complete working app landed in the first commit; five "major versions" shipped in the first 44 hours; the core TTNR detection algorithm was forged in a single 00:32–02:20 overnight session of sixteen commits; and the final month drifted from many small semantic commits to a few giant working-tree dumps as the tooling shifted to regex patch scripts. It is a genuinely impressive feat of domain-expert velocity — a field instrument that reached PDF-report maturity in 31 days — and the git record also documents, with precision, exactly where and how the defects catalogued in `V13.1-audit.md` were introduced.

---

## 2. Hard numbers

| Metric | Value |
|---|---|
| Duration | 31 days (2026-07-25 18:33 → 2026-08-25 23:42) |
| Commits / authors / branches / tags | 49 / 1 (Luigi-BARTH) / `master` only (v2 rebuild branch came later) / **0 tags** |
| Commits between 00:00 and 05:00 | **27 of 49 (55 %)** — this was built at night |
| Densest session | 2026-07-28, 00:32–02:20: **16 commits in 108 minutes** (the TTNR algorithm) |
| Largest commits | `4518ec6` (95 files, +7,273), `ada8159` (85 files, +5,234) — both August working-tree dumps |
| Most-churned files | `MainViewModel.kt` touched in **21/49** commits; `FFTProcessor.kt` 20; `MainScreen.kt` 19; `SpectrogramColormap.kt` 15 |
| Debug APK committed | from minute 3 of the project (`f449a1b`), then 11 more times |
| Version labels vs. reality | v1→v13.1 in commit messages; v4 and v8 each "released" twice; **v11 never existed**; Gradle stayed at `12.1.4` |
| Tests written after day zero | **0** (the only test files are the untouched day-one Android Studio templates) |

Commit-size regime change: July averages ~50 lines per commit across many targeted edits; August averages ~2,000 lines across a handful of catch-all commits. That inflection is the single most diagnostic fact in the history (see §5).

---

## 3. Five acts

### Act I — The genesis sprint (Jul 25–26 · v1 → v5 · 17 commits in 44 h)

`2096866` (Jul 25, 18:33) is not a skeleton: it is a **complete working app** — 60 files, 2,225 lines: live colormap, dBFS scaling, axes, freeze, PNG export. The Android Studio template (the dead `ui/main` module, `Navigation.kt`, and both template test files) enters here and **is never touched again for the rest of history** — the origin of audit findings A3 and the F-grade test score. Three minutes later the debug APK is committed; eleven minutes later the README and `AGENTS.md` "deployment procedure" — the AI-agent process was formalized before the app was a day old.

What the author did in the first evening reveals the priorities: by 20:10 the **Vibratec logo** was in the top bar and the export; by 20:26 the launcher icon was custom-branded. **Identity before features** — this was always meant to be shown to colleagues and customers. v2 (21:10) added the frequency cursor, 2D telemetry graphs, and the GPS LED; a "100 % pure GPS telemetry" refactor (21:19) locked in the deliberate no-accelerometer stance the audit later examined in §3b. The same evening, at 21:35, the "**doctoral ECMA-74 / Terhardt Critical Band TTNR**" mode appears — the standards-flavored framing was present from the first hour of the feature, long before any conformance work. Overnight: v3 (00:47), the 2D TTNR spectrum (00:58), the peak cursor (01:04), v4 (01:22). Next day by lunch: the author-info dialog, the architecture doc, and v5 with **"R8 obfuscation protection"** for IP — on day two, the author already treated the tool as professionally valuable intellectual property.

**Inferred requirements:** a live, branded, cabin-noise spectrogram for road testing, with tonal-emergence science as the differentiator, protected and presentable from the start.

### Act II — The detector wars (Jul 27–28 · v6 → v7 · 18 commits)

v6 (Jul 27, 00:09) added LED emergence beacons. Then came **the night of July 28** — sixteen commits between 00:32 and 02:20 that shaped the entire TTNR pipeline as it still exists in V13.1. Read in sequence, the commit messages are a live lab notebook of a false-positive whack-a-mole session, each round adding a gate on top of the previous ones:

> 30 Hz high-pass (00:32) → bitmap Y-orientation fix (00:39) → graph-origin fix (00:49) → Hybrid ECMA-74 + ISO 1996-2 (01:00) → Anti-Spike 1-Pixel filter + frequency-profiled gate (01:12) → squelch cutoff + 2-frame persistence (01:18) → Strict Local Peak + Dual-Lock HF gate for **MLI noise** (01:34) → reconstruct Hanning leakage shoulders *because the previous gates erased real harmonics* (01:37) → Anti-Shock squelch + 3-frame continuity (01:46) → ±3-bin persistence window *because sliding harmonics were being erased again* (01:47) → fix a "zero-deadlock" (01:49) → subtract noise baseline + 150 Hz CBW floor (01:54) → replace the filter stack with exponential integration τ=220 ms (02:04) → **"v7 GOLD release"** (02:08) → 5.0 dB squelch (02:15) → **halve τ to 110 ms, α=0.36, squelch 2.0 dB** (02:20).

Three things follow from this night. First, the "GOLD" label preceded its own tuning by twelve minutes — release labels in this project mark enthusiasm, not verification. Second, **every magic constant the audit flagged (D7) has its timestamp here**, set by eye against a live signal between midnight and 2 a.m., and the τ=220 ms comment the audit found contradicting the α=0.36 code (D2) is literally the 02:04 commit surviving the 02:20 commit. Third, the method was *additive*: each false-positive class got a new heuristic gate stacked on top; nobody went back to check the estimator's statistics — which is why the ENBW unit error (D1) survived twelve rounds of tuning untouched. The mention of **MLI (PWM inverter) noise** confirms the real target: electric-drive whine.

### Act III — The kinematics leap (Jul 30 – Aug 4 · v8 → v9 Pro)

v8 (Jul 30, 18:03, +908 lines) delivered the app's defining domain feature: the **GMPe kinematics engine** — V1000, gear-chain and tire-dimension modes, order tracking, and `EmergenceReportDialog` (born here; at some point in the August rewrites its entry point was severed, creating the orphaned feature the audit found as A4). A second v8 "release" followed 76 minutes after the first ("GOLD", then "Version Avancée"). From Aug 1, commit messages switch to **French** — the audience is now the author's French colleagues. v9 Pro landed at **04:15** (real-time 2D order tracking, 5-step emergence shading); after a three-day silence, Aug 4 brought the **Target Harmonics whitelist** and an instantaneous-order display fix — the signature of the project's cadence from here on: *field drive → list of annoyances → night session*.

### Act IV — From instrument to workstation (Aug 5–8 · v10 → v12)

At **03:53** on Aug 5, one +1,437-line commit created the entire WAV world: `WavDataReader`, `WavAudioWriter`, recording, the player bar, selection dialogs — record on the road, analyze at the desk. The naive 44-byte WAV parser and the 5-minute cap (audit C2/C3) were born in this single pre-dawn commit and never revisited. The splash screen followed the same morning. Aug 8: **v12** ("H1 visibility, dynamic 2D order tracking") — v11 was simply skipped; the version number had fully become a marketing counter.

### Act V — The deliverable era, and the process breaks down (Aug 18–25 · v12.x → v13.1)

A ten-day gap (field use, day job), then three bursts:

- **Aug 18** — Video mode (`VideoAudioExtractor`, `VideoPlayerView`, YouTube dialog): the need to analyze phone/customer videos, shipped in one +544-line commit.
- **Aug 21** — `ada8159` "v12.0 stable": an **85-file working-tree dump** — the first Python patch scripts, four historical APKs, a work PowerPoint (`W2628_EOL_Result.pptx`) and 65 slide images extracted from it. The repo had become the author's desk. The script names record a sub-story the commits never told: `update_viterbi3.py`, `update_viterbi_fix.py`, `update_viterbi_magnet.py`, `update_viterbi_pragmatic.py` — a **Viterbi-based order tracker attempted through at least four iterations and abandoned** into the "pragmatic" jump-penalty tracker that ships today; `plan_rewrite.py` and `apply_plan_c.py` — agent sessions that reached "Plan C".
- **Aug 25** — the final evening. 22:14: **V13** adds PDF export *and* the audio-filter feature in a single +2,358-line commit — two features at once, which is precisely why filters affect playback but not the analyzed spectrum (audit C10: the second feature never got its integration pass). 23:42: **V13.1** sweeps **95 more files** — sixty-plus patch scripts (`fix.py`…`fix4.py`, `brace_count.py`, `check_braces2.py`, `check_syntax*.py`, `parse_log*.py`), compiler error logs, and `vibratec_logo.png` — the duplicate logo asset created because the new PDF generator referenced a name that didn't exist ("Report logo fix"). The audit was performed against this commit the following day.

---

## 4. The requirements, as the sequence reveals them

Nobody wrote a requirements document; the version sequence *is* one. Each release answers a real field need, in the order a working NVH engineer would hit them:

1. **See the noise** — live spectrogram, dBFS, freeze (v1).
2. **See it in context** — GPS speed/accel synchronized to the waterfall (v2).
3. **Prove it's tonal** — TTNR emergence vs. broadband masking, standards-flavored (v3–v7). Target: e-drive / PWM whine.
4. **Attribute it to the machine** — V1000 kinematics, motor orders, whitelist (v8–v9). *The defining feature.*
5. **Capture and replay** — record on the road, analyze at the desk (v10).
6. **Analyze anything** — customer/phone videos (v12.x).
7. **Hand the customer a document** — PDF report with tracked orders (v13).

Non-functional requirements the history reveals: **branding and IP protection from day one** (professional ambition, not a toy), **French UI** (the working language of its users), **fully offline** (INTERNET permission never requested in 49 commits — a deliberate posture, not an accident), and **speed of iteration above everything else**.

---

## 5. The process, reconstructed

- **AI-agent development from hour one.** `AGENTS.md` codifies rules *for the agent* — notably "update `doc/ARCHITECTURE_AND_DSP_METHODS.md` before every commit." The rule held for seven doc updates through v10, then silently died in August (the doc still says v10; the app says v13.1 — audit B6).
- **The release ritual**: bump the number in the About dialog and the commit message, commit the APK into the repo. No tags, no branches, no Gradle version bump (stuck at 12.1.4 since v12), no changelog. Versioning was *communication to colleagues*, never build identity — the direct origin of audit B1.
- **Two tooling eras, visible in the diffs.** July: many small, well-scoped, semantically-named commits — an integrated agent making targeted edits. August: a handful of enormous catch-all commits plus a graveyard of Python regex-patch and brace-counting scripts — file-level patching with syntax fights (`brace_count.py`, `check_braces2.py`, `fix_brace.py` are the fossils of unbalanced-brace battles). **Every structural defect the audit traced to "edited by regex, not by a compiler-checked process" (misleading indentation A5, the orphaned dialog A4, dead branches) dates from this second era.**
- **Verification was a pair of eyes on a phone at 2 a.m.** No test was ever written after day zero. The `W2628_EOL_Result.pptx` and its extraction scripts (`inspect_ppt.py`, `extract_data*.py`, `analyze_img.py`) suggest the author *did* cross-check app output against bench/EOL results — but as a manual, out-of-repo exercise, never encoded as a regression test.

---

## 6. Issues faced, in chronological order of pain

| When | Battle | Trace | Outcome |
|---|---|---|---|
| Jul 26–28 | Rendering orientation & graph sync (bitmap Y-flip, path origins, 1-to-1 alignment) | 4 fix commits | Fixed locally; the *convention* (newest-first history) that later caused the U9/U10 mirroring family was never questioned |
| Jul 28 night | TTNR false positives vs. erased harmonics — 12 rounds of gate-stacking | 16 commits, 108 min | "Won" by heuristics; statistics never revisited (D1 survived) |
| Jul 30–Aug 4 | Kinematics UX (labels, defaults, banner layout); instantaneous order readout | 2 commits | Fixed |
| Aug (undated) | **Viterbi order tracker** — 4 script iterations | `update_viterbi*.py` | Abandoned for the pragmatic jump-penalty tracker |
| Aug 18–25 | Regex-patch syntax battles (braces, compile errors) | `brace_count.py`, `check_syntax*.py`, error logs | "Won" at the cost of code structure |
| Aug 25 | PDF logo resource mismatch on release evening | `vibratec_logo.png` duplicate | Patched by asset duplication (V13.1's headline fix) |

The meta-pattern: **every battle was won tactically and none strategically.** Each fix addressed the visible symptom the same night it appeared; no battle triggered a test, an abstraction, or a revisit of the underlying convention.

---

## 7. How the history explains the audit

| History fact | Audit consequence |
|---|---|
| Complete template app in commit 1, template never revisited | A3 (dead module), Testing = F (template tests are the only tests) |
| The Jul 28 night of eye-tuned constants; "GOLD" 12 min before retuning | D1, D2, D7 (magic numbers, contradictory τ comments, unit error never caught) |
| `MainViewModel.kt` touched in 21/49 commits, never split | A1 (2,004-line god object) — growth by accretion, one night at a time |
| One 03:53 commit created the whole WAV subsystem | C2, C3 (naive parser, 5-min cap desync) — born complete, never hardened |
| Version-as-marketing ritual; no tags; Gradle left behind | B1 (three-way version mismatch) |
| August catch-all commits sweeping the working desk | B2, V4 (APKs, 89 scripts, logs, PPTX in git) |
| PDF + filters shipped together in one evening commit | C10 (filters never wired into analysis) |
| Script-driven rewrites of MainScreen/report UI | A4 (Emergence Report entry point severed), A5 (misleading indentation) |
| Velocity without any test safety net | The entire silent-error class: C1, C17, U9, U10 — wrong numbers that *look* right |

---

## 8. What this means going forward

The record shows a domain expert with real algorithmic ideas and extraordinary momentum, working alone at night with AI leverage — and it shows that **the failure mode was never ability or effort; it was the absence of any mechanism that could say "no."** No test, no CI, no second reader, no tag, no convention ever pushed back. The `V13.1-AAA-plan.md` Phase 0 is aimed squarely at this: golden-file characterization before refactoring, CI grep gates, device checklists, finding-named regression tests — mechanisms that supply the "no" the process never had, without giving up the velocity that produced a working instrument in a month.

Two facts from the history deserve preservation as *strengths*: the requirement sequence in §4 is an excellent, field-validated product roadmap (worth keeping as the definition of what this tool is for), and the July commit discipline (small, semantic, well-messaged) shows the process can be healthy — it was the August tooling shift, not the author's habits, that degraded it.

---

*Reconstructed exclusively from the repository: commit metadata, diffs, file-introduction analysis, and tracked artifacts. Interpretations (e.g., field-use gaps, the bench-comparison purpose of the PPTX scripts) are marked as inferences; all dates, counts, and sequences are verbatim from `git log`.*
