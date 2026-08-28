---
type: adr
title: "Deux précisions de conversion coexistent, parce qu'elles ne servent pas à la même chose"
status: stable
article: A17
chantier: "#4578 (passe 7 de la clôture, chantier #4573)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "ConversionGeographiqueTest#les_deux_precisions_different"
  - "ConversionGeographiqueTest#distance_reproduit_la_mesure_du_serveur"
  - "ConversionGeographiqueTest#demi_cote_dessine_vaut_un_kilometre"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Deux précisions de conversion coexistent, parce qu'elles ne servent pas à la même chose

## Contexte

Trois classes passaient des degrés aux mètres, avec **deux valeurs pour la même grandeur physique**.

| Classe | Valeur | Ce qu'elle en fait |
|---|---|---|
| `EmpriseAutourDesPoints` | 111,0 km par degré | dessine |
| `FournisseurEmpriseCarreOfficiel` | 111,0 km par degré | dessine |
| `CarroyageNational` | 111 132 m par degré | **mesure** |

L'écart vaut 0,12 %, soit **130 m sur une maille de 2 km**. Dans un dessin, il ne se voit pas. Dans une
mesure, il déplace un seuil : le chantier compare des distances à 50 m pour juger deux candidats
indiscernables, et à un rayon pour décider ce qui entre dans la recherche.

Lu de l'extérieur, cela ressemble exactement à une incohérence qu'on a laissée traîner.

## Décision

**Les deux précisions restent, et un seul type les porte** - `ConversionGeographique` - où chaque
constante dit son usage à côté de sa valeur.

`KM_PAR_DEGRE_LAT_DESSIN` vaut 111,0 et sert à dessiner. `METRES_PAR_DEGRE_LAT` vaut 111 132 et sert à
mesurer. Elles ne sont pas interchangeables, et le type existe pour que ce soit lisible.

## Pourquoi ne pas les unifier

C'est le remède qui vient en premier, et les deux sens échouent.

**Sur la valeur ronde** : la mesure dérive de 0,12 %, et le seuil des 50 m se déplace. La règle
« deux carrés à moins de 50 m l'un de l'autre sont indiscernables » cesse de dire ce qu'elle dit.

**Sur la valeur précise** : les emprises se déplacent de 130 m. Cela ne corrige rien de visible - la
maille fait 2 km - et **change toutes les captures d'écran cartographiques**, dont le dépôt vérifie
la fraîcheur. On paierait un travail de mise à jour pour un dessin identique à l'œil.

Une valeur unique coûterait donc quelque chose et ne rendrait rien.

## Pourquoi un type plutôt qu'un commentaire

La passe 7 avait d'abord été tranchée autrement : documenter la divergence dans **une** des trois
classes. C'est la plus faible des solutions possibles, parce qu'elle **n'avertit que le lecteur qui
ouvre celle-là**. Les deux autres continuaient d'afficher 111,0 sans rien dire, et c'est dans l'une
d'elles qu'on aurait unifié.

Une divergence délibérée doit être visible depuis chacun de ses points d'usage, ou elle ne protège rien.

## Comment on saurait qu'elle est rompue

`ConversionGeographiqueTest#les_deux_precisions_different` **exige que les deux valeurs divergent**. Il
rougit si quelqu'un les unifie, dans un sens comme dans l'autre - c'est un test qui n'existe que pour
cette ADR, et il n'a pas d'autre raison d'être.

Les deux autres tiennent chaque précision par son usage : la mesure reproduit les 1 412 m rendus par la
plateforme au coin d'une maille, le dessin rend un demi-côté d'un kilomètre.
