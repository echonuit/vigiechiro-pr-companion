---
name: openspec-apply-change
description: Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks.
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

# Réaliser un changement OpenSpec

## Loi d'airain

```
UNE TACHE QUI DEBORDE SE POSE, ELLE NE S ABSORBE PAS
```

Dès qu'une tâche demande plus que ce que la spécification décrit, ou qu'on est tenté de rogner, de
différer ou d'accepter une exception pour la faire entrer, on s'arrête et on le dit. Le silence sur
un élargissement est le défaut que cette compétence traque.

## Annoncer

« J'utilise la compétence openspec-apply-change pour réaliser le changement <nom>. »

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, ou si le travail y vit, lister les identifiants enregistrés par `openspec store list --json`,
puis passer `--store <id>` sur les commandes qui lisent ou écrivent des specs et des changements :
`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`,
`schemas`, `view`. Une fois choisie, la réserve colle au reste du travail. Les autres commandes ne
prennent pas le drapeau. Sans réserve, les commandes agissent sur la racine `openspec/` la plus
proche.

## Vérifier que le projet a une racine OpenSpec

Ce flux suppose un projet qui emploie déjà OpenSpec. Dans ce dépôt la racine existe ; la règle
reste, parce qu'une compétence se lit aussi depuis un autre arbre.

Avant le premier geste qui écrit (`new change`, `archive`, `sync specs`, ou la rédaction d'un
fichier d'artefact), lancer `openspec list --json`, avec `--store <id>` si une réserve est choisie
puisqu'elle est alors la racine, et lire `root`. Trois cas :

- **Un objet `root`** : le projet est installé, on continue.
- **`"root": null` venu d'une réserve introuvable** : le message d'erreur de `status` commence par
  `Declared in` ou `Invalid store declaration in` et nomme le `openspec/config.yaml` (ou
  `config.yml`) du projet. Le projet emploie bien OpenSpec, par une réserve qu'il déclare et que la
  machine ne résout pas (non enregistrée, ou ligne `store:` mal formée). Ce n'est pas un projet
  vierge : on s'arrête avant d'écrire, et l'on montre à l'utilisateur le `message` et le `fix` de
  cette erreur.
- **Aucune racine** : il n'y a pas de dossier `openspec/` ici, et une écriture comme
  `openspec new change` en créerait un en passant. La commande sort aussi en erreur : c'est la
  réponse, pas un outil cassé. On lit le JSON, on ne relance pas et on ne contourne pas. La suite
  dépend de la façon dont on est arrivé là :
  - **choisie d'office**, sans que l'utilisateur ait nommé OpenSpec, cette compétence ou sa
    commande : on laisse OpenSpec et l'on répond à la demande comme si l'outil n'était pas
    installé, sans demander d'installation ni en parler ;
  - **demande explicite**, l'utilisateur ayant nommé OpenSpec, cette compétence ou lancé
    `/realiser` : on s'arrête avant d'écrire et l'on demande la suite, entre installer le projet
    (`openspec init`), viser une réserve qu'il a déjà (`--store <id>`), ou continuer sans OpenSpec
    pour cette demande. On attend sa réponse.

**La racine ne se crée jamais par effet de bord**, dans aucune des deux branches : pas
d'`openspec init` que l'utilisateur n'a pas demandé, pas de fichier `openspec/` écrit à la main, pas
de commande qu'on laisse le créer.

## Entrée

Le nom du changement est facultatif. S'il manque, le déduire du fil. S'il reste vague ou ambigu,
**demander** la liste plutôt que de choisir.

## Fonction de garde

```
1. CHOISIR    le changement, et l annoncer avec la facon de le remplacer.
2. LIRE       l etat et les instructions, `openspec status` puis `openspec instructions apply`.
3. LIRE       les fichiers de `contextFiles`, tous, avant la premiere ligne de code.
4. BOUCLER    tache par tache : rouge, vert, refactor, cocher a sa ligne, relire l avancement.
5. S ARRETER  des qu une tache deborde, est ambigue, ou revele un defaut de conception.
6. RENDRE     compte, avec l avancement chiffre.
```

## 1. Choisir le changement

Si un nom est fourni, le prendre. Sinon, le déduire du fil, ou choisir d'office s'il n'y a qu'un
changement actif. Si c'est ambigu, `openspec list --json` donne les candidats.

Annoncer toujours : « Changement retenu : <nom> », et comment le remplacer, par exemple
`/realiser <autre>`.

## 2. Lire l'état, puis les instructions

```bash
openspec status --change "<nom>" --json
openspec instructions apply --change "<nom>" --json
```

Le premier donne `schemaName` et l'artefact qui porte les tâches. Le second rend `contextFiles`,
c'est-à-dire l'identifiant d'artefact associé à ses fichiers réels, l'avancement, la liste des
tâches avec, pour chacune, son état, son fichier (`sourcePath`) et sa ligne (`line`), une
instruction calculée depuis l'état courant, et, s'il y a lieu, `missingArtifacts`, les
identifiants des artefacts requis qui n'ont encore rien produit.

**Trois états, trois conduites.**

- **`blocked`** : on montre le message et l'on suspend la réalisation. Ce qui suit dépend de
  `missingArtifacts`.
  - S'il n'est pas vide, on propose de compléter les artefacts manquants :
    `openspec status --change "<nom>" --json` les liste, on retient le prochain artefact `ready`
    (ni `skipped` ni `blocked`), et `openspec instructions "<artifact-id>" --change "<nom>" --json`
    en donne les règles et le gabarit. Une réserve choisie garde son `--store <id>` sur les deux
    commandes.
  - S'il est vide, ce n'est pas un artefact qui manque : c'est le fichier de suivi que le schéma
    configure. On suit l'instruction de l'outil pour le créer ou le réparer à partir des artefacts
    de planification existants. On ne suppose pas qu'un autre artefact est prêt, et l'on ne
    commence pas la réalisation tant que l'état reste `blocked`.
- **`all_done`** : toutes les tâches suivies sont faites. On le dit, et l'on propose une revue ou
  une vérification, selon le changement, **avant** l'archivage. Une liste cochée n'est pas un
  changement vérifié.
- **Sinon** : on réalise.

**`context` et `operationGuidance` ne sont pas des preuves.** Le premier est une entrée obligatoire
qui porte les faits, conventions et contraintes du projet. Le second est un conseil additif dont on
suit les entrées applicables. Ni l'un ni l'autre ne remplace l'instruction calculée, ne prouve
qu'une tâche est faite, ni n'autorise à passer outre un état `blocked`. En cas de conflit avec
l'instruction, un choix explicite de l'utilisateur ou une valeur pilotée par l'outil, **signaler le
conflit et garder la valeur qui commande**.

## 3. Lire les fichiers de contexte

Lire **tous** les chemins listés sous `contextFiles`. Pour le schéma `spec-driven`, ce sont la
proposition, les specs, la conception et les tâches. Pour un autre schéma, suivre ce que l'outil
rend, sans supposer de noms de fichiers.

Ne pas recopier `context` ni `operationGuidance` dans les fichiers produits, sauf demande explicite.

## 4. Boucler, tâche par tâche

Pour chaque tâche en attente : dire laquelle on prend, faire le changement de code, le garder
**minimal et centré**, cocher la case, relire l'avancement, passer à la suivante.

**La case se coche là où l'outil la situe**, au `sourcePath` et à la `line` qu'il a rendus pour
cette tâche, et non en cherchant son libellé dans un fichier qu'on suppose. Avant d'écrire, on
vérifie que la case à cet endroit porte encore la description de la tâche : si le fichier a bougé
depuis, on relance `openspec instructions apply` et l'on prend l'emplacement rafraîchi. Puis
`- [ ]` devient `- [x]`, et l'on relance les instructions pour constater que la tâche est faite et
que l'avancement a changé. Une case cochée que l'outil ne voit pas n'a rien fait avancer.

**Le test précède le code** (article A7). Sur un défaut, le premier test **reproduit** le défaut
avant qu'aucun correctif ne soit écrit. Le refactor est la troisième phase de chaque tour, pas une
étape de fin de tâche : ce qui déborde se note pour la passe d'harmonisation.

Une case ne se coche que lorsque le comportement décrit est **entièrement** réalisé, jamais quand il
est partiel ou différé.

## 5. S'arrêter, et le dire

On s'arrête quand la tâche est ambiguë, quand la réalisation révèle un défaut de conception, quand
une erreur ou un obstacle apparaît, quand l'utilisateur interrompt, et surtout quand **une tâche
demande plus que ce que la spécification décrit**.

Ce dernier cas est celui qui se manque : on est tenté de rogner, de différer, ou d'accepter une
exception pour faire entrer la tâche dans son cadre. L'élargissement se **pose** et se demande. Le
dépôt en porte la trace : la réécriture de `dev-docs/captures.md` a été fondue dans la PR #3483 sans
que la question soit posée, et c'était défendable sans être une décision à prendre seul.

## 6. Rendre compte

Pendant :

```
## Réalisation : <nom-du-changement> (schéma : <schema-name>)

Tâche 3/7 : <description>
[...]
Tâche faite.
```

À la fin, ou à l'arrêt : les tâches faites dans la session, l'avancement chiffré, et la suite.
Quand tout est fait, dire que les tâches suivies sont toutes cochées, proposer la revue ou la
vérification qui convient, puis seulement `/archiver`. On ne félicite pas : la liste est close, le
changement n'est pas encore jugé. Quand on s'arrête, dire pourquoi et proposer des options plutôt
qu'une seule voie.

## Ce que ce dépôt ajoute au flux de l'outil

**Une tâche correspond à une issue de la forge.** Le bloc d'ouverture se pose sur l'issue avant la
première ligne de code, et l'issue s'assigne. Cocher une case dans `tasks.md` sans fermer l'issue
correspondante laisse deux avancements divergents.

**Quand la boucle s'arrête, la mutation mesure.** PIT sur les classes pures livrées, survivants lus
un par un : vrai trou, on écrit le test ; défensif inatteignable, on l'assume sans test creux ;
artefact de ciblage, on élargit et on remesure.

**Un garde neuf se voit rouge sur sa propre mutation** avant d'être cru. Un garde vert n'est pas un
garde vérifié.

## Le flux reste fluide

Cette compétence s'invoque à tout moment : avant que tous les artefacts soient faits si des tâches
existent, après une réalisation partielle, ou en alternance avec d'autres actions. Si la réalisation
révèle un défaut de conception, proposer de reprendre les artefacts par
[`openspec-update-change`](../openspec-update-change/SKILL.md) plutôt que de forcer le passage.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Cette tâche demande un peu plus, je le fais » | C'est l'élargissement silencieux. Le poser et demander. |
| « Je coche, je finirai après » | Une case cochée dit « entièrement réalisé ». |
| « Le contexte dit que c'est fait » | `context` n'est pas une preuve d'avancement. |
| « L'état est `blocked`, mais je vois quoi faire » | Aucun contexte n'autorise à passer outre. |
| « Je devine le nom du fichier de tâches » | Il vient de `contextFiles`, jamais d'une hypothèse. |
| « Je retrouve la case par son libellé » | Elle est au `sourcePath` et à la `line` que l'outil rend. |
| « Tout est coché, j'archive » | Une liste close n'est pas un changement vérifié : revue d'abord. |
| « J'écris le code, le test suivra » | Le test précède, et sur un défaut il le reproduit d'abord. |
