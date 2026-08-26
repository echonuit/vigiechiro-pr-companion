---
name: openspec-archive-change
description: Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete.
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

## Entrée

Le nom du changement est facultatif. S'il manque, le déduire du fil. S'il reste vague, demander.

## Fonction de garde

```
1. CHOISIR    le changement, et charger les entrees d archivage (facultatif, jamais bloquant).
2. VERIFIER   les artefacts, puis les taches. Un manque AVERTIT, il ne refuse pas.
3. EVALUER    l ecart entre chaque delta spec et sa spec principale, et le MONTRER avant de demander.
4. FUSIONNER  en ligne si l utilisateur le choisit, puis RE-COMPARER toutes les capacites.
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

Puis lire le fichier de tâches, typiquement `tasks.md`, et compter les `- [ ]` face aux `- [x]`. Des
tâches inachevées **avertissent** et n'empêchent pas : afficher le compte, demander confirmation.
Sans fichier de tâches, continuer sans avertissement.

## 3. Évaluer l'écart avec les specs principales

`artifactPaths.specs.existingOutputPaths` est la **seule** source de delta specs. Si l'entrée
`specs` manque ou que la liste est vide, continuer sans proposer de fusion, et ne pas déduire des
delta specs depuis d'autres artefacts.

Quand des delta specs existent, comparer chacune à sa spec principale sous
`<planningHome.root>/openspec/specs/<capability-path>/spec.md`, en utilisant le `planningHome.root`
rendu à l'étape 2 et non un chemin écrit en dur. Déterminer ce qui serait appliqué : ajouts,
modifications, retraits, renommages. **Montrer un résumé combiné avant de demander.**

Les réponses possibles : quand il y a des changements, « fusionner maintenant (recommandé) » ou
« archiver sans fusionner » ; quand tout est déjà fusionné, « archiver maintenant », « fusionner
quand même » ou « annuler ». Toute autre réponse fait reposer la question plutôt qu'archiver.

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

Puis **re-comparer depuis le début de l'étape 3**, contre **toutes** les capacités portant une delta
spec, et pas seulement celles que la fusion dit avoir touchées. Une fusion réussie ne laisse rien à
appliquer, donc chaque capacité doit se lire comme déjà fusionnée :

- les exigences ADDED sont présentes ;
- les exigences MODIFIED portent les changements de scénario et de description nommés dans le delta,
  leurs autres scénarios intacts ;
- les exigences REMOVED ont disparu, et là où la fusion a retiré la dernière exigence d'une capacité,
  laissant `## Requirements` vide, sa spec principale est **supprimée** plutôt que laissée vide. Une
  spec que la fusion a délibérément conservée et signalée convient aussi ;
- les exigences RENAMED sont présentes sous le nouveau nom et absentes sous l'ancien.

Si la fusion a échoué, ou si une capacité ne correspond pas, dire ce qui diffère et **s'arrêter**.
Rien n'a bougé, `changeRoot` est intact, et la reprise reste possible.

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
| « La capacité n'a plus d'exigence, je laisse la spec vide » | Elle se supprime, sauf si la fusion l'a délibérément gardée et l'a dit. |
| « Je préfixe la date pour être sûr » | Jamais deux dates empilées. |
