# M-Qualification - Vérifier l'enregistrement par échantillonnage

> **Type** : vue plein écran (atteinte par clic « Vérifier l'enregistrement » depuis [M-Passage](M-Passage.md)).
> **Persona principal** : tous. C'est l'étape de **sound check global** que chaque utilisateur fait avant de déposer une nuit.
> **Parcours couverts** : [P3 - Vérifier l'enregistrement par échantillonnage](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md).
> **Stories couvertes** : [E3.S1 - Générer la sélection](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s1), [E3.S2 - Liste chronologique](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s2), [E3.S3 - Lecteur audio](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s3), [E3.S4 - Marquer écouté + suivi](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s4), [E3.S5 - Verdict global](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s5), [E3.S6 - Personnaliser la sélection](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s6).

L'écran est divisé en **2 colonnes** : à gauche, la liste chronologique des séquences d'écoute échantillonnées (avec leur statut écouté/pas écouté) ; à droite, le panneau de lecture audio de la séquence sélectionnée + la zone de saisie du verdict global. L'utilisateur enchaîne typiquement : clic sur une séquence → écoute → coche éventuelle → suivante. Quand assez d'éléments ont été écoutés pour se faire une opinion, il saisit son verdict global.

## Wireframe principal - 12 séquences sur 30 déjà écoutées, lecture en cours

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-Qualification - Vérification d'enregistrement par échantillonnage" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .info-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value { font: 600 13px sans-serif; fill: #2c3e50; }
    .col-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .col-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .col-sub { font: 12px sans-serif; fill: #6a737d; }
    .progress-bg { fill: #eef2f5; }
    .progress-bar { fill: #1e8449; }
    .progress-txt { font: 600 12px sans-serif; fill: #2c3e50; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 0.5; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-current { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-mono { font: 12px monospace; fill: #2c3e50; }
    .cell-listened { font: 14px sans-serif; fill: #1e8449; }
    .cell-not-listened { font: 14px sans-serif; fill: #d0d7de; }
    .play-btn { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .play-btn-current { fill: #2563a3; stroke: #154360; stroke-width: 1; }
    .play-icon { fill: #ffffff; font: 700 12px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-secondary-small { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-txt-dark-small { fill: #2c3e50; font: 11px sans-serif; }
    .player-bg { fill: #2c3e50; }
    .player-title { font: 600 13px sans-serif; fill: #ffffff; }
    .player-mono { font: 11px monospace; fill: #bdc3c7; }
    .waveform-bg { fill: #1c2833; }
    .waveform-bar { fill: #4a90d9; }
    .waveform-bar-played { fill: #1e8449; }
    .waveform-cursor { stroke: #e74c3c; stroke-width: 1.5; }
    .player-time { font: 11px monospace; fill: #ffffff; }
    .player-ctrl-btn { fill: #ffffff; }
    .verdict-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .verdict-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .verdict-radio-bg { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .verdict-radio-selected-ok { fill: #d4edda; stroke: #1e8449; stroke-width: 2; }
    .verdict-radio-doubt { fill: #ffffff; stroke: #b9770e; stroke-width: 1; }
    .verdict-radio-jeter { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .verdict-label-ok { font: 600 13px sans-serif; fill: #1e6f3f; }
    .verdict-label-doubt { font: 600 13px sans-serif; fill: #7e5109; }
    .verdict-label-jeter { font: 600 13px sans-serif; fill: #a93226; }
    .verdict-radio-icon { font: 14px sans-serif; }
    .comment-input { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .comment-text { font: 12px sans-serif; fill: #2c3e50; }
    .comment-placeholder { font: 12px sans-serif; fill: #bdc3c7; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
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

  <text x="40" y="108" class="breadcrumb">‹ Mes sites</text>
  <text x="125" y="108" class="breadcrumb-sep">›</text>
  <text x="140" y="108" class="breadcrumb">Carré 640380</text>
  <text x="265" y="108" class="breadcrumb-sep">›</text>
  <text x="280" y="108" class="breadcrumb">Passage 2 / A1</text>
  <text x="425" y="108" class="breadcrumb-sep">›</text>
  <text x="440" y="108" class="breadcrumb-curr">Vérifier l'enregistrement</text>

  <text x="40" y="148" class="pagetitle">🎧 Vérifier l'enregistrement par échantillonnage</text>
  <text x="40" y="170" class="pagesub">Écoutez quelques séquences réparties sur la nuit pour vous assurer que l'enregistrement est exploitable.</text>

  <!-- Bandeau infos passage -->
  <rect x="40" y="195" width="1120" height="50" rx="4" class="info-bar"/>
  <text x="60" y="216" class="info-label">PASSAGE</text>
  <text x="60" y="234" class="info-value">Carré 640380 / A1 / N° 2 (2026)</text>
  <text x="320" y="216" class="info-label">DATE</text>
  <text x="320" y="234" class="info-value">22/06/2026 (20:25 → 07:47)</text>
  <text x="560" y="216" class="info-label">SÉQUENCES TOTALES</text>
  <text x="560" y="234" class="info-value">3 614 (durée audible 5h01)</text>
  <text x="800" y="216" class="info-label">VERDICT ACTUEL</text>
  <text x="800" y="234" class="info-value" fill="#6a737d" font-style="italic">non saisi</text>
  <text x="1010" y="216" class="info-label">STATUT</text>
  <text x="1010" y="234" class="info-value">Transformé</text>

  <!-- ============== Colonne gauche : liste des séquences ============== -->
  <rect x="40" y="265" width="710" height="585" rx="4" class="col-section"/>

  <!-- Header colonne -->
  <text x="60" y="295" class="col-title">📋 Sélection d'écoute (30 séquences)</text>
  <text x="60" y="312" class="col-sub">Méthode RéparTemporel · réparties uniformément sur la nuit</text>

  <!-- Bouton Personnaliser -->
  <rect x="600" y="282" width="140" height="28" rx="3" class="btn-secondary-small"/>
  <text x="670" y="299" class="btn-txt-dark-small" text-anchor="middle">⚙ Personnaliser...</text>

  <!-- Barre de progression d'écoute -->
  <rect x="60" y="325" width="670" height="14" rx="7" class="progress-bg"/>
  <rect x="60" y="325" width="268" height="14" rx="7" class="progress-bar"/>
  <text x="60" y="356" class="progress-txt">12 / 30 séquences écoutées (40 %)</text>
  <text x="730" y="356" class="col-sub" text-anchor="end">Aucun seuil obligatoire — vous restez libre du verdict (R13)</text>

  <!-- Tableau séquences -->
  <rect x="60" y="375" width="670" height="28" class="table-head"/>
  <text x="78" y="394" class="col-head">▶</text>
  <text x="110" y="394" class="col-head">N°</text>
  <text x="150" y="394" class="col-head">HORODATAGE</text>
  <text x="290" y="394" class="col-head">DURÉE</text>
  <text x="360" y="394" class="col-head">FRÉQ. DOM.</text>
  <text x="470" y="394" class="col-head">FICHIER SOURCE</text>
  <text x="690" y="394" class="col-head" text-anchor="middle">ÉCOUTÉ</text>

  <!-- Lignes 1-15 -->
  <rect x="60" y="403" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="416" r="9" class="play-btn"/>
  <text x="78" y="420" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="420" class="cell">01</text>
  <text x="150" y="420" class="cell-mono">20:27:12</text>
  <text x="290" y="420" class="cell">5,0 s</text>
  <text x="360" y="420" class="cell">38 kHz</text>
  <text x="470" y="420" class="cell-mono">…_202612.wav</text>
  <text x="690" y="420" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="429" width="670" height="26" class="table-row-alt"/>
  <circle cx="78" cy="442" r="9" class="play-btn"/>
  <text x="78" y="446" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="446" class="cell">02</text>
  <text x="150" y="446" class="cell-mono">20:48:33</text>
  <text x="290" y="446" class="cell">5,0 s</text>
  <text x="360" y="446" class="cell">42 kHz</text>
  <text x="470" y="446" class="cell-mono">…_204833.wav</text>
  <text x="690" y="446" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="455" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="468" r="9" class="play-btn"/>
  <text x="78" y="472" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="472" class="cell">03</text>
  <text x="150" y="472" class="cell-mono">21:11:48</text>
  <text x="290" y="472" class="cell">5,0 s</text>
  <text x="360" y="472" class="cell">45 kHz</text>
  <text x="470" y="472" class="cell-mono">…_211148.wav</text>
  <text x="690" y="472" class="cell-listened" text-anchor="middle">✓</text>

  <!-- Ligne 4 : currently playing (highlighted) -->
  <rect x="60" y="481" width="670" height="26" class="table-row-current"/>
  <circle cx="78" cy="494" r="9" class="play-btn-current"/>
  <text x="78" y="498" class="play-icon" text-anchor="middle">⏸</text>
  <text x="110" y="498" class="cell" font-weight="700">04</text>
  <text x="150" y="498" class="cell-mono" font-weight="700">21:34:55</text>
  <text x="290" y="498" class="cell" font-weight="700">5,0 s</text>
  <text x="360" y="498" class="cell" font-weight="700">38 kHz</text>
  <text x="470" y="498" class="cell-mono" font-weight="700">…_213455.wav</text>
  <text x="690" y="498" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="507" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="520" r="9" class="play-btn"/>
  <text x="78" y="524" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="524" class="cell">05</text>
  <text x="150" y="524" class="cell-mono">21:58:21</text>
  <text x="290" y="524" class="cell">5,0 s</text>
  <text x="360" y="524" class="cell">29 kHz</text>
  <text x="470" y="524" class="cell-mono">…_215821.wav</text>
  <text x="690" y="524" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="533" width="670" height="26" class="table-row-alt"/>
  <circle cx="78" cy="546" r="9" class="play-btn"/>
  <text x="78" y="550" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="550" class="cell">06</text>
  <text x="150" y="550" class="cell-mono">22:21:47</text>
  <text x="290" y="550" class="cell">5,0 s</text>
  <text x="360" y="550" class="cell">35 kHz</text>
  <text x="470" y="550" class="cell-mono">…_222147.wav</text>
  <text x="690" y="550" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="559" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="572" r="9" class="play-btn"/>
  <text x="78" y="576" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="576" class="cell">07</text>
  <text x="150" y="576" class="cell-mono">22:45:13</text>
  <text x="290" y="576" class="cell">5,0 s</text>
  <text x="360" y="576" class="cell">52 kHz</text>
  <text x="470" y="576" class="cell-mono">…_224513.wav</text>
  <text x="690" y="576" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="585" width="670" height="26" class="table-row-alt"/>
  <circle cx="78" cy="598" r="9" class="play-btn"/>
  <text x="78" y="602" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="602" class="cell">08</text>
  <text x="150" y="602" class="cell-mono">23:08:39</text>
  <text x="290" y="602" class="cell">5,0 s</text>
  <text x="360" y="602" class="cell">— (silence)</text>
  <text x="470" y="602" class="cell-mono">…_230839.wav</text>
  <text x="690" y="602" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="611" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="624" r="9" class="play-btn"/>
  <text x="78" y="628" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="628" class="cell">09</text>
  <text x="150" y="628" class="cell-mono">23:32:05</text>
  <text x="290" y="628" class="cell">5,0 s</text>
  <text x="360" y="628" class="cell">41 kHz</text>
  <text x="470" y="628" class="cell-mono">…_233205.wav</text>
  <text x="690" y="628" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="637" width="670" height="26" class="table-row-alt"/>
  <circle cx="78" cy="650" r="9" class="play-btn"/>
  <text x="78" y="654" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="654" class="cell">10</text>
  <text x="150" y="654" class="cell-mono">23:55:31</text>
  <text x="290" y="654" class="cell">5,0 s</text>
  <text x="360" y="654" class="cell">37 kHz</text>
  <text x="470" y="654" class="cell-mono">…_235531.wav</text>
  <text x="690" y="654" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="663" width="670" height="26" class="table-row"/>
  <circle cx="78" cy="676" r="9" class="play-btn"/>
  <text x="78" y="680" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="680" class="cell">11</text>
  <text x="150" y="680" class="cell-mono">00:18:57</text>
  <text x="290" y="680" class="cell">5,0 s</text>
  <text x="360" y="680" class="cell">44 kHz</text>
  <text x="470" y="680" class="cell-mono">…_001857.wav</text>
  <text x="690" y="680" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="689" width="670" height="26" class="table-row-alt"/>
  <circle cx="78" cy="702" r="9" class="play-btn"/>
  <text x="78" y="706" class="play-icon" text-anchor="middle">▶</text>
  <text x="110" y="706" class="cell">12</text>
  <text x="150" y="706" class="cell-mono">00:42:23</text>
  <text x="290" y="706" class="cell">5,0 s</text>
  <text x="360" y="706" class="cell">25 kHz</text>
  <text x="470" y="706" class="cell-mono">…_004223.wav</text>
  <text x="690" y="706" class="cell-listened" text-anchor="middle">✓</text>

  <!-- Indicateur "scroll" + nb restant -->
  <text x="395" y="745" class="col-sub" text-anchor="middle" font-style="italic">↓ 18 séquences supplémentaires (faites défiler) ↓</text>

  <!-- Boutons d'aide en bas de la liste -->
  <rect x="60" y="780" width="200" height="30" rx="3" class="btn-secondary-small"/>
  <text x="160" y="800" class="btn-txt-dark-small" text-anchor="middle">+ Ajouter une séquence...</text>
  <rect x="280" y="780" width="200" height="30" rx="3" class="btn-secondary-small"/>
  <text x="380" y="800" class="btn-txt-dark-small" text-anchor="middle">↺ Régénérer la sélection</text>

  <!-- ============== Colonne droite : player + verdict ============== -->

  <!-- Player audio -->
  <rect x="770" y="265" width="390" height="245" rx="4" class="player-bg"/>

  <text x="788" y="290" class="player-title">▶ Lecture séquence n° 04</text>
  <text x="788" y="308" class="player-mono">Car640380-2026-Pass2-A1-…_213455.wav</text>

  <!-- Waveform -->
  <rect x="788" y="325" width="354" height="80" rx="3" class="waveform-bg"/>
  <!-- Petites barres de waveform faussement réalistes -->
  <rect x="795" y="365" width="3" height="10" class="waveform-bar-played"/>
  <rect x="800" y="361" width="3" height="18" class="waveform-bar-played"/>
  <rect x="805" y="358" width="3" height="24" class="waveform-bar-played"/>
  <rect x="810" y="356" width="3" height="28" class="waveform-bar-played"/>
  <rect x="815" y="361" width="3" height="18" class="waveform-bar-played"/>
  <rect x="820" y="354" width="3" height="32" class="waveform-bar-played"/>
  <rect x="825" y="358" width="3" height="24" class="waveform-bar-played"/>
  <rect x="830" y="350" width="3" height="40" class="waveform-bar-played"/>
  <rect x="835" y="345" width="3" height="50" class="waveform-bar-played"/>
  <rect x="840" y="340" width="3" height="60" class="waveform-bar-played"/>
  <rect x="845" y="350" width="3" height="40" class="waveform-bar-played"/>
  <rect x="850" y="346" width="3" height="48" class="waveform-bar-played"/>
  <rect x="855" y="340" width="3" height="60" class="waveform-bar-played"/>
  <rect x="860" y="335" width="3" height="70" class="waveform-bar-played"/>
  <!-- Cursor de lecture -->
  <line x1="867" y1="325" x2="867" y2="405" class="waveform-cursor"/>
  <!-- Reste de la waveform (non lue, en bleu) -->
  <rect x="872" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="877" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="882" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="887" y="355" width="3" height="30" class="waveform-bar"/>
  <rect x="892" y="350" width="3" height="40" class="waveform-bar"/>
  <rect x="897" y="356" width="3" height="28" class="waveform-bar"/>
  <rect x="902" y="360" width="3" height="20" class="waveform-bar"/>
  <rect x="907" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="912" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="917" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="922" y="360" width="3" height="20" class="waveform-bar"/>
  <rect x="927" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="932" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="937" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="942" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="947" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="952" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="957" y="358" width="3" height="24" class="waveform-bar"/>
  <rect x="962" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="967" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="972" y="362" width="3" height="16" class="waveform-bar"/>
  <rect x="977" y="362" width="3" height="16" class="waveform-bar"/>

  <!-- Contrôles audio -->
  <text x="788" y="425" class="player-time">▶ 02,1 s / 5,0 s</text>
  <text x="1140" y="425" class="player-time" text-anchor="end">🔊 ━━━━━━━━━○──</text>

  <text x="788" y="455" class="player-title">⏮  ⏯  ⏭   ↺</text>

  <text x="788" y="485" class="player-mono">⚠ Lecture à vitesse normale (le fichier est déjà ralenti ×10 sur disque, R10)</text>

  <!-- Verdict section -->
  <rect x="770" y="525" width="390" height="270" rx="4" class="verdict-section"/>
  <text x="790" y="552" class="verdict-title">📝 Verdict global pour ce passage</text>
  <text x="790" y="570" class="col-sub">Sélectionnez votre conclusion d'ensemble.</text>

  <!-- Radio OK (sélectionné) -->
  <rect x="790" y="585" width="350" height="40" rx="4" class="verdict-radio-selected-ok"/>
  <text x="810" y="610" class="verdict-radio-icon">●</text>
  <text x="830" y="610" class="verdict-label-ok">✓ OK — la nuit est exploitable, dépôt possible</text>

  <!-- Radio Douteux -->
  <rect x="790" y="630" width="350" height="36" rx="4" class="verdict-radio-doubt"/>
  <text x="810" y="652" class="verdict-radio-icon">○</text>
  <text x="830" y="652" class="verdict-label-doubt">⚠ Douteux — j'ai eu des doutes, à signaler</text>

  <!-- Radio À jeter -->
  <rect x="790" y="671" width="350" height="36" rx="4" class="verdict-radio-jeter"/>
  <text x="810" y="693" class="verdict-radio-icon">○</text>
  <text x="830" y="693" class="verdict-label-jeter">❌ À jeter — la nuit est inexploitable, ne pas déposer</text>

  <!-- Commentaire -->
  <text x="790" y="730" class="verdict-title" font-size="13">💬 Commentaire (optionnel)</text>
  <rect x="790" y="738" width="350" height="48" rx="3" class="comment-input"/>
  <text x="800" y="757" class="comment-text">Vent fort vers 02:00, sons à vérifier en validation</text>
  <text x="800" y="773" class="comment-text">Tadarida ultérieurement.</text>

  <!-- Bouton Sauvegarder en bas -->
  <rect x="930" y="825" width="230" height="40" rx="4" class="btn-primary"/>
  <text x="1045" y="850" class="btn-txt" text-anchor="middle">💾 Enregistrer le verdict</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💡 Astuce : utilisez ↑ ↓ pour naviguer entre les séquences et Espace pour lecture/pause.</text>
  <text x="1140" y="880" class="footer-txt" text-anchor="end">12/30 écoutées · verdict : non saisi</text>
</svg>
</div>

### Annotations

- **Bandeau infos passage** : 5 cellules pour rappeler le contexte (passage, date, séquences totales, verdict actuel, statut). Le verdict actuel est en italique gris « non saisi » tant qu'on ne l'a pas validé.
- **Colonne gauche - Liste séquences** :
    - **Header** avec compteur (30 séquences) + méthode de constitution + bouton Personnaliser
    - **Barre de progression** : 12/30 écoutées (40 %) avec rappel R13 « aucun seuil obligatoire »
    - **Tableau** : 7 colonnes dont une `▶` pour le bouton de lecture et une `ÉCOUTÉ` (✓ vert / ○ gris)
    - **Ligne courante** (n° 04) en surbrillance bleue, le bouton ▶ devient ⏸
    - **Boutons en bas** : ajouter manuellement une séquence + régénérer la sélection
- **Colonne droite - Player audio** (panneau sombre) :
    - **Titre** + nom de fichier en monospace
    - **Waveform** : barres vertes pour la portion lue, bleues pour le reste, cursor rouge à la position courante
    - **Contrôles** : timecode, volume, boutons précédent/play-pause/suivant/replay
    - **Note R10** : rappel que le fichier est déjà ralenti ×10 sur disque
- **Verdict global** :
    - 3 boutons radio en cards colorées (vert OK / orange Douteux / rouge À jeter)
    - Le sélectionné (OK ici) est mis en évidence avec bordure et fond verts
    - Champ commentaire libre multi-ligne (placeholder ou contenu pré-rempli ici)
- **Bouton Enregistrer le verdict** : primary à droite, action principale.
- **Footer** : astuce raccourcis clavier + récap rapide en pied de page.

### Interactions clés

| Élément | Action |
|---|---|
| Clic sur ▶ d'une ligne | Joue la séquence dans le player. Marque automatiquement comme « écoutée » (E3.S4) |
| Clic ailleurs sur la ligne | Sélectionne la séquence (highlight) et charge dans le player sans démarrer la lecture |
| ↑ / ↓ (clavier) | Navigation entre séquences |
| Espace (clavier) | Lecture / pause de la séquence courante |
| Bouton **⚙ Personnaliser** | Ouvre la modale de personnalisation (cf. variante ci-dessous) |
| Bouton **+ Ajouter une séquence** | Ouvre un sélecteur pour pointer une séquence par horodatage |
| Bouton **↺ Régénérer la sélection** | Demande confirmation, puis régénère selon les paramètres courants |
| Sélection d'un verdict + clic **Enregistrer** | Persiste le verdict + le commentaire, le passage transitionne au statut `Vérifié` (E3.S5) |
| Verdict **À jeter** + Enregistrer | Avertissement supplémentaire : « Ce passage ne pourra pas être inclus dans un lot prêt à déposer (R14) » |

---

## Variante - modale de personnalisation de la sélection

Activée par le bouton **⚙ Personnaliser** dans le header de la liste. Permet de changer la méthode (RéparTemporel ou Aléatoire) et la taille (entre 10 et 100 séquences). Cette variante implémente [E3.S6](../Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s6) (SHOULD).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 480" role="img" aria-label="Maquette M-Qualification - Modale personnalisation" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: rgba(44,62,80,0.6);">
  <style>
    .modal-bg { fill: rgba(44,62,80,0.5); }
    .modal-frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .modal-header { fill: #2c3e50; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .modal-close { fill: #ffffff; font: 600 18px sans-serif; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .field-hint { font: 12px sans-serif; fill: #6a737d; }
    .radio-card-selected { fill: #cce4f7; stroke: #2563a3; stroke-width: 2; }
    .radio-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .radio-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .radio-sub { font: 12px sans-serif; fill: #6a737d; }
    .radio-icon { font: 14px sans-serif; }
    .slider-track { fill: #d0d7de; }
    .slider-track-fill { fill: #4a90d9; }
    .slider-handle { fill: #ffffff; stroke: #2563a3; stroke-width: 2; }
    .slider-label { font: 11px sans-serif; fill: #6a737d; }
    .slider-value { font: 700 18px sans-serif; fill: #4a90d9; }
    .preview-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .preview-txt { font: 13px sans-serif; fill: #5d4e00; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
  </style>

  <rect x="0" y="0" width="800" height="480" class="modal-bg"/>
  <rect x="100" y="40" width="600" height="400" rx="6" class="modal-frame"/>

  <rect x="100" y="40" width="600" height="48" rx="6" class="modal-header"/>
  <rect x="100" y="70" width="600" height="18" class="modal-header"/>
  <text x="124" y="70" class="modal-header-txt">⚙ Personnaliser la sélection d'écoute</text>
  <text x="676" y="72" class="modal-close" text-anchor="end">✕</text>

  <!-- Méthode -->
  <text x="124" y="120" class="field-label">Méthode de constitution</text>

  <!-- Card RéparTemporel (selected) -->
  <rect x="124" y="135" width="270" height="80" rx="4" class="radio-card-selected"/>
  <text x="142" y="158" class="radio-icon">●</text>
  <text x="162" y="158" class="radio-title">⏱ RéparTemporel</text>
  <text x="162" y="178" class="radio-sub">Séquences réparties uniformément</text>
  <text x="162" y="194" class="radio-sub">sur la durée de la nuit (par défaut, R12).</text>

  <!-- Card Aléatoire -->
  <rect x="406" y="135" width="270" height="80" rx="4" class="radio-card"/>
  <text x="424" y="158" class="radio-icon">○</text>
  <text x="444" y="158" class="radio-title">🎲 Aléatoire</text>
  <text x="444" y="178" class="radio-sub">Tirage aléatoire (avec seed pour</text>
  <text x="444" y="194" class="radio-sub">reproductibilité d'une session à l'autre).</text>

  <!-- Taille -->
  <text x="124" y="252" class="field-label">Taille de la sélection</text>
  <text x="124" y="269" class="field-hint">Entre 10 et 100 séquences. La taille actuelle est appropriée pour ~3 600 séquences totales.</text>

  <!-- Slider -->
  <rect x="124" y="290" width="450" height="6" rx="3" class="slider-track"/>
  <rect x="124" y="290" width="100" height="6" rx="3" class="slider-track-fill"/>
  <circle cx="224" cy="293" r="10" class="slider-handle"/>
  <text x="125" y="318" class="slider-label">10</text>
  <text x="350" y="318" class="slider-label" text-anchor="middle">55</text>
  <text x="573" y="318" class="slider-label" text-anchor="end">100</text>

  <text x="610" y="298" class="slider-value" text-anchor="end">30</text>

  <!-- Aperçu impact -->
  <rect x="124" y="335" width="552" height="50" rx="4" class="preview-box"/>
  <text x="144" y="356" class="preview-txt">⚠ Régénérer la sélection effacera votre progression d'écoute actuelle</text>
  <text x="144" y="374" class="preview-txt">(12/30 séquences écoutées) mais conservera votre verdict s'il est saisi.</text>

  <!-- Boutons -->
  <rect x="466" y="400" width="100" height="34" rx="4" class="btn-secondary"/>
  <text x="516" y="421" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="576" y="400" width="100" height="34" rx="4" class="btn-primary"/>
  <text x="626" y="421" class="btn-txt" text-anchor="middle">↺ Régénérer</text>
</svg>
</div>

### Notes sur la modale de personnalisation

- **Méthode** : 2 cards radio horizontales. RéparTemporel sélectionnée par défaut (cf. [R12](../Modèle%20conceptuel/Règles%20métier.md#r12)).
- **Taille** : slider 10-100, valeur courante affichée à droite (gros chiffre bleu).
- **Aperçu impact** (encart jaune) : avertissement explicite que la régénération efface la progression d'écoute mais conserve le verdict.
- **Boutons** : Annuler (secondary) / Régénérer (primary).

## Notes pour l'implémentation

- **TableView avec virtualisation** : la liste des séquences peut atteindre 100 lignes (taille max). JavaFX `TableView` gère nativement la virtualisation, à confirmer avec un test sur 100+ lignes.
- **Player audio** : `MediaPlayer` JavaFX avec `Media`. La waveform peut être une simplification visuelle (quelques dizaines de barres affichant l'amplitude RMS sur des fenêtres temporelles) plutôt qu'un vrai rendu PCM.
- **Synchronisation lecture ↔ progression** : le statut `écouté` doit être marqué dès le début de la lecture (pas à la fin), pour permettre à l'utilisateur de zapper rapidement.
- **Persistance** : la sélection, son état d'écoute, le verdict et le commentaire doivent tous être persistés en BD ([E0.S4](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s4) + [E0.S3](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s3)). Au retour sur l'écran, on doit retrouver tout son contexte.
- **Raccourcis clavier** : à implémenter via `setOnKeyPressed` au niveau de la racine de la vue. Indispensables pour la productivité (Samuel).
