---
name: worktree
description: Use before starting any branch work, before taking an issue, and before running a build while the application is running. Ensures the work happens in an isolated worktree rather than in the main checkout, which several parallel sessions share.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: incidents du 2026-08-12
---

# Worktree

## Loi d'airain

```
LE DÉPÔT D'ORIGINE NE SERT QU'À RÉCUPÉRER ET TESTER `main`
```

On n'y crée pas de branche, on n'y édite pas, on n'y committe pas. Tout travail de branche se fait
dans un worktree dédié.

## Annoncer

« J'utilise la compétence worktree pour isoler <la branche>. »

## Pourquoi, et ce que ça a coûté

**Plusieurs sessions travaillent en parallèle sur ce dépôt, sous un compte partagé.** Partager un
seul arbre de travail produit des dégâts silencieux. Tous ceux-ci ont eu lieu le même jour :

| Ce qui s'est passé | Comment ça s'est manifesté |
|---|---|
| Une session a committé le travail en cours d'une autre | sous son propre message de commit |
| Un `git checkout` concurrent a déplacé HEAD entre deux commandes | un commit destiné à une branche a atterri **sur `main`** |
| Une version volontairement neutralisée, injectée pour prouver qu'un test rougit, a été committée | telle quelle |
| `target/` en contention | erreurs de compilation sans rapport : « could not create parent directories », « cannot access » sur des classes existantes |

Un worktree a son propre `target/`, ce qui supprime aussi la contention Maven.

## Étape 0 : suis-je déjà isolé ?

Avant de créer quoi que ce soit.

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" && pwd -P)
git rev-parse --show-superproject-working-tree   # non vide = sous-module, pas un worktree
```

- `GIT_DIR` different de `GIT_COMMON`, et pas de sur-projet : **vous êtes déjà dans un worktree**.
  Ne pas en créer un second.
- Égaux : vous êtes dans la copie principale. Créez le worktree.

## Étape 1 : créer

```bash
git -C /home/nedjar/sandbox/R202/SAE201/vigiechiro-pr-companion fetch -q origin
git -C /home/nedjar/sandbox/R202/SAE201/vigiechiro-pr-companion \
    worktree add ../vigiechiro-wt/<branche> -b <branche> origin/main
```

**Les worktrees vivent à côté du dépôt, jamais dans `/tmp`.** Le suivi se fait dans VSCode, sur le
dossier `sandbox/R202` : un worktree hors de cet arbre est invisible pour la personne qui relit.

## Étape 2 : y travailler, sans jamais `cd`

`cd` persiste entre deux appels d'outil et l'oubli ramène silencieusement dans la copie principale.
**Utiliser `git -C <worktree>`**, toujours.

## Avant de prendre une issue

L'assignation est muette, le compte est partagé : une autre session peut déjà l'avoir prise.

```bash
git -C <depot> worktree list      # une branche déjà sortie = quelqu'un y travaille
```

## Avant d'affirmer qu'un travail n'a pas été fait

**Refetch d'abord.** Une base de worktree périmée fait conclure à tort qu'un correctif manque.

```bash
git -C <depot> fetch -q origin && git -C <worktree> log --oneline origin/main -3
```

## Construire pendant que l'application tourne

Le build écrit dans `target/`, l'application y lit ses ressources. Lancer `mvn` pendant que l'appli
tourne produit des erreurs sans rapport avec le code. **Construire dans un autre worktree.**

## Signaux d'alerte : on s'arrête

| Signe | Ce qu'il révèle |
|---|---|
| `git status` propre alors qu'on vient d'éditer | on n'est pas dans l'arbre qu'on croit |
| `git branch --show-current` rend `main` sans qu'on l'ait demandé | HEAD a bougé sous nous |
| Un commit dont le message ne correspond pas au contenu | on a committé le travail d'une autre session |
| Erreurs Maven « cannot access » sur des classes existantes | `target/` en contention |

Dans tous ces cas : **mesurer avant de réparer.**

```bash
git -C <depot> rev-list --count origin/main..main
git -C <depot> log --oneline origin/main..main
```

## Nettoyer après fusion

```bash
git -C <depot> worktree remove ../vigiechiro-wt/<branche>
```
