# M-SonsValidation - Sons & validation

> **Type** : vue plein écran (écran **« Sons & validation »**, patron *liste + écoute*). Un **seul écran** atteint de **quatre manières** (sources) : depuis un passage, depuis l'accueil, depuis l'inventaire des espèces et depuis la carte multi-sites.
> **Persona principal** : [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md) (validation, constitution d'une réothèque) ; [Marie](../Personas/Marie.md) ponctuellement.
> **Parcours couverts** : [P7 - Valider les résultats Tadarida](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), [P10 - Exporter une bibliothèque de sons de référence](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md).

Cet écran **unifie** la validation taxonomique (post-Tadarida) et la bibliothèque de sons de référence : c'est **le même écran** qui écoute un spectrogramme, décide d'un taxon et marque des références. Il se **décline selon sa source** : le contexte affiché (fil d'Ariane, colonnes) et les actions disponibles (import/export) changent, mais le squelette reste identique.

> **Patron partagé** : ce « lieu d'écoute » partage son squelette (liste ↔ panneau d'écoute `AudioView`) avec [M-Qualification](M-Qualification.md) (mode **Vérification par échantillonnage**). Les deux écrans sont les **deux manières** d'écouter les enregistrements : *valider une observation* (ici) et *vérifier la qualité d'une nuit* (M-Qualification). L'étudiant n'implémente qu'**un seul** composant d'écoute, réutilisé dans les deux.

## Maquette principale - source « passage » (validation Tadarida)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 730" role="img" aria-label="Maquette M-SonsValidation - ecran Sons et validation, source passage" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .toolbar { fill: #ffffff; stroke: #e1e4e8; stroke-width: 1; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-ph { font: 13px sans-serif; fill: #9aa0b3; }
    .btn-sec { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .chip { fill: #e8eaf6; stroke: #c5cae9; stroke-width: 1; }
    .chip-txt { font: 12px sans-serif; fill: #3f51b5; }
    .menu { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .row-sel { fill: #e8eefc; stroke: #4a90d9; stroke-width: 1.2; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sec { font: 12px sans-serif; fill: #6a737d; }
    .cell-mono { font: 11px monospace; fill: #2c3e50; }
    .badge-revoir { fill: #fef3e2; stroke: #b9770e; stroke-width: 1; }
    .badge-revoir-txt { font: 11px sans-serif; fill: #7e5109; }
    .badge-validee { fill: #e6f4ea; stroke: #1e8449; stroke-width: 1; }
    .badge-validee-txt { font: 11px sans-serif; fill: #14532d; }
    .badge-corrigee { fill: #e8eefc; stroke: #2563a3; stroke-width: 1; }
    .badge-corrigee-txt { font: 11px sans-serif; fill: #1a3a5c; }
    .star { font: 13px sans-serif; fill: #e8a838; }
    .star-off { font: 13px sans-serif; fill: #c8ced4; }
    .comment { font: 13px sans-serif; fill: #4a90d9; }
    .audio { fill: #0e0e0e; }
    .audio-bar { fill: #1c2530; }
    .audio-ctrl { fill: #ffffff; font: 14px sans-serif; }
    .audio-time { fill: #bdc3c7; font: 11px monospace; }
    .audio-cri { fill: #2c3e50; opacity: 0.45; }
    .audio-seek { stroke: #e8a838; stroke-width: 1.5; }
    .audio-note { fill: #8a949e; font: 11px sans-serif; }
    .btn-valider { fill: #1e8449; stroke: #14532d; stroke-width: 1; }
    .btn-corriger { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-ref { fill: #8e44ad; stroke: #5e2d75; stroke-width: 1; }
    .btn-white { fill: #ffffff; font: 600 12px sans-serif; }
    .combo { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .combo-txt { font: 12px sans-serif; fill: #2c3e50; }
    .status { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .status-txt { font: 12px sans-serif; fill: #4a6785; }
    .status-kbd { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="710" rx="4" class="frame"/>

  <!-- Chrome -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro PR Companion</text>
  <text x="230" y="38" class="crumb-link">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="628" y="38" class="crumb-active">Sons &amp; validation</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <!-- Barre de filtres (à la Notion) -->
  <rect x="40" y="66" width="1120" height="42" rx="4" class="toolbar"/>
  <rect x="52" y="74" width="290" height="26" rx="3" class="field"/>
  <text x="66" y="91" class="field-ph">🔍  Rechercher (fichier, espèce, commentaire)</text>
  <rect x="352" y="74" width="86" height="26" rx="3" class="btn-sec"/>
  <text x="395" y="91" class="btn-txt-dark" text-anchor="middle">+ Filtre</text>
  <rect x="450" y="74" width="146" height="26" rx="13" class="chip"/>
  <text x="464" y="91" class="chip-txt">Statut : À revoir  ✕</text>
  <rect x="606" y="74" width="176" height="26" rx="13" class="chip"/>
  <text x="620" y="91" class="chip-txt">Groupe : Chiroptères  ✕</text>
  <rect x="792" y="74" width="132" height="26" rx="13" class="chip"/>
  <text x="806" y="91" class="chip-txt">Proba ≥ 50 %  ✕</text>
  <rect x="1114" y="74" width="34" height="26" rx="3" class="menu"/>
  <text x="1131" y="92" class="btn-txt-dark" text-anchor="middle">☰</text>

  <!-- Table des observations (colonnes de contexte masquées : source = passage unique) -->
  <rect x="40" y="120" width="1120" height="28" class="table-head"/>
  <text x="54" y="139" class="col-head">FICHIER</text>
  <text x="262" y="139" class="col-head">PROPOSITION TADARIDA</text>
  <text x="452" y="139" class="col-head">PROBA.</text>
  <text x="520" y="139" class="col-head">VOTRE TAXON</text>
  <text x="700" y="139" class="col-head">FRÉQ.</text>
  <text x="792" y="139" class="col-head">STATUT</text>
  <text x="1078" y="139" class="col-head" text-anchor="middle">⭐</text>
  <text x="1128" y="139" class="col-head" text-anchor="middle">💬</text>

  <rect x="40" y="148" width="1120" height="30" class="row-sel"/>
  <text x="54" y="167" class="cell-mono">…Pass2-000.wav</text>
  <text x="262" y="167" class="cell">Pippip</text>
  <text x="452" y="167" class="cell">0,94</text>
  <text x="520" y="167" class="cell-sec">- (non revue)</text>
  <text x="700" y="167" class="cell">45 kHz</text>
  <rect x="792" y="153" width="80" height="20" rx="10" class="badge-revoir"/>
  <text x="832" y="167" class="badge-revoir-txt" text-anchor="middle">À revoir</text>
  <text x="1078" y="168" class="star-off" text-anchor="middle">☆</text>
  <text x="1128" y="168" class="comment" text-anchor="middle">💬</text>

  <rect x="40" y="178" width="1120" height="30" class="row-alt"/>
  <text x="54" y="197" class="cell-mono">…Pass2-001.wav</text>
  <text x="262" y="197" class="cell">Nyclei</text>
  <text x="452" y="197" class="cell">0,88</text>
  <text x="520" y="197" class="cell">Noctule de Leisler</text>
  <text x="700" y="197" class="cell">27 kHz</text>
  <rect x="792" y="183" width="80" height="20" rx="10" class="badge-validee"/>
  <text x="832" y="197" class="badge-validee-txt" text-anchor="middle">Validée</text>
  <text x="1078" y="198" class="star" text-anchor="middle">⭐</text>

  <rect x="40" y="208" width="1120" height="30" class="row"/>
  <text x="54" y="227" class="cell-mono">…Pass2-002.wav</text>
  <text x="262" y="227" class="cell">Pipkuh</text>
  <text x="452" y="227" class="cell">0,61</text>
  <text x="520" y="227" class="cell">Pipistrelle commune</text>
  <text x="700" y="227" class="cell">46 kHz</text>
  <rect x="792" y="213" width="80" height="20" rx="10" class="badge-corrigee"/>
  <text x="832" y="227" class="badge-corrigee-txt" text-anchor="middle">Corrigée</text>

  <rect x="40" y="238" width="1120" height="30" class="row-alt"/>
  <text x="54" y="257" class="cell-mono">…Pass2-003.wav</text>
  <text x="262" y="257" class="cell">Rhihip</text>
  <text x="452" y="257" class="cell">0,79</text>
  <text x="520" y="257" class="cell-sec">- (non revue)</text>
  <text x="700" y="257" class="cell">108 kHz</text>
  <rect x="792" y="243" width="80" height="20" rx="10" class="badge-revoir"/>
  <text x="832" y="257" class="badge-revoir-txt" text-anchor="middle">À revoir</text>

  <rect x="40" y="268" width="1120" height="30" class="row"/>
  <text x="54" y="287" class="cell-mono">…Pass2-004.wav</text>
  <text x="262" y="287" class="cell-sec">bruit</text>
  <text x="452" y="287" class="cell">0,55</text>
  <text x="520" y="287" class="cell-sec">- (non revue)</text>
  <text x="700" y="287" class="cell-sec">-</text>
  <rect x="792" y="273" width="80" height="20" rx="10" class="badge-revoir"/>
  <text x="832" y="287" class="badge-revoir-txt" text-anchor="middle">À revoir</text>

  <text x="54" y="316" class="cell-sec">… 13 autres observations (colonnes FME, Fréq. term., Début, Durée masquables via ☰ Colonnes ; contexte Date/Heure/Passage/Carré/Point masqué ici).</text>

  <!-- Séparateur (SplitPane vertical) -->
  <line x1="40" y1="330" x2="1160" y2="330" stroke="#c4ccd4" stroke-width="1"/>

  <!-- Panneau d'écoute : AudioView pleine largeur -->
  <rect x="40" y="344" width="1120" height="240" class="audio"/>
  <rect x="40" y="344" width="1120" height="34" class="audio-bar"/>
  <text x="60" y="366" class="audio-ctrl">⏮  ⏯  ⏭</text>
  <text x="140" y="366" class="audio-time">0,00 / 5,00 s</text>
  <text x="1150" y="366" class="audio-note" text-anchor="end">expansion ×10  ·  🔊 lecture auto  ·  🔁 boucle</text>
  <line x1="40" y1="461" x2="1160" y2="461" stroke="#2c3e50" stroke-width="1"/>
  <rect x="470" y="388" width="130" height="66" class="audio-cri"/>
  <line x1="520" y1="378" x2="520" y2="584" class="audio-seek"/>
  <text x="475" y="576" class="audio-note">sonogramme (haut) / spectrogramme (bas)  -  cri sélectionné surligné, lecture positionnée dessus</text>

  <!-- Barre d'actions -->
  <rect x="40" y="600" width="150" height="28" rx="4" class="combo"/>
  <text x="54" y="618" class="combo-txt">Mode : Activité  ▾</text>
  <rect x="200" y="600" width="96" height="28" rx="4" class="btn-valider"/>
  <text x="248" y="618" class="btn-white" text-anchor="middle">✔ Valider</text>
  <rect x="320" y="600" width="180" height="28" rx="4" class="combo"/>
  <text x="334" y="618" class="combo-txt">Choisir un taxon…  ▾</text>
  <rect x="510" y="600" width="100" height="28" rx="4" class="btn-corriger"/>
  <text x="560" y="618" class="btn-white" text-anchor="middle">✎ Corriger</text>
  <rect x="984" y="600" width="176" height="28" rx="4" class="btn-ref"/>
  <text x="1072" y="618" class="btn-white" text-anchor="middle">⭐ Marquer référence</text>

  <!-- Barre de statut -->
  <rect x="10" y="686" width="1180" height="34" class="status"/>
  <text x="30" y="707" class="status-txt">18 observation(s)  ·  5 / 18 revues</text>
  <text x="1170" y="707" class="status-kbd" text-anchor="end">⌨  Entrée valider  ·  R référence  ·  N suivante à revoir  ·  ↑ ↓ naviguer</text>
</svg>
</div>

### Annotations

- **Barre de filtres (« à la Notion »)** : un champ de **recherche libre** (fichier, espèce, commentaire) et un bouton **« + Filtre »** qui ajoute une **puce** parmi six critères combinés en ET : *Statut* (À revoir / Validée / Corrigée), *Groupe*, *Espèce*, *Références*, *Probabilité* (curseur), *Heure* (plage horaire de la nuit). Le tri et les filtres sont **mémorisés** d'une réouverture à l'autre.
- **Menu ☰** (coin haut-droit) : actions selon la source. Source **passage** : `📥 Importer un CSV Tadarida…` (ou `🔁 Réimporter…`), `📤 Exporter _Vu…`, option *Inclure le mode de validation à l'export*. Source **références** : `📤 Exporter la bibliothèque…`. Toujours : `Colonnes…`, `🔊 Lecture automatique à la sélection`, `🔁 Lecture en boucle`.
- **Table des observations** : une ligne par observation ; colonnes **Fichier**, **Proposition Tadarida** (verrouillée visible), **Proba.**, **Votre taxon** (« - » tant que non revue), **Fréquence**, **Statut**, **⭐ référence**, **💬 commentaire** (clic = édition en popup), plus **FME**, **Fréq. terminale**, **Début**, **Durée** (masquables). Les colonnes de **contexte** (Date/Heure/Passage/Carré/Point) sont **masquées** quand la source est un passage unique, **affichées** sinon.
- **Panneau d'écoute** (`AudioView`, pleine largeur en bas) : sonogramme + spectrogramme, **expansion temporelle ×10** (ultrasons ralentis pour être audibles), le **cri sélectionné est surligné** et la lecture s'y **positionne** automatiquement. Options *lecture auto à la sélection* et *lecture en boucle* dans le ☰.
- **Barre d'actions** : *Mode de revue* (**Activité** / **Inventaire**), **✔ Valider** (accepte la proposition Tadarida), sélecteur de taxon + **✎ Corriger** (remplace le taxon), et à droite **⭐ Marquer référence** / *Retirer la référence*. La **sélection multiple** (Ctrl/Maj+clic) agit **en lot** (tout-ou-rien, mode Activité).
- **Barre de statut** : compteurs du **sous-ensemble affiché** (`N observation(s) · X / N revues`) et rappel des raccourcis.

### Interactions clés

| Élément | Action |
|---|---|
| Sélection d'une ligne | Charge le spectrogramme, positionne la lecture sur le cri, lance l'écoute (si *lecture auto*) |
| **✔ Valider** / touche **Entrée** | Accepte la proposition Tadarida comme taxon retenu (unitaire, ou **en lot** si plusieurs lignes) |
| Sélecteur de taxon + **✎ Corriger** | Remplace le taxon retenu par le taxon choisi (autocomplete code à 6 lettres) |
| **⭐ Marquer référence** / touche **R** | Ajoute (ou retire) l'observation à la **bibliothèque de sons de référence** |
| Touche **N** | Va à la prochaine observation « À revoir » (en bouclant) |
| **💬** (clic cellule) | Édite le **commentaire** de l'observation (popup) |
| Menu **☰ → Importer / Exporter** | Import CSV Tadarida, export `_Vu` (passage), export bibliothèque (références) |
| Fil d'Ariane | Remonte selon la source (passage, accueil, espèces, carte) |

## Variante - source « références » (Sons de référence, ex-bibliothèque)

Ouvert depuis la carte d'accueil **« Sons & validation »** (prisme *Espèces & biodiversité*), l'écran affiche cette fois **toutes les observations marquées « référence »**, toutes nuits confondues : les **colonnes de contexte sont affichées** (Date, Carré, Point), le fil d'Ariane est autonome (`Accueil › Sons de référence`), et le menu **☰** propose **« 📤 Exporter la bibliothèque… »** (récapitulatif CSV + copie des fichiers `.wav` par espèce) au lieu de l'import Tadarida.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 250" role="img" aria-label="Maquette M-SonsValidation - variante source references" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .toolbar { fill: #ffffff; stroke: #e1e4e8; stroke-width: 1; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-ph { font: 13px sans-serif; fill: #9aa0b3; }
    .btn-sec { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .menu { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-mono { font: 11px monospace; fill: #2c3e50; }
    .star { font: 13px sans-serif; fill: #e8a838; }
    .note { font: 12px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="230" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro PR Companion</text>
  <text x="230" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="302" y="38" class="crumb-active">Sons de référence</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <rect x="40" y="66" width="1120" height="42" rx="4" class="toolbar"/>
  <rect x="52" y="74" width="290" height="26" rx="3" class="field"/>
  <text x="66" y="91" class="field-ph">🔍  Rechercher (fichier, espèce, commentaire)</text>
  <rect x="352" y="74" width="86" height="26" rx="3" class="btn-sec"/>
  <text x="395" y="91" class="btn-txt-dark" text-anchor="middle">+ Filtre</text>
  <rect x="1114" y="74" width="34" height="26" rx="3" class="menu"/>
  <text x="1131" y="92" class="btn-txt-dark" text-anchor="middle">☰</text>
  <text x="470" y="91" class="note">☰ → 📤 Exporter la bibliothèque…  (colonnes de contexte Date / Carré / Point affichées)</text>

  <!-- Table avec colonnes de contexte affichées -->
  <rect x="40" y="120" width="1120" height="28" class="table-head"/>
  <text x="54" y="139" class="col-head">DATE</text>
  <text x="150" y="139" class="col-head">CARRÉ</text>
  <text x="240" y="139" class="col-head">POINT</text>
  <text x="320" y="139" class="col-head">FICHIER</text>
  <text x="520" y="139" class="col-head">VOTRE TAXON</text>
  <text x="720" y="139" class="col-head">FRÉQ.</text>
  <text x="820" y="139" class="col-head">COMMENTAIRE</text>
  <text x="1128" y="139" class="col-head" text-anchor="middle">⭐</text>

  <rect x="40" y="148" width="1120" height="30" class="row"/>
  <text x="54" y="167" class="cell">2026-06-22</text>
  <text x="150" y="167" class="cell">640380</text>
  <text x="240" y="167" class="cell">A1</text>
  <text x="320" y="167" class="cell-mono">…Pass2-000.wav</text>
  <text x="520" y="167" class="cell">Noctule de Leisler</text>
  <text x="720" y="167" class="cell">27 kHz</text>
  <text x="820" y="167" class="cell">Cri social typique, fin de nuit.</text>
  <text x="1128" y="168" class="star" text-anchor="middle">⭐</text>

  <rect x="40" y="178" width="1120" height="30" class="row-alt"/>
  <text x="54" y="197" class="cell">2026-06-15</text>
  <text x="150" y="197" class="cell">640380</text>
  <text x="240" y="197" class="cell">B2</text>
  <text x="320" y="197" class="cell-mono">…Pass1-014.wav</text>
  <text x="520" y="197" class="cell">Pipistrelle commune</text>
  <text x="720" y="197" class="cell">45 kHz</text>
  <text x="820" y="197" class="cell">Signal de référence net.</text>
  <text x="1128" y="198" class="star" text-anchor="middle">⭐</text>

  <text x="54" y="226" class="note">Le panneau d'écoute (identique à la maquette principale) reste sous la table ; l'export copie les .wav par espèce.</text>
</svg>
</div>

## Sources - un écran, quatre manières de l'ouvrir

| Source | Ouvert depuis | Fil d'Ariane | Colonnes contexte | Actions ☰ propres |
|---|---|---|---|---|
| **Passage** | carte d'un passage → *Validation Tadarida* | `… › Passage N° 2 › Sons & validation` | masquées | Import CSV Tadarida, export `_Vu` |
| **Références** | carte d'accueil *Sons & validation* | `Accueil › Sons de référence` | affichées | Export bibliothèque |
| **Espèce** | [M-Analyse](M-Analyse.md) → *🎧 Écouter / valider* | `Espèces & observations › [espèce]` | affichées | (aucune) |
| **Lot** | [M-MultiSite](M-MultiSite.md) → *Écouter* (passage ou lot filtré) | `Carte & passages › [lot]` | affichées | (aucune) |

### Notes pour l'implémentation

- **Un écran, plusieurs sources** : la source est décrite par un type scellé (`SourceObservations` : `ParPassage` / `ParPassages` / `ParEspece` / `References`) porté par le **socle**. Le filtre de statut éventuel voyage **en texte** (pas de dépendance socle → validation, sinon cycle de tranches ArchUnit). L'écran adapte fil d'Ariane, colonnes visibles et actions ☰ selon cette source.
- **Composant d'écoute partagé** : l'`AudioView` (sonogramme + spectrogramme + lecteur, expansion ×10) est **fourni**. Il est réutilisé tel quel par [M-Qualification](M-Qualification.md) : l'étudiant n'implémente **qu'un seul** patron « lieu d'écoute ».
- **Actions immédiates, revue au clavier** : Valider / Corriger / Référence agissent tout de suite (pas de brouillon). Le workflow « sans souris » (Entrée / R / N / ↑↓) vise la **productivité** sur des centaines d'observations.
- **Filtres = état du sous-ensemble affiché** : les compteurs de la barre de statut portent sur le **sous-ensemble filtré**, pas sur la source entière.
- **Bibliothèque = source, pas écran séparé** : « Sons de référence » n'est **pas** un écran distinct mais la source `References` de cet écran ; l'export bibliothèque (récapitulatif CSV + copie des `.wav` par espèce, cf. [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md)) s'y fait via le menu **☰**.
