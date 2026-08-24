---
type: adr
title: "Une restauration de filtres **rend compte**, et distingue une valeur disparue d'un critère absent"
status: stable
article: A12
chantier: "#3093, palier 1 du chantier #3092"
decided_at: 2026-08-04
verification: certaine
enforced_by:
  - "RetourOperationTest#les_deux_causes_ne_se_melangent_pas"
verified:
  - by: machine:ci
    at: 2026-08-04
---

# Une restauration de filtres **rend compte**, et distingue une valeur disparue d'un critère absent

## Contexte

Trois chemins remettent des filtres en place sans que l'utilisateur les repose :

| Chemin | Déclencheur |
|---|---|
| Vue mémorisée | on clique un onglet de vue enregistrée |
| Transport d'un écran à l'autre (#476) | « Voir sur la carte » emporte les filtres courants |
| Mémoire de session (#484) | on rouvre l'écran, il retrouve son état |

Chacun peut échouer **partiellement**, pour deux raisons de natures différentes :

- une **valeur** cochée n'existe plus dans les données (le carré « 640380 » a disparu de l'inventaire) ;
- un **critère** entier n'existe pas sur l'écran d'arrivée (le transport audio → analyse emporte dix
  critères vers un catalogue qui en offre cinq : six sont abandonnés).

Avant ce chantier, `GestionnaireFiltres.restaurer` rendait la première liste et **jetait la seconde**
(un `ifPresent` sans branche « sinon »), et sur les trois chemins **un seul** lisait ce retour. Le
résultat, dans les deux autres cas : l'écran filtre **moins large** qu'annoncé, et rien ne le dit.

C'est le mode de panne qui a donné son titre au chantier : *un filtre ne s'élargit jamais en silence*.

## Décision

`GestionnaireFiltres.restaurer` rend un `ResteDeRestauration(valeursPerdues, criteresInconnus)`, et
**les trois chemins le lisent**.

Les deux causes restent **séparées jusqu'au message**, chacune avec sa phrase :

- une valeur disparue : « la vue « Mes carrés » a été rejouée sans 640380 » ;
- un critère absent : « ... ni le critère « Heure », que cet écran n'offre pas ».

Un critère absent est **nommé en français** (`LibellesCriteres`), jamais par sa clé de sérialisation.

## Pourquoi

**Les deux causes n'appellent pas la même réaction.** Une valeur disparue tient aux **données** : elle
peut revenir, et l'utilisateur n'a rien à faire. Un critère absent tient à l'**écran** : il ne
reviendra pas ici, et la seule issue est de retourner d'où l'on vient. Les fondre dans une phrase unique
(« 3 filtres n'ont pas été repris ») laisserait l'utilisateur sans le seul renseignement qui oriente son
geste suivant.

**Un chemin muet est pire que pas de mémoire du tout.** Quand personne n'a rien demandé - le cas de la
mémoire de session - l'écran s'ouvre avec des filtres partiels que l'utilisateur croit être ceux qu'il
avait laissés. Il lit une liste en pensant qu'elle est restreinte alors qu'elle l'est moins, ce qui est
exactement la manière de rater une observation.

**Deux causes séparées coûtent un record, pas une architecture.** L'alternative - une `List<String>` où
l'on préfixe les critères - marche le jour où on l'écrit et se mélange au premier ajout.

## Conséquences

- Un écran branché sur le socle gagne son **bandeau de retour en même temps que sa barre**, jamais
  après : sans lui, la mémoire de session remet des filtres amputés sans le dire. C'est ce qui a manqué
  à « Audit de cohérence » lors de son branchement (#3100), et le manque ne se voyait pas, puisque
  l'écran fonctionnait.
- Ajouter un **quatrième chemin** de restauration oblige à lire ce retour. Aucun garde automatique ne
  l'impose : c'est une revue à faire, et cette ADR en est le rappel.
- `LibellesCriteres` doit couvrir **toutes** les clés, y compris celles qu'aucun écran local n'offre :
  c'est précisément celles-là qu'un compte rendu doit nommer.

## Alternatives écartées

**Ne rien dire et recharger la vue complète.** Écarté : la vue est un choix de l'utilisateur, la
remplacer sans le dire est le même défaut sous une autre forme.

**Refuser de rejouer une vue incomplète.** Écarté : une vue à moitié applicable reste utile, et
l'interdire ferait perdre le travail de mémorisation pour un carré disparu.
