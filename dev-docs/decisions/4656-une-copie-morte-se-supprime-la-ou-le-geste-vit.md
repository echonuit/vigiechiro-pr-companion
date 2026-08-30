---
type: adr
title: "Devant une copie morte, on cherche où le geste vit, on ne factorise pas"
status: stable
article: A1
chantier: "#4656 (clôture, passe 11)"
decided_at: 2026-08-28
verification: humaine
verification_note: "la règle est une heuristique de lecture : elle dit quelle question poser devant un signalement, ce qu'aucun garde ne peut vérifier à la place de qui lit"
verified:
  - by: human:nedseb
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
relations:
  complète: ["4617"]
---

# Devant une copie morte, on cherche où le geste vit, on ne factorise pas

## Contexte

L'[ADR 4617](4617-le-portail-voit-les-tests-et-le-code-mort.md) a rendu le code mort visible. Le
chantier #4656 en a retiré vingt-trois occurrences, et la même question s'est posée à chaque
famille : ces copies disent-elles qu'un helper manque ?

**Sept fois, la réponse a été non**, et pour la même raison.

| Famille | Où le geste vivait |
|---|---|
| six `executer` | remplacés par des `PreparedStatement` à valeurs liées |
| trois marches de `ligne(...)` | seule la signature complète était appelée |
| deux `vide` d'empreinte identique | plus aucun appelant des deux côtés |
| `minutesDepuis18h` | déplacé vers `CourbesActivite`, ce que l'ADR 3840 raconte |
| `ajouterEcarts` | réécrit dans `CompteRenduReactivation` |
| `ajouter` et son ancre CSS | remplacés par `Habillage.poser` |
| `ligne` de `GenerateurCartesSD` | le même format, au caractère près, dans `JournalDeCapteur` |

Aucune n'était une duplication en attente d'extraction. Toutes étaient le résidu d'un geste
**remplacé par une meilleure forme**, dont la copie a survécu à la disparition de son usage.

## Décision

**Devant une méthode morte, la question n'est pas « qui l'appelait ? » mais « où vit le même geste
aujourd'hui ? ».**

Les deux questions ne mènent pas au même endroit. La première invite à rebrancher ou à factoriser ;
la seconde fait découvrir que le travail est déjà fait ailleurs, mieux.

**Factoriser une copie morte ressusciterait le motif qu'on a quitté.** Le cas des six `executer` le
montre : en extraire un helper partagé aurait redonné un nom propre à une requête concaténée sur un
`Statement` brut, alors que le dépôt est passé aux `PreparedStatement` paramétrés. Le remède
paraissait vertueux et allait contre le sens du code.

**Le nom ne suffit pas à trouver le jumeau.** Un dénombrement par nom rendait 69 homonymes pour
`ligne` ; c'est la recherche du **format rendu** qui a désigné le vivant. Le graphe du dépôt a
désigné deux autres jumeaux qu'un `grep` aurait laissés indistincts.

## Conséquences

**Ce qu'on gagne.** Une suppression défendable : on ne retire pas parce qu'un compteur le dit, mais
parce qu'on a trouvé où la chose se fait désormais.

**Ce qu'on perd.** Du temps par site. Vingt-trois lectures, dont plusieurs interrogations du graphe.
C'est le prix pour ne pas effacer le symptôme d'un branchement oublié.

**Ce qui reste à la charge du lecteur.** Aucun garde ne peut poser cette question à sa place. La
vérification de cette ADR est donc **humaine**, et le restera.
