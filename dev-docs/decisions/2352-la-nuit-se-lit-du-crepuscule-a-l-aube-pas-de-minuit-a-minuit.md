---
type: adr
title: "La nuit se lit du crépuscule à l'aube, pas de minuit à minuit"
status: stable
article: A21
chantier: "#2352 (lot 2 de l'EPIC #2348)"
decided_at: 2026-07-26
verification: certaine
enforced_by:
  - "AgregationActiviteTest#l_export_date_ses_lignes_par_la_nuit_biologique"
verified:
  - by: machine:ci
    at: 2026-07-26
---

# La nuit se lit du crépuscule à l'aube, pas de minuit à minuit

## Contexte

Une nuit d'enregistrement chiroptère commence après le coucher du soleil et finit avant l'aube : elle est **à cheval sur deux dates civiles**. Le calendrier, lui, bascule à minuit, au milieu de l'objet qu'on observe.

Cette collision n'est pas théorique. Découper l'activité par date civile coupe chaque nuit en deux moitiés qui atterrissent dans deux cases différentes : le pic de 23 h 40 dans la case du 21, la retombée de 00 h 20 dans celle du 22. Un axe horaire 0 h → 24 h produit alors une courbe en deux morceaux, l'un collé à droite, l'autre à gauche, entre lesquels l'œil doit reconstruire la continuité. La forme de la nuit (montée, pic, décroissance) devient illisible, alors que c'est précisément elle qui porte l'information que le comptage total efface.

Le même écueil guette hors de l'écran : un export qui daterait ses tranches par leur date civile mêlerait deux nuits dans un même regroupement de tableur.

## Décision

**La nuit biologique est l'unité de lecture, et elle bascule à midi.** Un instant appartient à la nuit du soir précédent tant qu'il est avant 12 h ([`Nuit#de`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/Nuit.java)). Une capture à 02 h 00 le 22 juin appartient à la nuit du 21.

Trois conséquences de cette règle, appliquées partout où l'activité se lit :

1. **L'axe de la courbe court de 18 h à 8 h**, pas de 0 h à 24 h. L'abscisse est le nombre de minutes écoulées depuis 18 h ; l'heure du jour est **reconstruite** en étiquette de graduation. La nuit se lit d'un trait, de gauche à droite.
2. **L'axe est fixe**, non calé sur les données : deux nuits se comparent alors d'un coup d'œil, à la même échelle. Une nuit courte occupe le milieu du cadre au lieu d'être étirée sur toute sa largeur.
3. **La fenêtre réelle coucher → lever** est matérialisée par un aplat pâle, quand elle est calculable au point d'écoute. Elle situe l'activité dans la nuit **astronomique**, qui varie de plusieurs heures entre juin et octobre, là où l'axe 18 h → 8 h est un cadre fixe. Ce qui déborde de l'aplat est de l'activité crépusculaire ou diurne : un signal de dispositif (capteur mal réglé, horloge décalée) autant qu'écologique.

**La même règle gouverne l'export CSV** (`exporter-activite`) : chaque ligne porte la nuit biologique recalculée, jamais la date civile de la tranche.

## Conséquences

- Le rattachement d'un contact à sa nuit est un calcul **du domaine**, pas une mise en forme d'IHM : il vit dans `commun/model/Nuit` et sert la vue, l'agrégation et l'export sans être réécrit.
- L'axe fixe assume un **cadre plus large que la nuit** : en été, les deux extrémités restent vides. C'est le prix de la comparabilité, et l'aplat rend cette marge lisible plutôt qu'inquiétante.
- Une nuit **sans coordonnées** (pas de GPS au point) n'a pas de fenêtre calculable : l'aplat est alors **absent**, et non approximé par un défaut fixe. Une fenêtre inventée serait lue comme une mesure.
- La vue **transverse** (tous les passages) empile plusieurs nuits sur le même cadre : aucune fenêtre unique n'y a de sens, l'aplat s'y efface.

## Alternatives écartées

- **Un axe 0 h → 24 h.** Le plus simple à écrire, et celui qui coupe la nuit en deux. Écarté pour la raison qui a motivé cette ADR.
- **Un axe calé sur les données** (min/max des contacts). Chaque nuit occuperait toute la largeur, au prix de la comparabilité : deux nuits de durées différentes auraient la même longueur à l'écran, ce qui est exactement l'illusion qu'on veut éviter.
- **Un axe borné par le coucher et le lever réels.** Séduisant, mais l'échelle changerait à chaque nuit (même problème que ci-dessus) et une nuit sans GPS n'aurait plus d'axe du tout.
- **Rattacher la nuit par « date de la première séquence ».** Marche tant qu'un enregistrement commence avant minuit ; une session démarrée à 00 h 15 basculerait la nuit entière d'un jour. La bascule à midi ne dépend pas de l'heure de démarrage.
