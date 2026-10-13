---
type: adr
title: "Le seuil d'ouverture d'un écran se juge à chaud et en rendu GPU, et un relevé de l'IHM dit par quoi il a été dessiné"
status: stable
article: A5
chantier: "#6178 (l'interface tient O3 et O5), lot #6171"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/perf/outils/EchelleDuRenduTest.java"
verification_note: "le test tient la moitié mécanique : le relevé nomme le dessin que Prism a réellement pris, lu dans le processus, et rougit s il recopie la demande. Que le seuil se juge à chaud et en rendu GPU est un jugement, écrit ici avec ses chiffres : le banc de l IHM ne tourne pas en CI"
verified:
  - by: machine:ci
    at: 2026-10-13
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# Le seuil d'ouverture d'un écran se juge à chaud et en rendu GPU, et un relevé de l'IHM dit par quoi il a été dessiné

## Contexte

O3 demande qu'aucune image ne tarde de plus de 200 ms pendant la navigation qui suit un import. Le
banc de l'IHM relevait de 203 à 758 ms à l'ouverture des écrans sur un premier poste, de 197 à 411 ms
sur un second à l'échelle 1 : #6171 en a fait un seuil non tenu.

Le banc dessine en logiciel. Le pom le pose pour tous les tests (`prism.order=sw`), afin qu'aucun ne
dépende d'une carte graphique. L'application, elle, dessine par la carte.

Mesuré le 2026-10-13 sur le second poste (16 cœurs, carte Intel), à l'échelle 1, en datant chaque
ouverture : le début du geste, puis le premier pulse après mise en page où l'écran montre ses données.
Quatre nuits de 300 WAV, neuf ouvertures après chacune, quatre tirs par dessin :

| Après les nuits 2 à 4 | Rendu logiciel | Rendu GPU |
|---|---|---|
| écart max entre deux images | 182 à 240 ms | 52 à 122 ms |
| écarts de plus de 100 ms, par tir | de 19 à 21 | aucun, sauf un tir qui en a un de 122 ms |
| ouverture de la vue multisite, sur le fil JavaFX | 68 à 233 ms | 61 à 212 ms |
| ouverture de l'écran des sons | 59 à 225 ms | 56 à 158 ms |

Le fil JavaFX fait le même travail dans les deux dessins. Ce qui diffère est l'image qui suit : en
logiciel, presque chaque ouverture de la vue multisite et de l'écran des sons est suivie d'un écart de
176 à 193 ms, daté sur le jalon « prêt » ou juste après lui. En rendu GPU il n'existe pas.

La première ouverture d'un écran dans le processus coûte dans les deux dessins : de 259 à 465 ms
pour la vue multisite, de 352 à 527 ms pour l'écran des sons. C'est le chargement d'un écran jamais
ouvert.

## Décision

**1. Le seuil d'ouverture se juge à chaud et en rendu GPU.** À chaud : une fois chaque écran ouvert
dans le processus. En rendu GPU : le dessin de l'application. Jugé ainsi, il est tenu : 122 ms au
plus sur cent huit ouvertures.

**2. La première ouverture de chaque écran se publie à part, sous son nom.** Elle n'entre pas dans le
verdict, et le relevé du banc lui donne sa ligne.

**3. Un relevé de l'IHM dit par quoi il a été dessiné.** Sa ligne d'échelle porte `dessin=`, le
pipeline que Prism a pris, lu dans le processus : un rendu GPU demandé et refusé par le poste retombe
sur le logiciel sans rien dire, et ses chiffres passeraient pour ceux de la carte.

**4. Le dessin des tests reste logiciel par défaut.** Le banc se tire en rendu GPU par
`-Drecette.prism.order=es2`. Les deux se publient : le logiciel est ce que voit un poste sans
accélération graphique.

## Conséquences

Les relevés « non tenu » de la navigation, pris en rendu logiciel, mesuraient le dessin du banc plus
que l'application. Ils restent dans `docs/benchmarks/README.md`, sous leur dessin.

Trois choses ne sont pas réglées, et se disent :

- **L'ouverture à froid** reste longue. Elle se publie, elle ne se juge pas.
- **Un poste sans accélération graphique** paie l'image de 176 à 193 ms à chaque ouverture. Les
  journaux de Prism montrent des shaders d'ombre portée : une piste, non mesurée.
- **Le seuil de 50 ms d'O5** pour l'ouverture de la vue multisite et de l'écran des sons n'est pas
  tenu, dans aucun dessin : c'est du travail du fil JavaFX, porté par #6207.

La décision repose sur un poste. Le premier poste de la campagne n'a de relevés qu'en rendu logiciel.

## Ce qui a été écarté

**Garder les écrans vivants, ou les bâtir hors du fil JavaFX.** Ce sont des remèdes pour l'ouverture à
froid et pour le seuil de 50 ms, pas pour le gel à chaud, qui n'est pas dans l'application. Ils
touchent le navigateur et la mémoire, pour un coût que la mesure ne justifie pas ici.

**Passer tous les tests en rendu GPU.** La CI n'a pas de carte, et un test qui dépend du poste rend un
verdict par machine.

**Le journal des pulses de JavaFX** (`-Djavafx.pulseLogger=true`), pour partager une ouverture entre
ses travaux : il fait tomber le processus, sur une `NullPointerException` de `PrintLogger`.
