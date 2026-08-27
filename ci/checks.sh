#!/usr/bin/env bash
# Repo-hygiene and correctness gates for NVH Spectro — AAA plan step 0.5.
# Run from the repository root. Exits non-zero on any gate violation.
#
# Gates marked [ARMED] fail the build today. The sample-rate gate arms in
# Phase 1 (plan 1.1) by exporting ARM_SAMPLE_RATE_GATE=1 in ci.yml.

set -u
fail=0
say() { printf '%s\n' "$*"; }
violation() { say "GATE FAIL: $*"; fail=1; }

# --- [ARMED] no binaries or build outputs tracked -------------------------
if git ls-files | grep -qE '\.(apk|aab)$'; then
  violation "tracked APK/AAB binaries:"; git ls-files | grep -E '\.(apk|aab)$'
fi
if git ls-files | grep -qE '^(\.kotlin/|build/|app/build/)'; then
  violation "tracked build outputs or compiler state:"; git ls-files | grep -E '^(\.kotlin/|build/|app/build/)'
fi

# --- [ARMED] no one-off patch scripts at repo root ------------------------
if git ls-files | grep -qE '^[^/]+\.(py|ps1)$'; then
  violation "patch scripts tracked at repo root (edit files directly instead):"
  git ls-files | grep -E '^[^/]+\.(py|ps1)$'
fi

# --- [ARMED] gradlew must be executable -----------------------------------
if [ "$(git ls-files -s gradlew | cut -c1-6)" != "100755" ]; then
  violation "gradlew has lost its executable bit (git update-index --chmod=+x gradlew)"
fi

# --- [ARMED] version is single-sourced ------------------------------------
if ! grep -q 'BuildConfig.VERSION_NAME' app/src/main/java/com/example/nvhspectro/ui/InfoDialog.kt; then
  violation "InfoDialog no longer reads BuildConfig.VERSION_NAME [audit B1]"
fi

# --- [ARMED in Phase 1] no literal 44100 outside AudioConfig --------------
SR_COUNT=$(grep -RIn '44100' app/src/main core/src/main --include='*.kt' | grep -v 'AudioConfig.kt' | wc -l | tr -d ' ')
if [ "${ARM_SAMPLE_RATE_GATE:-0}" = "1" ]; then
  if [ "$SR_COUNT" -gt 0 ]; then
    violation "literal 44100 outside AudioConfig.kt ($SR_COUNT occurrences) [audit C1]:"
    grep -RIn '44100' app/src/main core/src/main --include='*.kt' | grep -v 'AudioConfig.kt' | head -20
  fi
else
  say "INFO: sample-rate gate disarmed (Phase 1 arms it). Current literal-44100 count: $SR_COUNT [audit C1]"
fi

# --- [ARMED] :core stays pure Kotlin — zero Android imports [plan 3.1] ----
if grep -RIn --include='*.kt' -E '^import (android\.|androidx\.|com\.google\.android\.)' core/src >/dev/null 2>&1; then
  violation ":core imports Android classes (must stay pure JVM) [plan 3.1]:"
  grep -RIn --include='*.kt' -E '^import (android\.|androidx\.|com\.google\.android\.)' core/src | head -10
fi

# --- [ARMED] purged dead code must not return [A3/A4/D5, plan 3.8] --------
# The regex-patch era resurrected deleted code more than once. These symbols
# and files were deliberately removed; any reappearance fails the build.
DEAD_SYMBOLS='CandidateHarmonicTracker|isFrequencyAllowed|toggleDrawingMode|parseYouTubeEmbedUrl_DELETED'
if grep -RIn --include='*.kt' -E "$DEAD_SYMBOLS" app/src/main core/src/main >/dev/null 2>&1; then
  violation "purged dead code has returned [plan 3.8]:"
  grep -RIn --include='*.kt' -E "$DEAD_SYMBOLS" app/src/main core/src/main | head -10
fi
if git ls-files | grep -qE '(report_mode_screen_copy\.kt|ui/main/|vibratec_logo\.png)'; then
  violation "deleted dead files are tracked again [plan 3.8]:"
  git ls-files | grep -E '(report_mode_screen_copy\.kt|ui/main/|vibratec_logo\.png)'
fi

if [ "$fail" -ne 0 ]; then
  say ""; say "ci/checks.sh: FAILED"
  exit 1
fi
say "ci/checks.sh: all armed gates passed"
