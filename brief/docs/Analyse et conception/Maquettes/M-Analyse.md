# M-Analyse - Espèces & observations

> **Type** : vue plein écran (carte « Espèces & observations » de l'accueil).
> **Persona principal** : [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md) ([Marie](../Personas/Marie.md) ponctuellement).
> **Parcours couverts** : [P11 - Inventaire des espèces détectées](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20espèces%20détectées.md).

L'écran est la porte d'entrée du prisme **biodiversité** : il **exploite transversalement** les observations (toutes nuits confondues) pour répondre à « quelles espèces ai-je détectées, où, quand, combien ? ». Il est en **maître-détail** : un **inventaire** en haut (regroupé par espèce ou par carré), et en bas le **détail des observations** de l'élément sélectionné.

## Maquette principale - inventaire par espèce + détail

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 760" role="img" aria-label="Maquette M-Analyse - Espèces & observations" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .toolbar { fill: #ffffff; stroke: #e1e4e8; stroke-width: 1; }
    .field-label { font: 12px sans-serif; fill: #34495e; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-ph { font: 13px sans-serif; fill: #bdc3c7; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .row-sel { fill: #e8eefc; stroke: #4a90d9; stroke-width: 1.2; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sec { font: 12px sans-serif; fill: #6a737d; }
    .cell-strong { font: 600 12px sans-serif; fill: #2c3e50; }
    .panel { fill: #fbfcfe; stroke: #d0d7de; stroke-width: 1; }
    .panel-title { font: 700 14px sans-serif; fill: #2c3e50; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="740" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="332" y="38" class="crumb-active">Espèces &amp; observations</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="86" class="pagetitle">Espèces &amp; observations</text>
  <text x="40" y="106" class="pagesub">12 espèces · 3 980 observations retenues · 2 carrés</text>

  <!-- Barre d'outils (sans étiquettes : chaque contrôle se décrit par sa valeur / son indice de saisie,
       aligné sur M-MultiSite). Le bouton « 🗺️ Carte » bascule l'inventaire en carte de répartition. -->
  <rect x="40" y="122" width="1120" height="44" rx="4" class="toolbar"/>
  <rect x="58" y="132" width="160" height="26" rx="3" class="field-input"/>
  <text x="72" y="149" class="field-text">Par espèce  ▾</text>
  <rect x="232" y="132" width="170" height="26" rx="3" class="field-input"/>
  <text x="246" y="149" class="field-text">Tous les statuts  ▾</text>
  <rect x="416" y="132" width="180" height="26" rx="3" class="field-input"/>
  <text x="430" y="149" class="field-ph">espèce, carré…</text>
  <rect x="866" y="132" width="130" height="26" rx="3" class="btn-secondary"/>
  <text x="931" y="149" class="btn-txt-dark" text-anchor="middle">🗺️ Carte</text>
  <rect x="1010" y="132" width="150" height="26" rx="3" class="btn-secondary"/>
  <text x="1085" y="149" class="btn-txt-dark" text-anchor="middle">📤 Exporter…</text>

  <!-- Inventaire (par espece) -->
  <rect x="40" y="180" width="1120" height="30" class="table-head"/>
  <text x="56" y="200" class="col-head">ESPÈCE</text>
  <text x="330" y="200" class="col-head">GROUPE</text>
  <text x="500" y="200" class="col-head">DÉTECTIONS</text>
  <text x="640" y="200" class="col-head">PASSAGES</text>
  <text x="760" y="200" class="col-head">CARRÉS</text>
  <text x="860" y="200" class="col-head">POINTS</text>
  <text x="980" y="200" class="col-head">PÉRIODE</text>

  <rect x="40" y="210" width="1120" height="30" class="row-sel"/>
  <text x="56" y="230" class="cell-strong">Pipistrelle commune</text>
  <text x="330" y="230" class="cell">Pipistrelles</text>
  <text x="500" y="230" class="cell">638</text>
  <text x="640" y="230" class="cell">4</text>
  <text x="760" y="230" class="cell">2</text>
  <text x="860" y="230" class="cell">3</text>
  <text x="980" y="230" class="cell-sec">avr - juin</text>

  <rect x="40" y="240" width="1120" height="30" class="row-alt"/>
  <text x="56" y="260" class="cell-strong">Noctule de Leisler</text>
  <text x="330" y="260" class="cell">Noctules</text>
  <text x="500" y="260" class="cell">139</text>
  <text x="640" y="260" class="cell">3</text>
  <text x="760" y="260" class="cell">2</text>
  <text x="860" y="260" class="cell">2</text>
  <text x="980" y="260" class="cell-sec">avr - juin</text>

  <rect x="40" y="270" width="1120" height="30" class="row"/>
  <text x="56" y="290" class="cell-strong">Molosse de Cestoni</text>
  <text x="330" y="290" class="cell">Molosses</text>
  <text x="500" y="290" class="cell">89</text>
  <text x="640" y="290" class="cell">2</text>
  <text x="760" y="290" class="cell">1</text>
  <text x="860" y="290" class="cell">1</text>
  <text x="980" y="290" class="cell-sec">mai - juin</text>

  <rect x="40" y="300" width="1120" height="30" class="row-alt"/>
  <text x="56" y="320" class="cell-strong">Petit rhinolophe</text>
  <text x="330" y="320" class="cell">Rhinolophes</text>
  <text x="500" y="320" class="cell">80</text>
  <text x="640" y="320" class="cell">2</text>
  <text x="760" y="320" class="cell">1</text>
  <text x="860" y="320" class="cell">2</text>
  <text x="980" y="320" class="cell-sec">avr - mai</text>

  <!-- Detail : observations de l'espece selectionnee -->
  <rect x="40" y="360" width="1120" height="338" rx="4" class="panel"/>
  <text x="58" y="388" class="panel-title">Pipistrelle commune - ses observations (638)</text>
  <rect x="700" y="372" width="170" height="28" rx="4" class="btn-primary"/>
  <text x="785" y="390" class="btn-txt" text-anchor="middle">🎧 Écouter / valider</text>
  <rect x="884" y="372" width="170" height="28" rx="4" class="btn-secondary"/>
  <text x="969" y="390" class="btn-txt-dark" text-anchor="middle">Ouvrir le passage →</text>

  <rect x="58" y="412" width="1084" height="28" class="table-head"/>
  <text x="74" y="431" class="col-head">PASSAGE</text>
  <text x="290" y="431" class="col-head">CARRÉ</text>
  <text x="380" y="431" class="col-head">POINT</text>
  <text x="470" y="431" class="col-head">TADARIDA (PROBA)</text>
  <text x="720" y="431" class="col-head">VOTRE TAXON</text>
  <text x="980" y="431" class="col-head">STATUT</text>

  <rect x="58" y="440" width="1084" height="28" class="row-sel"/>
  <text x="74" y="459" class="cell">2026-06-22 · n° 2</text>
  <text x="290" y="459" class="cell">640380</text>
  <text x="380" y="459" class="cell">A1</text>
  <text x="470" y="459" class="cell">Pippip (0,94)</text>
  <text x="720" y="459" class="cell-sec">- (non revue)</text>
  <text x="980" y="459" class="cell">Transformé</text>

  <rect x="58" y="468" width="1084" height="28" class="row-alt"/>
  <text x="74" y="487" class="cell">2026-06-15 · n° 1</text>
  <text x="290" y="487" class="cell">640380</text>
  <text x="380" y="487" class="cell">B2</text>
  <text x="470" y="487" class="cell">Pippip (0,88)</text>
  <text x="720" y="487" class="cell-strong">Pipistrelle commune</text>
  <text x="980" y="487" class="cell">Déposé</text>

  <rect x="58" y="496" width="1084" height="28" class="row"/>
  <text x="74" y="515" class="cell">2026-04-22 · n° 1</text>
  <text x="290" y="515" class="cell">640380</text>
  <text x="380" y="515" class="cell">A1</text>
  <text x="470" y="515" class="cell">Pipkuh (0,61)</text>
  <text x="720" y="515" class="cell-strong">Pipistrelle commune</text>
  <text x="980" y="515" class="cell">Déposé</text>

  <text x="74" y="556" class="cell-sec">… 635 autres observations (faites défiler).</text>

  <rect x="10" y="720" width="1180" height="30" class="footer"/>
  <text x="40" y="740" class="footer-txt">VigieChiro Companion · lecture transverse, consultation seule (aucune observation modifiée ici)</text>
</svg>
</div>

### Annotations

- **Barre d'outils** : le sélecteur **Regrouper** bascule l'inventaire entre **Par espèce** (montré ici) et **Par carré** (richesse spécifique par carré) ; un **filtre de statut** restreint la lecture (par exemple aux passages déposés) ; un champ de filtre texte, le bouton **🗺️ Carte** (vers la [carte de répartition](#variante-mode-carte-choroplethe-de-richesse)) et un bouton **Exporter** complètent.
- **Inventaire (par espèce)** : une ligne par espèce, avec son **groupe** taxonomique, ses compteurs (détections, passages, carrés, points) et sa **période** d'observation. L'espèce retenue pour chaque observation est le **taxon validé** s'il existe, sinon la **proposition Tadarida** (cf. [R17](../Modèle%20conceptuel/Règles%20métier.md#r17)) ; les pseudo-taxons « bruit » et « oiseau » sont exclus.
- **Détail (maître-détail)** : en sélectionnant une espèce, le panneau du bas liste **toutes ses observations** à travers les passages (passage, carré, point, proposition Tadarida + probabilité, votre taxon, statut). C'est la réponse à « où et quand ai-je détecté cette espèce ? », toutes nuits confondues.

### Interactions clés

| Élément | Action |
|---|---|
| Sélecteur **Regrouper** | Bascule l'inventaire entre *Par espèce* et *Par carré* |
| Bouton **🗺️ Carte** | Bascule la zone maître entre table d'inventaire et **carte de répartition** (choroplèthe de richesse) |
| Filtre **Statut** / champ texte | Restreint l'inventaire et le détail |
| Sélection d'une **espèce** | Charge ses observations dans le panneau du bas |
| **🎧 Écouter / valider** | Ouvre [M-SonsValidation](M-SonsValidation.md) **droit sur cette détection** (réécoute / validation) |
| **Ouvrir le passage →** | Ouvre [M-Passage](M-Passage.md) du passage concerné |
| **Double-clic** sur une ligne | Ouvre la **fiche de l'espèce** dans le navigateur (les deux tables) |
| **Clic droit** sur une ligne | Menu de la ligne : fiche de l'espèce, Écouter et Ouvrir le passage (détail), `Copier ▸`, `Colonnes…` |
| Bouton **📤 Exporter** | Exporte l'inventaire courant (CSV) |

## Variante - mode carte (choroplèthe de richesse)

Le bouton **« 🗺️ Carte »** remplace la table d'inventaire par une **carte de répartition** : la zone maître affiche le **carroyage officiel**, chaque carré teinté en **vert d'autant plus opaque qu'il est riche en espèces** (choroplèthe de richesse). Le panneau de détail sous la carte reste inchangé.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 470" role="img" aria-label="Maquette M-Analyse - variante mode carte (choroplethe de richesse)" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <defs>
    <linearGradient id="richesse" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#1e8449" stop-opacity="0.18"/>
      <stop offset="1" stop-color="#1e8449" stop-opacity="0.60"/>
    </linearGradient>
  </defs>
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .toolbar { fill: #ffffff; stroke: #e1e4e8; stroke-width: 1; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-ph { font: 13px sans-serif; fill: #bdc3c7; }
    .btn-active { fill: #3f51b5; stroke: #2c3a8c; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .map { fill: #eaf0f6; stroke: #b8c2cc; stroke-width: 1; }
    .grid { stroke: #cdd7e0; stroke-width: 0.6; }
    .carre-fort { fill: #1e8449; fill-opacity: 0.55; stroke: #1e8449; stroke-width: 1.2; }
    .carre-faible { fill: #1e8449; fill-opacity: 0.22; stroke: #1e8449; stroke-width: 1; }
    .carre-txt { font: 600 12px sans-serif; fill: #14532d; }
    .pt { fill: #2c3e50; }
    .pt-txt { font: 10px sans-serif; fill: #2c3e50; }
    .legende { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .legende-titre { font: 600 11px sans-serif; fill: #2c3e50; }
    .legende-borne { font: 10px sans-serif; fill: #6a737d; }
    .recadrer { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .recadrer-txt { font: 14px sans-serif; fill: #2c3e50; }
    .note { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .note-txt { font: 12px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="450" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="332" y="38" class="crumb-active">Espèces &amp; observations</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="86" class="pagetitle">Espèces &amp; observations</text>
  <text x="40" y="106" class="pagesub">12 espèces · 3 980 observations retenues · 2 carrés</text>

  <!-- Barre d'outils : bouton « 🗺️ Carte » actif (mode carte) -->
  <rect x="40" y="122" width="1120" height="44" rx="4" class="toolbar"/>
  <rect x="58" y="132" width="160" height="26" rx="3" class="field-input"/>
  <text x="72" y="149" class="field-text">Par espèce  ▾</text>
  <rect x="232" y="132" width="170" height="26" rx="3" class="field-input"/>
  <text x="246" y="149" class="field-text">Tous les statuts  ▾</text>
  <rect x="416" y="132" width="180" height="26" rx="3" class="field-input"/>
  <text x="430" y="149" class="field-ph">espèce, carré…</text>
  <rect x="866" y="132" width="130" height="26" rx="3" class="btn-active"/>
  <text x="931" y="149" class="btn-txt" text-anchor="middle">🗺️ Carte</text>
  <rect x="1010" y="132" width="150" height="26" rx="3" class="btn-secondary"/>
  <text x="1085" y="149" class="btn-txt-dark" text-anchor="middle">📤 Exporter…</text>

  <!-- Zone maître : carte de répartition -->
  <rect x="40" y="180" width="1120" height="228" class="map"/>
  <line x1="180" y1="180" x2="180" y2="408" class="grid"/>
  <line x1="320" y1="180" x2="320" y2="408" class="grid"/>
  <line x1="460" y1="180" x2="460" y2="408" class="grid"/>
  <line x1="600" y1="180" x2="600" y2="408" class="grid"/>
  <line x1="740" y1="180" x2="740" y2="408" class="grid"/>
  <line x1="880" y1="180" x2="880" y2="408" class="grid"/>
  <line x1="1020" y1="180" x2="1020" y2="408" class="grid"/>
  <line x1="40" y1="237" x2="1160" y2="237" class="grid"/>
  <line x1="40" y1="294" x2="1160" y2="294" class="grid"/>
  <line x1="40" y1="351" x2="1160" y2="351" class="grid"/>

  <!-- Deux carrés adjacents (dépt 64, côte basque) : 640380 riche (vert franc), 640381 moins riche -->
  <rect x="470" y="246" width="120" height="100" class="carre-fort"/>
  <text x="530" y="300" class="carre-txt" text-anchor="middle">640380</text>
  <text x="530" y="318" class="carre-txt" text-anchor="middle" font-weight="400">9 espèces</text>
  <circle cx="500" cy="268" r="3.5" class="pt"/>
  <circle cx="560" cy="330" r="3.5" class="pt"/>
  <rect x="590" y="246" width="120" height="100" class="carre-faible"/>
  <text x="650" y="300" class="carre-txt" text-anchor="middle">640381</text>
  <text x="650" y="318" class="carre-txt" text-anchor="middle" font-weight="400">3 espèces</text>
  <circle cx="630" cy="278" r="3.5" class="pt"/>

  <!-- Légende choroplèthe (bas gauche) -->
  <rect x="56" y="344" width="232" height="48" rx="4" class="legende"/>
  <text x="68" y="362" class="legende-titre">Espèces du carré</text>
  <text x="68" y="384" class="legende-borne">peu</text>
  <rect x="98" y="374" width="120" height="11" rx="3" fill="url(#richesse)"/>
  <text x="226" y="384" class="legende-borne">beaucoup</text>

  <!-- Bouton recadrer (haut droite) -->
  <rect x="1120" y="190" width="28" height="28" rx="4" class="recadrer"/>
  <text x="1134" y="210" class="recadrer-txt" text-anchor="middle">⤢</text>

  <!-- Rappel du panneau de détail sous la carte -->
  <rect x="40" y="418" width="1120" height="32" rx="4" class="note"/>
  <text x="58" y="438" class="note-txt">Sous la carte : le détail des observations de l'espèce sélectionnée (identique au mode tableau).</text>
</svg>
</div>

- **Choroplèthe de richesse** (aucune espèce sélectionnée) : chaque carré est vert, l'opacité croît avec le **nombre d'espèces distinctes** détectées (de `peu` à `beaucoup`). Le survol d'un carré affiche `Carré N · site · X espèces · Y détections · période`.
- **Répartition d'une espèce** (une espèce sélectionnée dans l'inventaire avant de basculer) : les carrés **où elle est présente** gardent leur couleur de richesse, les autres sont **atténués en gris** : on lit *où vit* l'espèce **et** la richesse de ces carrés.
- L'emprise des carrés vient du **carroyage officiel** ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26)) : le seul **numéro de carré** suffit, aucun GPS n'est requis. Un carré absent du carroyage reste dans le tableau mais **n'est pas tracé**. Le bouton **⤢** recadre la carte sur les carrés affichés.

### Notes pour l'implémentation

- **Lecture transverse** : l'écran agrège les observations de **tous** les passages de l'utilisateur, en **consultation seule** ; il ne modifie aucune observation (les décisions se prennent dans [M-SonsValidation](M-SonsValidation.md), et l'inventaire les **reflète** au retour).
- **Maître-détail** : `SplitPane` vertical (inventaire ou carte en haut, observations en bas). La zone maître est un `StackPane` à trois états (table **Par espèce**, table **Par carré**, **carte de répartition**) ; le **regroupement** choisit la table, le bouton **« 🗺️ Carte »** bascule sur la carte.
- **Carte de répartition** : réutilise le composant socle `CarteSites` (le même que [M-MultiSite](M-MultiSite.md)) ; la couleur des carrés (choroplèthe de richesse) est calculée côté `view`, le `ViewModel` restant agnostique de JavaFX.
- **Taxon retenu** : taxon observateur si validé, sinon Tadarida ([R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
