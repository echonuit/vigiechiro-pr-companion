---
type: adr
title: "Une cible cliquable tient vingt-quatre pixels"
status: stable
article: A23
heuristiques: ["wcag-cible"]
chantier: "#4462, lot de #5006"
decided_at: 2026-10-07
verification: certaine
enforced_by:
  - "scripts/adr/verifie_taille_des_cibles.py"
verified:
  - by: machine:ci
    at: 2026-10-07
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-07
---

# Une cible cliquable tient vingt-quatre pixels

## Contexte

WCAG 2.5.8, niveau AA, demande qu'une cible cliquable fasse au moins 24 × 24 px. Une cible trop
petite se rate, et le coût ne tombe pas sur tout le monde de la même façon : il tombe sur qui vise
moins bien, sur un écran tactile, ou avec une souris tenue d'une main qui tremble.

Le garde qui tient ce seuil est arrivé ici le 2026-08-25 par #4474, porté du dépôt de référence. La
décision qui le fonde y était née dans le même commit que lui, et elle n'a pas suivi : la demande
disait que le garde n'avait pas d'ADR à porter. Pendant six semaines, son contrat a donc déclaré
« sans ADR », et la matrice d'ergonomie a rangé `wcag-cible` parmi les heuristiques que rien ne
sert, alors qu'un garde bloquant la tenait à chaque demande. Cette page porte la décision, avec les
mesures de ce dépôt.

## Décision

**Une cible cliquable ne se déclare pas sous 24 px.** Le seuil vaut pour la hauteur comme pour la
largeur, et pour les six types de nœud qu'on clique : `Button`, `ToggleButton`, `CheckBox`,
`MenuButton`, `RadioButton` et `Hyperlink`.

`scripts/adr/verifie_taille_des_cibles.py` le refuse, sans marge et sans exemption. Il porte sur le
**type de nœud**, pas sur une liste de noms : c'est ce qui lui permet de laisser passer un
séparateur de 1 px sans avoir à le connaître.

## Ce que le garde lit, mesuré

Il lit, dans chaque FXML de `src/main/java`, quatre attributs de la balise ouvrante d'un nœud
cliquable : `prefHeight`, `minHeight`, `prefWidth`, `minWidth`. Il ne calcule rien.

Mesuré le 2026-10-07 sur `0e060d5bbb` : 30 vues, 147 nœuds cliquables, dont 124 `Button`. **Trois**
portent une dimension chiffrée, les trois `prefWidth="150.0"` de la qualification. Vingt-deux autres
déclarent `minWidth="-Infinity"`, qui n'est pas un nombre et que le garde ne lit pas. Aucune cible
n'est sous le seuil.

Hors des nœuds cliquables, trois dimensions passent sous 24 px : une `Region` de 12 px et un
`ProgressIndicator` de 18 × 18. Aucune ne se clique, et le garde les laisse passer par construction.

Le refus a été vu : un `prefHeight="14.0"` posé sur le bouton « OK » de la qualification fait
sortir le garde en 1, et il nomme le fichier, le contrôle et la valeur. Retiré, il sort en 0.

## Ce que le garde ne voit pas

Un dispositif dit sa portée (article A3), et celle-ci est étroite.

- **Les 144 cibles qui ne déclarent aucune taille chiffrée.** Leur taille vient de leur police et de
  leur marge interne. Le dépôt de référence l'évaluait à 34 px environ ; cette valeur n'a pas été
  remesurée ici.
- **Une hauteur posée par une classe CSS.** Rien dans une feuille ne distingue une classe qui
  habille un bouton d'une classe qui habille une jauge.
- **Une taille posée en Java**, par `setPrefHeight` ou `setMinSize`, et tout contrôle construit hors
  d'un FXML.
- **`maxHeight` et `maxWidth`**, qui peuvent pourtant écraser une cible.
- **Les autres nœuds qu'on clique** : `ComboBox`, `ChoiceBox`, `Slider`, un onglet, une cellule.
- **Une valeur qui n'est pas un littéral**, ou une valeur nulle.

Le garde ne rend pas non plus le compte de ce qu'il a lu. Il attrape une valeur écrite à la main
pour faire tenir une rangée, et rien d'autre : il ne prouve pas que l'écran rendu tient le critère.

## Conséquences

- **`wcag-cible` a sa première décision.** La matrice d'ergonomie compte 13 heuristiques sur 23 que
  rien ne sert, au lieu de 14, et quatre des cinq clés WCAG restent sans décision.
- **Le contrat du garde nomme cette ADR**, au lieu de dire qu'il n'en a pas.
- **Un `prefHeight="16"` posé sur un bouton fait rougir `methode`** à la demande qui l'apporte, et
  la porte locale avant elle.

## Alternatives écartées

- **Mesurer sur le rendu.** Plus juste, et beaucoup plus lent : il faudrait monter chaque vue et
  chacun de ses états. C'est pourtant le seul dispositif qui verrait les 144 cibles hors de portée,
  et cette page ne le ferme pas.
- **Lire aussi les hauteurs CSS.** Écarté dans le dépôt de référence par sa mesure du 2026-08-23 :
  huit dimensions sous le seuil, toutes sur des éléments de présentation.
- **Laisser le garde sans décision**, au titre de l'hygiène. Le seuil n'est pas une hygiène : il
  vient d'un critère d'accessibilité que la page des heuristiques nomme, et un garde qui en tient un
  sans le dire fait mentir la matrice.
