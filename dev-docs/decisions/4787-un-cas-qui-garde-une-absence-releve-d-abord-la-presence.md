---
type: adr
title: "Un cas qui garde une absence relève d'abord la présence, dans le même cas"
status: stable
article: A4
chantier: "#4787 (EPIC #4416)"
decided_at: 2026-08-30
verification: humaine
verified:
  - by: humain:mutation
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un cas qui garde une absence relève d'abord la présence, dans le même cas

## Contexte

Trois des six cas de #4787 gardent un **non-effet** : `Espace` ne doit pas déclencher le bouton
focalisé (#1504), `Entrée` ne doit rien faire sans verdict, `O`/`D`/`J` doivent se taire pendant la
saisie.

Sur des raccourcis simplement **débranchés**, les trois seraient verts. Un cas qui constate qu'il ne
s'est rien passé ne distingue pas « le produit s'est retenu » de « le produit ne fait rien ».

## Le défaut, mesuré et non supposé

La première écriture de `S3-42` ne gardait que « Entrée enregistre ». Retirer du produit la condition
`peutEnregistrer` **laissait le cas vert** : un verdict était déjà retenu au moment de la frappe, donc
le chemin sans verdict n'était jamais emprunté.

Aucune relecture ne l'aurait vu. C'est la mutation qui l'a dit, et le même défaut avait déjà frappé
deux fois dans le palier :

| Cas | Ce qui le rendait vert | Ce qui manquait |
|---|---|---|
| `S3-16` « Annuler ne touche rien » | la modale ne touchait rien, jamais (#4734) | établir qu'elle touche quand on ne l'annule pas |
| `S6-04` « les cinq plus contactées » | le semis n'avait que cinq espèces | une sixième, qui doit rester dehors |
| `S3-42` « Entrée ne fait rien sans verdict » | un verdict était déjà posé | relever l'état sans verdict, avant |

## Décision

**1. Un cas qui garde une absence relève la présence dans le même cas**, et dans l'ordre : la touche
agit là où elle doit agir, puis se tait là où elle doit se taire. Deux cas séparés ne suffisent pas -
rien ne garantit qu'ils s'exécutent, ni qu'ils restent d'accord.

**2. L'état où le non-effet est attendu se CONSTRUIT, il ne se suppose pas.** `S3-42` relève l'absence
de verdict avant d'en poser un ; `S6-04` sème une sixième espèce pour que « les cinq plus contactées »
ait un sens. Un jeu qui ne discrimine pas rend le cas vrai sans le rendre gardé.

**3. Ce qu'aucune mutation ne peut faire rougir se déclare dans l'en-tête du cas.** Le non-effet de
`S3-42` est tenu par **trois couches en série** - la condition du contrôleur, la garde muette du
ViewModel (#1970), un refus plus bas encore - et les retirer une à une, puis deux ensemble, laisse le
cas vert. Il constate un fait vrai sans prouver laquelle le tient, et l'en-tête le dit.

## Ce que cette ADR ne mécanise pas

`verification: humaine` : aucun code ne sait qu'une assertion porte sur une absence, ni qu'elle est
appariée. Le motif de non-mécanisation est là - la question est sémantique, pas syntaxique.

Ce qui la tient est la **mutation**, exigée par A8 dès qu'un comportement est complet. Les trois
défauts ci-dessus ont tous été trouvés par elle, et aucun par relecture.
