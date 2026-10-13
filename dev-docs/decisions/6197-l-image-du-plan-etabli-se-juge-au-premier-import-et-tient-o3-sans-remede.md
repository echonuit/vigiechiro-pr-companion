---
type: adr
title: "L'image du plan établi se juge au premier import d'un processus, et tient O3 sans remède"
status: stable
article: A5
chantier: "#6178 (l'interface tient O3 et O5), lot #6197"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/perf/outils/DatationDesEcartsTest.java"
verification_note: "le test tient l instrument : un écart se situe parmi les jalons de l import, donc l image du plan établi se reconnaît dans un relevé. Qu elle tienne O3 est un relevé du banc de l IHM, écrit ici avec ses chiffres : ce banc ne tourne pas en CI"
verified:
  - by: machine:ci
    at: 2026-10-13
relations:
  complete: ["6171-le-seuil-d-ouverture-d-un-ecran-se-juge-a-chaud-et-en-rendu-gpu"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# L'image du plan établi se juge au premier import d'un processus, et tient O3 sans remède

## Contexte

O3 demande qu'aucune image ne tarde de plus de 200 ms pendant un import. En datant les écarts de
#6175, le banc de l'IHM en a trouvé un qui ne devait rien au parallélisme : l'image qui suit le « plan
établi », celle où la table de suivi reçoit ses lignes et s'affiche. Elle durait de 100 à 255 ms sur
trente et un tirs en rendu logiciel, et #6197 demandait sa cause, ou une décision écrite.

## Ce que la mesure a établi

Le 2026-10-13, sur le second poste (16 cœurs, carte Intel), à l'échelle 1, `main` à `cb9ed7676`,
`BancIhmO3Test` sur une nuit de 1572 WAV, dix tirs par dessin, alternés :

| Écart du plan établi, dix tirs | Rendu GPU | Rendu logiciel |
|---|---|---|
| daté, donc de plus de 100 ms | 114, 147 et 162 ms | 107 ms |
| sous 100 ms | sept tirs | neuf tirs |
| au-delà de 200 ms | aucun | aucun |

**C'est le coût d'une première fois.** Sur quinze tirs de trois ou quatre nuits dans un même
processus, le premier import porte un écart de plus de 100 ms huit fois sur quinze. Les quarante-deux
imports suivants n'en portent que deux, de 106 et 129 ms.

**C'est une mise en page.** Un profil JFR de cette fenêtre, sur quatre tirs, place 35 échantillons du
fil JavaFX sur 65 dans la passe de mise en page du pulse, où les premières cellules de la table sont
construites et stylées. La transformation démarre au même instant : le fil attend le rendu de 6 à
63 ms dans la fenêtre, et le ramasse-miettes s'y arrête de 0 à 20 ms.

**Ce n'est pas le dessin** : les deux tiennent.

## Décision

**L'image du plan établi se juge au premier import du processus, dans les deux dessins.** L'ADR 6171
publie à part la première ouverture d'un écran, parce que les suivantes sont le cas courant. Ici le
cas courant est le premier import : on lance l'application pour importer une nuit. Le seuil d'O3
s'applique donc à cette image, à froid.

**Aucun remède n'est engagé.** L'image tient O3, à 162 ms au plus sur trente-trois premiers imports
relevés ce jour-là sur `main`. La raccourcir demanderait de construire la table avant le clic, donc
de changer l'écran pour une image qu'aucun relevé ne montre au-delà du seuil sur ce code.

**Elle se rouvre sur un relevé, pas sur un souvenir.** Un écart du plan établi de 200 ms ou plus, daté
par le banc sur `main` dans une série de dix tirs, rouvre la question avec son relevé.

## Ce qui n'est pas établi

- **La queue.** Avant #6209, quatre premiers imports sur quarante-neuf ont dépassé le seuil : 235, 244,
  255 et 291 ms. Aucun des trente-trois d'après. #6209 déclare 12 px sur la racine, là où Linux en
  donnait 13 : que ce soit lui n'est pas établi. Un témoin de dix tirs sur le code d'avant, tiré le
  même après-midi, en a rendu un seul, de 235 ms en rendu GPU, et les autres relevés d'avant datent
  d'heures où la charge du poste était autre.
- **Le profil est mince.** JFR n'échantillonne que quelques fils par milliseconde : pendant un import,
  le fil JavaFX n'en reçoit que de 9 à 22 par fenêtre. Il donne une répartition, pas des durées.
- **Un autre poste.** Tout vient du second poste de mesure.

## Conséquences

- `docs/benchmarks/README.md` porte le relevé, avec la machine, la date, le commit et le dessin de
  chaque tir.
- L'image de fin d'un import, de 100 à 185 ms, reste hors de cette décision : elle a sa ligne dans
  les suites du chantier #6178.
- La mesure a laissé deux outils hors du dépôt, sur le poste de mesure : la lecture d'un
  enregistrement JFR par fenêtre d'horloge, et l'extraction de la fenêtre du plan établi d'un relevé.
