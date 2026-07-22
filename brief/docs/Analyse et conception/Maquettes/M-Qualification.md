# M-Qualification - Vérifier l'enregistrement par échantillonnage

> **Type** : vue plein écran (atteinte par clic « Vérifier l'enregistrement » depuis [M-Passage](M-Passage.md)).
> **Persona principal** : tous. C'est l'étape de **sound check global** que chaque utilisateur fait avant de déposer une nuit.
> **Parcours couverts** : [P3 - Vérifier l'enregistrement par échantillonnage](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md).

L'écran est divisé en **2 colonnes** surmontant un **bandeau de décision en pied** : à gauche, la liste des séquences d'écoute échantillonnées avec, pour chacune, son état d'écoute et son **verdict par fichier son** ; à droite, le panneau de détail (info séquence + vue audio + **les trois boutons de verdict du fichier courant**). En pied, sur toute la largeur, le **verdict final du passage** : une valeur **proposée** (dérivée des verdicts par fichier), surchargeable, puis enregistrée. L'utilisateur enchaîne typiquement : clic sur une séquence → écoute → verdict du fichier (`Bon` / `Mauvais` / `Inexploitable`) → suivante ; le verdict final se recompose au fil de l'eau, et il l'enregistre quand il est prêt.

> **Patron d'écoute partagé avec [M-SonsValidation](M-SonsValidation.md)** : les deux écrans réutilisent le **même composant d'écoute** (`AudioView` : sonogramme + spectrogramme) sous un squelette « liste ↔ écoute » commun. La différence porte sur le **mode métier** : ici un **verdict par fichier son** puis un **verdict final du passage** (mode *Vérification* par échantillonnage), là un **verdict par observation** (mode *Validation* taxonomique).

> ✅ **Écran implémenté (chantier [#1524](https://github.com/echonuit/vigiechiro-pr-companion/issues/1524), lot 6).** Le modèle **cible** est en place : un **verdict par fichier son** (`Bon` / `Mauvais` / `Inexploitable`) saisi séquence par séquence, une **barre tricolore** qui en donne la répartition d'un coup d'œil, et un **verdict final du passage dérivé** et surchargeable (`OK` / `Utilisable` / `Inexploitable`, état initial `Non vérifié`) - cf. [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), [R13/R14](../Modèle%20conceptuel/Règles%20métier.md#r13) et le [glossaire](../Modèle%20conceptuel/Glossaire%20métier.md).

## Maquette principale - 12 séquences sur 30 écoutées, quelques verdicts par fichier posés

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
    .feu { font: 11px sans-serif; }
    .feu-vert { fill: #d5f0de; stroke: #1e8449; stroke-width: 1; }
    .feu-vert-txt { fill: #0e5128; font: 11px sans-serif; }
    .col-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .col-title { font: 600 15px sans-serif; fill: #2c3e50; }
    .col-sub { font: 12px sans-serif; fill: #6a737d; }
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
    .play-icon { fill: #ffffff; font: 700 11px sans-serif; }
    .btn-secondary-small { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .btn-txt-dark-small { fill: #2c3e50; font: 11px sans-serif; }

    /* Barre tricolore de répartition des verdicts par fichier */
    .tri-frame { fill: #eef2f5; stroke: #d0d7de; stroke-width: 1; }
    .tri-bon { fill: #1e8449; }
    .tri-mauvais { fill: #b9770e; }
    .tri-inexp { fill: #a93226; }

    /* Badges de la colonne Verdict (par fichier) */
    .badge-bon { fill: #d5f0de; }
    .badge-bon-txt { fill: #0e5128; font: 600 10px sans-serif; }
    .badge-mauvais { fill: #fbe6c8; }
    .badge-mauvais-txt { fill: #7e5109; font: 600 10px sans-serif; }
    .badge-inexp { fill: #f5d2cd; }
    .badge-inexp-txt { fill: #6e2c00; font: 600 10px sans-serif; }
    .badge-nonjuge { fill: #eef2f5; }
    .badge-nonjuge-txt { fill: #6a737d; font: 10px sans-serif; }

    .detail-section { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .section-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .section-sub { font: 11px sans-serif; fill: #6a737d; }
    .seq-info-card { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .seq-num { font: 700 15px monospace; fill: #2c3e50; }
    .seq-meta { font: 11px sans-serif; fill: #6a737d; }

    .waveform-bg-light { fill: #eef2f5; stroke: #6a737d; stroke-width: 1; }
    .waveform-bar-played { fill: #1e8449; }
    .waveform-bar-pending { fill: #4a90d9; }
    .waveform-curseur { stroke: #e74c3c; stroke-width: 1.5; }
    .specto-bg { fill: #1c2833; stroke: #34495e; stroke-width: 1; }
    .specto-axis-txt { font: 9px monospace; fill: #bdc3c7; }
    .specto-curseur { stroke: #e74c3c; stroke-width: 1.5; }
    .player-bar { fill: #2c3e50; }
    .player-time { fill: #ffffff; font: 11px monospace; }
    .player-ctrl { fill: #ffffff; font: 14px sans-serif; }

    /* Boutons verdict PAR FICHIER (contour coloré, fond blanc) */
    .vf-btn { fill: #ffffff; stroke-width: 1.5; }
    .vf-bon { stroke: #1e8449; }
    .vf-bon-txt { fill: #0e5128; font: 600 12px sans-serif; }
    .vf-mauvais { stroke: #b9770e; }
    .vf-mauvais-txt { fill: #7e5109; font: 600 12px sans-serif; }
    .vf-inexp { stroke: #a93226; }
    .vf-inexp-txt { fill: #6e2c00; font: 600 12px sans-serif; }
    .vf-bon-active { fill: #d5f0de; stroke: #1e8449; stroke-width: 2.5; }

    /* Pied : verdict GLOBAL du passage */
    .global-bar { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .propose-chip { fill: #fbe6c8; stroke: #b9770e; stroke-width: 1.5; }
    .propose-txt { fill: #7e5109; font: 600 12px sans-serif; }
    .btn-verdict-ok { fill: #1e8449; stroke: #0e5128; stroke-width: 1.5; }
    .btn-verdict-util { fill: #b9770e; stroke: #7e5109; stroke-width: 1.5; }
    .btn-verdict-util-selected { fill: #b9770e; stroke: #2c3e50; stroke-width: 3; }
    .btn-verdict-inexp { fill: #a93226; stroke: #6e2c00; stroke-width: 1.5; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .comment-input { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .comment-placeholder { font: 12px sans-serif; fill: #bdc3c7; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="880" rx="4" class="frame"/>
  <!-- Bandeau du chrome : titre + fil d'Ariane + recherche -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="38" class="breadcrumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="642" y="38" class="breadcrumb-curr">Vérifier</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="90" class="pagesub">Écoutez quelques séquences réparties sur la nuit pour vous assurer que l'enregistrement est exploitable.</text>

  <!-- Bandeau infos passage -->
  <rect x="40" y="105" width="1120" height="48" rx="4" class="info-bar"/>
  <text x="58" y="125" class="info-label">PASSAGE</text>
  <text x="58" y="142" class="info-value">Carré 640380 / A1 / N° 2 (2026)</text>
  <text x="320" y="125" class="info-label">DATE / PLAGE</text>
  <text x="320" y="142" class="info-value">22/06/2026 · 20:25 → 07:47</text>
  <text x="560" y="125" class="info-label">VOLUMÉTRIE</text>
  <text x="560" y="142" class="info-value">3 614 séquences · 5 h 1 min</text>
  <text x="835" y="125" class="info-label">VERDICT ACTUEL</text>
  <text x="835" y="142" class="info-value" fill="#6a737d" font-style="italic">non saisi</text>
  <text x="1010" y="125" class="info-label">STATUT</text>
  <text x="1010" y="142" class="info-value">Transformé</text>

  <!-- Pré-check consultatif (3 feux, R13) -->
  <text x="40" y="177" class="col-sub">Pré-check :</text>
  <rect x="108" y="166" width="150" height="18" rx="9" class="feu-vert"/>
  <text x="118" y="179" class="feu-vert-txt">✓ Couverture horaire</text>
  <rect x="266" y="166" width="150" height="18" rx="9" class="feu-vert"/>
  <text x="276" y="179" class="feu-vert-txt">✓ Nombre de fichiers</text>
  <rect x="424" y="166" width="180" height="18" rx="9" class="feu-vert"/>
  <text x="434" y="179" class="feu-vert-txt">✓ Cohérence du renommage</text>
  <text x="1160" y="179" class="col-sub" text-anchor="end">R13 : aucun seuil obligatoire</text>

  <!-- ============== Colonne gauche : liste des séquences ============== -->
  <rect x="40" y="200" width="600" height="500" rx="4" class="col-section"/>
  <text x="60" y="226" class="col-title">📋 Sélection d'écoute (30 séquences)</text>
  <rect x="470" y="212" width="76" height="24" rx="3" class="btn-secondary-small"/>
  <text x="508" y="228" class="btn-txt-dark-small" text-anchor="middle">Personnaliser…</text>
  <rect x="552" y="212" width="76" height="24" rx="3" class="btn-secondary-small"/>
  <text x="590" y="228" class="btn-txt-dark-small" text-anchor="middle">↺ Régénérer</text>

  <!-- Barre TRICOLORE : répartition des verdicts par fichier -->
  <rect x="60" y="245" width="560" height="14" class="tri-frame"/>
  <rect x="60" y="245" width="147" height="14" class="tri-bon"/>       <!-- 7 Bon -->
  <rect x="207" y="245" width="63" height="14" class="tri-mauvais"/>   <!-- 3 Mauvais -->
  <rect x="270" y="245" width="42" height="14" class="tri-inexp"/>     <!-- 2 Inexploitable -->
  <!-- reste (18 non jugés) = fond gris de tri-frame -->
  <text x="60" y="276" class="col-sub">7 Bon · 3 Mauvais · 2 Inexploitable · 18 non jugés</text>

  <!-- Tableau séquences (colonnes : N° | Fichier | Durée | Écouté | Verdict) -->
  <rect x="60" y="288" width="560" height="26" class="table-head"/>
  <text x="76" y="305" class="col-head">▶ N°</text>
  <text x="150" y="305" class="col-head">FICHIER</text>
  <text x="360" y="305" class="col-head">DURÉE</text>
  <text x="425" y="305" class="col-head">ÉCOUTÉ</text>
  <text x="500" y="305" class="col-head">VERDICT</text>

  <!-- Ligne 1 : Bon -->
  <rect x="60" y="314" width="560" height="30" class="table-row"/>
  <circle cx="78" cy="329" r="8" class="play-btn"/><text x="78" y="333" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="333" class="cell">01</text>
  <text x="150" y="333" class="cell-mono">…PaRec_202612.wav</text>
  <text x="360" y="333" class="cell">5,0 s</text>
  <text x="440" y="333" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="490" y="319" width="52" height="18" rx="9" class="badge-bon"/><text x="516" y="332" class="badge-bon-txt" text-anchor="middle">Bon</text>

  <!-- Ligne 2 : Bon -->
  <rect x="60" y="344" width="560" height="30" class="table-row-alt"/>
  <circle cx="78" cy="359" r="8" class="play-btn"/><text x="78" y="363" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="363" class="cell">02</text>
  <text x="150" y="363" class="cell-mono">…PaRec_204833.wav</text>
  <text x="360" y="363" class="cell">5,0 s</text>
  <text x="440" y="363" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="490" y="349" width="52" height="18" rx="9" class="badge-bon"/><text x="516" y="362" class="badge-bon-txt" text-anchor="middle">Bon</text>

  <!-- Ligne 3 : Mauvais -->
  <rect x="60" y="374" width="560" height="30" class="table-row"/>
  <circle cx="78" cy="389" r="8" class="play-btn"/><text x="78" y="393" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="393" class="cell">03</text>
  <text x="150" y="393" class="cell-mono">…PaRec_211148.wav</text>
  <text x="360" y="393" class="cell">5,0 s</text>
  <text x="440" y="393" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="484" y="379" width="64" height="18" rx="9" class="badge-mauvais"/><text x="516" y="392" class="badge-mauvais-txt" text-anchor="middle">Mauvais</text>

  <!-- Ligne 4 : courante (en cours d'écoute), pas encore jugée -->
  <rect x="60" y="404" width="560" height="30" class="table-row-current"/>
  <circle cx="78" cy="419" r="8" class="play-btn-current"/><text x="78" y="423" class="play-icon" text-anchor="middle">⏸</text>
  <text x="98" y="423" class="cell" font-weight="700">04</text>
  <text x="150" y="423" class="cell-mono" font-weight="700">…PaRec_213455.wav</text>
  <text x="360" y="423" class="cell" font-weight="700">5,0 s</text>
  <text x="440" y="423" class="cell-not-listened" text-anchor="middle">○</text>
  <rect x="480" y="409" width="72" height="18" rx="9" class="badge-nonjuge"/><text x="516" y="422" class="badge-nonjuge-txt" text-anchor="middle">Non jugé</text>

  <!-- Ligne 5 : Inexploitable -->
  <rect x="60" y="434" width="560" height="30" class="table-row"/>
  <circle cx="78" cy="449" r="8" class="play-btn"/><text x="78" y="453" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="453" class="cell">05</text>
  <text x="150" y="453" class="cell-mono">…PaRec_215821.wav</text>
  <text x="360" y="453" class="cell">5,0 s</text>
  <text x="440" y="453" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="478" y="439" width="76" height="18" rx="9" class="badge-inexp"/><text x="516" y="452" class="badge-inexp-txt" text-anchor="middle">Inexploitable</text>

  <!-- Ligne 6 : Bon -->
  <rect x="60" y="464" width="560" height="30" class="table-row-alt"/>
  <circle cx="78" cy="479" r="8" class="play-btn"/><text x="78" y="483" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="483" class="cell">06</text>
  <text x="150" y="483" class="cell-mono">…PaRec_222147.wav</text>
  <text x="360" y="483" class="cell">5,0 s</text>
  <text x="440" y="483" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="490" y="469" width="52" height="18" rx="9" class="badge-bon"/><text x="516" y="482" class="badge-bon-txt" text-anchor="middle">Bon</text>

  <!-- Ligne 7 : Bon -->
  <rect x="60" y="494" width="560" height="30" class="table-row"/>
  <circle cx="78" cy="509" r="8" class="play-btn"/><text x="78" y="513" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="513" class="cell">07</text>
  <text x="150" y="513" class="cell-mono">…PaRec_224513.wav</text>
  <text x="360" y="513" class="cell">5,0 s</text>
  <text x="440" y="513" class="cell-listened" text-anchor="middle">✓</text>
  <rect x="490" y="499" width="52" height="18" rx="9" class="badge-bon"/><text x="516" y="512" class="badge-bon-txt" text-anchor="middle">Bon</text>

  <!-- Ligne 8 : non écoutée, non jugée -->
  <rect x="60" y="524" width="560" height="30" class="table-row-alt"/>
  <circle cx="78" cy="539" r="8" class="play-btn"/><text x="78" y="543" class="play-icon" text-anchor="middle">▶</text>
  <text x="98" y="543" class="cell">08</text>
  <text x="150" y="543" class="cell-mono">…PaRec_230839.wav</text>
  <text x="360" y="543" class="cell">5,0 s</text>
  <text x="440" y="543" class="cell-not-listened" text-anchor="middle">○</text>
  <rect x="480" y="529" width="72" height="18" rx="9" class="badge-nonjuge"/><text x="516" y="542" class="badge-nonjuge-txt" text-anchor="middle">Non jugé</text>

  <text x="340" y="580" class="col-sub" text-anchor="middle" font-style="italic">↓ 22 séquences supplémentaires ↓</text>
  <text x="60" y="678" class="col-sub">La barre tricolore résume la qualité de l'échantillon : un rouge dominant = enregistrement à problème.</text>

  <!-- ============== Colonne droite : détail + verdict PAR FICHIER ============== -->
  <rect x="660" y="200" width="500" height="500" rx="4" class="detail-section"/>

  <text x="680" y="226" class="section-title">📋 Séquence sélectionnée</text>
  <rect x="680" y="235" width="460" height="46" rx="4" class="seq-info-card"/>
  <text x="700" y="256" class="seq-num">N° 04</text>
  <text x="700" y="273" class="seq-meta">Fichier …PaRec_213455.wav · durée 5,0 s · ○ non écoutée</text>

  <text x="680" y="305" class="section-title">🎵 Vue audio (sonogramme + spectrogramme)</text>
  <!-- Sonogramme (fond clair) -->
  <rect x="680" y="315" width="460" height="46" rx="4" class="waveform-bg-light"/>
  <rect x="700" y="330" width="2" height="16" class="waveform-bar-played"/>
  <rect x="712" y="324" width="2" height="28" class="waveform-bar-played"/>
  <rect x="726" y="320" width="2" height="36" class="waveform-bar-played"/>
  <rect x="742" y="326" width="2" height="24" class="waveform-bar-played"/>
  <rect x="760" y="322" width="2" height="32" class="waveform-bar-played"/>
  <rect x="800" y="328" width="2" height="20" class="waveform-bar-pending"/>
  <rect x="830" y="330" width="2" height="16" class="waveform-bar-pending"/>
  <rect x="870" y="332" width="2" height="12" class="waveform-bar-pending"/>
  <rect x="920" y="333" width="2" height="10" class="waveform-bar-pending"/>
  <rect x="980" y="334" width="2" height="8" class="waveform-bar-pending"/>
  <rect x="1060" y="334" width="2" height="8" class="waveform-bar-pending"/>
  <line x1="785" y1="315" x2="785" y2="361" class="waveform-curseur"/>
  <!-- Spectrogramme (fond sombre, composant partagé) -->
  <rect x="680" y="366" width="460" height="96" class="specto-bg"/>
  <text x="676" y="378" class="specto-axis-txt" text-anchor="end">150</text>
  <text x="676" y="415" class="specto-axis-txt" text-anchor="end">80</text>
  <text x="676" y="455" class="specto-axis-txt" text-anchor="end">8 kHz</text>
  <rect x="710" y="400" width="4" height="46" fill="#e74c3c"/>
  <rect x="770" y="404" width="4" height="42" fill="#e67e22"/>
  <rect x="835" y="400" width="4" height="46" fill="#e74c3c"/>
  <rect x="905" y="406" width="4" height="40" fill="#f39c12"/>
  <rect x="975" y="402" width="4" height="44" fill="#e67e22"/>
  <rect x="1050" y="400" width="4" height="46" fill="#e74c3c"/>
  <line x1="785" y1="366" x2="785" y2="462" class="specto-curseur"/>

  <!-- Lecteur audio -->
  <rect x="680" y="470" width="460" height="30" rx="3" class="player-bar"/>
  <text x="695" y="490" class="player-ctrl">⏮ ⏯ ⏭</text>
  <text x="775" y="490" class="player-time">2,1 s / 5,0 s</text>
  <text x="1130" y="490" class="player-time" text-anchor="end">🔊 ━━━○──</text>

  <!-- Verdict PAR FICHIER de la séquence courante -->
  <text x="680" y="530" class="section-title">🎧 Votre verdict sur ce fichier</text>
  <rect x="680" y="540" width="145" height="40" rx="4" class="vf-bon-active"/>
  <text x="752" y="565" class="vf-bon-txt" text-anchor="middle">Bon</text>
  <rect x="838" y="540" width="145" height="40" rx="4" class="vf-btn vf-mauvais"/>
  <text x="910" y="565" class="vf-mauvais-txt" text-anchor="middle">Mauvais</text>
  <rect x="995" y="540" width="145" height="40" rx="4" class="vf-btn vf-inexp"/>
  <text x="1067" y="565" class="vf-inexp-txt" text-anchor="middle">Inexploitable</text>
  <text x="680" y="604" class="section-sub">On juge le fichier qu'on vient d'écouter ; le bouton actif rappelle le verdict déjà posé.</text>

  <!-- ============== Pied : VERDICT GLOBAL du passage (sous les deux colonnes) ============== -->
  <rect x="40" y="712" width="1120" height="130" rx="4" class="global-bar"/>
  <text x="60" y="740" class="section-title">⚖ Verdict global du passage</text>
  <text x="60" y="758" class="section-sub">Décision d'ensemble pour toute la nuit, pas pour un seul fichier.</text>

  <!-- Puce Proposé (dérivé) -->
  <rect x="560" y="722" width="185" height="30" rx="15" class="propose-chip"/>
  <text x="652" y="742" class="propose-txt" text-anchor="middle">Proposé : Utilisable</text>
  <!-- Boutons O / U / I (Utilisable pré-sélectionné = dérivé) -->
  <rect x="760" y="720" width="120" height="34" rx="4" class="btn-verdict-ok"/>
  <text x="820" y="742" class="btn-txt" text-anchor="middle">OK</text>
  <rect x="890" y="720" width="120" height="34" rx="4" class="btn-verdict-util-selected"/>
  <text x="950" y="742" class="btn-txt" text-anchor="middle">Utilisable</text>
  <rect x="1020" y="720" width="120" height="34" rx="4" class="btn-verdict-inexp"/>
  <text x="1080" y="742" class="btn-txt" text-anchor="middle">Inexploitable</text>

  <!-- Commentaire -->
  <text x="60" y="782" class="section-sub">💬 Commentaire (optionnel)</text>
  <rect x="60" y="790" width="800" height="36" rx="3" class="comment-input"/>
  <text x="72" y="812" class="comment-placeholder">Ex. vent fort vers 02:00, sons à vérifier ultérieurement.</text>
  <!-- Enregistrer -->
  <rect x="930" y="792" width="210" height="34" rx="4" class="btn-primary"/>
  <text x="1035" y="813" class="btn-txt" text-anchor="middle">💾 Enregistrer le verdict</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💡 Raccourcis : ↑/↓ navigation · Espace lecture/pause · O → OK · D → Utilisable · J → Inexploitable · ⏎ Enregistrer</text>
  <text x="1150" y="880" class="footer-txt" text-anchor="end">Proposé « Utilisable » (dérivé) · non enregistré</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome** (barre de navigation commune) via le contrat `EmplacementNavigation` ; l'écran ne dessine plus son propre fil ni de lien « retour au passage ». Emplacement affiché : `🏠 Accueil › Mes sites › Carré N › Détails du passage N° X › Vérifier l'enregistrement`.
- **Bandeau infos passage** : 5 cellules de rappel (passage, date/plage, volumétrie, verdict actuel, statut).
- **Pré-check consultatif (R13)** : trois « feux » (couverture horaire, nombre de fichiers, cohérence du renommage) qui aident à décider **sans jamais bloquer**. Chaque feu porte une **icône** distincte en plus de sa couleur (lisibilité daltoniens) et une infobulle explicative. L'icône est un glyphe rendu par la police d'icônes, non un caractère écrit dans le libellé : un caractère dépend des polices installées sur le poste et ne se teinte pas avec le texte, ce qui affaiblissait la garantie qu'il servait à donner.
- **Colonne gauche - Liste séquences** :
    - En-tête avec compteur + boutons « Personnaliser… » / « Régénérer ».
    - **Barre tricolore** de répartition des verdicts par fichier (part `Bon` verte / `Mauvais` ambre / `Inexploitable` rouge, le reste gris = `Non jugé`) : un rouge dominant signale d'un coup d'œil un enregistrement à problème. Le résumé chiffré double la couleur en texte.
    - Tableau : N° · fichier · durée · écouté (✓/○) · **colonne Verdict** (badge coloré par séquence).
    - Ligne courante (n° 04) en surbrillance bleue.
- **Colonne droite - Panneau de détail** (style harmonisé avec [M-SonsValidation](M-SonsValidation.md)) :
    - **Séquence sélectionnée** (card claire) : N° + fichier + durée + état d'écoute.
    - **Vue audio combinée** (sonogramme + spectrogramme) - **composant fourni** (cf. Contraintes techniques), synchronisé par un curseur rouge unique.
    - **Lecteur audio** (barre sombre) : ⏮ ⏯ ⏭, minutage, volume.
    - **Votre verdict sur ce fichier** : trois boutons à contour coloré (`Bon` / `Mauvais` / `Inexploitable`) qui jugent **la séquence courante**. Celui du verdict déjà posé est mis en évidence ; désactivés tant qu'aucune séquence n'est sélectionnée.
- **Pied - Verdict global du passage** (pleine largeur, sous les deux colonnes, pour signifier qu'il porte sur **toute la nuit**) :
    - Une **puce « Proposé : X »** donne le verdict final **dérivé** des verdicts par fichier ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).
    - Trois boutons `OK` / `Utilisable` / `Inexploitable` **pré-remplis** par le proposé (surchargeables). Un « (surchargé) » apparaît sur la puce si le choix diffère du proposé.
    - **Commentaire** optionnel + bouton **« 💾 Enregistrer le verdict »**, qui persiste le verdict + commentaire et passe le passage au statut `Vérifié`.
- **Pied de page** : raccourcis clavier harmonisés (↑/↓/Espace/O/D/J/⏎).

### Différences sémantiques avec [M-SonsValidation](M-SonsValidation.md)

| Aspect | M-Qualification (cet écran) | M-SonsValidation |
|---|---|---|
| Objet de la revue | Séquences d'écoute échantillonnées | Observations Tadarida (avec taxon) |
| Visualisation audio | **Sonogramme + spectrogramme (composant partagé)** | **Sonogramme + spectrogramme (composant partagé)** |
| Décision | Un **verdict par fichier son**, puis un **verdict final du passage dérivé** | Une décision **par observation** |
| Persistance | Verdicts par fichier **instantanés** ; verdict final **différé** (bouton « Enregistrer ») | Instantanée (par clic Valider/Corriger) |
| Boutons d'action | Par fichier : Bon / Mauvais / Inexploitable · global : OK / Utilisable / Inexploitable | Valider / Corriger / Référence |
| Statut résultant | Le passage passe au statut `Vérifié` | L'observation passe à `Validée` / `Corrigée` |

### Interactions clés

| Élément | Action |
|---|---|
| Clic sur ▶ d'une ligne | Joue la séquence dans le lecteur. Marque automatiquement comme « écoutée » |
| Clic ailleurs sur la ligne | Sélectionne la séquence et charge dans le lecteur sans démarrer |
| ↑ / ↓ (clavier) | Navigation entre séquences |
| Espace (clavier) | Lecture / pause de la séquence courante |
| Boutons **Bon / Mauvais / Inexploitable** | Posent le **verdict par fichier** de la séquence courante (instantané). Le verdict final proposé se recompose aussitôt |
| **O** (clavier) ou clic vert | Verdict global `OK` |
| **D** (clavier) ou clic ambre | Verdict global `Utilisable` |
| **J** (clavier) ou clic rouge | Verdict global `Inexploitable` |
| **⏎** (Entrée) ou clic Enregistrer | Persiste le verdict global (proposé ou surchargé) + commentaire, passe le passage au statut `Vérifié` |
| Bouton **Personnaliser…** / **Régénérer** | Reconstitue la sélection (méthode + taille) ; **efface les verdicts par fichier** déjà saisis |
| Verdict global **Inexploitable** | Le passage ne pourra pas être inclus dans un lot prêt à déposer ([R14](../Modèle%20conceptuel/Règles%20métier.md#r14)) ; il faudra le **requalifier** |

---

## Variante - modale de personnalisation de la sélection

Activée par le bouton **Personnaliser…** dans l'en-tête de la liste. Permet de changer la méthode (RéparTemporel ou Aléatoire) et la taille (entre 10 et 30 séquences).

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
  <text x="144" y="356" class="preview-txt">⚠ Régénérer la sélection effacera votre progression d'écoute et</text>
  <text x="144" y="374" class="preview-txt">les verdicts par fichier déjà saisis (12/30 écoutées).</text>

  <rect x="466" y="400" width="100" height="34" rx="4" class="btn-secondary"/>
  <text x="516" y="421" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="576" y="400" width="100" height="34" rx="4" class="btn-primary"/>
  <text x="626" y="421" class="btn-txt" text-anchor="middle">↺ Régénérer</text>
</svg>
</div>

### Notes sur la modale de personnalisation

- **Méthode** : 2 cards radio horizontales. RéparTemporel sélectionnée par défaut (cf. [R12](../Modèle%20conceptuel/Règles%20métier.md#r12)).
- **Taille** : slider 10-30, valeur courante affichée à droite (gros chiffre bleu).
- **Aperçu impact** (encart jaune) : avertissement explicite que la régénération efface la progression d'écoute **et les verdicts par fichier**.
- **Boutons** : Annuler (secondary) / Régénérer (primary).

## Notes pour l'implémentation

- **TableView avec virtualisation** : la liste des séquences peut atteindre 30 lignes (taille max). JavaFX `TableView` gère nativement la virtualisation.
- **Composant de vue audio fourni** : le bloc `sonogramme + spectrogramme + curseur synchronisé + boutons zoom` est un **composant JavaFX fourni** (cf. Contraintes techniques), partagé avec [M-SonsValidation](M-SonsValidation.md).
- **Barre tricolore** : ce n'est **pas** un `ProgressBar` JavaFX (mono-couleur) mais une petite barre empilée maison (un `HBox` de segments colorés proportionnels aux comptes `Bon` / `Mauvais` / `Inexploitable`, le reste laissant voir le fond gris « non jugé »).
- **Verdict par fichier vs verdict final** : les boutons `Bon` / `Mauvais` / `Inexploitable` posent un verdict **par séquence** (persisté aussitôt). Le **verdict final du passage** en est **dérivé** ([`AgregationVerdict`](../Modèle%20conceptuel/Règles%20métier.md#r13)) et **pré-remplit** les boutons `OK` / `Utilisable` / `Inexploitable` du pied, que l'utilisateur peut **surcharger** avant d'enregistrer.
- **Synchronisation lecture ↔ progression** : le statut `écouté` est marqué dès le **début** de la lecture (pas à la fin), pour permettre de zapper rapidement.
- **Persistance** : la sélection, son état d'écoute, les verdicts par fichier, le verdict final et le commentaire sont tous persistés en base. Au retour sur l'écran, on retrouve tout son contexte.
- **Raccourcis clavier** (O/D/J/⏎/Espace/↑/↓) : implémentés via un filtre d'évènements clavier au niveau de la racine de la vue (phase de capture, pour que la barre d'espace lance la lecture même quand un bouton a le focus).
</content>
