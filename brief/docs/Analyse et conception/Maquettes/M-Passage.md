# M-Passage - Détail d'un passage

> **Type** : vue de détail (atteinte par clic sur une ligne de passage dans [M-Site-detail](M-Site-detail.md) ou [M-MultiSite](M-MultiSite.md)).
> **Persona principal** : tous. C'est l'écran pivot qui agrège les fonctionnalités liées à une nuit d'enregistrement spécifique.
> **Parcours couverts** : transverse - point d'entrée vers [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md).

C'est l'**écran pivot** d'un passage, présenté comme un **hub à plat** (une seule page, sans onglets). Il agrège, de haut en bas : l'en-tête (titre identifiant + actions sur le passage), un **bandeau d'identité** (date/plage, enregistreur, statut, verdict), le **indicateur d’étapes de statut d'avancement**, un **résumé de la nuit** (statistiques) et un jeu de **cartes d'actions « avancer »** vers les écrans spécialisés : [M-Qualification](M-Qualification.md) (vérifier), [M-Diagnostic](M-Diagnostic.md) (diagnostiquer), [M-Lot](M-Lot.md) (préparer le dépôt) et [M-SonsValidation](M-SonsValidation.md) (valider, verrouillée tant que le passage n'est pas déposé).

> **Navigation** : le **retour** (← écran précédent) et le **fil d'Ariane** (emplacement hiérarchique cliquable `🏠 Accueil › Mes sites › Carré N › Détails du passage N° X`) sont portés par le **chrome** de l'application (cadre commun), pas par cet écran. M-Passage ne porte donc plus de fil d'Ariane interne ni d'onglets : il déclare seulement son emplacement, que le chrome rend dans la barre de navigation.

## Maquette principale - pivot à plat

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 770" role="img" aria-label="Maquette M-Passage - hub à plat" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .backbtn { fill: #5566c4; stroke: #c5cae9; stroke-width: 1; }
    .backtxt { fill: #ffffff; font: 600 12px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagetitle { font: 700 24px sans-serif; fill: #2c3e50; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #a93226; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 600 13px sans-serif; }
    .btn-txt-danger { fill: #a93226; font: 600 13px sans-serif; }
    .info-bar { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .info-label { font: 11px sans-serif; fill: #6a737d; }
    .info-value { font: 600 14px sans-serif; fill: #2c3e50; }
    .info-mono { font: 600 13px monospace; fill: #2c3e50; }
    .section { font: 700 14px sans-serif; fill: #2c3e50; }
    .sc-done { fill: #1e8449; stroke: #0e5128; stroke-width: 1; }
    .sc-cur { fill: #4a90d9; stroke: #2563a3; stroke-width: 2; }
    .sc-pend { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .sl-done { stroke: #1e8449; stroke-width: 3; }
    .sl-pend { stroke: #d0d7de; stroke-width: 3; }
    .snum { fill: #ffffff; font: 700 12px sans-serif; }
    .snum-p { fill: #6a737d; font: 700 12px sans-serif; }
    .sl-d { font: 600 12px sans-serif; fill: #1e6f3f; }
    .sl-c { font: 700 12px sans-serif; fill: #2c3e50; }
    .sl-p { font: 12px sans-serif; fill: #6a737d; }
    .stat-card { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .stat-num { font: 700 24px sans-serif; fill: #4a90d9; }
    .stat-label { font: 11px sans-serif; fill: #6a737d; }
    .ac { fill: #ffffff; stroke: #d0d7de; stroke-width: 1; }
    .ac-primary { fill: #ffffff; stroke: #4a90d9; stroke-width: 2; }
    .ac-locked { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .ac-icon { font: 30px sans-serif; }
    .ac-title { font: 600 14px sans-serif; fill: #2c3e50; }
    .ac-title-l { font: 600 14px sans-serif; fill: #6a737d; }
    .ac-sub { font: 11px sans-serif; fill: #6a737d; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .footer-txt { font: 11px sans-serif; fill: #6a737d; }
  </style>

  <rect x="10" y="10" width="1180" height="750" rx="4" class="frame"/>
  <!-- Bandeau du chrome : titre + ← Retour (historique) + fil d'Ariane (emplacement) + recherche -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <rect x="226" y="22" width="96" height="22" rx="5" class="backbtn"/>
  <text x="274" y="38" class="backtxt" text-anchor="middle">← Mes sites</text>
  <text x="338" y="38" class="crumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  </text>
  <text x="650" y="38" class="crumb-curr">Passage N° 2</text>
  <rect x="940" y="22" width="220" height="22" rx="11" class="search"/>
  <text x="956" y="38" class="search-txt">🔍  Rechercher (Ctrl+F)</text>

  <!-- Titre + actions de tête -->
  <text x="40" y="124" class="pagetitle">Carré 640380 / A1 / N° 2 (2026)</text>
  <rect x="612" y="104" width="170" height="34" rx="4" class="btn-secondary"/>
  <text x="697" y="126" class="btn-txt-dark" text-anchor="middle">🗺 Voir sur la carte</text>
  <rect x="794" y="104" width="200" height="34" rx="4" class="btn-secondary"/>
  <text x="894" y="126" class="btn-txt-dark" text-anchor="middle">✏ Modifier le passage</text>
  <rect x="1006" y="104" width="154" height="34" rx="4" class="btn-danger"/>
  <text x="1083" y="126" class="btn-txt-danger" text-anchor="middle">🗑 Supprimer</text>

  <!-- Bandeau d'identité (4 cellules) -->
  <rect x="40" y="152" width="1120" height="58" rx="4" class="info-bar"/>
  <text x="60" y="174" class="info-label">DATE / PLAGE</text>
  <text x="60" y="195" class="info-value">2026-06-22  20:25 → 07:47</text>
  <text x="430" y="174" class="info-label">ENREGISTREUR</text>
  <text x="430" y="195" class="info-mono">PR 1925492</text>
  <text x="700" y="174" class="info-label">STATUT</text>
  <text x="700" y="195" class="info-value">Vérifié</text>
  <text x="900" y="174" class="info-label">VERDICT</text>
  <text x="900" y="195" class="info-value">OK</text>

  <!-- Indicateur d’étapes de statut (5 étapes) -->
  <text x="40" y="244" class="section">Statut du workflow</text>
  <line x1="130" y1="276" x2="350" y2="276" class="sl-done"/>
  <line x1="370" y1="276" x2="590" y2="276" class="sl-done"/>
  <line x1="610" y1="276" x2="830" y2="276" class="sl-pend"/>
  <line x1="850" y1="276" x2="1070" y2="276" class="sl-pend"/>
  <circle cx="130" cy="276" r="18" class="sc-done"/><text x="130" y="281" class="snum" text-anchor="middle">1</text><text x="130" y="311" class="sl-d" text-anchor="middle">Importé</text>
  <circle cx="350" cy="276" r="18" class="sc-done"/><text x="350" y="281" class="snum" text-anchor="middle">2</text><text x="350" y="311" class="sl-d" text-anchor="middle">Transformé</text>
  <circle cx="590" cy="276" r="18" class="sc-cur"/><text x="590" y="281" class="snum" text-anchor="middle">3</text><text x="590" y="311" class="sl-c" text-anchor="middle">Vérifié</text>
  <circle cx="830" cy="276" r="18" class="sc-pend"/><text x="830" y="281" class="snum-p" text-anchor="middle">4</text><text x="830" y="311" class="sl-p" text-anchor="middle">Prêt à déposer</text>
  <circle cx="1070" cy="276" r="18" class="sc-pend"/><text x="1070" y="281" class="snum-p" text-anchor="middle">5</text><text x="1070" y="311" class="sl-p" text-anchor="middle">Déposé</text>

  <!-- Résumé de la nuit (4 cartes de stats) -->
  <text x="40" y="352" class="section">Résumé de la nuit</text>
  <rect x="40" y="366" width="265" height="74" rx="4" class="stat-card"/><text x="172" y="406" class="stat-num" text-anchor="middle">38,4 Go</text><text x="172" y="426" class="stat-label" text-anchor="middle">VOLUME BRUTS</text>
  <rect x="325" y="366" width="265" height="74" rx="4" class="stat-card"/><text x="457" y="406" class="stat-num" text-anchor="middle">17 Go</text><text x="457" y="426" class="stat-label" text-anchor="middle">VOLUME TRANSFORMÉ</text>
  <rect x="610" y="366" width="265" height="74" rx="4" class="stat-card"/><text x="742" y="406" class="stat-num" text-anchor="middle">5 h 1 min</text><text x="742" y="426" class="stat-label" text-anchor="middle">DURÉE ENREGISTRÉE</text>
  <rect x="895" y="366" width="265" height="74" rx="4" class="stat-card"/><text x="1027" y="406" class="stat-num" text-anchor="middle">3 614</text><text x="1027" y="426" class="stat-label" text-anchor="middle">SÉQUENCES</text>

  <!-- Actions « avancer » (cartes). Vérifier = mise en avant ; Validation = verrouillée. -->
  <text x="40" y="476" class="section">Actions</text>
  <rect x="40" y="490" width="265" height="152" rx="6" class="ac"/><text x="172" y="536" class="ac-icon" text-anchor="middle">🎧</text><text x="172" y="568" class="ac-title" text-anchor="middle">Vérifier l'enregistrement</text><text x="172" y="588" class="ac-sub" text-anchor="middle">Sound check par échantillonnage</text>
  <rect x="325" y="490" width="265" height="152" rx="6" class="ac"/><text x="457" y="536" class="ac-icon" text-anchor="middle">🛠</text><text x="457" y="568" class="ac-title" text-anchor="middle">Diagnostic matériel</text><text x="457" y="588" class="ac-sub" text-anchor="middle">T°/hygrométrie, anomalies, journal</text>
  <rect x="610" y="490" width="265" height="152" rx="6" class="ac-primary"/><text x="742" y="536" class="ac-icon" text-anchor="middle">📦</text><text x="742" y="568" class="ac-title" text-anchor="middle">Préparer le dépôt</text><text x="742" y="588" class="ac-sub" text-anchor="middle">Constituer le dépôt Tadarida</text><rect x="662" y="608" width="160" height="16" rx="8" fill="#4a90d9"/><text x="742" y="620" class="snum" text-anchor="middle" font-size="10">▶ ACTION REQUISE</text>
  <rect x="895" y="490" width="265" height="152" rx="6" class="ac-locked"/><text x="1027" y="536" class="ac-icon" text-anchor="middle">🔒</text><text x="1027" y="568" class="ac-title-l" text-anchor="middle">Validation Tadarida</text><text x="1027" y="588" class="ac-sub" text-anchor="middle">Disponible après dépôt</text>

  <text x="40" y="668" class="ac-sub">ℹ La carte mise en avant indique la prochaine étape recommandée (ici : préparer le dépôt). L'indice s'adapte au statut.</text>

  <rect x="10" y="730" width="1180" height="30" class="footer"/>
  <text x="40" y="749" class="footer-txt">Hub d'un passage · retour &amp; fil d'Ariane portés par le chrome (plus d'onglets, plus de fil interne)</text>
  <text x="1140" y="749" class="footer-txt" text-anchor="end">Statut : Vérifié</text>
</svg>
</div>

### Annotations

- **Barre du chrome (cadre commun)** : le bouton **← Retour** ramène à l'écran réellement précédent (historique de navigation) ; le **fil d'Ariane** affiche l'emplacement hiérarchique cliquable (`🏠 Accueil › Mes sites › Carré N › Détails du passage N° X`). Ces deux affordances ne sont **pas** propres à M-Passage : elles sont rendues par le chrome sur tous les écrans (navigation homogène). M-Passage déclare seulement son emplacement.
- **En-tête** : le titre identifie le passage (`Carré N / Point / N° X (année)`). Les actions de tête sont **contextuelles selon l'état** ; il en existe **six** :
    - **toujours** : **🗺 Voir sur la carte**, **✏ Modifier le passage** (année, n° de passage, avec re-renommage des fichiers), **🗑 Supprimer** (refusé si le passage est déposé) ;
    - **après dépôt** : **🔗 Voir la participation** (ouvre la fiche de la participation sur la plateforme), **↩ Annuler le dépôt** ;
    - si le passage est **archivé** (audio absent du disque) : **♻ Réactiver ce passage**.

    L'écran ci-dessus montre l'état **Vérifié** (pas encore déposé) : seules les **trois actions permanentes** s'y affichent.
- **Bandeau d'identité** : 4 cellules condensées (date/plage horaire, enregistreur en monospace, statut d'avancement, verdict de vérification).
- **Indicateur d’étapes du statut** : 5 étapes `Importé › Transformé › Vérifié › Prêt à déposer › Déposé`. Vert = franchi, bleu = étape courante, gris = à venir.
- **Résumé de la nuit** : 4 statistiques clés (volume bruts, volume transformé, durée enregistrée, nombre de séquences). La **durée enregistrée** est la durée réelle captée par l'enregistreur (somme des durées des séquences), pas la durée d'écoute : les séquences étant ralenties ×10, les réécouter intégralement prend dix fois plus longtemps.
- **Actions** : 4 **cartes** « avancer ». Une seule porte le **liseré « recommandée »** (prochaine étape du cycle), qui **se déplace au fil de l'avancement** : Vérifier (à `Transformé`) → Préparer le dépôt (à `Vérifié` / `Prêt à déposer`, état montré ici) → Validation Tadarida (à `Déposé`). `Diagnostic matériel` est une action transverse, toujours disponible mais jamais « recommandée ». `Validation Tadarida` est **verrouillée** (carte grisée, non cliquable) tant que le passage n'est pas `Déposé`. Un **indice contextuel** sous les cartes explique l'action attendue / les conditions de déverrouillage.

### Interactions clés

| Élément | Action |
|---|---|
| **← Retour** (chrome) | Revient à l'écran précédent réel (historique), sans repasser par l'accueil |
| **Fil d'Ariane** (chrome) | Remonte à un ancêtre (Accueil, Mes sites, Carré N) - emplacement cliquable |
| Bouton **🗺 Voir sur la carte** | Ouvre [M-MultiSite](M-MultiSite.md) centré sur le point du passage |
| Bouton **✏ Modifier le passage** | Ouvre la **modale** d'édition (année, n° de passage), avec re-renommage des fichiers (voir variante ci-dessous) |
| Bouton **🗑 Supprimer** | Confirmation forte (suppression de la nuit et des fichiers) ; refusée si le passage est déposé |
| Bouton **🔗 Voir la participation** *(après dépôt)* | Ouvre la fiche de la participation sur la plateforme |
| Bouton **↩ Annuler le dépôt** *(après dépôt)* | Ramène le passage à un état pré-dépôt (le dépôt en ligne n'est pas défait automatiquement) |
| Bouton **♻ Réactiver ce passage** *(si archivé)* | Retrouve les fichiers d'un passage dont l'audio a été purgé ([E4.S6](../Story%20mapping/E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md#e4s6)) |
| Carte **🎧 Vérifier l'enregistrement** | Ouvre [M-Qualification](M-Qualification.md) (active dès que la nuit est transformée) |
| Carte **🛠 Diagnostic matériel** | Ouvre [M-Diagnostic](M-Diagnostic.md) (toujours disponible : relevé climatique + journal) |
| Carte **📦 Préparer le dépôt** | Ouvre [M-Lot](M-Lot.md) (active en phase de dépôt : `Vérifié` ou `Prêt à déposer`) |
| Carte **✅ Validation Tadarida** | Ouvre [M-SonsValidation](M-SonsValidation.md) - **verrouillée** tant que le passage n'est pas `Déposé` |

> **Diagnostic matériel** : son contenu (graphes T° / hygrométrie, batterie, évènements anormaux du `LogPR`) n'est pas un onglet de M-Passage mais un **écran à part entière** - voir [M-Diagnostic](M-Diagnostic.md), ouvert depuis la carte « Diagnostic matériel ».

## Variante - modale « Modifier le passage »

Le bouton **✏ Modifier le passage** ouvre une **modale** d'édition de l'**identité** du passage. Ce n'est pas un simple formulaire : changer ces valeurs **renomme tous les fichiers** de la session.

- **Champs éditables** : **année** et **n° de passage** (le site et le point ne se changent pas ici) ; les valeurs courantes sont pré-remplies.
- **Conséquence annoncée** : la modification recompose le **préfixe** `CarXXXXXX-AAAA-PassN-YY-` ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6)) et **re-renomme** tous les enregistrements et séquences ([R7](../Modèle%20conceptuel/Règles%20métier.md#r7)) ; la modale l'explique avant de valider.
- **Garde d'unicité** : la nouvelle combinaison `(Site, Point, Année, n° de passage)` doit rester **unique** ([R5](../Modèle%20conceptuel/Règles%20métier.md#r5)) ; une collision est refusée.
- **Confirmation forte** : l'opération touchant le disque, elle demande une confirmation explicite (**Enregistrer** / **Annuler**).

## Notes pour l'implémentation

- **Pivot à plat** : une seule page (`BorderPane` → `top` en-tête/bandeau/indicateur d'étapes, `center` résumé + cartes), sans `TabPane`. Le retrait des onglets supprime la redondance « onglet-lanceur ↔ carte d'action » : chaque facette du passage (vérification, diagnostic, dépôt, validation) est un **écran spécialisé** ouvert par une carte via un contrat socle (`Ouvrir*`), avec retour assuré par le chrome.
- **Navigation portée par le chrome** : M-Passage implémente le contrat `EmplacementNavigation` pour déclarer son chemin (`Mes sites › Carré N › Détails du passage N° X`) ; le chrome en dérive le fil d'Ariane et conserve l'historique pour le bouton Retour. Aucun fil ni retour interne à l'écran.
- **Indicateur d’étapes de statut** : 5 étapes, statut courant calculé depuis l'attribut d'avancement du passage.
- **États des cartes** : `Vérifier` activée dès `Transformé` ; `Préparer le dépôt` activée à `Vérifié`/`Prêt à déposer` ; `Validation Tadarida` verrouillée tant que ≠ `Déposé`. Les états sont liés aux propriétés du ViewModel (`verificationDisponible`, `depotDisponible`, `validationVerrouillee`).
- **Mise en avant dynamique** : la carte de la prochaine étape porte une pseudo-classe CSS `recommandee` (liseré bleu), pilotée par `actionRecommandee` (dérivée du statut). La mise en avant se déplace donc avec l'avancement, au lieu de rester figée sur la première action.
- **Icônes** : `FontIcon` (Ikonli FontAwesome5) pour un rendu net, y compris en capture headless.
- **Action « Modifier le passage »** : re-renomme tous les fichiers du passage (R6/R7). Confirmation forte obligatoire.

## Enrichissements prévus

> Décidé et maquetté, pas encore livré.

- Deux cartes d'action s'ajoutent à celles du passage, disponibles une fois les résultats
  d'identification importés : **Synthèse de la nuit** ([M-Synthese](M-Synthese.md), #2351),
  qui replace les comptages par espèce dans un référentiel de saison, de région et de
  milieu, et **Activité** ([M-Activite](M-Activite.md), #2352), qui trace les contacts par
  tranche horaire sur l'axe nocturne.
- Les deux suivent la règle d'activation déjà en place : verrouillées tant que le passage
  n'a pas d'observations, comme « Sons & validation » l'est tant que la nuit n'est pas
  déposée.
