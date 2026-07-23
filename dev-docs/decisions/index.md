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

- **`certaine`** — un invariant qui se prouve. Un test (`DecisionsRespecteesTest`) ou un script déterministe échoue en CI si la règle est violée. L'en-tête nomme le test ou le script.
- **`probable`** — pas de preuve possible, mais un script (`scripts/adr/NNNN-*.py`) liste des **suspects** qu'un humain trie. Le signal utile n'est pas « zéro » mais « aucun **nouveau** » : un **cliquet**, inscrit dans l'en-tête, borne la dette et fait rougir la CI si un cas s'ajoute.
- **`humaine`** — aucun invariant mécanique. Le motif dit **pourquoi**. Une décision de méthode ou de comportement ne se prouve pas par un scan, et un test creux serait pire que rien. Quand un pattern reconnaissable existe malgré tout, une **loupe** (`scripts/adr/loupe-NNNN-*.py`) surface une *surface de revue* pour la passe humaine : elle ne bloque jamais, elle aide à ne rien oublier.

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
# ADR NNNN — La décision, formulée comme une affirmation

- **Statut** : Accepté — AAAA-MM-JJ
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
