---
type: adr
title: "Une sonde live déclare sa cible, et ce qu'elle saute sur la plateforme de test se dit"
status: stable
article: A4
chantier: "#5746, clôture de l'EPIC #5643 (chantier #5640)"
decided_at: 2026-10-03
verification: certaine
enforced_by:
  - "CibleLiveTest#le_profil_de_test_designe_la_plateforme_de_test"
  - "DeclarationDeLaPlateformeTest#une_classe_qui_monte_la_plateforme_de_test_porte_son_tag"
verified:
  - by: humain
    at: 2026-10-03
relations:
  amende: ["5663-la-plateforme-de-test-pose-l-etat-de-la-jvm-dans-un-fork-a-elle"]
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
generated:
  by: "process:assistance-par-agents"
---

# Une sonde live déclare sa cible, et ce qu'elle saute sur la plateforme de test se dit

## Le contexte

L'[ADR 5641](5641-les-tests-connectes-ont-deux-cibles.md) veut qu'un test connecté déclare sa cible.
Les deux classes du contrat live, `ContratApiVigieChiroLiveTest` et
`AllerRetourParticipationLiveTest`, ne savaient viser que la plateforme nationale, derrière un jeton et
des verrous d'écriture, et elles sautaient en entier sans lui.

Un saut par `assumeTrue` sort vert. Une classe qui saute dès son `@BeforeAll` sort même en « Tests
run: 0 » sur la console. Mesuré sur #5760 : la cible ignorée, les 29 sondes du contrat sautaient et le
job restait vert.

## La décision

**La cible se déclare par le profil, et ne se déduit pas.** `-Pplateforme-de-test` pose
`vigiechiro.cible=plateforme-de-test`, et `CibleLive.declaree()` rend alors l'URL, le jeton et les
participations de la plateforme de test, verrous d'écriture ouverts : rien n'y est à abîmer. Sans la
propriété, c'est la plateforme nationale, avec ses verrous inchangés. Déduire la cible de l'absence de
jeton ferait passer un oubli pour un choix.

**Sur la plateforme de test, rien ne saute, et ce qui saute se dit.** Le job `plateforme-de-test`
affiche ses tests sautés et leur motif, et `releve_des_sautes.py` refuse **tout** saut : depuis #5772,
l'état de départ porte ce que les sondes supposent, et une sonde neuve qui suppose une donnée absente
doit la déclarer plutôt que la sauter. Le verdict ne dépend d'aucun texte. Deux diagnostics disent
pourquoi quand ils le savent :

- un motif qui cite le jeton ou un verrou d'écriture, puisque `CibleLive` les y ouvre ;
- un rapport dont **tous** les tests sont sautés : la signature d'une classe interrompue avant ses
  tests.

## Ce qui change dans l'ADR 5663

5663 énumère deux façons de monter la plateforme, le banc et l'extension, et son garde ne voyait
qu'elles. **`CibleLive` est la troisième.** Elle ne monte la plateforme que sous le profil de test ; une
classe qui l'emploie sans le tag ne monterait donc pas Docker dans le build par défaut, mais le job
ne la jouerait jamais, sans que rien ne le dise. Le cas de `DeclarationDeLaPlateformeTest` la compte
depuis la clôture de #5643.

## Ce qui la tient

`CibleLiveTest` tient les deux branches de la cible ; la mutation « la cible déclarée est ignorée » le
fait rougir, quand `ContratApiVigieChiroLiveTest` passait, lui, à « Tests run: 0 ». Le cas élargi de
`DeclarationDeLaPlateformeTest` rougit sur un tag retiré d'une sonde live et sur un motif faussé. Le
relevé porte un auto-test de 13 cas dans `lint.yml`, et ses refus ont été vus rouges sur leur mutation,
le refus de tout saut compris.

## Ce qui a été écarté

**Déduire la cible de l'absence de jeton**, pesé à l'ouverture de #5643, pour la raison dite plus haut.

**Laisser passer les sauts faute de données**, le premier dessin du relevé (#5748). Il obligeait à
reconnaître un saut fautif par son texte, et un verrou neuf dont le message ne nommerait pas
`vigiechiro.*` lui échappait. Depuis que l'état de départ porte tout, rien ne doit sauter, et le refus
de tout saut ne dépend plus d'aucun motif.
