---
type: adr
title: "Le carré d'une position se calcule hors ligne ; la plateforme garde l'autre question"
status: stable
article: A17
chantier: "#4577 (le geste dans l'écran, chantier #4573)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "SiteEditSituerPositionTest#situer_n_interroge_pas_la_plateforme"
  - "ModaleSiteViewTest#situer_ne_depend_pas_de_la_connexion"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Le carré d'une position se calcule hors ligne ; la plateforme garde l'autre question

## Contexte

Deux questions se ressemblent et n'ont pas la même réponse.

| Question | Qui sait répondre |
|---|---|
| Quel carré couvre cette position ? | de la **géométrie** |
| Ce carré existe-t-il en Point Fixe, et est-il à moi ? | le **portail** |

L'issue #733 les a confondues et a interrogé `GET /grille_stoc/cercle`. Le chantier #4573 a refait la
même confusion pendant trois lots, jusqu'à ce qu'une relecture demande : ne dispose-t-on pas déjà de la
base des carrés ?

Si. `carrenat.csv.gz` est embarqué depuis #325 pour dessiner l'emprise d'un carré sur la carte : 137 481
mailles, numéro vers centroïde WGS84, toute la métropole. Il porte donc aussi le sens inverse, le carré
d'une position étant la maille de centroïde le plus proche - la partition par centre le plus proche d'un
réseau de mailles carrées **est** ce réseau.

Mesuré le 2026-08-27, il reproduit la plateforme au centimètre : `40110` à 374,9 m contre un centroïde
à 4 cm, 1 411,7 m au coin contre 1 412 m rendus par le serveur.

## Décision

**Le carré d'une position se calcule sur le référentiel embarqué, sans réseau ni jeton.** Le portail
n'est interrogé que sur l'existence du carré, et c'est le geste « Vérifier sur Vigie-Chiro », qui ne
change pas.

Conséquence visible : le geste qui situe une position **reste ouvert hors connexion**. Il n'est fermé
que faute de position à situer.

Conséquence dans le code : `PropositionCarre` n'est pas `Optional`, là où `RechercheCarreExistant` et
`ControleCarreStoc` le sont. Ce qui a besoin de la plateforme se déclare optionnel ; ce qui n'en a pas
besoin ne le fait pas.

## Pourquoi, et ce que l'inverse coûtait

L'écran de déclaration existe pour qu'on puisse déclarer un carré **chez soi**. Sa documentation le
dit depuis toujours : fermer la saisie ferait de la plateforme une condition pour déclarer chez soi.

Passer la proposition par le portail aurait fermé le geste dans le cas d'usage même qui a ouvert le
chantier : une relève chez soi, un import le lendemain. La décision D7 du changement OpenSpec
`add-carre-par-coord` avait écrit cette fermeture, par analogie avec le bouton d'à côté, et elle était
fausse pour cette raison.

## Ce que la décision ne dit pas

Que la plateforme aurait tort. `GET /grille_stoc/cercle` fait autorité et reste la source si un doute
naît sur la fraîcheur du fichier embarqué. Le carroyage national est un découpage fixe, pas une donnée
vivante.

## Comment on saurait qu'elle est rompue

`SiteEditSituerPositionTest#situer_n_interroge_pas_la_plateforme` compte les invocations du port de
recherche après le geste et exige **zéro**.
`ModaleSiteViewTest#situer_ne_depend_pas_de_la_connexion` exige que le bouton reste ouvert sans jeton.

Les deux rougissent si quelqu'un rebranche la proposition sur le portail.
