---
name: openspec-propose
description: Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.
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

# Proposer un changement OpenSpec

## Loi d'airain

```
CE FLUX PRODUIT DES ARTEFACTS DE PLANIFICATION, ET RIEN D AUTRE
```

La demande qui a déclenché ce flux **n'autorise que la planification**, même si elle dit « construis »
ou « corrige ». Aucun code n'est touché. Une fois les artefacts présentés, on s'arrête et on attend
une nouvelle demande pour lancer la réalisation.

## Annoncer

« J'utilise la compétence openspec-propose pour ouvrir le changement <nom>. »

## Ce que le flux produit

Les artefacts que le schéma définit. Avec le schéma `spec-driven`, qui est celui de ce dépôt :

- `proposal.md` : quoi et pourquoi ;
- `specs/<capability-path>/spec.md` : ce que le système doit faire, sous forme de **delta** et non de
  spec principale ;
- `design.md` : comment ;
- `tasks.md` : les étapes de réalisation.

Le `<capability-path>` est le dossier de spec relatif à `specs/`, par exemple `user-auth` ou
`identity/user-auth`. Conserver le chemin entier d'une capacité existante, et suivre l'organisation
déjà en place pour une capacité nouvelle.

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, lister les identifiants par `openspec store list --json`, puis passer `--store <id>` sur les
commandes qui lisent ou écrivent des specs et des changements : `new change`, `status`,
`instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`. Une
fois choisie, la réserve colle au reste du travail. Sans réserve, les commandes agissent sur la
racine `openspec/` la plus proche.

## Entrée

Un nom de changement en kebab-case, ou une description de ce qu'il y a à construire.

## Fonction de garde

```
1. COMPRENDRE la demande, et lever les ambiguites QUI CHANGENT le perimetre.
2. RETENIR    le schema configure, sauf demande explicite d un autre.
3. CREER      le dossier du changement.
4. LIRE       l ordre de construction par `openspec status --json`.
5. ECRIRE     chaque artefact de l ensemble requis, en relisant ses dependances SUR DISQUE.
6. S ARRETER  une fois les artefacts presentes. La realisation est une autre demande.
```

## 1. Comprendre, et lever ce qui change le périmètre

Sans entrée claire, demander, en ouvert et sans proposer d'options :

> « Sur quel changement veux-tu travailler ? Décris ce que tu veux construire ou corriger. »

De la description, dériver un nom en kebab-case : « ajouter l'authentification » donne
`add-user-auth`.

**Ne pas avancer sans avoir compris ce qui est demandé.** Une ambiguïté qui change matériellement le
périmètre, le comportement observable, la compatibilité ou les critères d'acceptation se **demande**.
Un détail mineur se tranche par une hypothèse raisonnable, écrite dans les artefacts.

## 2. Retenir le schéma

Utiliser le schéma configuré par défaut, sauf si l'utilisateur en demande un autre par son nom, avec
`--schema <schema-name>`.

S'il demande à voir les flux disponibles, résoudre d'abord la racine faisant autorité par
`openspec context --json`, puis lancer `openspec schemas --json` depuis le `root.path` rendu. Cela
préserve les racines choisies par un pointeur `store:` local ou par le `defaultStore` global. Quand
une réserve enregistrée a été explicitement choisie, ajouter `--store "<store-id>"` aux deux
commandes. Si `context` ne rend que `no_openspec_root`, lancer `openspec schemas --json` depuis le
dossier courant.

## 3. Créer le dossier du changement

```bash
openspec new change "<nom>"
openspec new change "<nom>" --schema "<schema-name>"
```

La seconde forme n'est à employer que pour un schéma explicitement demandé. La commande crée un
changement échafaudé dans la racine de planification que l'outil résout avec `.openspec.yaml`.

## 4. Lire l'ordre de construction

```bash
openspec status --change "<nom>" --json
```

Le JSON porte `applyRequires`, la liste des artefacts nécessaires avant la réalisation ; `artifacts`,
chacun avec son statut et ses arêtes `requires` ; et le contexte de chemins `planningHome`,
`changeRoot`, `artifactPaths`, `actionContext`. **Se servir de ces chemins plutôt que de supposer des
chemins relatifs au dépôt.**

## 5. Écrire chaque artefact de l'ensemble requis

Tenir une liste de tâches pour suivre l'avancement, et boucler dans l'ordre des dépendances, en
commençant par les artefacts qui n'en attendent aucune.

Pour chaque artefact `ready` :

```bash
openspec instructions <artifact-id> --change "<nom>" --json
```

Le JSON rend `context` (les contraintes du projet, **pour vous**, jamais à recopier), `rules` (les
règles de l'artefact, mêmes conditions), `template` (la structure du fichier à produire),
`instruction` (la conduite propre à ce type d'artefact), `resolvedOutputPath` (le chemin ou le motif
où écrire), `dependencies` (les artefacts déjà faits à lire pour le contexte), et parfois `skipped`
ou `warning` quand le changement déclare `skip_specs` et que l'artefact ne doit **pas** être créé.

**Relire les dépendances depuis le disque**, même si on les a vues plus tôt dans la conversation :
l'utilisateur a pu les éditer.

Si `instruction` délègue la création à une compétence ou une commande, l'invoquer plutôt qu'écrire le
fichier soi-même, puis vérifier que l'artefact existe bien au `resolvedOutputPath`. Sinon, écrire le
fichier en suivant `template`. Quand `resolvedOutputPath` est un motif, `instruction` dit comment
choisir le chemin concret.

**L'ensemble requis, c'est `applyRequires` PLUS tout ce qui en découle** en suivant les arêtes
`requires`, transitivement. Avec `spec-driven`, cela ferme sur `proposal`, `specs`, `design` et
`tasks`. Ce qui est hors de cet ensemble se laisse tranquille.

**`status` ne regarde que l'existence des fichiers.** Un artefact d'`applyRequires` marqué `done` ne
prouve donc pas que ses dépendances existent : écrire `tasks.md` en premier marque `tasks` fait alors
que `specs` n'a jamais été écrit. C'est sur les arêtes `requires` qu'on construit l'ensemble, pas sur
les statuts, un artefact `done` déclarant toujours ce dont il dépend.

Un artefact déjà `skipped` est satisfait : ses fichiers ne doivent pas exister, et il ne faut jamais
essayer de le créer. Un artefact peut aussi être sauté quand sa propre `instruction` le dit
conditionnel, ce qui est le cas de `design.md` avec `spec-driven`. `specs` ne se saute que par le
statut `skipped`, jamais par jugement. Le dire à l'utilisateur, et ne pas y revenir.

**Les dépendances ouvrent, elles ne barrent pas** : un artefact requis encore `blocked` parce qu'on a
sauté une dépendance conditionnelle s'écrit quand même.

Après chaque écriture, relancer `openspec status --change "<nom>" --json` : créer un artefact peut en
débloquer d'autres. S'arrêter quand tout l'ensemble requis est `done`, `skipped`, ou délibérément
sauté.

Si un artefact demande une précision, la demander, puis continuer.

## 6. S'arrêter, et rendre compte

```bash
openspec status --change "<nom>"
```

Puis résumer : le nom et l'emplacement du changement ; la liste des artefacts créés avec une phrase
chacun, plus tout artefact conditionnel sauté et pourquoi ; et la phrase qui conclut, « les artefacts
sont prêts pour relecture ; quand tu veux, lance `/realiser` ».

## Ce que ce dépôt ajoute au flux de l'outil

**L'étape 0 du dépôt vient avant ce flux.** Balayer les issues ouvertes par **concept** et non par
mot-clé, chercher l'EPIC vivant qui couvrirait déjà le besoin, et vérifier ce qui est déjà pris.
Proposer un changement sur un sujet déjà traité ailleurs se découvre au conflit de fusion, quand deux
chemins existent.

**`tasks.md` se traduit en issues rattachées à l'EPIC.** Le découpage vit dans les deux endroits, et
c'est la forge qui fait foi pour ce qui est pris et par qui.

**Le « pourquoi » durable va en ADR.** `design.md` prépare la décision ; l'ADR la porte, avec son
article et son niveau de vérification.

**Un `SHALL` nomme son dispositif de vérification.** Quand aucun n'existe encore, l'écrire plutôt que
de laisser le lecteur le supposer.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « La demande dit de construire, j'enchaîne » | Elle n'autorise que la planification. On présente, puis on attend. |
| « `tasks` est `done`, donc `specs` existe » | `status` ne regarde que l'existence des fichiers. Suivre les arêtes `requires`. |
| « `specs` ne sert à rien ici, je le saute » | `specs` ne se saute que par le statut `skipped`, jamais par jugement. |
| « J'ai lu `design.md` tout à l'heure » | Les dépendances se relisent sur disque : l'utilisateur a pu les éditer. |
| « Je recopie `context` dans la proposition » | `context` et `rules` sont des contraintes pour vous, pas du contenu. |
| « L'artefact est `blocked`, j'attends » | Une dépendance conditionnelle sautée ne barre pas : on écrit quand même. |
| « Un changement de ce nom existe déjà, je le complète » | Demander : continuer celui-là, ou en ouvrir un autre. |
