# M-MultiSite - Vue tabulaire multi-sites

> **Type** : vue de production atteinte par l'onglet « Vue tabulaire » de la top nav.
> **Persona principal** : [Karim](../Personas/Karim.md) (3 chantiers en parallèle) et [Samuel](../Personas/Samuel.md) (24 enregistreurs × 40-50 nuits/saison = 1000+ passages). [Marie](../Personas/Marie.md) reste sur [M-Sites](M-Sites.md) plus visuelle.
> **Parcours couverts** : [P5 - Naviguer dans plusieurs sites et passages](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md).
> **Stories couvertes** : [E5.S1 - Vue arborescente](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s1), [E5.S2 - Vue tabulaire avec filtres](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s2), [E5.S3 - Filtres avancés + vues sauvegardées](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s3), [E5.S4 - Actions de masse](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s4), [E5.S5 - Import groupé](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s5).

Cette vue présente **tous les passages** de tous les sites confondus, sous forme de tableau triable et filtrable. C'est la vue de production de Karim et Samuel — elle doit rester **fluide même avec 500-1000+ lignes** (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)). Multi-sélection pour actions de masse, filtres rapides en chips, et accès à un panneau de filtres avancés avec sauvegarde de vues.

## Wireframe principal - vue tabulaire avec multi-sélection active

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-MultiSite - Vue tabulaire multi-sites" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .topnav { fill: #34495e; }
    .navtxt-active { fill: #ffffff; font: 600 13px sans-serif; }
    .navtxt-inactive { fill: #bdc3c7; font: 400 13px sans-serif; }
    .navunder { stroke: #4a90d9; stroke-width: 3; fill: none; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .view-toggle-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .view-toggle-active { fill: #4a90d9; }
    .view-toggle-txt-active { fill: #ffffff; font: 600 12px sans-serif; }
    .view-toggle-txt-inactive { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 12px sans-serif; }
    .filter-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .filter-label { font: 11px sans-serif; fill: #6a737d; }
    .filter-chip { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .filter-chip-active { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .filter-chip-txt { font: 12px sans-serif; fill: #2c3e50; }
    .filter-chip-txt-active { font: 600 12px sans-serif; fill: #2563a3; }
    .selection-bar { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .selection-txt { font: 600 13px sans-serif; fill: #2563a3; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-selected { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .col-head-sortable { font: 600 11px sans-serif; fill: #2563a3; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sec { font: 12px sans-serif; fill: #6a737d; }
    .cell-mono { font: 12px monospace; fill: #2c3e50; }
    .checkbox-empty { fill: #ffffff; stroke: #6a737d; stroke-width: 1.5; }
    .checkbox-checked { fill: #2563a3; stroke: #154360; stroke-width: 1; }
    .checkbox-icon { fill: #ffffff; font: 700 11px sans-serif; }
    .badge-pill { stroke-width: 1; }
    .badge-imp { fill: #f6f8fa; stroke: #6a737d; }
    .badge-trans { fill: #fef9e7; stroke: #b9770e; }
    .badge-veri { fill: #fde7e7; stroke: #a93226; }
    .badge-ok { fill: #d4edda; stroke: #1e8449; }
    .badge-dep { fill: #cce4f7; stroke: #2563a3; }
    .badge-txt { font: 600 10px sans-serif; }
    .action-icon { fill: #6a737d; font: 14px sans-serif; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="880" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="1180" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">🦇 VigieChiro PR Companion</text>
  <text x="1140" y="31" class="titletxt" text-anchor="end">— ☐ ☓</text>

  <rect x="10" y="42" width="1180" height="40" class="topnav"/>
  <text x="40" y="67" class="navtxt-inactive">🏠 Mes sites</text>
  <text x="170" y="67" class="navtxt-inactive">📥 Importer une nuit</text>
  <text x="330" y="67" class="navtxt-active">📊 Vue tabulaire</text>
  <line x1="322" y1="78" x2="430" y2="78" class="navunder"/>
  <text x="470" y="67" class="navtxt-inactive">⚙ Paramètres</text>
  <text x="1140" y="67" class="navtxt-inactive" text-anchor="end">👤 Local</text>

  <text x="40" y="120" class="pagetitle">📊 Tous mes passages (38 lignes affichées sur 1 247)</text>
  <text x="40" y="142" class="pagesub">Vue tabulaire haute densité pour Karim et Samuel. Multi-sélection pour actions de masse.</text>

  <!-- Toggle Arbre / Tableau + bouton import groupé -->
  <rect x="840" y="115" width="180" height="32" rx="16" class="view-toggle-bg"/>
  <rect x="842" y="117" width="88" height="28" rx="14" class="view-toggle-active"/>
  <text x="886" y="135" class="view-toggle-txt-active" text-anchor="middle">📊 Tableau</text>
  <text x="976" y="135" class="view-toggle-txt-inactive" text-anchor="middle">🌳 Arbre</text>

  <rect x="1030" y="115" width="130" height="32" rx="4" class="btn-primary"/>
  <text x="1095" y="136" class="btn-txt" text-anchor="middle" font-size="12">📥 Import groupé</text>

  <!-- Barre de filtres rapides -->
  <rect x="40" y="170" width="1120" height="50" rx="4" class="filter-bar"/>
  <text x="55" y="200" class="filter-label">FILTRES :</text>

  <!-- Chip Site (multi-select) -->
  <rect x="115" y="183" width="160" height="24" rx="12" class="filter-chip-active"/>
  <text x="195" y="200" class="filter-chip-txt-active" text-anchor="middle">🌐 3 sites · ▾</text>

  <!-- Chip Statut -->
  <rect x="285" y="183" width="170" height="24" rx="12" class="filter-chip"/>
  <text x="370" y="200" class="filter-chip-txt" text-anchor="middle">⚙ Statut : tous · ▾</text>

  <!-- Chip Verdict -->
  <rect x="465" y="183" width="170" height="24" rx="12" class="filter-chip"/>
  <text x="550" y="200" class="filter-chip-txt" text-anchor="middle">✓ Verdict : tous · ▾</text>

  <!-- Chip Date -->
  <rect x="645" y="183" width="200" height="24" rx="12" class="filter-chip-active"/>
  <text x="745" y="200" class="filter-chip-txt-active" text-anchor="middle">📅 Depuis 01/06/2026 · ▾</text>

  <!-- Bouton + Filtre avancé -->
  <rect x="860" y="183" width="170" height="24" rx="12" class="filter-chip"/>
  <text x="945" y="200" class="filter-chip-txt" text-anchor="middle">+ Filtre avancé...</text>

  <!-- Vues sauvegardées -->
  <text x="1045" y="200" class="filter-chip-txt-active" text-decoration="underline">⭐ Mes vues ▾</text>

  <!-- Barre d'actions de masse (visible car sélection non vide) -->
  <rect x="40" y="232" width="1120" height="46" rx="4" class="selection-bar"/>
  <text x="60" y="260" class="selection-txt">✓ 3 passages sélectionnés (sur 38 affichés)</text>

  <rect x="450" y="240" width="130" height="30" rx="3" class="btn-secondary"/>
  <text x="515" y="259" class="btn-txt-dark" text-anchor="middle">🎧 Verdict en masse</text>

  <rect x="590" y="240" width="120" height="30" rx="3" class="btn-secondary"/>
  <text x="650" y="259" class="btn-txt-dark" text-anchor="middle">📤 Exporter CSV</text>

  <rect x="720" y="240" width="120" height="30" rx="3" class="btn-secondary"/>
  <text x="780" y="259" class="btn-txt-dark" text-anchor="middle">🏷 Re-rattacher</text>

  <rect x="1010" y="240" width="130" height="30" rx="3" class="btn-danger"/>
  <text x="1075" y="259" class="btn-txt-danger" text-anchor="middle">🗑 Supprimer</text>

  <!-- ============== Tableau ============== -->
  <rect x="40" y="290" width="1120" height="34" class="table-head"/>
  <rect x="52" y="302" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="312" class="col-head-sortable">SITE / POINT ↕</text>
  <text x="280" y="312" class="col-head-sortable">PASS. ↕</text>
  <text x="345" y="312" class="col-head-sortable">DATE ↓</text>
  <text x="455" y="312" class="col-head-sortable">STATUT ↕</text>
  <text x="600" y="312" class="col-head-sortable">VERDICT ↕</text>
  <text x="725" y="312" class="col-head-sortable">PR ↕</text>
  <text x="845" y="312" class="col-head-sortable">SÉQ. ↕</text>
  <text x="940" y="312" class="col-head-sortable">DÉPOSÉ LE ↕</text>
  <text x="1085" y="312" class="col-head" text-anchor="middle">ACTIONS</text>

  <!-- Ligne 1 : sélectionnée -->
  <rect x="40" y="324" width="1120" height="30" class="table-row-selected"/>
  <rect x="52" y="334" width="14" height="14" rx="2" class="checkbox-checked"/>
  <text x="59" y="345" class="checkbox-icon" text-anchor="middle">✓</text>
  <text x="80" y="345" class="cell" font-weight="600">640380 / A1</text>
  <text x="180" y="345" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="345" class="cell">2/2026</text>
  <text x="345" y="345" class="cell">22/06/2026</text>
  <rect x="450" y="332" width="100" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="500" y="345" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="600" y="345" class="cell-sec">—</text>
  <text x="725" y="345" class="cell-mono">1925492</text>
  <text x="845" y="345" class="cell">3 614</text>
  <text x="940" y="345" class="cell-sec">—</text>
  <text x="1075" y="345" class="action-icon">🎧 ⋯</text>

  <!-- Ligne 2 : sélectionnée -->
  <rect x="40" y="354" width="1120" height="30" class="table-row-selected"/>
  <rect x="52" y="364" width="14" height="14" rx="2" class="checkbox-checked"/>
  <text x="59" y="375" class="checkbox-icon" text-anchor="middle">✓</text>
  <text x="80" y="375" class="cell" font-weight="600">640380 / B2</text>
  <text x="180" y="375" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="375" class="cell">2/2026</text>
  <text x="345" y="375" class="cell">22/06/2026</text>
  <rect x="450" y="362" width="100" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="500" y="375" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="600" y="375" class="cell-sec">—</text>
  <text x="725" y="375" class="cell-mono">1925492</text>
  <text x="845" y="375" class="cell">2 870</text>
  <text x="940" y="375" class="cell-sec">—</text>
  <text x="1075" y="375" class="action-icon">🎧 ⋯</text>

  <!-- Ligne 3 : sélectionnée -->
  <rect x="40" y="384" width="1120" height="30" class="table-row-selected"/>
  <rect x="52" y="394" width="14" height="14" rx="2" class="checkbox-checked"/>
  <text x="59" y="405" class="checkbox-icon" text-anchor="middle">✓</text>
  <text x="80" y="405" class="cell" font-weight="600">640380 / C3</text>
  <text x="180" y="405" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="405" class="cell">2/2026</text>
  <text x="345" y="405" class="cell">22/06/2026</text>
  <rect x="450" y="392" width="100" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="500" y="405" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="600" y="405" class="cell-sec">—</text>
  <text x="725" y="405" class="cell-mono">1925487</text>
  <text x="845" y="405" class="cell">1 942</text>
  <text x="940" y="405" class="cell-sec">—</text>
  <text x="1075" y="405" class="action-icon">🎧 ⋯</text>

  <!-- Ligne 4 -->
  <rect x="40" y="414" width="1120" height="30" class="table-row-alt"/>
  <rect x="52" y="424" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="435" class="cell">752204 / A1</text>
  <text x="180" y="435" class="cell-sec">ZAC Nord</text>
  <text x="280" y="435" class="cell">2/2026</text>
  <text x="345" y="435" class="cell">18/06/2026</text>
  <rect x="450" y="422" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="435" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="422" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="620" y="435" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="725" y="435" class="cell-mono">1925501</text>
  <text x="845" y="435" class="cell">2 558</text>
  <text x="940" y="435" class="cell">19/06/2026</text>
  <text x="1075" y="435" class="action-icon">📂 ⋯</text>

  <!-- Ligne 5 -->
  <rect x="40" y="444" width="1120" height="30" class="table-row"/>
  <rect x="52" y="454" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="465" class="cell">752204 / B2</text>
  <text x="180" y="465" class="cell-sec">ZAC Nord</text>
  <text x="280" y="465" class="cell">2/2026</text>
  <text x="345" y="465" class="cell">18/06/2026</text>
  <rect x="450" y="452" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="465" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="452" width="65" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="627" y="465" class="badge-txt" fill="#7e5109" text-anchor="middle">Douteux</text>
  <text x="725" y="465" class="cell-mono">1925501</text>
  <text x="845" y="465" class="cell">2 104</text>
  <text x="940" y="465" class="cell">19/06/2026</text>
  <text x="1075" y="465" class="action-icon">📂 ⋯</text>

  <!-- Ligne 6 -->
  <rect x="40" y="474" width="1120" height="30" class="table-row-alt"/>
  <rect x="52" y="484" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="495" class="cell">640380 / A1</text>
  <text x="180" y="495" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="495" class="cell">1/2026</text>
  <text x="345" y="495" class="cell">22/04/2026</text>
  <rect x="450" y="482" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="495" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="482" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="620" y="495" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="725" y="495" class="cell-mono">1925492</text>
  <text x="845" y="495" class="cell">2 114</text>
  <text x="940" y="495" class="cell">24/04/2026</text>
  <text x="1075" y="495" class="action-icon">📂 ⋯</text>

  <!-- Ligne 7 -->
  <rect x="40" y="504" width="1120" height="30" class="table-row"/>
  <rect x="52" y="514" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="525" class="cell">640380 / B2</text>
  <text x="180" y="525" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="525" class="cell">1/2026</text>
  <text x="345" y="525" class="cell">22/04/2026</text>
  <rect x="450" y="512" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="525" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="512" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="620" y="525" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="725" y="525" class="cell-mono">1925492</text>
  <text x="845" y="525" class="cell">2 558</text>
  <text x="940" y="525" class="cell">24/04/2026</text>
  <text x="1075" y="525" class="action-icon">📂 ⋯</text>

  <!-- Ligne 8 -->
  <rect x="40" y="534" width="1120" height="30" class="table-row-alt"/>
  <rect x="52" y="544" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="555" class="cell">640380 / C3</text>
  <text x="180" y="555" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="555" class="cell">1/2026</text>
  <text x="345" y="555" class="cell">22/04/2026</text>
  <rect x="450" y="542" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="555" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="542" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="620" y="555" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="725" y="555" class="cell-mono">1925487</text>
  <text x="845" y="555" class="cell">1 783</text>
  <text x="940" y="555" class="cell">24/04/2026</text>
  <text x="1075" y="555" class="action-icon">📂 ⋯</text>

  <!-- Ligne 9 -->
  <rect x="40" y="564" width="1120" height="30" class="table-row"/>
  <rect x="52" y="574" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="585" class="cell">752204 / A1</text>
  <text x="180" y="585" class="cell-sec">ZAC Nord</text>
  <text x="280" y="585" class="cell">1/2026</text>
  <text x="345" y="585" class="cell">15/04/2026</text>
  <rect x="450" y="572" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="585" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="572" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="620" y="585" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="725" y="585" class="cell-mono">1925501</text>
  <text x="845" y="585" class="cell">2 234</text>
  <text x="940" y="585" class="cell">17/04/2026</text>
  <text x="1075" y="585" class="action-icon">📂 ⋯</text>

  <!-- Ligne 10 : à jeter -->
  <rect x="40" y="594" width="1120" height="30" class="table-row-alt"/>
  <rect x="52" y="604" width="14" height="14" rx="2" class="checkbox-empty"/>
  <text x="80" y="615" class="cell">640380 / A1</text>
  <text x="180" y="615" class="cell-sec">Étang Tuilière</text>
  <text x="280" y="615" class="cell">test</text>
  <text x="345" y="615" class="cell">08/04/2026</text>
  <rect x="450" y="602" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="500" y="615" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="595" y="602" width="55" height="18" rx="9" class="badge-pill badge-veri"/>
  <text x="622" y="615" class="badge-txt" fill="#a93226" text-anchor="middle">À jeter</text>
  <text x="725" y="615" class="cell-mono">1925492</text>
  <text x="845" y="615" class="cell">421</text>
  <text x="940" y="615" class="cell-sec">—</text>
  <text x="1075" y="615" class="action-icon">📂 ⋯</text>

  <!-- Lignes hidden (indicateur) -->
  <text x="600" y="650" class="cell-sec" text-anchor="middle" font-style="italic">↓ 28 lignes supplémentaires (faites défiler) ↓</text>

  <!-- Lignes scrollées en aperçu -->
  <rect x="40" y="660" width="1120" height="30" class="table-row" opacity="0.5"/>
  <rect x="52" y="670" width="14" height="14" rx="2" class="checkbox-empty" opacity="0.5"/>
  <text x="80" y="681" class="cell" opacity="0.5">013570 / A1</text>
  <text x="180" y="681" class="cell-sec" opacity="0.5">Test_Maison</text>

  <rect x="40" y="690" width="1120" height="30" class="table-row-alt" opacity="0.3"/>

  <!-- Pagination -->
  <text x="60" y="755" class="cell-sec">Affichage 1-38 sur 1 247 passages filtrés (sur 1 247 total)</text>
  <rect x="780" y="745" width="30" height="24" rx="3" class="btn-secondary"/>
  <text x="795" y="761" class="btn-txt-dark" text-anchor="middle">‹</text>
  <rect x="815" y="745" width="30" height="24" rx="3" class="btn-primary"/>
  <text x="830" y="761" class="btn-txt" text-anchor="middle">1</text>
  <rect x="850" y="745" width="30" height="24" rx="3" class="btn-secondary"/>
  <text x="865" y="761" class="btn-txt-dark" text-anchor="middle">2</text>
  <rect x="885" y="745" width="30" height="24" rx="3" class="btn-secondary"/>
  <text x="900" y="761" class="btn-txt-dark" text-anchor="middle">3</text>
  <rect x="920" y="745" width="40" height="24" rx="3" class="btn-secondary"/>
  <text x="940" y="761" class="btn-txt-dark" text-anchor="middle">...</text>
  <rect x="965" y="745" width="40" height="24" rx="3" class="btn-secondary"/>
  <text x="985" y="761" class="btn-txt-dark" text-anchor="middle">33</text>
  <rect x="1010" y="745" width="30" height="24" rx="3" class="btn-secondary"/>
  <text x="1025" y="761" class="btn-txt-dark" text-anchor="middle">›</text>

  <text x="1140" y="761" class="cell-sec" text-anchor="end">38 par page ▾</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💡 Astuces : Ctrl+clic pour sélection multiple · Maj+clic pour plage · Ctrl+A pour tout sélectionner</text>
  <text x="1140" y="880" class="footer-txt" text-anchor="end">1 247 passages · 3 sites · 5 enregistreurs</text>
</svg>
</div>

### Annotations

- **Page header** : titre dynamique avec compteur de lignes affichées vs total (« 38 lignes affichées sur 1 247 »). Toggle de vue Tableau / Arbre + bouton primary « Import groupé » à droite.
- **Barre de filtres rapides** : chips compactes pour les filtres usuels. Les chips **actifs** (Sites, Date) sont en bleu, les chips **inactifs** (Statut, Verdict) sont en blanc. Bouton « + Filtre avancé... » pour ouvrir le panel détaillé (cf. variante). Lien « ⭐ Mes vues ▾ » pour les vues sauvegardées ([E5.S3](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s3)).
- **Barre de sélection** (apparaît uniquement quand des lignes sont sélectionnées) : compteur + 4 boutons d'action de masse :
    - 🎧 Verdict en masse (assigner OK / Douteux / À jeter en lot)
    - 📤 Exporter CSV (récapitulatif des passages sélectionnés)
    - 🏷 Re-rattacher (modifier le site/point en masse)
    - 🗑 Supprimer (avec confirmation forte, action en bouton danger)
- **En-tête de tableau** : toutes les colonnes sont **triables** (icône ↕ ou ↓ pour la colonne triée). La colonne `Date` est triée descendante.
- **Lignes sélectionnées** : fond bleu clair `.table-row-selected`, checkbox cochée en bleu foncé. Les 3 premières lignes sont sélectionnées (passages 2/2026 d'un même site).
- **Badges** : statut workflow et verdict ont chacun leur badge coloré pour identification rapide.
- **Pagination** : 38 lignes par page (paramétrable), pages numérotées avec ellipsis pour les grands volumes (33 pages × 38 = 1 254 = ~1 247 lignes filtrées).
- **Footer** : astuces clavier (Ctrl+clic, Maj+clic, Ctrl+A) + récap global de la base.

### Interactions clés

| Élément | Action |
|---|---|
| Toggle **Tableau / Arbre** | Bascule vers la variante arborescence (cf. ci-dessous) |
| Bouton **📥 Import groupé** | Ouvre [M-Import](M-Import.md) en mode multi-dossiers ([E5.S5](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s5)) |
| Chip de filtre actif | Clic pour ouvrir le sélecteur multi-valeurs ; clic ✕ pour retirer le filtre |
| Bouton **+ Filtre avancé...** | Ouvre le panneau de filtres avancés (cf. variante 2) |
| **⭐ Mes vues** | Menu déroulant des combinaisons de filtres sauvegardées |
| Checkbox header | Sélectionne / désélectionne toutes les lignes affichées |
| Clic sur une ligne | Ouvre [M-Passage](M-Passage.md) |
| Ctrl+clic / Maj+clic | Multi-sélection |
| **🗑 Supprimer** sur sélection | Modale de confirmation forte (saisie « SUPPRIMER » obligatoire) |
| Action ⋯ par ligne | Menu contextuel (Ouvrir, Vérifier, Marquer déposé, Modifier rattachement, Supprimer) |

---

## Variante - panneau de filtres avancés ouvert

Ouvert par clic sur **+ Filtre avancé...** dans la barre de filtres rapides. Permet de composer des filtres multi-critères en logique ET et de sauvegarder la combinaison comme **vue nommée**.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 480" role="img" aria-label="Maquette M-MultiSite - Filtres avancés" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: rgba(44,62,80,0.55);">
  <style>
    .modal-bg { fill: rgba(44,62,80,0.5); }
    .modal-frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .modal-header { fill: #2c3e50; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .modal-close { fill: #ffffff; font: 600 18px sans-serif; }
    .group-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .group-sub { font: 11px sans-serif; fill: #6a737d; }
    .multi-select { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .multi-select-chip { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .multi-select-chip-txt { font: 11px sans-serif; fill: #2563a3; }
    .multi-select-placeholder { font: 13px sans-serif; fill: #bdc3c7; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-label { font: 600 12px sans-serif; fill: #2c3e50; }
    .counter-box { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .counter-num { font: 700 22px sans-serif; fill: #2563a3; }
    .counter-label { font: 13px sans-serif; fill: #2563a3; }
    .save-view-section { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .save-view-title { font: 600 12px sans-serif; fill: #5d4e00; }
    .save-view-input { fill: #ffffff; stroke: #b9770e; stroke-width: 1; }
    .save-view-placeholder { font: 12px sans-serif; fill: #b9770e; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-primary-warm { fill: #b9770e; stroke: #7e5109; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
  </style>

  <rect x="0" y="0" width="1200" height="480" class="modal-bg"/>
  <rect x="150" y="20" width="900" height="440" rx="6" class="modal-frame"/>

  <rect x="150" y="20" width="900" height="48" rx="6" class="modal-header"/>
  <rect x="150" y="50" width="900" height="18" class="modal-header"/>
  <text x="174" y="50" class="modal-header-txt">⚙ Filtres avancés</text>
  <text x="1024" y="52" class="modal-close" text-anchor="end">✕</text>

  <!-- Colonne gauche : critères -->
  <!-- Site -->
  <text x="174" y="100" class="group-title">🌐 Sites</text>
  <text x="174" y="115" class="group-sub">Multi-sélection (logique OU)</text>
  <rect x="174" y="125" width="380" height="50" rx="3" class="multi-select"/>
  <rect x="184" y="135" width="100" height="22" rx="11" class="multi-select-chip"/>
  <text x="234" y="150" class="multi-select-chip-txt" text-anchor="middle">640380 ✕</text>
  <rect x="290" y="135" width="100" height="22" rx="11" class="multi-select-chip"/>
  <text x="340" y="150" class="multi-select-chip-txt" text-anchor="middle">752204 ✕</text>
  <rect x="396" y="135" width="100" height="22" rx="11" class="multi-select-chip"/>
  <text x="446" y="150" class="multi-select-chip-txt" text-anchor="middle">013570 ✕</text>

  <!-- Statut -->
  <text x="174" y="200" class="group-title">⚙ Statut workflow</text>
  <text x="174" y="215" class="group-sub">Multi-sélection</text>
  <rect x="174" y="225" width="380" height="36" rx="3" class="multi-select"/>
  <rect x="184" y="234" width="90" height="18" rx="9" class="multi-select-chip"/>
  <text x="229" y="246" class="multi-select-chip-txt" text-anchor="middle">Transformé ✕</text>
  <text x="290" y="246" class="multi-select-placeholder">+ ajouter...</text>

  <!-- Verdict -->
  <text x="174" y="285" class="group-title">✓ Verdict</text>
  <rect x="174" y="295" width="380" height="36" rx="3" class="multi-select"/>
  <text x="184" y="318" class="multi-select-placeholder">+ tous (cliquez pour filtrer)</text>

  <!-- Plage dates -->
  <text x="174" y="355" class="group-title">📅 Date de session d'enregistrement</text>
  <rect x="174" y="365" width="170" height="30" rx="3" class="field-input"/>
  <text x="184" y="384" class="field-text">01/06/2026</text>
  <text x="354" y="384" class="field-label">→</text>
  <rect x="384" y="365" width="170" height="30" rx="3" class="field-input"/>
  <text x="394" y="384" class="field-text">(aujourd'hui)</text>

  <!-- Colonne droite : Enregistreur + Compteur + Sauvegarde -->
  <text x="600" y="100" class="group-title">📻 Enregistreur</text>
  <text x="600" y="115" class="group-sub">Par n° de série (multi-sélection)</text>
  <rect x="600" y="125" width="420" height="36" rx="3" class="multi-select"/>
  <text x="610" y="148" class="multi-select-placeholder">+ tous les enregistreurs</text>

  <!-- Compteur résultats live -->
  <rect x="600" y="195" width="420" height="80" rx="6" class="counter-box"/>
  <text x="810" y="225" class="counter-num" text-anchor="middle">38 passages</text>
  <text x="810" y="245" class="counter-label" text-anchor="middle">correspondent aux critères ci-contre</text>
  <text x="810" y="265" class="group-sub" text-anchor="middle">(mise à jour en temps réel)</text>

  <!-- Sauvegarde de vue -->
  <rect x="600" y="295" width="420" height="105" rx="6" class="save-view-section"/>
  <text x="620" y="320" class="save-view-title">⭐ Sauvegarder cette combinaison comme vue</text>
  <text x="620" y="338" class="group-sub">Pour la retrouver dans le menu « Mes vues ».</text>
  <rect x="620" y="350" width="290" height="32" rx="3" class="save-view-input"/>
  <text x="630" y="370" class="save-view-placeholder">ex. « Mes nuits Pass 2 à vérifier »</text>
  <rect x="920" y="350" width="80" height="32" rx="3" class="btn-primary-warm"/>
  <text x="960" y="371" class="btn-txt" text-anchor="middle">⭐ Saver</text>

  <!-- Boutons en bas -->
  <rect x="700" y="420" width="120" height="32" rx="4" class="btn-secondary"/>
  <text x="760" y="441" class="btn-txt-dark" text-anchor="middle">↺ Réinitialiser</text>
  <rect x="830" y="420" width="100" height="32" rx="4" class="btn-secondary"/>
  <text x="880" y="441" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="940" y="420" width="100" height="32" rx="4" class="btn-primary"/>
  <text x="990" y="441" class="btn-txt" text-anchor="middle">Appliquer</text>
</svg>
</div>

### Notes sur les filtres avancés

- **5 critères composables** en logique ET entre groupes, OU à l'intérieur d'un groupe (multi-sélection) :
    1. Sites (chips multi-sélection)
    2. Statut workflow (chips multi-sélection)
    3. Verdict (chips multi-sélection)
    4. Plage de dates (deux date pickers)
    5. Enregistreur (multi-sélection par n° de série)
- **Compteur en temps réel** « 38 passages correspondent aux critères » mis à jour à chaque modification. Évite à l'utilisateur de tester aveuglément.
- **Sauvegarde de vue** (encart jaune) : zone dédiée pour nommer la combinaison de filtres et la sauvegarder. Pour les retrouver, menu **⭐ Mes vues** dans la barre rapide.
- **Boutons** : `↺ Réinitialiser` (vide tous les critères), `Annuler` (ferme sans appliquer), `Appliquer` (ferme et applique).

---

## Variante - vue arborescente (toggle « 🌳 Arbre »)

Le même contenu organisé hiérarchiquement : un nœud par site → ses points → ses passages. C'est la vue par défaut pour [Marie](../Personas/Marie.md) sur [M-Sites](M-Sites.md), mais avec arborescence dépliée et compteurs visibles. Implémente [E5.S1](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s1).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 480" role="img" aria-label="Maquette M-MultiSite - Vue arborescente" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .pagetitle { font: 700 20px sans-serif; fill: #2c3e50; }
    .tree-site { font: 700 14px sans-serif; fill: #2c3e50; }
    .tree-point { font: 600 13px sans-serif; fill: #2c3e50; }
    .tree-passage { font: 12px sans-serif; fill: #2c3e50; }
    .tree-meta { font: 11px sans-serif; fill: #6a737d; }
    .tree-line { stroke: #d0d7de; stroke-width: 1; }
    .tree-icon-collapse { fill: #6a737d; }
    .badge-pill { stroke-width: 1; }
    .badge-trans { fill: #fef9e7; stroke: #b9770e; }
    .badge-ok { fill: #d4edda; stroke: #1e8449; }
    .badge-dep { fill: #cce4f7; stroke: #2563a3; }
    .badge-txt { font: 600 10px sans-serif; }
    .verifier-link { font: 600 12px sans-serif; fill: #b9770e; }
    .view-toggle-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .view-toggle-active { fill: #4a90d9; }
    .view-toggle-txt-active { fill: #ffffff; font: 600 12px sans-serif; }
    .view-toggle-txt-inactive { fill: #2c3e50; font: 600 12px sans-serif; }
  </style>

  <rect x="10" y="10" width="1180" height="460" rx="4" class="frame"/>

  <text x="40" y="50" class="pagetitle">🌳 Mes 1 247 passages, vue arborescente</text>

  <!-- Toggle Arbre / Tableau (Arbre actif) -->
  <rect x="980" y="35" width="180" height="32" rx="16" class="view-toggle-bg"/>
  <text x="1024" y="56" class="view-toggle-txt-inactive" text-anchor="middle">📊 Tableau</text>
  <rect x="1070" y="37" width="88" height="28" rx="14" class="view-toggle-active"/>
  <text x="1114" y="56" class="view-toggle-txt-active" text-anchor="middle">🌳 Arbre</text>

  <!-- Site 1 : 640380 -->
  <text x="50" y="100" class="tree-icon-collapse" font-size="14">▾</text>
  <text x="70" y="100" class="tree-site">🌐 Carré 640380 — Étang de la Tuilière</text>
  <text x="480" y="100" class="tree-meta">3 points · 7 passages cette saison · dernier passage il y a 2 j</text>

  <!-- Point A1 -->
  <line x1="62" y1="110" x2="62" y2="142" class="tree-line"/>
  <text x="80" y="130" class="tree-icon-collapse" font-size="12">▾</text>
  <text x="100" y="130" class="tree-point">📍 Point A1</text>
  <text x="220" y="130" class="tree-meta">3 passages · 1 à vérifier ⚠</text>
  <rect x="420" y="118" width="100" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="470" y="131" class="badge-txt" fill="#7e5109" text-anchor="middle">Pass 2 (à vérifier)</text>
  <text x="540" y="130" class="verifier-link">🎧 Vérifier l'enregistrement →</text>

  <!-- Passages A1 -->
  <line x1="82" y1="138" x2="82" y2="200" class="tree-line"/>
  <text x="100" y="160" class="tree-passage">• Passage 2 (22/06/2026)</text>
  <rect x="320" y="148" width="100" height="18" rx="9" class="badge-pill badge-trans"/>
  <text x="370" y="161" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="440" y="160" class="tree-meta">3 614 séquences · à vérifier</text>

  <text x="100" y="183" class="tree-passage">• Passage 1 (22/04/2026)</text>
  <rect x="320" y="171" width="100" height="18" rx="9" class="badge-pill badge-dep"/>
  <text x="370" y="184" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="430" y="171" width="50" height="18" rx="9" class="badge-pill badge-ok"/>
  <text x="455" y="184" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="495" y="183" class="tree-meta">déposé le 24/04 · 2 114 séquences</text>

  <text x="100" y="200" class="tree-passage" fill="#6a737d" font-style="italic">• Passage test (08/04/2026) — À jeter</text>

  <!-- Point B2 -->
  <line x1="62" y1="138" x2="62" y2="230" class="tree-line"/>
  <text x="80" y="225" class="tree-icon-collapse" font-size="12">▸</text>
  <text x="100" y="225" class="tree-point">📍 Point B2</text>
  <text x="220" y="225" class="tree-meta">2 passages · tous vérifiés</text>

  <!-- Point C3 -->
  <line x1="62" y1="225" x2="62" y2="252" class="tree-line"/>
  <text x="80" y="248" class="tree-icon-collapse" font-size="12">▸</text>
  <text x="100" y="248" class="tree-point">📍 Point C3</text>
  <text x="220" y="248" class="tree-meta">2 passages · tous déposés</text>

  <!-- Site 2 : 752204 -->
  <text x="50" y="290" class="tree-icon-collapse" font-size="14">▸</text>
  <text x="70" y="290" class="tree-site">🌐 Carré 752204 — ZAC Nord</text>
  <text x="350" y="290" class="tree-meta">2 points · 4 passages · dernier passage il y a 8 j</text>

  <!-- Site 3 : 013570 -->
  <text x="50" y="320" class="tree-icon-collapse" font-size="14">▸</text>
  <text x="70" y="320" class="tree-site">🌐 Carré 013570 — Test_Maison</text>
  <text x="370" y="320" class="tree-meta">1 point · aucun passage</text>

  <!-- Sites Samuel -->
  <text x="50" y="360" class="tree-icon-collapse" font-size="14">▸</text>
  <text x="70" y="360" class="tree-site">🌐 [+ 21 autres sites de la campagne Samuel...]</text>
  <text x="490" y="360" class="tree-meta">1 233 passages cumulés cette saison</text>

  <text x="40" y="430" class="tree-meta" font-style="italic">💡 Cette vue est pratique pour 5-10 sites. Au-delà, la vue tabulaire reste plus efficace pour parcourir.</text>
</svg>
</div>

### Notes sur la vue arborescente

- **Hiérarchie à 3 niveaux** : Site → Point → Passage. Chaque nœud est dépliable / repliable.
- **Compteurs** à chaque niveau : nombre de points par site, nombre de passages par point, statut/verdict pour chaque passage.
- **Action contextuelle** : sur un point qui contient un passage à vérifier, lien direct « 🎧 Vérifier l'enregistrement → » pour gagner du temps.
- **Astuce footer** : signale que cette vue est lisible pour 5-10 sites mais devient inadaptée à l'échelle de Samuel (24 PR × 40+ nuits) — qui préfère le tableau filtré.

## Notes pour l'implémentation

- **Performance** : à 1 000+ passages, la TableView JavaFX doit **virtualiser le rendu** (ne créer que les lignes visibles). C'est le comportement par défaut, à vérifier en test de charge.
- **Pagination** : 38 lignes par page est une suggestion. Le test décisif est : « après application d'un filtre, le rendu doit être visible en < 200 ms même sur 1 000 passages ».
- **Persistance des vues sauvegardées** : table `saved_view(name, created_at, filters_json)` en BD avec `filters_json` qui sérialise la combinaison de critères ([E5.S3](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s3)).
- **Actions de masse** : implémentées via `Task<Void>` JavaFX pour ne pas freezer l'IHM sur N passages. Journal d'opérations à archiver pour audit ([E5.S4](../Story%20mapping/E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s4) critère « journal d'opérations »).
- **Toggle Tableau / Arbre** : préserve l'état des filtres et de la sélection en basculant entre les deux vues.
