# M-Sites - Mes sites de suivi

> **Type** : vue principale (atteinte depuis la carte « Mes sites » de l'accueil).
> **Persona principal** : [Marie](../Personas/Marie.md), partagée avec [Karim](../Personas/Karim.md). [Samuel](../Personas/Samuel.md) bascule sur [M-MultiSite](M-MultiSite.md) pour sa volumétrie.
> **Parcours couverts** : [P1 - Déclarer un site de suivi](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md), [P12 - Récupérer une nuit déposée sur VigieChiro](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md).

L'écran liste tous les sites de suivi déclarés sous forme de **cartes**, avec pour chacun : le n° de carré, le nom convivial (et la commune), le nombre de points d'écoute, le nombre de passages enregistrés cette saison, et un badge de fraîcheur (date du dernier passage). Deux boutons sont toujours visibles en haut à droite : `+ Nouveau site` et `☁️ Récupérer depuis Vigie-Chiro`, qui **synchronise les sites et points depuis la plateforme** (utile après une réinstallation ou pour un compte déjà rempli côté web). Le clic sur une carte ouvre [M-Site-detail](M-Site-detail.md). L'écran est atteint depuis l'accueil ; le **fil d'Ariane** du chrome (`Accueil › Mes sites`) et la **recherche globale** sont posés dans le bandeau.

## Maquette principale - utilisateur avec plusieurs sites déclarés

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" role="img" aria-label="Maquette M-Sites - Mes sites de suivi" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 13px sans-serif; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .card { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .card-hover { fill: #f6f8fa; stroke: #4a90d9; stroke-width: 1.5; }
    .carre-no { font: 700 20px sans-serif; fill: #2c3e50; }
    .carre-name { font: 500 14px sans-serif; fill: #6a737d; }
    .stat-num { font: 700 18px sans-serif; fill: #4a90d9; }
    .stat-label { font: 11px sans-serif; fill: #6a737d; }
    .stat-divider { stroke: #d0d7de; stroke-width: 1; }
    .badge-fresh { fill: #d4edda; stroke: #27ae60; stroke-width: 1; }
    .badge-stale { fill: #fff3cd; stroke: #b9770e; stroke-width: 1; }
    .badge-cold { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .badge-fresh-txt { font: 600 11px sans-serif; fill: #1e6f3f; }
    .badge-stale-txt { font: 600 11px sans-serif; fill: #7e5109; }
    .badge-cold-txt { font: 600 11px sans-serif; fill: #2c3e50; }
    .chev { fill: #6a737d; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <!-- Cadre fenetre -->
  <rect x="10" y="10" width="1180" height="680" rx="4" class="frame"/>

  <!-- Bandeau (chrome) : titre + fil d'Ariane + recherche globale -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="300" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="372" y="38" class="crumb-active">Mes sites</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <!-- En-tete de page -->
  <text x="40" y="100" class="pagetitle">Mes sites de suivi</text>
  <text x="40" y="122" class="pagesub">3 sites déclarés · 5 passages enregistrés en 2026</text>

  <!-- Bouton secondaire "Recuperer depuis Vigie-Chiro" (synchro des sites, #1045) -->
  <rect x="724" y="82" width="250" height="40" rx="4" class="btn-secondary"/>
  <text x="849" y="107" class="btn-txt-dark" text-anchor="middle">☁️ Récupérer depuis Vigie-Chiro</text>

  <!-- Bouton primary "+ Nouveau site" -->
  <rect x="990" y="82" width="170" height="40" rx="4" class="btn-primary"/>
  <text x="1075" y="107" class="btn-txt" text-anchor="middle">+ Nouveau site</text>

  <!-- Carte site 1 : Carre 640380 (frais, dernier passage recent) - survolee -->
  <rect x="40" y="160" width="1120" height="116" rx="6" class="card-hover"/>
  <text x="64" y="196" class="carre-no">Carré 640380</text>
  <text x="64" y="218" class="carre-name">📍 Étang de la Tuilière (Ahetze)</text>
  <rect x="64" y="234" width="180" height="22" rx="11" class="badge-fresh"/>
  <text x="154" y="249" class="badge-fresh-txt" text-anchor="middle">Dernier passage : il y a 2 j</text>
  <line x1="620" y1="178" x2="620" y2="258" class="stat-divider"/>
  <text x="660" y="198" class="stat-num">3</text>
  <text x="660" y="216" class="stat-label">points d'écoute</text>
  <text x="660" y="238" class="carre-name">A1 · B2 · C3</text>
  <line x1="860" y1="178" x2="860" y2="258" class="stat-divider"/>
  <text x="900" y="198" class="stat-num">4</text>
  <text x="900" y="216" class="stat-label">passages en 2026</text>
  <text x="900" y="238" class="carre-name">dont 1 à vérifier ⚠</text>
  <text x="1145" y="222" class="chev" text-anchor="end" font-size="18">›</text>

  <!-- Carte site 2 : Carre 131165 (Marseille, passage il y a une semaine) -->
  <rect x="40" y="292" width="1120" height="116" rx="6" class="card"/>
  <text x="64" y="328" class="carre-no">Carré 131165</text>
  <text x="64" y="350" class="carre-name">📍 ZAC Nord (Marseille)</text>
  <rect x="64" y="366" width="180" height="22" rx="11" class="badge-stale"/>
  <text x="154" y="381" class="badge-stale-txt" text-anchor="middle">Dernier passage : il y a 5 j</text>
  <line x1="620" y1="310" x2="620" y2="390" class="stat-divider"/>
  <text x="660" y="330" class="stat-num">1</text>
  <text x="660" y="348" class="stat-label">point d'écoute</text>
  <text x="660" y="370" class="carre-name">A1</text>
  <line x1="860" y1="310" x2="860" y2="390" class="stat-divider"/>
  <text x="900" y="330" class="stat-num">1</text>
  <text x="900" y="348" class="stat-label">passage en 2026</text>
  <text x="900" y="370" class="carre-name">dont 1 à vérifier ⚠</text>
  <text x="1145" y="354" class="chev" text-anchor="end" font-size="18">›</text>

  <!-- Carte site 3 : Carre 131275 (Calanques, aucun passage) -->
  <rect x="40" y="424" width="1120" height="116" rx="6" class="card"/>
  <text x="64" y="460" class="carre-no">Carré 131275</text>
  <text x="64" y="482" class="carre-name">📍 Calanques (protocole recherche)</text>
  <rect x="64" y="498" width="140" height="22" rx="11" class="badge-cold"/>
  <text x="134" y="513" class="badge-cold-txt" text-anchor="middle">Aucun passage</text>
  <line x1="620" y1="442" x2="620" y2="522" class="stat-divider"/>
  <text x="660" y="462" class="stat-num">1</text>
  <text x="660" y="480" class="stat-label">point d'écoute</text>
  <text x="660" y="502" class="carre-name">A1</text>
  <line x1="860" y1="442" x2="860" y2="522" class="stat-divider"/>
  <text x="900" y="462" class="stat-num">0</text>
  <text x="900" y="480" class="stat-label">passage en 2026</text>
  <text x="900" y="502" class="carre-name">jamais utilisé</text>
  <text x="1145" y="486" class="chev" text-anchor="end" font-size="18">›</text>

  <!-- Footer -->
  <rect x="10" y="660" width="1180" height="30" class="footer"/>
  <text x="40" y="680" class="footer-txt">VigieChiro Companion · base locale : &lt;Documents&gt;/VigieChiro-Companion/vigiechiro.db</text>
</svg>
</div>

### Annotations

- **Bandeau (chrome)** : le titre, le **fil d'Ariane** (`Accueil › Mes sites`) et la **recherche globale** (champ « 🔍 Rechercher », raccourci Ctrl+F, cf. [P8](../Parcours%20utilisateurs/P8%20-%20Rechercher%20globalement.md)) sont communs à tous les écrans.
- **Carte 1 (Carré 640380, Ahetze)** : le site en cours d'utilisation, en surbrillance (survol). Le badge vert « il y a 2 j » et « 1 à vérifier ⚠ » incitent à enchaîner sur la vérification.
- **Carte 2 (Carré 131165, Marseille)** : site secondaire, dernier passage il y a quelques jours (badge orange).
- **Carte 3 (Carré 131275, Calanques)** : site déclaré mais jamais utilisé (badge gris). Montre qu'on prépare un site avant la première nuit. Le carré et les coordonnées de chaque site sont **cohérents** (le préfixe du carré correspond au département des points, cf. [R26](../Modèle%20conceptuel/Règles%20métier.md#r26)).

### Interactions clés

| Élément | Action |
|---|---|
| Clic sur une carte | Ouvre [M-Site-detail](M-Site-detail.md) avec le site sélectionné |
| Bouton **+ Nouveau site** | Ouvre le formulaire de création d'un site (n° de carré, points) |
| Bouton **☁️ Récupérer depuis Vigie-Chiro** | Synchronise les sites et points depuis la plateforme (rapatrie ceux qui existent côté web) |
| Champ **🔍 Rechercher** (ou Ctrl+F) | Recherche globale : saute à un site, un point ou un passage ([P8](../Parcours%20utilisateurs/P8%20-%20Rechercher%20globalement.md)) |
| Fil d'Ariane **Accueil** | Revient à l'accueil (cartes d'activités) |

---

## Variante - état vide (premier lancement)

À la première ouverture, aucun site n'est encore déclaré. Plutôt qu'une vue vide, l'écran guide explicitement l'utilisateur vers la création de son premier site.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" role="img" aria-label="Maquette M-Sites - État vide (premier lancement)" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .empty-icon { font: 80px sans-serif; fill: #d0d7de; }
    .empty-title { font: 700 22px sans-serif; fill: #2c3e50; }
    .empty-sub { font: 14px sans-serif; fill: #6a737d; }
    .btn-primary-big { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt-big { fill: #ffffff; font: 600 15px sans-serif; }
    .hint-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .hint-txt { font: 13px sans-serif; fill: #5d4e00; }
    .hint-title { font: 600 13px sans-serif; fill: #5d4e00; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="680" rx="4" class="frame"/>
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="300" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="372" y="38" class="crumb-active">Mes sites</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <text x="40" y="100" class="pagetitle">Mes sites de suivi</text>

  <!-- Zone centrale "vide" avec gros appel a l'action -->
  <text x="600" y="280" class="empty-icon" text-anchor="middle">🌐</text>
  <text x="600" y="340" class="empty-title" text-anchor="middle">Bienvenue ! Commencez par déclarer votre premier site.</text>
  <text x="600" y="370" class="empty-sub" text-anchor="middle">Un site = un carré Vigie-Chiro (6 chiffres) avec un ou plusieurs points d'écoute.</text>
  <text x="600" y="392" class="empty-sub" text-anchor="middle">L'import d'une nuit n'est possible qu'une fois un site déclaré.</text>

  <rect x="430" y="420" width="340" height="56" rx="6" class="btn-primary-big"/>
  <text x="600" y="455" class="btn-txt-big" text-anchor="middle">+ Ajouter mon premier site de suivi</text>

  <rect x="280" y="510" width="640" height="130" rx="6" class="hint-box"/>
  <text x="300" y="535" class="hint-title">💡 Vous n'avez pas encore créé votre site sur Vigie-Chiro ?</text>
  <text x="300" y="558" class="hint-txt">Le carré et les points se déclarent d'abord sur le portail web</text>
  <text x="300" y="578" class="hint-txt">vigiechiro.herokuapp.com ; récupérez ensuite le n° de carré (6 chiffres) et les</text>
  <text x="300" y="598" class="hint-txt">codes points (ex. A1, B2) pour les saisir ici.</text>
  <text x="300" y="624" class="hint-txt">🔗 https://vigiechiro.herokuapp.com (s'ouvre dans le navigateur)</text>

  <rect x="10" y="660" width="1180" height="30" class="footer"/>
  <text x="40" y="680" class="footer-txt">VigieChiro Companion · base locale initialisée</text>
</svg>
</div>

### Notes sur l'état vide

- Le bouton **+ Ajouter mon premier site** est dimensionné plus grand que le bouton normal pour le marquer comme l'**unique action** disponible.
- Le **rappel pédagogique** dans l'encart jaune est volontaire : Marie (persona débutante) peut ne pas savoir que les sites se créent d'abord sur le portail web. Mieux vaut le dire ici qu'attendre un message d'erreur.
- Dès le premier site créé, la vue bascule vers la maquette principale (une carte présente).

## Notes pour l'implémentation

- L'agrégation par carte (compteurs de points et de passages, date du dernier passage) est fournie par le service du modèle ; la vue ne fait que l'afficher.
- Le **survol** d'une carte est un effet visuel (pseudo-classe CSS / JavaFX), pas une carte différente.
- Les **badges de fraîcheur** sont calculés depuis la date du dernier passage :
    - vert si dernier passage < 7 jours,
    - orange si entre 7 et 30 jours,
    - gris si > 30 jours ou aucun passage.
- Le **fil d'Ariane** et la **recherche globale** appartiennent au chrome (socle `commun`), partagés par tous les écrans.
- **Points rapatriés** (récupération d'une nuit, [P12](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md)) : la synchronisation « mes sites » ramène **tous** les points du carré Vigie-Chiro (la grille STOC peut en compter des dizaines). Le bandeau des points d'une carte **résume** ceux qui sont rapatriés mais **sans passage** (« A1 · B2 · C3  (+ N rapatrié(s)) ») plutôt que de les lister un à un, pour ne pas noyer les points réellement utilisés. Un point rapatrié réapparaît nommément dès qu'un passage l'y rattache.
