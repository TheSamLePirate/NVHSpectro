# NVH Spectro V14 — Layout, UI & UX Audit

**Scope:** The presentation layer **as it exists today** — branch `aaa/phase0`, commit `b8da90f`. All 24 Compose files under `app/src/main/java/com/example/nvhspectro/{ui,theme}/` plus `MainScreen.kt`, `MainActivity.kt`, `SpectrogramColormap.kt`, `res/values/{themes,strings}.xml`, and the Compose dependency set. DSP, metrology and ViewModel logic are out of scope except where they constrain layout.
**Date:** 2026-08-28
**Benchmark:** "A perfect Android app" — a Material 3 instrument that a stranger picks up and reads as *native, current, and deliberate*: one type scale, one shape language, one spacing rhythm, real vector iconography, motion that explains state changes, and a layout that earns every pixel on a phone in a car cradle.
**Method:** Full manual read of the presentation layer (~5,100 lines of Compose). Every count in this document was produced by grep over the current tree and is reproducible; findings cite `file:line`.

---

## 1. Executive Summary

This app has **one genuinely excellent design axis and four missing ones.**

`theme/Color.kt` is the best-executed design work in the repository and among the better colour systems you will find in an instrument app: 60 named tokens, each documented with its measured contrast ratio against the specific surface it is used on, a deliberate and well-argued product decision to reject dynamic colour and light mode (D4), a single ARGB source of truth shared between the screen and the PDF so the report can never disagree with the display, and a `PaletteContrastTest` that fails the build when a ratio regresses. **Do not touch it.** It is the model for what the other axes should look like.

Everything else in the design system is either template default or absent:

1. **`theme/Type.kt` is the untouched Android Studio template.** It overrides exactly one of Material 3's fifteen type roles (`bodyLarge`) and leaves the other fourteen as commented-out placeholder text. The app compensates with **159 hard-coded `fontSize` literals** across 20 files — including **23 sites between 9.sp and 10.5.sp**, below Material's smallest defined role (`labelSmall`, 11.sp) and below any defensible readability floor for a device used at arm's length in a moving vehicle.
2. **There is no iconography.** Not one `Icon()` call exists in the app; the `material-icons` artifact is not even a dependency. Every affordance is either a text label or an **emoji baked into a translatable string resource** — `⚙️ GMPe`, `📸 Exporter`, `🎙️ En direct`, `📂 LECTURE : %1$s`, and status glyphs `●`/`▲`/`✕`/`⛔`. Emoji cannot be tinted by the theme, render in a different OEM font on every manufacturer's device, carry their own baseline and advance metrics, and are the single loudest signal that a UI was not designed.
3. **There is no shape scale and no spacing scale.** No `Shapes()` is passed to `MaterialTheme`, so eight different corner radii (3, 4, 6, 8, 10, 12, 14, 16 dp) are applied ad hoc. **275 raw `dp` literals** carry the spacing, 36 of them `2.dp` and 22 of them `1.dp` — off any 4 dp grid, and the reason the UI reads as cramped rather than dense.
4. **There is essentially no motion.** Six animation calls exist in the entire app; three are the canvas beacon pulse and three are one colour fade in `ReportModeScreen`. Mode switches, freeze, popup menus, and the appearance of the WAV player all snap instantly. On Android, that absence *is* a look — it reads as unfinished.
5. **A real density bug misplaces both bottom-bar menus on every device.** `Popup(offset = IntOffset(0, -100))` and `IntOffset(0, -250)` (`MainScreen.kt:304,363`) pass **pixels** where the code clearly intends dp. The export button and the entire audio-source menu therefore land in a different place on every screen density — roughly 250 dp above the bar on a 1× device and roughly 91 dp on a 2.75× phone. This is a functional defect, not a polish item.

Beneath these, the structural problem is that **`MainScreen.kt` is a 1,484-line file whose `AppScreen` composable inlines the entire chrome, both panes, five popups and eight dialog call sites**, with layout constants, colours and font sizes chosen at each call site. There is no component layer — no `NvhButton`, no `NvhCard`, no `NvhSectionHeader` — so every visual decision is made 30 times and can drift 30 ways. That is why the app has 8 corner radii and 27 distinct font sizes: not because anyone chose them, but because nothing prevented them.

Finally, the work is being done **blind and without a net**: there are **zero `@Preview` composables** and **zero instrumented/screenshot tests** in the repository. Visual iteration currently requires a full install-and-look cycle, and no layout regression can be caught automatically. For a task whose goal is "visually perfect," that tooling gap is the first thing to close, not the last.

**Overall grade: B for colour, D for type, F for iconography, C− for layout, D for motion.** The app is not ugly by accident — it is a carefully engineered instrument with a professional palette and a systematically neglected surface. The gap to "a perfect Android app" is almost entirely *system work*: define the four missing token sets, extract a component layer, replace emoji with vectors, and give state changes 200 ms.

### Finding census

**34 findings**: **3 Blocker** · **11 Major** · **13 Moderate** · **7 Minor**

### Scorecard

| Axis | Grade | Summary |
|---|---|---|
| Colour system | **A−** | Documented, contrast-tested, CI-gated, single source of truth for screen + PDF. Exemplary. Missing only a light/high-contrast variant, which D4 deliberately declines. |
| Typography | **D** | Template file, one role of fifteen defined, 159 ad-hoc literals, 23 sites below 11.sp. |
| Iconography | **F** | Zero icons. Emoji in translatable strings. No icon dependency. |
| Shape | **D** | No `Shapes()`; 8 radii ad hoc. |
| Spacing / density | **C−** | No scale; 275 literals; 1–2 dp paddings produce cramping, not density. |
| Layout & responsiveness | **C−** | Two-pane split is sound; single `maxWidth > maxHeight` breakpoint, no `WindowSizeClass`, dead nested weights, fixed-dp popup widths. |
| Controls & affordances | **C** | 48 dp targets are respected (good). But toggle state is a container-colour swap on a filled button, no button hierarchy, no chips/segmented buttons, two competing dialog idioms. |
| Motion | **D** | 6 animation calls app-wide; no state, navigation or visibility transitions. |
| Screen-space efficiency | **C** | Bottom bar wastes its own width on 2 dp padding and 10 sp labels; no pane resize; no immersive/fullscreen mode for the canvas. |
| Accessibility | **B−** | Genuinely thoughtful: 48 dp floors, `contentDescription` on every emoji control, colour never the sole channel. Undone by sub-11.sp text and `softWrap = false` fixed widths that clip at large font scale. |
| Process / tooling | **F** | 0 previews, 0 screenshot tests, 0 instrumented tests. |

---

## 2. Blockers

### UX-B1 — `Popup` offsets are in pixels, so both bottom-bar menus are misplaced on every device
`MainScreen.kt:300-306, 359-365`

```kotlin
Popup(
    alignment = Alignment.TopCenter,
    offset = androidx.compose.ui.unit.IntOffset(0, -100),   // export button
)
Popup(
    alignment = Alignment.TopCenter,
    offset = androidx.compose.ui.unit.IntOffset(0, -250),   // audio-source menu
    onDismissRequest = { showAudioModeMenu = false },
)
```

`Popup`'s `offset: IntOffset` is applied in **raw pixels**, not density-independent units. The values were evidently tuned by eye on one device. Consequences on a 2.75× phone (440 dpi, entirely typical): the export button intended to sit ~100 dp above the freeze button sits ~36 dp above it, overlapping the bar; the three-item source menu intended to clear the bar by ~250 dp clears it by ~91 dp, so its lower items overlay the bottom bar they were launched from. On a 1× tablet the same menu floats ~250 dp up, detached from its trigger.

**Fix:** these are not popups at all — they are a menu and a contextual action. Replace with `DropdownMenu` (which positions itself against its anchor, animates, and dismisses on outside touch for free) or `ModalBottomSheet`. If a `Popup` must be kept, convert through `LocalDensity`: `with(LocalDensity.current) { IntOffset(0, -(100.dp).roundToPx()) }`.

### UX-B2 — 23 text sites are rendered below 11.sp, in a device used at arm's length in a vehicle
`MainScreen.kt` (13 sites), `KinematicsDialog.kt` (7), `EmergenceReportDialog.kt` (2), `SettingsDialog.kt` (1)

Measured distribution of the app's `fontSize` literals:

| Size | Count | Status |
|---|---|---|
| 9.sp | 7 | Below every readability guideline |
| 9.5.sp | 1 | Below every readability guideline |
| 10.sp | 12 | Below Material's smallest role |
| 10.5.sp | 3 | Below Material's smallest role |
| 11.sp | 51 | Material `labelSmall` — floor, used as the body default |
| 12.sp | 30 | |
| 13–40.sp | ~55 | |

`labelSmall` (11.sp) is the *smallest role Material 3 defines*, intended for sparse overline labels — and this app uses it as its most common size, then goes below it 23 times. The 9.sp sites in `KinematicsDialog.kt` are ratio and parameter readouts: numbers an engineer must read correctly to trust an RPM figure. This directly contradicts the accessibility care taken elsewhere in the same files (`MIN_TOUCH_TARGET = 48.dp`, documented as being for operators "wearing gloves, in a moving vehicle") — the same operator, in the same conditions, is being asked to read 9.sp text.

**Fix:** define the type scale (UX-M1), set the floor at `labelMedium` (12.sp) for any label and `bodyMedium` (14.sp) for any value an operator reads as a measurement, and delete all 159 literals in favour of roles.

### UX-B3 — No `@Preview` and no screenshot tests: the visual work has no feedback loop and no regression net
Repository-wide: `grep -rn "@Preview" app/src/main/java` → 0 results. `app/src/androidTest` → does not exist.

`ci/checks.sh` gates colour hexes, tracked binaries and version single-sourcing; `PaletteContrastTest` gates contrast ratios. Both are good. But **no gate and no tool observes layout.** There is no way to see a dialog without building and installing the app, no way to check a component at font scale 1.3 or 2.0, no way to check landscape without a device rotation, and nothing that fails when a padding change clips a label.

This is a blocker *for the stated goal specifically*: "visually perfect" is reached by iterating, and iteration speed here is currently one full Gradle install per look. It also means every fix in this audit is unverifiable at review time.

**Fix, in order:** (a) add `@Preview` composables for every dialog and both panes, with `@PreviewFontScale` and `@PreviewScreenSizes` multipreviews; (b) add the Compose UI test dependency to `androidTest` (already declared in `app/build.gradle.kts:101-102` but sourcing nothing) and assert the layout invariants that matter — no clipped labels at scale 2.0, 48 dp targets, both orientations; (c) consider Roborazzi or Paparazzi for JVM screenshot tests so layout regressions fail in CI without a device.

---

## 3. Major findings

### UX-M1 — `theme/Type.kt` is the Android Studio template, verbatim
`theme/Type.kt:1-36`

The entire file defines `bodyLarge` and then contains, as a comment, the placeholder block the IDE generates:

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(/* … */)
    /* Other default text styles to override
    titleLarge = TextStyle(…),
    labelSmall = TextStyle(…)
    */
)
```

Fourteen of fifteen roles are therefore Material defaults, no font family is chosen, and — critically for this app — **there is no monospace/tabular role.** An instrument that displays RPM, km/h, Hz and dB in continuously updating readouts needs figures that do not shift horizontally as digits change; every numeric readout in this app is proportional-figure sans, so the KPI row jitters as values update.

**Fix:** define all fifteen roles; add `NvhTypography.readout` (or a `FontFeature`-enabled `TextStyle` with `fontFeatureSettings = "tnum"`) for every measurement value; consider a condensed family for the bottom bar so labels fit without 10 sp.

### UX-M2 — Zero icons; emoji used as iconography, inside translatable strings
`res/values/strings.xml` (30+ sites), `WavPlayerBar.kt:98,111,118`, `MainScreen.kt:1368,1425`

```xml
<string name="gmpe_button">⚙️ GMPe</string>
<string name="export_frozen">📸 Exporter</string>
<string name="source_button_live">🎙️ En direct</string>
<string name="player_reading">📂 LECTURE : %1$s</string>
<string name="notice_with_close">%1$s   ✕</string>
<string name="target_orders_banner">🎯 FILTRE CIBLES : %1$s</string>
```

```kotlin
IconButton(onClick = …) { Text("⏪", fontSize = 14.sp) }
FilledIconButton(…) { Text(if (isPlaying) "⏸" else "▶", fontSize = 16.sp, …) }
```

Six distinct problems, each independently disqualifying for "a perfect Android app":

1. **Not themeable.** Emoji render in the system emoji font at its own colours; `color = NvhOnSurface` on `Text("⏸")` is ignored. The app's carefully specified palette stops at the icon.
2. **Device-dependent.** Samsung, Pixel, Xiaomi and Huawei ship different emoji fonts; `⚙️`, `🚘` and `📸` look materially different on each, and a few (`⛔`, `🎯`) differ enough to change perceived meaning.
3. **Wrong metrics.** Emoji carry their own baseline and advance width, which is why the code needs `fontSize = 14.sp`/`16.sp` tuning per glyph and why they sit visually off-centre in a 48 dp `IconButton`.
4. **Inside translatable resources.** A translator receives `📸 Exporter` and may reorder, drop, or duplicate the glyph. Icons are not language.
5. **`✕` as a close affordance** (`notice_with_close`) is a text glyph inside a formatted string — not a touch target, not 48 dp, not independently labelled.
6. **Text glyphs for status** — `●`/`▲`/`✕` in `GpsLedIndicator` (`MainScreen.kt:1368`) and `⛔` in `LocationPermissionChip` (`:1425`). The *intent* here is admirable and documented (shape as a second channel for colour-blind operators, per plan 4.4) and must be preserved — but it should be preserved with three vector shapes, not three characters at `FontWeight.Black`.

**Fix:** add `androidx.compose.material:material-icons-extended` (or, better for an instrument, author ~20 vector drawables so the set is deliberate and licence-clean), strip every emoji from `strings.xml`, and pass icons as `Icon(painter, contentDescription)` beside the label. The existing `contentDescription` work carries over unchanged.

### UX-M3 — No `Shapes()` in the theme; eight corner radii applied ad hoc
`theme/Theme.kt:44-50` (no `shapes =` argument), 40 `RoundedCornerShape` call sites

Measured: `8.dp` ×15, `4.dp` ×8, `6.dp` ×7, `12.dp` ×4, `16.dp` ×3, `3.dp` ×1, `10.dp` ×1, `14.dp` ×1. `MaterialTheme` therefore uses default shapes for its own components (`Button` = 20 dp full-ish, `Card` = 12 dp) while hand-built surfaces around them use 3–16 dp. A `Card` at 12 dp containing a `Surface` at 4 dp inside a dialog at 16 dp, adjacent to a `Button` at Material's default — four radii in one visual group.

**Fix:** pass an explicit `Shapes(extraSmall = 4.dp, small = 8.dp, medium = 12.dp, large = 16.dp, extraLarge = 28.dp)`, then use `MaterialTheme.shapes.*` everywhere and let `ci/checks.sh` gate raw `RoundedCornerShape(` outside the theme package — exactly the pattern already proven for colour hexes.

### UX-M4 — No spacing scale; 275 raw `dp` literals, and the small ones cause cramping
Repository-wide

Measured: `8.dp` ×50, `6.dp` ×44, `4.dp` ×42, `2.dp` ×36, `1.dp` ×22, `16.dp` ×14, `12.dp` ×14, `10.dp` ×14, then a long tail including `3.dp`, `5.dp`, `14.dp`, `20.dp`, `28.dp`.

Two distinct problems. First, `1.dp`, `2.dp`, `3.dp`, `5.dp`, `6.dp`, `10.dp` and `14.dp` are off any 4 dp grid, so nothing aligns to anything and vertical rhythm never establishes. Second and more visible: **58 uses of 1–2 dp padding** — including the bottom bar's `contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)` on five buttons and `WavPlayerBar`'s `padding(horizontal = 6.dp, vertical = 2.dp)` — are not "dense," they are *touching*. Dense professional UIs (Bloomberg, Ableton, Logic) use tight but consistent 4/8 px rhythm with generous internal padding on controls; this app has the opposite, loose outer margins and controls whose labels touch their own borders.

**Fix:** `object NvhSpacing { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }`, floor control padding at `sm`, and gate raw dp outside the theme package.

### UX-M5 — The bottom bar fights itself for space
`MainScreen.kt:237-518`

Five controls — GMPe, Rapport Manuel, Figer, Audio, and the popup children — share the bar as `Modifier.weight(1f)` each, with `contentPadding = PaddingValues(horizontal = 2.dp, …)`, labels at `10.sp`–`11.sp`, `maxLines = 1`, and `softWrap = false`. Every one of those five decisions is a symptom of the same cause: the labels are too long for the space, and instead of shortening them or moving to icons, the type was shrunk and the padding removed until they fit on the developer's device.

Specific consequences:
- `weight(1f)` gives "Rapport Manuel" (15 chars) the same width as "Figer" (5), so one is clipped while the other has slack.
- `softWrap = false` with `maxLines = 1` means **hard truncation, not ellipsis**, at font scale > 1.0. The label becomes a fragment.
- `2.dp` horizontal content padding puts text ~2 dp from the button edge — visually the label is *outside* its own container.
- The bar is the app's primary navigation and mode switch, and it is the least legible surface in the app.

**Fix:** this bar wants to be icon + short label, or a `NavigationBar` (which Android users already read as "top-level modes"), or two rows. Given four persistent modes plus a freeze action, `NavigationBar` for the modes and a `FloatingActionButton` for freeze/export is the idiomatic Android answer and frees the entire bar width.

### UX-M6 — Toggle state is communicated by swapping a filled button's container colour
`MainScreen.kt:250-256, 274-280, 337-343, 385-405`

```kotlin
containerColor = if (kinematicsConfig.isEnabled) NvhActiveContainer else MaterialTheme.colorScheme.primary
containerColor = if (isReportModeActive) NvhReportMode else MaterialTheme.colorScheme.primary
containerColor = if (isFrozen) NvhRecording else MaterialTheme.colorScheme.secondary
containerColor = if (audioSourceMode == LIVE) NvhActiveContainer else NvhInactiveContainer
```

A filled blue button that turns filled green is a weak on/off signal: both states look equally "pressable," neither looks "engaged," and the meaning has to be learned. The app partially knows this — `gmpe_button_active` appends the word `(Actif)` — which is the right instinct applied to the wrong widget.

Material 3 has purpose-built controls for exactly this, all of which render selection unambiguously through *container + outline + optional leading checkmark*: `FilterChip` for the source modes, `SegmentedButton` for a small exclusive set, `IconToggleButton` for freeze. `FilterChip` is already used correctly elsewhere in this very file (`MainScreen.kt:1145-1160`, the metric selector) — so the app contains its own solution and doesn't apply it to its most important controls.

### UX-M7 — Two competing dialog idioms, and no bottom sheets anywhere
`ExportDialog.kt:22`, `InfoDialog.kt:35`, `SettingsDialog.kt:78,484`, `SaveRecordingDialog.kt:21`, `WavSelectionDialog.kt:43`, `ReportModeScreen.kt:95` use `AlertDialog`; `EmergenceReportDialog.kt:41`, `KinematicsDialog.kt:96`, `OrderSelectionDialog.kt:39` use raw `Dialog { Card { … } }` with hand-built titles, padding and button rows.

The result is two visual languages for the same job — different corner radii, different title typography, different button placement, different insets — chosen per file. And `SettingsDialog` (571 lines, three bordered `Card` sections, five `Slider`s, a nested `AddFilterDialog`) is a **scrolling `AlertDialog`**: the platform's least appropriate container for a settings surface of that size, capped at ~60% screen height with its own scrollbar inside a dialog inside a scrim.

**Fix:** one `NvhDialog` wrapper for genuine confirmations; move `SettingsDialog`, `KinematicsDialog` and `EmergenceReportDialog` to `ModalBottomSheet` (drag handle, full-height expansion, edge-to-edge aware, the current Android idiom for exactly this) or to a real settings *screen*.

### UX-M8 — Motion is absent, and its absence is legible
Six animation calls exist app-wide: `animateFloat` ×3 (`SpectrogramColormap.kt:176,186,196` — the beacon pulse, correct and effective) and `animateColorAsState` ×3 (`ReportModeScreen.kt:671,676`).

Nothing else moves. Specifically, no transition covers: entering/leaving report mode (a **full-screen replacement** at `MainScreen.kt:196-199`, which currently hard-cuts), the audio-source menu appearing, the export button appearing on freeze, `WavPlayerBar` appearing when a file loads (it pops into the layout and shoves the telemetry card down), source-mode colour changes on the bottom bar, or dialog entry beyond the platform default.

**Fix:** `AnimatedContent`/`Crossfade` on the report-mode switch; `AnimatedVisibility` with `expandVertically` on `WavPlayerBar` and the export button; `animateColorAsState` on every mode-dependent container colour; `DropdownMenu` brings its own transition free (see UX-B1). Budget 150–250 ms with Material's standard easing. This is perhaps the highest visual return per line of code in the whole audit.

### UX-M9 — Responsiveness is one `maxWidth > maxHeight` test; no `WindowSizeClass`
`MainScreen.kt:1206-1223`

```kotlin
BoxWithConstraints(…) {
    if (maxWidth > maxHeight) { Row { spectrogramPane(…); vehicleDataPane(…) } }
    else { Column { spectrogramPane(…); vehicleDataPane(…) } }
}
```

The two-pane-declared-once pattern here is genuinely good, and the comment documents a real bug it fixed (targetSdk 36 ignoring orientation lock on large screens). But aspect ratio is the wrong axis. A 10-inch tablet held in portrait is 800×1280 dp — `maxWidth < maxHeight`, so it receives the **phone stack**, with a spectrogram pane 704 dp tall and a telemetry card beside nothing, in a window wide enough for a three-pane layout. A large unfolded foldable gets the same. Conversely a small phone in landscape (640×360) gets the side-by-side split with a 352 dp-wide spectrogram.

`material3-window-size-class` is not a dependency. **Fix:** add it, branch on `WindowWidthSizeClass` (Compact → stack, Medium → split, Expanded → split with a permanently visible settings pane instead of a dialog), and keep the aspect test only as a tiebreaker.

### UX-M10 — Dead nested weights make the data pane's proportions a fiction
`MainScreen.kt:884-925, 1163-1168` and `:913-916`

```kotlin
val vehicleDataPane: @Composable (Modifier) -> Unit = { paneModifier ->
    Column(modifier = paneModifier) {
        if (…) { WavPlayerBar(…) }                  // unweighted, intrinsic height
        Card(modifier = Modifier.weight(0.45f) …)   // the ONLY weighted child
    }
}
```

`weight(0.45f)` on the sole weighted child of a `Column` distributes **all** remaining space to it — the `0.45f` has no effect whatsoever. The same dead constant appears on `VideoPlayerView` (`:913`). A reader (and the next person to tune this layout) will reasonably believe the card takes 45% of the pane and that 55% goes somewhere else; it does not exist. Meanwhile the real behaviour — `WavPlayerBar` taking intrinsic height off the top and the card absorbing the rest — means loading a WAV silently shrinks the telemetry graph by the player's full height with no transition (see UX-M8).

**Fix:** delete the meaningless weights (`fillMaxSize()` expresses the actual behaviour), or introduce the second weighted sibling the constants imply.

### UX-M11 — `themes.xml` configures system bars through APIs that targetSdk 36 ignores
`res/values/themes.xml:19-21`, `MainActivity.kt:22`

```xml
<item name="android:statusBarColor">@color/nvh_primary_container</item>
<item name="android:navigationBarColor">@color/nvh_background</item>
```

`android:statusBarColor` and `android:navigationBarColor` are **deprecated and ignored from API 35**, and this app is `targetSdk = 36` with `enableEdgeToEdge()` in `MainActivity`. Both items are dead configuration. Edge-to-edge is therefore in force with system bar appearance unmanaged, and **no `WindowInsets` handling exists anywhere in the app** beyond whatever `Scaffold`, `TopAppBar` and `BottomAppBar` apply by default. `windowSplashScreenBackground` above them is still correct and does work.

The M3 `Scaffold`/`TopAppBar`/`BottomAppBar` defaults probably keep content clear of the bars, but nothing verifies it, the app never states an intent about icon contrast on the status bar, and the `Popup`s of UX-B1 are outside `Scaffold`'s inset scope entirely. **Fix:** delete the dead items, control bar icon appearance via `WindowInsetsControllerCompat.isAppearanceLightStatusBars`, and add an inset assertion to the (currently absent) UI tests.

---

## 4. Moderate findings

| ID | Finding | Location |
|---|---|---|
| UX-D1 | **No component layer.** `AppScreen` is a single ~1,020-line composable inlining chrome, both panes, 5 popups and 8 dialog call sites, with colour/size/padding chosen per call site. This is the *mechanism* behind UX-M1/M3/M4 — nothing centralises a visual decision, so every one drifts. Extract `NvhButton`, `NvhCard`, `NvhSectionHeader`, `NvhReadout`, `NvhStatusChip`. | `MainScreen.kt:96-1347` |
| UX-D2 | **Fixed `dp` widths on popup content** — `width(105.dp)` on the export button, `width(115.dp)` on the source menu, `width(130.dp)` elsewhere — combined with `softWrap = false`. Guaranteed truncation at font scale ≥ 1.3, in the same controls whose height was correctly made flexible via `MIN_TOUCH_TARGET`. | `MainScreen.kt:288-291, 372` |
| UX-D3 | **No button hierarchy.** 29 `Button` (filled), 12 `TextButton`, 6 `OutlinedButton`, 6 `IconButton`. Filled is the default everywhere, so nothing is visually primary; `FilledTonalButton` and `ElevatedButton` are unused. In the bottom bar, five filled buttons of equal weight mean the operator's eye has no entry point. | repository-wide |
| UX-D4 | **Elevation is unsystematic** — `defaultElevation` 4/6 dp and `tonalElevation` 4/6 dp, mixed freely with manual `border(1.dp, …)` strokes to distinguish surfaces. Two different mechanisms (shadow, outline) express the same hierarchy, so depth reads inconsistently. Pick tonal elevation as the single mechanism (correct for a dark theme, where shadows are nearly invisible) and define a 0/1/2/3 ladder. | `MainScreen.kt:924`, `WavPlayerBar.kt:53`, `SettingsDialog.kt:93-96`, others |
| UX-D5 | **`FontWeight.Bold` is the default weight.** Applied so broadly (bottom bar, all section titles, all KPI values, GPS label, player filename, chip labels) that it no longer marks emphasis. `FontWeight.Black` appears once (`MainScreen.kt:1368`) to out-shout the surrounding bold. Emphasis must come from the type scale and colour, not from making everything heavy. | repository-wide |
| UX-D6 | **The canvas — the app's primary surface — has no immersive mode.** A `Box(background = NvhCanvas)` at a fixed 0.55 weight, permanently framed by top bar, bottom bar and the data pane. An instrument's measurement surface should be expandable to full screen (double-tap, or a dedicated control) with chrome hidden. Currently ~45% of a phone screen is chrome + telemetry at all times. | `MainScreen.kt:522-527, 1456` |
| UX-D7 | **Pane split is fixed at 0.55/0.45 with no user control.** Different tasks want different splits (reading the spectrogram vs. watching RPM). A draggable splitter, or even a 3-state cycle control, is the expected affordance and costs little. | `MainScreen.kt:1456-1459` |
| UX-D8 | **`TopAppBar` actions are two `TextButton`s** ("Informations", "Réglages") plus a logo `Image`, one of them italic (`fontStyle = FontStyle.Italic`) for no stated reason. Three competing elements in the action slot, consuming most of the bar width; the italic is the only italic in the app. Standard: two `IconButton`s (info, settings) with the logo in the title slot or dropped. | `MainScreen.kt:210-234` |
| UX-D9 | **French-only, in the default resource folder.** All 293 strings are French in `values/` with no `values-en/` and no locale variants; `strings.xml` is also the store for the emoji of UX-M2. Not a layout defect, but "a perfect Android app" implies an English default with a `values-fr/` override, and the layout has never been exercised against longer German/English strings — which UX-M5's `softWrap = false` bar will not survive. | `res/values/strings.xml` |
| UX-D10 | **Status glyphs are non-scaling text in a `Row` with a text label**, so the LED's visual weight changes with the user's font scale independently of the label's. A vector at a fixed `dp` size is the correct construction for a status indicator. Preserve the shape-as-second-channel intent exactly. | `MainScreen.kt:1362-1372, 1416-1432` |
| UX-D11 | **`EmergenceReportButton` is a `Surface` with `Modifier.clickable`** at `RoundedCornerShape(4.dp)` with 6×2 dp padding and 10.sp text — a hand-rolled chip well under 48 dp, with no ripple bounds matching its shape and no `Role.Button` semantics. `AssistChip` or `FilterChip` gives all of this correctly. | `MainScreen.kt:1387-1405` |
| UX-D12 | **`SettingsDialog` sections are `Card` + manual `border` + a coloured title `Text` at 12.sp**, repeated three times with different accent colours and no shared component — so the three sections of one dialog differ in padding (`10.dp`) and internal spacing (`6.dp`) from the dialog's own rhythm (`14.dp`). A single `NvhSection(title, accent, content)` would enforce one. | `SettingsDialog.kt:89-300` |
| UX-D13 | **No empty/loading/error state design system.** States are ad-hoc: a scrim panel on the canvas (`NvhCanvasPanel`), a notice banner (`NvhNoticeContainer`), plain text with an emoji (`no_wav_loaded`). Three unrelated visual treatments for "the app has nothing to show you or something went wrong." | `MainScreen.kt`, `strings.xml:61` |

---

## 5. Minor findings

| ID | Finding |
|---|---|
| UX-N1 | `Modifier.padding(horizontal = 4.dp, vertical = 1.dp)` and similar 1 dp verticals produce sub-pixel padding on 1× devices — effectively zero, and a rounding difference across densities. |
| UX-N2 | `HorizontalDivider(thickness = 0.5.dp)` (`SettingsDialog.kt:277`) renders inconsistently across densities; 1 dp with a lower-alpha colour is the reliable construction for a hairline. |
| UX-N3 | `logo_vibratec.png` is a raster in a single density bucket, scaled to `height(28.dp)` — soft on xxhdpi/xxxhdpi. Should be a vector drawable. |
| UX-N4 | `WAV_NAME_MAX_CHARS = 14` truncates filenames by **character count** rather than by layout (`TextOverflow.Ellipsis` with `maxLines`), so the truncation point does not correspond to the available width and cuts mid-word regardless of pane size. |
| UX-N5 | `alpha` values are hand-tuned per site (`0.22f`, `0.6f`, `0.7f`, `0.8f`) rather than drawn from a defined set, so nominally identical treatments (chip fill, section border) differ slightly. |
| UX-N6 | No haptic feedback anywhere. On a device operated without looking — in a vehicle, per the app's own stated context — freeze/record/mode-switch are exactly the actions that warrant `LocalHapticFeedback`. |
| UX-N7 | `Arrangement.spacedBy` values (`2.dp`, `4.dp`, `6.dp`, `8.dp`, `14.dp`) vary between sibling containers, so gaps differ between rows that read as peers. |

---

## 6. What is already right — preserve deliberately

Explicitly listed so remediation does not regress it:

1. **`theme/Color.kt` in full.** 60 documented tokens, measured contrast ratios per token *per surface*, `PaletteContrastTest` enforcement, the hex-literal CI gate, and the shared `NvhOrderTraceArgb` int list that makes the PDF and the screen provably agree. This is reference-quality work.
2. **Decision D4 (fixed dark, no dynamic colour).** Correctly argued in both `Color.kt` and `Theme.kt`: wallpaper-derived colour would make order traces and criticality badges device-dependent, which a measurement UI cannot accept. Any "add light mode / Material You" suggestion must be refused on these grounds — including as this audit's own recommendations are implemented.
3. **`MIN_TOUCH_TARGET = 48.dp` and `PLAYER_TOUCH_TARGET = 48.dp`**, with the documented reasoning about gloved operators in moving vehicles, and the note that the previous 34–38 dp fixed heights clipped their labels at raised font scale.
4. **Colour is never the only channel.** `GpsLedIndicator` pairs each state with a distinct shape *and* words; the emergence ramp is paired with badges and text. Keep this invariant through the icon migration (UX-M2, UX-D10).
5. **`contentDescription` on every emoji-as-icon control**, with the explicit note that "through TalkBack '⏪' is not a word." The emoji must go, the labels must stay.
6. **The platform `SplashScreen`** replacing a `delay(2000)` Compose splash, and `windowBackground` set to `nvh_background` to kill the launch flash.
7. **The two-pane-declared-once pattern** (`spectrogramPane`/`vehicleDataPane` as `@Composable (Modifier) -> Unit` closures). The breakpoint is wrong (UX-M9) but the structure is right and makes fixing it cheap.
8. **`FilterChip` for the metric selector** (`MainScreen.kt:1145-1160`) — the correct widget, correctly used. It is the template for UX-M6.
9. **`String.format(Locale.ROOT, …)`** for every numeric readout, so a measurement never changes with the locale.

---

## 7. Remediation roadmap

Sequenced so each phase is independently shippable and reviewable, and so the foundation exists before anything is built on it. Phase 0 first is not optional — every later phase is unverifiable without it.

### Phase 0 — Feedback loop (closes UX-B3)
Add `@Preview` for both panes and all nine dialogs, with `@PreviewFontScale` and `@PreviewScreenSizes`. Wire `app/src/androidTest` (dependencies already declared, sourcing nothing). Add JVM screenshot tests (Roborazzi/Paparazzi) so layout regressions fail in CI.
**Gate:** every dialog and pane renders in the IDE at font scale 1.0 and 2.0, in both orientations, without a device.

### Phase 1 — Complete the design system (closes UX-M1, M3, M4; UX-B2)
Write all fifteen type roles plus a tabular-figure `readout` role. Add `Shapes()`. Add `NvhSpacing`. Extend `ci/checks.sh` with the gates that already work for colour: no raw `fontSize =`, no raw `RoundedCornerShape(`, no raw `dp` outside the theme package. Then mechanically replace all 159 font literals and 275 dp literals, raising every sub-11.sp site to the new floor.
**Gate:** `ci/checks.sh` passes with the three new gates armed; screenshot diffs reviewed.

### Phase 2 — Iconography (closes UX-M2, UX-D10, UX-D11, UX-N3)
Add the icon dependency or author ~20 vectors. Strip every emoji from `strings.xml`. Convert status glyphs to vector shapes preserving the shape-as-second-channel invariant. Vectorise the logo. Keep every existing `contentDescription`.
**Gate:** zero emoji in `res/`, zero `Text("<glyph>")` used as an icon; TalkBack pass unchanged.

### Phase 3 — Component layer (closes UX-D1, D3, D4, D5, D12, D13)
Extract `NvhButton` (with a real primary/secondary/tertiary hierarchy), `NvhCard`, `NvhSection`, `NvhReadout`, `NvhStatusChip`, `NvhEmptyState`. Define the tonal elevation ladder. Reduce `MainScreen.kt` by moving both panes and the chrome into their own files.
**Gate:** `AppScreen` under ~300 lines; no colour, size or padding literal at any call site.

### Phase 4 — Layout & screen space (closes UX-B1, M5, M9, M10, M11; UX-D2, D6, D7, D8)
Replace both `Popup`s with `DropdownMenu`/`ModalBottomSheet` (fixes the density bug). Rebuild the bottom bar as `NavigationBar` + FAB. Add `material3-window-size-class` and branch on `WindowWidthSizeClass`. Delete the dead nested weights and the dead `themes.xml` items; handle insets explicitly. Add canvas immersive mode and a draggable pane splitter.
**Gate:** correct layout verified on compact/medium/expanded width classes at font scale 1.0 and 2.0; menus anchored correctly on 1×, 2× and 3× densities.

### Phase 5 — Controls & motion (closes UX-M6, M7, M8; UX-N6)
Convert mode toggles to `SegmentedButton`/`FilterChip`/`IconToggleButton`. Unify dialogs on one `NvhDialog` and move the three large ones to `ModalBottomSheet`. Add `AnimatedContent` on report mode, `AnimatedVisibility` on the player bar and export button, `animateColorAsState` on every mode-dependent colour. Add haptics to freeze/record/mode-switch.
**Gate:** no state change in the app happens without a transition; screenshot tests cover both ends of each.

### Phase 6 — Polish (remaining Minor findings)
UX-N1, N2, N4, N5, N7, plus UX-D9 (English default with `values-fr/`) if internationalisation is in scope.

---

## 8. Method & reproducibility

Every quantitative claim above is reproducible from the repository root:

```bash
# 159 font-size literals; 23 below 11.sp
grep -rn "fontSize = [0-9.]*\.sp" app/src/main/java | wc -l
grep -rn "fontSize = \(9\|9\.5\|10\|10\.5\)\.sp" app/src/main/java

# Zero icons, no icon dependency
grep -rn "Icon(\|Icons\." app/src/main/java | wc -l
grep -rn "icons" app/build.gradle.kts gradle/libs.versions.toml

# 275 dp literals; 8 corner radii; no Shapes()
grep -rho "[0-9.]\+\.dp" app/src/main/java | wc -l
grep -rho "RoundedCornerShape([0-9]*\.dp)" app/src/main/java | sort | uniq -c
grep -rn "shapes =" app/src/main/java

# 6 animation calls
grep -rho "animate[A-Za-z]*\|AnimatedVisibility\|Crossfade" app/src/main/java | sort | uniq -c

# Pixel-unit popup offsets
grep -rn "IntOffset" app/src/main/java/com/example/nvhspectro/MainScreen.kt

# 30+ emoji in translatable strings
grep -nP "<string[^>]*>.*[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]" app/src/main/res/values/strings.xml

# Zero previews, zero instrumented tests
grep -rn "@Preview" app/src/main/java | wc -l
ls app/src/androidTest
```

**Not covered by this audit:** runtime rendering was not observed on a physical device or emulator — findings are from source analysis, and the memory note *"green unit tests missed a crash and two layout bugs; run the app before claiming a fix"* applies with full force to every fix proposed here. Canvas-internal drawing quality (`SpectrogramColormap.kt`, 1,206 lines: axis rendering, colormap gradients, overlay legibility on the measurement surface) deserves its own dedicated pass and is deliberately excluded — it is measurement presentation rather than app chrome, and judging it properly requires looking at real spectrograms.
