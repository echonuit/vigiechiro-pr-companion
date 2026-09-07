---
type: adr
title: "Un avant se fabrique à partir du passé, il ne se gèle pas dans le présent"
status: stable
article: A4
chantier: "#4980 (le retour de terrain de la 2.189.0), sous-chantier #4993"
decided_at: 2026-08-31
verification: humaine
enforced_by: []
verified:
  - by: humain:porteur-du-produit
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
---

# Un avant se fabrique à partir du passé, il ne se gèle pas dans le présent

## Contexte

Le sous-chantier #4993 devait montrer à l'observateur d'une session de terrain ce que huit correctifs
changent **à l'écran**, plutôt que le lui écrire. Un avant/après suppose le même cas joué sur deux
versions.

Or le cas qui démontre un correctif est presque toujours écrit **avec** le correctif : il n'existe pas
dans la version d'avant, et un tournage de cette version ne peut donc pas le contenir.

**Le raisonnement d'ouverture en tirait un gel des fusions**, et il tenait debout : le seul avant
fidèle s'obtiendrait en écrivant les cas pendant que `main` porte encore les défauts, en filmant, puis
en fusionnant les correctifs. Il fallait donc suspendre les fusions jusqu'à ce que les cas existent.

Il était faux, et il se mordait la queue. On ne peut pas filmer d'avant tant que le cas n'existe pas,
et écrire les cas était l'un des lots du sous-chantier : le gel aurait bloqué les correctifs pendant
**toute sa durée**, pour rien.

## Décision

**L'avant se fabrique à la demande, à partir de l'histoire, et aucune fusion n'attend.** Git garde le
code fautif indéfiniment ; un avant se reconstitue donc à tout moment.

Deux méthodes, selon l'âge du correctif :

| Le correctif est | La méthode | Pourquoi |
|---|---|---|
| récent, ses fichiers n'ayant pas rebougé | on applique l'**inverse de sa production** sur `main` | le harnais d'aujourd'hui reste en place, rien à greffer |
| plus ancien | on part de **son parent** et on y **greffe le scénario** | la rustine inverse est refusée, les fichiers ayant bougé |

**Dans les deux cas on ne défait que la production.** Le scénario reste celui d'aujourd'hui, et c'est
ce qui rend les deux clips comparables : si le scénario changeait aussi, la paire montrerait deux
gestes différents et ne prouverait rien sur le correctif.

Le test échoue alors, et c'est **voulu** : il constate le défaut qu'on vient de remettre. Le clip est
produit quand même, l'enregistreur indexant délibérément le film d'un cas rouge.

## Conséquences

Le mode d'emploi complet, avec le piège qui invalide la paire et le témoin qui le dit, vit dans
[Ce que la 2.189.0 montrait, et ce qu'elle montre maintenant](../recette/avant-apres-de-la-2189.md).
Cette ADR ne porte que la décision : **on ne gèle pas le dépôt pour produire une preuve**.

**Et cette page n'est pas ce qui tient la décision.** Elle a figuré en `enforced_by` jusqu'au
2026-09-07, l'ADR se déclarant `certaine`. Une page de prose ne s'exécute pas : elle ne peut faire
rougir aucune demande de fusion, et le champ promettait donc quelque chose que rien ne rendait. Le
renvoi était juste, seul son emplacement était faux.

Ce que la décision règle - à partir de quoi on fabrique un avant, et qu'aucune fusion n'attend - est
un geste de méthode qu'aucun dispositif ne peut observer. Le niveau est donc `humaine`, ce que le
champ `verified:` disait déjà en nommant le porteur du produit, et c'est ce que l'[ADR 5414](5414-une-regle-que-rien-ne-peut-garder-se-declare.md)
prescrit : une règle que rien ne peut garder se déclare. Mesuré et corrigé par #5448.

Elle prolonge l'[ADR 4111](4111-un-clip-montre-la-version-qu-on-valide.md), qui exige qu'un
clip dise la version qu'il montre. Un avant fabriqué n'échappe pas à la règle : il montre un état qui
n'a jamais été publié, et il doit donc dire duquel il part.

## Ce que cette décision a coûté d'être prise tard

Quelques heures seulement, l'erreur ayant été vue le jour même de l'ouverture. Ce qui mérite d'être
retenu n'est pas le délai mais la **forme** de l'erreur : une contrainte inventée par un raisonnement
correct sur une prémisse fausse - « le passé n'est plus accessible » - et qui allait immobiliser huit
correctifs attendus par un utilisateur réel.
