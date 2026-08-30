---
type: adr
title: "Un motif ne se déclare qu'après son contrôle inverse"
status: stable
article: A17
chantier: "#4841, lot 1 de l'EPIC #4804"
decided_at: 2026-08-30
verification: humaine
verification_note: "un contrôle inverse est une mesure ponctuelle, propre au motif examiné : aucun compteur ne dit s'il a été fait. La règle se tient à la revue, sur la présence des deux taux dans le corps de l'issue ou de la PR"
relations:
  prolonge: ["2213-un-dispositif-rapporte-avant-de-conclure"]
verified:
  - by: human:nedseb
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un motif ne se déclare qu'après son contrôle inverse

## Contexte

Le chantier #4804 cherchait ce qui rend six bancs instables. À chaque étape, un motif apparaissait,
net, avec des chiffres à l'appui. Quatre fois de suite.

| Motif supposé | Ce qui l'appuyait | Ce que le contrôle a rendu |
|---|---|---|
| `Respiration` employée avant une assertion | 16 classes sur 19 au relevé des instables | 84 % contre 75 %, soit **1,4 classe** |
| les parcours de bout en bout | 3 sur les 7 premiers suspects | 24 % contre 17 %, soit **1,4 test** |
| `waitForFxEvents` suivi d'une assertion | 50 bancs l'enchaînent, 4 suspects en sont | 16 % contre 13 %, soit **1,5 banc** |
| un `waitFor` **muet** | 8 des 19 nommés en tête | 42 % contre 10 % : **il tient** |

Les trois premiers avaient l'air aussi solides que le quatrième. Le premier a failli fonder un
sous-chantier entier.

## Ce qui les rendait faux

Un chiffre isolé mesure la population **choisie**, pas le motif. Quand 76 % des classes JavaFX du
dépôt figurent au relevé des instables, n'importe quel sous-ensemble en rend environ 76 %, et l'appui
paraît écrasant sans rien dire.

Le dénominateur manquait, et il ne se devine pas : il se mesure sur le **complément**.

## Décision

**Un motif ne se déclare qu'accompagné de son contrôle inverse**, c'est-à-dire du même taux mesuré sur
la population qui ne le porte pas. Les deux chiffres partent ensemble dans l'issue ou la PR, ou le
motif n'est pas déclaré.

```
                          porteurs   complément
  avec le motif             X %          -
  sans le motif              -          Y %
```

Un écart qui se compte en **unités** (1,4 classe, 1,5 banc) n'est pas un motif. Un facteur quatre en
est un.

## Ce que cette décision empêche

Elle interdit d'ouvrir un chantier sur une corrélation non contrôlée. C'est ce qui a failli arriver
trois fois ici, et la première fois le sous-chantier était écrit dans son cadrage.

Elle interdit aussi de conclure d'un motif qui tient qu'il est une **cause**. Les deux motifs
survivants de ce chantier sont des **marqueurs** : un banc qui a écrit sa propre attente est un banc
qui a *rencontré* le problème, ce qui explique qu'il soit instable sans que la duplication en soit la
source. Un marqueur oriente la lecture ; il ne désigne pas de coupable.

## Ce qu'elle ne demande pas

Aucun outillage. Le contrôle est une seconde mesure sur le complément, souvent la même commande avec
une négation. Ce qui manquait n'était pas un moyen, c'était l'habitude.

## Conséquences

- Deux issues de ce chantier portent leurs deux taux : #4845 (42 % contre 10 %) et #4847 (60 % contre 9 %).
- Trois motifs ont été écartés **par écrit** plutôt que tus, ce qui évite qu'ils se redécouvrent.
- Elle prolonge l'[ADR 2213] : un dispositif qui ne peut pas conclure rapporte ce qu'il a vu. Ici,
  ce qui ne peut pas conclure est une corrélation, et ce qu'elle rapporte est son complément.

[ADR 2213]: https://companion-dev.echonuit.fr/decisions/2213-un-dispositif-rapporte-avant-de-conclure/
