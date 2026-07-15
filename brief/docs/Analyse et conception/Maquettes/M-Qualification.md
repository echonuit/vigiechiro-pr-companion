# M-Qualification - Vérifier l'enregistrement par échantillonnage

> **Type** : vue plein écran (atteinte par clic « Vérifier l'enregistrement » depuis [M-Passage](M-Passage.md)).
> **Persona principal** : tous. C'est l'étape de **sound check global** que chaque utilisateur fait avant de déposer une nuit.
> **Parcours couverts** : [P3 - Vérifier l'enregistrement par échantillonnage](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md).

L'écran est divisé en **2 colonnes** : à gauche, la liste chronologique des séquences d'écoute échantillonnées (avec leur statut écouté/pas écouté) ; à droite, le panneau de détail avec info de la séquence + visualisation audio + boutons de verdict global + commentaire. L'utilisateur enchaîne typiquement : clic sur une séquence → écoute → coche éventuelle → suivante. Quand assez d'éléments ont été écoutés pour se faire une opinion, il sélectionne son verdict global puis l'enregistre.

> **Patron d'écoute partagé avec [M-SonsValidation](M-SonsValidation.md)** : les deux écrans réutilisent le **même composant d'écoute** (`AudioView` : sonogramme + spectrogramme) sous un squelette « liste ↔ écoute » commun. La différence porte sur le **mode métier** : ici un **verdict** du passage (mode *Vérification* par échantillonnage), là un **verdict par observation** (mode *Validation* taxonomique).

> ⚠️ **Maquette en cours de refonte (chantier [#1524](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1524), lot 6).** Les visuels ci-dessous illustrent le modèle **historique** : un **verdict global unique** (`OK` / `Douteux` / `À jeter`). Le modèle **cible** retient un **verdict par fichier son** (`Bon` / `Mauvais` / `Inexploitable`) alimentant une **barre de progression tricolore**, puis un **verdict final du passage dérivé** et surchargeable (`OK` / `Utilisable` / `Inexploitable`, état initial `Non vérifié`) - cf. [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), [R13/R14](../Modèle%20conceptuel/Règles%20métier.md#r13) et le [glossaire](../Modèle%20conceptuel/Glossaire%20métier.md). Le redessin accompagnera l'implémentation de l'écran (lot 6).

## Maquette principale - 12 séquences sur 30 déjà écoutées, lecture en cours

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-Qualification - Vérification d'enregistrement par échantillonnage" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .breadcrumb { font: 13px sans-serif; fill: #c5cae9; }
    .breadcrumb-curr { font: 700 13px sans-serif; fill: #ffffff; }
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

    /* Panneau détail style M-SonsValidation (fond clair) */
    .detail-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .section-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .section-sub { font: 11px sans-serif; fill: #6a737d; }
    .seq-info-card { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .seq-num { font: 700 16px monospace; fill: #2c3e50; }
    .seq-time { font: 600 13px sans-serif; fill: #2c3e50; }
    .seq-meta { font: 11px sans-serif; fill: #6a737d; }
    .seq-stat-large { font: 700 22px sans-serif; fill: #4a90d9; }

    /* Waveform sur fond léger */
    .waveform-bg-light { fill: #eef2f5; stroke: #6a737d; stroke-width: 1; }
    .waveform-bar-played { fill: #1e8449; }
    .waveform-bar-pending { fill: #4a90d9; }
    .waveform-curseur { stroke: #e74c3c; stroke-width: 1.5; }
    .waveform-axis-txt { font: 9px monospace; fill: #6a737d; }
    /* Spectrogramme sur fond sombre (composant audio partagé avec M-SonsValidation) */
    .specto-bg { fill: #1c2833; stroke: #34495e; stroke-width: 1; }
    .specto-axis { stroke: #6a737d; stroke-width: 1; }
    .specto-axis-txt { font: 9px monospace; fill: #bdc3c7; }
    .specto-curseur { stroke: #e74c3c; stroke-width: 1.5; }
    .specto-zoom-btn { fill: rgba(255,255,255,0.1); stroke: #6a737d; stroke-width: 1; }
    .specto-zoom-txt { fill: #ffffff; font: 600 11px sans-serif; }

    /* Lecteur audio (barre sombre, comme M-SonsValidation) */
    .player-bar { fill: #2c3e50; }
    .player-time { fill: #ffffff; font: 11px monospace; }
    .player-ctrl { fill: #ffffff; font: 14px sans-serif; }

    /* 3 boutons verdict colorés (style M-SonsValidation) */
    .btn-verdict-ok { fill: #1e8449; stroke: #0e5128; stroke-width: 1.5; }
    .btn-verdict-ok-selected { fill: #0e5128; stroke: #0e5128; stroke-width: 3; }
    .btn-verdict-doubt { fill: #b9770e; stroke: #7e5109; stroke-width: 1.5; }
    .btn-verdict-jeter { fill: #a93226; stroke: #6e2c00; stroke-width: 1.5; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }

    .comment-input { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .comment-text { font: 12px sans-serif; fill: #2c3e50; }
    .comment-placeholder { font: 12px sans-serif; fill: #bdc3c7; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }

    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="880" rx="4" class="frame"/>
  <!-- Bandeau du chrome : titre + fil d'Ariane (emplacement complet du passage) + recherche -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro PR Companion</text>
  <text x="210" y="38" class="breadcrumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="642" y="38" class="breadcrumb-curr">Vérifier</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="148" class="pagetitle">🎧 Vérifier l'enregistrement par échantillonnage</text>
  <text x="40" y="170" class="pagesub">Écoutez quelques séquences réparties sur la nuit pour vous assurer que l'enregistrement est exploitable.</text>

  <!-- Bandeau infos passage -->
  <rect x="40" y="195" width="1120" height="50" rx="4" class="info-bar"/>
  <text x="60" y="216" class="info-label">PASSAGE</text>
  <text x="60" y="234" class="info-value">Carré 640380 / A1 / N° 2 (2026)</text>
  <text x="320" y="216" class="info-label">DATE</text>
  <text x="320" y="234" class="info-value">22/06/2026 (20:25 → 07:47)</text>
  <text x="560" y="216" class="info-label">SÉQUENCES TOTALES</text>
  <text x="560" y="234" class="info-value">3 614 (5 h 1 min enregistrées)</text>
  <text x="800" y="216" class="info-label">VERDICT ACTUEL</text>
  <text x="800" y="234" class="info-value" fill="#6a737d" font-style="italic">non saisi</text>
  <text x="1010" y="216" class="info-label">STATUT</text>
  <text x="1010" y="234" class="info-value">Transformé</text>

  <!-- ============== Colonne gauche : liste des séquences ============== -->
  <rect x="40" y="265" width="600" height="585" rx="4" class="col-section"/>

  <!-- Header colonne -->
  <text x="60" y="295" class="col-title">📋 Sélection d'écoute (30 séquences)</text>
  <text x="60" y="312" class="col-sub">Méthode RéparTemporel · réparties uniformément</text>

  <!-- Bouton Personnaliser -->
  <rect x="490" y="282" width="140" height="28" rx="3" class="btn-secondary-small"/>
  <text x="560" y="299" class="btn-txt-dark-small" text-anchor="middle">⚙ Personnaliser...</text>

  <!-- Barre de progression d'écoute -->
  <rect x="60" y="325" width="560" height="14" rx="7" class="progress-bg"/>
  <rect x="60" y="325" width="224" height="14" rx="7" class="progress-bar"/>
  <text x="60" y="356" class="progress-txt">12 / 30 écoutées (40 %)</text>
  <text x="620" y="356" class="col-sub" text-anchor="end">R13 : aucun seuil obligatoire</text>

  <!-- Tableau séquences -->
  <rect x="60" y="375" width="560" height="28" class="table-head"/>
  <text x="78" y="394" class="col-head">▶</text>
  <text x="108" y="394" class="col-head">N°</text>
  <text x="148" y="394" class="col-head">HORODATAGE</text>
  <text x="290" y="394" class="col-head">DURÉE</text>
  <text x="360" y="394" class="col-head">FRÉQ. DOM.</text>
  <text x="450" y="394" class="col-head">FICHIER</text>
  <text x="595" y="394" class="col-head" text-anchor="middle">ÉCOUTÉ</text>

  <!-- Lignes 1-12 (compact) -->
  <rect x="60" y="403" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="416" r="9" class="play-btn"/>
  <text x="78" y="420" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="420" class="cell">01</text>
  <text x="148" y="420" class="cell-mono">20:27:12</text>
  <text x="290" y="420" class="cell">5,0 s</text>
  <text x="360" y="420" class="cell">38 kHz</text>
  <text x="450" y="420" class="cell-mono">…_202612</text>
  <text x="595" y="420" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="429" width="560" height="26" class="table-row-alt"/>
  <circle cx="78" cy="442" r="9" class="play-btn"/>
  <text x="78" y="446" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="446" class="cell">02</text>
  <text x="148" y="446" class="cell-mono">20:48:33</text>
  <text x="290" y="446" class="cell">5,0 s</text>
  <text x="360" y="446" class="cell">42 kHz</text>
  <text x="450" y="446" class="cell-mono">…_204833</text>
  <text x="595" y="446" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="455" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="468" r="9" class="play-btn"/>
  <text x="78" y="472" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="472" class="cell">03</text>
  <text x="148" y="472" class="cell-mono">21:11:48</text>
  <text x="290" y="472" class="cell">5,0 s</text>
  <text x="360" y="472" class="cell">45 kHz</text>
  <text x="450" y="472" class="cell-mono">…_211148</text>
  <text x="595" y="472" class="cell-listened" text-anchor="middle">✓</text>

  <!-- Ligne 4 : currently playing (highlighted) -->
  <rect x="60" y="481" width="560" height="26" class="table-row-current"/>
  <circle cx="78" cy="494" r="9" class="play-btn-current"/>
  <text x="78" y="498" class="play-icon" text-anchor="middle">⏸</text>
  <text x="108" y="498" class="cell" font-weight="700">04</text>
  <text x="148" y="498" class="cell-mono" font-weight="700">21:34:55</text>
  <text x="290" y="498" class="cell" font-weight="700">5,0 s</text>
  <text x="360" y="498" class="cell" font-weight="700">38 kHz</text>
  <text x="450" y="498" class="cell-mono" font-weight="700">…_213455</text>
  <text x="595" y="498" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="507" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="520" r="9" class="play-btn"/>
  <text x="78" y="524" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="524" class="cell">05</text>
  <text x="148" y="524" class="cell-mono">21:58:21</text>
  <text x="290" y="524" class="cell">5,0 s</text>
  <text x="360" y="524" class="cell">29 kHz</text>
  <text x="450" y="524" class="cell-mono">…_215821</text>
  <text x="595" y="524" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="533" width="560" height="26" class="table-row-alt"/>
  <circle cx="78" cy="546" r="9" class="play-btn"/>
  <text x="78" y="550" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="550" class="cell">06</text>
  <text x="148" y="550" class="cell-mono">22:21:47</text>
  <text x="290" y="550" class="cell">5,0 s</text>
  <text x="360" y="550" class="cell">35 kHz</text>
  <text x="450" y="550" class="cell-mono">…_222147</text>
  <text x="595" y="550" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="559" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="572" r="9" class="play-btn"/>
  <text x="78" y="576" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="576" class="cell">07</text>
  <text x="148" y="576" class="cell-mono">22:45:13</text>
  <text x="290" y="576" class="cell">5,0 s</text>
  <text x="360" y="576" class="cell">52 kHz</text>
  <text x="450" y="576" class="cell-mono">…_224513</text>
  <text x="595" y="576" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="585" width="560" height="26" class="table-row-alt"/>
  <circle cx="78" cy="598" r="9" class="play-btn"/>
  <text x="78" y="602" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="602" class="cell">08</text>
  <text x="148" y="602" class="cell-mono">23:08:39</text>
  <text x="290" y="602" class="cell">5,0 s</text>
  <text x="360" y="602" class="cell">- (silence)</text>
  <text x="450" y="602" class="cell-mono">…_230839</text>
  <text x="595" y="602" class="cell-not-listened" text-anchor="middle">○</text>

  <rect x="60" y="611" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="624" r="9" class="play-btn"/>
  <text x="78" y="628" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="628" class="cell">09</text>
  <text x="148" y="628" class="cell-mono">23:32:05</text>
  <text x="290" y="628" class="cell">5,0 s</text>
  <text x="360" y="628" class="cell">41 kHz</text>
  <text x="450" y="628" class="cell-mono">…_233205</text>
  <text x="595" y="628" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="637" width="560" height="26" class="table-row-alt"/>
  <circle cx="78" cy="650" r="9" class="play-btn"/>
  <text x="78" y="654" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="654" class="cell">10</text>
  <text x="148" y="654" class="cell-mono">23:55:31</text>
  <text x="290" y="654" class="cell">5,0 s</text>
  <text x="360" y="654" class="cell">37 kHz</text>
  <text x="450" y="654" class="cell-mono">…_235531</text>
  <text x="595" y="654" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="663" width="560" height="26" class="table-row"/>
  <circle cx="78" cy="676" r="9" class="play-btn"/>
  <text x="78" y="680" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="680" class="cell">11</text>
  <text x="148" y="680" class="cell-mono">00:18:57</text>
  <text x="290" y="680" class="cell">5,0 s</text>
  <text x="360" y="680" class="cell">44 kHz</text>
  <text x="450" y="680" class="cell-mono">…_001857</text>
  <text x="595" y="680" class="cell-listened" text-anchor="middle">✓</text>

  <rect x="60" y="689" width="560" height="26" class="table-row-alt"/>
  <circle cx="78" cy="702" r="9" class="play-btn"/>
  <text x="78" y="706" class="play-icon" text-anchor="middle">▶</text>
  <text x="108" y="706" class="cell">12</text>
  <text x="148" y="706" class="cell-mono">00:42:23</text>
  <text x="290" y="706" class="cell">5,0 s</text>
  <text x="360" y="706" class="cell">25 kHz</text>
  <text x="450" y="706" class="cell-mono">…_004223</text>
  <text x="595" y="706" class="cell-listened" text-anchor="middle">✓</text>

  <!-- Indicateur "scroll" -->
  <text x="340" y="745" class="col-sub" text-anchor="middle" font-style="italic">↓ 18 séquences supplémentaires ↓</text>

  <!-- Boutons d'aide en bas de la liste -->
  <rect x="60" y="780" width="200" height="30" rx="3" class="btn-secondary-small"/>
  <text x="160" y="800" class="btn-txt-dark-small" text-anchor="middle">+ Ajouter une séquence...</text>
  <rect x="280" y="780" width="200" height="30" rx="3" class="btn-secondary-small"/>
  <text x="380" y="800" class="btn-txt-dark-small" text-anchor="middle">↺ Régénérer la sélection</text>

  <!-- ============== Colonne droite : panneau de détail (fond clair) ============== -->
  <rect x="660" y="265" width="500" height="585" rx="4" class="detail-section"/>

  <!-- Section 1 : info séquence en haut -->
  <text x="680" y="290" class="section-title">📋 Séquence sélectionnée</text>

  <rect x="680" y="305" width="460" height="60" rx="4" class="seq-info-card"/>
  <text x="700" y="332" class="seq-num">N° 04</text>
  <text x="780" y="332" class="seq-time">22/06 - 21:34:55</text>
  <text x="780" y="350" class="seq-meta">Durée 5,0 s · Fréquence dominante 38 kHz</text>
  <text x="1130" y="332" class="seq-stat-large" text-anchor="end">04 / 30</text>
  <text x="1130" y="350" class="seq-meta" text-anchor="end">⚪ non écoutée</text>

  <!-- Section 2 : VUE AUDIO partagée avec M-SonsValidation (sonogramme + spectrogramme) -->
  <text x="680" y="395" class="section-title">🎵 Vue audio (sonogramme + spectrogramme)</text>
  <text x="1130" y="395" class="seq-meta" text-anchor="end">Lecture vitesse normale · R10</text>

  <!-- Sonogramme (waveform amplitude/temps) sur fond clair -->
  <rect x="680" y="405" width="460" height="60" rx="4" class="waveform-bg-light"/>
  <text x="676" y="412" class="waveform-axis-txt" text-anchor="end">+1</text>
  <text x="676" y="438" class="waveform-axis-txt" text-anchor="end">0</text>
  <text x="676" y="462" class="waveform-axis-txt" text-anchor="end">-1</text>
  <!-- Ligne médiane -->
  <line x1="685" y1="435" x2="1135" y2="435" stroke="#6a737d" stroke-width="0.5" stroke-dasharray="2 2"/>
  <!-- Portion lue (vert) -->
  <rect x="700" y="430" width="2" height="10" class="waveform-bar-played"/>
  <rect x="705" y="427" width="2" height="16" class="waveform-bar-played"/>
  <rect x="712" y="422" width="2" height="26" class="waveform-bar-played"/>
  <rect x="720" y="425" width="2" height="20" class="waveform-bar-played"/>
  <rect x="728" y="418" width="2" height="34" class="waveform-bar-played"/>
  <rect x="735" y="415" width="2" height="40" class="waveform-bar-played"/>
  <rect x="745" y="410" width="2" height="50" class="waveform-bar-played"/>
  <rect x="755" y="408" width="2" height="54" class="waveform-bar-played"/>
  <rect x="765" y="412" width="2" height="46" class="waveform-bar-played"/>
  <rect x="780" y="418" width="2" height="34" class="waveform-bar-played"/>
  <rect x="795" y="412" width="2" height="46" class="waveform-bar-played"/>
  <rect x="810" y="408" width="2" height="54" class="waveform-bar-played"/>
  <rect x="828" y="415" width="2" height="40" class="waveform-bar-played"/>
  <!-- Portion à jouer (bleu) -->
  <rect x="875" y="422" width="2" height="26" class="waveform-bar-pending"/>
  <rect x="885" y="425" width="2" height="20" class="waveform-bar-pending"/>
  <rect x="895" y="420" width="2" height="30" class="waveform-bar-pending"/>
  <rect x="910" y="425" width="2" height="20" class="waveform-bar-pending"/>
  <rect x="925" y="427" width="2" height="16" class="waveform-bar-pending"/>
  <rect x="945" y="430" width="2" height="10" class="waveform-bar-pending"/>
  <rect x="960" y="425" width="2" height="20" class="waveform-bar-pending"/>
  <rect x="980" y="427" width="2" height="16" class="waveform-bar-pending"/>
  <rect x="998" y="430" width="2" height="10" class="waveform-bar-pending"/>
  <rect x="1015" y="427" width="2" height="16" class="waveform-bar-pending"/>
  <rect x="1035" y="430" width="2" height="10" class="waveform-bar-pending"/>
  <rect x="1055" y="431" width="2" height="8" class="waveform-bar-pending"/>
  <rect x="1075" y="430" width="2" height="10" class="waveform-bar-pending"/>
  <rect x="1095" y="431" width="2" height="8" class="waveform-bar-pending"/>
  <rect x="1115" y="432" width="2" height="6" class="waveform-bar-pending"/>
  <!-- Cursor sonogramme -->
  <line x1="868" y1="405" x2="868" y2="465" class="waveform-curseur"/>

  <!-- Spectrogramme (fréquence/temps) sur fond sombre -->
  <rect x="680" y="475" width="460" height="130" class="specto-bg"/>
  <!-- Axe Y (fréquence) -->
  <text x="676" y="485" class="specto-axis-txt" text-anchor="end">120</text>
  <text x="676" y="515" class="specto-axis-txt" text-anchor="end">80</text>
  <text x="676" y="550" class="specto-axis-txt" text-anchor="end">40</text>
  <text x="676" y="580" class="specto-axis-txt" text-anchor="end">20</text>
  <text x="676" y="605" class="specto-axis-txt" text-anchor="end">8 kHz</text>
  <!-- Axe X (temps) partagé avec sonogramme -->
  <text x="685" y="620" class="specto-axis-txt">0</text>
  <text x="795" y="620" class="specto-axis-txt">1,5</text>
  <text x="905" y="620" class="specto-axis-txt">3,0</text>
  <text x="1015" y="620" class="specto-axis-txt">4,0</text>
  <text x="1130" y="620" class="specto-axis-txt" text-anchor="end">5,0 s</text>
  <!-- Spectrogramme : bandes colorées simulant énergie sur fréquences -->
  <rect x="685" y="585" width="450" height="13" fill="#243a52"/>
  <rect x="685" y="535" width="450" height="40" fill="#34495e"/>
  <!-- Pics d'énergie à 38-45 kHz -->
  <rect x="700" y="525" width="3" height="40" fill="#f1c40f"/>
  <rect x="708" y="525" width="3" height="40" fill="#f39c12"/>
  <rect x="755" y="518" width="3" height="48" fill="#e67e22"/>
  <rect x="762" y="515" width="3" height="50" fill="#e74c3c"/>
  <rect x="770" y="518" width="3" height="48" fill="#e67e22"/>
  <rect x="820" y="525" width="3" height="40" fill="#f39c12"/>
  <rect x="850" y="518" width="3" height="48" fill="#e67e22"/>
  <rect x="858" y="515" width="3" height="50" fill="#e74c3c"/>
  <rect x="900" y="522" width="3" height="44" fill="#f39c12"/>
  <rect x="950" y="518" width="3" height="48" fill="#e67e22"/>
  <rect x="958" y="515" width="3" height="50" fill="#e74c3c"/>
  <rect x="1000" y="525" width="3" height="40" fill="#f39c12"/>
  <rect x="1050" y="518" width="3" height="48" fill="#e67e22"/>
  <rect x="1058" y="515" width="3" height="50" fill="#e74c3c"/>
  <rect x="1100" y="522" width="3" height="44" fill="#f39c12"/>
  <!-- Cursor spectrogramme (synchronisé avec sonogramme) -->
  <line x1="868" y1="475" x2="868" y2="605" class="specto-curseur"/>
  <!-- Boutons zoom -->
  <rect x="1090" y="480" width="20" height="20" rx="3" class="specto-zoom-btn"/>
  <text x="1100" y="495" class="specto-zoom-txt" text-anchor="middle">+</text>
  <rect x="1115" y="480" width="20" height="20" rx="3" class="specto-zoom-btn"/>
  <text x="1125" y="495" class="specto-zoom-txt" text-anchor="middle">−</text>

  <!-- Section 3 : lecteur audio -->
  <rect x="680" y="615" width="460" height="36" rx="3" class="player-bar"/>
  <text x="695" y="637" class="player-ctrl">⏮ ⏯ ⏭</text>
  <text x="780" y="637" class="player-time">2,1 s / 5,0 s</text>
  <text x="1130" y="637" class="player-time" text-anchor="end">🔊 ━━━━━○──</text>

  <!-- Section 4 : verdict global avec 3 boutons d'action (compact) -->
  <text x="680" y="678" class="section-title">📝 Verdict global du passage</text>

  <rect x="680" y="685" width="145" height="44" rx="4" class="btn-verdict-ok-selected"/>
  <text x="752" y="704" class="btn-txt" text-anchor="middle">✓ OK</text>
  <text x="752" y="720" class="btn-txt" text-anchor="middle" font-size="10">la nuit est exploitable</text>

  <rect x="835" y="685" width="145" height="44" rx="4" class="btn-verdict-doubt"/>
  <text x="907" y="704" class="btn-txt" text-anchor="middle">⚠ Douteux</text>
  <text x="907" y="720" class="btn-txt" text-anchor="middle" font-size="10">à signaler</text>

  <rect x="990" y="685" width="145" height="44" rx="4" class="btn-verdict-jeter"/>
  <text x="1062" y="704" class="btn-txt" text-anchor="middle">❌ À jeter</text>
  <text x="1062" y="720" class="btn-txt" text-anchor="middle" font-size="10">bloque dépôt (R14)</text>

  <!-- Section 5 : commentaire -->
  <text x="680" y="752" class="section-sub">💬 Commentaire (optionnel)</text>
  <rect x="680" y="760" width="460" height="38" rx="3" class="comment-input"/>
  <text x="690" y="778" class="comment-text">Vent fort vers 02:00, sons à vérifier en validation</text>
  <text x="690" y="792" class="comment-text">Tadarida ultérieurement.</text>

  <!-- Bouton Enregistrer -->
  <rect x="930" y="810" width="210" height="34" rx="4" class="btn-primary"/>
  <text x="1035" y="831" class="btn-txt" text-anchor="middle">💾 Enregistrer le verdict</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💡 Raccourcis : ↑/↓ navigation · Espace lecture/pause · O verdict OK · D Douteux · J À jeter · ⏎ Enregistrer</text>
  <text x="1140" y="880" class="footer-txt" text-anchor="end">12/30 écoutées · verdict OK sélectionné (non enregistré)</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome** (barre de navigation commune) via le contrat `EmplacementNavigation` ; l'écran ne dessine plus son propre fil ni de lien « retour au passage ». Emplacement affiché : `🏠 Accueil › Mes sites › Carré N › Détails du passage N° X › Vérifier l'enregistrement`, identique quelle que soit la route (depuis M-Sites comme depuis M-Multisite).
- **Bandeau infos passage** : 5 cellules de rappel (passage, date, séquences totales, verdict actuel, statut).
- **Colonne gauche - Liste séquences** (identique au design précédent) :
    - En-tête avec compteur + bouton « ⚙ Personnaliser »
    - Barre de progression verte (12/30, 40 %) + rappel R13
    - Tableau 7 colonnes (▶, N°, horodatage, durée, fréquence, fichier, écouté ✓/○)
    - Ligne courante (n° 04) en surbrillance bleue
    - Boutons en bas : ajouter manuellement / régénérer
- **Colonne droite - Panneau de détail** (style harmonisé avec [M-SonsValidation](M-SonsValidation.md)) :
    - **Section 1 : info séquence** (card claire `.seq-info-card`) - N° de séquence + horodatage + métadonnées + position dans la sélection (04/30), en tête du panneau d'écoute.
    - **Section 2 : vue audio combinée** (sonogramme + spectrogramme) - **Composant partagé avec [M-SonsValidation](M-SonsValidation.md)**, fourni par l'équipe pédagogique. Le sonogramme (amplitude/temps, fond clair) et le spectrogramme (fréquence/temps, fond sombre) sont **synchronisés** par un curseur rouge vertical unique. Boutons de zoom temps/fréquence accessibles sur le spectrogramme. L'utilisateur peut faire de l'écoute simple (sonogramme) ou analyser plus en détail (spectrogramme) selon ses besoins.
    - **Section 3 : lecteur audio** (barre sombre comme M-SonsValidation) - Contrôles ⏮ ⏯ ⏭, minutage, volume.
    - **Section 4 : verdict global** - 3 boutons colorés grand format (`✓ OK` vert / `⚠ Douteux` orange / `❌ À jeter` rouge). Le bouton sélectionné a une bordure plus épaisse (le `OK` ici est sélectionné). **Sélection différée** : ne persiste pas, attend le bouton « Enregistrer le verdict ».
    - **Section 5 : commentaire** - Champ texte multi-ligne optionnel.
    - **Bouton « 💾 Enregistrer le verdict »** primary à droite - Action finale qui persiste le verdict + commentaire et passe le passage au statut `Vérifié`.
- **Pied de page** : raccourcis clavier harmonisés avec M-SonsValidation (↑/↓/Espace/O/D/J/⏎).

### Différences sémantiques avec [M-SonsValidation](M-SonsValidation.md)

| Aspect | M-Qualification (cet écran) | M-SonsValidation |
|---|---|---|
| Objet de la revue | Séquences d'écoute échantillonnées | Observations Tadarida (avec taxon) |
| Visualisation audio | **Sonogramme + spectrogramme (composant partagé)** | **Sonogramme + spectrogramme (composant partagé)** |
| Décision | **Un verdict GLOBAL** pour toute la nuit | Une décision **par observation** |
| Persistance | Différée (bouton « Enregistrer ») | Instantanée (par clic Valider/Corriger) |
| Boutons d'action | OK / Douteux / À jeter | Valider / Corriger / Référence |
| Statut résultant | Le passage passe au statut `Vérifié` | L'observation passe à `Validée` / `Corrigée` |

### Interactions clés

| Élément | Action |
|---|---|
| Clic sur ▶ d'une ligne | Joue la séquence dans le lecteur. Marque automatiquement comme « écoutée » |
| Clic ailleurs sur la ligne | Sélectionne la séquence et charge dans le lecteur sans démarrer |
| ↑ / ↓ (clavier) | Navigation entre séquences |
| Espace (clavier) | Lecture / pause de la séquence courante |
| **O** (clavier) ou clic vert | Sélectionne le verdict OK |
| **D** (clavier) ou clic orange | Sélectionne le verdict Douteux |
| **J** (clavier) ou clic rouge | Sélectionne le verdict À jeter |
| **⏎** (Entrée) ou clic Enregistrer | Persiste le verdict + commentaire, passe le passage au statut `Vérifié` |
| Bouton **⚙ Personnaliser** | Ouvre la modale de personnalisation (cf. variante ci-dessous) |
| Verdict **À jeter** + Enregistrer | Avertissement supplémentaire : « Ce passage ne pourra pas être inclus dans un lot prêt à déposer (R14) » |

---

## Variante - modale de personnalisation de la sélection

Activée par le bouton **⚙ Personnaliser** dans l'en-tête de la liste. Permet de changer la méthode (RéparTemporel ou Aléatoire) et la taille (entre 10 et 100 séquences).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 480" role="img" aria-label="Maquette M-Qualification - Modale personnalisation" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #6b7785;">
  <style>
    .modal-bg { fill: #6b7785; }
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

  <rect x="124" y="135" width="270" height="80" rx="4" class="radio-card-selected"/>
  <text x="142" y="158" class="radio-icon">●</text>
  <text x="162" y="158" class="radio-title">⏱ RéparTemporel</text>
  <text x="162" y="178" class="radio-sub">Séquences réparties uniformément</text>
  <text x="162" y="194" class="radio-sub">sur la durée de la nuit (par défaut, R12).</text>

  <rect x="406" y="135" width="270" height="80" rx="4" class="radio-card"/>
  <text x="424" y="158" class="radio-icon">○</text>
  <text x="444" y="158" class="radio-title">🎲 Aléatoire</text>
  <text x="444" y="178" class="radio-sub">Tirage aléatoire (avec graine pour</text>
  <text x="444" y="194" class="radio-sub">reproductibilité d'une session à l'autre).</text>

  <text x="124" y="252" class="field-label">Taille de la sélection</text>
  <text x="124" y="269" class="field-hint">Entre 10 et 30 séquences, réparties sur les ~3 600 séquences de la nuit.</text>

  <rect x="124" y="290" width="450" height="6" rx="3" class="slider-track"/>
  <rect x="124" y="290" width="450" height="6" rx="3" class="slider-track-fill"/>
  <circle cx="574" cy="293" r="10" class="slider-handle"/>
  <text x="125" y="318" class="slider-label">10</text>
  <text x="350" y="318" class="slider-label" text-anchor="middle">20</text>
  <text x="573" y="318" class="slider-label" text-anchor="end">30</text>
  <text x="610" y="298" class="slider-value" text-anchor="end">30</text>

  <rect x="124" y="335" width="552" height="50" rx="4" class="preview-box"/>
  <text x="144" y="356" class="preview-txt">⚠ Régénérer la sélection effacera votre progression d'écoute actuelle</text>
  <text x="144" y="374" class="preview-txt">(12/30 séquences écoutées) mais conservera votre verdict s'il est saisi.</text>

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
- **Composant de vue audio fourni** : le bloc `sonogramme + spectrogramme + curseur synchronisé + boutons zoom` est un **composant JavaFX fourni par l'équipe pédagogique** (cf. Contraintes techniques). Les étudiants ne le réimplémentent pas - ils l'instancient avec un `wav:Path` et reçoivent les évènements de lecture / curseur. Ce composant est partagé avec [M-SonsValidation](M-SonsValidation.md), évitant ainsi la duplication d'un calcul FFT non trivial.
- **Lecteur audio** : `MediaPlayer` JavaFX avec `Media`. Le composant audio fourni gère les contrôles standards (⏮ ⏯ ⏭) et expose une API simple.
- **Cohérence visuelle avec [M-SonsValidation](M-SonsValidation.md)** : les deux écrans partagent le même style de panneau de détail (fond clair, sections numérotées, vue audio combinée, boutons d'action colorés en bas). Les étudiants n'implémentent qu'un seul patron de « lieu d'écoute », réutilisé sur les deux écrans.
- **Sélection vs persistance du verdict** : le clic sur l'un des 3 boutons (OK / Douteux / À jeter) **sélectionne** mais ne persiste pas. La persistance se fait via le bouton « Enregistrer le verdict » (qui peut aussi être déclenché par ⏎ Entrée). Cette dissociation évite les fausses manipulations et permet de changer d'avis avant de valider.
- **Synchronisation lecture ↔ progression** : le statut `écouté` doit être marqué dès le début de la lecture (pas à la fin), pour permettre à l'utilisateur de zapper rapidement.
- **Persistance** : la sélection, son état d'écoute, le verdict et le commentaire sont tous persistés en base. Au retour sur l'écran, on retrouve tout son contexte.
- **Raccourcis clavier** (O/D/J/⏎/Espace/↑/↓) : à implémenter via `setOnKeyPressed` au niveau de la racine de la vue. Cohérents dans l'esprit avec M-SonsValidation (Entrée/R/N) pour faciliter le transfert d'apprentissage.
