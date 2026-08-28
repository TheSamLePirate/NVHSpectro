#!/usr/bin/env python3
"""Generate docs/reference.html from the real Kotlin source.

Parses core/src/main and app/src/main, extracts every non-private type
(class / object / interface / enum) and its non-private functions with
their KDoc summary, groups them by domain area, and writes a static,
searchable reference page. Re-run after API changes:

    python3 site/tools/gen_reference.py
"""
import html
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC_DIRS = [os.path.join(ROOT, "core/src/main"), os.path.join(ROOT, "app/src/main")]

AREAS = [
    ("DSP & analyse spectrale", ["FFTProcessor", "LiveAnalysisEngine", "AudioConfig",
                                 "BiQuadFilter", "FilterChain", "FilterType", "AudioFilter"]),
    ("Chaîne GNSS / vitesse", ["SpeedProvider", "GnssSpeedSession", "KalmanSpeedEstimator",
                               "AlphaBetaSpeedEstimator", "SpeedEstimator", "SpeedEstimation",
                               "GnssDiagnostics", "RtsSpeedSmoother", "SpeedReconstruction",
                               "GnssDiagnosticsMonitor"]),
    ("Suivi d'ordres & cinématique", ["OrderTrackingEngine", "OrderSearchPolicy", "KinematicsData",
                                      "SmartPathTracker", "SmartTrackedOrder", "PlotGeometry",
                                      "TimelineMapper"]),
    ("Session & analyse de fichiers", ["MeasurementSession", "WavAnalysis", "Telemetry.kt",
                                       "AudioFrameClock", "CapturedAudioFrame"]),
    ("Capture, stockage & persistence", ["CaptureEngine", "AudioRepository", "RecordingStore",
                                         "SettingsStore", "WavDataReader", "WavReadMessages",
                                         "WavAudioWriter", "VideoAudioExtractor", "TelemetryCodec",
                                         "FieldTraceV2", "FieldLocationLogger", "DiagnosticLog",
                                         "LoadedWavData", "NumberParsing"]),
]

UI_AREA = "UI, ViewModels & exports"


def area_for(fname):
    for area, keys in AREAS:
        for k in keys:
            if k in fname:
                return area
    return UI_AREA


ORDER = [a for a, _ in AREAS] + [UI_AREA]

TYPE_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+|private\s+|internal\s+|abstract\s+|open\s+|data\s+|sealed\s+|value\s+|enum\s+)*"
    r"(?P<kw>enum class|class|interface|object)\s+(?P<name>\w+)"
)
FUN_RE = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s+|private\s+|internal\s+|override\s+|suspend\s+|inline\s+|operator\s+)*fun\s+(?P<name>\w+)")


def kdoc_above(lines, decl_idx):
    """Collect the KDoc block ending just above the declaration (annotations allowed between)."""
    i = decl_idx - 1
    while i >= 0 and (lines[i].strip().startswith("@") or not lines[i].strip()):
        i -= 1
    if i < 0 or not lines[i].strip().endswith("*/"):
        return ""
    buf = []
    while i >= 0:
        s = lines[i].strip()
        if s.startswith("/**"):
            first = s[3:].strip()
            if first.endswith("*/"):
                first = first[:-2]
            first = first.strip().lstrip("*").strip()
            if first:
                buf.append(first)
            break
        s = s[:-2].strip().lstrip("*").strip() if s.endswith("*/") else s.lstrip("*").strip()
        if s and not s.startswith("@"):
            buf.append(s)
        i -= 1
    summary = " ".join(reversed(buf))
    summary = re.sub(r"\s+", " ", summary)
    summary = re.sub(r"\[([^\]]+)\]", r"\1", summary)
    # cut at the first @tag if the block had no blank-line separation
    summary = re.split(r"\s@[A-Za-z]", summary)[0]
    return summary.strip()


def full_signature(lines, decl_idx):
    """Join the (possibly multi-line) signature: from the decl line to balanced parens."""
    parts, depth, j = [], 0, decl_idx
    while j < len(lines) and j < decl_idx + 12:
        seg = re.sub(r"//.*$", "", lines[j]).strip()
        seg = re.sub(r"/\*.*?\*/", "", seg).strip()
        parts.append(seg)
        depth += seg.count("(") - seg.count(")")
        if depth <= 0 and ")" in " ".join(parts):
            break
        j += 1
    sig = " ".join(parts)
    sig = re.sub(r"\s+", " ", sig)
    # cut body remnants
    sig = re.split(r"\}\s*return|\breturn\b", sig)[0].strip()
    m = re.search(r"fun\s+\w+\s*(\(.*\))", sig)
    params = m.group(1) if m else "()"
    ret = ""
    rm = re.search(r"\)\s*(?::\s*([^=]+?))?(?:\s*=|\s*\{)?$", sig)
    if rm and rm.group(1):
        ret = ": " + rm.group(1).strip()
    return params + ret


def parse_file(path):
    rel = os.path.relpath(path, ROOT)
    short = (rel.replace("core/src/main/kotlin/com/example/nvhspectro/", "core/")
                .replace("app/src/main/java/com/example/nvhspectro/", "app/"))
    lines = open(path, encoding="utf-8").read().splitlines()
    types, current = [], None
    for i, line in enumerate(lines):
        s = line.strip()
        if not s or s.startswith("//"):
            continue
        tm = TYPE_RE.match(s)
        if tm:
            current = {"name": tm.group("name"),
                       "kind": "enum" if tm.group("kw") == "enum class" else tm.group("kw"),
                       "file": short, "doc": kdoc_above(lines, i), "members": []}
            types.append(current)
            continue
        fm = FUN_RE.match(line)
        if fm and "private" not in line.split("fun")[0] and "internal" not in line.split("fun")[0]:
            if current is None:
                current = {"name": os.path.basename(path)[:-3] + ".kt", "kind": "fichier",
                           "file": short, "doc": "", "members": []}
                types.append(current)
            current["members"].append({
                "name": fm.group("name"),
                "sig": fm.group("name") + full_signature(lines, i),
                "doc": kdoc_above(lines, i),
            })
    return types


def main():
    all_types = []
    for src in SRC_DIRS:
        for dirpath, _, files in os.walk(src):
            for f in sorted(files):
                if f.endswith(".kt"):
                    all_types.extend(parse_file(os.path.join(dirpath, f)))

    areas = {}
    for t in all_types:
        areas.setdefault(area_for(t["file"]), []).append(t)
    for a in areas:
        areas[a].sort(key=lambda t: t["name"].lower())

    total = sum(len(t["members"]) for t in all_types)

    parts = ["""<!DOCTYPE html>
<html lang="fr"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Référence API — NVH Spectro</title>
<meta name="description" content="Référence complète de l'API NVH Spectro : chaque classe, objet et fonction publique, extraits du source.">
<link rel="icon" type="image/webp" href="../assets/icon.webp">
<link rel="stylesheet" href="../assets/site.css">
</head><body>
<!--NAV-->
<main class="docs-main"><div class="wrap docs-grid">
<aside class="docs-toc">
  <div class="toc-title">Sur cette page</div>
"""]
    for i, a in enumerate(ORDER):
        if a in areas:
            n = len(areas[a])
            parts.append(f'<a href="#area-{i}" class="toc-link">{html.escape(a)} <span class="toc-n">{n}</span></a>\n')
    parts.append(f"""  <div class="toc-stats">{len(all_types)} types · {total} fonctions · généré du source</div>
  <input id="ref-search" type="search" placeholder="Filtrer…" autocomplete="off" aria-label="Filtrer la référence">
</aside>
<article class="docs-content">
<nav class="crumbs"><a href="../index.html">Accueil</a> › <a href="index.html">Docs</a> › Référence API</nav>
<h1>Référence API</h1>
<p class="lead">Chaque type et chaque fonction publique du projet — <b>extraits directement du
source Kotlin</b> par <code>site/tools/gen_reference.py</code> (commité : la référence est
régénérable et donc vérifiable). Les mentions entre crochets renvoient aux audits du dépôt.</p>
""")

    for i, a in enumerate(ORDER):
        if a not in areas:
            continue
        parts.append(f'<h2 id="area-{i}" class="ref-area">{html.escape(a)}</h2>\n')
        for t in areas[a]:
            searchable = html.escape((t["name"] + " " + " ".join(
                m["name"] + " " + m["doc"] for m in t["members"])).lower(), quote=True)
            members = ""
            for m in t["members"]:
                if m["doc"]:
                    members += (f'<div class="ref-fun"><code class="ref-sig">{html.escape(m["sig"])}</code>'
                                f'<div class="ref-doc">{html.escape(m["doc"])}</div></div>')
                else:
                    members += f'<div class="ref-fun"><code class="ref-sig">{html.escape(m["sig"])}</code></div>'
            if not members:
                members = '<div class="ref-doc ref-muted">type de données (porteur d\'état, sans méthode publique)</div>'
            doc = f'<p class="ref-doc ref-typedoc">{html.escape(t["doc"])}</p>' if t["doc"] else ""
            parts.append(f"""<details class="ref-type" data-s="{searchable}">
<summary><span class="ref-kind">{t['kind']}</span><span class="ref-name">{html.escape(t['name'])}</span><span class="ref-file">{html.escape(t['file'])}</span></summary>
{doc}
{members}
</details>
""")

    parts.append("""
<nav class="pager"><a href="quality.html" class="pager-prev">← Qualité & CI</a><span></span></nav>
</article></div></main>
<!--FOOTER-->
<script src="../assets/site.js"></script>
<script>
(function(){var q=document.getElementById('ref-search');if(!q)return;
q.addEventListener('input',function(){var v=q.value.toLowerCase();
document.querySelectorAll('.ref-type').forEach(function(d){
d.style.display=(!v||d.dataset.s.indexOf(v)>=0)?'':'none';});
document.querySelectorAll('.ref-area').forEach(function(h){
var vis=false,sec=document.getElementById(h.id).nextElementSibling;
while(sec&&!sec.classList.contains('ref-area')){if(sec.style.display!=='none')vis=true;sec=sec.nextElementSibling;}
h.style.display=vis?'':'none';});});})();
</script>
</body></html>""")

    dest = os.path.join(ROOT, "site/docs/reference.html")
    open(dest, "w", encoding="utf-8").write("".join(parts))
    print(f"reference.html written: {len(all_types)} types, {total} functions")


if __name__ == "__main__":
    main()
