---
type: adr
title: "Un verdict ne juge que ce qu'on lui a demandé"
status: stable
article: A3
chantier: "#3458 (versant IHM du besoin 1)"
decided_at: 2026-08-15
verification: certaine
enforced_by:
  - "SiteEditRechercheCarreTest#un_verdict_arrive_en_retard_est_ecarte"
verified:
  - by: machine:ci
    at: 2026-08-15
---

# Un verdict ne juge que ce qu'on lui a demandé

## Contexte

La modale de déclaration d'un site sait demander à Vigie-Chiro si un carré y existe déjà. La réponse
s'affiche sous le champ, en encart coloré. Elle est **manuelle** (un bouton) et **lente à l'échelle
d'une frappe** : l'appel part hors du fil JavaFX, et l'utilisateur garde la main pendant ce temps.

Entre la question et la réponse, **le numéro peut changer**. Deux fois, dans les deux sens :

| Ordre | Ce qui se passe | Ce qu'on voyait |
|---|---|---|
| la saisie bouge **après** la réponse | vérifier `640380`, lire « ce carré n'existe pas encore », corriger en `640381` | le message vert reste, sous un carré que personne n'a vérifié |
| la réponse arrive **après** la saisie | vérifier `640380`, corriger pendant l'appel, la réponse revient | le verdict de l'ancien numéro s'affiche sous le nouveau |

Les deux produisent la même chose à l'écran : **un jugement affiché sous un carré qu'il ne juge pas**.
Et c'est précisément la panne que ce chantier existe pour fermer - un utilisateur avait déclaré un carré
déjà pris, faute de pouvoir vérifier ; le pire résultat possible est de le lui faire faire **en croyant
avoir vérifié**, preuve visuelle à l'appui.

Le jumeau `PointEditViewModel` n'a pas ce problème, et c'est ce qui a caché celui-ci : son contrôle du
carré STOC est **automatique** et se relance à chaque frappe, si bien que sa réponse se remplace
d'elle-même.

## Décision

**Un verdict ne s'affiche que si le numéro qu'il a jugé est encore celui qui est à l'écran.** Deux
mécanismes, un par sens, parce que ce sont deux questions différentes :

1. **le numéro change** → le verdict est **effacé**. Un écouteur sur la propriété repose
   `RetourOperation.AUCUN` : ce qui était affiché ne concerne plus rien ;
2. **la réponse revient** → elle porte **le numéro qui l'a demandée** (`ResultatRechercheCarre`), et
   l'application la **jette** s'il ne correspond plus. Le contrôleur lit ce numéro sur le fil JavaFX au
   moment du clic, pour que la branche d'échec sache elle aussi de quel carré elle parle.

Corollaire, qui vaut indépendamment de la course : **une absence de réponse n'est pas une réponse.**
Hors connexion, plateforme injoignable ou refus rendent un verdict `Indisponible` qui **parle** - « ce
carré n'a donc PAS été vérifié » - là où le contrôle STOC, lui, se tait. La différence tient à qui a
demandé : un contrôle automatique peut se taire sans mentir, un geste que l'utilisateur a **cliqué** ne
le peut pas.

## Conséquences

- le verdict n'est **jamais** une condition de l'enregistrement : la déclaration reste entière hors
  ligne, et la vérification n'est offerte que si la feature est installée (`OptionalBinder` vide dans les
  injecteurs partiels, cf. [ADR 0003](0003-feature-plugin-desactivable-ports-optionnels.md)) ;
- l'effacement au changement de numéro rend **inutile** toute remise à zéro écrite à la main ailleurs :
  `preparerCreation` vide le numéro, donc l'écouteur fait le travail. PIT a signalé le doublon en le
  supprimant sans faire rougir personne ;
- ce que cette ADR ne dit pas : quand **relancer** la recherche. Le geste reste manuel, et rien ne
  reposera la question à la place de l'utilisateur. Automatiser la relance ferait partir une requête
  par frappe, ce qu'aucune mesure ne justifie aujourd'hui.

## Ce qui reste vrai ailleurs

La règle porte sur **tout jugement asynchrone rendu sur une saisie modifiable**, pas sur ce seul écran.
Le contrôle du carré STOC (`PointEditViewModel#appliquerControleCarre`) applique lui aussi une réponse
sans vérifier qu'elle correspond aux coordonnées courantes ; il s'en tire parce que la relance est
automatique et que le dernier arrivé écrase, mais deux réponses qui se croisent y produiraient le même
défaut. Rien ne l'a mesuré : c'est une **hypothèse**, notée ici pour qu'elle soit vérifiée le jour où
cet écran bouge, et non un défaut confirmé.
