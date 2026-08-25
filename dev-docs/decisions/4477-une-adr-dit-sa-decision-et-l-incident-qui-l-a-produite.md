---
type: adr
title: "Une ADR dit sa décision et l'incident qui l'a produite, le reste se résorbe par cliquet"
status: stable
article: A9
chantier: "#4477 (report d outillage, chantier #4462)"
decided_at: 2026-08-25
verification: probable
enforced_by:
  - "scripts/adr/4477-longueur-des-adr.py"
ratchet: 58
verified:
  - by: machine:suspects
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# Une ADR dit sa décision et l'incident qui l'a produite, le reste se résorbe par cliquet

## Contexte

Le corpus compte 174 décisions pour 125 000 mots, d'une longueur remarquablement uniforme : médiane
697 mots, maximum 1 556, aucune queue monstrueuse. Le problème n'est donc pas quelques documents
démesurés qu'on pourrait couper ; c'est que **la longueur moyenne est celle d'un exposé**, là où la
jurisprudence d'un article demande un cas.

Avec la constitution, un lecteur n'a plus besoin de descendre dans les cas pour connaître la règle :
il lit vingt-six articles. Il ouvre une ADR pour une seule raison, savoir **ce qui a été décidé et
quel incident l'a imposé**. Tout ce qui excède cela était utile pendant le chantier et pèse après.

Deux façons de traiter cette dette, et une seule tient. Réécrire les 174 d'un bloc produirait
174 abrégés bâclés, et l'on perdrait en un après-midi ce que quatre mois ont su noter. Ne rien
faire laisserait la dette grossir, puisque rien ne l'empêche.

## Décision

**Une ADR porte sa décision, ses conséquences, et l'incident qui l'a produite. Le reste se retire par
tranches, sous cliquet.**

- Le cliquet compte les ADR dont le **corps** dépasse **800 mots**, en-tête exclu. Il vaut **45**
  après deux tranches, et il ne remonte jamais.
- Le seuil n'est pas la longueur souhaitable : c'est le rang où la dette devient résorbable. À 600
  mots elles seraient 126, un objectif que personne ne tient et qui finit ignoré ; à 1 000 elles ne
  seraient que 18, trop peu pour porter le travail. **Le seuil descendra quand le cliquet sera
  descendu**, et pas avant.
- Ce qui se retire en premier, dans une ADR : la reprise du contexte que la constitution énonce
  désormais, les alternatives écartées pour des raisons devenues évidentes, et les récapitulatifs
  qui répètent la décision.
- Ce qui ne se retire **jamais** : l'incident daté, le chiffre mesuré, et la raison pour laquelle une
  alternative plausible a été refusée. Ce sont les trois choses qu'un repreneur ne peut pas
  reconstituer.

## Conséquences

Le nombre de mots est un **indice**, pas la règle : d'où une vérification `probable` et non
`certaine`. Une décision épineuse peut légitimement demander mille mots, et une ADR de trois cents
peut rester illisible. Le script rend des suspects que la relecture trie ; il ne condamne aucune ADR.

Une ADR nouvelle qui dépasse le seuil fait **monter** le compte et rougir la CI. Elle n'est pas
interdite : on la justifie et l'on relève le cliquet, mais un cliquet qui monte est une décision,
pas une formalité.

## Alternatives écartées

**Un cliquet sur le volume total du corpus.** Il aurait laissé compenser une ADR raccourcie par une
autre allongée, et n'aurait rien dit du document qu'on ouvre.

**Exclure les blocs de code de la mesure.** Cela avait l'air soigneux, et ne mesurait rien : les
27 ADR qui portent du code ne totalisent que 495 mots de bloc sur 124 385. Une exclusion qui ne
change rien est décorative, et elle offrirait un moyen de passer sous le seuil en déplaçant de la
prose dans un tableau.

**Fixer une longueur maximale par refus.** C'est le nettoyage que l'article A9 écarte : une règle
qu'on ne peut pas appliquer d'un coup se contourne, puis s'ignore.
