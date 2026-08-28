/* NVH Spectro — comportements partagés : nav/footer injectés, scroll-spy,
   copy-code, reveal au scroll. Sans dépendance. */
(function () {
  "use strict";

  var ROOT = document.body.dataset.root || "..";

  /* ---------------- dépôt GitHub ----------------
     Le site est publié depuis plusieurs comptes (Luigi-BARTH = dépôt de
     référence, miroirs de développement). On déduit le dépôt de l'hôte
     `<compte>.github.io/<repo>/` pour que « GitHub ↗ » pointe toujours vers
     le dépôt qui sert la page ; sinon on retombe sur le dépôt de référence. */
  var REPO = (function () {
    var CANON = "https://github.com/Luigi-BARTH/NVHSpectro";
    var owner = /^([a-z0-9-]+)\.github\.io$/.exec(location.hostname.toLowerCase());
    if (!owner) return CANON;
    var seg = location.pathname.split("/").filter(Boolean)[0] || "";
    if (!seg || /\./.test(seg) || seg === "docs" || seg === "assets") seg = "NVHSpectro";
    return "https://github.com/" + owner[1] + "/" + seg;
  })();

  /* ---------------- nav ---------------- */
  var NAV = '' +
    '<nav class="nav"><div class="wrap nav-inner">' +
    '<a class="nav-brand" href="' + ROOT + '/index.html"><img src="' + ROOT + '/assets/icon.webp" alt="" width="32" height="32">NVH&nbsp;Spectro</a>' +
    '<div class="nav-links" id="navlinks">' +
    '<a href="' + ROOT + '/index.html" data-match="index">Accueil</a>' +
    '<a href="' + ROOT + '/docs/index.html" data-match="docs">Documentation</a>' +
    '<a href="' + ROOT + '/docs/reference.html" data-match="reference">Référence API</a>' +
    '<a href="' + ROOT + '/index.html#honest" data-match="">Précision</a>' +
    '<a class="nav-cta" href="' + REPO + '">GitHub ↗</a>' +
    '</div></div></nav>';
  var navSlot = document.getElementById("nav-slot") || document.body;
  navSlot.insertAdjacentHTML("afterbegin", NAV);

  var path = location.pathname;
  document.querySelectorAll("#navlinks a").forEach(function (a) {
    var m = a.dataset.match;
    if (!m) return;
    if ((m === "docs" && /\/docs\/(?!reference)/.test(path)) ||
        (m === "reference" && /reference/.test(path)) ||
        (m === "index" && !/\/docs\//.test(path) && /index\.html$|\/$/.test(path))) {
      a.classList.add("active");
    }
  });

  /* ---------------- footer ---------------- */
  var FOOTER = '' +
    '<footer><div class="wrap foot-grid">' +
    '<div style="display:flex;align-items:center;gap:14px">' +
    '<img src="' + ROOT + '/assets/icon.webp" alt="" width="28" height="28" style="border-radius:6px">' +
    '<div><div style="font-weight:700">NVH Spectro v14.0.0</div>' +
    '<div style="font-size:13px;color:var(--muted)">Analyse NVH véhicule sur Android — open source</div></div></div>' +
    '<div style="display:flex;align-items:center;gap:18px;flex-wrap:wrap">' +
    '<a href="' + ROOT + '/docs/index.html" style="font-weight:600">Documentation</a>' +
    '<a href="' + REPO + '" style="font-weight:600">GitHub ↗</a>' +
    '<img src="' + ROOT + '/assets/logo-vibratec.png" alt="Vibratec — Everenn Group" height="30"></div></div>' +
    '<div class="wrap foot-note">NVH Spectro est un instrument d’aide à l’essai : l’indice d’émergence affiché n’est pas un TTNR ECMA-74 ' +
    'et les constantes de la chaîne GNSS sont en cours de validation terrain (voir l’audit V13.2 du dépôt).</div></footer>';
  document.body.insertAdjacentHTML("beforeend", FOOTER);

  /* ------------- liens dépôt dans les pages ------------- */
  document.querySelectorAll("[data-repo-link]").forEach(function (a) { a.href = REPO; });
  document.querySelectorAll("[data-repo-clone]").forEach(function (el) { el.textContent = REPO + ".git"; });

  /* ---------------- copy buttons ---------------- */
  document.querySelectorAll(".codebox pre").forEach(function (pre) {
    var btn = document.createElement("button");
    btn.className = "copy-btn";
    btn.type = "button";
    btn.textContent = "Copier";
    btn.addEventListener("click", function () {
      navigator.clipboard.writeText(pre.innerText).then(function () {
        btn.textContent = "✓ Copié";
        setTimeout(function () { btn.textContent = "Copier"; }, 1600);
      });
    });
    pre.parentNode.appendChild(btn);
  });

  /* ---------------- reveal on scroll ---------------- */
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
      });
    }, { threshold: 0.12 });
    document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });
  } else {
    document.querySelectorAll(".reveal").forEach(function (el) { el.classList.add("in"); });
  }

  /* ---------------- docs scroll-spy ---------------- */
  var toc = document.querySelectorAll(".toc-link");
  if (toc.length && "IntersectionObserver" in window) {
    var heads = [];
    toc.forEach(function (a) {
      var id = a.getAttribute("href").split("#")[1];
      if (id) { var h = document.getElementById(id); if (h) heads.push([h, a]); }
    });
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          toc.forEach(function (a) { a.classList.remove("active"); });
          for (var i = 0; i < heads.length; i++) {
            if (heads[i][0] === e.target) { heads[i][1].classList.add("active"); break; }
          }
        }
      });
    }, { rootMargin: "-15% 0px -70% 0px" });
    heads.forEach(function (p) { spy.observe(p[0]); });
  }
})();
