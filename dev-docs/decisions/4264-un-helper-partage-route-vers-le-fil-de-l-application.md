---
type: adr
title: "Un helper partagé route lui-même vers le fil de l'application"
status: stable
article: A16
chantier: "#4264, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "ScenesHabilleesTest#un_helper_qui_lit_la_scene_passe_par_le_fil_fx"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# Un helper partagé route lui-même vers le fil de l'application

## Contexte

`recette.CadreVisible` lisait les bornes de ses nœuds depuis le fil du test : onze appels partaient de
`main`. JavaFX n'autorise ces lectures que sur son fil, mais il ne les refuse pas franchement. Le
symptôme n'était donc pas une exception nette, c'était **deux instabilités qui rougissaient ailleurs**,
une fois sur cinq, très loin de la cause. Elles ont coûté deux enquêtes, #4200 et #4187.

L'idiome existait pourtant déjà : `e2e.AttenteAvantClic` lit ses bornes dans une méthode appelée
uniquement depuis `robot.interact(...)`. `CadreVisible` en avait divergé, et rien ne l'en empêchait.

## Décision

Un **helper partagé** de test qui lit le graphe de scène **route lui-même** vers le fil de
l'application, par `WaitForAsyncUtils.asyncFx(...)`, `robot.interact(...)` ou `Platform.runLater(...)`.

Ce n'est pas à son appelant de savoir sur quel fil il se trouve.

## Conséquences

**Un aller-retour par lecture**, avec un butoir de dix secondes. C'est réel, et c'est le prix d'une
lecture qui ne ment pas : une lecture depuis le mauvais fil ne coûte rien et rend une valeur dont on ne
sait pas si elle est juste.

**Le garde fait un saut, parce que la propriété est inter-procédurale.** `AttenteAvantClic` route dans
une méthode et lit dans une autre : aucune lecture de sources ne peut relier les deux sans devenir un
analyseur de flot. Les jetons de routage sont donc les trois idiomes JavaFX, **plus toute méthode du
fichier dont le corps en contient un** - c'est ainsi que le `surLeFilFx` de `CadreVisible` en devient
un. Une lecture est en règle si elle est dans une région routée, ou si le nom de la méthode qui la
contient est cité dans une région.

⚠️ **Ce garde a été faux deux fois avant d'être juste**, et les deux erreurs valent d'être connues
parce qu'elles se reproduiront ailleurs :

1. **Vert sans rien inspecter.** Il filtrait sur un chemin déjà amputé de son préfixe
   `src/test/java/fr/univ_amu/iut/`, si bien qu'un test sur `"/recette/"` y était faux pour **tous** les
   fichiers. D'où le compteur de non-vacuité, sur le modèle de `BancDesClipsTest`.
2. **Vert sur le vrai défaut.** La règle était « le fichier route quelque part ». Mesuré en retirant le
   routage de `CadreVisible` : il lui reste un `robot.interact` pour son défilement, sans aucun rapport
   avec les lectures, et cela suffisait à le rassurer. **Un garde qui ne rattrape pas le cas qui l'a
   motivé est pire que pas de garde**, parce qu'il rassure.

Aucune des deux n'a été trouvée en relisant le garde. Les deux l'ont été en **mutant le code qu'il
surveille** et en regardant s'il rougissait.

## Alternatives écartées

- **Documenter le contrat et faire confiance.** C'est l'état d'origine, et il a coûté deux enquêtes.
- **Vérifier au comportement.** Le défaut est intermittent : un test qui appelle depuis le mauvais fil
  passe la plupart du temps. Un garde de sources ne dépend d'aucune machine, comme le dit déjà
  l'[ADR 3826](3826-un-test-qui-mesure-monte-une-scene-habillee.md) pour son voisin.
- **Introduire `semgrep`** pour exprimer la règle. L'outil n'est pas dans le dépôt, et l'introduire pour
  un seul garde coûterait plus que l'analyse à un saut.
