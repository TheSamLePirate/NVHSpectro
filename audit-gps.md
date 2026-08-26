# Audit GNSS/GPS — qualité de la mesure de vitesse

**Projet :** NVH Spectro v13.2.0

**Référence auditée :** branche `aaa/phase0`, commit `3fc1dd2`

**Date :** 2026-08-26

**Périmètre :** vitesse issue exclusivement du téléphone Android et de son
récepteur GNSS/GPS interne. Aucun récepteur externe, OBD, CAN, service réseau ou
IMU n'est retenu comme source de vitesse de production.

Ce document est un audit figé de l'existant. Le plan de correction associé est
`plan-gps.md`.

---

## 1. Conclusion exécutive

La chaîne actuelle est une **bonne fondation logicielle**, mais elle ne constitue
pas encore la meilleure implémentation possible pour une mesure NVH défendable.
Les bons choix de base sont présents : `GPS_PROVIDER`, `Location.speed`, temps
monotone, prédiction entre fixes et arrêt du GPS hors mode LIVE.

Les faiblesses restantes sont toutefois directement métrologiques :

1. une vitesse périmée peut continuer à piloter le suivi d'ordre indéfiniment ;
2. l'incertitude `speedAccuracyMetersPerSecond` n'influence pas l'estimateur ;
3. la vitesse est alignée sur l'instant de traitement DSP, pas sur l'instant de
   capture du buffer audio ;
4. le filtre α-β est fixe, sans covariance et explicitement provisoire ;
5. la provenance GNSS du secours Fused n'est pas garantie par l'API ;
6. l'incertitude de vitesse n'est pas propagée vers l'incertitude RPM/fréquence ;
7. aucune validation terrain avec vérité de référence n'établit encore l'erreur,
   le retard ou la robustesse en multipath.

**Verdict :** la chaîne peut servir au développement et aux essais exploratoires,
mais ne doit pas encore être présentée comme une mesure de vitesse ou de régime
qualifiée. La priorité n'est pas d'ajouter un algorithme GNSS complexe : elle est
d'abord de rendre la validité, le temps et l'incertitude corrects de bout en bout.

### Appréciation par domaine

| Domaine | Appréciation | Motif principal |
|---|---|---|
| Source GNSS | B+ | `GPS_PROVIDER` et `Location.speed` sont les bons choix portables |
| Base temporelle GNSS | A− | `elapsedRealtimeNanos` est utilisé correctement |
| Synchronisation GNSS/audio | F | aucun timestamp de capture dans les frames audio |
| Estimation vitesse/accélération | C | α-β propre mais fixe, provisoire et sans covariance |
| Gestion de l'incertitude | D | l'incertitude ne sert qu'au voyant |
| Perte de signal et validité | D | valeur figée réutilisable après expiration du fix |
| Provenance et intégrité | C− | secours Fused ambigu, mock/cached non qualifiés |
| Propagation vers RPM/ordres | D | bande ±1 bin indépendante de l'erreur de vitesse |
| Validation terrain | D | traces disponibles, mais aucune vérité terrain ni matrice appareil |

---

## 2. Chaîne actuelle

La chaîne LIVE réalise aujourd'hui :

1. abonnement direct à `LocationManager.GPS_PROVIDER` si celui-ci est activé ;
2. abonnement au Fused Location Provider uniquement si le provider GPS est
   désactivé au démarrage ;
3. alimentation d'un estimateur α-β avec `Location.speed` et
   `Location.elapsedRealtimeNanos` ;
4. extrapolation `v + a × dt` à l'instant courant de traitement de chaque frame
   audio ;
5. conversion de cette vitesse dite « théorique » en RPM, H1 et fréquence
   d'ordre ;
6. recherche du pic spectral dans une fenêtre fixe de ±1 bin.

Fichiers principaux :

- `app/src/main/java/com/example/nvhspectro/SpeedProvider.kt` ;
- `app/src/main/java/com/example/nvhspectro/data/AlphaBetaSpeedEstimator.kt` ;
- `app/src/main/java/com/example/nvhspectro/CaptureEngine.kt` ;
- `app/src/main/java/com/example/nvhspectro/MainViewModel.kt` ;
- `app/src/main/java/com/example/nvhspectro/data/KinematicsData.kt`.

---

## 3. Points forts à préserver

### GPS-S01 — Utilisation de la vitesse fournie par Android

`Location.speed` est préférable à une vitesse reconstruite par différence entre
deux positions. Android précise que cette vitesse peut notamment tenir compte du
Doppler GNSS et être plus exacte que `distance / temps`.

La documentation ne garantit cependant pas que la valeur soit un « Doppler brut ».
La terminologie du code et de la documentation devra rester « vitesse GNSS fournie
par Android » tant que la provenance mathématique n'est pas démontrée appareil par
appareil.

### GPS-S02 — Base temporelle monotone

Les intervalles sont calculés sur `Location.elapsedRealtimeNanos`, et non sur
`Location.time` ou `System.currentTimeMillis()`. Les changements d'heure, NTP et
heure réseau ne peuvent donc plus créer de dérivée d'accélération aberrante.

### GPS-S03 — Estimateur pur et testable

`AlphaBetaSpeedEstimator` ne dépend pas d'Android et possède des tests sur la
vitesse constante, la rampe, les valeurs non monotones, les pertes de signal et
les valeurs aberrantes. Cette séparation doit être conservée dans le futur module
`:core`.

### GPS-S04 — Prédiction plutôt que délai artificiel

La suppression de l'ancien délai de 1,2 s est correcte. Une application LIVE doit
estimer l'état au temps du signal au lieu de retarder tout l'affichage pour attendre
le prochain fix.

### GPS-S05 — Cycle de vie maîtrisé

Le GPS est arrêté hors mode LIVE et dans `onCleared`. Cela évite une consommation
et une collecte de localisation permanentes.

---

## 4. Constats à corriger

Les priorités utilisées ici sont :

- **P0** : peut produire une association vitesse/spectre fausse tout en paraissant valide ;
- **P1** : dégrade fortement l'exactitude, la robustesse ou la traçabilité ;
- **P2** : optimisation nécessaire pour atteindre le plafond du GNSS interne.

### GPS-01 — Une estimation périmée reste exploitable indéfiniment — P0

**Preuve :** `AlphaBetaSpeedEstimator.predictAt()` limite `dt` à deux secondes,
mais ne rend jamais l'estimation invalide. Après une perte GNSS, la sortie reste
donc figée à `v + a × 2 s`. `SpeedProvider.qualityOf()` fait passer le voyant à
`NONE` après cinq secondes, mais `MainViewModel.processLiveFrame()` continue à
utiliser `theoreticalSpeedKmh` sans vérifier ce statut.

**Impact :** un tunnel, un pare-brise atténuant ou un passage en zone couverte peut
laisser une ancienne vitesse piloter RPM, H1 et ordres pendant toute la perte de
signal.

**Correction attendue :** retourner un objet d'estimation comportant une validité,
un âge et une incertitude. Au-delà de l'horizon ou de l'incertitude admise, la
vitesse devient `INVALID` et le suivi cinématique s'interrompt.

### GPS-02 — L'incertitude de vitesse n'alimente pas l'estimateur — P0

**Preuve :** tout fix GNSS ayant `hasSpeed()` alimente le filtre avec le même poids.
`speedAccuracyMetersPerSecond` n'est lu que par `qualityOf()` pour afficher le
voyant.

**Impact :** une mesure à ±0,1 m/s et une mesure à ±5 m/s corrigent l'état avec le
même gain. Un fix dégradé peut donc déplacer le suivi d'ordre avant que l'utilisateur
n'interprète la couleur du voyant.

**Correction attendue :** utiliser la variance déclarée `R = σv²` dans un filtre de
Kalman, ou rejeter explicitement la mesure lorsque son incertitude dépasse la limite
du mode métrologique.

### GPS-03 — Le timestamp utilisé est celui du DSP, pas celui du son — P0

**Preuve :** `CaptureEngine.frames()` émet un `ShortArray` sans timestamp.
`processLiveFrame()` appelle ensuite `speedProvider.currentTelemetry()`, qui prédit
à `SystemClock.elapsedRealtimeNanos()` au moment du traitement.

**Impact :** dès que la file DSP contient du retard, le spectre d'un buffer audio est
associé à une vitesse plus récente que le son analysé. Le compteur de frames prouve
l'absence de perte, mais pas l'absence de délai en file.

**Correction attendue :** émettre un `CapturedAudioFrame(pcm, captureTimeNanos)`.
Ancrer les positions de frame avec `AudioRecord.getTimestamp(...,
TIMEBASE_BOOTTIME)` puis interpoler le temps du centre de chaque fenêtre FFT. La
vitesse doit être évaluée à ce timestamp précis.

### GPS-04 — Le filtre ne produit aucune covariance — P1

**Preuve :** le résultat est un simple couple vitesse/accélération. Aucun écart-type
ne représente l'incertitude filtrée ou prédite.

**Impact :** le logiciel ne peut pas savoir quand une prédiction devient trop
incertaine, ni propager un budget d'erreur vers RPM, H1 ou l'ordre suivi.

**Correction attendue :** état `[v, a]` accompagné d'une matrice de covariance et
d'un écart-type de vitesse prédit au timestamp demandé.

### GPS-05 — Gains α-β fixes et provisoires — P1

**Preuve :** `α=0,5`, `β=0,17`, seuil d'accélération `12 m/s²`, réamorçage à trois
secondes et prédiction maximale de deux secondes sont constants. Le commentaire du
code indique que ces gains attendent un réglage sur traces routières.

**Impact :** le compromis bruit/retard varie avec la cadence réelle du GNSS, le
téléphone, le niveau de signal et le style de conduite. Les mêmes gains ne sont pas
optimaux à 1 Hz, 5 Hz ou 10 Hz.

**Correction attendue :** filtre à temps variable dont les gains résultent des
covariances de processus et de mesure, avec paramètres nommés et calibrés.

### GPS-06 — Deuxième valeur aberrante acceptée sans cohérence — P1

**Preuve :** la première innovation impliquant plus de `12 m/s²` est ignorée, puis
toute seconde innovation dépassant le seuil est acceptée parce que
`pendingOutlier == true`. Le code ne vérifie pas que les deux fixes aberrants sont
proches ou décrivent la même transition.

**Impact :** deux erreurs multipath successives peuvent être interprétées comme un
changement réel.

**Correction attendue :** test statistique du résidu normalisé et état de
réacquisition exigeant plusieurs mesures mutuellement cohérentes.

### GPS-07 — Le secours Fused ne garantit pas une provenance GNSS — P1

**Preuve :** le Fused Location Provider est lancé uniquement lorsque
`GPS_PROVIDER` est désactivé. Ses fixes ne sont injectés dans l'estimateur que si
`location.provider == GPS_PROVIDER`.

**Impact :** le Fused peut combiner GNSS, réseau, Wi-Fi et capteurs. Le champ
`provider` ne constitue pas un contrat documenté sur les capteurs ayant produit la
solution. Le secours peut donc rester inactif, être rejeté, ou transmettre à
l'interface une vitesse dont la provenance n'est pas métrologiquement qualifiée.

**Correction attendue :** réserver `GPS_PROVIDER` à la chaîne métrologique. Un fix
Fused éventuel doit être marqué `INFORMATION_ONLY` et ne jamais piloter un ordre
sans qualification explicite.

### GPS-08 — État non réinitialisé lors des transitions de mode — P1

**Preuve :** `setAudioSourceMode()` appelle `speedProvider.stop()` hors LIVE et
`start()` au retour, mais pas `speedProvider.reset()`.

**Impact :** avant le premier nouveau fix, `currentTelemetry()` peut réexposer une
ancienne vitesse prédite au pipeline qui vient de redémarrer.

**Correction attendue :** nouvelle session GNSS explicite avec remise à zéro de
l'état, de la covariance et de la validité.

### GPS-09 — Le voyant ne protège pas les calculs — P1

**Preuve :** `GpsStatus` est purement visuel. Une vitesse `POOR` ou `NONE` peut
toujours être utilisée pour calculer H1, RPM et les fréquences d'ordre. La vitesse
brute du dernier fix reste elle aussi affichable après expiration.

**Impact :** l'interface signale une dégradation, mais les résultats continuent à
être calculés et exportés comme si la donnée restait exploitable.

**Correction attendue :** séparer l'état d'affichage de la décision métrologique.
Toute utilisation en calcul doit exiger une estimation `VALID` ou, selon une règle
explicite, `PREDICTED` dans son horizon autorisé.

### GPS-10 — L'incertitude n'est pas propagée à la fréquence d'ordre — P1

Pour une vitesse exprimée en km/h :

`f(Hn) = n × vitesse × 1000 / (V1000 × 60)`

et donc :

`σf = n × σvitesse × 1000 / (V1000 × 60)`.

Avec les valeurs par défaut `V1000=10 km/h`, `H18` et un fix encore classé `GOOD`
à `σv=0,5 m/s = 1,8 km/h`, on obtient `σf≈54 Hz`. À 44,1 kHz et FFT 2048, un bin
vaut environ `21,53 Hz`. La recherche fixe de ±1 bin est donc plus étroite qu'un
seul écart-type de l'ordre prédit.

**Impact :** le logiciel peut manquer la vraie raie tout en annonçant un GPS vert.

**Correction attendue :** bande de recherche dérivée de `σf`, bornée par une limite
de confiance. Si la bande devient trop large pour identifier un ordre sans ambiguïté,
le résultat doit être suspendu ou marqué incertain.

### GPS-11 — Full-tracking supprimé sans comparaison métrologique — P2

L'ancienne implémentation demandait `GnssMeasurementRequest.setFullTracking(true)`
sur Android 12+. Cette inscription a été supprimée parce que son callback était vide
et qu'elle consommait de l'énergie.

La documentation Android indique pourtant que le full-tracking désactive le duty
cycling, demande toutes les constellations/bandes saines supportées et affecte aussi
la localisation GNSS. Le callback vide n'exploitait pas les mesures, mais la requête
pouvait modifier favorablement le fonctionnement du chipset.

**Correction attendue :** réintroduire le full-tracking uniquement pendant une
session de mesure, collecter ses diagnostics et réaliser un essai A/B. Le conserver
en production seulement si le gain de continuité ou de précision est mesuré.

### GPS-12 — Intégrité du fix incomplète — P1

La chaîne ne qualifie pas explicitement :

- permission précise contre permission approximative ;
- fix mock ;
- âge initial/cached du fix ;
- incertitude de son timestamp lorsque disponible ;
- nombre de satellites utilisés, C/N0, diversité de constellations ou fréquence L5 ;
- capacité réelle du matériel GNSS.

Ces informations ne doivent pas remplacer `speedAccuracy`, mais elles sont
nécessaires pour diagnostiquer et expliquer une dégradation.

### GPS-13 — Journal terrain insuffisant pour régler l'estimateur — P1

Le logger enregistre le timestamp du fix, la vitesse, son incertitude, la position et
le provider. Il n'enregistre pas l'heure de livraison du callback, l'état de
l'estimateur, sa covariance, les satellites/C/N0, les pertes de fixes ni une vérité
terrain synchronisée.

Les 211 fixes observés à environ 1,02 s prouvent la réception et la monotonie, mais
pas l'exactitude, le retard dynamique ou la robustesse.

### GPS-14 — Accélération présentée sans incertitude — P1

`accelerationG` est une dérivée estimée de la vitesse GNSS. À environ 1 Hz, elle ne
mesure pas les transitoires rapides entre fixes et peut être très sensible au réglage
du filtre.

**Correction attendue :** la renommer/documenter comme accélération GNSS estimée,
publier son incertitude ou la masquer lorsque celle-ci n'est pas défendable.

### GPS-15 — Pas de solution GNSS brute qualifiée — P2

Android expose les pseudorange rates, leurs incertitudes, le C/N0, les fréquences et
l'horloge récepteur sur les appareils compatibles. Une solution de vitesse par
moindres carrés pondérés est donc possible sans matériel externe.

Ce n'est pas automatiquement une amélioration : il faut gérer les vitesses
satellites, l'horloge récepteur, les constellations, les valeurs aberrantes et le
multipath. La solution propriétaire du chipset peut rester meilleure.

**Correction attendue :** considérer la solution brute comme un axe R&D optionnel,
à confronter à `Location.speed` et à une vérité terrain avant toute utilisation en
production.

---

## 5. Limites physiques et contractuelles d'Android

Même une implémentation correcte ne peut pas garantir une vitesse parfaite :

- beaucoup de téléphones ne produisent qu'environ un fix par seconde ; certains
  modèles peuvent atteindre 10 ou 20 Hz ;
- la vitesse Android peut bénéficier du Doppler GNSS, sans que cette méthode soit
  garantie par le contrat de `Location.speed` ;
- `speedAccuracyMetersPerSecond` représente un intervalle à 68 %, pas une borne
  maximale ;
- les performances se dégradent sous tunnel, pare-brise atténuant, végétation,
  canyon urbain et multipath ;
- sans capteur inertiel, toute vitesse entre deux fixes est une prédiction de modèle,
  non une nouvelle mesure ;
- le référentiel de compatibilité Android récent exige au minimum 1 Hz et une erreur
  de vitesse inférieure à 0,5 m/s dans 95 % des cas seulement en ciel ouvert et à
  faible accélération. Les appareils anciens ou les mauvaises conditions peuvent
  être moins bons.

Le produit doit donc communiquer un état et une incertitude, jamais seulement une
valeur numérique.

---

## 6. Architecture cible recommandée

La meilleure solution portable dans le périmètre retenu est :

```text
GPS_PROVIDER / Location.speed
→ échantillon (temps, vitesse, σv, intégrité GNSS)
→ filtre de Kalman robuste [vitesse, accélération]
→ estimation (temps, vitesse, accélération, covariance, validité)
→ évaluation au timestamp exact du centre de la frame audio
→ propagation d'incertitude vers RPM, H1 et fréquence d'ordre
```

Deux niveaux sont distingués :

1. **Production portable :** vitesse Android directe, Kalman conscient de
   l'incertitude, timestamp audio, full-tracking qualifié et diagnostics GNSS.
2. **R&D appareils compatibles :** solution de vitesse issue des mesures GNSS brutes,
   activée seulement après preuve qu'elle dépasse la solution Android.

Pour les rapports différés, un lissage avant/arrière peut améliorer la trajectoire de
vitesse puisqu'il dispose des fixes futurs. Il ne doit pas être appliqué au direct.

---

## 7. Conditions minimales avant qualification

La chaîne ne pourra être déclarée qualifiée que lorsque :

- aucune vitesse expirée ne pilote un calcul ;
- chaque frame audio et chaque fix partagent une base temporelle BOOTTIME ;
- chaque estimation transporte son âge et son incertitude ;
- l'incertitude est propagée aux ordres ;
- les pertes, valeurs aberrantes et transitions de mode ont des tests de régression ;
- les modes GPS pur, permission approximative, mock, perte de signal et reprise ont
  été testés sur appareil ;
- au moins trois téléphones ont été comparés à une vérité terrain synchronisée ;
- les seuils et covariances sont issus des traces, documentés et non choisis par
  intuition ;
- la solution respecte ou dépasse `Location.speed` sans ajouter de retard artificiel ;
- le rapport final publie les limites d'emploi et le budget d'erreur vitesse → ordre.

---

## 8. Sources officielles

- Android `Location` — vitesse, précision et temps monotone :
  <https://developer.android.com/reference/android/location/Location>
- Android `LocationManager` — `GPS_PROVIDER` et requêtes de localisation :
  <https://developer.android.com/reference/android/location/LocationManager>
- Android `LocationRequest` — cadence, qualité et batching :
  <https://developer.android.com/reference/android/location/LocationRequest>
- Android `GnssMeasurementRequest.Builder` — full-tracking :
  <https://developer.android.com/reference/android/location/GnssMeasurementRequest.Builder>
- Android `GnssMeasurement` — pseudorange rate et incertitudes :
  <https://developer.android.com/reference/android/location/GnssMeasurement>
- Android Raw GNSS Measurements :
  <https://developer.android.com/develop/sensors-and-location/sensors/gnss>
- Android `GnssStatus` — satellites, C/N0, constellations et fréquences :
  <https://developer.android.com/reference/android/location/GnssStatus>
- Android `AudioRecord.getTimestamp` et `AudioTimestamp` :
  <https://developer.android.com/reference/android/media/AudioRecord#getTimestamp(android.media.AudioTimestamp,%20int)>
- Google Play services `LocationRequest` — limites de cadence et nature du Fused :
  <https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest>
- Android Compatibility Definition, exigences GNSS :
  <https://source.android.com/docs/compatibility/17/android-17-cdd>
