---
name: openspec-update-change
description: Update an OpenSpec change by revising its existing planning artifacts and keeping them coherent with one another. Use when the user wants to revise a change's plan, fold new decisions into it, or reconcile its artifacts after an edit. Never edits code.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.10.0"
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

## Ce que cette compétence fait, et ce qu'elle ne fait pas

Elle révise les artefacts de planification d'un changement **qui existent déjà**, et les tient
cohérents entre eux. Elle ne crée aucun artefact, n'ouvre aucun fichier sous un artefact glob, et
n'écrit pas une ligne de code.

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

## Entrée

Le nom du changement est facultatif. S'il manque, le déduire du fil de la conversation. S'il reste
vague ou ambigu, **demander** plutôt que de choisir.

`/opsx:continue` est un flux optionnel qui peut ne pas être installé. Avant de le suggérer, vérifier
qu'il existe. Sinon, `openspec status --change "<nom>" --json` donne l'artefact suivant, et
`openspec instructions "<artifact-id>" --change "<nom>" --json` dit comment le créer.

`/opsx:new` est un flux optionnel de la même famille. S'il manque, `openspec new change
"<nouveau-nom>"` crée le changement sous un nom neuf.

## Fonction de garde

```
1. CHOISIR    le changement, et l annoncer avec la facon de le remplacer.
2. LIRE       l etat par `openspec status --json`, sans jamais deviner les identifiants
              d artefacts ni les chemins.
3. COMPRENDRE la demande : une revision precise, ou une revue de coherence.
4. RAPPROCHER chaque artefact existant de l edit demande, DANS LES DEUX SENS.
5. CONFIRMER  chaque revision AVANT de l ecrire, une par une.
6. RENVOYER   vers l etape suivante, sans jamais l executer.
```

## 1. Choisir le changement

Si un nom est fourni, le prendre. Sinon : le déduire du fil, ou choisir d'office s'il n'y a qu'un
seul changement actif. Si c'est ambigu, lister les candidats :

```bash
openspec list --json
```

Présenter les trois ou quatre changements les plus récemment modifiés, avec pour chacun son nom, son
schéma (champ `schema`, sinon « spec-driven »), son avancement (« 0/5 tâches », « complet », « aucune
tâche ») et sa fraîcheur (champ `lastModified`). Marquer le plus récent « (recommandé) ».

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

## 4. Rapprocher, dans les deux sens

Lire l'artefact que la demande touche, et les autres artefacts existants du changement. Appliquer
l'édition demandée, puis confronter chaque autre artefact à cette édition, **dans les deux sens** :
une édition d'un artefact tardif peut obliger à revoir un artefact antérieur, et pas seulement
l'inverse. L'ordre de construction est un ordre de lecture commode, pas une contrainte sur ce qui
peut être révisé.

Réviser uniquement les fichiers qui existent (`existingOutputPaths`). Ne pas créer d'artefact
absent, ne pas inventer de fichier sous un artefact glob : les signaler et renvoyer vers
`/opsx:continue`.

Si le changement est déjà cohérent, le dire et ne rien éditer.

## 5. Confirmer, puis écrire, un artefact à la fois

Montrer chaque révision proposée et sa raison. **N'écrire qu'après accord.** Un refus laisse
l'artefact inchangé. Pour une réécriture substantielle, récupérer d'abord les règles et le gabarit
de l'artefact :

```bash
openspec instructions "<artifact-id>" --change "<nom>" --json
```

## 6. Renvoyer vers la suite, sans l'exécuter

- Artefacts encore absents : `/opsx:continue` pour les créer.
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

Après chaque passage, dire : quels artefacts ont été révisés et lesquels ont été refusés ; ce qui a
été renvoyé vers `/opsx:continue` ; où en est le changement et quelle commande vient ensuite.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « L'artefact `tasks` existe forcément » | Les identifiants viennent du schéma. Lire `openspec status --json`. |
| « J'écris dans `resolvedOutputPath` » | Pour un artefact glob, c'est le motif, pas un fichier. |
| « Le plan change, donc j'ajuste le code » | Jamais ici. On s'arrête et on renvoie vers `/realiser`. |
| « Cet artefact manque, je le crée » | C'est le travail de `/opsx:continue`. Le signaler, pas le combler. |
| « J'ai révisé, je confirmerai à la fin » | Chaque édition se confirme avant d'être écrite. |
| « La demande change le but du changement » | Ce n'est plus une révision. Vérifier si `/opsx:new` existe, sinon proposer `openspec new change "<nouveau-nom>"`. |
