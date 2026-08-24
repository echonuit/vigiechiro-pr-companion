---
type: adr
title: "Ce qui se cherche doit pouvoir se **balayer**, pas seulement s'atteindre"
status: stable
article: A23
chantier: "#3348, suite de la clôture du chantier #3151"
decided_at: 2026-08-06
verification: certaine
enforced_by:
  - "SonsValidationViewTest#le_texte_du_commentaire_a_sa_colonne"
verified:
  - by: machine:ci
    at: 2026-08-06
---

# Ce qui se cherche doit pouvoir se **balayer**, pas seulement s'atteindre

## Contexte

`CriteresAudio.correspond` interroge le **texte du commentaire** d'une observation. La table de Sons &
validation ne lui donnait pas de colonne : seule une colonne-indicateur de 50 px, sans libellé, portait
une icône 💬 disant qu'un commentaire *existe*.

C'est le **jumeau** du défaut que le chantier #3151 a corrigé quatre fois, sur une dimension qui n'a
rien de géographique.

## Ce que l'issue disait de trop

Elle affirmait qu'« il faut ouvrir chaque popup pour le savoir ». **C'est faux** : la cellule
indicateur installe déjà une infobulle portant le commentaire **entier**
(`CellulesAudio#commentaire`), et non un extrait. La piste 2 de l'issue - « survoler 💬 montre le début
du commentaire » - était donc déjà livrée, et mieux que proposée.

Le manque réel est plus étroit, et c'est celui qui compte : sur une liste **filtrée** de cinquante
lignes, une infobulle se lit **ligne à ligne**. Elle rend le contenu *atteignable*, jamais
*balayable* : rien ne permet de voir d'un coup d'œil laquelle a répondu, ni pourquoi.

## Décision

Une colonne « Commentaire (texte) », **masquée par défaut**, posée contre son indicateur - le texte et
l'icône qui en signale l'existence se lisent au même endroit. Le sélecteur la rend disponible ; la
table la plus dense du produit ne la subit pas.

C'est le patron déjà retenu par #3300 pour « Nom du carré », et il est **explicitement prévu** par
l'[ADR 3151](3151-un-ecran-n-offre-pas-ce-qu-il-ne-montre-pas.md) : une table pleine garde sa colonne
masquée au sélecteur. La cellule est celle des autres colonnes longues (`CellulesAudio#avecInfobulle`)
: elle élide et expose la valeur complète au survol, donc réduire la colonne ne perd rien.

## Conséquences

- la règle du chantier se formule un cran plus finement : **ce qu'un écran offre de chercher, il doit
  pouvoir le montrer *de façon balayable*.** Une infobulle satisfait « montrer » au sens strict et
  manque l'usage - après une recherche, on trie du regard, on ne survole pas cinquante lignes ;
- les deux colonnes du commentaire coexistent sans redondance : l'indicateur dit **qu'il existe** et
  reste visible, la colonne dit **ce qu'il contient** et se demande ;
- l'audit de la passe 7 avait confronté **18 catalogues et filtres** aux colonnes de leur écran.
  C'était le seul cas ; il est fermé.

## Alternatives écartées

- **Une colonne visible par défaut** : un texte long coûte cher sur la table la plus dense du produit,
  et une colonne étroite le tronquerait sans rien apprendre ;
- **Ne rien faire, l'infobulle suffit** : elle donne le contenu, pas la vue d'ensemble. C'est
  précisément l'écart entre *atteindre* et *balayer* que cette ADR nomme ;
- **N'accepter la recherche du commentaire que le panneau de discussion ouvert** : conditionner un
  critère à l'état d'un panneau rend son comportement imprévisible, et la recherche libre n'a nulle
  part ailleurs ce genre de garde.
