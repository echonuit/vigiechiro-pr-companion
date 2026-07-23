# ADR 0046 — Une classe CSS a une seule feuille pour maison

- **Statut** : Accepté — 2026-07-21
- **Chantier** : #1974
- **Vérification** : probable — `scripts/adr/0046-classe-css-plusieurs-feuilles.py` (cliquet : 5)

## Contexte

Les feuilles de style de l'application sont découpées par feature (`sites.css`, `qualification.css`…)
au-dessus d'un socle partagé (`design.css`, `base.css`, `palette.css`, que toutes les vues chargent).
Rien n'empêchait un même nom de classe de vivre dans deux feuilles à la fois, et ça s'était produit
sous **trois formes**, toutes rencontrées en #1974 :

- **la copie** : `.message-erreur` recopié à l'identique dans cinq feuilles, `.field-label`,
  `.field-hint`, `.menu-actions` en double. Chaque copie surchargeait sans rien changer — jusqu'au
  jour où l'une dérivait sans que l'autre suive ;
- **le code mort** : `.fil-ariane` de la qualification, reliquat d'un breadcrumb déplacé dans le
  chrome, ne ciblant plus aucun élément ;
- **la collision** : `.entete`, deux écrans sous un nom générique avec des paddings différents — et,
  plus grave, `.carte-chevron`, dont la collision avec la classe de l'accueil (`-fx-opacity: 0`,
  révélée au survol) a rendu **invisible** le chevron d'invite des cartes de sites, depuis la création
  de la feature. Personne ne l'avait vu, parce que rien ne le testait.

Le point commun des trois : **un même nom, deux feuilles**. La cascade CSS ne dit jamais laquelle
gagne de façon évidente, et le résultat dérive en silence.

## Décision

**Une classe CSS se définit dans une seule feuille.**

- Concept **transverse** (bouton, encart, badge, message d'état) : dans le socle partagé
  (`design.css` / `base.css`), et les feuilles de feature n'y touchent pas.
- Concept **local** à un écran : dans la seule feuille de cette feature.
- Deux écrans ont vraiment deux concepts sous un même nom ? On les **désambiguïse**
  (`.entete-passage` / `.entete-qualification`), on ne les laisse pas se marcher dessus.

La règle est tenue par un test, [`DoublonsFeuillesDeStyleTest`], qui **refuse tout nom de classe
présent dans deux feuilles**. Il ne distingue pas la forme du défaut (copie, code mort, collision) :
le nom en double est l'invariant, quelle qu'en soit la cause.

**Une seule exception, structurelle : `.root`.** `palette.css` y pose les jetons de couleur, `base.css`
la police et le fond. La séparation est voulue — `palette.css` est chargée **seule** sur les scènes de
capture, sans `base.css`, précisément pour que les jetons se résolvent partout. Toute autre exception
future s'inscrit dans la liste du test **avec sa raison**.

## Conséquences

Le défaut ne peut plus renaître en silence : une copie réintroduite fait rougir le test. La collision
qui a rendu le chevron invisible aurait été attrapée à la naissance.

Ce n'est pas de la déduplication pour elle-même. La duplication de style est un **outil de diagnostic**
ici : quand deux feuilles nomment la même chose, soit c'est une copie qui dérivera, soit c'est une
collision qui masque un bug. Le test transforme ce signal en garde-fou.

Le **coût** est réel sur un cas : consolider `.menu-actions` (identique dans deux feuilles, posé par
six écrans) l'a hissé dans `design.css`, ce qui applique son `font-size` à quatre écrans qui ne
l'avaient pas — un décalage de layout d'environ 1px, imperceptible mais **pas nul**. Aucune résolution
d'une classe partagée par plusieurs écrans n'est à zéro pixel ; la vérifier à la capture n'est pas
optionnel.

## Ce qui a été écarté

**Interdire tout littéral égal à un jeton**, tant qu'à faire de la cohérence. C'est un autre sujet
(la tokenisation, cf. le cliquet de contraste de #322) : `#2c3e50` en littéral a un contraste parfait,
le forcer au jeton est un chantier distinct. Ce test-ci ne vise que le **nom en double**.

**Distinguer copie et collision dans le test.** Tentant — une copie se corrige en supprimant, une
collision en renommant. Mais le test n'a pas à savoir : les deux se soldent par « donne à cette classe
une seule feuille », et un message unique est plus simple à suivre qu'une taxonomie.
