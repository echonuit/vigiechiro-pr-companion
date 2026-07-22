# ADR 1881 - Le numéro d'une ADR est celui de son chantier, pas un compteur

- **Statut** : Accepté - 2026-07-22
- **Chantier** : #1881 (numérotation des ADR : collision systématique entre chantiers parallèles)

## Contexte

Le numéro d'une ADR était pris au **compteur** : le premier libre dans `dev-docs/decisions/`. C'est une
ressource partagée mutable, sans verrou, lue et écrite par des chantiers qui avancent en parallèle. Deux
d'entre eux prenaient régulièrement le même numéro, et le second à fusionner le découvrait au rebase.

Sur les quatre dernières ADR numérotées ainsi, **trois** ont dû être renumérotées. Deux atténuations ont
été essayées, et les deux ont échoué à l'usage :

- **réserver le numéro à l'ouverture du chantier**, publiquement en commentaire. Démenti en trente
  minutes : un autre chantier a pris le même numéro sans avoir vu la réservation. Elle ne protège que si
  tous les chantiers en cours la lisent, ce qui suppose une coordination qu'on n'a pas ;
- **balayer les branches distantes avant d'écrire**, parce qu'une PR ouverte réserve un numéro qui
  n'apparaît nulle part. Le script était juste, sa sortie a été survolée : une liste se survole. Et même
  lu en entier, il ne protège pas de la fenêtre entre la vérification et la fusion, qui est précisément
  celle qui s'est refermée.

Une collision coûte bien plus que le renommage d'un fichier. Elle se répare sur une branche déjà
poussée, avec une PR ouverte, dans un index que tout le monde édite : le dernier cas a enchaîné un
rebase conflictuel, une PR fermée par GitHub après un `reset --hard` mal placé, et un index corrompu par
un `printf` cassé sur les apostrophes françaises.

Elle laisse aussi un dégât **durable** : chaque renumérotation libère un numéro qui restera vide, parce
qu'il a déjà voulu dire autre chose dans une PR et une discussion. Le journal en porte deux, 0029 et
0030.

Une contrainte encadre tout remède : le corpus existant ne peut pas être renuméroté. « ADR 00NN » est
cité **288 fois dans 154 fichiers**, dont 93 fichiers Java, et surtout dans les discussions GitHub déjà
closes, qu'on ne peut pas réécrire. Toute solution ne vaut donc que pour l'avenir.

## Décision

**Le numéro d'une ADR est le numéro de l'issue qui porte sa décision** : le lot quand le chantier est
découpé en lots, l'EPIC sinon. Une ADR par décision, un numéro par ADR.

Le numéro ne se choisit plus : il est déjà attribué quand on s'assoit pour écrire. Aucune réservation,
aucun balayage, aucune fenêtre entre la vérification et la fusion.

Le **compteur est clos à 0048**. Les ADR 0001 à 0048 gardent leur numéro ; 0049 n'existera jamais. Une
ADR numérotée sous 1000 est, par construction, une ADR d'avant la bascule.

`DocumentationAJourTest` garde la règle : numéros uniques, en-tête d'accord avec le nom de fichier,
ligne de journal et entrée de nav pour chaque ADR, compteur resté clos, et pour toute ADR postérieure à
la bascule, un numéro qui figure bien dans sa ligne « Chantier ».

## Conséquences

**Il n'y a plus de collision possible**, et pas parce qu'on la surveille mieux : parce que personne ne
choisit plus le numéro. C'est la différence entre un garde-fou et une propriété de construction.

**L'ordre chronologique du dossier est préservé**, les numéros d'issue croissant avec le temps.

**Le numéro dit désormais quelque chose.** « ADR 1881 » renvoie à l'issue #1881, donc au constat, aux
mesures et à la discussion qui ont mené à la décision. Un numéro de compteur ne portait rien.

**Le corpus devient hétérogène** : 0001 à 0048 numérotées au compteur, puis des numéros d'issue. La
rupture est visible, ce qui est préférable à une homogénéité obtenue en réécrivant l'histoire.

**Une ADR ne peut plus s'écrire sans issue.** C'est une contrainte assumée : une décision structurante
qui n'a nulle part où se discuter n'a pas de raison d'être consignée seule.

**Deux ADR d'un même chantier prennent deux numéros de lots différents.** C'est le cas courant :
un chantier qui prend trois décisions structurantes est, en pratique, un chantier découpé en lots.

## Alternatives écartées

**Réserver le numéro à l'ouverture du chantier.** Essayée, et démentie par l'usage en trente minutes
(voir le contexte). Elle demande à tous les chantiers de lire les réservations des autres.

**Numéroter à la fusion**, l'ADR vivant sous un nom provisoire jusqu'au merge. Supprime le conflit mais
**déplace** le renommage au lieu de l'éliminer, et laisse les liens entrants en attente d'un numéro qui
n'existe pas encore.

**Un identifiant daté** (`2026-07-22-titre.md`). Supprime le compteur lui aussi, mais rompt avec le
vocabulaire « ADR NNNN » cité partout, jusque dans 93 fichiers Java, et deux ADR écrites le même jour
entrent à nouveau en collision.

**Renuméroter tout le corpus** pour l'homogénéiser. Écartée par la contrainte du contexte : 288
citations, et des discussions GitHub closes qu'aucune migration ne peut atteindre.

**Ne rien changer et se contenter du garde-fou.** Le test qui refuse deux numéros identiques existe
depuis #2082 et reste utile, mais il n'attrape la collision qu'**après** coup, sur la branche qui rebase.
Il nomme la cause au lieu de la laisser paraître en conflit de fusion, ce qui est déjà beaucoup, mais il
ne supprime pas le travail de renumérotation ni les trous qu'elle laisse.
