---
name: openspec-sync-specs
description: Sync delta specs from a change to main specs. Use when the user wants to update main specs with changes from a delta spec, without archiving the change.
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

# Fusionner les delta specs dans les specs principales

## Loi d'airain

```
ON FUSIONNE, ON NE RECOPIE PAS
```

Une delta spec ne se déverse jamais telle quelle dans une spec principale. Ce qu'elle ne mentionne
pas reste, dans l'ordre existant, et les en-têtes d'opération ne survivent pas à la fusion.

## Annoncer

« J'utilise la compétence openspec-sync-specs pour fusionner les delta specs de <nom>. »

## Une opération conduite par l'agent

Ce n'est pas une fusion programmatique. On lit les delta specs et on édite les specs principales,
ce qui permet une fusion **intelligente** : ajouter un scénario sans recopier l'exigence entière.

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, lister les identifiants par `openspec store list --json`, puis passer `--store <id>` sur les
commandes qui lisent ou écrivent des specs et des changements : `new change`, `status`,
`instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`. Une
fois choisie, la réserve colle au reste du travail. Sans réserve, les commandes agissent sur la
racine `openspec/` la plus proche.

Le `<capability-path>` est le dossier de spec relatif à `specs/`. Conserver le chemin **entier** de
chaque delta spec pour retrouver sa spec principale.

## Fonction de garde

```
1. CHOISIR    le changement, et l annoncer avec la facon de le remplacer.
2. RESOUDRE   `planningHome.root` : les specs principales en dependent, jamais un chemin en dur.
3. TROUVER    les delta specs par `artifactPaths.specs.existingOutputPaths`, et RIEN d autre.
4. FUSIONNER  capacite par capacite, en preservant ce que le delta ne mentionne pas.
5. VALIDER    par `openspec validate --specs`, et ne rien affirmer avant.
6. RENDRE     compte, y compris des specs laissees avec un Purpose a ecrire.
```

## 1. Choisir le changement

Si un nom est fourni, le prendre. Sinon, le déduire du fil, ou choisir d'office s'il n'y a qu'un
changement actif. Si c'est ambigu, `openspec list --json` donne les candidats : ne montrer que les
changements qui **portent des delta specs**.

Annoncer toujours : « Changement retenu : <nom> », et comment le remplacer, par exemple
`/fusionner <autre>`.

## 2. Résoudre le contexte

```bash
openspec status --change "<nom>" --json
```

Le JSON porte `planningHome.root`. Les specs principales vivent sous
`<planningHome.root>/openspec/specs/`. C'est cette racine, consciente des réserves, qui sert pour
**tous** les chemins de specs principales, jamais un chemin de dépôt écrit en dur. Quand une réserve
est choisie, elle désigne la réserve et non le dépôt courant.

## 3. Trouver les delta specs

`artifactPaths.specs.existingOutputPaths` est la **seule** source de chemins de delta specs. Si
l'entrée `specs` manque ou que la liste est vide, dire qu'il n'y a rien à fusionner et s'arrêter,
sans demander d'instructions d'artefact ni écrire de spec principale. Ne pas les déduire d'autres
artefacts.

Fusionner **tous** les chemins de la liste, sauf si l'appelant a restreint l'ensemble. Il le fait en
nommant une liste explicite d'entrées complètes de `existingOutputPaths`, recopiées telles quelles.
L'archivage le fait en ligne, un utilisateur peut le faire aussi. Alors on ne fusionne que les
chemins nommés et on laisse les autres intacts : un archivage en lot écarte une delta dont il n'a pas
trouvé la réalisation, et la fusionner quand même écrirait une spec principale que l'appelant avait
délibérément retenue. **Cette restriction se porte jusqu'à l'étape 4, jamais élargie en chemin.** Un
chemin nommé absent de `existingOutputPaths` ne se fusionne pas : on le signale et on s'arrête, plutôt
que de le laisser tomber en silence. Une liste nommée vide veut dire qu'il n'y a rien à fusionner.

Chaque delta spec porte des sections `## ADDED Requirements`, `## MODIFIED Requirements`,
`## REMOVED Requirements` et `## RENAMED Requirements`, cette dernière au format `FROM:` / `TO:`.

## 4. Fusionner, capacité par capacité

Avant la première écriture, obtenir **un** instantané courant des règles de `specs`. Si l'archivage
a invoqué ce flux en ligne et a fourni un instantané valide, le réutiliser sans redemander. Sinon,
lancer une fois `openspec instructions specs --change "<nom>" --json` avec les mêmes drapeaux de
racine. Si cette lecture directe sort non nulle ou rend un JSON invalide, **signaler et s'arrêter
avant toute écriture** : un échec ne vaut pas « aucune règle ». Une réponse valide sans `rules`, en
revanche, veut bien dire qu'aucune règle n'est configurée, et la fusion continue.

Les `rules` rendues ne contraignent que le contenu et la forme des specs principales produites par
cette fusion. Ce ne sont pas des consignes d'opération : elles ne changent ni la racine choisie, ni
les chemins de delta, ni les contrôles de l'outil, ni les étapes. Leur texte ne se recopie pas.

Pour chaque delta spec retenue : lire la delta, lire la spec principale, qui peut ne pas exister
encore, puis appliquer.

**ADDED** : l'exigence absente s'ajoute ; l'exigence déjà présente se met à jour, ce qui revient à un
MODIFIED implicite.

**MODIFIED** : retrouver l'exigence et appliquer les changements, qu'il s'agisse d'ajouter des
scénarios que la spec principale n'a pas, d'en modifier, ou de changer la description. **Préserver
les scénarios et le contenu que le delta ne mentionne pas.**

**REMOVED** : retirer le bloc entier. La suppression du fichier `spec.md`, et du dossier une fois
qu'il ne reste rien, ne se fait que si **toutes** ces conditions tiennent :

1. le retrait de ce passage ne laisse aucun bloc d'exigence ;
2. le reste de la spec est bien formé, avec son `## Purpose` ;
3. la spec principale n'était pas déjà vide avant la fusion, car ne rien retirer ne change rien ;
4. chaque autre ligne non vide du fichier s'explique comme le titre, le Purpose, l'en-tête des
   exigences, ou l'énoncé, les scénarios et les exemples d'une exigence canonique ;
5. le `.openspec.yaml` du changement déclare `retire_capabilities: true` ;
6. le `spec.md` se résout **dans** la vraie racine des specs, sans suivre un lien symbolique de
   dossier de capacité vers un fichier extérieur.

Si le retrait ne laisserait aucun bloc d'exigence et qu'une condition manque, **ne pas modifier la
spec principale** : arrêter la fusion pour cette capacité, dire quelle condition bloque et comment la
lever. Ne jamais écrire ni laisser une section `## Requirements` vide. Quand seul le marqueur manque,
le dire aussi : c'est la seule chose que l'utilisateur peut ajouter pour que le retrait passe.

Supprimer le fichier supprime aussi son `## Purpose`, et toute autre section bloque le retrait.
Nommer le Purpose dans le compte rendu. Ne proposer un `git checkout` collable que si la spec vivait
dans la copie de l'appelant ; sinon, donner une conduite de récupération adaptée à sa copie.

**RENAMED** : retrouver l'exigence FROM, la renommer en TO.

**Un `## Purpose` dans le delta** : si la spec principale en a déjà un, il fait autorité et on n'y
touche pas, ce que fait `openspec archive`, qui avertit et poursuit.

**Créer une spec principale** quand la capacité n'existe pas encore, sous
`<planningHome.root>/openspec/specs/<capability-path>/spec.md` : reprendre le `## Purpose` du delta
mot pour mot quand il en porte un, et ne poser un court texte à écrire que s'il n'y en a pas. Puis la
section des exigences avec les ADDED, au format de référence ci-dessous.

## 5. Valider

```bash
openspec validate --specs
```

Avec les mêmes drapeaux de racine. En cas d'échec, dire ce qui cloche et **ne pas annoncer que la
fusion a réussi**.

## 6. Rendre compte

Quelles capacités ont été mises à jour et ce qui a changé ; toute spec principale neuve laissée avec
un Purpose à écrire, pour qu'il s'écrive maintenant plutôt qu'il ne traîne ; toute capacité retirée,
en nommant le `spec.md` supprimé, son Purpose, et la conduite de récupération.

## Le format d'une delta spec

```markdown
## Purpose

Seulement sur un delta qui introduit une capacité neuve. Il amorce la spec principale.

## ADDED Requirements

### Requirement: New Feature
The system SHALL do something new.

#### Scenario: Basic case
- **WHEN** user does X
- **THEN** system does Y

## MODIFIED Requirements

### Requirement: Existing Feature
The system SHALL keep doing the existing thing, now also handling A.

#### Scenario: Scenario the main spec already has
- **WHEN** user does X
- **THEN** system does Y

#### Scenario: New scenario to add
- **WHEN** user does A
- **THEN** system does B

## REMOVED Requirements

### Requirement: Deprecated Feature

## RENAMED Requirements

- FROM: `### Requirement: Old Name`
- TO: `### Requirement: New Name`
```

## Le format d'une spec principale

C'est ce dans quoi le delta fusionne. Elle ne porte **jamais** d'en-tête d'opération
(`## ADDED/MODIFIED/REMOVED/RENAMED Requirements`) : après fusion, chaque exigence vit sous une seule
section `## Requirements`.

```markdown
# <capability> Specification

## Purpose
Short description of what this capability does and why it exists.

## Requirements

### Requirement: New Feature
The system SHALL do something new.

#### Scenario: Basic case
- **WHEN** user does X
- **THEN** system does Y
```

## Le principe : fusionner plutôt qu'écraser

Un bloc MODIFIED porte l'exigence **entière**, son corps et chaque scénario qui survit au changement.
`openspec validate` et `openspec archive` refusent tous deux un bloc qui laisserait tomber un scénario
que la spec principale porte encore. Ce que le delta ne mentionne pas se garde, dans l'ordre existant.

L'opération est **idempotente** : la lancer deux fois donne le même résultat.

## Ce que ce dépôt ajoute au flux de l'outil

**Un `SHALL` nomme son dispositif de vérification** : test, garde, script, cas de recette. Quand
aucun n'existe encore, l'écrire plutôt que de laisser le lecteur le supposer. Une exigence qui ne dit
pas comment elle est vérifiée se relit comme vérifiée.

**Les en-têtes structurels et les mots-clés restent en anglais**, `SHALL`, `MUST`, `WHEN`, `THEN`,
`Requirement`, `Scenario`. Le reste s'écrit en français.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je recopie la delta dans la spec principale » | On fusionne. Les en-têtes d'opération ne survivent pas. |
| « L'appelant a nommé deux chemins, je fusionne tout » | La restriction se porte, jamais élargie. |
| « Les instructions `specs` ont échoué, donc pas de règles » | Un échec ne vaut pas « aucune règle ». On s'arrête avant d'écrire. |
| « Plus aucune exigence, je supprime le fichier » | Six conditions, toutes. Sinon on ne modifie rien et on dit laquelle bloque. |
| « Le MODIFIED ne cite qu'un scénario, je remplace » | Il porte l'exigence entière. Ce qu'il ne mentionne pas se garde. |
| « J'écris `## Requirements` vide en attendant » | Jamais. C'est l'état que le retrait doit éviter. |
