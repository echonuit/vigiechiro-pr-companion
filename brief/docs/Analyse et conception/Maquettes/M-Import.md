# M-Import - Importer une nuit de capture

> **Type** : assistant d'import (action « Importer une nuit » depuis la nav).
> **Persona principal** : tous ([Marie](../Personas/Marie.md), [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md)). C'est l'écran le plus utilisé après la mise en route initiale.
> **Parcours couverts** : [P2 - Importer une nuit de capture](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20de%20capture.md).
> **Stories couvertes** : [E2.S1 - Inspecter dossier](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1), [E2.S2 - Rattacher (sans préfixe)](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s2), [E2.S3 - Extraire (déjà préfixé)](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s3), [E2.S4 - Copie protégée](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s4), [E2.S5 - Renommage](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s5), [E2.S6 - Transformation](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6), [E2.S7 - Mémoriser association](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s7).

C'est l'écran central de la chaîne fil rouge. L'assistant est conçu comme une **page unique en 4 sections** (dossier source → inspection → rattachement → action), pas comme un wizard multi-étapes, pour que l'utilisateur garde tout son contexte sous les yeux et puisse revenir en arrière sans naviguer entre écrans.

## Wireframe principal - cas standard (dossier inspecté, fichiers sans préfixe)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" role="img" aria-label="Maquette M-Import - Importer une nuit (cas standard)" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .topnav { fill: #34495e; }
    .navtxt-active { fill: #ffffff; font: 600 13px sans-serif; }
    .navtxt-inactive { fill: #bdc3c7; font: 400 13px sans-serif; }
    .navunder { stroke: #4a90d9; stroke-width: 3; fill: none; }
    .breadcrumb { font: 13px sans-serif; fill: #4a90d9; }
    .breadcrumb-sep { font: 13px sans-serif; fill: #6a737d; }
    .breadcrumb-curr { font: 13px sans-serif; fill: #2c3e50; }
    .pagetitle { font: 700 24px sans-serif; fill: #2c3e50; }
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
    .field-placeholder { font: 13px sans-serif; fill: #bdc3c7; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .field-required { font: 600 13px sans-serif; fill: #a93226; }
    .field-hint { font: 12px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .check-warn { font: 14px sans-serif; fill: #b9770e; }
    .insp-row { font: 13px sans-serif; fill: #2c3e50; }
    .insp-mono { font: 12px monospace; fill: #2c3e50; }
    .auto-badge { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .auto-badge-txt { font: 600 11px sans-serif; fill: #2563a3; }
    .preview-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .preview-label { font: 600 12px sans-serif; fill: #5d4e00; }
    .preview-mono { font: 12px monospace; fill: #5d4e00; }
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
  <text x="170" y="67" class="navtxt-active">📥 Importer une nuit</text>
  <line x1="162" y1="78" x2="320" y2="78" class="navunder"/>
  <text x="330" y="67" class="navtxt-inactive">📊 Vue tabulaire</text>
  <text x="470" y="67" class="navtxt-inactive">⚙ Paramètres</text>
  <text x="1140" y="67" class="navtxt-inactive" text-anchor="end">👤 Local</text>

  <text x="40" y="108" class="breadcrumb">‹ Mes sites</text>
  <text x="125" y="108" class="breadcrumb-sep">›</text>
  <text x="140" y="108" class="breadcrumb-curr">Importer une nuit</text>

  <text x="40" y="148" class="pagetitle">Importer une nuit de capture</text>
  <text x="40" y="172" class="pagesub">L'application copie les fichiers depuis votre carte SD sans toucher aux originaux, les renomme et les transforme.</text>

  <!-- ============ Étape 1 : Dossier source ============ -->
  <circle cx="62" cy="218" r="16" class="step-num"/>
  <text x="62" y="223" class="step-num-txt" text-anchor="middle">1</text>
  <text x="92" y="215" class="step-title">Dossier source</text>
  <text x="92" y="232" class="step-sub">Le dossier de votre carte SD ou un dossier déjà copié sur disque.</text>

  <rect x="40" y="248" width="1120" height="62" rx="4" class="section-card"/>
  <text x="60" y="270" class="field-label">Chemin du dossier</text>
  <rect x="60" y="278" width="900" height="22" rx="3" class="field-readonly"/>
  <text x="72" y="294" class="field-mono">/media/marie/SDCARD/PR1925492/</text>
  <rect x="980" y="276" width="160" height="26" rx="3" class="btn-secondary"/>
  <text x="1060" y="293" class="btn-txt-dark" text-anchor="middle">📂 Parcourir...</text>

  <!-- ============ Étape 2 : Inspection ============ -->
  <circle cx="62" cy="345" r="16" class="step-num"/>
  <text x="62" y="350" class="step-num-txt" text-anchor="middle">2</text>
  <text x="92" y="342" class="step-title">Inspection du dossier</text>
  <text x="92" y="359" class="step-sub">Lecture seule — aucun fichier n'est modifié pour l'instant.</text>

  <rect x="40" y="375" width="1120" height="155" rx="4" class="section-card"/>

  <text x="60" y="400" class="check-ok">✓</text>
  <text x="80" y="400" class="insp-row">Journal du capteur détecté :</text>
  <text x="270" y="400" class="insp-mono">LogPR1925492.txt</text>
  <text x="510" y="400" class="step-sub">PR n° 1925492 · Fe 384 kHz · gain 16 dB · bande 8-120 kHz</text>

  <text x="60" y="425" class="check-ok">✓</text>
  <text x="80" y="425" class="insp-row">Relevé climatique détecté :</text>
  <text x="270" y="425" class="insp-mono">PaRecPR1925492_THLog.csv</text>
  <text x="510" y="425" class="step-sub">144 mesures (T° + hygro toutes les 600 s)</text>

  <text x="60" y="450" class="check-ok">✓</text>
  <text x="80" y="450" class="insp-row">Enregistrements WAV détectés :</text>
  <text x="290" y="450" class="insp-mono">1 572 fichiers</text>
  <text x="420" y="450" class="step-sub">38,4 Go au total · plage horaire 20:25 → 07:47</text>

  <text x="60" y="475" class="check-ok">✓</text>
  <text x="80" y="475" class="insp-row">État du nommage :</text>
  <text x="220" y="475" class="insp-row" font-weight="600">sans préfixe</text>
  <text x="320" y="475" class="step-sub">(cas standard — les fichiers seront renommés à l'étape 4)</text>

  <text x="60" y="500" class="check-warn">ⓘ</text>
  <text x="80" y="500" class="step-sub">Le quadruplet (carré, année, n° passage, point) sera utilisé pour générer le préfixe.</text>

  <text x="60" y="518" class="check-warn">ⓘ</text>
  <text x="80" y="518" class="step-sub">Volume estimé après transformation ×10 + chunks 5 s : environ 3 614 séquences (~17 Go).</text>

  <!-- ============ Étape 3 : Rattachement ============ -->
  <circle cx="62" cy="565" r="16" class="step-num"/>
  <text x="62" y="570" class="step-num-txt" text-anchor="middle">3</text>
  <text x="92" y="562" class="step-title">Rattachement de la nuit</text>
  <text x="92" y="579" class="step-sub">Indiquez à quelle session du protocole cette nuit appartient.</text>

  <rect x="40" y="595" width="1120" height="200" rx="4" class="section-card"/>

  <!-- Site -->
  <text x="60" y="620" class="field-label">Site de suivi</text>
  <text x="142" y="620" class="field-required">*</text>
  <rect x="60" y="630" width="320" height="34" rx="3" class="field-input"/>
  <text x="74" y="652" class="field-text">🌐 Carré 640380 — Étang de la Tuilière</text>
  <text x="370" y="650" class="field-text" text-anchor="end">▾</text>
  <rect x="60" y="668" width="200" height="20" rx="10" class="auto-badge"/>
  <text x="160" y="683" class="auto-badge-txt" text-anchor="middle">↺ dernier site PR 1925492</text>

  <!-- Point -->
  <text x="400" y="620" class="field-label">Point d'écoute</text>
  <text x="488" y="620" class="field-required">*</text>
  <rect x="400" y="630" width="220" height="34" rx="3" class="field-input"/>
  <text x="414" y="652" class="field-text">📍 A1</text>
  <text x="610" y="650" class="field-text" text-anchor="end">▾</text>
  <rect x="400" y="668" width="200" height="20" rx="10" class="auto-badge"/>
  <text x="500" y="683" class="auto-badge-txt" text-anchor="middle">↺ dernier point pour ce PR</text>

  <!-- Année -->
  <text x="640" y="620" class="field-label">Année</text>
  <rect x="640" y="630" width="120" height="34" rx="3" class="field-input"/>
  <text x="654" y="652" class="field-text">2026</text>
  <text x="750" y="650" class="field-text" text-anchor="end">▾</text>

  <!-- N° passage -->
  <text x="780" y="620" class="field-label">N° de passage</text>
  <text x="884" y="620" class="field-required">*</text>
  <rect x="780" y="630" width="160" height="34" rx="3" class="field-input"/>
  <text x="794" y="652" class="field-text">2</text>
  <text x="930" y="650" class="field-text" text-anchor="end">▴▾</text>
  <rect x="780" y="668" width="160" height="20" rx="10" class="auto-badge"/>
  <text x="860" y="683" class="auto-badge-txt" text-anchor="middle">⚙ auto-incrément (max+1)</text>

  <!-- Action ajouter site -->
  <rect x="970" y="630" width="170" height="34" rx="3" class="btn-secondary"/>
  <text x="1055" y="651" class="btn-txt-dark" text-anchor="middle">+ Créer un site</text>

  <!-- Aperçu du préfixe généré -->
  <rect x="60" y="710" width="1080" height="65" rx="4" class="preview-box"/>
  <text x="80" y="731" class="preview-label">📝 Aperçu du préfixe qui sera appliqué aux 1 572 fichiers :</text>
  <text x="80" y="754" class="preview-mono">Car640380-2026-Pass2-A1-PaRecPR1925492_AAAAMMJJ_HHMMSS.wav</text>
  <text x="80" y="770" class="step-sub">Tirets « du 6 » (U+002D HYPHEN-MINUS), suffixe original conservé (R6, R7).</text>

  <!-- ============ Étape 4 : Boutons ============ -->
  <rect x="40" y="815" width="1120" height="60" rx="4" class="section-card"/>
  <rect x="60" y="827" width="120" height="36" rx="4" class="btn-secondary"/>
  <text x="120" y="850" class="btn-txt-dark" text-anchor="middle">Annuler</text>

  <text x="200" y="850" class="field-hint">Aucun fichier ne sera modifié si vous annulez maintenant (R9).</text>

  <rect x="940" y="827" width="200" height="36" rx="4" class="btn-primary"/>
  <text x="1040" y="850" class="btn-txt" text-anchor="middle">📥 Importer cette nuit</text>
</svg>
</div>

### Annotations

- **Étape 1 (Dossier source)** : le chemin sélectionné est en lecture seule (style `.field-readonly`). Le bouton « Parcourir » ouvre le sélecteur natif de l'OS. Drag-and-drop d'un dossier sur cette zone marche aussi (non figuré ici).
- **Étape 2 (Inspection)** : 4 lignes ✓ pour les éléments détectés + 2 lignes ⓘ d'info contextuelle. Tout est en lecture seule, aucune action requise.
- **Étape 3 (Rattachement)** : les 3 badges bleus `↺ dernier site/point pour ce PR` et `⚙ auto-incrément` montrent que **tous les champs sont préremplis** grâce à E2.S7 (mémoire) et E2.S2 (auto-incrément). L'utilisateur valide en un clic dans le cas nominal.
- **Aperçu du préfixe** (encart jaune) : montre **le résultat exact** qui sera appliqué aux fichiers, ce qui permet à l'utilisateur de vérifier visuellement avant de cliquer Importer.
- **Bouton Importer** : libellé explicite « Importer cette nuit » plutôt que générique « Valider ».

### Interactions clés

| Élément | Action |
|---|---|
| Bouton « 📂 Parcourir... » | Ouvre le sélecteur de dossier natif |
| Dossier change → re-déclenche l'inspection | Lecture seule, recharge tout l'écran avec les nouveaux résultats |
| Combobox **Site** : option « + Créer un site » | Ouvre la modale d'édition d'un site (cf. [M-Site-detail](M-Site-detail.md)) sans fermer cet écran ([E1.S5](../Story%20mapping/E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s5)) |
| Modification du n° de passage | Met à jour l'aperçu du préfixe en temps réel |
| Bouton **Annuler** | Retour à l'écran précédent ([M-Sites](M-Sites.md) ou autre). R9 : aucun fichier touché. |
| Bouton **📥 Importer cette nuit** | Lance copie + renommage + transformation. Bascule sur la variante « progression » ci-dessous. |

---

## Variante - cas « fichiers déjà préfixés » (re-import ou dossier ex-LupasRename)

Quand l'inspection détecte que **tous les fichiers** ont déjà le préfixe `CarXXXXXX-AAAA-PassN-YY-`, le quadruplet est extrait automatiquement et présélectionné. L'utilisateur peut valider tel quel (pas de re-renommage) ou modifier un champ (déclenche le scénario d'incohérence).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 480" role="img" aria-label="Maquette M-Import - Cas déjà préfixé" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .step-num-extract { fill: #1e8449; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .step-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .section-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-extracted { fill: #d4edda; stroke: #1e8449; stroke-width: 1.5; }
    .field-text { font: 13px sans-serif; fill: #2c3e50; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .extract-badge { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .extract-badge-txt { font: 600 11px sans-serif; fill: #1e6f3f; }
    .info-banner { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .info-banner-title { font: 600 14px sans-serif; fill: #1e6f3f; }
    .info-banner-txt { font: 13px sans-serif; fill: #1e6f3f; }
    .insp-row { font: 13px sans-serif; fill: #2c3e50; }
    .insp-mono { font: 12px monospace; fill: #2c3e50; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .preview-box-ok { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .preview-label-ok { font: 600 12px sans-serif; fill: #1e6f3f; }
    .preview-mono-ok { font: 12px monospace; fill: #1e6f3f; }
  </style>

  <rect x="10" y="10" width="1180" height="460" rx="4" class="frame"/>

  <!-- Bandeau d'info "préfixe détecté" -->
  <rect x="40" y="30" width="1120" height="60" rx="4" class="info-banner"/>
  <text x="60" y="56" class="info-banner-title">✓ Préfixe Vigie-Chiro détecté sur tous les fichiers</text>
  <text x="60" y="78" class="info-banner-txt">Le quadruplet (carré, année, n° passage, point) a été extrait du préfixe et présélectionné ci-dessous. Vous pouvez valider tel quel sans re-renommage, ou modifier un champ pour réaligner les noms.</text>

  <!-- Étape 2 partielle : ligne nommage -->
  <text x="60" y="120" class="check-ok">✓</text>
  <text x="80" y="120" class="insp-row">État du nommage :</text>
  <text x="220" y="120" class="insp-row" font-weight="600">tous fichiers déjà préfixés</text>
  <text x="430" y="120" class="step-sub">— extrait :</text>
  <text x="510" y="120" class="insp-mono">Car640380-2026-Pass1-B2-...</text>

  <!-- Étape 3 : champs présélectionnés en vert -->
  <circle cx="62" cy="170" r="16" class="step-num-extract"/>
  <text x="62" y="175" class="step-num-txt" text-anchor="middle">3</text>
  <text x="92" y="167" class="step-title">Rattachement (extrait du préfixe)</text>

  <rect x="40" y="195" width="1120" height="170" rx="4" class="section-card"/>

  <text x="60" y="220" class="field-label">Site de suivi</text>
  <rect x="60" y="230" width="320" height="34" rx="3" class="field-extracted"/>
  <text x="74" y="252" class="field-text">🌐 Carré 640380 — Étang de la Tuilière</text>
  <text x="370" y="250" class="field-text" text-anchor="end">▾</text>
  <rect x="60" y="268" width="180" height="20" rx="10" class="extract-badge"/>
  <text x="150" y="283" class="extract-badge-txt" text-anchor="middle">✓ extrait du préfixe</text>

  <text x="400" y="220" class="field-label">Point d'écoute</text>
  <rect x="400" y="230" width="220" height="34" rx="3" class="field-extracted"/>
  <text x="414" y="252" class="field-text">📍 B2</text>
  <text x="610" y="250" class="field-text" text-anchor="end">▾</text>
  <rect x="400" y="268" width="180" height="20" rx="10" class="extract-badge"/>
  <text x="490" y="283" class="extract-badge-txt" text-anchor="middle">✓ extrait du préfixe</text>

  <text x="640" y="220" class="field-label">Année</text>
  <rect x="640" y="230" width="120" height="34" rx="3" class="field-extracted"/>
  <text x="654" y="252" class="field-text">2026</text>
  <rect x="640" y="268" width="120" height="20" rx="10" class="extract-badge"/>
  <text x="700" y="283" class="extract-badge-txt" text-anchor="middle">✓ extrait</text>

  <text x="780" y="220" class="field-label">N° de passage</text>
  <rect x="780" y="230" width="160" height="34" rx="3" class="field-extracted"/>
  <text x="794" y="252" class="field-text">1</text>
  <rect x="780" y="268" width="160" height="20" rx="10" class="extract-badge"/>
  <text x="860" y="283" class="extract-badge-txt" text-anchor="middle">✓ extrait</text>

  <!-- Aperçu : pas de re-renommage -->
  <rect x="60" y="305" width="1080" height="50" rx="4" class="preview-box-ok"/>
  <text x="80" y="326" class="preview-label-ok">✓ Aucun re-renommage à effectuer</text>
  <text x="80" y="345" class="info-banner-txt">Les fichiers gardent leur nom actuel. Seules les étapes copie protégée + transformation seront exécutées.</text>

  <!-- Bouton -->
  <rect x="940" y="395" width="200" height="36" rx="4" fill="#4a90d9" stroke="#2563a3" stroke-width="1"/>
  <text x="1040" y="418" class="extract-badge-txt" fill="#ffffff" text-anchor="middle" font-size="13">📥 Importer cette nuit</text>
</svg>
</div>

### Notes sur le cas « déjà préfixés »

- **Bandeau vert** en haut explique clairement que les valeurs ont été extraites du préfixe.
- Les **4 champs sont en vert** (`.field-extracted`) avec un badge `✓ extrait du préfixe` pour signaler l'auto-pré-remplissage. L'utilisateur reste libre de modifier.
- L'**aperçu du préfixe** est remplacé par un encart vert « Aucun re-renommage à effectuer ».
- Si l'utilisateur **modifie un champ**, l'application bascule sur le scénario **incohérence préfixe ↔ saisie** (cf. [E2.S3](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s3)) et propose un dialog : « Réaligner les noms de fichiers sur la nouvelle saisie » ou « Restaurer les valeurs extraites ».

---

## Variante - import en cours (copie + transformation)

Après le clic sur **📥 Importer cette nuit**, l'écran bascule en mode progression. Les 3 étapes (copie, renommage, transformation) sont visibles avec leur avancement détaillé.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 540" role="img" aria-label="Maquette M-Import - Import en cours" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 14px sans-serif; fill: #6a737d; }
    .step-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .step-card-current { fill: #ffffff; stroke: #4a90d9; stroke-width: 2; }
    .step-card-done { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .step-num-pending { fill: #6a737d; }
    .step-num-current { fill: #4a90d9; }
    .step-num-done { fill: #1e8449; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .step-title-active { font: 600 16px sans-serif; fill: #2c3e50; }
    .step-title-done { font: 600 16px sans-serif; fill: #1e6f3f; }
    .step-title-pending { font: 600 16px sans-serif; fill: #6a737d; }
    .step-sub { font: 12px sans-serif; fill: #6a737d; }
    .step-detail-mono { font: 12px monospace; fill: #2c3e50; }
    .progress-bg { fill: #eef2f5; }
    .progress-bar { fill: #4a90d9; }
    .progress-bar-done { fill: #1e8449; }
    .progress-pct { font: 600 14px sans-serif; fill: #2c3e50; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-cancel { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-cancel-txt { fill: #a93226; font: 600 13px sans-serif; }
  </style>

  <rect x="10" y="10" width="1180" height="520" rx="4" class="frame"/>

  <text x="40" y="50" class="pagetitle">Import en cours - Carré 640380 / A1 / Passage 2</text>
  <text x="40" y="74" class="pagesub">Vous pouvez fermer cette fenêtre, l'import continue en arrière-plan.</text>

  <!-- Étape A : Copie protégée (terminée) -->
  <rect x="40" y="100" width="1120" height="100" rx="6" class="step-card-done"/>
  <circle cx="72" cy="135" r="16" class="step-num-done"/>
  <text x="72" y="140" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="106" y="130" class="step-title-done">Copie protégée des fichiers depuis la SD</text>
  <text x="106" y="148" class="step-sub">1 572 fichiers · 38,4 Go · 2 min 14 s écoulées · ✓ Aucune écriture sur la SD source (R9)</text>

  <rect x="106" y="160" width="1020" height="14" rx="7" class="progress-bg"/>
  <rect x="106" y="160" width="1020" height="14" rx="7" class="progress-bar-done"/>
  <text x="1140" y="172" class="progress-pct" text-anchor="end">100 %</text>

  <!-- Étape B : Renommage (terminée) -->
  <rect x="40" y="215" width="1120" height="65" rx="6" class="step-card-done"/>
  <circle cx="72" cy="247" r="16" class="step-num-done"/>
  <text x="72" y="252" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="106" y="242" class="step-title-done">Renommage selon le préfixe Vigie-Chiro</text>
  <text x="106" y="260" class="step-sub">1 572 fichiers renommés en `Car640380-2026-Pass2-A1-...` · 8 s écoulées</text>
  <text x="1140" y="260" class="step-sub" text-anchor="end">100 %</text>

  <!-- Étape C : Transformation (en cours) -->
  <rect x="40" y="295" width="1120" height="125" rx="6" class="step-card-current"/>
  <circle cx="72" cy="333" r="16" class="step-num-current"/>
  <text x="72" y="338" class="step-num-txt" text-anchor="middle">3</text>
  <text x="106" y="328" class="step-title-active">Transformation : expansion ×10 + découpage 5 s</text>
  <text x="106" y="346" class="step-sub">En cours sur le fichier 743 / 1 572 · 7 min 32 s écoulées · ETA 9 min 18 s</text>

  <rect x="106" y="358" width="1020" height="14" rx="7" class="progress-bg"/>
  <rect x="106" y="358" width="483" height="14" rx="7" class="progress-bar"/>
  <text x="1140" y="370" class="progress-pct" text-anchor="end">47 %</text>

  <text x="106" y="394" class="step-detail-mono">→ Car640380-2026-Pass2-A1-PaRecPR1925492_20260622_223415.wav</text>
  <text x="106" y="412" class="step-sub">2 séquences produites · 1 706 séquences au total jusqu'ici sur 3 614 prévues</text>

  <!-- Boutons -->
  <rect x="40" y="450" width="160" height="36" rx="4" class="btn-secondary"/>
  <text x="120" y="473" class="btn-txt-dark" text-anchor="middle">Fermer la fenêtre</text>
  <text x="220" y="473" class="step-sub">L'import continue en arrière-plan, vous serez notifié à la fin.</text>

  <rect x="1010" y="450" width="130" height="36" rx="4" class="btn-cancel"/>
  <text x="1075" y="473" class="btn-cancel-txt" text-anchor="middle">⏹ Annuler l'import</text>
</svg>
</div>

### Notes sur la progression

- **3 cards verticales** : copie / renommage / transformation. Les terminées sont vert clair (`.step-card-done`), celle en cours est bordée bleu (`.step-card-current`), les futures (non figurées) seraient grises.
- **Barre de progression détaillée** sur l'étape en cours : % global, fichier en cours (chemin tronqué), nombre de séquences déjà produites, ETA.
- **Bouton « Fermer la fenêtre »** : l'import continue en arrière-plan, l'utilisateur peut faire autre chose dans l'application. À la fin, une notification apparaît (et le passage prend le statut `Transformé`).
- **Bouton « Annuler l'import »** : confirmation forte (modal non figurée) car cela rollback ce qui a été fait — copie supprimée, transformation interrompue.

## Cas non figurés (documentés textuellement)

### Cas « mélange » (dossier corrompu)

Si l'inspection (étape 2) détecte un **mélange** de fichiers préfixés et non préfixés, le bouton **Importer** est désactivé et un message d'erreur explicite s'affiche en haut de la modale :

> ⚠ Le dossier contient un mélange de fichiers nommés et non nommés. L'application ne peut pas l'importer en l'état. Nettoyez le dossier puis réessayez.

L'utilisateur a alors deux options :

1. **Annuler** et aller nettoyer le dossier manuellement.
2. **Forcer un re-renommage uniforme** (action explicite « Re-renommer tous les fichiers selon ma saisie ») — équivaut au cas standard mais sur un dossier mixte.

### Cas « incohérence préfixe ↔ saisie »

Si l'utilisateur a modifié l'un des 4 champs après extraction automatique (variante « déjà préfixés »), un **dialog de confirmation** s'ouvre au clic sur Importer :

> ⚠ Vous avez modifié le rattachement extrait du préfixe.
>
> Les noms actuels disent : `Car640380-2026-Pass1-B2-...`
> Votre saisie indique : `Carré 640380 / Point B2 / Pass **2**`
>
> Que souhaitez-vous faire ?
>
> - **Réaligner les noms de fichiers sur ma saisie** (re-renomme les 1 572 fichiers en `Car640380-2026-Pass2-B2-...`)
> - **Restaurer les valeurs extraites du préfixe** (revient à `Pass 1`, aucun re-renommage)

## Notes pour l'implémentation

- **Threading** : la copie et la transformation doivent être en **arrière-plan** (`Task<Void>` JavaFX ou équivalent), avec callbacks Platform.runLater pour mettre à jour la barre de progression.
- **Volumétrie** : sur 40 Go, l'utilisateur ne doit jamais voir l'IHM freezer. Tester avec un dossier de 5+ Go avant de soutenir 40 Go.
- **Reprise** : si l'application crashe en cours d'import, [E0.S6](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s6) doit pouvoir reprendre au démarrage suivant. La file d'attente est persistée à chaque fichier traité.
- **Validation du n° de passage** : l'auto-incrément (max+1 sur les passages existants pour ce point/année) est calculé à l'ouverture de l'écran, **pas** à chaque rendu (sinon problème de course si plusieurs passages se créent en parallèle).
