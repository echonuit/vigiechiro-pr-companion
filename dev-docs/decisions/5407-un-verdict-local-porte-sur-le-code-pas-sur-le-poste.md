---
type: adr
title: "Un verdict local porte sur le code, pas sur l'état du poste"
status: stable
article: A9
chantier: "#5404 (la préparation de l'environnement local), lot #5407"
decided_at: 2026-09-07
verification: certaine
enforced_by:
  - "scripts/methode/prepare-l-environnement.py"
  - "scripts/methode/verifie-commandes-prescrites.py"
verified:
  - by: machine:ci
    at: 2026-09-07
generated:
  by: "process:assistance-par-agents"
---

# Un verdict local porte sur le code, pas sur l'état du poste

## Le défaut, et pourquoi il ne se voyait pas

Un garde qui refuse faute d'un prérequis rend un verdict qui **ressemble à un défaut du changement en
cours**. On le lit comme tel, on cherche dans son diff, on ne trouve pas, et on finit par classer le
refus « environnemental ».

Le dépôt avait déjà constaté ce mécanisme pour les **modules** Python. L'en-tête de `pyproject.toml`,
écrit par #5008, le dit sans détour : « en local, six des neuf plantaient nu sur
`ModuleNotFoundError`, erreur qui ressemble à un défaut du changement en cours ». La leçon avait été
tirée pour les modules et jamais pour les **outils**.

**Ce que ce classement a coûté, mesuré.** Le 2026-09-06, le cliquet `4617` a été classé
« environnemental » six fois de suite dans une même session. Il portait un refus réel : un paramètre
de constructeur faisait passer deux contrôleurs au-delà du seuil `ExcessiveParameterList`. La CI l'a
dit ; la batterie locale l'avait tu six fois.

Un garde qui refuse faute de données n'est pas vert, il est **muet** - c'est la différence que
l'[ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md) pose entre `0` et `?`.

## La décision

**Ce que le dépôt déclare, il le pose. Ce qu'il ne peut pas poser, il le refuse en le disant.**

Trois familles, et elles n'appellent pas le même geste :

| famille | exemple | ce que le dépôt fait |
|---|---|---|
| déclarée, posable | l'outil OpenSpec, le `.venv` et son groupe `gardes` | il la **pose**, à la création du worktree |
| déclarée, chère et conditionnelle | `target/pmd.xml` | il la pose **si le diff l'engage**, et la porte seule le sait |
| non posable | un paquet réel, une carte montée, un module dans un interpréteur qu'on n'a pas choisi | il **refuse en le disant**, avec le remède |

La troisième ligne est celle qui compte le plus, et elle se lit à l'envers : **une préparation muette
qui échoue rendrait la porte moins sûre qu'avant**, puisque le lecteur croirait l'environnement
complet. Chaque échec nomme sa commande.

## Trois conséquences, qui n'ont pas mérité leur propre ADR

**Un venv par worktree**, et non un venv partagé. C'est la décision de #4849 appliquée aux outils
Python : une branche qui change une version épinglée serait sinon éprouvée contre celle d'une autre.
Deux prescriptions concurrentes coexistaient - `CONTRIBUTING.md` décrivait `.venv` avec le groupe
entier, une compétence décrivait un venv partagé avec `ruff` seul - et la seconde a disparu. Une
prescription qui perd ne cohabite pas.

**Un outil se déclare avec sa distribution ET sa commande**, quand les deux diffèrent. `graphifyy`
s'installe, `graphify` s'appelle : `pip install graphify` n'existe pas et `npm i -g graphify` installe
un paquet tiers sans rapport. On n'obtient pas une erreur, on obtient le mauvais outil.

**Le dépôt propose une installation, il ne l'impose pas.** `graphify install --project` enregistre des
crochets qui s'exécutent avant chaque appel d'outil de l'agent, et écrit dans un fichier de méthode
tenu à la main. Changer ce qu'un agent exécute n'est pas une déclaration de dépendance : c'est un
**choix individuel**, qui appartient à qui travaille sur le dépôt. Le dépôt dit quoi installer et
comment, puis s'arrête.

## Ce que cette décision n'autorise pas

**Imposer un outil pour se simplifier la tâche.** `uv run --group gardes` résolvait tout - 6 s la
première fois, **0 s** ensuite, rien à poser ni à nommer. Il a été écarté parce que `pyproject.toml`
écrit qu'« aucune des deux n'est un prérequis du dépôt », et que la CI ne l'a pas. Une décision déjà
prise ne se contredit pas au détour d'un correctif de confort.

## Le risque assumé

**Ce dispositif n'a aucun gardien en CI.** Le runner installe tout lui-même et ne joue jamais ce
chemin. Il repose donc entièrement sur ses auto-tests, qui ont été **vus rouges sur leur propre
mutation** plutôt que crus sur parole - une préparation qui cesse de poser fait tomber deux cas, et
retirer le croisement avec le groupe `gardes` en fait tomber deux autres.

C'est la contrepartie de tenir une promesse que la CI n'a pas besoin qu'on lui tienne.
