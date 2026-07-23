# M-Import - Importer une nuit d'enregistrement

> **Type** : assistant d'import, ouvert par le bouton **« Importer une nuit »** d'un site (cf. [M-Site-detail](M-Site-detail.md)) ; il n'y a **pas** de carte d'import sur l'accueil, l'import part toujours d'un site déjà choisi.
> **Persona principal** : tous ([Marie](../Personas/Marie.md), [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md)). C'est l'écran le plus utilisé après la mise en route.
> **Parcours couverts** : [P2 - Importer une nuit d'enregistrement](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md).

C'est l'écran central de la chaîne de production. L'assistant est une **page unique en sections** (dossier source → inspection → rattachement → action), pas un wizard multi-étapes : l'utilisateur garde tout son contexte sous les yeux. Une **carte de rattachement** situe le site et le point choisis sur le carroyage.

## Maquette principale - cas standard (dossier inspecté, fichiers sans préfixe)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 1020" role="img" aria-label="Maquette M-Import - Importer une nuit (cas standard)" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 14px sans-serif; fill: #6a737d; }
    .step-num { fill: #4a90d9; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .step-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .section-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-readonly { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-mono { font: 12px monospace; fill: #2c3e50; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .field-required { font: 600 13px sans-serif; fill: #a93226; }
    .field-hint { font: 12px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .insp-row { font: 13px sans-serif; fill: #2c3e50; }
    .insp-mono { font: 12px monospace; fill: #2c3e50; }
    .auto-badge { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .auto-badge-txt { font: 600 11px sans-serif; fill: #2563a3; }
    .preview-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .preview-label { font: 600 12px sans-serif; fill: #5d4e00; }
    .preview-mono { font: 12px monospace; fill: #5d4e00; }
    .map-bg { fill: #e8efe6; stroke: #c9d6c5; stroke-width: 1; }
    .map-carre { fill: #c3caf0; stroke: #2c3e50; stroke-width: 1.2; }
    .map-point { fill: #1e8449; stroke: #ffffff; stroke-width: 1.5; }
    .map-label { font: 11px sans-serif; fill: #2c3e50; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="1000" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  Mes sites  ›  Carré 640380  ›  </text>
  <text x="566" y="38" class="crumb-active">Import</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="86" class="pagetitle">Importer une nuit d'enregistrement</text>
  <text x="40" y="108" class="pagesub">L'application copie les fichiers de la carte SD sans toucher aux originaux, les renomme et les transforme.</text>

  <!-- Section 1 : Dossier source -->
  <circle cx="62" cy="152" r="15" class="step-num"/>
  <text x="62" y="157" class="step-num-txt" text-anchor="middle">1</text>
  <text x="92" y="150" class="step-title">Dossier source</text>
  <text x="92" y="167" class="step-sub">Le dossier de votre carte SD, une copie sur disque, ou une archive .zip.</text>
  <rect x="40" y="182" width="1120" height="56" rx="4" class="section-card"/>
  <rect x="60" y="198" width="740" height="24" rx="3" class="field-readonly"/>
  <text x="72" y="214" class="field-mono">/media/marie/SDCARD/PR1925492/</text>
  <rect x="816" y="197" width="150" height="26" rx="3" class="btn-secondary"/>
  <text x="891" y="214" class="btn-txt-dark" text-anchor="middle">📂 Parcourir...</text>
  <rect x="978" y="197" width="160" height="26" rx="3" class="btn-secondary"/>
  <text x="1058" y="214" class="btn-txt-dark" text-anchor="middle">🗜 Choisir un .zip...</text>

  <!-- Section 2 : Inspection -->
  <circle cx="62" cy="278" r="15" class="step-num"/>
  <text x="62" y="283" class="step-num-txt" text-anchor="middle">2</text>
  <text x="92" y="276" class="step-title">Inspection du dossier</text>
  <text x="92" y="293" class="step-sub">Lecture seule : aucun fichier n'est modifié pour l'instant.</text>
  <rect x="40" y="308" width="1120" height="120" rx="4" class="section-card"/>
  <text x="60" y="334" class="check-ok">✓</text>
  <text x="80" y="334" class="insp-row">Journal du capteur : PR n° 1925492</text>
  <text x="430" y="334" class="step-sub">Fe 384 kHz · gain 16 dB · bande 8-120 kHz</text>
  <text x="60" y="358" class="check-ok">✓</text>
  <text x="80" y="358" class="insp-row">Relevé climatique détecté</text>
  <text x="430" y="358" class="step-sub">144 mesures (T° + hygro toutes les 600 s)</text>
  <text x="60" y="382" class="check-ok">✓</text>
  <text x="80" y="382" class="insp-row">1 572 enregistrement(s) WAV détecté(s)</text>
  <text x="430" y="382" class="step-sub">38,4 Go · plage 20:25 → 07:47</text>
  <text x="60" y="406" class="check-ok">✓</text>
  <text x="80" y="406" class="insp-row">État du nommage : fichiers bruts (seront renommés)</text>

  <!-- Section 3 : Rattachement -->
  <circle cx="62" cy="468" r="15" class="step-num"/>
  <text x="62" y="473" class="step-num-txt" text-anchor="middle">3</text>
  <text x="92" y="466" class="step-title">Rattachement de la nuit</text>
  <text x="92" y="483" class="step-sub">À quel site / point / passage du protocole cette nuit appartient-elle ?</text>
  <rect x="40" y="498" width="1120" height="300" rx="4" class="section-card"/>

  <text x="60" y="524" class="field-label">Site de suivi</text>
  <text x="142" y="524" class="field-required">*</text>
  <rect x="60" y="534" width="360" height="32" rx="3" class="field-input"/>
  <text x="74" y="555" class="field-text">Carré 640380 - Étang de la Tuilière</text>
  <text x="410" y="554" class="field-text" text-anchor="end">▾</text>

  <text x="700" y="524" class="field-label">Point d'écoute</text>
  <rect x="700" y="534" width="150" height="32" rx="3" class="field-input"/>
  <text x="714" y="555" class="field-text">A1</text>
  <text x="840" y="554" class="field-text" text-anchor="end">▾</text>

  <text x="870" y="524" class="field-label">Année</text>
  <rect x="870" y="534" width="110" height="32" rx="3" class="field-input"/>
  <text x="884" y="555" class="field-text">2026</text>

  <text x="1000" y="524" class="field-label">N° de passage</text>
  <rect x="1000" y="534" width="140" height="32" rx="3" class="field-input"/>
  <text x="1014" y="555" class="field-text">2</text>
  <rect x="60" y="572" width="200" height="18" rx="9" class="auto-badge"/>
  <text x="160" y="585" class="auto-badge-txt" text-anchor="middle">↺ dernier rattachement mémorisé</text>

  <!-- Carte de rattachement -->
  <text x="60" y="615" class="step-sub">Vérifiez l'emplacement : carré du site et point choisi (en indigo).</text>
  <rect x="60" y="624" width="1080" height="120" rx="4" class="map-bg"/>
  <rect x="470" y="640" width="200" height="88" class="map-carre"/>
  <circle cx="545" cy="694" r="6" class="map-point"/>
  <text x="556" y="690" class="map-label">A1</text>
  <text x="486" y="654" class="map-label">640380</text>

  <!-- Apercu du prefixe -->
  <rect x="60" y="752" width="1080" height="36" rx="4" class="preview-box"/>
  <text x="80" y="775" class="preview-label">Aperçu du préfixe appliqué : <tspan class="preview-mono">Car640380-2026-Pass2-A1-PaRecPR1925492_AAAAMMJJ_HHMMSS.wav</tspan></text>

  <!-- Actions -->
  <rect x="40" y="820" width="1120" height="56" rx="4" class="section-card"/>
  <rect x="60" y="832" width="120" height="32" rx="4" class="btn-secondary"/>
  <text x="120" y="853" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <text x="200" y="853" class="field-hint">Aucun fichier n'est modifié si vous annulez (R9).</text>
  <rect x="940" y="832" width="200" height="32" rx="4" class="btn-primary"/>
  <text x="1040" y="853" class="btn-txt" text-anchor="middle">📥 Importer cette nuit</text>

  <rect x="10" y="980" width="1180" height="30" class="footer"/>
  <text x="40" y="1000" class="footer-txt">VigieChiro Companion · la copie est vérifiée bit-à-bit (SHA-256, R9)</text>
</svg>
</div>

### Annotations

- **Section 1 (Dossier source)** : un dossier de carte SD, une copie disque, ou une **archive .zip** (décompressée d'abord). Le glisser-déposer d'un dossier ou d'un .zip n'importe où sur l'écran marche aussi.
- **Section 2 (Inspection)** : en **lecture seule**, l'application détecte le journal du capteur, le relevé climatique, les WAV et l'état du nommage. Rien n'est modifié.
- **Section 3 (Rattachement)** : site / point / année / n° de passage, **pré-remplis** depuis le dernier rattachement mémorisé pour cet enregistreur. La **carte** situe le carré et le point choisis (en indigo) ; un point sans GPS se place au centre de son carré ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26), [R27](../Modèle%20conceptuel/Règles%20métier.md#r27)).
- **Aperçu du préfixe** : montre le résultat exact appliqué aux fichiers ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6), [R7](../Modèle%20conceptuel/Règles%20métier.md#r7)), à vérifier avant de cliquer.

### Interactions clés

| Élément | Action |
|---|---|
| Bouton **📂 Parcourir** / **🗜 .zip** / glisser-déposer | Choisit la source ; l'inspection se relance (lecture seule) |
| Combobox **Site** : « + Créer un site » | Crée un site à la volée sans quitter l'import |
| Modification du n° de passage / du point | Met à jour l'aperçu du préfixe et la carte |
| Bouton **Annuler** | Revient en arrière ; aucun fichier touché ([R9](../Modèle%20conceptuel/Règles%20métier.md#r9)) |
| Bouton **📥 Importer cette nuit** | Lance copie vérifiée + renommage + transformation (variante « en cours » ci-dessous) |

---

## Variante - carte SD contenant plusieurs nuits

Quand la carte SD (ou l'archive) contient **plusieurs nuits** enregistrées à la suite (le cas de [Samuel](../Personas/Samuel.md), dont les enregistreurs tournent plusieurs nuits d'affilée), l'inspection les **détecte** et insère, sous la section « Dossier source », un **tableau des nuits détectées** : une ligne par nuit (date, plage horaire, nombre de fichiers, complétude), chacune cochable.

- Chaque nuit retenue devient un **passage distinct** ([E2.S9](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s9)) : le découpage suit la nuit **soir J → matin J+1** (bascule à midi).
- Une nuit **tronquée** (cycle mal terminé, SD pleine) est signalée avec son motif, sans être exclue d'office ; une nuit **déjà importée** est signalée (doublon) et décochée par défaut.
- Le **rattachement** (site, point, n° de passage) est demandé par nuit ; le n° de passage s'incrémente d'une nuit à l'autre.

Le reste de l'assistant (copie protégée, renommage, transformation, progression) est identique, appliqué à chaque nuit retenue.

## Variante - cas « fichiers déjà préfixés » (re-import ou dossier déjà nommé)

Quand l'inspection détecte que **tous les fichiers** portent déjà le préfixe `CarXXXXXX-AAAA-PassN-YY-`, le rattachement est **extrait** du préfixe et présélectionné. L'utilisateur valide tel quel (aucun re-renommage) ou modifie un champ.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 360" role="img" aria-label="Maquette M-Import - Cas déjà préfixé" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .step-num-extract { fill: #1e8449; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .step-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .section-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .field-extracted { fill: #d4edda; stroke: #1e8449; stroke-width: 1.5; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .extract-badge { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .extract-badge-txt { font: 600 11px sans-serif; fill: #1e6f3f; }
    .info-banner { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .info-banner-title { font: 600 14px sans-serif; fill: #1e6f3f; }
    .info-banner-txt { font: 13px sans-serif; fill: #1e6f3f; }
  </style>

  <rect x="10" y="10" width="1180" height="340" rx="4" class="frame"/>
  <rect x="40" y="28" width="1120" height="58" rx="4" class="info-banner"/>
  <text x="60" y="54" class="info-banner-title">✓ Préfixe Vigie-Chiro détecté sur tous les fichiers</text>
  <text x="60" y="76" class="info-banner-txt">Le rattachement (carré, année, n° de passage, point) a été extrait du préfixe. Validez tel quel (aucun re-renommage), ou modifiez un champ pour réaligner les noms.</text>

  <circle cx="62" cy="135" r="15" class="step-num-extract"/>
  <text x="62" y="140" class="step-num-txt" text-anchor="middle">3</text>
  <text x="92" y="133" class="step-title">Rattachement (extrait du préfixe)</text>

  <rect x="40" y="160" width="1120" height="150" rx="4" class="section-card"/>
  <text x="60" y="186" class="field-label">Site de suivi</text>
  <rect x="60" y="196" width="360" height="32" rx="3" class="field-extracted"/>
  <text x="74" y="217" class="field-text">Carré 640380 - Étang de la Tuilière</text>
  <rect x="60" y="234" width="170" height="18" rx="9" class="extract-badge"/>
  <text x="145" y="247" class="extract-badge-txt" text-anchor="middle">✓ extrait du préfixe</text>

  <text x="700" y="186" class="field-label">Point</text>
  <rect x="700" y="196" width="100" height="32" rx="3" class="field-extracted"/>
  <text x="714" y="217" class="field-text">B2</text>
  <text x="830" y="186" class="field-label">Année</text>
  <rect x="830" y="196" width="100" height="32" rx="3" class="field-extracted"/>
  <text x="844" y="217" class="field-text">2026</text>
  <text x="960" y="186" class="field-label">N° passage</text>
  <rect x="960" y="196" width="100" height="32" rx="3" class="field-extracted"/>
  <text x="974" y="217" class="field-text">1</text>

  <rect x="60" y="262" width="1000" height="32" rx="4" class="info-banner"/>
  <text x="80" y="283" class="info-banner-txt">✓ Aucun re-renommage : les fichiers gardent leur nom. Seules la copie vérifiée et la transformation seront exécutées.</text>
</svg>
</div>

### Notes sur le cas « déjà préfixés »

- Les **4 champs sont en vert** avec un badge « extrait du préfixe » ; l'utilisateur reste libre de modifier.
- L'**aperçu du préfixe** est remplacé par un encart « Aucun re-renommage à effectuer ».
- Si l'utilisateur **modifie un champ**, un dialog propose : « Réaligner les noms de fichiers sur la nouvelle saisie » ou « Restaurer les valeurs extraites ».

---

## Variante - import en cours (copie + transformation)

Après le clic sur **📥 Importer cette nuit**, l'écran passe en mode progression. Une barre **déterminée** (« X / N fichiers ») affiche un **temps restant estimé**, et un bouton **Annuler** est disponible (le formulaire est gelé).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 500" role="img" aria-label="Maquette M-Import - Import en cours" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .pagetitle { font: 700 20px sans-serif; fill: #2c3e50; }
    .pagesub { font: 14px sans-serif; fill: #6a737d; }
    .step-card-done { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .step-card-current { fill: #ffffff; stroke: #4a90d9; stroke-width: 2; }
    .step-num-done { fill: #1e8449; }
    .step-num-current { fill: #4a90d9; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .step-title-done { font: 600 16px sans-serif; fill: #1e6f3f; }
    .step-title-active { font: 600 16px sans-serif; fill: #2c3e50; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .step-detail-mono { font: 12px monospace; fill: #2c3e50; }
    .progress-bg { fill: #eef2f5; }
    .progress-bar { fill: #4a90d9; }
    .progress-bar-done { fill: #1e8449; }
    .progress-pct { font: 600 14px sans-serif; fill: #2c3e50; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-cancel { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-cancel-txt { fill: #a93226; font: 600 13px sans-serif; }
  </style>

  <rect x="10" y="10" width="1180" height="480" rx="4" class="frame"/>
  <text x="40" y="48" class="pagetitle">Import en cours - Carré 640380 / A1 / Passage 2</text>
  <text x="40" y="72" class="pagesub">Vous pouvez fermer cette fenêtre : l'import continue en arrière-plan.</text>

  <!-- Copie protegee (terminee) -->
  <rect x="40" y="96" width="1120" height="92" rx="6" class="step-card-done"/>
  <circle cx="72" cy="130" r="15" class="step-num-done"/>
  <text x="72" y="135" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="106" y="126" class="step-title-done">Copie protégée et vérifiée (SHA-256)</text>
  <text x="106" y="144" class="step-sub">1 572 fichiers · 38,4 Go · ✓ aucune écriture sur la SD source (R9)</text>
  <rect x="106" y="156" width="1020" height="12" rx="6" class="progress-bg"/>
  <rect x="106" y="156" width="1020" height="12" rx="6" class="progress-bar-done"/>
  <text x="1140" y="167" class="progress-pct" text-anchor="end">100 %</text>

  <!-- Renommage (termine) -->
  <rect x="40" y="200" width="1120" height="56" rx="6" class="step-card-done"/>
  <circle cx="72" cy="228" r="15" class="step-num-done"/>
  <text x="72" y="233" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="106" y="223" class="step-title-done">Renommage selon le préfixe Vigie-Chiro</text>
  <text x="106" y="241" class="step-sub">1 572 fichiers renommés en Car640380-2026-Pass2-A1-...</text>
  <text x="1140" y="241" class="step-sub" text-anchor="end">100 %</text>

  <!-- Transformation (en cours) -->
  <rect x="40" y="268" width="1120" height="130" rx="6" class="step-card-current"/>
  <circle cx="72" cy="306" r="15" class="step-num-current"/>
  <text x="72" y="311" class="step-num-txt" text-anchor="middle">3</text>
  <text x="106" y="301" class="step-title-active">Transformation : découpage 5 s réelles + expansion ×10</text>
  <text x="106" y="319" class="step-sub">Fichier 743 / 1 572 · 7 min 32 s écoulées · temps restant estimé ~9 min</text>
  <rect x="106" y="332" width="1020" height="12" rx="6" class="progress-bg"/>
  <rect x="106" y="332" width="483" height="12" rx="6" class="progress-bar"/>
  <text x="1140" y="343" class="progress-pct" text-anchor="end">47 %</text>
  <text x="106" y="368" class="step-detail-mono">→ Car640380-2026-Pass2-A1-PaRecPR1925492_20260622_223415.wav</text>
  <text x="106" y="386" class="step-sub">1 706 séquences produites sur ~3 614 prévues</text>

  <rect x="40" y="424" width="170" height="34" rx="4" class="btn-secondary"/>
  <text x="125" y="446" class="btn-txt-dark" text-anchor="middle">Fermer la fenêtre</text>
  <text x="230" y="446" class="step-sub">L'import continue en arrière-plan ; notification à la fin.</text>
  <rect x="1010" y="424" width="130" height="34" rx="4" class="btn-cancel"/>
  <text x="1075" y="446" class="btn-cancel-txt" text-anchor="middle">⏹ Annuler l'import</text>
</svg>
</div>

### Notes sur la progression

- **Étapes** : copie vérifiée → renommage → transformation. La barre est **déterminée** (« X / N ») avec **temps restant estimé**.
- **Fermer la fenêtre** : l'import continue en arrière-plan (le passage prendra le statut `Transformé`) ; **Annuler** interrompt et la session précédente est **restaurée** (remplacement atomique, [R29](../Modèle%20conceptuel/Règles%20métier.md#r29)).
- **Import résilient** ([R29](../Modèle%20conceptuel/Règles%20métier.md#r29)) : un fichier source illisible (en-tête corrompu, fréquence non divisible par 10) est **rejeté individuellement** et consigné dans un **rapport** (importés / rejetés / non pertinents) à la fin, sans interrompre les autres.

## Avertissements d'inspection (non bloquants)

À l'inspection, l'application **signale sans bloquer** ([R30](../Modèle%20conceptuel/Règles%20métier.md#r30)) ; l'utilisateur décide de poursuivre :

- **Mélange** : le dossier contient des fichiers de **plusieurs enregistreurs**. Un bandeau l'indique ; l'import reste possible.
- **Incohérence** : le journal du capteur (n° de série, nuit) **contredit** les WAV (autre série / autre date). Bandeau rouge non bloquant.
- **Nuit déjà importée** : un passage existe déjà pour le même enregistreur et la même date. Réimporter crée simplement un nouveau passage (autre point / autre numéro), ce que l'utilisateur peut vouloir ou non.

## Notes pour l'implémentation

- **Threading** : copie et transformation en **arrière-plan**, l'IHM ne gèle jamais ; la progression est mise à jour sur le fil JavaFX.
- **Volumétrie** : tenir 40 Go sans freeze (cas Samuel).
- **Remplacement atomique** ([R29](../Modèle%20conceptuel/Règles%20métier.md#r29)) : un import qui échoue (annulation, tous les WAV rejetés, erreur disque) **restaure** la session précédente ; rien n'est perdu.
- **N° de passage** : l'auto-incrément (max+1 pour ce point/année) est calculé à l'ouverture de l'écran.
