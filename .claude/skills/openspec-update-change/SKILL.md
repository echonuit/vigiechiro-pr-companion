---
name: openspec-update-change
description: Update an OpenSpec change by revising its existing planning artifacts and keeping them coherent with one another. Use when the user wants to revise a change's plan, fold new decisions into it, or reconcile its artifacts after an edit. Never edits code.
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

# Reprendre un changement OpenSpec

## Loi d'airain

```
ON REVISE LE PLAN, JAMAIS LE CODE
```

Si le plan revise implique de toucher au code, on s'arrete et on renvoie vers `/realiser`.

## Annoncer

« J'utilise la compétence openspec-update-change pour reprendre le changement <nom>. »

## Quand on l'ouvre

Cette compétence a longtemps porté une procédure sans déclencheur : rien n'y disait à quel moment on
la convoque, et une compétence qu'aucune cérémonie n'appelle est une compétence qu'on n'ouvre que si
l'on y pense déjà (#4911).

Trois moments l'appellent, et les trois viennent du **milieu** d'un chantier :

- **un élargissement posé et accepté.** [`openspec-apply-change`](../openspec-apply-change/SKILL.md)
  s'arrête quand une tâche demande plus que la spécification ne décrit, et fait poser la question.
  Quand la réponse élargit le périmètre, **le plan écrit doit suivre** : c'est ici.
- **une conception que la réalisation dément.** Le même arrêt, pour une autre cause : ce qu'on
  découvre en écrivant contredit ce que la note de conception supposait.
- **un arbitrage du mainteneur** qui renverse une décision déjà posée dans les artefacts.

**Ce que coûte de ne pas le faire, mesuré.** Une révision de `emporter-une-nuit` a écarté la
régénération d'une sélection reçue. Trois artefacts ont continué de la supposer : le titre de D1, une
clause de la delta spec et la tâche 5.2. Ils ont été trouvés en relisant, pas par un garde, et un
seul d'entre eux aurait suffi à faire archiver en passe 10 une spécification que le code dément.

C'est la raison d'être de l'étape 4 : la confrontation se fait **dans les deux sens**, et une édition
d'un artefact tardif oblige à revoir un artefact antérieur aussi souvent que l'inverse.

**Ce qu'elle n'est pas : la commande `openspec update`.** Le nom prête à confusion. Si l'utilisateur
veut cette commande de l'outil, qui rafraîchit les fichiers engendrés, ce n'est pas ce flux : lancer
la commande à la place. Dans ce dépôt, jamais avec `--force` : elle rendrait à l'anglais amont les six
compétences adoptées (ADR 4515), et le garde de #4516 rougirait.

## Ce que cette compétence fait, et ce qu'elle ne fait pas

Elle révise les artefacts de planification d'un changement **qui existent déjà**, et les tient
cohérents entre eux. Elle ne crée aucun artefact manquant et n'écrit pas une ligne de code.

Une seule création de fichier lui revient : un fichier qui manque sous un artefact glob **déjà en
partie peuplé**, à un chemin concret que l'utilisateur a confirmé (étape 4). Hors de ce cas, elle
n'ouvre aucun fichier neuf.

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, ou si le travail y vit, lister les identifiants enregistrés :

```bash
openspec store list --json
```

Puis passer `--store <id>` sur les commandes qui lisent ou écrivent des specs et des changements :
`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`,
`schemas`, `view`. Une fois choisie, la réserve **colle** au reste du travail : chaque exemple non
qualifié ci-dessous est un raccourci, et le drapeau se rajoute avant de lancer. Les autres commandes
ne le prennent pas. Sans réserve, les commandes agissent sur la racine `openspec/` la plus proche.

## Vérifier que le projet a une racine OpenSpec

Ce flux suppose un projet qui se sert déjà d'OpenSpec. **Avant la première étape qui écrit quoi que
ce soit** (`new change`, `archive`, `sync specs`, ou l'écriture d'un fichier d'artefact), s'assurer
que le projet a une racine :

```bash
openspec list --json
```

Avec `--store <id>` si une réserve est choisie, puisque la réserve est alors la racine. Lire le champ
`root` :

- **un objet** : le projet est installé, on continue.
- **`"root": null`** : il ne l'est pas. Il n'y a pas de dossier `openspec/` ici, et une écriture
  comme `openspec new change` en créerait un par effet de bord. La commande sort aussi en erreur :
  c'est la réponse, pas un outil cassé. Lire le JSON plutôt que de relancer ou de contourner.

**Un `"root": null` qui ne parle pas d'installation.** Quand un message du tableau `status` de ce JSON
commence par `Declared in` ou par `Invalid store declaration in`, et nomme le
`openspec/config.yaml` (ou `config.yml`) de ce projet, le projet se sert bien d'OpenSpec, par une
réserve qu'il déclare et que la machine ne sait pas résoudre : réserve non enregistrée, ou ligne
`store:` mal formée. Ce n'est pas un projet vierge, et les deux cas qui suivent ne s'appliquent pas.
On s'arrête avant d'écrire, et on montre à l'utilisateur le `message` et le `fix` de cette erreur.

**Sinon, sans racine, la suite dépend de la façon dont on est arrivé ici :**

- **choisie d'office** : on a pris ce flux de soi-même, sans que l'utilisateur nomme OpenSpec, nomme
  cette compétence ou lance sa commande. On laisse OpenSpec de côté et on répond à la demande comme
  si l'outil n'était pas installé. On ne lui demande aucune installation, on n'en parle pas.
- **demande explicite** : l'utilisateur a nommé OpenSpec, nommé cette compétence ou lancé sa
  commande. On s'arrête avant d'écrire et on demande comment poursuivre : installer le projet
  (`openspec init`), viser une réserve qu'il a déjà (`--store <id>`), ou continuer sans OpenSpec
  pour cette demande. On attend sa réponse.

**Dans les deux cas, la racine ne se crée jamais par effet de bord** : pas d'`openspec init` tant que
l'utilisateur ne l'a pas demandé, pas de fichier `openspec/` écrit à la main, pas de commande qui la
crée en passant. Un dossier apparu sans qu'on l'ait voulu fait croire à un projet installé.

Dans ce dépôt, la racine `openspec/` existe : la vérification répond un objet. Elle se fait quand même,
parce que la réserve choisie peut en changer la réponse.

## Entrée

Le nom du changement est facultatif. S'il manque, le déduire du fil de la conversation. S'il reste
vague ou ambigu, **demander** plutôt que de choisir.

Ce flux révise des artefacts qui existent déjà ; **il ne crée jamais ceux qui manquent**. Quand un
artefact manque, `openspec status --change "<nom>" --json` nomme le suivant, et
`openspec instructions "<artifact-id>" --change "<nom>" --json` dit comment l'écrire.

## Fonction de garde

```
1. CHOISIR    le changement, et l annoncer avec la facon de le remplacer.
2. LIRE       l etat par `openspec status --json`, sans jamais deviner les identifiants
              d artefacts ni les chemins.
3. COMPRENDRE la demande : une revision precise, ou une revue de coherence.
4. RAPPROCHER chaque artefact existant de l edit demande, DANS LES DEUX SENS. L edit se
              redige dans la conversation : aucun fichier n est touche ici.
5. CONFIRMER  chaque revision AVANT de l ecrire, une par une. C est la seule etape qui ecrit.
6. RENVOYER   vers l etape suivante, sans jamais l executer.
```

## 1. Choisir le changement

Si un nom est fourni, le prendre. Sinon : le déduire du fil, ou choisir d'office s'il n'y a qu'un
seul changement actif. Si c'est ambigu, lister les candidats :

```bash
openspec list --json
```

Présenter les trois ou quatre changements les plus récemment modifiés, avec pour chacun son nom, son
avancement (« 0/5 tâches », « complet », « aucune tâche ») et sa fraîcheur (champ `lastModified`).
Marquer le plus récent « (recommandé) ».

Annoncer toujours : « Changement retenu : <nom> », et comment le remplacer, par exemple
`/reprendre <autre>`.

## 2. Lire l'état, sans rien supposer

```bash
openspec status --change "<nom>" --json
```

Le JSON porte `schemaName`, `artifacts` avec leur statut (`done`, `skipped`, `ready`, `blocked`),
`isPlanningComplete` (les versions plus anciennes de l'outil rendent la même valeur sous
`isComplete`), et le contexte de chemins : `planningHome`, `changeRoot`, `artifactPaths`,
`actionContext`.

**Les identifiants d'artefacts et les chemins viennent du schéma actif.** Ne pas les supposer, ne pas
brancher sur des noms d'artefacts écrits en dur : un schéma personnalisé doit fonctionner sans
retouche.

Les fichiers à éditer sont ceux de `artifactPaths.<id>.existingOutputPaths`, déjà développés pour un
artefact glob. **Ne jamais écrire dans `resolvedOutputPath`** : pour un artefact glob, c'est encore le
motif, pas un fichier.

## 3. Comprendre la demande

Une demande précise (« la conception passe à X ») est le point de départ de l'édition. Un « mets ça
au propre » est une **revue de cohérence** : lire les artefacts existants et les confronter les uns
aux autres, en cherchant contradictions, manques et redites.

**Une demande qui change l'intention du changement n'est plus une révision.** Elle le remplace au lieu
de l'affiner. Demander alors un nom de changement distinct et libre, et recommander de repartir de
zéro sous ce nom :

```bash
openspec new change "<nouveau-nom>"
```

## 4. Rapprocher, dans les deux sens

Lire l'artefact que la demande touche, et les autres artefacts existants du changement.

**Rédiger l'édition demandée dans la conversation, pas dans les fichiers.** Établir exactement ce
qu'elle change ; l'écriture revient à l'étape 5, et à elle seule. Puis confronter chaque autre
artefact à cette édition rédigée, **dans les deux sens** : une édition d'un artefact tardif peut
obliger à revoir un artefact antérieur, et pas seulement l'inverse. L'ordre de construction est un
ordre de lecture commode, pas une contrainte sur ce qui peut être révisé. Noter tout ce qui devient
incohérent, manquant ou contradictoire.

Proposer des révisions aux seuls fichiers qui existent (`existingOutputPaths`). Pour le reste, le
statut décide :

- **un artefact sans aucun fichier, au statut `ready` ou `blocked`** : le noter, et renvoyer
  l'utilisateur vers `openspec instructions "<artifact-id>" --change "<nom>" --json` pour savoir
  comment le créer. On ne le crée pas ici.
- **un artefact `skipped`** : n'y pas toucher. Il n'est ni manquant ni en retard, il a été écarté ;
  ne pas le signaler comme absent, ne pas le renvoyer à plus tard.

**Le cas d'un artefact glob déjà en partie peuplé.** Un artefact glob (par exemple `specs/**/*.md`)
passe `done` dès qu'un fichier correspond au motif, et le flux de continuation de l'outil ne prend
que les artefacts `ready`. `/opsx:continue` est un flux optionnel que ce dépôt n'installe pas, et
même installé il ne verrait pas ce fichier manquant : personne d'autre ne le créerait. Quand le
rapprochement trouve un fichier qui manque sous un artefact glob dont `existingOutputPaths` n'est pas
vide, on le crée ici, en cinq temps :

1. **Lire les instructions.** `openspec instructions "<artifact-id>" --change "<nom>" --json`, et
   s'appuyer sur son `instruction` et son `template`. `context` et `rules` sont des contraintes : on
   les respecte, on ne les recopie pas dans le fichier. Si les instructions rendent `skipped: true`,
   on ne crée pas le fichier. Relire sur le disque les fichiers dont l'artefact dépend ; si une
   dépendance requise, non écartée, manque, on s'arrête et on demande à l'utilisateur de la rétablir
   d'abord.
2. **Choisir un chemin concret** dans `changeRoot`, qui correspond à `artifactPaths.<id>.outputPath`
   et n'existe pas encore. Vérifier qu'il reste dans `changeRoot` une fois résolus les liens
   symboliques de ses dossiers parents. Le `resolvedOutputPath` du glob n'est pas une cible valable.
3. **Le mettre dans les révisions proposées** à l'étape 5, et ne le créer qu'après accord.
4. **Rafraîchir juste avant de créer.** Après l'accord, relancer le statut et les instructions.
   Vérifier que l'artefact est toujours dans le périmètre, toujours non écarté, toujours en partie
   peuplé, et refaire les vérifications de chemin du deuxième temps.
5. **Créer par une opération qui échoue si le fichier existe.** Si `instruction` confie la création à
   une autre compétence ou commande, ne l'appeler que si elle peut tenir le chemin confirmé et ces
   garde-fous ; sinon s'arrêter. Si une vérification échoue, ou si le brouillon confirmé ne tient
   plus, s'arrêter et reprendre avec l'utilisateur : ne jamais remplacer un contenu existant ni se
   rabattre sur un autre chemin.

Si le changement est déjà cohérent, le dire et ne proposer aucune révision.

## 5. Confirmer, puis écrire, un artefact à la fois

**Cette étape porte toutes les écritures du flux** : aucune étape antérieure n'édite un artefact.

Montrer chaque révision proposée et sa raison, **y compris l'édition demandée**, rédigée à l'étape 4.
**N'écrire qu'après accord.** Un refus laisse l'artefact inchangé. Pour une réécriture substantielle,
récupérer d'abord les règles et le gabarit de l'artefact :

```bash
openspec instructions "<artifact-id>" --change "<nom>" --json
```

## 6. Renvoyer vers la suite, sans l'exécuter

- Artefacts sans fichier, au statut `ready` ou `blocked` : `openspec status --change "<nom>" --json`
  donne le suivant, et `openspec instructions "<artifact-id>" --change "<nom>" --json` dit comment le
  créer.
- Changement déjà réalisé, tâches cochées : le code peut ne plus correspondre au plan révisé,
  renvoyer vers `/realiser`.
- Tout fait et tout réalisé : renvoyer vers `/archiver`.

## Ce que ce dépôt ajoute au flux de l'outil

**Le corps de l'EPIC et celui de l'issue portent la vérité publique.** Une révision qui change la
lecture d'une issue se répercute **dans le corps de l'issue**, pas seulement dans l'artefact. Le
dépôt en porte la trace : #3451 et #3439 ont laissé une prémisse démentie en commentaire, et c'est
l'erreur qu'on lit en premier.

**Le « pourquoi » durable ne vit pas dans `design.md`.** Il va dans une ADR de
`dev-docs/decisions/`, numérotée par son issue. La note de conception prépare l'ADR ; elle ne la
remplace pas. Une révision qui renverse une décision déjà prise appelle donc une ADR, pas seulement
une édition.

**Une tâche est une issue.** Réviser `tasks.md` sans toucher aux issues rattachées à l'EPIC laisse
deux découpages divergents, et c'est la forge qui fait foi pour ce qui est pris et par qui.

## Compte rendu

Après chaque passage, dire : quels artefacts ont été révisés et lesquels ont été refusés ; quel
fichier a été créé sous un artefact glob déjà en partie peuplé ; ce qui a été laissé de côté parce
qu'il n'existe pas encore (artefacts sans fichier au statut `ready` ou `blocked`, jamais un artefact
`skipped`) ; où en est le changement et quelle commande vient ensuite.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « L'artefact `tasks` existe forcément » | Les identifiants viennent du schéma. Lire `openspec status --json`. |
| « J'écris dans `resolvedOutputPath` » | Pour un artefact glob, c'est le motif, pas un fichier. |
| « Le plan change, donc j'ajuste le code » | Jamais ici. On s'arrête et on renvoie vers `/realiser`. |
| « J'applique l'édition demandée, elle est demandée » | Elle se rédige dans la conversation. L'étape 5 porte toutes les écritures, après accord. |
| « Cet artefact manque, je le crée » | S'il n'a aucun fichier et qu'il est `ready` ou `blocked`, le signaler et renvoyer vers `openspec instructions`, pas le combler. Seule exception : un fichier sous un artefact glob déjà peuplé, en cinq temps. |
| « Cet artefact `skipped` est en retard » | Il a été écarté. On n'y touche pas, on ne le signale pas comme absent. |
| « Le chemin a changé entre l'accord et la création, je prends le voisin » | On s'arrête et on reprend avec l'utilisateur. Jamais un autre chemin, jamais un fichier écrasé. |
| « Pas de dossier `openspec/`, je lance `openspec init` » | La racine ne se crée jamais par effet de bord. Demander, ou répondre sans OpenSpec. |
| « J'ai révisé, je confirmerai à la fin » | Chaque édition se confirme avant d'être écrite. |
| « La demande change le but du changement » | Ce n'est plus une révision. Demander un nom distinct et libre, et proposer `openspec new change "<nouveau-nom>"`. |
