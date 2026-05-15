# M-Vision-Tadarida - Validation taxonomique des résultats Tadarida

> **⚠️ CIBLE ÉTIRÉE HORS MVP STRICT.** Cette maquette correspond aux épopées [E7](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md) (SHOULD, cible étirable principale) et [E8](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md) (COULD au mieux). À engager **uniquement** si la chaîne fil rouge ([M-Sites](M-Sites.md) → [M-Import](M-Import.md) → [M-Qualification](M-Qualification.md) → [M-Lot](M-Lot.md)) est solidement livrée.

> **Type** : vue plein écran de validation taxonomique (atteinte par clic « Valider Tadarida » depuis [M-Passage](M-Passage.md) après que le passage soit `Déposé` et qu'un CSV de résultats Tadarida ait été importé).
> **Persona principal** : [Marie](../Personas/Marie.md) (validation simple post-Tadarida) et [Samuel](../Personas/Samuel.md) (validation intensive sur grands volumes, mode regroupement multi-nuits).
> **Parcours couverts** : [P7 - Valider les résultats Tadarida](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), [P9 - Regrouper les nuits successives par point](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md), [P10 - Exporter une bibliothèque de sons de référence](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md).
> **Stories couvertes** : [E7.S1 - Importer CSV](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s1), [E7.S2 - Liste + détail](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s2), [E7.S3 - Spectrogramme + zoom](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3), [E7.S4 - Valider/corriger](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4), [E7.S5 - Filtres](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s5), [E7.S6 - Mode inventaire/activité](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s6), [E7.S7 - Exporter Vu.csv](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s7), [E8.S1 - Regroupement](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s1), [E8.S2 - Bibliothèque sons](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s2).

L'écran présente la **vue de validation** : liste des observations Tadarida à gauche (avec filtres et compteur de validation), panneau de détail à droite (info séquence + spectrogramme + lecteur audio + actions de validation/correction). C'est l'écran le plus riche techniquement de toute l'application — implémente notamment le **spectrogramme avec zoom** (E7.S3, ★★★★★), brique technique majeure.

## Wireframe principal - validation en cours sur une observation Tadarida

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-Vision-Tadarida - Validation taxonomique" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .topnav { fill: #34495e; }
    .navtxt-active { fill: #ffffff; font: 600 13px sans-serif; }
    .navtxt-inactive { fill: #bdc3c7; font: 400 13px sans-serif; }
    .breadcrumb { font: 13px sans-serif; fill: #4a90d9; }
    .breadcrumb-sep { font: 13px sans-serif; fill: #6a737d; }
    .breadcrumb-curr { font: 13px sans-serif; fill: #2c3e50; }

    .stretch-banner { fill: #fef9e7; stroke: #b9770e; stroke-width: 1.5; }
    .stretch-icon { font: 24px sans-serif; }
    .stretch-title { font: 700 14px sans-serif; fill: #5d4e00; }
    .stretch-txt { font: 12px sans-serif; fill: #5d4e00; }

    .pagetitle { font: 700 20px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }

    .mode-toggle-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .mode-toggle-active { fill: #4a90d9; }
    .mode-toggle-txt-active { fill: #ffffff; font: 600 11px sans-serif; }
    .mode-toggle-txt-inactive { fill: #2c3e50; font: 600 11px sans-serif; }

    .filter-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .filter-label { font: 11px sans-serif; fill: #6a737d; }
    .filter-chip { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .filter-chip-active { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .filter-chip-txt { font: 11px sans-serif; fill: #2c3e50; }
    .filter-chip-txt-active { font: 600 11px sans-serif; fill: #2563a3; }

    .progress-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .progress-fill { fill: #1e8449; }
    .progress-txt { font: 600 12px sans-serif; fill: #2c3e50; }

    .col-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 0.5; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-current { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .col-head { font: 600 10px sans-serif; fill: #2c3e50; }
    .cell { font: 11px sans-serif; fill: #2c3e50; }
    .cell-mono { font: 11px monospace; fill: #2c3e50; }
    .cell-sec { font: 11px sans-serif; fill: #6a737d; }

    .status-todo { font: 14px sans-serif; fill: #d0d7de; }
    .status-validated { font: 14px sans-serif; fill: #1e8449; }
    .status-corrected { font: 14px sans-serif; fill: #b9770e; }
    .status-ref { font: 14px sans-serif; fill: #f1c40f; }

    .prob-bar-bg { fill: #eef2f5; }
    .prob-bar-high { fill: #1e8449; }
    .prob-bar-mid { fill: #b9770e; }
    .prob-bar-low { fill: #a93226; }
    .prob-txt { font: 600 10px sans-serif; fill: #2c3e50; }

    .detail-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .section-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .section-sub { font: 11px sans-serif; fill: #6a737d; }

    .specto-bg { fill: #1c2833; stroke: #34495e; stroke-width: 1; }
    .specto-bar { stroke-width: 0; }
    .specto-axis { stroke: #6a737d; stroke-width: 1; }
    .specto-axis-txt { font: 9px monospace; fill: #bdc3c7; }
    .specto-cursor { stroke: #e74c3c; stroke-width: 1.5; }
    .specto-zoom-btn { fill: rgba(255,255,255,0.1); stroke: #6a737d; stroke-width: 1; }
    .specto-zoom-txt { fill: #ffffff; font: 600 11px sans-serif; }

    .player-bar { fill: #2c3e50; }
    .player-time { fill: #ffffff; font: 11px monospace; }
    .player-ctrl { fill: #ffffff; font: 14px sans-serif; }

    .taxon-info { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .taxon-code { font: 700 16px monospace; fill: #2c3e50; }
    .taxon-name { font: 600 13px sans-serif; fill: #2c3e50; }
    .taxon-meta { font: 11px sans-serif; fill: #6a737d; }
    .prob-large { font: 700 22px sans-serif; fill: #1e8449; }

    .btn-validate { fill: #1e8449; stroke: #0e5128; stroke-width: 1.5; }
    .btn-correct { fill: #b9770e; stroke: #7e5109; stroke-width: 1.5; }
    .btn-ref { fill: #f1c40f; stroke: #b7950b; stroke-width: 1.5; }
    .btn-skip { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }

    .comment-input { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .comment-text { font: 12px sans-serif; fill: #2c3e50; }
    .comment-placeholder { font: 12px sans-serif; fill: #bdc3c7; }

    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="880" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="10" width="1180" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">🦇 VigieChiro PR Companion</text>
  <text x="1140" y="31" class="titletxt" text-anchor="end">— ☐ ☓</text>

  <rect x="10" y="42" width="1180" height="40" class="topnav"/>
  <text x="40" y="67" class="navtxt-inactive">🏠 Mes sites</text>
  <text x="170" y="67" class="navtxt-inactive">📥 Importer une nuit</text>
  <text x="330" y="67" class="navtxt-inactive">📊 Vue tabulaire</text>
  <text x="470" y="67" class="navtxt-inactive">⚙ Paramètres</text>
  <text x="1140" y="67" class="navtxt-inactive" text-anchor="end">👤 Local</text>

  <!-- Bandeau "cible étirée" -->
  <rect x="10" y="90" width="1180" height="42" class="stretch-banner"/>
  <text x="32" y="118" class="stretch-icon">🎯</text>
  <text x="60" y="110" class="stretch-title">Cible étirée hors MVP strict</text>
  <text x="60" y="125" class="stretch-txt">Cet écran correspond aux épopées E7 (SHOULD) et E8 (COULD). À engager uniquement si la chaîne fil rouge est livrée et stable.</text>

  <!-- Breadcrumb + page header -->
  <text x="40" y="158" class="breadcrumb">‹ Mes sites</text>
  <text x="125" y="158" class="breadcrumb-sep">›</text>
  <text x="140" y="158" class="breadcrumb">Carré 640380</text>
  <text x="265" y="158" class="breadcrumb-sep">›</text>
  <text x="280" y="158" class="breadcrumb">Passage 1 / A1</text>
  <text x="425" y="158" class="breadcrumb-sep">›</text>
  <text x="440" y="158" class="breadcrumb-curr">Validation Tadarida</text>

  <text x="40" y="190" class="pagetitle">✅ Validation des résultats Tadarida — Pass 1 / A1 (22/04/2026)</text>

  <!-- Toggle mode inventaire / activité -->
  <text x="780" y="180" class="filter-label">MODE :</text>
  <rect x="828" y="170" width="220" height="24" rx="12" class="mode-toggle-bg"/>
  <rect x="830" y="172" width="108" height="20" rx="10" class="mode-toggle-active"/>
  <text x="884" y="187" class="mode-toggle-txt-active" text-anchor="middle">📋 Inventaire</text>
  <text x="998" y="187" class="mode-toggle-txt-inactive" text-anchor="middle">📊 Activité</text>

  <rect x="1058" y="170" width="100" height="24" rx="3" class="btn-secondary"/>
  <text x="1108" y="186" class="btn-txt-dark" text-anchor="middle" font-size="11">📤 Vu.csv ↓</text>

  <!-- Barre de filtres -->
  <rect x="40" y="210" width="1120" height="44" rx="4" class="filter-bar"/>
  <text x="55" y="237" class="filter-label">FILTRES :</text>

  <rect x="115" y="220" width="150" height="22" rx="11" class="filter-chip-active"/>
  <text x="190" y="236" class="filter-chip-txt-active" text-anchor="middle">🦇 Pippip · ▾</text>

  <rect x="275" y="220" width="170" height="22" rx="11" class="filter-chip"/>
  <text x="360" y="236" class="filter-chip-txt" text-anchor="middle">📁 Groupe taxo · ▾</text>

  <rect x="455" y="220" width="170" height="22" rx="11" class="filter-chip-active"/>
  <text x="540" y="236" class="filter-chip-txt-active" text-anchor="middle">📊 Probabilité ≥ 0,5 · ▾</text>

  <rect x="635" y="220" width="160" height="22" rx="11" class="filter-chip"/>
  <text x="715" y="236" class="filter-chip-txt" text-anchor="middle">🌙 Plage horaire · ▾</text>

  <rect x="805" y="220" width="170" height="22" rx="11" class="filter-chip-active"/>
  <text x="890" y="236" class="filter-chip-txt-active" text-anchor="middle">⚙ Statut : à voir · ▾</text>

  <text x="1090" y="236" class="filter-chip-txt-active" text-anchor="end">↺ Réinitialiser</text>

  <!-- Progression de validation -->
  <text x="40" y="277" class="progress-txt">Avancement : 47 observations validées sur 638 Pippip filtrées (7 %)</text>
  <rect x="40" y="282" width="780" height="10" rx="5" class="progress-bg"/>
  <rect x="40" y="282" width="55" height="10" rx="5" class="progress-fill"/>
  <text x="850" y="290" class="progress-txt" font-size="11">47 ✓ · 12 ✏ · 579 ☐</text>
  <text x="1095" y="290" class="progress-txt" font-size="11" text-anchor="end">8 séquences ⭐ référence</text>

  <!-- ============== Colonne gauche : liste observations ============== -->
  <rect x="40" y="305" width="600" height="540" rx="4" class="col-section"/>

  <!-- Header table -->
  <rect x="40" y="305" width="600" height="28" class="table-head"/>
  <text x="55" y="324" class="col-head">SÉQUENCE</text>
  <text x="180" y="324" class="col-head">TAXON ↕</text>
  <text x="280" y="324" class="col-head">PROB. ↓</text>
  <text x="380" y="324" class="col-head">FRÉQ.</text>
  <text x="450" y="324" class="col-head">HEURE</text>
  <text x="540" y="324" class="col-head" text-anchor="middle">STATUT</text>
  <text x="610" y="324" class="col-head" text-anchor="middle">⭐</text>

  <!-- Lignes 1-15 -->
  <rect x="40" y="333" width="600" height="26" class="table-row"/>
  <text x="55" y="350" class="cell-mono">…_212817_003.wav</text>
  <text x="180" y="350" class="cell">Pippip</text>
  <rect x="280" y="341" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="341" width="54" height="12" rx="2" class="prob-bar-high"/>
  <text x="343" y="350" class="prob-txt">0,98</text>
  <text x="380" y="350" class="cell">45 kHz</text>
  <text x="450" y="350" class="cell-mono">21:28</text>
  <text x="540" y="352" class="status-validated" text-anchor="middle">✓</text>
  <text x="610" y="352" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="359" width="600" height="26" class="table-row-alt"/>
  <text x="55" y="376" class="cell-mono">…_213455_001.wav</text>
  <text x="180" y="376" class="cell">Pippip</text>
  <rect x="280" y="367" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="367" width="51" height="12" rx="2" class="prob-bar-high"/>
  <text x="343" y="376" class="prob-txt">0,93</text>
  <text x="380" y="376" class="cell">42 kHz</text>
  <text x="450" y="376" class="cell-mono">21:34</text>
  <text x="540" y="378" class="status-validated" text-anchor="middle">✓</text>
  <text x="610" y="378" class="status-ref" text-anchor="middle">⭐</text>

  <!-- Ligne 3 : SÉQUENCE COURANTE (en surbrillance) -->
  <rect x="40" y="385" width="600" height="26" class="table-row-current"/>
  <text x="55" y="402" class="cell-mono" font-weight="700">…_220142_002.wav</text>
  <text x="180" y="402" class="cell" font-weight="700">Pippip</text>
  <rect x="280" y="393" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="393" width="48" height="12" rx="2" class="prob-bar-high"/>
  <text x="343" y="402" class="prob-txt" font-weight="700">0,87</text>
  <text x="380" y="402" class="cell" font-weight="700">38 kHz</text>
  <text x="450" y="402" class="cell-mono" font-weight="700">22:01</text>
  <text x="540" y="404" class="status-todo" text-anchor="middle">☐</text>
  <text x="610" y="404" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="411" width="600" height="26" class="table-row-alt"/>
  <text x="55" y="428" class="cell-mono">…_222831_001.wav</text>
  <text x="180" y="428" class="cell">Pippip</text>
  <rect x="280" y="419" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="419" width="48" height="12" rx="2" class="prob-bar-high"/>
  <text x="343" y="428" class="prob-txt">0,86</text>
  <text x="380" y="428" class="cell">44 kHz</text>
  <text x="450" y="428" class="cell-mono">22:28</text>
  <text x="540" y="430" class="status-corrected" text-anchor="middle">✏</text>
  <text x="610" y="430" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="437" width="600" height="26" class="table-row"/>
  <text x="55" y="454" class="cell-mono">…_230557_004.wav</text>
  <text x="180" y="454" class="cell">Pippip</text>
  <rect x="280" y="445" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="445" width="42" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="454" class="prob-txt">0,76</text>
  <text x="380" y="454" class="cell">46 kHz</text>
  <text x="450" y="454" class="cell-mono">23:05</text>
  <text x="540" y="456" class="status-todo" text-anchor="middle">☐</text>
  <text x="610" y="456" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="463" width="600" height="26" class="table-row-alt"/>
  <text x="55" y="480" class="cell-mono">…_233012_002.wav</text>
  <text x="180" y="480" class="cell">Pippip</text>
  <rect x="280" y="471" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="471" width="40" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="480" class="prob-txt">0,72</text>
  <text x="380" y="480" class="cell">41 kHz</text>
  <text x="450" y="480" class="cell-mono">23:30</text>
  <text x="540" y="482" class="status-todo" text-anchor="middle">☐</text>
  <text x="610" y="482" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="489" width="600" height="26" class="table-row"/>
  <text x="55" y="506" class="cell-mono">…_001847_003.wav</text>
  <text x="180" y="506" class="cell">Pippip</text>
  <rect x="280" y="497" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="497" width="35" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="506" class="prob-txt">0,64</text>
  <text x="380" y="506" class="cell">39 kHz</text>
  <text x="450" y="506" class="cell-mono">00:18</text>
  <text x="540" y="508" class="status-validated" text-anchor="middle">✓</text>
  <text x="610" y="508" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="515" width="600" height="26" class="table-row-alt"/>
  <text x="55" y="532" class="cell-mono">…_010533_001.wav</text>
  <text x="180" y="532" class="cell">Pippip</text>
  <rect x="280" y="523" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="523" width="32" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="532" class="prob-txt">0,58</text>
  <text x="380" y="532" class="cell">37 kHz</text>
  <text x="450" y="532" class="cell-mono">01:05</text>
  <text x="540" y="534" class="status-todo" text-anchor="middle">☐</text>
  <text x="610" y="534" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="541" width="600" height="26" class="table-row"/>
  <text x="55" y="558" class="cell-mono">…_023217_002.wav</text>
  <text x="180" y="558" class="cell">Pippip</text>
  <rect x="280" y="549" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="549" width="29" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="558" class="prob-txt">0,53</text>
  <text x="380" y="558" class="cell">43 kHz</text>
  <text x="450" y="558" class="cell-mono">02:32</text>
  <text x="540" y="560" class="status-validated" text-anchor="middle">✓</text>
  <text x="610" y="560" class="status-todo" text-anchor="middle">☐</text>

  <rect x="40" y="567" width="600" height="26" class="table-row-alt"/>
  <text x="55" y="584" class="cell-mono">…_034028_001.wav</text>
  <text x="180" y="584" class="cell">Pippip</text>
  <rect x="280" y="575" width="55" height="12" rx="2" class="prob-bar-bg"/>
  <rect x="280" y="575" width="28" height="12" rx="2" class="prob-bar-mid"/>
  <text x="343" y="584" class="prob-txt">0,51</text>
  <text x="380" y="584" class="cell">44 kHz</text>
  <text x="450" y="584" class="cell-mono">03:40</text>
  <text x="540" y="586" class="status-todo" text-anchor="middle">☐</text>
  <text x="610" y="586" class="status-todo" text-anchor="middle">☐</text>

  <text x="340" y="615" class="cell-sec" text-anchor="middle" font-style="italic">↓ 628 observations supplémentaires (faites défiler) ↓</text>

  <!-- Légende statuts -->
  <text x="55" y="660" class="filter-label">LÉGENDE :</text>
  <text x="125" y="660" class="status-todo" font-size="14">☐</text>
  <text x="140" y="660" class="cell">À voir</text>
  <text x="195" y="660" class="status-validated" font-size="14">✓</text>
  <text x="210" y="660" class="cell">Validée (R15)</text>
  <text x="295" y="660" class="status-corrected" font-size="14">✏</text>
  <text x="310" y="660" class="cell">Corrigée (R16)</text>
  <text x="395" y="660" class="status-ref" font-size="14">⭐</text>
  <text x="410" y="660" class="cell">Référence pour bibliothèque</text>

  <!-- Action regroupement multi-passages (E8.S1) -->
  <rect x="40" y="690" width="600" height="40" rx="4" class="filter-bar"/>
  <text x="60" y="715" class="cell">🔁 <tspan font-weight="600">Mode regroupement multi-nuits</tspan> : sélectionnez plusieurs passages d'un même point dans</text>
  <text x="60" y="730" class="cell">[M-MultiSite](M-MultiSite.md), puis « Regrouper pour validation » → toutes les obs fusionnées ici.</text>

  <text x="40" y="755" class="cell-sec" font-style="italic">⚠ Probabilités Tadarida non fiables au sens strict : 99 % peut être faux, 20 % peut être vrai. Heuristique de tri uniquement.</text>

  <!-- ============== Colonne droite : panneau de détail ============== -->
  <rect x="660" y="305" width="500" height="540" rx="4" class="detail-section"/>

  <!-- Info séquence -->
  <text x="680" y="330" class="section-title">📋 Observation sélectionnée</text>

  <rect x="680" y="345" width="460" height="60" rx="4" class="taxon-info"/>
  <text x="700" y="372" class="taxon-code">Pippip</text>
  <text x="780" y="372" class="taxon-name">Pipistrellus pipistrellus</text>
  <text x="780" y="390" class="taxon-meta">Pipistrelle commune · Genre Pipistrellus · Famille Vespertilionidae</text>
  <text x="1130" y="372" class="prob-large" text-anchor="end">87 %</text>
  <text x="1130" y="390" class="taxon-meta" text-anchor="end">probabilité Tadarida</text>

  <!-- Spectrogramme -->
  <text x="680" y="430" class="section-title">📊 Spectrogramme (zoom temps + fréquence)</text>
  <text x="1130" y="430" class="taxon-meta" text-anchor="end">38 kHz médian · 5,0 s</text>

  <!-- Spectrogramme simulé : barres verticales colorées par "amplitude" -->
  <rect x="680" y="445" width="460" height="180" class="specto-bg"/>
  <!-- Axe Y (fréquence) -->
  <text x="676" y="455" class="specto-axis-txt" text-anchor="end">120</text>
  <text x="676" y="495" class="specto-axis-txt" text-anchor="end">80</text>
  <text x="676" y="535" class="specto-axis-txt" text-anchor="end">40</text>
  <text x="676" y="575" class="specto-axis-txt" text-anchor="end">20</text>
  <text x="676" y="615" class="specto-axis-txt" text-anchor="end">8 kHz</text>
  <!-- Axe X (temps) -->
  <text x="685" y="635" class="specto-axis-txt">0</text>
  <text x="800" y="635" class="specto-axis-txt">1,5</text>
  <text x="910" y="635" class="specto-axis-txt">3,0</text>
  <text x="1020" y="635" class="specto-axis-txt">4,0</text>
  <text x="1130" y="635" class="specto-axis-txt" text-anchor="end">5,0 s</text>

  <!-- Spectrogramme représenté par bandes colorées (simulant énergie sur fréquences) -->
  <!-- Bande basse fréquence : faible -->
  <rect x="685" y="595" width="450" height="20" fill="#243a52"/>
  <!-- 30-50 kHz : amplitude principale (Pippip vers 45) -->
  <rect x="685" y="525" width="450" height="55" fill="#34495e"/>
  <!-- Pics d'énergie : barres verticales discrètes -->
  <rect x="700" y="510" width="3" height="55" fill="#f1c40f"/>
  <rect x="708" y="510" width="3" height="55" fill="#f39c12"/>
  <rect x="755" y="500" width="3" height="65" fill="#e67e22"/>
  <rect x="762" y="495" width="3" height="70" fill="#e74c3c"/>
  <rect x="770" y="500" width="3" height="65" fill="#e67e22"/>
  <rect x="820" y="510" width="3" height="55" fill="#f39c12"/>
  <rect x="850" y="498" width="3" height="68" fill="#e67e22"/>
  <rect x="858" y="495" width="3" height="70" fill="#e74c3c"/>
  <rect x="900" y="505" width="3" height="60" fill="#f39c12"/>
  <rect x="950" y="500" width="3" height="65" fill="#e67e22"/>
  <rect x="958" y="495" width="3" height="70" fill="#e74c3c"/>
  <rect x="1000" y="510" width="3" height="55" fill="#f39c12"/>
  <rect x="1050" y="498" width="3" height="68" fill="#e67e22"/>
  <rect x="1058" y="495" width="3" height="70" fill="#e74c3c"/>
  <rect x="1100" y="505" width="3" height="60" fill="#f39c12"/>

  <!-- Cursor de lecture (ligne rouge verticale) -->
  <line x1="855" y1="445" x2="855" y2="625" class="specto-cursor"/>

  <!-- Boutons zoom -->
  <rect x="1090" y="450" width="20" height="20" rx="3" class="specto-zoom-btn"/>
  <text x="1100" y="465" class="specto-zoom-txt" text-anchor="middle">+</text>
  <rect x="1115" y="450" width="20" height="20" rx="3" class="specto-zoom-btn"/>
  <text x="1125" y="465" class="specto-zoom-txt" text-anchor="middle">−</text>

  <!-- Player audio -->
  <rect x="680" y="640" width="460" height="36" rx="3" class="player-bar"/>
  <text x="695" y="662" class="player-ctrl">⏮ ⏯ ⏭</text>
  <text x="780" y="662" class="player-time">2,1 s / 5,0 s</text>
  <text x="1130" y="662" class="player-time" text-anchor="end">🔊 ━━━━━○──</text>

  <!-- Section validation : 3 boutons d'action -->
  <text x="680" y="703" class="section-title">📝 Votre décision</text>

  <rect x="680" y="713" width="145" height="44" rx="4" class="btn-validate"/>
  <text x="752" y="732" class="btn-txt" text-anchor="middle">✓ Valider</text>
  <text x="752" y="748" class="btn-txt" text-anchor="middle" font-size="11">(taxon = Pippip)</text>

  <rect x="835" y="713" width="145" height="44" rx="4" class="btn-correct"/>
  <text x="907" y="732" class="btn-txt" text-anchor="middle">✏ Corriger</text>
  <text x="907" y="748" class="btn-txt" text-anchor="middle" font-size="11">(autre taxon)</text>

  <rect x="990" y="713" width="145" height="44" rx="4" class="btn-ref"/>
  <text x="1062" y="732" class="btn-txt" text-anchor="middle">⭐ + Référence</text>
  <text x="1062" y="748" class="btn-txt" text-anchor="middle" font-size="11">(bibliothèque)</text>

  <!-- Commentaire -->
  <text x="680" y="780" class="section-sub">💬 Commentaire (optionnel)</text>
  <rect x="680" y="788" width="460" height="40" rx="3" class="comment-input"/>
  <text x="690" y="805" class="comment-placeholder">Note libre, ex. « pic 39 kHz, morphologie atypique »</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💡 Raccourcis : ↑/↓ navigation · Espace lecture/pause · V valider · C corriger · R référence · → suivante</text>
  <text x="1140" y="880" class="footer-txt" text-anchor="end">Mode inventaire · Pippip filtré</text>
</svg>
</div>

### Annotations

- **Bandeau jaune en haut** : signale clairement que c'est une cible étirée hors MVP, avec rappel que c'est à engager **après** que le fil rouge soit livré.
- **Toggle Mode inventaire / activité** ([E7.S6](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s6)) en haut à droite avec le bouton d'export Vu.csv ([E7.S7](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s7)).
- **Barre de filtres** ([E7.S5](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s5)) : 5 chips (Taxon, Groupe taxo, Probabilité, Plage horaire, Statut). Les chips actifs (Pippip, ≥0,5, à voir) sont en bleu.
- **Barre de progression** : 47 ✓ + 12 ✏ + 579 ☐ = 638 obs filtrées Pippip. Indicateur **8 séquences ⭐ référence** rappelle l'épopée E8 (bibliothèque sons).
- **Colonne gauche - Liste observations** ([E7.S2](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s2)) :
    - 7 colonnes : Séquence (chemin tronqué monospace), Taxon, Probabilité (barre colorée + chiffre), Fréquence dominante, Heure, Statut, ⭐ Référence
    - **Probabilité** colorée par seuil : vert (≥ 0,80), orange (0,5-0,80), rouge (< 0,5)
    - Ligne courante en surbrillance bleue
    - Légende des statuts en bas
    - Bandeau « Mode regroupement multi-nuits » ([E8.S1](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s1))
    - Avertissement explicite que les probabilités Tadarida ne sont pas fiables au sens strict
- **Colonne droite - Panneau de détail** :
    - Card info taxon (code, nom latin, vernaculaire, groupe taxo) + probabilité Tadarida en gros (87 %)
    - **Spectrogramme** ([E7.S3](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3), ★★★★★) : fond sombre avec axes fréquence (8-120 kHz) et temps (0-5 s). Représentation par barres verticales colorées (gradient jaune → rouge selon amplitude). Cursor rouge à la position de lecture. Boutons zoom + / − en haut à droite
    - Player audio compact (timecode, contrôles, volume)
    - **3 boutons d'action** colorés ([E7.S4](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4) + [E8.S2](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s2)) :
        - ✓ **Valider** (vert) : confirme le taxon Tadarida
        - ✏ **Corriger** (orange) : ouvre le sélecteur d'autre taxon
        - ⭐ **+ Référence** (jaune) : ajoute à la bibliothèque
    - Champ commentaire libre
- **Footer** : raccourcis clavier indispensables pour la productivité (V, C, R, → pour passer à la suivante).

### Interactions clés

| Élément | Action |
|---|---|
| Toggle **Inventaire / Activité** | Bascule le mode de validation ([E7.S6](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s6)) |
| Bouton **📤 Vu.csv ↓** | Exporte le fichier de résultats validés ([E7.S7](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s7)) |
| Chip de filtre | Ouvre le sélecteur multi-valeurs ; clic ✕ pour retirer |
| ↑ / ↓ ou clic ligne | Navigation entre observations |
| Espace | Lecture / pause de la séquence courante |
| **V** (clavier) ou clic ✓ | Valider l'observation (statut → Validée, R15) |
| **C** (clavier) ou clic ✏ | Ouvrir le sélecteur de taxon de correction (statut → Corrigée, R16) |
| **R** (clavier) ou clic ⭐ | Marquer comme référence pour la bibliothèque ([E8.S2](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s2)) |
| **→** (clavier) | Passer à l'observation suivante (sans valider) |
| Boutons zoom + / − sur spectrogramme | Zoom indépendant temps et fréquence ([E7.S3](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3)) |

---

## Variante - import du CSV Tadarida

Avant de pouvoir valider, l'utilisateur doit **importer le CSV de résultats** téléchargé depuis le portail Vigie-Chiro. Cette modale s'affiche au premier accès si aucun import n'a été fait sur ce passage.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 480" role="img" aria-label="Maquette M-Vision-Tadarida - Import CSV" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: rgba(44,62,80,0.55);">
  <style>
    .modal-bg { fill: rgba(44,62,80,0.5); }
    .modal-frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .modal-header { fill: #2c3e50; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .modal-close { fill: #ffffff; font: 600 18px sans-serif; }
    .step-num { fill: #4a90d9; }
    .step-num-txt { fill: #ffffff; font: 700 13px sans-serif; }
    .step-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .field-readonly { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .field-mono { font: 12px monospace; fill: #2c3e50; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .check-warn { font: 14px sans-serif; fill: #b9770e; }
    .insp-row { font: 13px sans-serif; fill: #2c3e50; }
    .info-banner { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .info-banner-title { font: 600 13px sans-serif; fill: #1e6f3f; }
    .info-banner-txt { font: 12px sans-serif; fill: #1e6f3f; }
    .step-sub-warn { font: 12px sans-serif; fill: #b9770e; }
  </style>

  <rect x="0" y="0" width="1000" height="480" class="modal-bg"/>
  <rect x="100" y="20" width="800" height="440" rx="6" class="modal-frame"/>

  <rect x="100" y="20" width="800" height="48" rx="6" class="modal-header"/>
  <rect x="100" y="50" width="800" height="18" class="modal-header"/>
  <text x="124" y="50" class="modal-header-txt">📥 Importer les résultats Tadarida (CSV)</text>
  <text x="876" y="52" class="modal-close" text-anchor="end">✕</text>

  <!-- Étape 1 -->
  <circle cx="142" cy="110" r="14" class="step-num"/>
  <text x="142" y="115" class="step-num-txt" text-anchor="middle">1</text>
  <text x="170" y="107" class="step-title">Téléchargez le CSV depuis Vigie-Chiro</text>
  <text x="170" y="124" class="step-sub">Connectez-vous sur vigiechiro.herokuapp.com et téléchargez le fichier de résultats du passage déposé.</text>

  <!-- Étape 2 -->
  <circle cx="142" cy="160" r="14" class="step-num"/>
  <text x="142" y="165" class="step-num-txt" text-anchor="middle">2</text>
  <text x="170" y="157" class="step-title">Sélectionnez le fichier sur votre disque</text>

  <rect x="170" y="175" width="500" height="30" rx="3" class="field-readonly"/>
  <text x="180" y="194" class="field-mono">/home/marie/Téléchargements/8a4fa9...-observations.csv</text>
  <rect x="685" y="175" width="160" height="30" rx="3" class="btn-secondary"/>
  <text x="765" y="195" class="btn-txt-dark" text-anchor="middle">📂 Parcourir...</text>

  <!-- Étape 3 : récap inspection -->
  <circle cx="142" cy="240" r="14" class="step-num"/>
  <text x="142" y="245" class="step-num-txt" text-anchor="middle">3</text>
  <text x="170" y="237" class="step-title">Inspection du CSV (lecture seule)</text>

  <rect x="170" y="255" width="690" height="115" rx="4" class="info-banner"/>

  <text x="190" y="280" class="check-ok">✓</text>
  <text x="210" y="280" class="info-banner-txt">Format détecté :</text>
  <text x="320" y="280" class="info-banner-title">Brut Tadarida</text>
  <text x="430" y="280" class="info-banner-txt">(11 colonnes, séparateur ;)</text>

  <text x="190" y="302" class="check-ok">✓</text>
  <text x="210" y="302" class="info-banner-txt">4 031 observations détectées sur 2 114 séquences uniques.</text>

  <text x="190" y="324" class="check-warn">⚠</text>
  <text x="210" y="324" class="step-sub-warn">12 observations orphelines (séquence introuvable dans le passage).</text>
  <text x="540" y="324" class="info-banner-txt" text-decoration="underline">Voir le détail</text>

  <text x="190" y="346" class="check-ok">✓</text>
  <text x="210" y="346" class="info-banner-txt">Volume : 4 031 lignes seront insérées (~0,5 s d'import estimé).</text>

  <!-- Boutons -->
  <rect x="660" y="400" width="100" height="36" rx="4" class="btn-secondary"/>
  <text x="710" y="423" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="770" y="400" width="100" height="36" rx="4" class="btn-primary"/>
  <text x="820" y="423" class="btn-txt" text-anchor="middle">📥 Importer</text>
</svg>
</div>

### Notes sur l'import CSV

- **3 étapes numérotées** : (1) téléchargez sur le portail web, (2) sélectionnez le fichier sur disque, (3) inspection automatique avec récap.
- **Inspection** ([E7.S1](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s1)) : détection automatique du format (Brut ou Vu, R17), comptage des observations et des séquences uniques, signalement des **observations orphelines** (séquence introuvable, ce qui peut arriver si le passage a été modifié après dépôt).
- **Volumétrie** : un import standard fait 4 000+ observations. La volumétrie cible est < 10 s d'import sans freezer l'IHM.
- **Si un import existe déjà** pour ce passage (non figuré ici), une alerte demande confirmation : remplacer (perd les validations en cours) ou annuler.

## Notes pour l'implémentation

- **Spectrogramme** ([E7.S3](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3)) : c'est la **brique la plus lourde techniquement** (★★★★★). Implémentation possible :
    - Calcul FFT sur chaque chunk de la séquence (fenêtre 1024 ou 2048, recouvrement 50 %)
    - Rendu sur `Canvas` JavaFX (plus performant que des `Rectangle` SVG-style)
    - Couleurs : gradient noir → bleu → jaune → rouge selon amplitude (échelle log)
    - Calcul en arrière-plan avec placeholder « Calcul du spectrogramme... » si > 200 ms
    - Bibliothèques utiles : `JTransforms` (FFT) ou implémentation maison
- **Zoom interactif** : molette souris → zoom temps. Maj+molette → zoom fréquence. Bouton « Reset » pour revenir à la vue complète.
- **TableView des observations** : avec virtualisation pour absorber 4 000+ lignes sans freezer.
- **Raccourcis clavier** (V/C/R/→) : à implémenter au niveau racine de la vue. Indispensables pour la productivité Samuel.
- **Sélecteur de taxon de correction** (modal sur clic Corriger) : autocomplete sur le code à 6 lettres ou le nom latin/vernaculaire, depuis le DAO Taxon ([E0.S5](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s5)).
- **Persistance par observation** : chaque clic Valider/Corriger/Référence persiste immédiatement en BD ([E0.S5](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s5) + [E0.S7](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s7) pour reprise de session).
