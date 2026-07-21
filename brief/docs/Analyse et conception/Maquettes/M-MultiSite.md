# M-MultiSite - Carte & passages

> **Type** : vue plein écran (carte « Vue multi-sites » de l'accueil).
> **Persona principal** : [Karim](../Personas/Karim.md), [Samuel](../Personas/Samuel.md) (montée en charge multi-sites). [Marie](../Personas/Marie.md) reste surtout sur [M-Sites](M-Sites.md).
> **Parcours couverts** : [P5 - Naviguer dans plusieurs sites et passages](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md).

La vue **« Carte & passages »** rassemble **tous les passages**, tous sites confondus. Elle combine une **carte** (à gauche) et un **tableau** (à droite) : la carte situe les sites et points dans l'espace, le tableau les liste pour les **trier**, **filtrer** et **exporter**. C'est l'écran adapté dès qu'on suit plusieurs sites.

## Maquette principale - carte + tableau

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" role="img" aria-label="Maquette M-MultiSite - Carte & passages" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .toolbar { fill: #ffffff; stroke: #e1e4e8; stroke-width: 1; }
    .field-input { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .field-text { font: 12px sans-serif; fill: #2c3e50; }
    .field-ph { font: 12px sans-serif; fill: #bdc3c7; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .map-bg { fill: #e8efe6; stroke: #c9d6c5; stroke-width: 1; }
    .map-carre { fill: #c3caf0; stroke: #2c3e50; stroke-width: 1.2; }
    .map-carre-num { font: 10px sans-serif; fill: #2c3e50; }
    .map-point { fill: #1e8449; stroke: #ffffff; stroke-width: 1.5; }
    .map-point-lbl { font: 11px sans-serif; }
    .map-chip { fill: #ffffff; stroke: #c9d6c5; stroke-width: 1; }
    .map-chip-txt { font: 12px sans-serif; fill: #34495e; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 0.5; }
    .row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sec { font: 12px sans-serif; fill: #6a737d; }
    .badge-txt { font: 600 10px sans-serif; }
    .b-imp { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .b-trans { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .b-veri { fill: #fde7e7; stroke: #a93226; stroke-width: 1; }
    .b-pret { fill: #e8eefc; stroke: #4a90d9; stroke-width: 1; }
    .b-dep { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .b-ok { fill: #d4edda; stroke: #1e8449; stroke-width: 1; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="680" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="332" y="38" class="crumb-active">Vue multi-sites</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="78" class="pagesub">5 passage(s) affiché(s).</text>

  <!-- Barre de filtres (une rangée) + menu ☰ à droite -->
  <rect x="40" y="90" width="1120" height="46" rx="4" class="toolbar"/>
  <rect x="56" y="102" width="110" height="24" rx="3" class="field-input"/>
  <text x="68" y="119" class="field-ph">N° carré</text>
  <rect x="178" y="102" width="150" height="24" rx="3" class="field-input"/>
  <text x="190" y="119" class="field-text">Tous les statuts  ▾</text>
  <rect x="340" y="102" width="150" height="24" rx="3" class="field-input"/>
  <text x="352" y="119" class="field-text">Tous les verdicts  ▾</text>
  <rect x="502" y="102" width="90" height="24" rx="3" class="field-input"/>
  <text x="514" y="119" class="field-ph">Année</text>
  <rect x="604" y="102" width="140" height="24" rx="3" class="field-input"/>
  <text x="616" y="119" class="field-text">Tri : Par site  ▾</text>
  <rect x="756" y="102" width="110" height="24" rx="3" class="btn-secondary"/>
  <text x="811" y="119" class="btn-txt-dark" text-anchor="middle">Réinitialiser</text>
  <rect x="1100" y="102" width="44" height="24" rx="3" class="btn-secondary"/>
  <text x="1122" y="119" class="btn-txt-dark" text-anchor="middle">☰ ▾</text>

  <!-- ===== Carte (gauche) ===== -->
  <rect x="40" y="150" width="490" height="488" rx="4" class="map-bg"/>
  <rect x="120" y="290" width="150" height="120" class="map-carre"/>
  <text x="126" y="304" class="map-carre-num">640380</text>
  <circle cx="170" cy="360" r="6" class="map-point"/>
  <text x="182" y="356" class="map-point-lbl" fill="#1c2833">A1</text>
  <rect x="270" y="280" width="150" height="120" class="map-carre"/>
  <text x="276" y="294" class="map-carre-num">640381</text>
  <circle cx="345" cy="330" r="6" class="map-point"/>
  <text x="357" y="326" class="map-point-lbl" fill="#1c2833">B2</text>
  <rect x="488" y="160" width="32" height="26" rx="6" class="map-chip"/>
  <text x="504" y="178" class="map-chip-txt" text-anchor="middle">⤢</text>
  <rect x="50" y="600" width="110" height="28" rx="4" class="map-chip"/>
  <text x="64" y="618" class="map-chip-txt">Légende  ▸</text>

  <!-- ===== Tableau des passages (droite) ===== -->
  <rect x="546" y="150" width="614" height="30" class="table-head"/>
  <text x="560" y="170" class="col-head">CARRÉ</text>
  <text x="636" y="170" class="col-head">POINT</text>
  <text x="700" y="170" class="col-head">ANNÉE</text>
  <text x="760" y="170" class="col-head">N°</text>
  <text x="810" y="170" class="col-head">DATE</text>
  <text x="930" y="170" class="col-head">STATUT</text>
  <text x="1070" y="170" class="col-head">VERDICT</text>

  <rect x="546" y="180" width="614" height="28" class="row"/>
  <text x="560" y="199" class="cell">640380</text>
  <text x="636" y="199" class="cell">A1</text>
  <text x="700" y="199" class="cell">2025</text>
  <text x="760" y="199" class="cell">3</text>
  <text x="810" y="199" class="cell">2025-07-19</text>
  <rect x="925" y="185" width="100" height="18" rx="9" class="b-trans"/><text x="975" y="198" class="badge-txt" fill="#7e5109" text-anchor="middle">Transformé</text>
  <text x="1070" y="199" class="cell-sec">Non vérifié</text>

  <rect x="546" y="208" width="614" height="28" class="row-alt"/>
  <text x="560" y="227" class="cell">640380</text>
  <text x="636" y="227" class="cell">A1</text>
  <text x="700" y="227" class="cell">2026</text>
  <text x="760" y="227" class="cell">1</text>
  <text x="810" y="227" class="cell">2026-06-08</text>
  <rect x="925" y="213" width="80" height="18" rx="9" class="b-veri"/><text x="965" y="226" class="badge-txt" fill="#a93226" text-anchor="middle">Vérifié</text>
  <rect x="1065" y="213" width="70" height="18" rx="9" class="b-trans"/><text x="1100" y="226" class="badge-txt" fill="#7e5109" text-anchor="middle">Utilisable</text>

  <rect x="546" y="236" width="614" height="28" class="row"/>
  <text x="560" y="255" class="cell">640380</text>
  <text x="636" y="255" class="cell">A1</text>
  <text x="700" y="255" class="cell">2026</text>
  <text x="760" y="255" class="cell">2</text>
  <text x="810" y="255" class="cell">2026-06-22</text>
  <rect x="925" y="241" width="80" height="18" rx="9" class="b-dep"/><text x="965" y="254" class="badge-txt" fill="#2563a3" text-anchor="middle">Déposé</text>
  <rect x="1065" y="241" width="44" height="18" rx="9" class="b-ok"/><text x="1087" y="254" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>

  <rect x="546" y="264" width="614" height="28" class="row-alt"/>
  <text x="560" y="283" class="cell">640381</text>
  <text x="636" y="283" class="cell">B2</text>
  <text x="700" y="283" class="cell">2026</text>
  <text x="760" y="283" class="cell">1</text>
  <text x="810" y="283" class="cell">2026-06-15</text>
  <rect x="925" y="269" width="100" height="18" rx="9" class="b-pret"/><text x="975" y="282" class="badge-txt" fill="#2563a3" text-anchor="middle">Prêt à déposer</text>
  <rect x="1065" y="269" width="44" height="18" rx="9" class="b-ok"/><text x="1087" y="282" class="badge-txt" fill="#1e6f3f" text-anchor="middle">OK</text>

  <rect x="546" y="292" width="614" height="28" class="row"/>
  <text x="560" y="311" class="cell">640381</text>
  <text x="636" y="311" class="cell">B2</text>
  <text x="700" y="311" class="cell">2026</text>
  <text x="760" y="311" class="cell">2</text>
  <text x="810" y="311" class="cell">2026-06-29</text>
  <rect x="925" y="297" width="80" height="18" rx="9" class="b-imp"/><text x="965" y="310" class="badge-txt" fill="#2c3e50" text-anchor="middle">Importé</text>
  <text x="1070" y="311" class="cell-sec">Non vérifié</text>

  <text x="560" y="345" class="cell-sec">Astuce : double-cliquez une ligne pour ouvrir l'écran du passage.</text>

  <!-- ===== Replis carte / tableau, en bas aux extrémités ===== -->
  <rect x="40" y="600" width="100" height="28" rx="4" class="btn-secondary"/>
  <text x="90" y="618" class="btn-txt-dark" text-anchor="middle">◀ Carte</text>
  <rect x="1050" y="600" width="110" height="28" rx="4" class="btn-secondary"/>
  <text x="1105" y="618" class="btn-txt-dark" text-anchor="middle">Tableau ▶</text>

  <rect x="10" y="660" width="1180" height="30" class="footer"/>
  <text x="40" y="680" class="footer-txt">VigieChiro Companion · la carte montre TOUS les sites (non restreinte par les filtres du tableau)</text>
</svg>
</div>

### Annotations

- **Carte (à gauche)** : chaque **carré** (maille 2 km du carroyage national, indigo) affiche son numéro dans le coin ; ses **points d'écoute** sont des marqueurs **colorés selon le statut** du dernier passage. Un point **sans GPS** est posé **au centre de son carré** ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26), [R27](../Modèle%20conceptuel/Règles%20métier.md#r27)). Une **légende** repliée en bas à gauche et un bouton **⤢** (recadrer) en haut à droite complètent. La carte montre **tous** les sites : elle n'est **pas** restreinte par les filtres du tableau.
- **Tableau (à droite)** : un passage par ligne (carré, point, année, n° de passage, date, statut, verdict). On **trie** en cliquant un en-tête (Année et N° se trient numériquement) ; un **double-clic** ouvre [M-Passage](M-Passage.md).
- **Barre de filtres** : filtrer par carré, statut, verdict, année ; un sélecteur de **tri** ; un bouton **Réinitialiser** ; et un menu **☰** qui regroupe les actions secondaires (**Vues** enregistrées, **Exporter**).
- **Replis** : en bas, **◀ Carte** et **Tableau ▶** replient entièrement un panneau pour donner toute la largeur à l'autre ; on ne peut pas masquer les deux. Replier la carte est aussi la **dégradation élégante hors connexion** (le fond OpenStreetMap n'est alors pas joignable).

### Interactions clés

| Élément | Action |
|---|---|
| Filtres (carré, statut, verdict, année) | Restreignent le **tableau** (la carte reste complète) |
| Sélecteur **Tri** / clic d'en-tête | Trie le tableau |
| Menu **☰** | Ouvre **Vues** enregistrées / **Exporter** (CSV) |
| Double-clic sur une ligne | Ouvre [M-Passage](M-Passage.md) |
| **◀ Carte** / **Tableau ▶** | Replient / rouvrent un panneau |
| Marqueur de point (carte) | Survol = mini-stats ; en mode édition, glisser pour corriger la position (contraint au carré, [R26](../Modèle%20conceptuel/Règles%20métier.md#r26)) |

---

## Variante - vues sauvegardées

Une combinaison de filtres utile peut être **enregistrée sous un nom** pour être rejouée d'un clic, depuis le menu **☰ › Vues…**. La fenêtre permet d'**appliquer**, de **mettre à jour** ou de **supprimer** une vue.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 360" role="img" aria-label="Maquette M-MultiSite - Vues sauvegardées" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #6b7785;">
  <style>
    .modal-bg { fill: #6b7785; }
    .modal-frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .modal-header { fill: #3f51b5; }
    .modal-header-txt { fill: #ffffff; font: 600 16px sans-serif; }
    .list-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .list-row-sel { fill: #e8eefc; stroke: #4a90d9; stroke-width: 1; }
    .vue-name { font: 600 13px sans-serif; fill: #2c3e50; }
    .vue-detail { font: 12px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 600 12px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 12px sans-serif; }
  </style>
  <rect x="0" y="0" width="800" height="360" class="modal-bg"/>
  <rect x="180" y="40" width="440" height="280" rx="6" class="modal-frame"/>
  <rect x="180" y="40" width="440" height="44" rx="6" class="modal-header"/>
  <rect x="180" y="70" width="440" height="14" class="modal-header"/>
  <text x="202" y="70" class="modal-header-txt">⭐ Vues sauvegardées</text>

  <rect x="200" y="100" width="400" height="40" rx="4" class="list-row-sel"/>
  <text x="216" y="118" class="vue-name">Déposés 2026</text>
  <text x="216" y="133" class="vue-detail">Statut = Déposé · Année = 2026</text>

  <rect x="200" y="144" width="400" height="40" rx="4" class="list-row"/>
  <text x="216" y="162" class="vue-name">À revérifier</text>
  <text x="216" y="177" class="vue-detail">Verdict = Utilisable</text>

  <rect x="200" y="262" width="110" height="34" rx="4" class="btn-primary"/>
  <text x="255" y="283" class="btn-txt" text-anchor="middle">Appliquer</text>
  <rect x="320" y="262" width="120" height="34" rx="4" class="btn-secondary"/>
  <text x="380" y="283" class="btn-txt-dark" text-anchor="middle">Mettre à jour</text>
  <rect x="490" y="262" width="110" height="34" rx="4" class="btn-danger"/>
  <text x="545" y="283" class="btn-txt-danger" text-anchor="middle">Supprimer</text>
</svg>
</div>

---

## Variante - éditer les positions des points (carte)

Un bouton **✎** superposé en haut à gauche de la carte fait passer celle-ci en **mode édition** (la pince devient ambrée). On peut alors **glisser un marqueur** pour corriger le GPS d'un point ; le marqueur **reste dans son carré** ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26)). Un point **sans GPS** se **place** en le glissant depuis le centre de son carré ([R27](../Modèle%20conceptuel/Règles%20métier.md#r27)). Les déplacements s'accumulent jusqu'au clic sur **💾** (superposé sous la pince) ; quitter le mode édition avec des déplacements non enregistrés propose de les **Enregistrer**, **Abandonner** ou **Annuler**.

## Notes pour l'implémentation

- **Carte** : composant réutilisable (dépendance Gluon Maps) ; les carrés et points sont reprojetés depuis leur emprise du carroyage national (référentiel `carrenat` embarqué), avec **repli** autour des points géolocalisés si le carré est hors référentiel ([R26](../Modèle%20conceptuel/Règles%20métier.md#r26)).
- **Tableau** : `TableView` triée par un `SortedList` ; le tri par clic d'en-tête s'applique par-dessus l'ordre du service ; l'export reprend l'ordre réellement affiché.
- **Vues sauvegardées** : une vue persiste un **nom** et la combinaison de **filtres** (sérialisée).
- **Carte non filtrée** : la carte affiche **tous** les sites (vision d'ensemble) indépendamment des filtres du tableau ; un rafraîchissement dédié la met à jour au chargement et au retour.
