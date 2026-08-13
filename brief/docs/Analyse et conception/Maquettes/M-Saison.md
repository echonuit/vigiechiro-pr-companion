# M-Saison - Solde de la saison

> **Type** : écran **« Ma saison »**, carte d'activité du prisme *Collecte & passages* de [M-Accueil](M-Accueil.md).
> **Persona principal** : [Marie](../Personas/Marie.md) (savoir ce qu'il lui reste à faire) et [Samuel](../Personas/Samuel.md) (suivre l'avancement d'un ensemble de points).
> **Parcours couverts** : complète [P5 - Naviguer dans plusieurs sites et passages](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md) et [P9 - Regrouper les nuits successives par point](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md).
> **Issues** : #2356 (écran), #2355 (campagne), #2525 (participations opportunistes), #2610 (filtre campagne dans l'écran, livré) - chantier #2349.

L'écran répond à une seule question, celle qu'un observateur se pose au milieu de sa saison : **qu'est-ce qu'il me reste à faire ?** Le protocole demande deux passages par an et par point, dans des fenêtres calendaires ([règles métier](../Modèle%20conceptuel/Règles%20métier.md) R3 et R4). L'application connaît la règle et la vérifie déjà ; cet écran est ce qui la **restitue**.

> **Un état se décrit, une action se fait.** La colonne « Reste à faire » est le cœur de l'écran : « Téléverser la nuit du 22/06 » s'exécute, « Prêt à déposer » demande encore un raisonnement. C'est la seule colonne qui transforme un tableau de suivi en plan de travail.

## Maquette principale - saison en cours

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 520" role="img" aria-label="Maquette M-Saison - solde des passages de la saison, point par point" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagesub { font: 13px sans-serif; fill: #4a6785; }
    .tab { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .tab-on { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .tab-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .tab-on-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .list-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .list-row-alt { fill: #f6f8fa; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .cell-sub { font: 11px sans-serif; fill: #6a737d; }
    .todo { font: 600 12px sans-serif; fill: #1e5f8a; }
    .todo-none { font: 12px sans-serif; fill: #6a737d; }
    .pill-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .p-depose { fill: #1e8449; }
    .p-encours { fill: #2c6fad; }
    .p-absent { fill: #a93226; }
    .p-refaire { fill: #b9770e; }
    .p-opportuniste { fill: #ede9fe; stroke: #c4b5fd; stroke-width: 1; }
    .pill-txt-opp { font: 600 11px sans-serif; fill: #5b21b6; text-anchor: middle; }
    .warn-txt { font: 12px sans-serif; fill: #7e5109; }
    .statusbar { fill: #eceff5; stroke: #d0d7de; stroke-width: 1; }
    .status-txt { font: 12px sans-serif; fill: #4a6785; }
  </style>

  <rect x="0" y="0" width="1000" height="520" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome"/>
  <text x="20" y="28" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb">Accueil  ›  </text>
  <text x="278" y="28" class="crumb-curr">Ma saison</text>
  <rect x="780" y="12" width="200" height="22" rx="11" class="search"/>
  <text x="794" y="28" class="search-txt">Rechercher (Ctrl+F)</text>

  <!-- Selecteur d'annee -->
  <rect x="30" y="62" width="58" height="24" rx="3" class="tab-on"/><text x="59" y="79" class="tab-on-txt">2026</text>
  <rect x="94" y="62" width="58" height="24" rx="3" class="tab"/><text x="123" y="79" class="tab-txt">2025</text>
  <rect x="158" y="62" width="58" height="24" rx="3" class="tab"/><text x="187" y="79" class="tab-txt">2024</text>

  <text x="970" y="79" class="pagesub" text-anchor="end">4 points suivis · 5 faits, 1 à refaire, 2 à réaliser · 1 hors protocole · fenêtre du 2ᵉ passage : 31/08</text>

  <!-- Tableau -->
  <rect x="30" y="104" width="940" height="286" class="list-frame"/>
  <rect x="30" y="104" width="940" height="26" class="list-head"/>
  <text x="42" y="121" class="head-txt">Carré</text>
  <text x="112" y="121" class="head-txt">Point</text>
  <text x="172" y="121" class="head-txt">Passage 1</text>
  <text x="357" y="121" class="head-txt">Passage 2</text>
  <text x="542" y="121" class="head-txt">Hors protocole</text>
  <text x="700" y="121" class="head-txt">Reste à faire</text>

  <!-- ligne 1 : P2 a televerser -->
  <text x="42" y="151" class="cell">640380</text>
  <text x="112" y="151" class="cell-b">A1</text>
  <rect x="172" y="140" width="118" height="16" rx="8" class="p-depose"/><text x="231" y="152" class="pill-txt">Déposé · 22/05</text>
  <rect x="357" y="140" width="140" height="16" rx="8" class="p-encours"/><text x="427" y="152" class="pill-txt">Prêt à déposer · 22/06</text>
  <text x="700" y="151" class="todo">Téléverser la nuit du 22/06</text>

  <!-- ligne 2 : rien a faire -->
  <rect x="31" y="161" width="938" height="30" class="list-row-alt"/>
  <text x="42" y="181" class="cell">640380</text>
  <text x="112" y="181" class="cell-b">B2</text>
  <rect x="172" y="170" width="118" height="16" rx="8" class="p-depose"/><text x="231" y="182" class="pill-txt">Déposé · 24/05</text>
  <rect x="357" y="170" width="118" height="16" rx="8" class="p-depose"/><text x="416" y="182" class="pill-txt">Déposé · 25/06</text>
  <text x="700" y="181" class="todo-none">rien</text>

  <!-- ligne 3 : P2 non planifie, avec une nuit hors protocole sur le meme point -->
  <text x="42" y="211" class="cell">640250</text>
  <text x="112" y="211" class="cell-b">A1</text>
  <rect x="172" y="200" width="118" height="16" rx="8" class="p-depose"/><text x="231" y="212" class="pill-txt">Déposé · 19/05</text>
  <rect x="357" y="200" width="106" height="16" rx="8" class="p-absent"/><text x="410" y="212" class="pill-txt">Non planifié</text>
  <rect x="542" y="200" width="136" height="16" rx="8" class="p-opportuniste"/><text x="610" y="212" class="pill-txt-opp">Opportuniste · 12/07</text>
  <text x="700" y="211" class="todo">Poser l'enregistreur avant le 31/08</text>

  <!-- ligne 4 : P1 inexploitable -->
  <rect x="31" y="221" width="938" height="30" class="list-row-alt"/>
  <text x="42" y="241" class="cell">640315</text>
  <text x="112" y="241" class="cell-b">C1</text>
  <rect x="172" y="230" width="140" height="16" rx="8" class="p-refaire"/><text x="242" y="242" class="pill-txt">Inexploitable · 21/05</text>
  <rect x="357" y="230" width="106" height="16" rx="8" class="p-absent"/><text x="410" y="242" class="pill-txt">Non planifié</text>
  <text x="700" y="241" class="todo">Refaire le 1ᵉʳ passage</text>

  <!-- avertissement fenetre -->
  <text x="42" y="292" class="warn-txt">La fenêtre du second passage se referme dans 40 jours pour 2 points.</text>

  <text x="42" y="368" class="cell-sub">Astuce : double-cliquez une ligne pour ouvrir le passage concerné, ou le point s'il n'existe pas encore.</text>

  <!-- Barre de statut -->
  <rect x="0" y="500" width="1000" height="20" class="statusbar"/>
  <text x="12" y="514" class="status-txt">Saison 2026</text>
  <text x="500" y="514" class="status-txt" text-anchor="middle">3 nuits à traiter · 1 à refaire</text>
  <text x="988" y="514" class="status-txt" text-anchor="end">5 / 8 passages</text>
</svg>
</div>

### Annotations

- **Sélecteur d'année** (`groupeAnnee`) : la saison courante par défaut ; les saisons antérieures restent consultables en lecture.
- **Résumé d'en-tête** (`lblResume`) : les passages attendus **ventilés** - faits, à refaire, à réaliser - puis l'**échéance de la fenêtre du second passage**. La somme des trois vaut le total attendu (5 + 1 + 2 = 8 ci-dessus) : un décompte qui ne ferme pas laisse chercher où sont les manquants, ce qu'une proportion seule (« 5 sur 8 ») faisait. Les **nuits hors protocole** se comptent **à côté**, jamais dans le total, et la mention disparaît s'il n'y en a aucune. Ce décompte et les lignes du tableau proviennent de la **même source** : ils ne peuvent pas diverger.
- **La campagne est un filtre, pas une colonne** (issue #2355). Une ligne du solde est un **point**, avec ses **deux** passages, qui peuvent relever de campagnes différentes : une colonne aurait dû en choisir une à afficher. Le filtre retient un point dès qu'**au moins un** de ses passages relève de la campagne, et le montre **en entier** - c'est l'état complet du point qui dit ce qu'il reste à y faire.
    Le sélecteur **Campagne** (`choixCampagne`) est posé à côté de celui de l'année. Il n'apparaît que s'il y a une campagne à proposer : coupée la fonctionnalité, ou aucune campagne créée, il est **retiré de la mise en page** (`managed=false`) et non simplement masqué - sans quoi il laisserait un trou dans la barre.
- **Deux filtres s'ajoutent aux sélecteurs, sans les remplacer** (issue #3103, chantier #3092). Cet écran
  est le seul tableau de l'application à **ne pas** prendre la barre à puces du socle, et c'est délibéré :
  une saison **est** une année et une campagne, et garder ces deux sélecteurs toujours visibles donne la
  lecture immédiate « je suis sur telle saison » qu'une puce parmi d'autres ferait perdre. C'est un
  **tableau de bord**, pas une table exploratoire.
    La limite était ailleurs : sur un jeu conséquent, les deux questions qu'on pose à la liste n'avaient
    aucune réponse directe. Un champ **« Chercher un lieu »** (`champRechercheLieu`) retient les points
    dont le carré, le nom qu'on lui a donné, le code du point ou la **commune** correspond ; une case **« Reste à faire »**
    (`caseResteAFaire`) ne garde que les points qui ne sont pas à jour - la raison d'être de l'écran.
    ⚠️ Ces deux filtres ne touchent **que la liste des points** : le résumé d'en-tête continue de compter
    la **saison entière**. Filtrer change ce qu'on regarde, pas ce qu'il y a à faire
    ([ADR 3092](https://companion-dev.echonuit.fr/decisions/3092-un-filtre-ne-change-que-ce-quon-regarde/)).
    Les deux existent aussi en ligne de commande (`solde-saison --lieu`, `--reste-a-faire`), sur la même
    écriture de la règle.
- **Colonnes « Nom du carré » et « Commune »** (`colNomSite`, `colCommune`, chantier #3151) : le lieu
  se **montre** là où il se cherche. Le champ « Chercher un lieu » retenait une ligne sur le nom du carré
  (#3219) puis sur la commune, et cet écran n'ayant **pas** de puce « Lieu » - contrairement aux quatre
  autres tableaux - ces deux valeurs n'apparaissaient nulle part : on trouvait sans voir pourquoi
  ([ADR 3151](https://companion-dev.echonuit.fr/decisions/3151-un-ecran-n-offre-pas-ce-qu-il-ne-montre-pas/)).
    Les deux cellules restent **vides** quand il n'y a rien à dire - un carré sans nom, un point dont la
    commune n'est pas résolue - comme « Hors protocole » quand aucune nuit opportuniste n'existe.
    ⚠️ La **forme** de ces colonnes s'est jouée sur des captures, pas sur un raisonnement. Qualifier
    « Carré » en « 640380 · Vallon », comme les entrées de puce des autres écrans, paraissait évident :
    l'aperçu régénéré montrait « 640380 · … ». Il a fallu une colonne dédiée, puis élargir l'aperçu, pour
    que le nom **et** « Reste à faire » tiennent ensemble. Le croquis ci-dessus, lui, date d'avant ces
    deux colonnes.
- **Colonne « Hors protocole »** (`colHorsProtocole`, issue #2525) : les nuits **opportunistes** réalisées sur ce point. Elles ont leur colonne précisément pour ne pas occuper celle d'un passage attendu : les voir en « Passage 1 » se lirait « le passage 1 est fait » alors que la ligne réclame encore de poser l'enregistreur (ligne 3 de la maquette). Les colonnes de passage restent donc sur « Non planifié » tant que le passage du protocole manque réellement, et ces nuits ne comptent pas dans le décompte ([R34](../Modèle%20conceptuel/Règles%20métier.md#r34)).
- **Colonnes Passage 1 et Passage 2** (`colPassage1`, `colPassage2`) : l'état du passage et sa date, ou son absence. **Les états et leurs couleurs sont repris du modèle existant** ; l'écran ne crée pas un second vocabulaire de statuts, ce qui obligerait l'utilisateur à en apprendre deux.
- **Un passage inexploitable compte comme restant à faire** : c'est le cas où un décompte naïf induit en erreur, puisque le passage existe en base mais ne vaut rien pour le protocole.
- **Colonne « Reste à faire »** (`colResteAFaire`) : **le cœur de l'écran**. Elle formule une action, pas un état. Elle est vide (« rien ») quand le point est à jour.
- **Signalement de fenêtre** (`lblFenetre`) : une ligne indique combien de points ont une fenêtre calendaire proche de la clôture. L'application **signale**, elle n'alerte pas : elle ne pose pas de rappel et ne planifie pas de sortie terrain.
- **Barre de statut** : saison courante, travail restant, avancement global.

## Variante - aucun passage cette saison

Un observateur qui ouvre l'écran en début de saison, ou qui vient de déclarer ses premiers points, doit y trouver par où commencer plutôt qu'un tableau vide.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 190" role="img" aria-label="Maquette M-Saison - variante aucun passage pour la saison" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .empty-txt { font: 600 14px sans-serif; fill: #4a6785; text-anchor: middle; }
    .empty-sub { font: 12px sans-serif; fill: #6a737d; text-anchor: middle; }
    .ctrl-pri { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .ctrl-pri-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
  </style>
  <rect x="0" y="0" width="1000" height="190" fill="#f7f9fb"/>
  <rect x="30" y="20" width="940" height="150" class="list-frame"/>
  <text x="500" y="70" class="empty-txt">Aucun passage enregistré pour la saison 2026.</text>
  <text x="500" y="94" class="empty-sub">Vos 4 points d'écoute attendent leur premier passage. Le premier est attendu à partir du 15 juin.</text>
  <rect x="420" y="114" width="160" height="26" rx="3" class="ctrl-pri"/>
  <text x="500" y="132" class="ctrl-pri-txt">Importer une nuit</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| Double-clic sur une ligne | Ouvre le **passage** concerné, ou le **point** s'il n'existe pas encore de passage |
| Clic sur une pastille d'état | Ouvre le passage correspondant |
| Changement d'année | Recharge le solde pour la saison choisie, en lecture pour les saisons passées |
| Survol de « Reste à faire » | Infobulle : la règle de protocole qui fonde cette action (R3 ou R4) |
| Aucun passage | État vide expliquant par où commencer, avec l'accès direct à l'import |

## Notes pour l'implémentation

- **Les règles restent dans `passage/model/ServicePassage`** : l'écran les **consomme**, il ne les réimplémente pas. C'est la condition pour que R3 et R4 n'existent pas en deux exemplaires divergents.
- **Carte d'accueil** via le contrat `ActiviteAccueil` et le prisme *Collecte & passages*, sans toucher au chrome ([M-Accueil](M-Accueil.md)).
- **Rafraîchissement** : le tableau et le résumé se recalculent quand les données changent. Le défaut déjà signalé sur les compteurs d'accueil (#1376) ne doit pas se reproduire ici. Cette exigence était **plus ancienne que le moyen de la tenir** : l'écran a d'abord rechargé au retour de navigation, faute de mieux, ce qui laissait passer tout ce qui survenait pendant qu'on le regardait. Il suit désormais le **signal de mutation** du socle, et l'exigence est tenue au sens propre.
- **`TableView`** avec une `cellFactory` pour les pastilles d'état, réutilisant les couleurs de statut existantes de [M-MultiSite](M-MultiSite.md).
- **Icônes** : `FontIcon` Ikonli, pas d'emoji (règle #700).
- **Fonctionnalité optionnelle** : l'écran doit rester cohérent quand la campagne est désactivée. Le service la consomme en `Optional<ServiceCampagne>` ; coupée, aucun point ne relève d'une campagne et le solde reste entier. Le futur sélecteur ([#2610](https://github.com/echonuit/vigiechiro-pr-companion/issues/2610)) ne doit alors pas apparaître.
- **Carrés d'un tiers exclus** : le solde ne parle que des carrés de l'observateur. Un carré rattaché à quelqu'un d'autre ([R35](../Modèle%20conceptuel/Règles%20métier.md#r35)) sort entièrement du tableau - y participer est une occasion, pas une obligation de protocole.
