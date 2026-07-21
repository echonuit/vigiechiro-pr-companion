# M-Lot - Préparation du dépôt

> **Type** : vue plein écran (atteinte par clic « Vérifier et préparer le dépôt » depuis [M-Passage](M-Passage.md) ou depuis un raccourci dans [M-Qualification](M-Qualification.md) après saisie du verdict).
> **Persona principal** : tous. C'est l'**étape finale** de la chaîne fil rouge : la nuit est vérifiée, on prépare son téléversement sur Vigie-Chiro.
> **Parcours couverts** : [P4 - Préparer le dépôt](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md).

Cette vue affiche un **rapport de cohérence** (toutes les vérifications préalables passent ✓ ou échouent ✗), puis le **récapitulatif du dépôt** prêt à téléverser (chemin sur disque, volume, lien direct pour ouvrir le dossier dans l'explorateur), et enfin la **section de téléversement manuel** avec lien vers le portail Vigie-Chiro et bouton de confirmation « J'ai déposé ». L'application **ne dialogue jamais** avec Vigie-Chiro : le téléversement reste manuel via navigateur.

## Maquette principale - vérifications passent, dépôt prêt

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 820" role="img" aria-label="Maquette M-Lot - Préparation du dépôt" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .section-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .section-sub { font: 12px sans-serif; fill: #6a737d; }
    .check-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .check-card-success { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .check-row { font: 13px sans-serif; fill: #2c3e50; }
    .check-detail { font: 12px sans-serif; fill: #6a737d; }
    .check-ok { font: 16px sans-serif; fill: #1e8449; }
    .check-pending { font: 16px sans-serif; fill: #6a737d; }
    .lot-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .lot-label { font: 11px sans-serif; fill: #6a737d; }
    .lot-value { font: 700 24px sans-serif; fill: #2c3e50; }
    .lot-value-mono { font: 600 13px monospace; fill: #2c3e50; }
    .lot-detail { font: 12px sans-serif; fill: #6a737d; }
    .path-display { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .path-mono { font: 12px monospace; fill: #2c3e50; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-primary-big { fill: #1e8449; stroke: #0e5128; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-link { fill: none; stroke: none; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-big { fill: #ffffff; font: 700 14px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-txt-link { fill: #4a90d9; font: 600 13px sans-serif; text-decoration: underline; }
    .step-num { fill: #4a90d9; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .upload-note { font: 12px sans-serif; fill: #2c3e50; }
    .upload-warn-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .upload-warn-txt { font: 13px sans-serif; fill: #5d4e00; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="800" rx="4" class="frame"/>
  <!-- Bandeau du chrome : titre + fil d'Ariane (emplacement complet du passage) + recherche -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="38" class="breadcrumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="642" y="38" class="breadcrumb-curr">Préparer le dépôt</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="148" class="pagetitle">📦 Préparer le dépôt sur Vigie-Chiro</text>
  <text x="40" y="170" class="pagesub">Le téléversement est manuel via navigateur. L'application prépare le dossier et trace la date de dépôt.</text>

  <!-- Bandeau passage -->
  <rect x="40" y="195" width="1120" height="50" rx="4" class="info-bar"/>
  <text x="60" y="216" class="info-label">PASSAGE</text>
  <text x="60" y="234" class="info-value">Carré 640380 / A1 / N° 2 (2026) - 22/06/2026</text>
  <text x="430" y="216" class="info-label">VERDICT</text>
  <text x="430" y="234" class="info-value" fill="#1e6f3f">✓ OK</text>
  <text x="540" y="216" class="info-label">STATUT</text>
  <text x="540" y="234" class="info-value">Vérifié</text>
  <text x="680" y="216" class="info-label">SÉQUENCES À DÉPOSER</text>
  <text x="680" y="234" class="info-value">3 614</text>
  <text x="900" y="216" class="info-label">VOLUME TOTAL</text>
  <text x="900" y="234" class="info-value">17,2 Go</text>

  <!-- ============ Étape 1 : Vérifications de cohérence ============ -->
  <circle cx="62" cy="290" r="16" class="step-num"/>
  <text x="62" y="295" class="step-num-txt" text-anchor="middle">1</text>
  <text x="92" y="287" class="section-title">Vérifications de cohérence</text>
  <text x="92" y="304" class="section-sub">Contrôles automatiques avant préparation du dépôt (R31).</text>

  <rect x="40" y="320" width="1120" height="135" rx="4" class="check-card-success"/>

  <text x="60" y="344" class="check-ok">✓</text>
  <text x="86" y="344" class="check-row" font-weight="600">Tous les enregistrements originaux ont été transformés en séquences d'écoute.</text>
  <text x="86" y="360" class="check-detail">1 572 fichiers WAV bruts → 3 614 séquences d'écoute (ratio 2,30, conforme à R10).</text>

  <text x="60" y="383" class="check-ok">✓</text>
  <text x="86" y="383" class="check-row" font-weight="600">Préfixe Car640380-2026-Pass2-A1- présent et conforme sur tous les fichiers.</text>
  <text x="86" y="399" class="check-detail">Vérifié sur 3 614 séquences + 1 572 originaux + journal + climat (R6, R7, R8 OK).</text>

  <text x="60" y="421" class="check-ok">✓</text>
  <text x="86" y="421" class="check-row" font-weight="600">Journal du capteur et relevé climatique présents.</text>
  <text x="86" y="438" class="check-detail">LogPR1925492.txt (4,2 Mo) · PaRecPR1925492_THLog.csv (12 Ko, 144 mesures).</text>

  <!-- ============ Étape 2 : Récapitulatif du dépôt ============ -->
  <circle cx="62" cy="495" r="16" class="step-num"/>
  <text x="62" y="500" class="step-num-txt" text-anchor="middle">2</text>
  <text x="92" y="492" class="section-title">Récapitulatif du dépôt</text>
  <text x="92" y="509" class="section-sub">Tout ce qui sera téléversé sur Vigie-Chiro.</text>

  <rect x="40" y="525" width="1120" height="115" rx="4" class="lot-card"/>

  <!-- 4 stats côte à côte -->
  <text x="100" y="553" class="lot-label">FICHIERS À DÉPOSER</text>
  <text x="100" y="585" class="lot-value">3 616</text>
  <text x="100" y="605" class="lot-detail">3 614 séquences + journal + climat</text>

  <text x="320" y="553" class="lot-label">VOLUME TOTAL</text>
  <text x="320" y="585" class="lot-value">17,2 Go</text>
  <text x="320" y="605" class="lot-detail">prêt à téléverser</text>

  <text x="540" y="553" class="lot-label">FORMAT</text>
  <text x="540" y="585" class="lot-value-mono" font-size="16">WAV ×10 + LogPR + THLog</text>
  <text x="540" y="605" class="lot-detail">conforme protocole Vigie-Chiro</text>

  <!-- Chemin du dossier + bouton ouvrir -->
  <text x="760" y="553" class="lot-label">EMPLACEMENT SUR DISQUE</text>
  <rect x="760" y="563" width="320" height="32" rx="3" class="path-display"/>
  <text x="772" y="585" class="path-mono">&lt;Documents&gt;/VigieChiro-Companion/</text>
  <rect x="1090" y="563" width="60" height="32" rx="3" class="btn-secondary"/>
  <text x="1120" y="585" class="btn-txt-dark" text-anchor="middle" font-size="14">📋</text>
  <text x="760" y="615" class="lot-detail">→ Car640380-2026-Pass2-A1/</text>

  <rect x="40" y="650" width="300" height="34" rx="4" class="btn-primary"/>
  <text x="190" y="671" class="btn-txt" text-anchor="middle">📂 Ouvrir le dossier dans l'explorateur</text>

  <!-- ============ Étape 3 : Téléversement et confirmation ============ -->
  <circle cx="62" cy="715" r="16" class="step-num"/>
  <text x="62" y="720" class="step-num-txt" text-anchor="middle">3</text>
  <text x="92" y="712" class="section-title">Téléverser sur Vigie-Chiro, puis confirmer</text>

  <rect x="40" y="730" width="880" height="48" rx="4" class="upload-warn-box"/>
  <text x="58" y="750" class="upload-warn-txt">⚠ L'application ne dialogue pas avec Vigie-Chiro : sélectionnez les fichiers dans l'explorateur</text>
  <text x="58" y="768" class="upload-warn-txt">ouvert ci-dessus et téléversez-les manuellement sur <tspan class="btn-txt-link">vigiechiro.herokuapp.com 🔗</tspan></text>

  <rect x="940" y="734" width="220" height="40" rx="4" class="btn-primary-big"/>
  <text x="1050" y="759" class="btn-txt-big" text-anchor="middle">✓ J'ai déposé</text>

  <!-- Footer -->
  <rect x="10" y="790" width="1180" height="20" class="footer"/>
  <text x="40" y="805" class="footer-txt">Statut actuel : Vérifié (verdict OK) · prêt à passer au statut Déposé après confirmation</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome** (barre de navigation commune) via le contrat `EmplacementNavigation` ; l'écran ne dessine pas son propre fil. Emplacement affiché : `🏠 Accueil › Mes sites › Carré N › Détails du passage N° X › Préparer le dépôt`, identique quelle que soit la route.
- **Bandeau passage** : 5 cellules de rappel (passage, verdict ✓ OK en vert, statut Vérifié, nombre de séquences, volume total).
- **Étape 1 - Vérifications** (encart vert) : 3 lignes ✓ couvrant les contrôles de cohérence (R31). Toutes les vérifications passent, donc on peut continuer. Si **au moins une** échoue (✗ rouge), l'encart passe en rouge et le bouton « J'ai déposé » de l'étape 3 est désactivé.
- **Étape 2 - Récapitulatif du dépôt** : 4 informations clés (nombre de fichiers, volume, format, chemin sur disque). Le **chemin** est tronqué dans l'affichage mais cliquable pour copier (icône 📋) ou ouvrir dans l'explorateur (bouton primary).
- **Étape 3 - Téléversement** :
    - Bandeau jaune d'avertissement explicite que l'application **ne dialogue pas** avec Vigie-Chiro, avec lien direct vers le portail (s'ouvre dans le navigateur par défaut)
    - Bouton **vert primary `✓ J'ai déposé`** à droite : action de confirmation finale : marque le passage **Déposé**.

### Interactions clés

| Élément | Action |
|---|---|
| Bouton **📂 Ouvrir le dossier** | Ouvre l'explorateur natif de l'OS sur le dossier du dépôt (`java.awt.Desktop.open`) |
| Icône **📋** près du chemin | Copie le chemin absolu dans le presse-papier |
| Lien **vigiechiro.herokuapp.com 🔗** | Ouvre le portail dans le navigateur par défaut |
| Bouton **✓ J'ai déposé** | Confirmation modale → passage au statut `Déposé` + persistance de la date courante comme date de dépôt déclarée |

---

## Variante - passage déjà déposé (état final)

Une fois que l'utilisateur a confirmé le dépôt, l'écran évolue : la zone de confirmation est remplacée par un encart de traçabilité.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 280" role="img" aria-label="Maquette M-Lot - Passage déjà déposé" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .section-title { font: 600 16px sans-serif; fill: #2c3e50; }
    .section-sub { font: 12px sans-serif; fill: #6a737d; }
    .step-num-done { fill: #1e8449; }
    .step-num-txt { fill: #ffffff; font: 700 14px sans-serif; }
    .deposit-card { fill: #d4edda; stroke: #1e8449; stroke-width: 1.5; }
    .deposit-icon { font: 36px sans-serif; }
    .deposit-title { font: 700 18px sans-serif; fill: #1e6f3f; }
    .deposit-date { font: 13px sans-serif; fill: #1e6f3f; }
    .deposit-mono { font: 600 12px monospace; fill: #1e6f3f; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-link { fill: #4a90d9; font: 600 13px sans-serif; text-decoration: underline; }
    .btn-danger-link { fill: #a93226; font: 600 13px sans-serif; }
    .tadarida-banner { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .tadarida-txt { font: 13px sans-serif; fill: #2563a3; }
    .tadarida-mono { font: 600 13px monospace; fill: #2563a3; }
  </style>

  <rect x="10" y="10" width="1180" height="260" rx="4" class="frame"/>

  <circle cx="62" cy="50" r="16" class="step-num-done"/>
  <text x="62" y="55" class="step-num-txt" text-anchor="middle">✓</text>
  <text x="92" y="47" class="section-title">Dépôt confirmé sur Vigie-Chiro</text>
  <text x="92" y="64" class="section-sub">Statut du passage maintenant : Déposé.</text>

  <rect x="40" y="80" width="1120" height="80" rx="6" class="deposit-card"/>
  <text x="80" y="125" class="deposit-icon">📤</text>
  <text x="130" y="115" class="deposit-title">Déposé le 24/06/2026</text>
  <text x="130" y="138" class="deposit-date">Confirmé par vous-même · stocké en base locale.</text>

  <rect x="800" y="105" width="170" height="30" rx="3" class="btn-secondary"/>
  <text x="885" y="125" class="btn-txt-dark" text-anchor="middle" font-size="12">✏ Corriger la date</text>

  <text x="985" y="124" class="btn-danger-link">↺ Annuler le dépôt</text>

  <!-- Bannière "et après ?" -->
  <rect x="40" y="180" width="1120" height="70" rx="4" class="tadarida-banner"/>
  <text x="60" y="207" class="tadarida-txt" font-weight="600">⏳ Et maintenant ?</text>
  <text x="60" y="225" class="tadarida-txt">24-48 h après le dépôt, Vigie-Chiro vous fournira un fichier de résultats Tadarida au format CSV.</text>
  <text x="60" y="243" class="tadarida-txt">Téléchargez-le depuis le portail et importez-le dans l'application pour entamer la validation taxonomique.</text>
  <text x="900" y="225" class="btn-link" text-anchor="end">→ Voir P7 / E7 (cible étirable)</text>
</svg>
</div>

### Notes sur le mode « déjà déposé »

- **Encart vert** « Déposé le DD/MM/AAAA » qui remplace les boutons d'action principaux.
- **✏ Corriger la date** : utile si l'utilisateur a coché « J'ai déposé » un jour après le téléversement réel (modale avec date picker).
- **↺ Annuler le dépôt** : action de récupération en cas d'erreur (le statut redevient `Vérifié`, la date est effacée).
- **Bannière bleue « Et maintenant ? »** : guide l'utilisateur vers la suite (validation Tadarida via [M-SonsValidation](M-SonsValidation.md) une fois le CSV reçu). Signale clairement que c'est une **cible étirable**.

---

## Variante - vérifications échouent ou verdict Inexploitable

Si le verdict du passage est `Inexploitable` (R14) ou si une vérification de cohérence échoue, le bouton de préparation du dépôt est désactivé avec un message explicite.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 320" role="img" aria-label="Maquette M-Lot - Vérification échoue ou verdict inexploitable" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .info-bar-warn { fill: #fde7e7; stroke: #a93226; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value-warn { font: 600 13px sans-serif; fill: #a93226; }
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
    .btn-txt-disabled { fill: #6a737d; font: 700 14px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .info-value { font: 600 13px sans-serif; fill: #2c3e50; }
  </style>

  <rect x="10" y="10" width="1180" height="300" rx="4" class="frame"/>

  <!-- Bandeau passage avec verdict Inexploitable -->
  <rect x="40" y="30" width="1120" height="50" rx="4" class="info-bar-warn"/>
  <text x="60" y="51" class="info-label">PASSAGE</text>
  <text x="60" y="69" class="info-value">Carré 640380 / A1 / N° 2 (2026) - 22/06/2026</text>
  <text x="430" y="51" class="info-label">VERDICT</text>
  <text x="430" y="69" class="info-value-warn">❌ Inexploitable</text>
  <text x="540" y="51" class="info-label">STATUT</text>
  <text x="540" y="69" class="info-value">Vérifié</text>

  <!-- Étape vérifications -->
  <circle cx="62" cy="120" r="16" class="step-num-err"/>
  <text x="62" y="125" class="step-num-txt" text-anchor="middle">!</text>
  <text x="92" y="117" class="section-title">Préparation du dépôt impossible</text>
  <text x="92" y="134" class="section-sub">Une ou plusieurs vérifications bloquent le passage au statut « Prêt à déposer ».</text>

  <rect x="40" y="150" width="1120" height="105" rx="4" class="check-card-fail"/>

  <text x="60" y="174" class="check-fail">❌</text>
  <text x="86" y="174" class="check-row" font-weight="600">Verdict actuel : Inexploitable (R14).</text>
  <text x="86" y="190" class="check-detail">Un passage avec verdict « Inexploitable » ne peut pas être inclus dans un dépôt.</text>

  <text x="60" y="216" class="check-ok">✓</text>
  <text x="86" y="216" class="check-row">Préfixe Car640380-2026-Pass2-A1- conforme sur tous les fichiers.</text>

  <text x="60" y="238" class="check-ok">✓</text>
  <text x="86" y="238" class="check-row">Journal et relevé climatique présents.</text>

  <!-- Bouton désactivé + lien correctif -->
  <rect x="930" y="270" width="230" height="34" rx="4" class="btn-disabled"/>
  <text x="1045" y="293" class="btn-txt-disabled" text-anchor="middle">✓ J'ai déposé</text>

  <rect x="60" y="270" width="240" height="34" rx="4" class="btn-secondary"/>
  <text x="180" y="293" class="btn-txt-dark" text-anchor="middle">🎧 Modifier le verdict</text>
</svg>
</div>

### Notes sur le cas bloqué

- **Bandeau rouge** en haut signale immédiatement le verdict bloquant.
- **3 lignes** dans l'encart rouge : la première est l'échec ❌ (raison du blocage), les suivantes sont ✓ pour montrer que les autres vérifications passent.
- **Le bouton « ✓ J'ai déposé »** est **désactivé** (gris) - l'utilisateur ne peut pas franchir l'étape même en force.
- **Bouton secondary « 🎧 Modifier le verdict »** : redirige vers [M-Qualification](M-Qualification.md) pour permettre à l'utilisateur de revoir sa décision s'il le souhaite (par exemple si le « Inexploitable » était précipité).

## Notes pour l'implémentation

- **Calcul des vérifications** : exécuté à chaque ouverture de l'écran (pas mémorisé en BD). Coût négligeable car les contrôles sont des requêtes simples sur les passages/séquences déjà persistés.
- **Détection ✗ vs ✓** : le bouton « J'ai déposé » est activé **uniquement** si toutes les vérifications passent ET que le verdict est OK ou Utilisable (jamais Inexploitable).
- **Confirmation de dépôt** : modale séparée (non figurée) avec récap des conséquences (« Le passage va passer au statut Déposé, la date 24/06/2026 sera enregistrée ») avant d'écrire en BD.
- **Annulation du dépôt** : autorisée pendant N jours (à arbitrer) pour récupération d'erreur. Au-delà, considérer le passage comme « clos ».
- **Ouverture du dossier** : `java.awt.Desktop.open(File)` est la méthode standard, fonctionne sur Linux/macOS/Windows. Tester en environnement sans bureau graphique : le bouton doit être désactivé proprement avec un message explicite (et le chemin reste copiable via 📋).
