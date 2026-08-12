# ADR 3575 - Le journal fait exception, et le cliquet ne recopie rien

- **Statut** : Accepté - 2026-08-12
- **Chantier** : #3575, suite de la clôture du lot 1 (#3559)
- **Amende** : [ADR 3498](3498-la-declaration-porte-sur-les-lectrices.md)
- **Vérification** : certaine - `ClassementLectureEcritureTest#aucune_commande_n_est_sans_classement`

## Contexte

L'[ADR 3498](3498-la-declaration-porte-sur-les-lectrices.md) a posé le bon principe - on déclare les
**lectrices**, parce qu'oublier une écrivaine se paie en silence tandis qu'oublier une lectrice se paie
en gêne visible. Deux de ses pièces, en revanche, ne tenaient pas ce qu'elles promettaient. Toutes deux
ont été trouvées à la clôture du lot, aux passes 0 et 7.

## Décision 1 - Le journal fait exception, et la définition le dit

La définition disait : « ne touche ni la base **ni les dossiers du dossier de travail** ». Or
`Cli.main` amorce la journalisation avant tout, et `dossierLogs()` vaut `racine.resolve("logs")` :
**toute** commande crée et écrit un dossier du dossier de travail, les vingt-trois lectrices comprises.

La définition était donc contredite par les commandes mêmes qu'elle décrivait.

Elle devient : **ne touche ni la base ni les dossiers de session**, et l'exception du journal est
**nommée**. Ce n'est pas une concession : un incident doit laisser une trace même sur une commande qui
ne fait que lire, et deux processus qui écrivent chacun ses lignes dans un journal ne se corrompent
pas - ce que deux processus qui écrivent la même base feraient.

⚠️ Ce qui est corrigé est la **formulation**, pas la décision. Une définition qui promet plus qu'elle ne
tient s'use vite : le premier lecteur qui remarque l'écart cesse de croire le reste.

## Décision 2 - Le cliquet est un compteur, pas une liste

Le garde portait la liste des quarante-quatre commandes écrivaines. Ses deux assertions se refermaient
l'une sur l'autre : toute commande non marquée **devait** y figurer, et elle ne pouvait contenir que des
commandes non marquées. La liste valait donc **exactement** l'ensemble que le code déclare déjà, et le
second test ne protégeait que le premier.

C'est la question que pose l'[ADR 3535](3535-un-inventaire-ne-se-duplique-pas-il-se-cite.md), née
pendant le lot : **ai-je besoin de la liste, ou de ce que ses éléments doivent faire ?**

Ici, de ce qu'ils doivent faire : **trancher**. Un compteur verrouillé le force autant, parce que ce
qui force la décision n'est pas la liste mais le **message** - et le message se construit du côté du
code, en énumérant les commandes qui prennent le verrou.

### Ce que le compteur perd, et pourquoi c'est nul

Le second test attrapait une entrée devenue périmée après un **renommage**. Il n'existait que parce que
la liste existait : un renommage ne change pas le classement d'une commande, seulement la copie de son
nom. Le risque disparaît avec la copie.

### Ce qu'il perd vraiment

Le message ne **désigne** plus la commande fautive : il affiche les quarante-cinq et laisse comparer.
C'est acceptable parce que la personne qui lit ce rouge est celle qui vient d'ajouter la commande. Mais
c'est un vrai recul de précision, et il est assumé plutôt que tu.

## Conséquences

- Le cliquet rejoint l'idiome du dépôt : `cli-surface.bats` verrouille déjà un compte de commandes.
- Éprouvé en ajoutant une commande **factice** au câblage, et non en relisant le code : le garde rougit,
  et AssertJ nomme l'intruse dans sa propre sortie.
- Le message d'échec ne répète pas la liste qu'AssertJ affiche déjà : la doubler la rendait illisible.
