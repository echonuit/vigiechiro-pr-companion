# M-Passage - Détail d'un passage

> **Type** : vue de détail (atteinte par clic sur une ligne de passage dans [M-Site-detail](M-Site-detail.md) ou [M-MultiSite](M-MultiSite.md)).
> **Persona principal** : tous. C'est l'écran pivot qui agrège les fonctionnalités liées à une nuit de capture spécifique.
> **Parcours couverts** : transverse — sert de point d'entrée pour [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md).
> **Stories couvertes** : [E0.S3 - Persister passages avec statut](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s3), [E4.S4 - Stepper de statut + chronologie](../Story%20mapping/E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md#e4s4), [E2.S8 - Modifier le rattachement](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s8), [E6.S1 / E6.S2 / E6.S3 - Diagnostic](../Story%20mapping/E6%20-%20Diagnostiquer%20le%20matériel.md).

C'est l'**écran pivot** d'un passage. Il agrège la fiche d'identité (site, point, année, n° passage, dates, enregistreur), le stepper de statut workflow, et 4 onglets : **Vue d'ensemble** (résumé + actions rapides), **Vérification d'enregistrement** (résumé + lien vers [M-Qualification](M-Qualification.md)), **Diagnostic matériel**, **Validation Tadarida** (résumé + lien vers [M-Vision-Tadarida](M-Vision-Tadarida.md)).

## Wireframe principal - vue d'ensemble

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-Passage - Vue d'ensemble" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .pagetitle { font: 700 24px sans-serif; fill: #2c3e50; }
    .pagesub { font: 14px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 13px sans-serif; }
    .info-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value { font: 600 14px sans-serif; fill: #2c3e50; }
    .info-mono { font: 600 13px monospace; fill: #2c3e50; }
    .step-circle-done { fill: #1e8449; stroke: #0e5128; stroke-width: 1; }
    .step-circle-current { fill: #4a90d9; stroke: #2563a3; stroke-width: 2; }
    .step-circle-pending { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .step-line-done { stroke: #1e8449; stroke-width: 3; }
    .step-line-pending { stroke: #d0d7de; stroke-width: 3; }
    .step-num-txt { fill: #ffffff; font: 700 12px sans-serif; }
    .step-num-pending { fill: #6a737d; font: 700 12px sans-serif; }
    .step-label-done { font: 600 13px sans-serif; fill: #1e6f3f; }
    .step-label-current { font: 700 13px sans-serif; fill: #2c3e50; }
    .step-label-pending { font: 13px sans-serif; fill: #6a737d; }
    .step-date { font: 11px sans-serif; fill: #6a737d; }
    .tab-active { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-inactive { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .tab-bottom { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-txt-active { font: 600 14px sans-serif; fill: #2c3e50; }
    .tab-txt-inactive { font: 14px sans-serif; fill: #6a737d; }
    .stat-card { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .stat-num { font: 700 28px sans-serif; fill: #4a90d9; }
    .stat-label { font: 12px sans-serif; fill: #6a737d; }
    .stat-detail { font: 11px sans-serif; fill: #2c3e50; }
    .action-card { fill: #ffffff; stroke: #4a90d9; stroke-width: 1.5; }
    .action-card-locked { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .action-icon { font: 32px sans-serif; }
    .action-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .action-title-locked { font: 600 14px sans-serif; fill: #6a737d; }
    .action-sub { font: 12px sans-serif; fill: #6a737d; }
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
  <text x="330" y="67" class="navtxt-inactive">📊 Vue tabulaire</text>
  <text x="470" y="67" class="navtxt-inactive">⚙ Paramètres</text>
  <text x="1140" y="67" class="navtxt-inactive" text-anchor="end">👤 Local</text>

  <text x="40" y="108" class="breadcrumb">‹ Mes sites</text>
  <text x="125" y="108" class="breadcrumb-sep">›</text>
  <text x="140" y="108" class="breadcrumb">Carré 640380</text>
  <text x="265" y="108" class="breadcrumb-sep">›</text>
  <text x="280" y="108" class="breadcrumb-curr">Passage 2 / A1 / 2026-06-22</text>

  <text x="40" y="148" class="pagetitle">Passage 2 - Point A1 - Nuit du 22/06/2026</text>
  <text x="40" y="172" class="pagesub">Carré 640380 (Étang de la Tuilière) · Enregistreur PR 1925492 · Importé il y a 2 jours</text>

  <!-- Boutons header -->
  <rect x="900" y="125" width="160" height="36" rx="4" class="btn-secondary"/>
  <text x="980" y="148" class="btn-txt-dark" text-anchor="middle">✏ Modifier rattachement</text>
  <rect x="1070" y="125" width="100" height="36" rx="4" class="btn-danger"/>
  <text x="1120" y="148" class="btn-txt-danger" text-anchor="middle">🗑 Supprimer</text>

  <!-- Bandeau d'infos clés -->
  <rect x="40" y="195" width="1120" height="60" rx="4" class="info-bar"/>
  <text x="60" y="216" class="info-label">SITE / POINT</text>
  <text x="60" y="240" class="info-value">640380 / A1</text>
  <text x="200" y="216" class="info-label">PASSAGE</text>
  <text x="200" y="240" class="info-value">N° 2 (2026)</text>
  <text x="350" y="216" class="info-label">DATE DE CAPTURE</text>
  <text x="350" y="240" class="info-value">22/06/2026 (mer)</text>
  <text x="540" y="216" class="info-label">PLAGE HORAIRE</text>
  <text x="540" y="240" class="info-value">20:25 → 07:47</text>
  <text x="710" y="216" class="info-label">ENREGISTREUR</text>
  <text x="710" y="240" class="info-mono">PR 1925492</text>
  <text x="860" y="216" class="info-label">VOLUME</text>
  <text x="860" y="240" class="info-value">38,4 Go (1 572 fichiers)</text>
  <text x="1080" y="216" class="info-label">SÉQUENCES</text>
  <text x="1080" y="240" class="info-value">3 614</text>

  <!-- Stepper de statut workflow -->
  <text x="40" y="295" class="step-label-current">📍 Avancement dans le workflow</text>

  <!-- Cercles + lignes du stepper -->
  <line x1="105" y1="335" x2="295" y2="335" class="step-line-done"/>
  <line x1="305" y1="335" x2="495" y2="335" class="step-line-done"/>
  <line x1="505" y1="335" x2="695" y2="335" class="step-line-pending"/>
  <line x1="705" y1="335" x2="895" y2="335" class="step-line-pending"/>
  <line x1="905" y1="335" x2="1095" y2="335" class="step-line-pending"/>

  <!-- Étape 1 : Importé (done) -->
  <circle cx="100" cy="335" r="22" class="step-circle-done"/>
  <text x="100" y="340" class="step-num-txt" text-anchor="middle">1</text>
  <text x="100" y="375" class="step-label-done" text-anchor="middle">Importé</text>
  <text x="100" y="392" class="step-date" text-anchor="middle">22/06 06:48</text>

  <!-- Étape 2 : Transformé (done) -->
  <circle cx="300" cy="335" r="22" class="step-circle-done"/>
  <text x="300" y="340" class="step-num-txt" text-anchor="middle">2</text>
  <text x="300" y="375" class="step-label-done" text-anchor="middle">Transformé</text>
  <text x="300" y="392" class="step-date" text-anchor="middle">22/06 07:14 (26 min)</text>

  <!-- Étape 3 : Vérifié (current) -->
  <circle cx="500" cy="335" r="22" class="step-circle-current"/>
  <text x="500" y="340" class="step-num-txt" text-anchor="middle">3</text>
  <text x="500" y="375" class="step-label-current" text-anchor="middle">Vérification en cours</text>
  <text x="500" y="392" class="step-date" text-anchor="middle">action requise →</text>

  <!-- Étape 4 : Prêt à déposer (pending) -->
  <circle cx="700" cy="335" r="22" class="step-circle-pending"/>
  <text x="700" y="340" class="step-num-pending" text-anchor="middle">4</text>
  <text x="700" y="375" class="step-label-pending" text-anchor="middle">Prêt à déposer</text>

  <!-- Étape 5 : Déposé (pending) -->
  <circle cx="900" cy="335" r="22" class="step-circle-pending"/>
  <text x="900" y="340" class="step-num-pending" text-anchor="middle">5</text>
  <text x="900" y="375" class="step-label-pending" text-anchor="middle">Déposé</text>

  <!-- Étape 6 (optionnelle) : Annoté Tadarida (pending, en pointillé) -->
  <circle cx="1100" cy="335" r="22" class="step-circle-pending" stroke-dasharray="3 3"/>
  <text x="1100" y="340" class="step-num-pending" text-anchor="middle">6</text>
  <text x="1100" y="375" class="step-label-pending" text-anchor="middle">Annoté Tadarida</text>
  <text x="1100" y="392" class="step-date" text-anchor="middle">(cible étirée)</text>

  <!-- Onglets -->
  <rect x="40" y="425" width="200" height="36" rx="4" class="tab-active"/>
  <text x="140" y="448" class="tab-txt-active" text-anchor="middle">📋 Vue d'ensemble</text>
  <rect x="240" y="425" width="200" height="36" rx="4" class="tab-inactive"/>
  <text x="340" y="448" class="tab-txt-inactive" text-anchor="middle">🎧 Vérification</text>
  <rect x="440" y="425" width="200" height="36" rx="4" class="tab-inactive"/>
  <text x="540" y="448" class="tab-txt-inactive" text-anchor="middle">🩺 Diagnostic</text>
  <rect x="640" y="425" width="240" height="36" rx="4" class="tab-inactive"/>
  <text x="760" y="448" class="tab-txt-inactive" text-anchor="middle">✅ Validation Tadarida</text>

  <!-- Zone contenu onglet "Vue d'ensemble" -->
  <rect x="40" y="461" width="1120" height="370" rx="0" class="tab-bottom"/>
  <line x1="40" y1="461" x2="240" y2="461" stroke="#ffffff" stroke-width="2"/>

  <!-- Stats cards -->
  <text x="60" y="495" class="step-label-current">📊 Stats du passage</text>

  <rect x="60" y="510" width="240" height="90" rx="4" class="stat-card"/>
  <text x="180" y="552" class="stat-num" text-anchor="middle">1 572</text>
  <text x="180" y="572" class="stat-label" text-anchor="middle">enregistrements WAV bruts</text>
  <text x="180" y="588" class="stat-detail" text-anchor="middle">Mono 16 bits 384 kHz · 38,4 Go</text>

  <rect x="320" y="510" width="240" height="90" rx="4" class="stat-card"/>
  <text x="440" y="552" class="stat-num" text-anchor="middle">3 614</text>
  <text x="440" y="572" class="stat-label" text-anchor="middle">séquences d'écoute</text>
  <text x="440" y="588" class="stat-detail" text-anchor="middle">Ralenties ×10 · 5 s · ~17 Go</text>

  <rect x="580" y="510" width="240" height="90" rx="4" class="stat-card"/>
  <text x="700" y="552" class="stat-num" text-anchor="middle">5h01</text>
  <text x="700" y="572" class="stat-label" text-anchor="middle">durée audible totale</text>
  <text x="700" y="588" class="stat-detail" text-anchor="middle">après expansion ×10</text>

  <rect x="840" y="510" width="240" height="90" rx="4" class="stat-card"/>
  <text x="960" y="552" class="stat-num" text-anchor="middle">11h22</text>
  <text x="960" y="572" class="stat-label" text-anchor="middle">plage de capture nocturne</text>
  <text x="960" y="588" class="stat-detail" text-anchor="middle">20:25 → 07:47</text>

  <!-- Actions rapides -->
  <text x="60" y="640" class="step-label-current">🚀 Que voulez-vous faire ?</text>

  <!-- Action 1 : Vérifier l'enregistrement (mise en avant) -->
  <rect x="60" y="660" width="340" height="160" rx="6" class="action-card"/>
  <text x="230" y="710" class="action-icon" text-anchor="middle">🎧</text>
  <text x="230" y="745" class="action-title" text-anchor="middle">Vérifier l'enregistrement</text>
  <text x="230" y="765" class="action-sub" text-anchor="middle">Sound check par échantillonnage</text>
  <text x="230" y="781" class="action-sub" text-anchor="middle">avant dépôt sur Vigie-Chiro.</text>
  <rect x="120" y="795" width="220" height="14" rx="7" fill="#4a90d9"/>
  <text x="230" y="806" class="step-num-txt" text-anchor="middle" font-size="11">▶ ACTION REQUISE</text>

  <!-- Action 2 : Voir le diagnostic -->
  <rect x="420" y="660" width="340" height="160" rx="6" class="action-card-locked"/>
  <text x="590" y="710" class="action-icon" text-anchor="middle">🩺</text>
  <text x="590" y="745" class="action-title-locked" text-anchor="middle">Voir le diagnostic matériel</text>
  <text x="590" y="765" class="action-sub" text-anchor="middle">Température, hygro, batterie,</text>
  <text x="590" y="781" class="action-sub" text-anchor="middle">évènements anormaux.</text>

  <!-- Action 3 : Valider Tadarida (verrouillée car pas encore déposé) -->
  <rect x="780" y="660" width="340" height="160" rx="6" class="action-card-locked"/>
  <text x="950" y="710" class="action-icon" text-anchor="middle">🔒</text>
  <text x="950" y="745" class="action-title-locked" text-anchor="middle">Valider les résultats Tadarida</text>
  <text x="950" y="765" class="action-sub" text-anchor="middle">Disponible après dépôt sur Vigie-Chiro</text>
  <text x="950" y="781" class="action-sub" text-anchor="middle">et réception du CSV (24-48 h).</text>

  <!-- Footer -->
  <rect x="10" y="860" width="1180" height="30" class="footer"/>
  <text x="40" y="880" class="footer-txt">💾 Passage #142 · /home/marie/VigieChiroCompanion/data/Car640380-2026-Pass2-A1/</text>
  <text x="1140" y="880" class="footer-txt" text-anchor="end">Statut : Transformé</text>
</svg>
</div>

### Annotations

- **Bandeau d'infos clés** : 7 cellules condensées qui décrivent le passage. La cellule `ENREGISTREUR` utilise une font monospace pour le n° de série (lisibilité).
- **Stepper de statut workflow** : 6 cercles avec lignes de connexion. Vert = franchi, bleu = en cours (action requise), gris = à venir. La 6e étape « Annoté Tadarida » est en pointillé pour signifier qu'elle est en cible étirée.
- **Onglets** : 4 onglets pour naviguer entre les facettes du passage. L'onglet actif (ici Vue d'ensemble) est blanc avec fond, les autres sont grisés.
- **Stats cards** : 4 chiffres clés visualisables d'un coup d'œil (volume bruts, volume transformés, durée audible, plage horaire).
- **Actions rapides** : 3 cards d'action. **Vérifier** est mise en avant (bordure bleue + bandeau « ACTION REQUISE »), **Diagnostic** est accessible mais discret, **Validation Tadarida** est explicitement verrouillée 🔒 tant que le passage n'est pas `Déposé`.

### Interactions clés

| Élément | Action |
|---|---|
| Breadcrumb | Navigation hiérarchique (sites > carré > passage) |
| Bouton **✏ Modifier rattachement** | Modale d'édition site/point/année/passage avec re-renommage des fichiers ([E2.S8](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s8)) |
| Bouton **🗑 Supprimer** | Confirmation forte (suppression des fichiers sur disque incluse) |
| Onglet **🎧 Vérification** | Bascule vers le résumé de vérification (avec bouton vers [M-Qualification](M-Qualification.md) plein écran) |
| Onglet **🩺 Diagnostic** | Bascule vers la variante diagnostic ci-dessous |
| Onglet **✅ Validation Tadarida** | Bascule vers le résumé de validation (avec bouton vers [M-Vision-Tadarida](M-Vision-Tadarida.md)) |
| Card **Vérifier l'enregistrement** | Ouvre directement [M-Qualification](M-Qualification.md) |
| Card **Voir le diagnostic** | Active l'onglet Diagnostic |
| Card **Valider Tadarida** verrouillée | Tooltip explicatif : « Disponible après que vous ayez déposé le lot sur Vigie-Chiro et reçu le CSV de résultats (24-48 h en moyenne) » |

---

## Variante - onglet Diagnostic actif

L'onglet Diagnostic regroupe ce qu'on extrait du `LogPR` et du `THLog` : graphes climatiques, batterie, évènements anormaux, et (si GPS connu) cohérence horaires astronomiques.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" role="img" aria-label="Maquette M-Passage - Onglet Diagnostic" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-active { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-inactive { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .tab-bottom { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-txt-active { font: 600 14px sans-serif; fill: #2c3e50; }
    .tab-txt-inactive { font: 14px sans-serif; fill: #6a737d; }
    .section-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .chart-area { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .chart-axis { stroke: #6a737d; stroke-width: 1; }
    .chart-grid { stroke: #d0d7de; stroke-width: 0.5; stroke-dasharray: 2 2; }
    .chart-line-temp { stroke: #c0392b; stroke-width: 2; fill: none; }
    .chart-line-hum { stroke: #2980b9; stroke-width: 2; fill: none; }
    .chart-label { font: 11px sans-serif; fill: #6a737d; }
    .chart-legend { font: 12px sans-serif; fill: #2c3e50; }
    .battery-bar-bg { fill: #eef2f5; stroke: #6a737d; stroke-width: 1; }
    .battery-bar-fill { fill: #1e8449; }
    .battery-label { font: 12px sans-serif; fill: #2c3e50; }
    .battery-num { font: 700 16px sans-serif; fill: #2c3e50; }
    .event-row { font: 12px sans-serif; fill: #2c3e50; }
    .event-time { font: 12px monospace; fill: #6a737d; }
    .event-warn { font: 14px sans-serif; fill: #b9770e; }
    .event-err { font: 14px sans-serif; fill: #a93226; }
    .astro-box { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .astro-box-warn { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .astro-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .astro-row { font: 12px sans-serif; fill: #2c3e50; }
    .astro-mono { font: 12px monospace; fill: #2c3e50; }
    .astro-status-ok { font: 600 13px sans-serif; fill: #1e6f3f; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
  </style>

  <rect x="10" y="10" width="1180" height="680" rx="4" class="frame"/>

  <!-- Onglets (Diagnostic actif) -->
  <rect x="40" y="20" width="200" height="36" rx="4" class="tab-inactive"/>
  <text x="140" y="43" class="tab-txt-inactive" text-anchor="middle">📋 Vue d'ensemble</text>
  <rect x="240" y="20" width="200" height="36" rx="4" class="tab-inactive"/>
  <text x="340" y="43" class="tab-txt-inactive" text-anchor="middle">🎧 Vérification</text>
  <rect x="440" y="20" width="200" height="36" rx="4" class="tab-active"/>
  <text x="540" y="43" class="tab-txt-active" text-anchor="middle">🩺 Diagnostic</text>
  <rect x="640" y="20" width="240" height="36" rx="4" class="tab-inactive"/>
  <text x="760" y="43" class="tab-txt-inactive" text-anchor="middle">✅ Validation Tadarida</text>

  <!-- Zone contenu -->
  <rect x="40" y="56" width="1120" height="610" rx="0" class="tab-bottom"/>
  <line x1="440" y1="56" x2="640" y2="56" stroke="#ffffff" stroke-width="2"/>

  <!-- Bouton Exporter en haut à droite -->
  <rect x="990" y="75" width="150" height="30" rx="4" class="btn-secondary"/>
  <text x="1065" y="95" class="btn-txt-dark" text-anchor="middle">📤 Exporter (CSV/PDF)</text>

  <!-- Section Climatique : T° et hygro -->
  <text x="60" y="95" class="section-title">🌡 Température et hygrométrie sur la nuit (THLog, 144 mesures)</text>

  <rect x="60" y="110" width="700" height="180" rx="4" class="chart-area"/>
  <line x1="100" y1="270" x2="740" y2="270" class="chart-axis"/>
  <line x1="100" y1="130" x2="100" y2="270" class="chart-axis"/>
  <!-- Grille -->
  <line x1="100" y1="150" x2="740" y2="150" class="chart-grid"/>
  <line x1="100" y1="190" x2="740" y2="190" class="chart-grid"/>
  <line x1="100" y1="230" x2="740" y2="230" class="chart-grid"/>
  <!-- Ligne de température (rouge, fictive) -->
  <polyline class="chart-line-temp" points="100,165 180,170 260,178 340,200 420,225 500,240 580,238 660,220 740,195"/>
  <!-- Ligne d'hygro (bleue, fictive) -->
  <polyline class="chart-line-hum" points="100,235 180,225 260,210 340,200 420,195 500,200 580,210 660,225 740,240"/>
  <!-- Légendes axes -->
  <text x="80" y="155" class="chart-label" text-anchor="end">25°</text>
  <text x="80" y="195" class="chart-label" text-anchor="end">20°</text>
  <text x="80" y="235" class="chart-label" text-anchor="end">15°</text>
  <text x="80" y="270" class="chart-label" text-anchor="end">10°</text>
  <text x="100" y="285" class="chart-label" text-anchor="middle">20:25</text>
  <text x="500" y="285" class="chart-label" text-anchor="middle">02:00</text>
  <text x="740" y="285" class="chart-label" text-anchor="middle">07:47</text>
  <!-- Légende -->
  <line x1="600" y1="125" x2="620" y2="125" class="chart-line-temp"/>
  <text x="625" y="129" class="chart-legend">T° (°C)</text>
  <line x1="680" y1="125" x2="700" y2="125" class="chart-line-hum"/>
  <text x="705" y="129" class="chart-legend">Humidité (%)</text>

  <!-- Section Batterie (à droite) -->
  <text x="785" y="95" class="section-title">🔋 Batterie</text>

  <rect x="785" y="110" width="355" height="180" rx="4" class="chart-area"/>
  <text x="805" y="135" class="battery-label">Tension au démarrage</text>
  <text x="1120" y="135" class="battery-num" text-anchor="end">8,1 V</text>
  <rect x="805" y="142" width="315" height="14" rx="3" class="battery-bar-bg"/>
  <rect x="805" y="142" width="270" height="14" rx="3" class="battery-bar-fill"/>

  <text x="805" y="180" class="battery-label">Tension à la mise en veille</text>
  <text x="1120" y="180" class="battery-num" text-anchor="end">7,4 V</text>
  <rect x="805" y="187" width="315" height="14" rx="3" class="battery-bar-bg"/>
  <rect x="805" y="187" width="200" height="14" rx="3" fill="#b9770e"/>

  <text x="805" y="225" class="battery-label">Delta sur la nuit</text>
  <text x="1120" y="225" class="battery-num" text-anchor="end">- 0,7 V</text>

  <text x="805" y="260" class="battery-label" font-style="italic" fill="#6a737d">Niveau acceptable. Surveiller la prochaine nuit.</text>

  <!-- Section Évènements anormaux -->
  <text x="60" y="320" class="section-title">⚠ Évènements anormaux du LogPR (3 détectés)</text>

  <rect x="60" y="335" width="700" height="160" rx="4" class="chart-area"/>
  <text x="80" y="358" class="event-warn">⚠</text>
  <text x="100" y="358" class="event-time">23:14:02</text>
  <text x="190" y="358" class="event-row">Wakeup non programmé (RTC drift suspecté)</text>

  <text x="80" y="385" class="event-warn">⚠</text>
  <text x="100" y="385" class="event-time">02:31:47</text>
  <text x="190" y="385" class="event-row">Erreur SD transitoire, retry succès (1 fichier rejoué)</text>

  <text x="80" y="412" class="event-err">❌</text>
  <text x="100" y="412" class="event-time">04:12:18</text>
  <text x="190" y="412" class="event-row">Redémarrage inopiné (cause : tension batterie 6,9 V brièvement, alerte critique)</text>

  <text x="80" y="445" class="event-row" font-style="italic" fill="#6a737d">Note : le journal capteur est circulaire ; des évènements antérieurs à 21:30 ont pu être effacés (R19).</text>

  <text x="80" y="475" class="event-row" fill="#6a737d">Comparer avec un passage précédent du même enregistreur :</text>
  <rect x="500" y="463" width="180" height="24" rx="3" class="btn-secondary"/>
  <text x="590" y="479" class="btn-txt-dark" text-anchor="middle">Sélectionner un passage...</text>

  <!-- Section Cohérence horaires (astro) -->
  <text x="785" y="320" class="section-title">🌅 Cohérence horaires (astronomique)</text>

  <rect x="785" y="335" width="355" height="160" rx="4" class="astro-box"/>
  <text x="805" y="360" class="astro-title">📍 Calculé pour 43.5298, 5.4474 (point A1)</text>

  <text x="805" y="386" class="astro-row">Coucher du soleil (22/06)</text>
  <text x="1120" y="386" class="astro-mono" text-anchor="end">21:35</text>

  <text x="805" y="408" class="astro-row">Lever du soleil (23/06)</text>
  <text x="1120" y="408" class="astro-mono" text-anchor="end">06:01</text>

  <text x="805" y="430" class="astro-row">Plage théorique (-30 / +30 min)</text>
  <text x="1120" y="430" class="astro-mono" text-anchor="end">21:05 → 06:31</text>

  <text x="805" y="452" class="astro-row">Plage effective (depuis LogPR)</text>
  <text x="1120" y="452" class="astro-mono" text-anchor="end">20:25 → 07:47</text>

  <text x="805" y="478" class="astro-status-ok">✓ Conforme (couverture suffisante : démarré 40 min en avance, arrêté 76 min après)</text>

  <!-- Pied de page diagnostic -->
  <text x="60" y="540" class="event-row" fill="#6a737d" font-style="italic">Note : ce diagnostic est calculé à la volée à partir des fichiers <code>LogPR1925492.txt</code> et <code>PaRecPR1925492_THLog.csv</code> archivés à l'import.</text>

  <text x="60" y="610" class="section-title">⚙ Cas particuliers gérés (R19, R20)</text>
  <text x="60" y="630" class="event-row">• Si le LogPR est saturé / circulaire → bandeau d'avertissement signalant la perte d'évènements antérieurs ([R19](../Modèle%20conceptuel/Règles%20métier.md#r19))</text>
  <text x="60" y="650" class="event-row">• Si le THLog est absent (sonde défaillante) → encart climatique remplacé par un message « Pas de relevé climatique » ([R20](../Modèle%20conceptuel/Règles%20métier.md#r20))</text>
</svg>
</div>

### Notes sur l'onglet Diagnostic

- **Graphe T°/hygro** : `LineChart` JavaFX à deux axes Y (T° à gauche, % humidité à droite). Les couleurs (rouge / bleu) sont conventionnelles pour ces grandeurs physiques.
- **Batterie** : visualisation simple en barres de progression. Le passage de vert (8,1 V) à orange (7,4 V) montre la dégradation. Un troisième seuil rouge (< 7 V) déclencherait une alerte critique.
- **Évènements anormaux** : liste chronologique avec icônes ⚠/❌ pour la gravité. Le timestamp en monospace facilite la lecture.
- **Cohérence horaires** : encart vert si conforme, orange si écart 5-30 min, rouge si écart > 30 min. Affiché uniquement si les coordonnées GPS du point sont saisies (cf. [E1.S3](../Story%20mapping/E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s3) et [E6.S3](../Story%20mapping/E6%20-%20Diagnostiquer%20le%20matériel.md#e6s3)). Sinon masqué avec un lien direct vers la fiche site pour saisir les coordonnées.
- **Bouton Exporter** : génère un CSV ou PDF du diagnostic ([E6.S5](../Story%20mapping/E6%20-%20Diagnostiquer%20le%20matériel.md#e6s5)) — utile pour SAV ou partage avec un fabricant.
- **Comparer avec un passage précédent** : ouvre une vue de comparaison côte à côte ([E6.S4](../Story%20mapping/E6%20-%20Diagnostiquer%20le%20matériel.md#e6s4)).

## Notes pour l'implémentation

- **Stepper de statut** : 6 cercles avec ligne de connexion. Le statut courant est calculé à partir de l'attribut workflow du passage en BD ([E0.S3](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s3)). La 6e étape est conditionnelle (cible étirée), à griser ou masquer selon disponibilité de E7.
- **Onglets** : composant standard JavaFX `TabPane`. Chaque onglet charge son contenu de manière paresseuse (lazy) pour éviter de calculer le diagnostic complet si l'utilisateur reste sur la vue d'ensemble.
- **Card Validation Tadarida verrouillée** : le verrou 🔒 doit être visuellement dissuasif mais le tooltip doit expliquer **comment** le déverrouiller (déposer le lot + recevoir le CSV).
- **Action « Modifier rattachement »** : implémente [E2.S8](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s8) — re-renomme tous les fichiers du passage. Confirmation forte obligatoire.
