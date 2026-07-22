# M-Accueil - Lanceur (deux prismes)

> **Type** : écran d'accueil (lanceur du chrome, première vue affichée au lancement).
> **Persona principal** : tous ([Marie](../Personas/Marie.md), [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md)).
> **Parcours couverts** : point d'entrée transverse de tous les parcours.

L'accueil est la **porte d'entrée** de l'application : un **bandeau nocturne** (identité « une nuit de capture ») avec un **tableau de bord de compteurs**, suivi de deux **sections-prismes** de cartes d'activité. Les deux prismes rendent explicites les deux usages complémentaires : **produire** la donnée (collecte des nuits et des passages) et l'**exploiter** (inventaire des espèces, biodiversité). Chaque carte ouvre une fonctionnalité ; le **fil d'Ariane** du chrome repart toujours d'`Accueil`.

## Maquette principale - accueil avec données

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" role="img" aria-label="Maquette M-Accueil - lanceur a deux prismes" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <defs>
    <linearGradient id="nuit" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#16223b"/>
      <stop offset="1" stop-color="#27395d"/>
    </linearGradient>
  </defs>
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .retour { fill: #9aa0b3; font: 13px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .hero-titre { fill: #ffffff; font: 700 26px sans-serif; }
    .hero-sous { fill: #c5cae9; font: 14px sans-serif; }
    .moon { fill: #ffffff; opacity: 0.08; font: 160px sans-serif; }
    .chip { fill: #21314f; stroke: #3a4a6a; stroke-width: 1; }
    .chip-num { fill: #ffffff; font: 700 22px sans-serif; }
    .chip-lib { fill: #aeb6c8; font: 12px sans-serif; }
    .chip-glyph { font: 15px sans-serif; }
    .sect-pastille-txt { font: 13px sans-serif; }
    .sect-titre { fill: #2c3e50; font: 700 16px sans-serif; }
    .sect-tag { fill: #8a93a0; font: 12px sans-serif; }
    .sep { stroke: #eceff3; stroke-width: 1; }
    .card { fill: #ffffff; stroke: #e6e9ee; stroke-width: 1.2; }
    .card-glyph { font: 22px sans-serif; }
    .card-desc { fill: #5a6573; font: 13px sans-serif; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { fill: #6a737d; font: 11px sans-serif; }
  </style>

  <rect x="10" y="10" width="1180" height="680" rx="4" class="frame"/>

  <!-- Chrome -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="38" class="retour">← Retour</text>
  <text x="290" y="38" class="crumb-active">Accueil</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <!-- Hero nocturne -->
  <rect x="10" y="54" width="1180" height="220" fill="url(#nuit)"/>
  <text x="1120" y="190" class="moon" text-anchor="middle">🌙</text>
  <text x="600" y="106" class="hero-titre" text-anchor="middle">Bienvenue dans VigieChiro Companion</text>
  <text x="600" y="138" class="hero-sous" text-anchor="middle">Deux entrées : collecter vos nuits d'enregistrement (sites &amp; passages)</text>
  <text x="600" y="158" class="hero-sous" text-anchor="middle">et exploiter vos espèces observées (biodiversité).</text>

  <!-- Tableau de bord : 4 compteurs -->
  <g>
    <rect x="170" y="188" width="200" height="70" rx="8" class="chip"/>
    <circle cx="204" cy="223" r="16" fill="#4a90d9"/>
    <text x="204" y="228" class="chip-glyph" text-anchor="middle">🗺️</text>
    <text x="232" y="218" class="chip-num">2</text>
    <text x="232" y="240" class="chip-lib">Sites</text>

    <rect x="390" y="188" width="200" height="70" rx="8" class="chip"/>
    <circle cx="424" cy="223" r="16" fill="#16a085"/>
    <text x="424" y="228" class="chip-glyph" text-anchor="middle">📍</text>
    <text x="452" y="218" class="chip-num">5</text>
    <text x="452" y="240" class="chip-lib">Points d'écoute</text>

    <rect x="610" y="188" width="200" height="70" rx="8" class="chip"/>
    <circle cx="644" cy="223" r="16" fill="#a29bfe"/>
    <text x="644" y="228" class="chip-glyph" text-anchor="middle">🌙</text>
    <text x="672" y="218" class="chip-num">8</text>
    <text x="672" y="240" class="chip-lib">Passages</text>

    <rect x="830" y="188" width="200" height="70" rx="8" class="chip"/>
    <circle cx="864" cy="223" r="16" fill="#f39c12"/>
    <text x="864" y="228" class="chip-glyph" text-anchor="middle">🐾</text>
    <text x="892" y="218" class="chip-num">3 980</text>
    <text x="892" y="240" class="chip-lib">Observations</text>
  </g>

  <!-- Section 1 : Collecte & passages -->
  <circle cx="56" cy="304" r="14" fill="#34495e"/>
  <text x="56" y="309" class="sect-pastille-txt" text-anchor="middle">🛰️</text>
  <text x="80" y="310" class="sect-titre">Collecte &amp; passages</text>
  <text x="278" y="310" class="sect-tag">produire la donnée</text>

  <rect x="40" y="324" width="540" height="118" rx="10" class="card"/>
  <circle cx="92" cy="383" r="26" fill="#4a90d9"/>
  <text x="92" y="391" class="card-glyph" text-anchor="middle">🗺️</text>
  <text x="140" y="362" font-family="sans-serif" font-size="17" font-weight="700" fill="#4a90d9">Mes sites</text>
  <text x="140" y="392" class="card-desc">Vos carrés et points d'écoute.</text>
  <text x="544" y="392" font-family="sans-serif" font-size="28" font-weight="700" fill="#4a90d9">›</text>

  <rect x="620" y="324" width="540" height="118" rx="10" class="card"/>
  <circle cx="672" cy="383" r="26" fill="#e8a838"/>
  <text x="672" y="391" class="card-glyph" text-anchor="middle">📊</text>
  <text x="720" y="362" font-family="sans-serif" font-size="17" font-weight="700" fill="#b9770e">Carte &amp; passages</text>
  <text x="720" y="388" class="card-desc">La carte de vos sites et le tableau de tous</text>
  <text x="720" y="408" class="card-desc">les passages : filtres, tri et export.</text>
  <text x="1124" y="392" font-family="sans-serif" font-size="28" font-weight="700" fill="#e8a838">›</text>

  <line x1="40" y1="468" x2="1160" y2="468" class="sep"/>

  <!-- Section 2 : Espèces & biodiversité -->
  <circle cx="56" cy="498" r="14" fill="#1e8449"/>
  <text x="56" y="503" class="sect-pastille-txt" text-anchor="middle">🍃</text>
  <text x="80" y="504" class="sect-titre">Espèces &amp; biodiversité</text>
  <text x="298" y="504" class="sect-tag">exploiter la donnée</text>

  <rect x="40" y="518" width="540" height="118" rx="10" class="card"/>
  <circle cx="92" cy="577" r="26" fill="#1e8449"/>
  <text x="92" y="585" class="card-glyph" text-anchor="middle">🪶</text>
  <text x="140" y="556" font-family="sans-serif" font-size="17" font-weight="700" fill="#1e8449">Espèces &amp; observations</text>
  <text x="140" y="582" class="card-desc">L'inventaire de vos espèces détectées : où,</text>
  <text x="140" y="602" class="card-desc">quand, combien - par espèce ou par carré.</text>
  <text x="544" y="586" font-family="sans-serif" font-size="28" font-weight="700" fill="#1e8449">›</text>

  <rect x="620" y="518" width="540" height="118" rx="10" class="card"/>
  <circle cx="672" cy="577" r="26" fill="#8e44ad"/>
  <text x="672" y="585" class="card-glyph" text-anchor="middle">🔊</text>
  <text x="720" y="556" font-family="sans-serif" font-size="17" font-weight="700" fill="#8e44ad">Sons &amp; validation</text>
  <text x="720" y="582" class="card-desc">Écouter, valider et exporter vos sons de référence.</text>
  <text x="1124" y="586" font-family="sans-serif" font-size="28" font-weight="700" fill="#8e44ad">›</text>

  <!-- Footer -->
  <rect x="10" y="656" width="1180" height="34" class="footer"/>
  <text x="40" y="677" class="footer-txt">VigieChiro Companion</text>
</svg>
</div>

### Annotations

- **Bandeau nocturne** : bandeau en dégradé bleu nuit (identité « une nuit de capture », filigrane lune 🌙) avec le titre de bienvenue et l'invite « deux entrées ». Il porte le **tableau de bord** de compteurs.
- **Tableau de bord** : quatre compteurs chiffrés (**Sites**, **Points d'écoute**, **Passages**, **Observations**), chacun avec sa pastille d'icône colorée. Les valeurs sont **recalculées à chaque affichage** de l'accueil (elles reflètent l'état de la base après un import ou une déclaration de site). Le bandeau est **masqué tant qu'aucune donnée** n'existe (premier lancement).
- **Sections-prismes** : les cartes d'activité sont regroupées en deux sections selon leur **prisme** :
  - **🛰️ Collecte & passages** (*produire la donnée*) : **Mes sites** puis **Carte & passages** ;
  - **🍃 Espèces & biodiversité** (*exploiter la donnée*) : **Espèces & observations** puis **Sons & validation**.
- **Cartes** : chaque carte affiche une **pastille d'icône**, un **titre** et une **description** courte, teintés par la **couleur d'accent** de la fonctionnalité ; un **chevron** `›` apparaît au survol et la carte entière est cliquable.

### Interactions clés

| Élément | Action |
|---|---|
| Carte **Mes sites** | Ouvre [M-Sites](M-Sites.md) (vos carrés et points d'écoute) |
| Carte **Carte & passages** | Ouvre [M-MultiSite](M-MultiSite.md) (carte au carroyage + tableau des passages) |
| Carte **Espèces & observations** | Ouvre [M-Analyse](M-Analyse.md) (inventaire transverse) |
| Carte **Sons & validation** | Ouvre [M-SonsValidation](M-SonsValidation.md) (source *références* : écoute, validation, export bibliothèque) |
| Compteur du tableau de bord | Repère visuel (non cliquable) ; reflète l'état courant de la base |
| Champ **Rechercher** (Ctrl+F) | Recherche globale ([M-Recherche](M-Recherche.md)), disponible sur tous les écrans |

### Notes pour l'implémentation

- **Inversion de dépendance** : le socle (`commun.view`, `MainController`) bâtit l'accueil à partir des `Set<ActiviteAccueil>` et `Set<IndicateurAccueil>` injectés ; chaque fonctionnalité **publie sa carte et ses compteurs** via un `Multibinder` Guice. Ajouter une activité à l'accueil = une implémentation + une ligne de binding, **sans retoucher le socle** (graphe de slices acyclique, garanti par `ArchitectureTest`).
- **Prismes** : l'ordre des sections suit l'énumération `Prisme` (Collecte & passages, puis Espèces & biodiversité) ; l'ordre des cartes **dans** un prisme suit le rang `ordre()` déclaré par chaque carte.
- **Bandeau nocturne** : `StackPane` clippé à ses bornes (le filigrane lune déborde volontairement). Le `FlowPane` des compteurs et celui des sections se réagencent selon la largeur de la fenêtre.
- **Icônes** : codes [Ikonli](https://kordamp.org/ikonli/) FontAwesome 5 fournis par chaque fonctionnalité sous forme de **chaîne** (le socle construit le `FontIcon`) ; les emojis de la maquette ne sont qu'un substitut basse fidélité.

## Enrichissements prévus

> Décidé et maquetté, pas encore livré.

- Une carte **« Ma saison »** rejoint le prisme *Collecte & passages*, au rang qui suit
  « Carte & passages ». Elle ouvre [M-Saison](M-Saison.md) et répond à la question
  « qu'est-ce qu'il me reste à faire cette saison ? » (#2356). Comme toute carte d'accueil,
  elle est publiée par sa fonctionnalité via le `Multibinder`, sans retouche du socle.
