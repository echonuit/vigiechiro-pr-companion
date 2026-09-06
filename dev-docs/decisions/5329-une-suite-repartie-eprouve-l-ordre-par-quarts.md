---
type: adr
title: "Une suite répartie éprouve l'ordre par quarts, et la graine reconstitue le reste"
status: stable
article: A4
chantier: "#5294 (le coût des ateliers), lot #5329"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - ".github/scripts/partition_de_la_suite.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Une suite répartie éprouve l'ordre par quarts, et la graine reconstitue le reste

## Le contexte

`ordre-alternatif` rejoue la suite entière en **fork unique et ordre inverse**. Ce ne sont pas deux
réglages de confort : c'est avec eux que les trois fuites d'état que l'en-tête de `maven.yml` nomme
se reproduisent de façon déterministe, et sans eux qu'aucune ne se reproduit.

Il coûte 20,4 minutes, et il tient le **chemin critique** de toute demande qui touche du Java. Le
chantier #5294 a ramené les demandes sans Java à 10 minutes ; celles avec Java restent à 20,4, et il
est seul responsable de cet écart.

Il **n'aboutit par ailleurs pas quatre fois sur dix** ([#5034]), à des durées sans rapport entre
elles. Un job bloquant qui ne conclut pas quatre fois sur dix n'est ni vert ni rouge.

## La décision

**`ordre-alternatif` est réparti en quatre lots, dérivés de l'arbre des tests, et la graine de la
partition tourne d'un passage à l'autre.**

## Ce que cela coûte, et il faut le lire avant le reste

Une pollution entre deux classes tombées dans **deux lots différents devient invisible**. Ce n'est
pas un effet de bord de la répartition : c'est une part de ce que ce job mesure, et on l'abandonne
sur un passage donné.

Un quart des paires est éprouvé à chaque passage, non la totalité. Dit autrement : **ce dispositif
ne prouve plus, sur une demande seule, ce qu'il prouvait avant.**

## Ce qui rend la perte acceptable

**La graine.** Elle décale la partition à chaque passage, donc les paires co-localisées changent. Ce
qui est invisible aujourd'hui devient visible quand la graine réunit les deux classes. La couverture
se reconstitue **au fil des passages** au lieu d'être acquise sur un seul, et c'est un déplacement
assumé, pas une équivalence.

**Le prix payé était réel.** 20,4 minutes d'attente sur 37 % des demandes, et quatre annulations sur
dix qui ne rendaient aucun verdict. Un dispositif complet qui ne conclut pas quatre fois sur dix
prouve moins qu'un dispositif partiel qui conclut.

## Ce que la décision ne dit pas

Elle **ne corrige pas** [#5034]. Des lots plus courts se font moins annuler, donc l'exposition
baisse ; la cause reste entière, et l'issue ouverte. Lire ce lot comme sa réponse serait une erreur.

Elle **ne réserve rien à `main`**. L'ADR 3560 a tranché qu'une étape jouée sur `main` seul n'est
jamais jouée avant sa fusion, et peut donc être fusionnée cassée. Les quatre lots s'exercent sur
chaque demande.

## La partition se dérive, elle ne s'énumère pas

L'[ADR 3450] a tranché pour ce job même : « toute la suite, et non une liste de classes sensibles -
une liste ne voit que ce qu'on y a mis et se périme. »

Une partition tenue à la main serait exactement cette liste. Celle-ci part de l'arbre : tout
`*Test.java` sous `src/test/java`, ce qui est ce que surefire sélectionne ici - les trois autres
motifs qu'il connaît ne trouvent **aucun** fichier dans ce dépôt, mesuré le 2026-09-06. Le corpus
couvre donc 100 % des classes par construction.

Les noms sont **pleinement qualifiés**, et ce n'est pas du zèle : `TaxonDaoTest` existe dans deux
paquets, et un `-Dtest=` par nom simple le ferait tomber dans deux lots à la fois. L'union ne serait
plus une partition.

La répartition est **en rond et non en blocs contigus** : des blocs alphabétiques mettraient tout un
paquet dans le même lot, donc n'éprouveraient jamais l'ordre entre deux paquets voisins - qui est
précisément là où une fuite d'état se loge.

## Comment elle se vérifie

`partition_de_la_suite.py --verifie` confronte **l'union des lots au corpus, classe par classe et
pour toutes les graines**. Classe par classe, et non par un compte : deux erreurs qui se compensent
rendraient le même total.

C'est le défaut de [#4544] monté d'un cran. Un passage tronqué y annonçait « toutes les classes de
test » sur **618 des 758**, sans un échec, et rendait 0. Ici la forme serait quatre lots verts dont
l'union ne couvre pas la suite : chacun rend 0 en n'ayant joué que sa part, et personne ne voit ce
qui manque.

Le garde refuse aussi un lot vide, un recouvrement, et un déséquilibre au-delà de 1,5 fois la
moyenne. Chaque lot écrit enfin son témoin de fin de passage, sur le patron de [#4544] : un job coupé
par son butoir meurt pendant Maven, donc avant cette ligne.

## La condition de validité

Le chantier #5294 repose sur le fait que `main` n'a **aucune protection de branche** ([#4933]). Le
jour où une protection arrive, un lot dont la portée a dit non se lira comme un contrôle manquant, et
cette décision se relit avec le reste.

[#4544]: https://github.com/echonuit/vigiechiro-pr-companion/issues/4544
[#4933]: https://github.com/echonuit/vigiechiro-pr-companion/issues/4933
[#5034]: https://github.com/echonuit/vigiechiro-pr-companion/issues/5034
[ADR 3450]: 3450-une-propriete-de-fuseau-se-tient-en-rejouant-pas-en-relisant.md
