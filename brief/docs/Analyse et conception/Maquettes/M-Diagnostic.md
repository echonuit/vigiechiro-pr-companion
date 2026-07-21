# M-Diagnostic - Diagnostiquer le matériel

> **Type** : écran **« Diagnostic matériel »** d'un passage (atteint depuis [M-Passage](M-Passage.md) pour un passage disposant d'un journal du capteur et/ou d'un relevé climatique).
> **Persona principal** : [Karim](../Personas/Karim.md) et [Samuel](../Personas/Samuel.md) (exploitation pro, contrôle du parc d'enregistreurs).
> **Parcours couverts** : [P6 - Diagnostiquer le matériel](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md).

L'écran présente, pour le passage courant, un **bilan technique** de la nuit : un **graphe climatique** (température / hygrométrie) issu du relevé de la sonde, la liste des **anomalies** détectées dans le journal du capteur (R19), le **journal** brut des évènements, la **cohérence horaire** (fenêtre nocturne réelle au point d'écoute et repère « hors nuit »), et l'état du **GPS du point d'écoute**. L'objectif : décider si un enregistreur doit être révisé.

> **Robustesse** : quand le relevé climatique est manquant (sonde absente ou défaillante), la section climat n'est **pas masquée** : un bandeau d'avertissement explicite le signale (R20) et le reste du diagnostic reste exploitable.

## Maquette principale - passage avec relevé climatique présent

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 660" role="img" aria-label="Maquette M-Diagnostic - diagnostic materiel d'un passage" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagesub { font: 13px sans-serif; fill: #4a6785; }
    .pagesub2 { font: 12px sans-serif; fill: #6a737d; }
    .section-title { font: 600 15px sans-serif; fill: #2c3e50; }
    .chart-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .axis { stroke: #b8c2cc; stroke-width: 1; }
    .grid { stroke: #d8dee4; stroke-width: 0.5; stroke-dasharray: 3 3; }
    .axis-txt { font: 9px sans-serif; fill: #6a737d; }
    .axis-title { font: 11px sans-serif; fill: #6a737d; }
    .temp-line { fill: none; stroke: #e8631a; stroke-width: 2; }
    .hygro-line { fill: none; stroke: #f1a017; stroke-width: 2; }
    .marker-temp { fill: #ffffff; stroke: #e8631a; stroke-width: 1.5; }
    .marker-hygro { fill: #ffffff; stroke: #f1a017; stroke-width: 1.5; }
    .legend-box { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .legend-txt { font: 12px sans-serif; fill: #2c3e50; }
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .list-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 0.5; }
    .list-row-alt { fill: #f3f5f7; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .footer-txt { font: 12px sans-serif; fill: #4a6785; }
    .footer-warn { font: 12px sans-serif; fill: #7e5109; }
    .footer-ok { font: 12px sans-serif; fill: #1e8449; }
    .statusbar { fill: #eceff5; stroke: #d0d7de; stroke-width: 1; }
    .status-txt { font: 12px sans-serif; fill: #4a6785; }
  </style>

  <rect x="0" y="0" width="1000" height="660" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome"/>
  <text x="20" y="28" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="600" y="28" class="crumb-curr">Diagnostic</text>
  <rect x="780" y="12" width="200" height="22" rx="11" class="search"/>
  <text x="794" y="28" class="search-txt">Rechercher (Ctrl+F)</text>

  <!-- Pas de titre d'écran (#693) : redondant avec le fil d'Ariane ; l'enregistreur est déporté en barre de statut. -->
  <text x="30" y="80" class="pagesub">Température en début de nuit : 8,5 °C</text>

  <!-- Graphe climat -->
  <rect x="60" y="110" width="900" height="160" class="chart-bg"/>
  <text x="22" y="195" class="axis-title" transform="rotate(-90 22 195)" text-anchor="middle">T° (°C) / Humidité …</text>
  <!-- grilles + axe Y -->
  <line x1="60" y1="125" x2="960" y2="125" class="grid"/><text x="54" y="129" class="axis-txt" text-anchor="end">100</text>
  <line x1="60" y1="161" x2="960" y2="161" class="grid"/><text x="54" y="165" class="axis-txt" text-anchor="end">75</text>
  <line x1="60" y1="197" x2="960" y2="197" class="grid"/><text x="54" y="201" class="axis-txt" text-anchor="end">50</text>
  <line x1="60" y1="233" x2="960" y2="233" class="grid"/><text x="54" y="237" class="axis-txt" text-anchor="end">25</text>
  <line x1="60" y1="269" x2="960" y2="269" class="axis"/><text x="54" y="269" class="axis-txt" text-anchor="end">0</text>
  <!-- Humidité (haute, croissante) -->
  <polyline class="hygro-line" points="110,180 215,171 320,165 425,162 530,156 635,152 740,149 845,145 905,150"/>
  <!-- Température (basse, décroissante) -->
  <polyline class="temp-line" points="110,233 215,236 320,239 425,241 530,243 635,245 740,247 845,248 905,250"/>
  <!-- marqueurs -->
  <g><circle cx="110" cy="180" r="3.5" class="marker-hygro"/><circle cx="215" cy="171" r="3.5" class="marker-hygro"/><circle cx="320" cy="165" r="3.5" class="marker-hygro"/><circle cx="425" cy="162" r="3.5" class="marker-hygro"/><circle cx="530" cy="156" r="3.5" class="marker-hygro"/><circle cx="635" cy="152" r="3.5" class="marker-hygro"/><circle cx="740" cy="149" r="3.5" class="marker-hygro"/><circle cx="845" cy="145" r="3.5" class="marker-hygro"/><circle cx="905" cy="150" r="3.5" class="marker-hygro"/></g>
  <g><circle cx="110" cy="233" r="3.5" class="marker-temp"/><circle cx="215" cy="236" r="3.5" class="marker-temp"/><circle cx="320" cy="239" r="3.5" class="marker-temp"/><circle cx="425" cy="241" r="3.5" class="marker-temp"/><circle cx="530" cy="243" r="3.5" class="marker-temp"/><circle cx="635" cy="245" r="3.5" class="marker-temp"/><circle cx="740" cy="247" r="3.5" class="marker-temp"/><circle cx="845" cy="248" r="3.5" class="marker-temp"/><circle cx="905" cy="250" r="3.5" class="marker-temp"/></g>
  <!-- axe X temporel (HH:mm, quelques repères) -->
  <text x="110" y="285" class="axis-txt" text-anchor="middle">20:30</text>
  <text x="320" y="285" class="axis-txt" text-anchor="middle">22:30</text>
  <text x="530" y="285" class="axis-txt" text-anchor="middle">00:30</text>
  <text x="740" y="285" class="axis-txt" text-anchor="middle">02:30</text>
  <text x="905" y="285" class="axis-txt" text-anchor="middle">05:30</text>
  <text x="510" y="305" class="axis-title" text-anchor="middle">Heure</text>

  <!-- Légende -->
  <rect x="390" y="318" width="220" height="24" rx="3" class="legend-box"/>
  <circle cx="408" cy="330" r="4" class="marker-temp"/><text x="418" y="334" class="legend-txt">T° (°C)</text>
  <circle cx="500" cy="330" r="4" class="marker-hygro"/><text x="510" y="334" class="legend-txt">Humidité (%)</text>

  <!-- Anomalies (R19) -->
  <text x="30" y="374" class="section-title">Anomalies (R19)</text>
  <rect x="30" y="384" width="455" height="188" class="list-frame"/>
  <rect x="30" y="384" width="455" height="2" class="list-head"/>
  <rect x="31" y="385" width="453" height="26" fill="#ffffff"/>
  <text x="44" y="402" class="cell">Réveil non programmé : 23/06/26 - 03:12:00 PR1925492 Wakeup</text>
  <rect x="31" y="411" width="453" height="26" class="list-row-alt"/>
  <text x="44" y="428" class="cell">Batterie faible (18%) : Batteries internes 18%</text>

  <!-- Évènements du journal -->
  <text x="515" y="374" class="section-title">Évènements du journal</text>
  <rect x="515" y="384" width="455" height="188" class="list-frame"/>
  <rect x="516" y="385" width="453" height="26" fill="#ffffff"/>
  <text x="529" y="402" class="cell">### Démarrage PR1925492</text>
  <rect x="516" y="411" width="453" height="26" class="list-row-alt"/>
  <text x="529" y="428" class="cell">Arrêt programmé à 06:00:00</text>

  <!-- Cohérence horaire (#548) : fenêtre nocturne + repère hors nuit -->
  <text x="30" y="592" class="footer-txt">Nuit : coucher 21:58 · lever 05:48</text>
  <text x="30" y="612" class="footer-warn">Hors nuit : démarrage avant le coucher et arrêt après le lever du soleil (une partie de l'enregistrement est diurne).</text>
  <!-- État GPS du point d'écoute (ligne permanente) -->
  <text x="30" y="632" class="footer-ok">GPS du point : disponible</text>

  <!-- Barre de statut (#693) : contexte du passage, enregistreur + nombre de mesures, alerte prioritaire -->
  <rect x="0" y="640" width="1000" height="20" class="statusbar"/>
  <text x="12" y="654" class="status-txt">Carré 640380 · A1 · N° 2</text>
  <text x="500" y="654" class="status-txt" text-anchor="middle">PR 1925492 · 10 mesures</text>
  <text x="988" y="654" class="status-txt" text-anchor="end">Hors nuit</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome** (barre de navigation commune) via le contrat `EmplacementNavigation` ; l'écran ne dessine pas son propre fil. Emplacement affiché : `Accueil › Mes sites › Carré N › Détails du passage N° X › Diagnostic matériel`, identique quelle que soit la route.
- **Pas de titre d'écran** (#693) : le titre « Diagnostic matériel » serait redondant avec le fil d'Ariane. L'**enregistreur** diagnostiqué et le **nombre de mesures** climatiques sont portés par la **barre de statut** (zone centre : `PR 1925492 · 10 mesures`).
- **Température en début de nuit** (`lblTemperature`) : donnée optionnelle saisie en M-Passage (`8,5 °C`, ou un tiret si elle n'est pas renseignée).
- **Graphe climatique** (`grapheClimat`) : `LineChart` à **deux séries** (température en orange foncé, hygrométrie en orange clair). Axe X **temporel** (`NumberAxis` en minutes depuis la première mesure, étiquettes `HH:mm`, quelques repères espacés), axe Y commun `T° (°C) / Humidité…`. Légende sous le graphe.
- **Anomalies (R19)** (`listeAnomalies`) : `ListView` des évènements anormaux du journal (réveils non programmés, batterie faible, erreurs SD…). Le journal étant circulaire, certaines entrées anciennes peuvent manquer.
- **Évènements du journal** (`listeEvenements`) : `ListView` du journal brut horodaté du capteur (démarrage, arrêt programmé…).
- **Cohérence horaire** (`lblFenetreNuit`, `lblAlerteHorsNuit`, #548) : la **fenêtre nocturne** réelle au point d'écoute (`Nuit : coucher 21:58 · lever 05:48`), calculée depuis les coordonnées et la date ; une **alerte « hors nuit »** apparaît si l'enregistrement démarre avant le coucher ou s'arrête après le lever du soleil.
- **État GPS** (`lblGps`) : ligne d'état **permanente** portant les coordonnées du **point d'écoute** (et non du capteur) : « GPS du point : disponible », ou « GPS du point : non renseigné (compléter la fiche site) ». Le calcul de la fenêtre nocturne en dépend.
- **Zone de message** (`lblMessage`) : retours d'état (passage introuvable, erreurs).

## Variante - relevé climatique absent (R20)

Si la sonde est absente ou défaillante, la section climat **n'est pas masquée** : un **bandeau d'avertissement** (`lblReleveAbsent`) annonce « Relevé climatique absent : la température et l'hygrométrie ne sont pas disponibles pour cette nuit. », le `LineChart` s'affiche **vide** (sans étiquettes d'axe) et le nombre de mesures disparaît de la barre de statut. Les listes Anomalies / Journal, la cohérence horaire et l'état GPS restent affichés et exploitables.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 200" role="img" aria-label="Maquette M-Diagnostic - variante releve climatique absent" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .pagesub { font: 13px sans-serif; fill: #4a6785; }
    .banner { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .banner-txt { font: 13px sans-serif; fill: #5d4e00; }
    .sub-grey { font: 12px sans-serif; fill: #6a737d; }
  </style>
  <rect x="0" y="0" width="1000" height="200" fill="#f7f9fb"/>
  <rect x="30" y="30" width="700" height="40" rx="4" class="banner"/>
  <text x="50" y="55" class="banner-txt">Relevé climatique absent : la température et l'hygrométrie ne sont pas disponibles pour cette nuit.</text>
  <text x="30" y="105" class="pagesub">Température en début de nuit : 8,5 °C</text>
  <text x="30" y="140" class="sub-grey">Le graphe s'affiche vide (sans étiquettes d'axe).</text>
  <text x="30" y="168" class="sub-grey">Anomalies, journal, cohérence horaire et état GPS restent disponibles.</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| Survol d'un point du graphe | Infobulle : horodatage + valeur (T° / hygrométrie) |
| Sélection d'une **anomalie** | Met en évidence l'entrée correspondante dans le journal |
| Relevé climatique absent | Bandeau R20 + graphe vide, le reste du diagnostic reste exploitable |
| Coordonnées GPS absentes | `lblGps` affiche « non renseigné » et invite à compléter la fiche site ([P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)) |

## Notes pour l'implémentation

- **`LineChart` JavaFX** : deux `XYChart.Series` (température, hygrométrie) sur un **axe temporel** (`NumberAxis`, étiquettes `HH:mm`). Lier les séries à des collections observables du ViewModel ; gérer proprement le cas « série vide » (graphe affiché sans étiquettes d'axe, pas d'exception).
- **`ListView` pour anomalies et journal** : `cellFactory` simple (texte). La virtualisation native suffit même pour un journal volumineux.
- **Données manquantes explicites (R20)** : ne jamais masquer une section absente. Piloter la visibilité du bandeau via une `BooleanProperty` du ViewModel (`releveClimatiqueAbsent`).
- **Cohérence horaires astronomiques** : **implémentée** (#548). L'écran calcule la fenêtre nocturne réelle au point d'écoute (coucher / lever du soleil via une éphéméride, fuseau Europe/Paris) et compare les horaires d'enregistrement à cette fenêtre pour repérer un démarrage ou un arrêt **hors nuit** (portion diurne). Indisponible proprement si le GPS ou les horaires manquent, ou en latitude polaire.
- **Icônes** : les repères visuels (température, anomalies, journal, nuit, GPS) sont des `FontIcon` Ikonli, pas des emojis (règle #700).
- **Réutilisation** : l'écran consomme le **relevé climatique** et le **journal du capteur** déjà chargés par le socle pour le passage ; le ViewModel expose des propriétés observables, la vue ne fait que les lier.
