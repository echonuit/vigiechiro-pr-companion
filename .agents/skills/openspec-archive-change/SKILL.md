---
name: openspec-archive-change
description: Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.14.1"
  langue: fr
  origine: adoptee de l'outil OpenSpec, puis reecrite ici (ADR 4515)
---

# Archiver un changement OpenSpec

## Loi d'airain

```
ON NE DEPLACE RIEN TANT QUE LA FUSION DES SPECS N EST PAS VERIFIEE
```

Archiver déplace `changeRoot`. Le faire pendant qu'une fusion de specs le lit encore laisse le
changement archivé et les specs principales jamais mises à jour, sans que rien ne le dise.

## Annoncer

« J'utilise la compétence openspec-archive-change pour archiver le changement <nom>. »

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, ou si le travail y vit, lister les identifiants par `openspec store list --json`, puis passer
`--store <id>` sur les commandes qui lisent ou écrivent des specs et des changements : `new change`,
`status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`,
`view`. Une fois choisie, la réserve colle au reste du travail. Sans réserve, les commandes agissent
sur la racine `openspec/` la plus proche.

Le `<capability-path>` est le dossier de spec relatif à `specs/`, par exemple `user-auth` ou
`identity/user-auth`. Conserver le chemin **entier** de chaque delta spec pour retrouver sa spec
principale.

## Vérifier que le projet a une racine OpenSpec

Ce flux suppose un projet qui emploie déjà OpenSpec. Avant la première étape qui écrit quoi que ce
soit (`new change`, `archive`, la fusion des specs, ou l'écriture d'un artefact), s'assurer que le
projet a une racine : lancer `openspec list --json`, avec `--store <id>` quand une réserve est
choisie puisqu'elle est alors la racine, et lire `root`.

- **Un objet `root`** : le projet est en place, on continue.
- **`"root": null`** : il n'y a pas de dossier `openspec/` ici, et une écriture comme
  `openspec new change` en créerait un par effet de bord. La commande sort aussi en code non nul.
  C'est la réponse, pas un outil cassé : lire le JSON plutôt que réessayer ou contourner.

Un `"root": null` ne parle pas toujours d'installation. Quand un message d'erreur de `status`
commence par `Declared in` ou `Invalid store declaration in` et nomme l'`openspec/config.yaml` (ou
`config.yml`) de ce projet, le projet emploie bien OpenSpec, par une réserve qu'il déclare et que la
machine ne sait pas résoudre : réserve non enregistrée, ou ligne `store:` mal formée. Ne pas le
traiter comme un projet vierge : s'arrêter avant d'écrire et montrer à l'utilisateur le `message` et
le `fix` de cette erreur.

Hors ce cas, sans racine, la suite dépend de la façon dont on est arrivé ici :

- **Choisie d'office** : on a pris ce flux de soi-même, sans que l'utilisateur nomme OpenSpec, nomme
  cette compétence ou lance `/archiver`. On laisse OpenSpec de côté et on répond à la demande comme
  si l'outil n'était pas installé, sans lui demander de rien mettre en place et sans parler
  d'installation.
- **Demande explicite** : l'utilisateur a nommé OpenSpec, cette compétence, ou lancé `/archiver`. On
  s'arrête avant d'écrire et on demande comment continuer : mettre le projet en place
  (`openspec init`), viser une réserve qu'il a déjà (`--store <id>`), ou continuer sans OpenSpec pour
  cette demande. Puis on attend sa réponse.

Dans les deux cas, **on ne crée jamais la racine par effet de bord** : pas d'`openspec init` tant que
l'utilisateur ne l'a pas demandé, pas de fichiers `openspec/` écrits à la main, pas de commande qu'on
laisse la créer.

Dans ce dépôt, la racine `openspec/` existe : la vérification répond « en place », et la règle reste
pour le jour où elle ne répondrait plus ainsi.

## Entrée

Le nom du changement est facultatif. S'il manque, le déduire du fil. S'il reste vague, demander.

## Quand elle s'emploie

**L'archivage est un acte de clôture, pas une fin d'issue.** Il se fait à la passe 10, quand le
chantier entier est livré, et non à chaque demande de fusion. Un changement archivé alors que son
chantier court encore fait mentir la liste des changements actifs dans l'autre sens.

## Fonction de garde

```
1. CHOISIR    le changement, et charger les entrees d archivage (facultatif, jamais bloquant).
2. VERIFIER   les artefacts, puis les taches. Un manque AVERTIT, un compte illisible ARRETE.
3. EVALUER    l ecart entre chaque delta spec et sa spec principale, et le MONTRER avant de demander.
              Une capacite BLOQUEE ferme la fusion, pas l evaluation des autres.
4. FUSIONNER  en ligne si l utilisateur le choisit, verifier la STRUCTURE, puis RE-COMPARER toutes
              les capacites. Une fusion qui rend un arret fait echouer l archivage.
5. DEPLACER   `changeRoot` sous l archive, une fois et une seule.
6. RENDRE     compte de ce qui a ete fusionne, et de ce qui a ete archive malgre un avertissement.
```

## 1. Choisir le changement, et charger les entrées

Si un nom est fourni, le prendre. Sinon, le déduire du fil, ou choisir d'office s'il n'y a qu'un
changement actif. Si c'est ambigu, `openspec list --json` donne les candidats : ne montrer que les
changements **actifs**, pas ceux déjà archivés, avec leur schéma quand il est connu.

Annoncer toujours : « Changement retenu : <nom> », et comment le remplacer, par exemple
`/archiver <autre>`.

Puis, une fois le changement et la racine résolus :

```bash
openspec instructions archive --change "<nom>" --json
```

**Cette lecture est facultative et ne doit jamais bloquer l'archivage.** Si elle sort non nulle ou
rend du JSON invalide, par exemple sur une version plus ancienne de l'outil, continuer sans contexte
ni consigne. Ne pas signaler d'erreur, ne pas s'arrêter.

Une réponse valide peut omettre les deux champs facultatifs. `context` est une entrée obligatoire
qui porte les faits et contraintes du projet. `operationGuidance` est un conseil additif dont on
suit les entrées applicables. Ni l'un ni l'autre ne remplace une étape, un choix explicite de
l'utilisateur, un chemin résolu ou un contrat de commande : en cas de conflit, **signaler et garder
la valeur qui commande**. Ne déduire d'eux ni chemin de remplacement, ni question sautée, ni
drapeau, et ne pas recopier leur texte dans les specs, les artefacts ou le compte rendu.

## 2. Vérifier les artefacts, puis les tâches

```bash
openspec status --change "<nom>" --json
```

Le JSON porte `schemaName`, le contexte de chemins (`planningHome`, `changeRoot`, `artifactPaths`,
`actionContext`) et `artifacts` avec leur statut.

**Si un artefact n'est ni `done` ni `skipped`**, un artefact `skipped` satisfaisant l'exigence
puisque le changement déclare `skip_specs` : afficher l'avertissement, demander confirmation,
continuer si l'utilisateur confirme.

Puis compter les tâches **par l'outil, pas en lisant `tasks.md`** : lancer `openspec list --json`
avec les mêmes drapeaux de racine (`--store <id>` s'il y en a un), et prendre dans `changes`
l'entrée dont le `name` est **exactement** le changement retenu. L'outil résout les fichiers de
tâches que le schéma suit, y compris les noms d'artefacts propres, les chemins de sortie et les
motifs : un `tasks.md` absent à la racine du changement ne dit donc pas qu'il n'y a pas de tâche, et
le statut des artefacts ne dit pas qu'elles sont faites.

Exiger une seule entrée, et des `totalTasks` et `completedTasks` entiers et non négatifs, avec
`completedTasks <= totalTasks`. Les tâches inachevées valent `totalTasks - completedTasks`. Seules
les cases marquées `x` ou `X` comptent comme faites ; toute autre marque, même inconnue, reste
inachevée.

**Si cette lecture échoue**, rend un JSON invalide, omet le changement ou le rend deux fois, ou porte
des comptes invalides : signaler le problème et **s'arrêter avant toute fusion et tout archivage**.
Un compte qu'on ne sait pas lire ne se remplace pas par un compte deviné.

Des tâches inachevées, elles, **avertissent** et n'empêchent pas : afficher le compte, demander
confirmation, continuer si l'utilisateur confirme. Si `totalTasks` vaut zéro, continuer sans
avertissement.

## 3. Évaluer l'écart avec les specs principales

`artifactPaths.specs.existingOutputPaths` est la **seule** source de delta specs. Si l'entrée
`specs` manque ou que la liste est vide, continuer sans proposer de fusion, et ne pas déduire des
delta specs depuis d'autres artefacts.

Quand des delta specs existent, comparer chacune à sa spec principale sous
`<planningHome.root>/openspec/specs/<capability-path>/spec.md`, en utilisant le `planningHome.root`
rendu à l'étape 2 et non un chemin écrit en dur. Déterminer ce qui serait appliqué : ajouts,
modifications, retraits, renommages.

**Une spec principale absente ne vaut pas « déjà fusionnée ».** Pour une capacité neuve, la spec
principale est une **sortie** de la fusion, pas une entrée. Quatre cas, dans cet ordre :

- **Le delta porte des exigences MODIFIED ou RENAMED** : seules des exigences ADDED peuvent créer une
  spec principale. Le dire, et marquer la capacité **fusion bloquée**. On n'invente jamais une
  exigence qui n'a pas de version courante.
- **Le delta ne porte que des REMOVED, et le `.openspec.yaml` du changement déclare
  `retire_capabilities: true`** : la capacité est déjà retirée. La compter comme déjà fusionnée,
  avertir qu'il ne reste rien à retirer, et ne pas recréer la spec principale. Cette règle vaut ici
  et à la vérification de l'étape 4.
- **Sinon, le delta ne porte aucune exigence ADDED** : aucune fusion n'est possible, la capacité est
  marquée **fusion bloquée**. Pour un delta qui ne fait que retirer, avertir qu'il n'y a pas de spec
  principale où retirer, et laisser l'arbre des specs principales tel quel. `openspec archive`
  refuse ce cas non déclaré par `Spec must have at least one requirement`.
- **Sinon** : la capacité est à fusionner, et le résumé la nomme
  (`<capability-path>: new main spec will be created`). Si le delta porte aussi des REMOVED, avertir
  qu'ils seront ignorés faute de spec où retirer. La fusion crée la spec principale à partir des
  seules exigences ADDED, exactement comme `openspec archive`.

**Une capacité bloquée n'arrête pas l'évaluation des autres.** On les évalue toutes, puis on
**montre un résumé combiné avant de demander**.

Les réponses possibles :

- si une capacité au moins est bloquée : expliquer pourquoi, et ne proposer que « archiver sans
  fusionner » ou « annuler » ;
- sinon, quand il y a des changements : « fusionner maintenant (recommandé) » ou « archiver sans
  fusionner » ;
- sinon, quand tout est déjà fusionné : « archiver maintenant », « fusionner quand même » ou
  « annuler ».

« Annuler » arrête sans archiver. Aucune fusion ne démarre tant qu'une capacité est bloquée : si
l'utilisateur la demande quand même, réexpliquer le blocage et reproposer les choix ouverts. Toute
autre réponse fait reposer la question plutôt qu'archiver.

## 4. Fusionner en ligne, puis re-comparer

Avant qu'une fusion n'écrive la moindre spec principale :

```bash
openspec instructions specs --change "<nom>" --json
```

Exiger un code de sortie nul et un JSON d'instructions valide. En cas d'échec, signaler et
**s'arrêter avant d'écrire** ou de déplacer quoi que ce soit. Une réponse valide sans `rules` est le
cas « aucune règle ». Les `rules` rendues ne s'appliquent qu'au contenu et à la forme des specs
principales produites par cette fusion : ni consigne d'archivage, ni modification du comportement de
l'outil, ni texte à recopier.

Lancer ensuite le flux `openspec-sync-specs` **en ligne** pour le changement, en lui passant
l'analyse des delta specs et l'instantané de règles ci-dessus, et attendre qu'il finisse. La fusion
en ligne réutilise cet instantané sans redemander les instructions `specs`. **Ne pas la déléguer à
une tâche de fond** : l'étape 5 déplacerait `changeRoot` sous une fusion qui le lit encore. Si votre
agent ne sait la lancer que par délégation, déléguer de façon synchrone et attendre le résultat.

**Si la fusion rend une condition d'arrêt ou de blocage, elle a échoué.** Arrêter l'archivage tout de
suite : pas de comparaison de contenu après fusion, pas de déplacement de `changeRoot`. Rien n'a
bougé, l'utilisateur peut lever le blocage ou relancer la fusion.

Après que la fusion a écrit chaque spec principale, en vérifier la **structure** contre le contrat
de fusion :

- une spec principale neuve commence par un titre `# <capability> Specification` ; une spec
  existante garde son titre tel quel ;
- la section `## Purpose` d'une spec principale établie reste **entièrement intacte** ;
- pour une spec neuve, le `## Purpose` du delta est recopié tel quel. N'avertir que si ce texte est
  plus court que ce qu'attend la validation ordinaire, et ne jamais régénérer ni réécrire un
  `Purpose` déjà écrit. Sans `## Purpose` exploitable, garder le comportement existant : un
  `Purpose` TBD et son avertissement ;
- aucun en-tête de delta ne reste dans la spec principale : ni `## ADDED Requirements`, ni
  `## MODIFIED Requirements`, ni `## REMOVED Requirements`, ni `## RENAMED Requirements` ;
- les blocs d'exigence que la fusion a écrits ou changés portent des titres `### Requirement:`, et
  leurs scénarios des titres `#### Scenario:`, sous la section `## Requirements`. Ce que le delta ne
  mentionne pas reste exactement tel quel.

Puis **re-comparer depuis le début de l'étape 3**, y compris le cas de la capacité retirée par
`retire_capabilities: true` dont la spec principale manque, contre **toutes** les capacités portant
une delta spec, et pas seulement celles que la fusion dit avoir touchées. Une fusion réussie ne
laisse rien à appliquer, donc chaque capacité doit se lire comme déjà fusionnée :

- les exigences ADDED sont présentes ;
- les exigences MODIFIED portent les changements de scénario et de description nommés dans le delta,
  leurs autres scénarios intacts ;
- les exigences REMOVED ont disparu, et là où la fusion a retiré la dernière exigence d'une capacité,
  laissant `## Requirements` vide, sa spec principale est **supprimée** plutôt que laissée vide ;
- les exigences RENAMED sont présentes sous le nouveau nom et absentes sous l'ancien.

Si la fusion a échoué, ou si une capacité ne correspond pas, dire ce qui diffère et **s'arrêter**.
Rien n'a bougé, `changeRoot` est intact, et la reprise reste possible.

## Une exigence écrite à la proposition a vieilli

Une delta spec écrite **avant** la réalisation porte souvent, sous une exigence, la mention qu'aucun
dispositif ne la vérifie encore. C'était vrai à l'écriture, c'est faux à la clôture, et la recopier
telle quelle rend la spécification principale **fausse le jour de sa création**.

Mesuré sur le premier archivage du dépôt, #4833 : **quatre exigences sur cinq** étaient dans ce cas.

Une exigence qui ne dit pas comment elle est vérifiée se relit comme vérifiée. Et quand le dispositif
manque encore, l'écrire aussi : un scénario qu'aucun test ne tient décrit une garantie **visée**, non
tenue, et la spec le dit à cet endroit plutôt que de laisser le lecteur le supposer.

## 5. Déplacer

Créer le dossier d'archive s'il manque, puis déplacer :

```bash
mkdir -p "<planningHome.changesDir>/archive"
mv "<changeRoot>" "<planningHome.changesDir>/archive/<nom-cible>"
```

Le nom cible garde le nom du changement quand il commence déjà par `YYYY-MM-DD-`, sinon on préfixe
la date du jour. **Jamais deux dates empilées**, c'est la règle d'`openspec archive`. Si la cible
existe déjà, échouer et proposer de renommer l'archive existante ou de changer de date.

Le fichier `.openspec.yaml` suit le dossier : il n'y a rien à préserver à la main.

## 6. Rendre compte

```markdown
## Ce qui dit que la passe est finie

Trois signaux, et le troisième est celui qu'on oublie parce qu'il regarde ailleurs :

```bash
openspec list --json        # plus aucun changement actif
openspec validate --specs   # vert
```

Et `openspec/changes/` ne doit plus porter le delta que `openspec/specs/` porte désormais.

**Un archivage qu'on croit fait laisse la spécification principale muette.** C'est le mode de panne
de cette passe : le dossier a bougé, et ce qu'il devait verser n'a été versé nulle part.

## Archivage terminé

**Changement :** <nom>
**Schéma :** <schema-name>
**Archivé sous :** le chemin dérivé de `planningHome.changesDir`/<nom-cible>/
**Specs :** « fusionnées dans les specs principales » seulement si la vérification de l'étape 4 est
passée ; sinon « aucune delta spec » ou « fusion écartée »
```

Et la ligne des avertissements : « tous les artefacts et toutes les tâches sont complets », ou la
liste de ce qui ne l'était pas, par exemple « archivé avec 2 tâches inachevées ».

## Ce que ce dépôt ajoute au flux de l'outil

**L'archivage est un acte de clôture de chantier, pas de fin d'issue.** Il se fait quand le chantier
entier est livré, à la passe de clôture qui le porte, et non à chaque PR fusionnée.

**Il ne remplace pas l'écriture des ADR.** La spec principale dit ce que le produit doit faire ;
l'ADR dit pourquoi on l'a décidé, avec son article et son niveau de vérification. Les deux se
tiennent, aucune ne se substitue à l'autre.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « La fusion tourne, j'archive en parallèle » | Le déplacement de `changeRoot` la casse en silence. Fusion en ligne, puis vérification, puis déplacement. |
| « La fusion dit avoir touché deux capacités, je vérifie ces deux-là » | On re-compare **toutes** les capacités portant une delta spec. |
| « Des tâches restent, j'arrête » | Un manque avertit et se confirme, il ne refuse pas. |
| « `openspec instructions archive` a échoué, j'abandonne » | Cette lecture est facultative. On continue sans contexte. |
| « La capacité n'a plus d'exigence, je laisse la spec vide » | Elle se supprime. Une spec gardée, même signalée, ne passe plus la vérification. |
| « La spec principale n'existe pas, donc tout est déjà fusionné » | Pour une capacité neuve, elle est la sortie de la fusion. Seul un retrait déclaré par `retire_capabilities: true` se lit comme déjà fait. |
| « `openspec list` ne répond pas, je compte les cases de `tasks.md` » | Un compte illisible arrête avant toute fusion et tout archivage. |
| « Je préfixe la date pour être sûr » | Jamais deux dates empilées. |
