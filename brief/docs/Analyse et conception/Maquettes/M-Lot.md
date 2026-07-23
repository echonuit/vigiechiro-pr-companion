# M-Lot - Préparation du dépôt

> **Type** : vue plein écran (atteinte par la carte « Préparer le dépôt » depuis [M-Passage](M-Passage.md)).
> **Persona principal** : tous. C'est l'**étape finale** de la chaîne fil rouge : la nuit est vérifiée, on la dépose sur Vigie-Chiro.
> **Parcours couverts** : [P4 - Préparer le dépôt](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md).
> **Issue** : #2337 (recadrage de la maquette sur l'écran livré), chantier #2369.

La vue déroule le dépôt en **quatre étapes** (un stepper les rappelle en tête), suivies de deux sections de suivi et d'entretien. Le chemin nominal est un **dépôt direct sur Vigie-Chiro depuis l'application** : elle crée la participation, téléverse les séquences au bon format, reprend sur coupure, puis on **lance l'analyse** Tadarida. L'ouverture du dossier pour un **dépôt navigateur** est un **repli** hors connexion, jamais le mode par défaut.

> **Forme du dépôt** : le dépôt part en **archives ZIP** (≤ 700 Mo) ou en **séquences WAV**, selon le réglage *Réglages ▸ Dépôt*. En ZIP, la plateforme **ne conserve pas les sons** et la participation **n'est plus relançable** : c'est un arbitrage à connaître, rappelé à l'écran.

## Maquette principale - la nuit est vérifiée, prête à déposer

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 980" role="img" aria-label="Maquette M-Lot - Préparation du dépôt en quatre étapes" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .step-on { fill: #3f51b5; }
    .step-off { fill: #eef0f6; stroke: #c8cee0; stroke-width: 1; }
    .step-txt-on { fill: #ffffff; font: 600 12px sans-serif; }
    .step-txt-off { fill: #6a737d; font: 600 12px sans-serif; }
    .info-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value { font: 600 13px sans-serif; fill: #2c3e50; }
    .card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .section-title { font: 600 15px sans-serif; fill: #2c3e50; }
    .section-sub { font: 12px sans-serif; fill: #6a737d; }
    .check-ok { font: 14px sans-serif; fill: #1e8449; }
    .check-warn { font: 14px sans-serif; fill: #b9770e; }
    .check-row { font: 12.5px sans-serif; fill: #2c3e50; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-cloud { fill: #1e8449; stroke: #0e5128; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 13px sans-serif; }
    .btn-txt-link { fill: #4a90d9; font: 600 12px sans-serif; text-decoration: underline; }
    .tbl-head { fill: #eef0f6; stroke: #d0d7de; stroke-width: 0.5; }
    .tbl-head-txt { font: 600 11px sans-serif; fill: #4c5573; }
    .tbl-empty { font: 12px sans-serif; fill: #9aa0b3; }
    .note-box { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .note-txt { font: 12px sans-serif; fill: #4a6785; }
    .warn-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .warn-txt { font: 12px sans-serif; fill: #5d4e00; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="960" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="38" class="breadcrumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="642" y="38" class="breadcrumb-curr">Préparer le dépôt</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="86" class="pagetitle">📦 Préparer le dépôt sur Vigie-Chiro</text>
  <text x="40" y="107" class="pagesub">Téléversement direct depuis l'application, reprenable sur coupure ; dépôt navigateur en repli hors connexion.</text>

  <!-- Stepper 4 etapes -->
  <rect x="40" y="122" width="245" height="30" rx="15" class="step-on"/>
  <text x="162" y="141" class="step-txt-on" text-anchor="middle">1 · Préparer</text>
  <rect x="300" y="122" width="270" height="30" rx="15" class="step-off"/>
  <text x="435" y="141" class="step-txt-off" text-anchor="middle">2 · Générer les archives</text>
  <rect x="585" y="122" width="245" height="30" rx="15" class="step-off"/>
  <text x="707" y="141" class="step-txt-off" text-anchor="middle">3 · Téléverser</text>
  <rect x="845" y="122" width="270" height="30" rx="15" class="step-off"/>
  <text x="980" y="141" class="step-txt-off" text-anchor="middle">4 · Marquer déposé</text>

  <!-- Bandeau passage -->
  <rect x="40" y="166" width="1120" height="48" rx="4" class="info-bar"/>
  <text x="60" y="186" class="info-label">PASSAGE</text>
  <text x="60" y="203" class="info-value">Carré 640380 / A1 / N° 2 (2026) - 22/06/2026</text>
  <text x="430" y="186" class="info-label">VERDICT</text>
  <text x="430" y="203" class="info-value" fill="#1e6f3f">✓ OK</text>
  <text x="540" y="186" class="info-label">STATUT</text>
  <text x="540" y="203" class="info-value">Vérifié</text>
  <text x="700" y="186" class="info-label">SÉQUENCES</text>
  <text x="700" y="203" class="info-value">3 614 · 17,2 Go</text>
  <text x="900" y="186" class="info-label">FORME DU DÉPÔT</text>
  <text x="900" y="203" class="info-value">Archives ZIP (≤ 700 Mo)</text>

  <!-- ===== 1. Vérifier et préparer ===== -->
  <text x="40" y="245" class="section-title">1. Vérifier et préparer le dépôt</text>
  <text x="40" y="262" class="section-sub">Contrôles de cohérence (R33). Un ✗ bloque ; un ⚠ laisse déposer.</text>
  <rect x="40" y="272" width="1120" height="128" rx="4" class="card"/>
  <text x="58" y="298" class="check-ok">✓</text><text x="80" y="298" class="check-row">Verdict de vérification : OK</text>
  <text x="58" y="322" class="check-ok">✓</text><text x="80" y="322" class="check-row">Transformation des enregistrements : 1 572 originaux → 3 614 séquences</text>
  <text x="58" y="346" class="check-ok">✓</text><text x="80" y="346" class="check-row">Nommage des fichiers : préfixe Car640380-2026-Pass2-A1- conforme</text>
  <text x="58" y="370" class="check-ok">✓</text><text x="80" y="370" class="check-row">Journal du capteur : LogPR1925492.txt présent</text>
  <text x="58" y="392" class="check-warn">⚠</text><text x="80" y="392" class="check-row" fill="#5d4e00">Relevé climatique absent : sonde non installée ou défaillante. Le dépôt reste possible.</text>
  <rect x="920" y="352" width="222" height="34" rx="4" class="btn-primary"/>
  <text x="1031" y="374" class="btn-txt" text-anchor="middle">📦 Vérifier et préparer</text>

  <!-- ===== 2. Générer les archives ===== -->
  <text x="40" y="440" class="section-title">2. Générer les archives de dépôt</text>
  <text x="40" y="457" class="section-sub">Découpe les séquences en archives ZIP « préfixe-N.zip » ≤ 700 Mo, dans le sous-dossier depot/.</text>
  <rect x="40" y="467" width="1120" height="118" rx="4" class="card"/>
  <rect x="58" y="483" width="230" height="32" rx="4" class="btn-secondary"/>
  <text x="173" y="504" class="btn-txt-dark" text-anchor="middle">🗂 Générer les archives</text>
  <rect x="58" y="527" width="1084" height="24" class="tbl-head"/>
  <text x="70" y="543" class="tbl-head-txt">#</text>
  <text x="170" y="543" class="tbl-head-txt">Fichiers</text>
  <text x="420" y="543" class="tbl-head-txt">Taille</text>
  <text x="700" y="543" class="tbl-head-txt">Progression</text>
  <text x="600" y="572" class="tbl-empty" text-anchor="middle">Aucune archive de dépôt pour l'instant.</text>

  <!-- ===== 3. Téléverser ===== -->
  <text x="40" y="625" class="section-title">3. Téléverser sur Vigie-Chiro</text>
  <text x="40" y="642" class="section-sub">Téléverse la nuit directement (participation créée, envoi reprenable). Repli navigateur en cas de besoin.</text>
  <rect x="40" y="652" width="1120" height="86" rx="4" class="card"/>
  <rect x="58" y="668" width="270" height="38" rx="4" class="btn-cloud"/>
  <text x="193" y="692" class="btn-txt" text-anchor="middle" font-size="14">☁ Téléverser sur Vigie-Chiro</text>
  <rect x="345" y="668" width="290" height="38" rx="4" class="btn-secondary"/>
  <text x="490" y="692" class="btn-txt-dark" text-anchor="middle">📁 Ouvrir le dossier (dépôt manuel)</text>
  <text x="58" y="726" class="note-txt">En ligne : téléversement direct, reprenable. Hors connexion : dépôt des archives ZIP depuis le navigateur sur <tspan class="btn-txt-link">vigiechiro.herokuapp.com</tspan>.</text>

  <!-- ===== Traitement Vigie-Chiro ===== -->
  <text x="40" y="775" class="section-title">Traitement Vigie-Chiro</text>
  <text x="40" y="792" class="section-sub">Lancer l'analyse Tadarida (geste volontaire) puis relever son état, à la demande.</text>
  <rect x="40" y="802" width="1120" height="54" rx="4" class="card"/>
  <rect x="58" y="815" width="230" height="30" rx="4" class="btn-primary"/>
  <text x="173" y="835" class="btn-txt" text-anchor="middle">▶ Lancer la participation</text>
  <text x="310" y="835" class="check-row" fill="#6a737d">État : non lancé</text>
  <rect x="1010" y="815" width="132" height="30" rx="4" class="btn-secondary"/>
  <text x="1076" y="835" class="btn-txt-dark" text-anchor="middle">↻ Actualiser</text>

  <!-- ===== 4. Marquer déposé + Libérer l'espace ===== -->
  <text x="40" y="890" class="section-title">4. Marquer le passage déposé  ·  Libérer l'espace disque</text>
  <text x="40" y="907" class="section-sub">« Marquer déposé » est le repli du dépôt navigateur. Les archives ZIP locales sont régénérables.</text>
  <rect x="40" y="917" width="1120" height="30" rx="4" class="note-box"/>
  <rect x="58" y="920" width="180" height="24" rx="4" class="btn-secondary"/>
  <text x="148" y="937" class="btn-txt-dark" text-anchor="middle" font-size="12">✔ Marquer déposé</text>
  <rect x="250" y="920" width="260" height="24" rx="4" class="btn-danger"/>
  <text x="380" y="937" class="btn-txt-danger" text-anchor="middle" font-size="12">🗑 Supprimer les archives de dépôt</text>
  <text x="980" y="937" class="footer-txt" text-anchor="end">Statut : Vérifié → Déposé une fois tout en ligne</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome** via le contrat `EmplacementNavigation` ; l'écran ne dessine pas son propre fil. Emplacement : `Accueil › Mes sites › Carré N › Passage N° X › Préparer le dépôt`.
- **Stepper** (`1 · Préparer` / `2 · Générer les archives` / `3 · Téléverser` / `4 · Marquer déposé`) : rappelle les quatre temps. L'étape 2 devient **facultative** quand on est connecté : le téléversement produit les archives à la volée et n'en garde que quelques-unes sur disque.
- **Bandeau passage** : rappel du passage, du verdict, du statut, du volume, et de la **forme du dépôt** (ZIP ou WAV, réglable).
- **1. Vérifier et préparer** : la checklist de cohérence (R33) affiche **quatre contrôles bloquants** (verdict, transformation, nommage, journal) et **un avertissement non bloquant** (relevé climatique absent). Un ✗ interdit la préparation ; un ⚠ laisse déposer. Le bouton verrouille ensuite la liste des séquences et fait passer le passage à `Prêt à déposer`.
- **2. Générer les archives** : découpe les séquences en `préfixe-N.zip` (≤ 700 Mo, réglable), écrites dans `depot/`. La table suit chaque archive (numéro, fichiers, taille, progression). Facultatif si connecté (cf. stepper).
- **3. Téléverser** : le bouton **☁ Téléverser sur Vigie-Chiro** est le chemin nominal (participation créée, envoi **reprenable** unité par unité). **📁 Ouvrir le dossier** ouvre `depot/` pour un **dépôt navigateur de repli** hors connexion.
- **Traitement Vigie-Chiro** : téléverser **ne suffit pas** à lancer l'analyse. **▶ Lancer la participation** déclenche le calcul Tadarida serveur ; **↻ Actualiser** relève son état (Planifiée / En cours / Terminée / Échec) **à la demande**, sans sondage automatique.
- **4. Marquer déposé / Libérer l'espace** : « **✔ Marquer déposé** » trace le dépôt à la main, en **repli** du dépôt navigateur (le dépôt direct pose `Déposé` tout seul une fois tout en ligne). « **🗑 Supprimer les archives de dépôt** » libère l'espace : les ZIP sont régénérables à l'identique.

### Interactions clés

| Élément | Action |
|---|---|
| **📦 Vérifier et préparer** | Rejoue les contrôles, verrouille les séquences, passe le passage à `Prêt à déposer` |
| **🗂 Générer les archives** | Produit les ZIP `préfixe-N.zip` dans `depot/`, avec progression |
| **☁ Téléverser sur Vigie-Chiro** | Crée la participation et téléverse ; reprend les échecs ; `Déposé` quand tout est en ligne |
| **📁 Ouvrir le dossier (dépôt manuel)** | Ouvre `depot/` dans l'explorateur natif (repli hors connexion) |
| **▶ Lancer la participation** | Déclenche l'analyse Tadarida côté serveur (geste volontaire) |
| **↻ Actualiser** | Relève l'état du traitement serveur (pas de sondage automatique) |
| **✔ Marquer déposé** | Trace la date de dépôt à la main (repli) |
| **🗑 Supprimer les archives de dépôt** | Supprime les ZIP locaux (régénérables) |

---

## Variante - passage déposé (état atteint)

Une fois le dépôt effectué, l'écran montre l'état atteint et oriente vers la suite. Le retour de statut (annuler le dépôt) se pilote depuis [M-Passage](M-Passage.md).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 250" role="img" aria-label="Maquette M-Lot - Passage déposé" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .section-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .section-sub { font: 12px sans-serif; fill: #6a737d; }
    .step-num-done { fill: #1e8449; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .deposit-card { fill: #d4edda; stroke: #1e8449; stroke-width: 1.5; }
    .deposit-icon { font: 32px sans-serif; }
    .deposit-title { font: 700 17px sans-serif; fill: #1e6f3f; }
    .deposit-date { font: 13px sans-serif; fill: #1e6f3f; }
    .btn-link { fill: #4a90d9; font: 600 13px sans-serif; }
    .tadarida-banner { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .tadarida-txt { font: 13px sans-serif; fill: #2563a3; }
  </style>
  <rect x="10" y="10" width="1180" height="230" rx="4" class="frame"/>
  <circle cx="62" cy="50" r="16" class="step-num-done"/>
  <text x="62" y="55" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="92" y="47" class="section-title">Déposé sur Vigie-Chiro</text>
  <text x="92" y="64" class="section-sub">Statut du passage : Déposé.</text>

  <rect x="40" y="78" width="1120" height="66" rx="6" class="deposit-card"/>
  <text x="78" y="120" class="deposit-icon">📤</text>
  <text x="128" y="108" class="deposit-title">Déposé le 24/06/2026</text>
  <text x="128" y="130" class="deposit-date">Participation créée sur Vigie-Chiro · analyse Tadarida lancée.</text>
  <text x="1140" y="118" class="btn-link" text-anchor="end">🌐 Voir la participation</text>

  <rect x="40" y="162" width="1120" height="64" rx="4" class="tadarida-banner"/>
  <text x="60" y="188" class="tadarida-txt" font-weight="600">⏳ Et maintenant ?</text>
  <text x="60" y="208" class="tadarida-txt">24-48 h après, importez les résultats Tadarida : directement depuis Vigie-Chiro (☰ ▸ Importer), ou depuis un CSV téléchargé du portail.</text>
  <text x="1140" y="208" class="btn-link" text-anchor="end">→ P7 - Valider</text>
</svg>
</div>

### Notes sur l'état « déposé »

- **Encart vert** « Déposé le DD/MM/AAAA » : trace le dépôt et rappelle que la **participation** a été créée et l'analyse lancée. Un lien **« Voir la participation »** ouvre la page sur le portail.
- **Annuler le dépôt** : le retour du statut `Déposé` → `Prêt à déposer` (validations conservées) se fait depuis [M-Passage](M-Passage.md) ; sur M-Lot, l'action d'interruption ne concerne qu'un **téléversement en cours** (arrêt coopératif). Il n'y a **pas** de « corriger la date de dépôt ».
- **Bannière « Et maintenant ? »** : oriente vers la validation ([M-SonsValidation](M-SonsValidation.md)). Les résultats se récupèrent **directement par l'API** (☰ ▸ « Importer depuis Vigie-Chiro »), l'import d'un CSV téléchargé restant un repli.

---

## Variante - vérification bloquante (verdict Inexploitable)

Si le verdict du passage est `Inexploitable` (R14) ou si un contrôle **bloquant** échoue, la préparation est refusée avec un message explicite.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 300" role="img" aria-label="Maquette M-Lot - Vérification bloquante" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .info-bar-warn { fill: #fde7e7; stroke: #a93226; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value-warn { font: 600 13px sans-serif; fill: #a93226; }
    .info-value { font: 600 13px sans-serif; fill: #2c3e50; }
    .section-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .section-sub { font: 12px sans-serif; fill: #6a737d; }
    .step-num-err { fill: #a93226; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .check-card-fail { fill: #fde7e7; stroke: #a93226; stroke-width: 1; }
    .check-row { font: 13px sans-serif; fill: #2c3e50; }
    .check-detail { font: 12px sans-serif; fill: #6a737d; }
    .check-fail { font: 16px sans-serif; fill: #a93226; }
    .check-ok { font: 16px sans-serif; fill: #1e8449; }
    .btn-disabled { fill: #d0d7de; stroke: #6a737d; stroke-width: 1; }
    .btn-txt-disabled { fill: #6a737d; font: 700 13px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
  </style>
  <rect x="10" y="10" width="1180" height="280" rx="4" class="frame"/>
  <rect x="40" y="30" width="1120" height="50" rx="4" class="info-bar-warn"/>
  <text x="60" y="51" class="info-label">PASSAGE</text>
  <text x="60" y="69" class="info-value">Carré 640380 / A1 / N° 2 (2026) - 22/06/2026</text>
  <text x="430" y="51" class="info-label">VERDICT</text>
  <text x="430" y="69" class="info-value-warn">❌ Inexploitable</text>
  <text x="600" y="51" class="info-label">STATUT</text>
  <text x="600" y="69" class="info-value">Vérifié</text>

  <circle cx="62" cy="120" r="16" class="step-num-err"/>
  <text x="62" y="125" class="step-num-txt" text-anchor="middle">!</text>
  <text x="92" y="117" class="section-title">Préparation du dépôt impossible</text>
  <text x="92" y="134" class="section-sub">Un contrôle bloquant empêche de préparer le dépôt.</text>

  <rect x="40" y="150" width="1120" height="100" rx="4" class="check-card-fail"/>
  <text x="60" y="174" class="check-fail">❌</text>
  <text x="86" y="174" class="check-row" font-weight="600">Verdict actuel : Inexploitable (R14).</text>
  <text x="86" y="190" class="check-detail">Un passage « Inexploitable » ne peut pas être déposé.</text>
  <text x="60" y="216" class="check-ok">✓</text>
  <text x="86" y="216" class="check-row">Transformation et nommage conformes ; journal présent.</text>
  <text x="60" y="238" class="check-ok">✓</text>
  <text x="86" y="238" class="check-row">Archives régénérables si besoin.</text>

  <rect x="930" y="256" width="230" height="30" rx="4" class="btn-disabled"/>
  <text x="1045" y="276" class="btn-txt-disabled" text-anchor="middle">📦 Vérifier et préparer</text>
  <rect x="60" y="256" width="240" height="30" rx="4" class="btn-secondary"/>
  <text x="180" y="276" class="btn-txt-dark" text-anchor="middle">🎧 Modifier le verdict</text>
</svg>
</div>

### Notes sur le cas bloqué

- **Bandeau rouge** en haut : le verdict bloquant est signalé immédiatement.
- **Encart rouge** : la première ligne est l'échec ❌ (la cause), les suivantes ✓ montrent que le reste passe.
- **Le bouton « 📦 Vérifier et préparer »** est **désactivé** : impossible de préparer un dépôt sur un passage `Inexploitable`.
- **« 🎧 Modifier le verdict »** renvoie vers [M-Qualification](M-Qualification.md) pour revoir la décision si elle était précipitée.

## Notes pour l'implémentation

- **Contrôles de cohérence** : rejoués à l'ouverture (pas mémorisés en base). Bloquants : verdict, transformation, nommage, journal ; non bloquant : relevé climatique.
- **Forme du dépôt** : réglage global (*Réglages ▸ Dépôt*), ZIP par défaut. En ZIP, la plateforme ne conserve pas les sons et la participation n'est pas relançable : la conséquence est rappelée à l'écran.
- **Dépôt reprenable** : le plan est persisté unité par unité (`depot_plan`, `depot_unite`), statut `Dépôt en cours` ; un dépôt interrompu se **reprend** (« Retenter les échecs »), il ne se rejoue pas.
- **Lancement du traitement** : `▶ Lancer la participation` déclenche le calcul serveur ; l'état est **relevé à la demande** (pas de sondage automatique).
- **Ouverture du dossier** : `java.awt.Desktop.open(File)` sur `depot/` ; désactivée proprement en environnement sans bureau graphique (chemin copiable en repli).
- **Icônes** : `FontIcon` Ikonli, pas d'emoji (règle #700) ; les emojis de la maquette ne sont qu'un substitut basse fidélité.
