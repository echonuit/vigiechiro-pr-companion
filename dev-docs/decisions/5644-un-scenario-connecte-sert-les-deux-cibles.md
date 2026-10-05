---
type: adr
title: "Un scénario connecté sert les deux cibles, et celui qui n'existe que sur la plateforme de test se déclare"
status: stable
article: A5
chantier: "#5644, sous-chantier de #5640 (deux cibles pour les tests connectés)"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/recette/DeclarationDeLaPlateformeTest.java"
  - "src/test/java/fr/univ_amu/iut/recette/CorrespondanceRecetteTest.java"
  - ".github/scripts/verifie_decisions_du_tournage_connecte.py"
verification_note: "le premier refuse une classe qui déclare une plateforme sans en porter le tag ; le deuxième compte les cas du tournage national sans ceux qui portent plateforme-de-test-seule, et l oracle du tournage attend ce compte ; le garde tient que la comparaison refuse clips-connectes et ne refuse pas clips-plateforme-de-test. Rien ne tient qu un scénario écrit pour une seule cible aurait pu servir les deux : c est un jugement"
relations:
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
  complete: ["4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un scénario connecté sert les deux cibles, et celui qui n'existe que sur la plateforme de test se déclare

## Contexte

L'[ADR 5641](5641-les-tests-connectes-ont-deux-cibles.md) donne deux cibles aux tests connectés : la
plateforme de test, montée par les tests, et la plateforme nationale. Le tournage des clips connectés
visait la seconde en dur, avec un jeton révoqué en fin d'exécution.

Elle laissait une condition ouverte. Les clips de la plateforme de test ne se comparent que si deux
tournages du même commit restent sous le plancher de bruit, et cela devait se mesurer « au lot qui
tournera ces clips ».

## Décision

**Le tournage choisit sa cible, et un même scénario sert les deux quand il le peut.**

Un scénario qui déclare `connecteALaPlateforme()` ou `parleALaPlateforme()` lit la cible que le profil
de l'exécution déclare. Il est tourné sur la plateforme nationale et sur la plateforme de test, sans
être écrit deux fois : deux scénarios pour un même cas feraient deux sources de vérité.

**Un scénario qui n'a de sens que sur la plateforme de test la vise explicitement**, par
`surLaPlateformeDeTest(...)`, et porte le tag `plateforme-de-test-seule`. Le tournage national
l'exclut, et son oracle ne l'attend pas. C'est le cas de ce qui écrit : lancer une participation,
publier une correction, importer les observations d'une analyse terminée. Sur la plateforme nationale
ces gestes abîmeraient des données réelles, ou supposeraient un état qu'on ne peut pas y déclarer.

**Les clips de la plateforme de test vont sur leur propre pré-version**, et la comparaison les
accepte. Elle continue de refuser `clips-connectes`, dont l'écran suit des données vivantes.

## La condition de l'ADR 5641, mesurée

Six tournages du même commit, soit quinze paires par clip, avec l'instrument du flux :

| clip | son plancher |
|---|---|
| la publication d'une correction | 0,005 % |
| la connexion | 0,134 % |
| l'actualisation d'un traitement | 0,302 % |
| l'annonce de l'import | 0,325 % |
| le lancement d'une participation | 0,503 % |

Aucun ne dépasse le pire plancher des clips ordinaires, 0,979 %. La condition est tenue : ces clips se
comparent.

## Ce qui a été écarté

**Un scénario par cible.** Il aurait rendu chaque scénario plus simple à lire, et fait diverger les
deux en silence.

**Tourner sur la plateforme de test tout ce qui se tourne sur la nationale.** Un cas qui regarde un
état de traitement bouger demande le worker, que la plateforme de test n'embarque pas : sans lui, un
calcul lancé y reste planifié.

## Conséquences

Neuf cas de recette ont leur clip sur la plateforme de test, sept sur la plateforme nationale. Un cas
dont le verdict se lit hors de l'image le dit par sa portée, et la réserve est reprise mot pour mot
sur la page des clips.

Un tournage sur la plateforme nationale reste manuel, et ses clips restent hors comparaison.
