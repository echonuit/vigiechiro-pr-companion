# ADR 3788 - Un banc qui maximise tout ne montre pas ce qu'on livre

- **Statut** : Accepté - 2026-08-16
- **Chantier** : #3788, trouvé pendant la tranche (c) de l'EPIC #3667
- **Vérification** : certaine - `.github/scripts/lance-test-filme.sh`

## Contexte

Le banc de recette filmée employait `matchbox-window-manager`, choisi parce qu'il est minuscule et
démarre sans configuration.

⚠️ **Il maximise tout ce qu'il affiche.** C'est son parti pris - il est fait pour de petits écrans -
et il a fait mentir **deux** dispositifs pendant des mois, chacun d'une façon qui ne ressemblait pas
à la cause.

**Premier mensonge.** Les tests de croissance de fenêtre (`Modales.suivreLaCroissance`, #1534) ne
**pouvaient pas** passer en fenêtré : une fenêtre déjà maximisée ne grandit pas, donc
`assertThat(fenetre.getHeight()).isGreaterThan(avant)` était faux quoi que fasse le code. On a
d'abord soupçonné le produit, et le vert headless d'être un faux vert. C'était l'inverse.

**Second mensonge, le grave.** Les clips par cas (#3774) montraient la modale de connexion sur
1280 × 900, contenu tassé en haut et grand vide en dessous. Or ces clips servent à faire **juger**
des cas perceptifs par un humain : sur une mise en page qui n'est pas celle qu'on livre, **qui juge,
juge autre chose**.

Aucun compteur ne le disait. Le montage était juste, la couverture satisfaite, la CI verte. Le défaut
n'a été vu qu'en **extrayant une image** d'un clip et en la regardant.

## Décision

**1. Le banc emploie `openbox`**, qui honore les dimensions demandées par la fenêtre.

Preuve directe : `ConnexionModaleViewTest` rend **10 tests / 0 échec** sous openbox, contre 2 échecs
sous matchbox, **sans qu'une ligne de l'application ait changé**.

**2. Corollaire imposé par le calcul : le seuil de luminance descend de 50 à 20.**

Les 216-226 sur lesquels il était calibré étaient ceux d'une fenêtre **plein écran**. À taille réelle,
la même modale rend 69, et le fond seul 16.

⚠️ 69 passait encore un seuil à 50, mais de justesse, et le calcul dit que ça ne tient pas : une boîte
de dialogue de 400 × 250 sur 1280 × 900 couvre 8,7 % de la surface, donc rend
`0,087 × 243 + 0,913 × 16 ≈ 36`. Elle aurait été déclarée « rien à l'écran », la coupe aurait refusé,
et la couverture se serait effondrée **sur un banc pourtant juste**.

Le seuil se pose donc juste au-dessus du fond : il sépare « aucune fenêtre » de « une fenêtre, si
petite soit-elle », qui est la question réellement posée. Il reste valable pour l'ancien banc.

## Conséquences

- Le vert headless de #1534 ne cachait rien : la capacité est acquise chez l'utilisateur.
- Le banc conditionne l'usage entier du dispositif de #3774. Un clip pris sur un banc non
  représentatif n'est pas une preuve dégradée : c'est une preuve d'autre chose.
- `openbox` n'est pas installé sur les postes de développement du projet : `--auto-test` y rougit sur
  le cas « écran AVEC gestionnaire de fenêtres » (25 sur 26). Le dispositif **signale** que le banc y
  est incomplet, ce qui est le comportement voulu ; la CI, elle, l'installe.

## Alternatives écartées

- **Figer la taille de la modale** (`setWidth`/`setHeight`) pour contourner la maximisation. Cela
  tuerait l'auto-ajustement, c'est-à-dire précisément ce que `S1-26` et `S1-27` donnent à juger.
- **Garder matchbox et corriger les tests**. Ils avaient raison ; c'est le banc qui mentait.
- **Un bureau complet** (GNOME, KDE) pour être « plus représentatif ». Coût d'installation sans
  commune mesure, pour une propriété - honorer les dimensions demandées - qu'`openbox` donne déjà.
