# M-Analyse - Espèces & observations

> **Type** : vue plein écran (carte « Espèces & observations » de l'accueil).
> **Persona principal** : [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md) ([Marie](../Personas/Marie.md) ponctuellement).
> **Parcours couverts** : [P11 - Inventaire des espèces détectées](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20espèces%20détectées.md).

L'écran est la porte d'entrée du prisme **biodiversité** : il **exploite transversalement** les observations (toutes nuits confondues) pour répondre à « quelles espèces ai-je détectées, où, quand, combien ? ». Il est en **maître-détail** : un **inventaire** en haut (regroupé par espèce ou par carré), et en bas le **détail des observations** de l'élément sélectionné.

## Wireframe principal - inventaire par espèce + détail

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
  <text x="28" y="38" class="chrometxt">VigieChiro PR Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="332" y="38" class="crumb-active">Espèces &amp; observations</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="86" class="pagetitle">Espèces &amp; observations</text>
  <text x="40" y="106" class="pagesub">12 espèces · 3 980 observations retenues · 2 carrés</text>

  <!-- Barre d'outils -->
  <rect x="40" y="122" width="1120" height="44" rx="4" class="toolbar"/>
  <text x="58" y="149" class="field-label">Regrouper :</text>
  <rect x="128" y="132" width="150" height="26" rx="3" class="field-input"/>
  <text x="142" y="149" class="field-text">Par espèce  ▾</text>
  <text x="300" y="149" class="field-label">Statut :</text>
  <rect x="350" y="132" width="150" height="26" rx="3" class="field-input"/>
  <text x="364" y="149" class="field-text">Tous les statuts  ▾</text>
  <rect x="520" y="132" width="180" height="26" rx="3" class="field-input"/>
  <text x="534" y="149" class="field-ph">espèce, carré…</text>
  <rect x="1000" y="132" width="140" height="26" rx="3" class="btn-secondary"/>
  <text x="1070" y="149" class="btn-txt-dark" text-anchor="middle">📤 Exporter…</text>

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
  <text x="40" y="740" class="footer-txt">SAÉ 2.01 · IUT d'Aix-Marseille · lecture transverse, consultation seule (aucune observation modifiée ici)</text>
</svg>
</div>

### Annotations

- **Barre d'outils** : le sélecteur **Regrouper** bascule l'inventaire entre **Par espèce** (montré ici) et **Par carré** (richesse spécifique par carré) ; un **filtre de statut** restreint la lecture (par exemple aux passages déposés) ; un champ de filtre texte et un bouton **Exporter** complètent.
- **Inventaire (par espèce)** : une ligne par espèce, avec son **groupe** taxonomique, ses compteurs (détections, passages, carrés, points) et sa **période** d'observation. L'espèce retenue pour chaque observation est le **taxon validé** s'il existe, sinon la **proposition Tadarida** (cf. [R17](../Modèle%20conceptuel/Règles%20métier.md#r17)) ; les pseudo-taxons « bruit » et « oiseau » sont exclus.
- **Détail (maître-détail)** : en sélectionnant une espèce, le panneau du bas liste **toutes ses observations** à travers les passages (passage, carré, point, proposition Tadarida + probabilité, votre taxon, statut). C'est la réponse à « où et quand ai-je détecté cette espèce ? », toutes nuits confondues.

### Interactions clés

| Élément | Action |
|---|---|
| Sélecteur **Regrouper** | Bascule l'inventaire entre *Par espèce* et *Par carré* |
| Filtre **Statut** / champ texte | Restreint l'inventaire et le détail |
| Sélection d'une **espèce** | Charge ses observations dans le panneau du bas |
| **🎧 Écouter / valider** | Ouvre [M-Vision-Tadarida](M-Vision-Tadarida.md) **droit sur cette détection** (réécoute / validation) |
| **Ouvrir le passage →** (ou double-clic) | Ouvre [M-Passage](M-Passage.md) du passage concerné |
| Bouton **📤 Exporter** | Exporte l'inventaire courant (CSV) |

### Notes pour l'implémentation

- **Lecture transverse** : l'écran agrège les observations de **tous** les passages de l'utilisateur, en **consultation seule** ; il ne modifie aucune observation (les décisions se prennent dans [M-Vision-Tadarida](M-Vision-Tadarida.md), et l'inventaire les **reflète** au retour).
- **Maître-détail** : `SplitPane` vertical (inventaire en haut, observations en bas). Le tableau affiché (espèces ou carrés) suit le **regroupement** sélectionné.
- **Taxon retenu** : taxon observateur si validé, sinon Tadarida ([R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
