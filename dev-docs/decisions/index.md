# Décisions d'architecture (ADR)

Ce journal consigne les **décisions structurantes** du projet : les choix qui engagent l'architecture ou le domaine sur la durée, et surtout **pourquoi** ils ont été faits. Une décision oubliée se re-débat ; écrite, elle se relit.

## Ce qu'est (et n'est pas) une ADR

Une **ADR** (Architecture Decision Record) décrit **une** décision : son contexte, ce qui a été tranché, ses conséquences, et les pistes écartées. Elle est **immuable** une fois acceptée : on ne la réécrit pas, on en écrit une nouvelle qui la remplace (statut « Remplacée par ADR NNNN »).

Une ADR n'est **pas** :

- un compte rendu de chantier (ça, c'est le **bilan**, déposé dans le corps de l'EPIC à sa clôture) ;
- une description de l'implémentation (ça, c'est le code et sa Javadoc, ou [Patterns et principes](../patterns.md)) ;
- une note de rappel opérationnelle (« attention au piège X ») : ces notes vivent ailleurs.

On écrit une ADR quand un chantier prend une décision qu'un développeur futur **pourrait raisonnablement remettre en cause** faute d'en connaître les raisons : « pourquoi ne pas simplement comparer l'empreinte ? », « pourquoi une fenêtre courte plutôt qu'une moyenne ? ».

## Comment une ADR est vérifiée

Une décision qui n'est jamais reconfrontée au code se re-débat de la même façon qu'une décision oubliée. Chaque ADR déclare donc, dans son en-tête, **comment on sait si elle est tenue** :

- **`certaine`** : un invariant qui se prouve. Un test (`DecisionsRespecteesTest`) ou un script déterministe échoue en CI si la règle est violée. L'en-tête nomme le test ou le script.
- **`probable`**, pas de preuve possible, mais un script (`scripts/adr/NNNN-*.py`) liste des **suspects** qu'un humain trie. Le signal utile n'est pas « zéro » mais « aucun **nouveau** » : un **cliquet**, inscrit dans l'en-tête, borne la dette et fait rougir la CI si un cas s'ajoute.
- **`humaine`**, aucun invariant mécanique. Le motif dit **pourquoi**. Une décision de méthode ou de comportement ne se prouve pas par un scan, et un test creux serait pire que rien. Quand un pattern reconnaissable existe malgré tout, une **loupe** (`scripts/adr/loupe-NNNN-*.py`) surface une *surface de revue* pour la passe humaine : elle ne bloque jamais, elle aide à ne rien oublier.

Un garde-fou (`DocumentationAJourTest`) exige que **chaque** ADR déclare son niveau, et que le test, le script ou la loupe nommé **existe vraiment** : une ADR ne peut pas annoncer une garde disparue.

Le **rapport hebdomadaire** (`scripts/adr/rapport.py`, workflow `rapport-adr`) agrège les cliquets et les loupes pour mesurer l'écart d'une semaine sur l'autre, et **resserre** automatiquement, par une PR, les cliquets dont la réalité est passée sous la marge. Resserrer est mécanique ; desserrer reste un geste humain.

## Quand en écrire une

Au fil d'un chantier, à la **passe 3 (doc développeur)** de sa [clôture](../cycle-de-chantier.md) : chaque décision structurante prise pendant le chantier donne une ADR. La **passe 10 (bilan)** s'y réfère plutôt que de dupliquer le raisonnement.

## Le numéro d'une ADR est celui de son chantier

**Le numéro d'une ADR est le numéro de l'issue du chantier qui l'a décidée** (l'EPIC quand il y en a un). Il ne se choisit pas : il est déjà attribué quand on s'assoit pour écrire.

Ce n'était pas le cas avant : le numéro était pris au compteur, « le premier libre dans le dossier ». Ce compteur était une ressource partagée sans verrou, et deux chantiers parallèles prenaient régulièrement le même. Sur les quatre dernières ADR numérotées ainsi, **trois** ont dû être renumérotées, et la dernière collision a coûté bien plus que le renommage d'un fichier (#1881). Réserver le numéro à l'avance a été essayé, et démenti en trente minutes ; un script de balayage des branches a été écrit, et sa sortie s'est survolée. Un numéro d'issue, lui, ne peut pas entrer en collision : personne ne le choisit.

Comme les numéros d'issue croissent avec le temps, le dossier reste dans l'ordre chronologique.

!!! note "Le compteur est clos à 0048"
    Les ADR **0001 à 0048** gardent leur numéro de compteur : elles ne bougent pas. Les renuméroter toucherait 288 citations dans 154 fichiers, dont 93 fichiers Java, et surtout les discussions GitHub déjà closes, qu'on ne peut pas réécrire. **0049 n'existera jamais** : une ADR numérotée sous 1000 est une ADR d'avant la bascule.

    Deux trous subsistent dans cette série, **0029 et 0030** : ce sont les numéros qu'une résolution de collision a libérés. Ils restent vides, parce que les combler ferait pointer vers un numéro qui a déjà voulu dire autre chose dans une PR et une discussion.

`DocumentationAJourTest` garde la règle : numéros uniques, en-tête d'accord avec le nom de fichier, ligne de journal et entrée de nav pour chaque ADR, et pour toute ADR postérieure à la bascule, un numéro qui figure bien dans sa ligne « Chantier ».

## Format

Copier le squelette suivant dans `NNNN-titre-court.md`, où `NNNN` est le numéro de l'issue du chantier :

```markdown
# ADR NNNN : La décision, formulée comme une affirmation

- **Statut** : Accepté - AAAA-MM-JJ
- **Chantier** : EPIC #NNNN (titre court)

## Contexte
Les forces en présence, le problème, ce qui contraint.

## Décision
Ce qui est tranché, à l'impératif.

## Conséquences
Ce que cela implique, en bien comme en moins bien.

## Alternatives écartées
Les autres pistes, et pourquoi elles ont perdu.
```

## Journal

Les premières entrées sont **rétroactives** : elles consignent, à partir des bilans de chantier, des décisions structurantes prises avant l'ouverture du journal.

| # | Décision | Chantier |
|---|---|---|
| [0001](0001-reactivation-passage-reconstruit-identite-structurelle.md) | Identité d'un passage reconstruit : régénération structurelle, l'acoustique en indice | #1653 |
| [0002](0002-detection-acoustique-energie-de-pointe.md) | Détection acoustique par énergie de pointe, pas par moyenne globale | #1653 |
| [0003](0003-feature-plugin-desactivable-ports-optionnels.md) | Une feature est un plugin désactivable ; dépendances entre features = ports optionnels | #923, #1057 |
| [0004](0004-cross-feature-sans-cycle-ports-commun.md) | Pas de cycle entre features : les ponts passent par un port dans `commun` | ArchUnit |
| [0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md) | Réactivation par cascade de preuves ; « archivé » est un état observé | #1297 |
| [0006](0006-depot-zip-par-defaut-perte-audio-serveur-assumee.md) | Le dépôt par ZIP est le mode par défaut ; la perte de l'audio serveur est assumée | #984, #1297 |
| [0007](0007-retours-http-type-scelle-reponse-api.md) | Les retours de l'API sont un type scellé `ReponseApi` | #1284 |
| [0008](0008-aucun-echec-silencieux-severite-a-l-emission.md) | Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission | #1523 |
| [0009](0009-la-nuit-est-l-unite-bornee-a-midi.md) | La nuit (soir → matin, bornée à midi) est l'unité de traitement | #664, #1696 |
| [0010](0010-dialogues-bloquants-sont-des-ports.md) | Les dialogues bloquants (confirmation, compte rendu) sont des ports injectables | #789, #1405 |
| [0011](0011-transformation-audio-pilotee-par-le-log.md) | La transformation audio est pilotée par le log (fréquence réelle), pas par l'en-tête | import Tadarida |
| [0012](0012-audit-coherence-tout-ecart-visible-etat-normal-silencieux.md) | L'audit rend tout écart visible, mais un état normal ne crie pas | #1154 |
| [0013](0013-ancrage-passage-relie-a-sa-participation.md) | Un passage local est ancré à sa participation serveur (lien explicite) | #720 |
| [0014](0014-parite-cli-ihm.md) | Toute capacité métier est offerte aussi en CLI (parité CLI ↔ IHM) | #619, #1304 |
| [0015](0015-generateur-deterministe-cartes-sd-recette.md) | Cartes SD de recette : specs déclaratives + générateur déterministe | #1749, #1769 |
| [0016](0016-synchro-rapatrie-des-squelettes-hydrates-a-la-demande.md) | La synchro rapatrie les nuits en squelettes, hydratés à la demande | EPIC #1662 |
| [0017](0017-origine-d-un-point-etat-porte-pas-deduit.md) | L'origine d'un point (rapatrié vs manuel) est un état porté, pas déduit | #1738 |
| [0018](0018-la-synchro-rapatrie-l-identite-de-la-nuit.md) | La synchro rapatrie aussi l'identité de la nuit (amende 0016) | #1814 |
| [0019](0019-ancrage-acquis-quand-il-sert.md) | L'ancrage s'acquiert quand il sert, pas à un moment décrété (amende 0016) | #1838 |
| [0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md) | Écrire sur la plateforme : ne rien inventer, ne rien effacer, parler la langue du lecteur | #1828, #1844 |
| [0021](0021-double-clic-miroir-qui-rend-compte.md) | Le double-clic est le miroir de l'action principale, et il rend compte quand il n'aboutit pas | EPIC #1792 |
| [0022](0022-le-verbe-dit-le-sens-de-l-echange.md) | Le verbe d'un geste dit le sens réel de l'échange | #1855, #1866 |
| [0023](0023-rendre-compte-bandeau-par-defaut-modal-si-irreversible.md) | Rendre compte se fait au bandeau ; le modal est réservé à l'irréversible | EPIC #1870 |
| [0024](0024-les-heures-d-une-nuit-viennent-de-ses-preuves.md) | Les heures d'une nuit viennent de ses preuves ; de l'utilisateur seulement à défaut | #1860, #1878, #1892 |
| [0025](0025-une-capture-passe-par-le-code-de-production.md) | Une capture passe par le code de production, elle ne le reconstruit pas | #1468, #1865 |
| [0026](0026-le-nommage-des-tranches-est-une-etape-du-pipeline.md) | Le nommage des tranches est une étape du pipeline, pas un détail de la découpe | EPIC #1944 |
| [0027](0027-une-attente-porte-toujours-un-nom.md) | Une attente porte toujours un nom, et c'est l'étape qui va attendre qui le pose | #1931, #1951, #1959 |
| [0028](0028-un-etat-n-est-pas-un-compte-rendu.md) | Un état n'est pas un compte rendu, et ils ne partagent pas de canal | EPIC #1870 |
| [0031](0031-un-retour-n-est-pas-un-compte-rendu.md) | Un retour d'opération n'est pas un compte rendu : le mot « compte rendu » se libère pour l'extensible | EPIC #1990 |
| [0032](0032-le-plan-precede-l-ecriture.md) | Le plan de dépôt précède l'écriture des archives | EPIC #1991 |
| [0033](0033-la-fenetre-borne-le-disque.md) | Une fenêtre bornée, pas un pipeline unitaire, et deux seuils disque au lieu d'un | EPIC #1991 |
| [0034](0034-la-forme-du-depot-se-choisit.md) | La forme du dépôt se choisit, elle ne se déduit pas de la place disponible | EPIC #1991 |
| [0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md) | Un pictogramme d'IHM est une icône ; un caractère dans une phrase reste un caractère | #1933, #700 |
| [0036](0036-la-copie-des-bruts-est-une-option.md) | La copie des enregistrements bruts est une option de ré-analyse, pas un défaut | EPIC #2061 |
| [0037](0037-une-barre-d-actions-plie-elle-ne-tronque-pas.md) | Une barre d'actions plie ; et tout texte coupé n'est pas une barre qui ne plie pas | #2012, #1641, #1873 |
| [0038](0038-l-echelle-de-severite-a-quatre-niveaux.md) | L'échelle de sévérité compte quatre niveaux, et son ordre de déclaration porte la sémantique *(amendée : elle en ignorait une seconde)* | #1990, #2004, #2159 |
| [0039](0039-une-barre-de-statut-est-neutre.md) | Une barre de statut dit où l'on en est, pas si c'est bien ou mal | #1990, #2004 |
| [0040](0040-le-sujet-de-commit-est-une-syntaxe.md) | Le sujet d'un commit est une syntaxe, pas une phrase française : le `:` ne prend pas d'espace | EPIC #2104, #2105 |
| [0041](0041-un-check-requis-gouverne-la-branche.md) | Un check requis ne gouverne pas les PR, il gouverne la branche : le contrôle du titre reste informatif | EPIC #2104, #2106 |
| [0042](0042-un-apercu-qui-ment-est-refuse.md) | Un aperçu qui ment est refusé, et l'exception se déclare dans la vue | #2049, #1641, #1873, #1579 |
| [0043](0043-la-mesure-fait-foi-en-ci.md) | La mesure fait foi en CI, pas sur le poste (amende 0037) | #1873, #2129 |
| [0044](0044-le-mecanisme-de-parallelisme-suit-la-nature-de-l-attente.md) | Le mécanisme de parallélisme se choisit sur la nature de l'attente, la borne se chiffre sur autre chose | #2040 (EPIC #2116) |
| [0045](0045-l-upgradecode-windows-est-une-constante-d-identite.md) | L'UpgradeCode et le scope de l'installeur Windows sont des constantes d'identité, figées avant la première soumission winget | EPIC #2104, #2110, #2213 |
| [0046](0046-une-classe-css-a-une-seule-feuille.md) | Une classe CSS a une seule feuille pour maison ; un cliquet refuse tout nom en double | #1974 |
| [0047](0047-l-identite-de-distribution-est-le-projet-echonuit.md) | L'identité de distribution est le projet Echonuit : produit « VigieChiro Companion », app-id `fr.echonuit`, éditeur Echonuit (fait évoluer #2108) | #2240, #2213, #2111 |
| [0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md) | L'utilisateur possède ses fichiers : l'application observe la disponibilité de l'audio au lieu de l'archiver, et peut le référencer en place (reformule #1038) | #1038, #2028, EPIC #1297 |
| [1038](1038-la-configuration-d-amorcage-vit-hors-de-la-base.md) | La seule configuration qui vive hors de la base est celle qui dit où la base se trouve : fichier d'amorçage `%APPDATA%` / `$XDG_CONFIG_HOME`, lu avant tout le reste (absorbe #2187) | #1038, #2187 (EPIC #2258) |
| [1881](1881-l-identifiant-d-une-adr-est-le-numero-de-son-chantier.md) | Le numéro d'une ADR est celui de son chantier, pas un compteur : le compteur est clos à 0048 | #1881 |
| [2465](2465-une-adr-declare-comment-elle-est-verifiee.md) | Une ADR déclare comment elle est vérifiée (certaine / probable / humaine) ; cliquets dans l'en-tête, loupes, rapport hebdomadaire | EPIC #2465 |
| [2525](2525-un-fait-booleen-d-une-entite-centrale-vit-dans-une-table-laterale.md) | Un fait booléen porté par une entité centrale (construite en des dizaines d'endroits) vit dans une **table latérale de présence**, pas dans une colonne du record : V10 `passage_equipment`, V34 `passage_opportuniste`, V35 `site_tiers` | #2525 (EPIC #2349) |
| [2349](2349-une-derivation-automatique-ne-defait-jamais-une-saisie-manuelle.md) | Une dérivation automatique (ici depuis l'API VigieChiro) **ajoute mais ne retranche jamais** : démarquer effacerait en silence les saisies manuelles des cas que la source externe ne connaît pas | EPIC #2349 (décidé dans #2525) |
| [2493](2493-une-modale-a-revelation-suit-la-croissance.md) | Une modale qui révèle un bandeau après ouverture appelle `Modales.suivreLaCroissance`, sinon les boutons du bas débordent | #2493 (issu de #2486, #1534) |
| [2433](2433-un-dossier-de-transformes-s-importe-par-reference.md) | Un dossier de transformés déjà présents s'importe par référence, sans brut : original placeholder, empreinte calculée à l'inscription (complète #2255) | #2433 (EPIC #2258) |
| [2385](2385-la-doc-chiffree-est-adossee-au-code.md) | Les chiffres et les relations de la documentation sont adossés au code par des gardes (balises d'inventaire, réciprocité des amendements, liens racine) ; étendu par #2386, clôt l'EPIC #2367 | #2385, #2386, EPIC #2367 |
| [2358](2358-un-compte-rendu-chiffre-tient-ses-regles-par-le-type.md) | Un compte rendu d opération lourde se rend en proportions, et ses règles tiennent par le type : largeurs liées aux quantités, ventilation exhaustive refusée à la construction, action suivante en pied ; chaque mention porte sa sévérité | #2358 (lot 2 de l EPIC #2350) |
| [2354](2354-le-reessai-reseau-est-gradue-jamais-aveugle-toujours-jittere.md) | Le réessai réseau est gradué par profil (premier plan / arrière-plan), jamais aveugle (jamais un `4xx`, `estReessayable` sur `ReponseApi`), toujours jitteré, `Retry-After` faisant autorité ; l'idempotence reste arbitrée à l'appel | #2354 (lot 1 de l'EPIC #2350) |
| [2352](2352-la-nuit-se-lit-du-crepuscule-a-l-aube-pas-de-minuit-a-minuit.md) | La nuit biologique bascule à midi et se lit sur un axe fixe 18 h → 8 h, la fenêtre coucher/lever réelle étant matérialisée quand elle est calculable ; la même règle date les lignes de l'export | #2352 (lot 2 de l'EPIC #2348) |
| [2348](2348-un-export-d-image-se-redessine-il-ne-se-capture-pas.md) | Un export d'image reconstruit ce qu'il exporte hors écran (jamais `snapshot()` du nœud affiché, dont l'échec est silencieux) et l'estampille de son contexte : identité, réglages, filtres en clair, provenance | EPIC #2348 |
| [2554](2554-la-synchro-amene-chaque-nuit-a-un-niveau-de-completude.md) | La synchro amène chaque nuit à un niveau de complétude (structure → identité → contenu), le repli `donnees` est réservé au geste désigné, l'hydratation se fait en place sur le chemin de la réactivation, et le compte rendu ventile par cause sans jamais l'affirmer sans l'avoir constatée ; amende #0016 | EPIC #2554 |
| [2357](2357-un-traitement-en-lot-compose-des-gestes-unitaires.md) | Un lot applique N fois un geste qui existe déjà et n ajoute que ce qui relève du nombre : moteur **séquentiel** (le plafond de parallélisme reste celui d un passage), éligibilité **locale** annoncée avant de partir, annulation **entre** deux passages, échec qui n arrête pas le lot, et **aucune option destructrice** exposée en lot | #2357 (lot 3 de l EPIC #2349) |
| [2635](2635-un-refus-dit-ce-qui-manque-la-surface-dit-quoi-faire.md) | Un refus du modèle énonce ce qui **manque** (`Besoin` : connexion, fonctionnalité) sans nommer d'écran ni de commande ; chaque surface ajoute son geste (menu pour l'application, commande pour la ligne de commande) | #2635 (suite de #2554) |
| [2802](2802-un-texte-qu-on-n-a-pas-ecrit-se-borne-a-son-entree.md) | Un texte **venu d'ailleurs** (message d'exception, réponse serveur) se borne à sa **porte d'entrée**, qui est aussi le seul endroit où on peut l'enrichir ; ce que nous écrivons reste entier | #2802 (suites de #2350) |
| [2614](2614-une-nuit-hors-protocole-se-filtre-au-lieu-de-se-fondre.md) | Une nuit opportuniste se **filtre** dans les vues agrégées au lieu d'être exclue d'office ; l'absence de marquage vaut « Protocole », et la lecture du marquage passe par un port distinct de l'écriture | #2614 (suite du lot 2 de l'EPIC #2348) |
| [2353](2353-l-enjeu-de-conservation-est-celui-que-le-plan-national-designe.md) | L'enjeu de conservation est le **booléen** que le PNA Chiroptères 2016-2025 désigne (19 espèces prioritaires, 17 présentes au référentiel Tadarida), joint par nom latin et stocké en table latérale ; rien n'est marqué par déduction taxonomique | #2353 (lot 3 de l'EPIC #2348) |
| [2351](2351-un-nombre-de-contacts-se-lit-contre-un-referentiel-cite.md) | Un nombre de contacts se lit contre le référentiel **ACTICHIRO cité** (source recopiée à l'écran et dans chaque export) ; le repli retient la **première déclinaison fiable**, pas la plus fine ; la saison et la région se déduisent, le milieu se choisit | #2351 (lot 1 de l'EPIC #2348) |
| [2581](2581-un-etat-qui-decide-de-l-affichage-se-declare.md) | Un état reste **observé** tant qu'il ne sert qu'à décider et se **déclare** quand il doit être montré : `StatutWorkflow.RECUPERE` vit hors de la file linéaire, la migration V37 rejoue le critère observé sans en inventer un second, et la valeur est déclarée en dernier pour ne pas décaler les comparaisons par `ordinal()` | #2581 (lot 1, #2772) |
| [2791](2791-la-commune-se-derive-du-gps-et-s-attache-au-point.md) | La commune d'un point se dérive **une fois** de son GPS (API Géo, best-effort, rattrapable) et vit en table latérale `point_commune` ; elle s'attache au **point**, jamais au carré (chevauchements) ; département et région se dérivent du code INSEE via la table unique `RegionsFrancaises` (ADR 2351 généralisée) | #2791 (lot 0 de l'EPIC #2790) |
| [2792](2792-l-archive-d-export-range-les-sons-par-session.md) | L'archive d'export « observations + sons » est un **contrat** : `observations.csv` à la racine (octet-identique à l'export CSV seul), sons dédupliqués sous `sons/<dossier-session>/` (suffixe `-s<id>` seulement en collision), son introuvable compté jamais bloquant, pas de manifeste séparé (le CSV fait foi) | #2792 (lot A de l'EPIC #2790) |
| [3006](3006-le-groupe-api-est-borne.md) | Le groupe `api` de la CLI est **borné** : lecture seule, pièges connus traduits en **refus avant émission** (`max_results > 100`, `where=` ignoré), échappatoire `lectureBrute` réservée au groupe **par une règle d'architecture**, sous-commandes volontairement discrètes en contrepartie de quoi le groupe ne grossit pas sans décision. Et : ce qui teste l'API se passe de notre client, ce qui teste notre client passe par lui | #3006 (lot 4 de l'EPIC #2999) |
| [2843](2843-typographie-cliquet-plutot-que-nettoyage.md) | La convention typographique (pas de tiret cadratin) se tient par un **cliquet** sur les sources Java, pas par un nettoyage d un seul tenant ; la documentation Markdown est hors perimetre a dessein, deux populations dans un seul nombre pouvant se masquer | #2843, suite de #2813 (cloture du chantier #2348) |
| [2867](2867-une-dette-se-tient-par-un-cliquet.md) | Une dette qu'on migre au fil de l'eau est épinglée dans une liste explicite qui ne peut que **rétrécir** (un « cliquet ») : les deux sens de variation sont rouges et le message les distingue, la **destination** de la migration est exclue dès la pose, pas de court-circuit sur un objet « déjà traité », et usage n'est pas mention | #2867 (axe 5 du chantier #1771) |
| [2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) | Les cinq décisions prises **en appliquant** le cliquet typographique, avec les incidents qui les ont déclenchées : la mesure compte la prose, une zone se promeut dans la tranche qui l'amène à zéro, une zone qui ne balaie rien lève, une forme citée se taille au plus juste, la racine se balaie sans descendre *(amende 2843)* | #2365 |
| [2951](2951-une-exclusion-nomme-son-repreneur.md) | Une exclusion de détecteur **nomme le dispositif qui reprend** l'objet écarté : une partition ne fait disparaître aucun objet des deux comptes, un court-circuit si ; deux exclusions de natures différentes ne partagent pas un `if` | #2951 (chantier #1771), amende #2867 |
| [3018](3018-un-outil-compose-depuis-la-racine.md) | Un outil (capture, banc, graine) compose son injecteur depuis **`RacineInjecteur.modules()`** et s'adapte par `Modules.override`, jamais en retirant de la liste : un injecteur amputé et une fonctionnalité désactivée produisent le **même écran**, donc la règle ne peut pas tenir par la vigilance. Une substitution reste acceptable seulement si elle est **étroite** et qu'elle **est le sujet** de l'aperçu | #3018, finit #2669 et #333 |
| [3048](3048-la-parite-dune-sortie-machine-est-de-dire.md) | Face à un état **dégradé**, l'IHM peut **retirer** (un écran se relit d'un coup), mais une **sortie machine dit** : la structure reste stable, un champ nomme l'état, et ce qui n'a pas pu être établi cesse d'être affirmé (pas de comparaison inventée, pas de citation d'une source non chargée). Retirer une colonne de CSV casse ce que le retrait prétend protéger *(précise 0014)* | #3048, suites de #3018 |
| [3068](3068-le-determinisme-porte-sur-ce-que-le-produit-rend.md) | Le déterminisme des captures porte sur **ce que le produit rend** ; une entrée **extérieure** au dépôt y échappe. On garde donc les tuiles OpenStreetMap et leur variabilité mesurée (0,34 % des pixels) : ces captures valent parce qu'elles montrent une **vraie** carte. Corollaire : sur ces quatre fichiers, un diff n'est pas un signal | #3068, suites de #3018 |
| [3053](3053-une-capture-exige-son-libelle.md) | Quand un outil de capture désigne un contrôle par son **libellé**, il **exige** de le trouver et **lève en nommant les libellés présents** : l'abstention (`findFirst().ifPresent`) produit l'aperçu **sans le geste**, publié sous une légende affirmant le contraire. Repli de l'[ADR 0025](0025-une-capture-passe-par-le-code-de-production.md) §4 là où la constante ne peut pas être partagée (catalogues package-private) | #3053, clôture des suites de #2967 |
| [2727](2727-une-restauration-verifie-en-place-et-replace-ou-cest-possible.md) | Une restauration complète **vérifie la sauvegarde en place** (pas de zone temporaire : restaurer 40 Go par-dessus 40 Go demanderait 80 Go libres) et replace un dossier **à son emplacement d'origine s'il existe encore**, sinon dans le dossier de travail. **Les six tables à chemin** suivent, pas seulement `root_path` : ne réécrire que la racine donne une base qui paraît juste et une application qui ne trouve plus rien | #2727, lot 1 de #2720 |
| [2729](2729-un-script-publie-ne-se-modifie-plus.md) | Un script de migration **publié ne se modifie plus** : son empreinte SHA-256 est inscrite avec sa version, et une dérive est un **refus** au démarrage, pas un avertissement. L'empreinte porte sur les **instructions**, pas sur le texte du fichier : un refus faux use plus vite la confiance qu'une alerte manquée | #2729, lot 1 de #2720 |
| [2731](2731-un-seul-processus-par-workspace.md) | **Un seul processus écrit** dans un dossier de travail, garanti par un **verrou de fichier système** (que l'OS relâche à la mort du processus) et non par un fichier de PID (dont la validité n'a pas de réponse portable). La seconde instance graphique est **refusée** et non basculée en lecture seule : ce mode n'existe nulle part et le livrer à moitié serait pire. La CLI ne verrouille que ses opérations exclusives, pour ne pas casser l'usage scriptable | #2731, lot 1 de #2720 |
| [2732](2732-on-n-ecrit-pas-plus-que-ce-qui-est-declare.md) | La décompression est bornée par **deux** gardes - l'inventaire **annoncé** avant le premier octet, les octets **réellement** écrits pendant la copie - qui tiennent ensemble une garantie unique : *on n'écrit jamais plus que ce qui a été déclaré, et on n'accepte jamais une déclaration qui ne tient pas*. **Pas de plafond de taux de compression** : une carte SD de recette se décompresse ≈ 140 fois et un enregistrement silencieux bien plus, or l'audio silencieux et une bombe sont les mêmes octets | #2732, lot 2 de #2720 |
| [2736](2736-le-clair-est-assume-et-annonce.md) | Le jeton reste un **fichier `600`** et les sauvegardes ne sont **pas chiffrées** : un coffre système protégerait le mauvais actif, puisque les **localisations d'espèces protégées** vivent en clair dans la base du même dossier, et une clé de chiffrement n'a pas de bon endroit où vivre (dérivée d'un mot de passe, elle fait de la sauvegarde un piège au moment où elle sert). Le clair est **assumé et annoncé** : l'archive dit ce qu'elle emporte | #2736, lot 2 de #2720 |
| [2861](2861-une-donnee-de-point-ne-se-montre-pas-sur-une-ligne-agregee.md) | Une donnee du **point** (sa commune) ne s affiche que sur une table dont **une ligne porte un point** : sur un agregat, un carre de 2 km chevauchant deux communes ferait mentir la cellule. Chaque table garde par ailleurs **sa** marque d absence (vide ou tiret), et la revue audio la traite comme du **contexte**, masque sur un passage unique | #2861, lot 3 du chantier #3151 |
| [3082](3082-designer-refuse-qualifier-rend-vide.md) | Un critère de commande qui **désigne** (un lieu, une nuit, un taxon) **refuse** en nommant ce qui est présent : l'ensemble vide y est une faute de frappe. Un critère qui **qualifie** (une nature, un enjeu) **rend vide** sans refuser : c'est une réponse. Le test : « cette valeur peut-elle être fausse ? » | #3082, clôture des suites de #2967 |
| [3092](3092-un-filtre-ne-change-que-ce-quon-regarde.md) | Un filtre ne touche que **la liste affichée** : le résumé, le verdict et le **code de sortie** continuent de juger l'ensemble. `audit-coherence --gravite INFO` rend `1` si le workspace porte une erreur, sinon un script d'intégration conclurait que tout va bien. Corollaire : un filtre qui ne retient rien **le dit**, au lieu de laisser un en-tête suivi du vide | #3092, passe 2 de la clôture |
| [3093](3093-une-restauration-rend-compte-de-deux-causes.md) | Les **trois** chemins qui remettent des filtres (vue mémorisée, transport entre écrans, mémoire de session) rendent compte de ce qu'ils n'ont pas su replacer, et distinguent une **valeur disparue** (les données) d'un **critère absent** (l'écran) : les deux n'appellent pas la même réaction. Un écran gagne son bandeau **en même temps** que sa barre | #3093, palier 1 du chantier #3092 |
| [3096](3096-une-cle-de-critere-est-un-contrat-de-serialisation.md) | Une clé de critère **sérialise** les vues enregistrées : elle vit à un seul endroit quand elle est partagée (`ClesCriteres`), reste chez son écran quand elle lui est propre, et se renomme **sans migration** via `nomsHerites`. Les **libellés** sont une préoccupation distincte, qui doit couvrir jusqu'aux clés qu'aucun écran local n'offre | #3096, palier 3 du chantier #3092 |
| [3095](3095-un-domaine-se-calcule-sans-son-propre-critere.md) | Le domaine d'une puce se calcule sur les lignes que les **autres** critères laissent passer (`Filtres.saufLui`), sinon elle s'auto-effondre sur la valeur déjà retenue. Une valeur cochée devenue impossible est **conservée et marquée**, jamais retirée en silence. Et une **facette** cascade là où un **sélecteur** ne cascade pas : restreindre aide, mais retirer du menu ce vers quoi on veut naviguer gêne | #3095, palier 2 du chantier #3092 |
| [3099](3099-une-puce-preselectionnee-annonce-ce-quelle-filtre.md) | Deux puces de Sons & validation filtrent dès leur ajout, contre la règle « rien de coché n'écarte rien ». C'est **acceptable parce qu'elles annoncent leur valeur** : la capture montre « Taxon parent | Chiroptères » lisible sans déplier. Un critère qui filtrerait d'emblée **sans le montrer** serait, lui, un défaut | #3099, palier 4 du chantier #3092 |
| [3157](3157-un-carre-a-un-identifiant-et-une-etiquette.md) | Le domaine a **trois** niveaux géographiques, pas quatre : `monitoring_site` porte le numéro de carré **et** son nom convivial sur la même ligne. Le carré s'offre donc dans **une seule entrée**, « 640380 · Vallon », dont le **préfixe identifie** et le suffixe ne fait que nommer. Deux carrés homonymes s'en trouvent distingués, et #3145 cesse d'être une question | #3157, lot 1 du chantier #3151 |
| [3158](3158-une-valeur-memorisee-se-rattrape-par-dimension.md) | Une vue mémorise le **texte** coché : requalifier une entrée le rend introuvable. Le socle **demande au critère** ce que la valeur désigne, chaque dimension déclarant où vivait son ancienne écriture (**en tête** pour le carré, **en queue** pour le point) - chercher un segment n'importe où ne rattrape **jamais** un carré, le point étant qualifié par lui. **Deux candidates ne cochent rien** | #3158, lot 1 du chantier #3151 |
| [3168](3168-un-audit-qui-ne-peut-pas-trancher-montre-sans-juger.md) | Le departement d un point se lit **deux fois** (par son carre, par sa commune) et les deux lectures peuvent se contredire. L audit **montre l ecart sans le trier** : il n a ni la geometrie du carre ni la distance au bord, et un tri approximatif ferait croire les faux positifs deja ecartes. La severite **INFO** est un **contrat de sortie** - un AVERTISSEMENT ferait rendre 1 a audit-coherence sur le cas normal. Et deux ecritures qu on ne sait pas departager (Corse `20`/`2A`, outre-mer `97`/`971`) ne divergent pas : la comparaison **s abstient** | #3168, lot 4 du chantier #3151 |
| [3222](3222-une-entree-externe-se-lit-sous-plafond.md) | Une entrée externe se lit **sous plafond**, et le plafond vient d'une **mesure** sur une carte réelle : le journal du capteur pèse 1,9 Ko pour une nuit (il consigne des événements de session, pas un enregistrement par fichier), le plus gros corps rendu par la plateforme 446 Kio. Deux gardes comme à l'extraction - la taille **annoncée** avant lecture, les octets **lus** pendant - et un dépassement qui est un refus **définitif**, jamais rejoué | #3222, suite du lot 2 de #2720 |
| [2744](2744-la-publication-part-a-heure-fixe.md) | La publication part **à heure fixe** (mercredi 6 h UTC) et non à chaque fusion. Le dépôt publiait 3 à 37 fois par jour, jusqu'à **31 versions** dans la même journée - non pas des versions vides (95 % de `feat` et `fix`) mais **atomisées** : une version = un changement, donc aucune validable par la recette ni descriptible. La cadence pesait déjà sur une décision en aval, comptée parmi les raisons du déclenchement manuel de winget et Flathub | #2744, lot 3 de #2720 |
