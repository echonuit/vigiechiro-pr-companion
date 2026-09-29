---
type: adr
title: "Une mesure déclare la composition de ce qu'elle résume, et n'en fait pas partie"
status: stable
article: A3
chantier: "#5562 (deux instruments mesurent le coût de la CI, et à eux deux ils couvrent un workflow sur vingt)"
decided_at: 2026-09-29
verification: certaine
enforced_by:
  - ".github/scripts/mesure_duree_portail.py"
  - ".github/scripts/mesure_minutes_par_pr.py"
verified:
  - by: machine:ci
    at: 2026-09-29
relations:
  complete: ["5015-un-garde-sans-unite-a-compter-le-declare-chez-lui", "4287-un-ecart-se-lit-contre-le-plancher-de-son-cas"]
generated:
  by: "process:assistance-par-agents"
---

# Une mesure déclare la composition de ce qu'elle résume, et n'en fait pas partie

## Le contexte

L'ADR 5015 a tranché qu'un garde déclare `lus`, le compte de ce qu'il a lu. Elle répond à « sur
combien ? ». Ce chantier a rencontré trois fois la question d'après, à laquelle un compte seul ne
répond pas : **de quoi cette population est-elle faite, et qu'en manque-t-il ?**

## La mesure qui décide

Le coût d'une demande est bimodal depuis le régime des portées. Une médiane unique suit donc la
composition de la fenêtre, pas le régime :

| fenêtre | part sans Java | médiane globale |
|---|---|---|
| 40 dernières au 2026-09-07 | 82 % | 36,0 min |
| 2026-09-08 au 2026-09-22 | 39 % | 62,5 min |

Le régime n'avait pas bougé. Le chiffre publié, oui, de 74 %.

Deux autres faits du même chantier. `commits/<sha>/pulls` rend **vide** pour un commit rebasé : une
liste vide ne porte pas de Java, donc la demande se classait « sans Java », plausible et faux. Et
`mesure_duree_portail.py` mesurait `maven.yml` **depuis `maven.yml`** : la durée du pas entrait dans
la fenêtre que le pas calcule.

## La décision

**Une mesure qui résume une population déclare comment cette population est composée, nomme ce
qu'elle n'a pas pu lire, et ne figure pas dans ce qu'elle résume.** Trois obligations.

**L'effectif accompagne la médiane.** Sans lui, « 72,0 min » se lit comme un régime alors qu'une
seule demande le porte. Le rendu partitionne donc, chaque classe avec son compte.

**Une lecture non faite est une CLASSE, jamais une valeur.** Une forge qui se tait rend
« indéterminée ». Le piège est qu'une absence ressemble à une réponse négative : zéro fichier Java
et aucun fichier lu produisent le même « sans Java » si rien ne les distingue.

**Une mesure n'entre pas dans sa propre fenêtre.** Les deux mesures de durée sont croisées :
`lint.yml` lit `maven.yml`, `maven.yml` lit `lint.yml`.

## Ce que cela ne dit pas

Ce n'est pas l'ADR 4287, qui tranche qu'un **seuil** se lit contre le plancher de son propre cas.
Ici rien n'est un seuil : ces instruments avertissent et ne refusent pas. La parenté est la
population hétérogène, la conséquence est un rendu, pas un butoir.

Ce n'est pas non plus une règle sur l'auto-inclusion en général. Un **classement** qui se compte
lui-même décale d'une unité, une fois, et se constate : mesuré à une sur cinquante sur un banc voisin,
sans changer aucune conclusion. Une **rétroaction** se compose, chaque exécution nourrissant la
suivante. Seule la seconde est visée.

## Le contrôle qui met la règle à l'épreuve

Les deux gages tournent en CI dans `lint.yml`, donc ils peuvent faire rougir une demande.

`mesure_duree_portail.py --auto-test` lit les invocations des ateliers et refuse qu'un atelier
mesure celui où il vit. Il a rougi sur l'état d'avant, où `maven.yml` se mesurait depuis #3508.

`mesure_minutes_par_pr.py --auto-test` porte deux cas qui tiennent les deux autres obligations. Le
premier meurt si l'on revient à `commits/<sha>`, le second si une forge muette rend « sans Java ».
Chacun a été tué par sa mutation, une fois chacun.

## Les alternatives écartées

**Rendre la médiane globale et laisser le lecteur partitionner.** C'est l'état d'avant : le bilan de
#5294 a publié « 82 vers 34,0 min, -59 % » en mesurant le mélange, et personne ne l'a vu.

**Traiter une lecture manquée comme une valeur par défaut.** C'est le choix qui fait qu'une route
officielle et muette produit une classification plausible. Le dépôt a un nom pour cette forme.
