---
type: adr
title: "Un verdict se remesure là où il sert à ne pas faire quelque chose"
status: stable
article: A11
chantier: "#4650 (un dispositif peut être vert sans avoir jugé), lot #5572"
decided_at: 2026-09-29
verification: humaine
verification_note: "savoir si un verdict RETIRE une question dépend de ce que son lecteur en fait, non de la forme du verdict : l'information n'est dans aucun fichier du dépôt. Ce qui se tient mécaniquement est chacune des sept formes, par le gage de sa propre décision"
enforced_by: []
relations:
  complete: ["4490-un-temoin-se-prouve-par-mutation-mecaniquement", "4918-un-cas-rouge-pour-la-mauvaise-raison-ne-prouve-rien", "5015-un-garde-sans-unite-a-compter-le-declare-chez-lui", "5053-le-compte-lu-vient-de-l-arbre-vise-pas-de-la-source", "5054-un-temoin-qui-n-affirme-que-des-vides-ne-prouve-rien", "5452-une-exemption-se-derive-de-ce-que-la-chose-fait", "5483-un-gage-inerte-est-un-defaut-pas-un-quatrieme-niveau"]
verified:
  - by: humain
    at: 2026-09-29
generated:
  by: "process:assistance-par-agents"
---

# Un verdict se remesure là où il sert à ne pas faire quelque chose

## Le contexte

Sept décisions de ce dépôt traitent du même défaut : **un dispositif rend un verdict sans avoir
exercé ce qu'il prétend juger.**

| Décision | La forme qu'elle tient |
|---|---|
| 4918 | il refuse, mais pour une autre raison que celle qu'il nomme |
| 4490 | sa détection n'est pas atteinte par la mutation, donc son vert ne dit rien |
| 5054 | son témoin n'affirme que des vides, indistinguables d'une fonction morte |
| 5015 | il conclut sans dire sur quelle population |
| 5053 | il compte ce que sa source lui rapporte, non ce vers quoi il a été pointé |
| 5452 | il s'exempte d'une part de sa population au vu d'un idiome d'écriture |
| 5483 | l'ADR nomme un gage qui ne peut faire rougir aucune demande |

**Aucune des sept ne nomme les six autres.** Cinq ne déclarent aucune relation, et les deux qui en
déclarent pointent ailleurs. Chacune emploie son propre vocabulaire, si bien qu'une recherche sur les
mots d'une forme ne rend aucune des six autres.

## La mesure qui décide

Huit instances en un mois, sur cinq chantiers : #4650, #5452, #5533, #5265 et #5564. **Aucune n'a été
trouvée par un garde.** Les huit l'ont été parce que quelqu'un a remesuré ce qu'un autre annonçait.

Sept décisions correctes, chacune avec son gage, n'ont pas empêché la huitième ; une huitième de la
même facture n'empêcherait pas la neuvième.

## La décision

**Un verdict se remesure là où il sert à ne PAS faire quelque chose.**

Un vert qui accompagne un travail coûte peu s'il est faux : le travail se fait quand même, et
l'erreur se découvre en chemin. Un vert qui **retire une question** - « c'est couvert », « c'est
éprouvé », « il n'y a rien à trier » - est celui dont la fausseté ne se découvre jamais, puisque
plus personne ne regarde. Les huit instances sont toutes de cette seconde espèce.

**Remesurer veut dire dériver le même nombre par un chemin qui ne passe pas par le dispositif.** Le
relire n'est pas le remesurer : les sept formes ont toutes survécu à des relectures.

## Ce que cela ne dit pas

Ce n'est pas « remesurer partout » : le coût serait tel qu'on ne le ferait nulle part. L'endroit se
reconnaît à une question : **qu'est-ce que je ne ferai pas si ce verdict est vert ?**

Et ce n'est pas une doctrine neuve. Les sept décisions restent la référence pour leur forme ; celle-ci
dit où les chercher, et porte la relation qui leur manquait.

## Le contrôle qui met la règle à l'épreuve

Le banc de l'ADR 4490 est le dispositif le plus mécanique de la famille, et son vert retire une
question : « ce témoin prouve-t-il quelque chose ? ». Remesuré, il ne la retire pas entièrement. La
neutralisation remplace **toutes** les détections d'un garde à la fois, donc un rouge prouve que leur
conjonction compte, jamais que chacune est nécessaire : une couche morte, placée derrière une couche
qui filtre avant elle, rend « tient » comme une couche utile. Le remède est d'en retirer une, non de
muter des paires - une propriété qui exige deux mutations simultanées est une propriété dont on ne
sait plus laquelle des deux la tient.

**Une limite déclarée est une raison de remesurer, jamais de croire.** La plus solide rencontrée
avouait qu'un index ne couvrait qu'en partie les classes anonymes, et c'était vrai : le trou était à
côté, 1 226 méthodes de classes imbriquées nommées que la même phrase déclarait complètes. Une limite
partiellement vraie résiste mieux qu'une limite inventée.

## Les alternatives écartées

- **Un garde qui refuserait qu'un membre de la famille ne déclare aucune relation.** Sa liste serait
  tenue à la main : le gage inerte de l'ADR 5483, écrit pour garder l'ADR qui le condamne.
- **Écrire la huitième forme comme huitième décision.** Le dépôt l'a fait sept fois.
- **Se contenter de relier les sept.** Relier n'aurait pas trouvé la huitième, qui l'a été par
  remesure : la navigation manquait, mais ce n'est pas elle qui a failli.

## Comment on le sait

On ne le sait pas mécaniquement, et c'est déclaré plutôt que masqué derrière un gage décoratif
(article A11, ADR 5414). Un dispositif qui trancherait « ce verdict retire-t-il une question ? »
aurait à juger ce que son lecteur fait du verdict, non ce que le verdict dit.

Ce qui reste mécanique est considérable : les sept gages des sept décisions, que cette page relie
sans les remplacer.
