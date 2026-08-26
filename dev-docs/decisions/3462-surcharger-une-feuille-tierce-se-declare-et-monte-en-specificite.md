---
type: adr
title: "Surcharger une feuille de style tierce se déclare, et monte en spécificité"
status: stable
article: A20
chantier: "#3462, finitions de recette avant la campagne 2 (#3424)"
decided_at: 2026-08-18
verification: certaine
enforced_by:
  - "ContrasteVueAudioTest#les_commandes_se_detachent_de_la_barre"
verified:
  - by: machine:ci
    at: 2026-08-18
relations:
  prolonge: ["0046"]
---

# Surcharger une feuille de style tierce se déclare, et monte en spécificité

## Contexte

Un utilisateur n'a lancé aucune écoute de toute une séance de vérification : *« je n'avais pas vu le
bouton lecture du fait de la sobriété de l'écran (bouton noir sur fond noir) »*.

La mesure a déplacé le défaut. Le **texte** du bouton est à **10,68:1**, très au-dessus des 4,5:1 que
WCAG 1.4.3 demande : il était parfaitement lisible. C'est la **surface** du bouton qui était à
**1,17:1** du fond de la barre, là où WCAG 1.4.11 demande 3:1 pour un composant d'interface. Le libellé
se lisait ; le bouton ne se **voyait** pas.

Ces boutons ne sont pas à nous : ils vivent dans `fr.nedjar.vigiechiro:audio-view`, dont la feuille
documente elle-même le point d'extension - *« surcharger ces classes dans une feuille appliquée
après »*. Deux écrans montent ce composant, la qualification et Sons & validation.

## Décision

**Une surcharge de feuille tierce se pose dans le socle, monte d'un cran en spécificité, et le dit.**

Concrètement, `design.css` porte `.audio-view .audio-view-toolbar .button` - **trois** classes - là où
la bibliothèque écrit `.audio-view-toolbar .button`.

### Pourquoi trois classes et non deux

`AudioView.fxml` déclare `stylesheets="@audio-view.css"` **sur sa propre racine**. Une feuille déclarée
sur un nœud profond est appliquée **après** celles des écrans qui le contiennent : à spécificité égale,
c'est donc elle qui gagne. Recopier son sélecteur aurait produit une règle **juste dans le fichier et
sans effet à l'écran**.

### Pourquoi un contour et non une surface plus claire

Calculé avant de choisir : même éclaircie **×3,8**, la surface plafonne à **2,35:1** et vire déjà au
gris-bleu clair, ce qui dénaturerait une vue sombre à dessein - un spectrogramme se lit sur du noir.
WCAG 1.4.11 accepte explicitement les deux leviers, surface **ou** contour.

`#6b737b` rend **3,77:1** sur la barre sombre et **3,76:1** sur la barre claire du thème `:light`. Une
seule déclaration sert donc les deux thèmes, et elle y survit : la bibliothèque ne pose de bordure dans
aucun des deux, sa règle `:light` plus spécifique ne réécrivant que le fond et le texte.

## Ce que cette décision dépasse, et qui ne doit pas rester tacite

L'[ADR 0046](0046-une-classe-css-a-une-seule-feuille.md) pose qu'**une classe CSS se définit dans une
seule feuille**, parce que *« la cascade CSS ne dit jamais laquelle gagne de façon évidente, et le
résultat dérive en silence »*.

Ici, `.audio-view-toolbar .button` vit dans **deux** feuilles : celle de la bibliothèque et la nôtre.
C'est exactement la forme que 0046 refuse.

Le dépassement est **délibéré** : 0046 arbitre entre deux feuilles **que nous écrivons**, et son remède
- désambiguïser les noms - n'existe pas ici, le nom appartenant à la bibliothèque. Le seul autre chemin
serait de corriger `audio-view` en amont, ce qui n'aurait rien livré à l'utilisateur avant une
publication.

**Et son garde ne peut pas le voir.** `DoublonsFeuillesDeStyleTest` balaie
`src/main/java/fr/univ_amu/iut` : la feuille tierce vit dans un jar, hors de son champ. Le test est donc
resté vert, et sans cette ADR le dépassement n'aurait laissé **aucune trace**.

## Conséquences

- **Le contraste est mesuré sur le RENDU, pas sur la feuille.** `ContrasteVueAudioTest` lit les couleurs
  que le moteur CSS a effectivement appliquées aux nœuds. C'est ce que `ContrasteAATest` nomme
  lui-même comme son trou : *« seule une mesure sur rendu le donnerait »*. Un test qui lirait le CSS
  aurait rendu un **vert faux** sur une surcharge n'atteignant pas sa cible.
- **Une montée de version d'`audio-view` peut déplacer la cible sans que rien ne le dise.** C'est le
  risque que 0046 décrivait, reporté à la frontière du jar. La mesure sur rendu est la seule chose qui
  l'attrape : elle rougit si le contraste retombe sous 3:1, quelle qu'en soit la cause.
- **Le défaut touchait deux écrans**, pas un. La règle vit donc dans `design.css` et non dans
  `qualification.css`.

## Alternatives écartées

- **Corriger `audio-view` en amont** : juste sur le fond, et le défaut touche tous ses consommateurs.
  Mais cela n'aurait rien livré avant une publication de la bibliothèque, alors que le correctif
  applicatif tient en trois lignes. À faire en plus, pas à la place.
- **Recopier le sélecteur à deux classes** : mesuré sans effet, la feuille tierce gagnant à spécificité
  égale.
- **Éclaircir le texte** : le remède qu'on aurait pris sans mesurer, et qui n'aurait rien réparé - le
  texte était déjà à 10,68:1.
