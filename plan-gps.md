# Plan d'amélioration GNSS/GPS — vitesse et synchronisation NVH

**Entrée :** `audit-gps.md`

**Périmètre produit :** Android + récepteur GNSS/GPS interne uniquement

**Objectif :** obtenir une vitesse horodatée, qualifiée et accompagnée de son
incertitude, exploitable de façon défendable pour le calcul RPM/H1/ordres.

**Date :** 2026-08-26

Ce plan complète les étapes 2.4, 3.1–3.7 et 5.3 du plan AAA principal. Il ne crée
pas une source de vitesse externe. Une instrumentation externe est autorisée
uniquement pendant la validation pour établir la vérité terrain.

---

## 1. Définition de terminé

Le chantier GNSS est terminé lorsque :

1. aucune valeur périmée ou invalide ne pilote RPM, H1 ou un ordre ;
2. la vitesse est évaluée au timestamp de capture du centre de la frame audio ;
3. chaque estimation expose vitesse, accélération, âge, covariance et validité ;
4. `speedAccuracyMetersPerSecond` est intégrée mathématiquement au filtre ;
5. les valeurs aberrantes sont rejetées statistiquement et la reprise est cohérente ;
6. l'incertitude vitesse est propagée jusqu'à la fréquence d'ordre ;
7. le mode full-tracking est conservé ou rejeté sur preuve A/B ;
8. les rapports différés utilisent une reconstruction adaptée, sans confondre
   prédiction LIVE et vérité mesurée ;
9. trois téléphones représentatifs passent le protocole terrain ;
10. le budget d'erreur et les limites d'emploi sont documentés.

---

## 2. Principes d'implémentation

- `Location.speed` de `GPS_PROVIDER` reste la référence portable initiale.
- Aucun calcul `distance / temps` entre positions ne devient la source principale.
- Tous les temps de calcul utilisent BOOTTIME/`elapsedRealtimeNanos`.
- Une valeur sans incertitude connue est explicitement `DEGRADED`, jamais supposée
  précise.
- Une couleur UI ne remplace pas une règle de validité dans le moteur de calcul.
- Les seuils sont nommés, testés et calibrés sur données ; aucun nouveau nombre
  magique dans `MainViewModel`.
- La logique pure réside dans de petites classes destinées au futur module `:core`.
- Le pipeline LIVE reste causal. Le lissage utilisant le futur est réservé aux
  analyses et rapports différés.
- La solution GNSS brute est optionnelle et ne bloque pas la correction P0.

---

## 3. Modèle de données cible

Créer des contrats explicites avant de remplacer l'algorithme :

```text
GnssSpeedSample
  fixTimeNanos
  callbackTimeNanos
  speedMps
  speedSigmaMps?
  elapsedTimeSigmaNanos?
  source
  isMock
  satellitesUsed
  meanUsedInFixCn0DbHz?
  constellationsUsed
  dualFrequencyObserved

SpeedEstimate
  estimateTimeNanos
  lastFixTimeNanos
  speedMps
  accelerationMps2
  speedSigmaMps
  accelerationSigmaMps2
  ageSinceFixNanos
  validity = VALID | PREDICTED | DEGRADED | INVALID
  rejectionReason?

CapturedAudioFrame
  pcm
  firstSampleTimeNanos
  centerTimeNanos
  sampleRateHz
  sequenceNumber
```

`TelemetryData` ne doit plus utiliser `0` pour représenter à la fois une vitesse
nulle et une absence de vitesse. La validité porte cette distinction.

---

## 4. Phase GPS-0 — Caractérisation et contrats *(≈ 1–2 jours)*

Cette phase ne change pas encore le comportement utilisateur.

| Étape | Travail | Constats couverts |
|---|---|---|
| GPS-0.1 | Introduire les types purs `GnssSpeedSample`, `SpeedEstimate`, `EstimateValidity` et les raisons de rejet | GPS-01, 04, 09 |
| GPS-0.2 | Extraire une interface `SpeedEstimator` avec `update(sample)`, `estimateAt(time)` et `reset()` | GPS-04, 05, 08 |
| GPS-0.3 | Ajouter des tests qui figent les défauts actuels : vitesse conservée après 60 s, fix très imprécis accepté, reprise de mode sans reset, second outlier incohérent accepté | GPS-01, 02, 06, 08 |
| GPS-0.4 | Étendre le logger terrain en schéma v2 : temps fix, temps callback, vitesse brute, σv, état de l'estimateur, covariance, validité, motif de rejet et identifiant appareil anonymisé | GPS-13 |
| GPS-0.5 | Documenter les unités et les bases temporelles dans les en-têtes de classes | GPS-03, 04 |

### Gate GPS-0

- tests historiques et nouveaux tests verts ;
- aucun changement des sorties LIVE ;
- sérialisation d'une trace v2 testée par aller-retour ;
- aucune sentinelle numérique utilisée pour représenter l'absence de mesure.

---

## 5. Phase GPS-1 — Intégrité P0 et temps audio *(≈ 2–4 jours)*

### GPS-1.1 — Validité, expiration et transitions

- Réinitialiser la session de vitesse à chaque entrée/sortie du mode LIVE.
- Distinguer dernier fix brut, dernière estimation filtrée et estimation demandée.
- Autoriser la prédiction uniquement tant que :
  - l'âge reste sous l'horizon configuré ;
  - l'incertitude prédite reste sous la limite métrologique.
- Après dépassement, retourner `INVALID`, pas une vitesse figée.
- Interdire le calcul cinématique lorsque l'estimation est `INVALID`.
- Définir explicitement si `PREDICTED` est autorisé pour l'affichage, le suivi
  automatique et l'export.

Constats : GPS-01, GPS-08, GPS-09.

### GPS-1.2 — Timestamp de capture audio

- Remplacer `Flow<ShortArray>` par `Flow<CapturedAudioFrame>`.
- Utiliser `AudioRecord.getTimestamp(AudioTimestamp, TIMEBASE_BOOTTIME)`.
- Construire une relation entre `framePosition` et `nanoTime`.
- Calculer le temps du premier échantillon et du centre de chaque fenêtre FFT à
  partir de cet ancrage et de la fréquence d'échantillonnage réelle.
- Prévoir un fallback explicitement moins précis lorsque le timestamp matériel est
  temporairement indisponible ; journaliser ce mode.
- Appeler `estimateAt(audioFrame.centerTimeNanos)` et non « estimate now ».

Constat : GPS-03.

### GPS-1.3 — Qualification minimale des fixes

Avant toute mise à jour de l'estimateur :

- exiger `hasSpeed()` et une valeur finie, positive ou nulle ;
- vérifier la monotonie et l'âge du fix ;
- rejeter ou signaler `Location.isMock` selon le mode de test ;
- exiger la localisation précise pour le mode métrologique ;
- conserver `speedAccuracyMetersPerSecond` lorsqu'elle existe ;
- sans précision de vitesse disponible, affecter une variance conservatrice et
  classer l'échantillon `DEGRADED` ;
- ne pas utiliser la précision horizontale comme équivalent mathématique de la
  précision de vitesse.

Constats : GPS-02, GPS-12.

### Tests GPS-1

- `gps01_staleEstimate_becomesInvalidAfterConfiguredHorizon`
- `gps01_staleEstimate_neverDrivesOrderTracking`
- `gps03_audioFrames_haveMonotonicCaptureTimestamps`
- `gps03_estimate_isEvaluatedAtAudioFrameCenter`
- `gps08_liveModeRestart_requiresFreshFix`
- `gps09_invalidEstimate_doesNotProduceRpmOrH1`
- `gps12_mockFix_isFlaggedAndExcludedFromMeasurementMode`
- `gps12_coarsePermission_disablesMetrologicalSpeed`

### Gate GPS-1

- perte GNSS simulée : aucune vitesse utilisable après l'horizon configuré ;
- sortie puis retour LIVE : aucune ancienne vitesse avant le premier nouveau fix ;
- retard artificiel du consommateur DSP : l'association vitesse/audio reste basée
  sur le timestamp de capture ;
- l'interface distingue clairement vitesse nulle et vitesse indisponible ;
- test sur appareil API 24, API 31+ et API cible.

---

## 6. Phase GPS-2 — Estimateur probabiliste *(≈ 3–5 jours)*

### GPS-2.1 — Filtre de Kalman vitesse/accélération

Remplacer l'α-β fixe par un filtre linéaire à état :

```text
x = [vitesse, accélération]
F(dt) = [[1, dt],
         [0,  1]]
z = vitesse GNSS Android
H = [1, 0]
R = max(σv², variancePlancher)
```

Pour un bruit blanc de jerk de densité `q`, utiliser une covariance de processus
dépendante de `dt` :

```text
Q = q × [[dt³/3, dt²/2],
         [dt²/2, dt]]
```

Exigences :

- calcul en `Double` dans le cœur ;
- covariance symétrique et définie positive ;
- mise à jour stable de type Joseph ;
- `dt` variable ;
- paramètres regroupés dans une configuration nommée ;
- aucune saturation silencieuse servant de substitut à la validité.

Constats : GPS-02, GPS-04, GPS-05, GPS-14.

### GPS-2.2 — Rejet robuste et réacquisition

- Calculer innovation `y`, variance d'innovation `S` et score normalisé `NIS=y²/S`.
- Rejeter statistiquement un fix incohérent avec son incertitude déclarée.
- Après rejet, augmenter l'incertitude de prédiction normalement.
- Réacquérir uniquement après plusieurs fixes cohérents entre eux ou après une perte
  assez longue pour justifier un réamorçage complet.
- Journaliser le NIS et le motif de rejet.

Constat : GPS-06.

### GPS-2.3 — Arrêt, faible vitesse et accélération

- Définir un état stationnaire lorsque la vitesse est indiscernable de zéro au vu de
  son incertitude.
- Utiliser une hystérésis pour éviter l'alternance arrêt/mouvement.
- Ne publier l'accélération GNSS que si son incertitude est sous une limite définie.
- Renommer partout la grandeur en « accélération GNSS estimée ».

Constat : GPS-14.

### Tests GPS-2

Jeux déterministes à cadences 1, 5 et 10 Hz :

- vitesse constante avec bruit hétéroscédastique ;
- rampes ±0,5, ±1, ±3 et freinage −6 m/s² ;
- stop-and-go proche de zéro ;
- intervalles irréguliers ;
- un outlier, deux outliers incohérents, changement réel soutenu ;
- pertes de 1, 2, 5, 30 et 60 s ;
- reprise à une vitesse très différente ;
- incertitude variant de 0,05 à 5 m/s ;
- invariants de covariance et absence de NaN/Inf.

### Gate GPS-2

- aucune mesure à forte incertitude ne reçoit le même poids qu'une mesure précise ;
- aucun second outlier incohérent n'est accepté aveuglément ;
- l'incertitude croît pendant une perte de signal ;
- les traces synthétiques montrent moins de bruit que `Location.speed` sans délai
  artificiel ajouté ;
- les paramètres initiaux sont enregistrés comme provisoires jusqu'au Gate GPS-5.

---

## 7. Phase GPS-3 — Acquisition GNSS maximale et diagnostics *(≈ 2–4 jours)*

### GPS-3.1 — Requête GPS explicite

- Garder `LocationManager.GPS_PROVIDER` comme source métrologique.
- Sur API 31+, utiliser `android.location.LocationRequest` avec :
  - qualité haute précision ;
  - intervalle demandé adapté au mode mesure, zéro si la cadence maximale est
    réellement souhaitée ;
  - aucun batching ;
  - distance minimale nulle.
- Garder le chemin legacy pour API 24–30.
- Livrer les callbacks sur un exécuteur GNSS dédié, pas sur le main thread.
- Mesurer et journaliser `callbackTime - fixTime`.

### GPS-3.2 — États provider et permission

- Réagir aux changements d'activation du provider.
- Ne pas prétendre avoir un secours GNSS lorsqu'aucun fix GPS n'est disponible.
- Isoler tout Fused éventuel sous une source `INFORMATION_ONLY`.
- Interdire qu'un fix Fused non qualifié alimente RPM/H1/ordres.
- Exposer un message clair en cas de localisation approximative seulement.

Constats : GPS-07, GPS-12.

### GPS-3.3 — Qualité GNSS observable

Enregistrer via `GnssStatus.Callback` :

- satellites vus et utilisés dans le fix ;
- C/N0 moyen et médian des satellites utilisés ;
- constellations utilisées ;
- présence de fréquences L1/L5 lorsqu'exposées ;
- périodes sans fix et changements de capacité.

Ces données servent au diagnostic et à l'analyse des traces. Elles ne remplacent
pas arbitrairement la variance `speedAccuracy`.

### GPS-3.4 — Full-tracking contrôlé

- Sur API 31+, demander `GnssMeasurementRequest.setFullTracking(true)` uniquement
  pendant une session de mesure active.
- Consommer le callback au minimum pour produire les diagnostics de signal et
  vérifier la cadence ; ne pas conserver un callback réellement vide.
- Prévoir un interrupteur de test A/B et enregistrer l'état full-tracking dans les
  traces.
- Mesurer précision, continuité, délai, température et consommation.
- Choisir l'activation par défaut uniquement après le Gate GPS-5.

Constat : GPS-11.

### GPS-3.5 — Matrice de capacités

Au démarrage de session, enregistrer :

- version Android et modèle de téléphone ;
- année/modèle matériel GNSS quand disponible ;
- support des mesures brutes ;
- support multi-fréquence observé ;
- cadence effective ;
- présence des incertitudes de vitesse et de timestamp.

### Gate GPS-3

- la chaîne métrologique reçoit uniquement `GPS_PROVIDER` ;
- activation/désactivation du GPS en cours de session gérée sans valeur fantôme ;
- mode approximatif testé ;
- full-tracking actif uniquement en mesure et correctement libéré ;
- zéro callback ou ressource GNSS restante hors LIVE ;
- traces A/B collectées sur au moins deux chipsets différents.

---

## 8. Phase GPS-4 — Propagation aux ordres et traitement différé *(≈ 2–4 jours)*

### GPS-4.1 — Budget d'erreur cinématique

Pour chaque estimation, calculer :

```text
rpm = vitesseKmh × 1000 / V1000
σrpm = σvitesseKmh × 1000 / V1000

fHn = n × rpm / 60
σfHn = n × σrpm / 60
```

Ajouter ensuite l'incertitude de `V1000` si elle est connue. Le rayon dynamique du
pneu et les rapports de transmission peuvent dominer l'erreur GNSS ; ils doivent
être documentés séparément.

### GPS-4.2 — Fenêtre de recherche dynamique

- Construire la demi-largeur de recherche à partir d'un niveau de confiance de
  `σfHn`, plus la résolution FFT.
- Borner cette largeur pour empêcher qu'une recherche trop large sélectionne une
  raie sans rapport.
- Si l'incertitude dépasse cette borne, suspendre le suivi automatique ou afficher
  « ordre non identifiable ».
- Conserver dans les données le niveau de confiance utilisé.

Constat : GPS-10.

### GPS-4.3 — Télémétrie et export

Le schéma de télémétrie v2 doit stocker au minimum :

- temps monotone du fix et de l'audio ;
- vitesse GNSS brute et son σ ;
- vitesse estimée, accélération estimée et covariance ;
- validité et âge ;
- source/provenance ;
- qualité GNSS diagnostique ;
- version de l'algorithme et de ses paramètres.

Les anciens fichiers v1 doivent rester lisibles, avec statut d'incertitude inconnue.

### GPS-4.4 — Reconstruction différée

- Conserver le filtre causal pour le LIVE.
- Pour WAV/vidéo/rapport enregistré, appliquer un lissage avant/arrière de type RTS
  sur les fixes horodatés.
- Ne jamais réutiliser comme vérité une série de vitesses déjà extrapolées frame par
  frame.
- Comparer causal, lissé et vitesse Android brute dans les tests de replay.

### Gate GPS-4

- un fix classé précis contient la vraie fréquence d'ordre dans sa bande de confiance
  sur les jeux de référence ;
- un ordre est suspendu lorsque son incertitude devient ambiguë ;
- l'écran, la télémétrie et le PDF utilisent la même vitesse, le même timestamp et le
  même statut ;
- migration v1 → v2 testée ;
- le rapport précise si la vitesse est brute, filtrée causale ou lissée différée.

---

## 9. Phase GPS-5 — Validation terrain et réglage *(≈ 4–7 jours)*

### GPS-5.1 — Protocole

Tester au moins :

- trois téléphones : ancien API compatible, milieu de gamme actuel, haut de gamme
  bi-fréquence ;
- ciel ouvert, végétation, canyon urbain, pare-brise du véhicule et tunnel ;
- arrêt, vitesse constante, accélération, freinage et reprise après perte ;
- montage fixe du téléphone, emplacement et orientation documentés ;
- full-tracking OFF puis ON dans un parcours répété.

Une vérité terrain externe synchronisée est indispensable pour mesurer l'erreur. Elle
ne devient pas une dépendance du produit : elle sert uniquement à la validation.

### GPS-5.2 — Indicateurs

Mesurer pour chaque téléphone et scénario :

- biais, MAE, RMSE et erreur P95 de vitesse ;
- retard par corrélation pendant accélérations/freinages ;
- erreur et incertitude d'accélération ;
- taux de rejet, fausses acceptations et temps de réacquisition ;
- couverture réelle des intervalles 1σ/2σ ;
- erreur RPM et fréquence d'ordre ;
- disponibilité `VALID/PREDICTED/DEGRADED/INVALID` ;
- cadence des fixes, délai de callback et pertes ;
- consommation et échauffement avec/sans full-tracking.

### GPS-5.3 — Critères d'acceptation initiaux

Sous ciel ouvert et faible dynamique :

- erreur P95 de vitesse ≤ 0,5 m/s sur les appareils conformes ;
- biais absolu ≤ 0,1 m/s ;
- couverture statistique de l'incertitude cohérente avec le niveau annoncé ;
- aucun retard artificiel supérieur à une période GNSS ;
- aucun résultat valide après dépassement de l'horizon de perte.

En dynamique :

- le filtre ne doit pas dégrader significativement le retard de `Location.speed` ;
- le dépassement après une transition doit rester dans l'enveloppe d'incertitude ;
- la vraie fréquence d'ordre doit rester dans la bande de confiance lorsqu'un ordre
  est déclaré identifiable.

Ces seuils deviennent définitifs après la première campagne et doivent être publiés
dans `doc/VALIDATION.md` avec les limites rencontrées.

### GPS-5.4 — Réglage

- Ajuster bruit de jerk, variance plancher, seuil statistique, horizon et limites de
  validité sur le jeu d'entraînement.
- Réserver au moins un parcours/appareil comme jeu de validation non utilisé pour le
  réglage.
- Versionner les paramètres.
- Interdire un réglage spécifique à un seul téléphone sans mécanisme de profil et
  preuve de généralisation.

### Gate GPS-5

- matrice des trois appareils complétée ;
- critères d'acceptation passés ou écarts explicitement documentés ;
- décision full-tracking signée ;
- paramètres figés et versionnés ;
- budget d'erreur vitesse → RPM → fréquence publié ;
- aucune revendication de précision supérieure à la preuve collectée.

---

## 10. Phase GPS-6 optionnelle — Vitesse GNSS brute *(≈ 5–10+ jours)*

Cette phase est R&D et ne bloque pas la mise en production de la chaîne corrigée.

### Travaux

1. Capturer `GnssMeasurementsEvent`, `GnssClock`, pseudorange rates, incertitudes,
   C/N0, constellations, fréquences et états de mesure.
2. Obtenir/calculer positions et vitesses satellites ainsi que corrections d'horloge.
3. Résoudre vitesse 3D du récepteur et dérive d'horloge par moindres carrés pondérés.
4. Ajouter exclusion robuste, contrôle géométrique et diagnostics multipath.
5. Comparer simultanément :
   - `Location.speed` brut ;
   - Kalman alimenté par `Location.speed` ;
   - solution pseudorange-rate brute ;
   - vérité terrain.
6. Ne promouvoir la solution brute que si elle améliore plusieurs appareils et
   scénarios sans régression de disponibilité ni de latence.

Constat : GPS-15.

---

## 11. Décisions propriétaire

| ID | Décision | Recommandation |
|---|---|---|
| GPS-D1 | Le Fused peut-il piloter les ordres ? | Non ; information seulement tant que sa provenance n'est pas qualifiée |
| GPS-D2 | Full-tracking par défaut ? | Oui pendant une mesure, uniquement si le Gate GPS-5 confirme un bénéfice acceptable |
| GPS-D3 | Horizon maximal fixe ou fondé sur covariance ? | Les deux : plafond de sécurité fixe et arrêt plus tôt si σv dépasse la limite |
| GPS-D4 | Exporter une vitesse prédite invalide ? | Conserver éventuellement la valeur diagnostique, mais jamais comme mesure valide |
| GPS-D5 | Accélération visible sans précision ? | Non ; afficher « accélération GNSS estimée » avec qualité, sinon masquer |
| GPS-D6 | Développer immédiatement la solution GNSS brute ? | Non ; finir GPS-0 à GPS-5 et lancer GPS-6 seulement si le besoin subsiste |

---

## 12. Ordre recommandé et dépendances AAA

```text
GPS-0 → GPS-1 → GPS-2 → GPS-3 → GPS-4 → GPS-5
                                      ↘ GPS-6 optionnelle
```

- GPS-0 à GPS-2 rouvrent et complètent le plan AAA 2.4.
- Les classes pures de GPS-0/GPS-2 migrent dans `:core` avec le plan AAA 3.1.
- GPS-4 doit être coordonnée avec l'unification `OrderTrackingEngine` du plan 3.2,
  la chronologie canonique 3.4 et le schéma télémétrique 3.6.
- GPS-5 alimente directement la validation métrologique AAA 5.3.
- Aucun Gate 2 strict ne doit être considéré définitivement clos tant que GPS-01,
  GPS-02 et GPS-03 restent ouverts.

### Effort indicatif

| Phase | Effort |
|---|---:|
| GPS-0 | 1–2 j |
| GPS-1 | 2–4 j |
| GPS-2 | 3–5 j |
| GPS-3 | 2–4 j |
| GPS-4 | 2–4 j |
| GPS-5 | 4–7 j |
| **Socle qualifié** | **14–26 j** |
| GPS-6 optionnelle | 5–10+ j |

---

## 13. Traçabilité audit → plan

| Constat | Étapes propriétaires |
|---|---|
| GPS-01 | GPS-0.1, GPS-1.1 |
| GPS-02 | GPS-1.3, GPS-2.1 |
| GPS-03 | GPS-1.2 |
| GPS-04 | GPS-0.1, GPS-2.1 |
| GPS-05 | GPS-2.1, GPS-5.4 |
| GPS-06 | GPS-2.2 |
| GPS-07 | GPS-3.2 |
| GPS-08 | GPS-1.1 |
| GPS-09 | GPS-1.1, GPS-4.3 |
| GPS-10 | GPS-4.1, GPS-4.2 |
| GPS-11 | GPS-3.4, GPS-5.1–5.3 |
| GPS-12 | GPS-1.3, GPS-3.2–3.5 |
| GPS-13 | GPS-0.4, GPS-5.1–5.2 |
| GPS-14 | GPS-2.3, GPS-4.3 |
| GPS-15 | GPS-6 |

---

## 14. Risques

| Risque | Réponse |
|---|---|
| Kalman trop lissé, retard dynamique | Validation contre vitesse brute et vérité terrain ; contrainte explicite sur le retard |
| Covariance Android mal calibrée selon OEM | Mesurer la couverture par appareil ; variance plancher et profils seulement sur preuve |
| AudioTimestamp indisponible sur une route | Fallback horodaté et marqué dégradé ; test après changements de route audio |
| Full-tracking chauffe ou consomme trop | Activation limitée à la mesure, essais A/B endurance et décision documentée |
| Bande d'ordre trop large | Limite d'identifiabilité ; suspendre plutôt que sélectionner une mauvaise raie |
| Multipath accepté comme changement réel | NIS, cohérence multi-fixes, diagnostics C/N0 et validation urbaine |
| Solution GNSS brute moins bonne que le firmware | Rester optionnelle ; promotion uniquement sur résultats comparatifs |
| Réglage sur un seul téléphone | Trois appareils et jeu de validation tenu à l'écart du réglage |

---

## 15. Livrables finaux

- moteur d'estimation pur avec tests et covariance ;
- frames audio horodatées en BOOTTIME ;
- acquisition GPS pure et diagnostics GNSS ;
- décision full-tracking documentée ;
- télémétrie v2 migrable ;
- suivi d'ordre conscient de l'incertitude ;
- logger/replayer de traces versionnées ;
- `doc/VALIDATION.md` avec résultats par appareil ;
- documentation du budget d'erreur et des limites d'utilisation ;
- éventuellement, rapport R&D sur la solution pseudorange-rate brute.
