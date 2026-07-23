# M-Site-detail - Détail d'un site de suivi

> **Type** : vue de détail (atteinte par clic sur une carte de [M-Sites](M-Sites.md)).
> **Persona principal** : [Marie](../Personas/Marie.md), partagée avec [Karim](../Personas/Karim.md). [Samuel](../Personas/Samuel.md) y accède ponctuellement ; sa vue de prédilection est [M-MultiSite](M-MultiSite.md).
> **Parcours couverts** : [P1 - Déclarer un site de suivi](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md), [P12 - Récupérer une nuit déposée sur VigieChiro](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md).

Cette vue présente **un site et tout ce qui s'y rattache** : sa fiche d'identité (n° de carré, département, protocole, dates), ses points d'écoute (coordonnées GPS optionnelles) et l'historique des passages enregistrés sur ce site. C'est aussi depuis cet écran qu'on **modifie le site** (ajout/retrait de points, mise à jour des coordonnées GPS) et qu'on lance un import.

## Maquette principale - site avec 3 points et plusieurs passages

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 820" role="img" aria-label="Maquette M-Site-detail - Détail d'un site de suivi" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 12px sans-serif; }
    .info-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value { font: 600 14px sans-serif; fill: #2c3e50; }
    .section-title { font: 700 16px sans-serif; fill: #2c3e50; }
    .point-card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .point-code { font: 700 18px sans-serif; fill: #2c3e50; }
    .point-desc { font: 13px sans-serif; fill: #6a737d; }
    .gps-ok { fill: #1e8449; font: 600 12px sans-serif; }
    .gps-missing { fill: #b9770e; font: 600 12px sans-serif; }
    .point-actions { fill: #4a90d9; font: 12px sans-serif; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sec { font: 12px sans-serif; fill: #6a737d; }
    .badge-txt { font: 600 11px sans-serif; }
    .badge-trans { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .badge-veri { fill: #fde7e7; stroke: #a93226; stroke-width: 1; }
    .badge-ok { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .badge-dep { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <!-- Cadre + bandeau chrome (fil d'Ariane + recherche) -->
  <rect x="10" y="10" width="1180" height="800" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  Mes sites  ›  </text>
  <text x="430" y="38" class="crumb-active">Carré 640380</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <!-- En-tete : titre + sous-titre -->
  <text x="40" y="92" class="pagetitle">Carré 640380 - Étang de la Tuilière</text>
  <text x="40" y="114" class="pagesub">📍 Ahetze (64) · Protocole PointFixeStandard</text>

  <!-- Boutons d'action (6 controles : 5 boutons + menu colonnes) -->
  <rect x="470" y="74" width="138" height="34" rx="4" class="btn-primary"/>
  <text x="539" y="96" class="btn-txt" text-anchor="middle">📥 Importer une nuit</text>
  <rect x="614" y="74" width="82" height="34" rx="4" class="btn-secondary"/>
  <text x="655" y="96" class="btn-txt-dark" text-anchor="middle">🗺 Carte</text>
  <rect x="702" y="74" width="176" height="34" rx="4" class="btn-secondary"/>
  <text x="790" y="96" class="btn-txt-dark" text-anchor="middle">☁️ Ouvrir sur Vigie-Chiro</text>
  <rect x="884" y="74" width="98" height="34" rx="4" class="btn-secondary"/>
  <text x="933" y="96" class="btn-txt-dark" text-anchor="middle">✏ Modifier</text>
  <rect x="988" y="74" width="104" height="34" rx="4" class="btn-danger"/>
  <text x="1040" y="96" class="btn-txt-danger" text-anchor="middle">🗑 Supprimer</text>
  <rect x="1098" y="74" width="40" height="34" rx="4" class="btn-secondary"/>
  <text x="1118" y="96" class="btn-txt-dark" text-anchor="middle">☰</text>

  <!-- Bandeau d'infos cles -->
  <rect x="40" y="132" width="1120" height="60" rx="4" class="info-bar"/>
  <text x="60" y="153" class="info-label">N° DE CARRÉ</text>
  <text x="60" y="177" class="info-value">640380</text>
  <text x="185" y="153" class="info-label">DÉPARTEMENT</text>
  <text x="185" y="177" class="info-value">64</text>
  <text x="300" y="153" class="info-label">PROTOCOLE</text>
  <text x="300" y="177" class="info-value">PointFixeStandard</text>
  <text x="500" y="153" class="info-label">CRÉÉ LE</text>
  <text x="500" y="177" class="info-value">2026-04-12</text>
  <text x="650" y="153" class="info-label">DERNIÈRE NUIT</text>
  <text x="650" y="177" class="info-value">2026-06-22 (2 j)</text>
  <text x="830" y="153" class="info-label">PASSAGES (2026)</text>
  <text x="830" y="177" class="info-value">4 (1 à vérifier ⚠)</text>
  <text x="1010" y="153" class="info-label">VIGIE-CHIRO</text>
  <text x="1010" y="177" class="info-value" fill="#1e6f3f">Lié ✓</text>

  <!-- Section Points d'ecoute -->
  <text x="40" y="232" class="section-title">Points d'écoute</text>
  <rect x="180" y="216" width="170" height="30" rx="4" class="btn-primary"/>
  <text x="265" y="236" class="btn-txt" text-anchor="middle">+ Ajouter un point</text>

  <!-- Card A1 (GPS present) -->
  <rect x="40" y="258" width="365" height="110" rx="6" class="point-card"/>
  <text x="60" y="286" class="point-code">A1</text>
  <text x="60" y="308" class="point-desc">Près du grand chêne, à 30 m du chemin</text>
  <text x="60" y="330" class="gps-ok">✓ GPS - voir sur la carte</text>
  <text x="60" y="352" class="point-desc">3 passage(s) rattaché(s)</text>
  <text x="385" y="352" class="point-actions" text-anchor="end">✏ Modifier · 🗑 Supprimer</text>

  <!-- Card B2 (GPS present) -->
  <rect x="417" y="258" width="365" height="110" rx="6" class="point-card"/>
  <text x="437" y="286" class="point-code">B2</text>
  <text x="437" y="308" class="point-desc">Lisière de roselière, plage sud</text>
  <text x="437" y="330" class="gps-ok">✓ GPS - voir sur la carte</text>
  <text x="437" y="352" class="point-desc">2 passage(s) rattaché(s)</text>
  <text x="762" y="352" class="point-actions" text-anchor="end">✏ Modifier · 🗑 Supprimer</text>

  <!-- Card C3 (sans GPS) -->
  <rect x="794" y="258" width="365" height="110" rx="6" class="point-card"/>
  <text x="814" y="286" class="point-code">C3</text>
  <text x="814" y="308" class="point-desc">Bord de l'étang - GPS à relever</text>
  <text x="814" y="330" class="gps-missing">⚠ GPS manquant - placer sur la carte</text>
  <text x="814" y="352" class="point-desc">0 passage(s) rattaché(s)</text>
  <text x="1139" y="352" class="point-actions" text-anchor="end">✏ Modifier · 🗑 Supprimer</text>

  <!-- Section Passages -->
  <text x="40" y="408" class="section-title">Passages enregistrés sur ce site</text>
  <text x="40" y="426" class="pagesub" font-size="12">Astuce : double-cliquez une ligne pour ouvrir l'écran du passage.</text>

  <!-- Tableau passages (memes colonnes que l'app) -->
  <rect x="40" y="440" width="1120" height="34" class="table-head"/>
  <text x="60" y="462" class="col-head">DATE</text>
  <text x="190" y="462" class="col-head">POINT</text>
  <text x="280" y="462" class="col-head">N° PASSAGE</text>
  <text x="430" y="462" class="col-head">STATUT</text>
  <text x="620" y="462" class="col-head">VERDICT</text>
  <text x="800" y="462" class="col-head">ENREGISTREUR</text>
  <text x="1010" y="462" class="col-head">DÉPOSÉ LE</text>

  <!-- Ligne 1 -->
  <rect x="40" y="474" width="1120" height="34" class="table-row"/>
  <text x="60" y="496" class="cell">2026-06-22</text>
  <text x="190" y="496" class="cell">A1</text>
  <text x="300" y="496" class="cell">2</text>
  <rect x="425" y="481" width="110" height="20" rx="10" class="badge-trans"/>
  <text x="480" y="495" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="620" y="496" class="cell-sec">Non vérifié</text>
  <text x="800" y="496" class="cell">PR 1925492</text>
  <text x="1010" y="496" class="cell-sec">-</text>

  <!-- Ligne 2 -->
  <rect x="40" y="508" width="1120" height="34" class="table-row-alt"/>
  <text x="60" y="530" class="cell">2026-06-15</text>
  <text x="190" y="530" class="cell">B2</text>
  <text x="300" y="530" class="cell">2</text>
  <rect x="425" y="515" width="80" height="20" rx="10" class="badge-dep"/>
  <text x="465" y="529" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="615" y="515" width="50" height="20" rx="10" class="badge-ok"/>
  <text x="640" y="529" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>
  <text x="800" y="530" class="cell">PR 1925492</text>
  <text x="1010" y="530" class="cell">2026-06-16</text>

  <!-- Ligne 3 -->
  <rect x="40" y="542" width="1120" height="34" class="table-row"/>
  <text x="60" y="564" class="cell">2026-04-22</text>
  <text x="190" y="564" class="cell">A1</text>
  <text x="300" y="564" class="cell">1</text>
  <rect x="425" y="549" width="80" height="20" rx="10" class="badge-dep"/>
  <text x="465" y="563" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="615" y="549" width="80" height="20" rx="10" class="badge-trans"/>
  <text x="655" y="563" class="badge-txt" fill="#7e5109" text-anchor="middle">Utilisable</text>
  <text x="800" y="564" class="cell">PR 1925492</text>
  <text x="1010" y="564" class="cell">2026-04-24</text>

  <!-- Ligne 4 (inexploitable) -->
  <rect x="40" y="576" width="1120" height="34" class="table-row-alt"/>
  <text x="60" y="598" class="cell">2026-04-08</text>
  <text x="190" y="598" class="cell">B2</text>
  <text x="300" y="598" class="cell">1</text>
  <rect x="425" y="583" width="80" height="20" rx="10" class="badge-veri"/>
  <text x="465" y="597" class="badge-txt" fill="#a93226" text-anchor="middle">Inexploitable</text>
  <text x="620" y="598" class="cell-sec">-</text>
  <text x="800" y="598" class="cell">PR 1925487</text>
  <text x="1010" y="598" class="cell-sec">-</text>

  <!-- Footer -->
  <rect x="10" y="780" width="1180" height="30" class="footer"/>
  <text x="40" y="800" class="footer-txt">VigieChiro Companion · base locale : &lt;Documents&gt;/VigieChiro-Companion/vigiechiro.db</text>
</svg>
</div>

### Annotations

- **Bandeau d'infos clés** : la fiche d'identité du site (n° de carré, département déduit des 2 premiers chiffres du carré [R1](../Modèle%20conceptuel/Règles%20métier.md#r1), protocole, date de création, dernière nuit importée, total passages de l'année, et le **statut Vigie-Chiro** : lié ou non à la plateforme).
- **Cartes points d'écoute** : pour A1 et B2 (géolocalisés), le lien vert **« GPS - voir sur la carte »**, précédé d'une **icône de validation**, ouvre la carte multi-sites centrée sur le point. Pour C3 (sans GPS), le lien ambré **« GPS manquant - placer sur la carte »**, précédé d'une **icône d'avertissement**, ouvre la carte sur le carré, **mode édition actif**, pour glisser le point à sa position ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26), [R27](../Modèle%20conceptuel/Règles%20métier.md#r27)). Chaque carte indique le nombre de passages rattachés ; une carte qui en porte ne peut pas être supprimée ([R28](../Modèle%20conceptuel/Règles%20métier.md#r28)).
- **Tableau passages** : colonnes Date, Point, N° de passage, Statut, Verdict, Enregistreur, Déposé le. Le **statut** suit le cycle `Importé → Transformé → Vérifié → Prêt à déposer → Déposé` (couleur dérivée du statut).

### Interactions clés

| Élément | Action |
|---|---|
| Fil d'Ariane **Mes sites** | Retour à [M-Sites](M-Sites.md) |
| Bouton **📥 Importer une nuit** | Ouvre [M-Import](M-Import.md) avec le site pré-sélectionné |
| Bouton **🗺 Carte** | Ouvre [M-MultiSite](M-MultiSite.md) centré sur le carré du site |
| Bouton **☁️ Ouvrir sur Vigie-Chiro** | Ouvre la fiche du site sur le portail web (si le site est lié à la plateforme) |
| Bouton **✏ Modifier** | Ouvre le formulaire d'édition du site (mêmes champs que la création) |
| Bouton **🗑 Supprimer** | Désactivé tant que des passages sont rattachés au site ([R28](../Modèle%20conceptuel/Règles%20métier.md#r28)) ; sinon confirmation |
| Menu **☰** | Choix des colonnes affichées (tableau des points) |
| Lien **GPS / GPS manquant** d'une carte (icône de validation ou d'avertissement) | Ouvre la carte (voir / placer le point) |
| **✏ / 🗑** d'une carte de point | Édite le point (modale) / le supprime (bloqué si passages, [R28](../Modèle%20conceptuel/Règles%20métier.md#r28)) |
| Double-clic sur une ligne du tableau | Ouvre [M-Passage](M-Passage.md) du passage sélectionné |

---

## Variante - modale d'édition d'un point d'écoute

Activée par **+ Ajouter un point** ou par **✏ Modifier** d'une carte. Le code est obligatoire ; le descriptif et les coordonnées GPS sont optionnels.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 500" role="img" aria-label="Maquette M-Site-detail - Modale d'édition d'un point" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #6b7785;">
  <style>
    .modal-bg { fill: #6b7785; }
    .modal-frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .modal-header { fill: #3f51b5; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .modal-close { fill: #ffffff; font: 600 18px sans-serif; }
    .field-label { font: 600 13px sans-serif; fill: #2c3e50; }
    .field-required { font: 600 13px sans-serif; fill: #a93226; }
    .field-hint { font: 12px sans-serif; fill: #6a737d; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-placeholder { font: 13px sans-serif; fill: #bdc3c7; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .section-divider { stroke: #eef2f5; stroke-width: 1; }
    .section-label { font: 700 12px sans-serif; fill: #6a737d; letter-spacing: 1px; }
  </style>

  <rect x="0" y="0" width="800" height="500" class="modal-bg"/>
  <rect x="100" y="40" width="600" height="420" rx="6" class="modal-frame"/>
  <rect x="100" y="40" width="600" height="48" rx="6" class="modal-header"/>
  <rect x="100" y="70" width="600" height="18" class="modal-header"/>
  <text x="124" y="70" class="modal-header-txt">Nouveau point d'écoute · Carré 640380</text>
  <text x="676" y="72" class="modal-close" text-anchor="end">✕</text>

  <text x="124" y="118" class="section-label">IDENTIFICATION</text>
  <text x="124" y="146" class="field-label">Code du point</text>
  <text x="232" y="146" class="field-required">*</text>
  <rect x="124" y="156" width="120" height="34" rx="3" class="field-input"/>
  <text x="138" y="178" class="field-placeholder">A1</text>
  <text x="260" y="178" class="field-hint">1 lettre + 1 chiffre (ex. A1, Z4) - règle R2</text>

  <text x="124" y="216" class="field-label">Descriptif</text>
  <text x="190" y="216" class="field-hint">(optionnel)</text>
  <rect x="124" y="226" width="552" height="52" rx="3" class="field-input"/>
  <text x="138" y="248" class="field-placeholder">Notes de terrain (« près du grand chêne, à 30 m... »)</text>

  <line x1="124" y1="298" x2="676" y2="298" class="section-divider"/>
  <text x="124" y="322" class="section-label">GÉOLOCALISATION (OPTIONNELLE)</text>
  <text x="124" y="340" class="field-hint">📡 Les coordonnées doivent rester dans le carré du site ; à la carte, le point est contraint à sa maille (R26).</text>

  <text x="124" y="368" class="field-label">Latitude</text>
  <rect x="124" y="378" width="180" height="34" rx="3" class="field-input"/>
  <text x="138" y="400" class="field-placeholder">43.4010</text>
  <text x="324" y="368" class="field-label">Longitude</text>
  <rect x="324" y="378" width="180" height="34" rx="3" class="field-input"/>
  <text x="338" y="400" class="field-placeholder">-1.5740</text>
  <text x="524" y="398" class="field-hint">Décimal, point séparateur</text>

  <rect x="466" y="420" width="100" height="34" rx="4" class="btn-secondary"/>
  <text x="516" y="441" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="576" y="420" width="100" height="34" rx="4" class="btn-primary"/>
  <text x="626" y="441" class="btn-txt" text-anchor="middle">+ Ajouter</text>
</svg>
</div>

### Notes sur la modale

- Seul le **code de point** est obligatoire (étoile rouge `*`) ; le descriptif et les coordonnées GPS sont optionnels.
- La **validation R2** (1 lettre + 1 chiffre) est faite à la saisie : un code mal formé passe le champ en rouge.
- Les coordonnées GPS doivent tomber **dans le carré du site** ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26)) ; sur la carte, le point se **place / se corrige au glisser**, contraint à sa maille.
- Le bouton **+ Ajouter** devient **Enregistrer** en mode édition (valeurs pré-remplies).

---

## Variante - suppression de point bloquée

Si on tente de supprimer un point qui porte des passages, l'opération est **bloquée** avec un message explicite ([R28](../Modèle%20conceptuel/Règles%20métier.md#r28)).

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 360" role="img" aria-label="Maquette M-Site-detail - Suppression de point bloquée" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #6b7785;">
  <style>
    .modal-bg { fill: #6b7785; }
    .modal-frame { fill: #ffffff; stroke: #a93226; stroke-width: 2; }
    .modal-header { fill: #a93226; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .body-icon { font: 56px sans-serif; }
    .body-title { font: 700 18px sans-serif; fill: #2c3e50; }
    .body-text { font: 13px sans-serif; fill: #2c3e50; }
    .body-hint { font: 12px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
  </style>

  <rect x="0" y="0" width="800" height="360" class="modal-bg"/>
  <rect x="150" y="50" width="500" height="260" rx="6" class="modal-frame"/>
  <rect x="150" y="50" width="500" height="44" rx="6" class="modal-header"/>
  <rect x="150" y="80" width="500" height="14" class="modal-header"/>
  <text x="174" y="80" class="modal-header-txt">⚠ Suppression impossible</text>

  <text x="196" y="178" class="body-icon">🚫</text>
  <text x="280" y="146" class="body-title">Le point A1 ne peut pas être supprimé</text>
  <text x="280" y="176" class="body-text">3 passages sont rattachés à ce point.</text>
  <text x="280" y="196" class="body-text">Les supprimer effacerait aussi leurs fichiers audio sur disque.</text>
  <text x="280" y="232" class="body-hint">Pour le supprimer, retirez d'abord ses passages</text>
  <text x="280" y="248" class="body-hint">depuis ce site.</text>

  <rect x="430" y="270" width="100" height="34" rx="4" class="btn-secondary"/>
  <text x="480" y="291" class="btn-txt-dark" text-anchor="middle">Compris</text>
  <rect x="540" y="270" width="100" height="34" rx="4" class="btn-primary"/>
  <text x="590" y="291" class="btn-txt" text-anchor="middle">Voir passages</text>
</svg>
</div>

### Notes sur la suppression bloquée

- L'application **n'autorise jamais** la suppression silencieuse de données rattachées ([R28](../Modèle%20conceptuel/Règles%20métier.md#r28)) : un point (ou un site) qui porte des passages est protégé.
- **« Voir passages »** ramène vers le tableau des passages du site pour identifier ce qu'il faudrait retirer en premier ; **« Compris »** ferme la modale sans rien faire.

## Notes pour l'implémentation

- La **fiche d'identité** et le **tableau des passages** sont calculés par le service (agrégation par site).
- Le **tableau** supporte de nombreuses lignes sans pagination (un site très actif sur la saison) ; le tri se fait par en-tête de colonne.
- Le **statut d'avancement** affiché est l'attribut persisté ; la couleur du badge en est **dérivée**, pas stockée.
- **Points rapatriés** (récupération d'une nuit, [P12](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md)) : la synchronisation « mes sites » ramène **tous** les points du carré Vigie-Chiro. La section « Points d'écoute » **masque par défaut** ceux qui sont rapatriés mais **sans passage**, repliés derrière un « **+ N rapatrié(s)** » qu'on déplie au besoin, pour ne montrer d'emblée que les points réellement utilisés. Un point rapatrié réapparaît dès qu'un passage l'y rattache.
