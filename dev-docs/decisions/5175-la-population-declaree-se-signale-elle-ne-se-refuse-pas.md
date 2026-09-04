---
type: adr
title: "La population déclarée d'un contrat se signale, elle ne se refuse pas"
status: stable
article: A5
chantier: "#5175 (chantier #5006)"
decided_at: 2026-09-04
verification: humaine
verification_note: "confronter une population déclarée en prose à ce qu'un garde parcourt demande de juger si la phrase décrit un sous-ensemble légitime ou tait un corpus. Quatre des cinq écarts mesurés sont des descriptions plus précises que le chemin, pas des populations fausses. Deux dispositifs le tiennent sans refuser : le verdict dit combien il a confronté, une loupe signale ce qui reste"
loupe:
  - "scripts/adr/loupe-5175-population-non-nommee.py"
verified:
  - by: human:nedseb
    at: 2026-09-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-04
---

# La population déclarée d'un contrat se signale, elle ne se refuse pas

## Contexte

`verifie_contrats_tiennent.py` annonce qu'il confronte les quatre champs confrontables d'un contrat,
dont `population`. Mesuré le 2026-09-04 : il le fait pour **26 contrats sur 67**, et s'abstient pour
**41**.

**L'abstention était invisible**, et c'est ce qui a coûté. Elle a laissé passer #5176, où l'invariant
des contrats déclarait lire `scripts/adr` pendant qu'il en lisait 66 sur deux dossiers : #5157 avait
élargi sa population sans toucher à sa déclaration. Il a fallu construire un artefact de clôture pour
le voir.

## La cause n'est pas celle qu'on croyait

L'issue supposait que la prose déclarée bloquait la résolution. Mesuré : pour les **40 abstentions
Python, c'est l'inférence qui rend `(non declaree)`**. Les deux côtés sont vides, et il y a une
seule raison, qui vaut **40 fois sur 40** : tous calculent leur racine depuis `__file__`, quand
l'inférence ne reconnaissait qu'un chemin littéral écrit au niveau du module.

`chemins_lus()` sait désormais résoudre ces racines par `ast`, sans lancer le garde - le lancer lui
ferait faire son travail, ce qui pour un générateur veut dire écrire dans le dépôt (ADR 5102).

## Pourquoi cela ne devient pas un refus

Parce que la confrontation ne conclut pas. Sur les 41 abstentions :

| | |
|---|---:|
| chemins résolus | **13** |
| dont la population les nomme tous | 8 |
| dont un chemin n'est pas nommé | **5** |

Sur ces cinq, **quatre déclarent une population plus précise que le chemin** :
`verifie_scripts.py` parcourt `scripts/adr` et déclare « les gardes que `_charge` nomme dans ce
fichier », qui est un sous-ensemble correctement décrit. Un garde qui refuserait ferait rougir une
déclaration meilleure que la règle, et un dispositif qui crie sur du juste apprend à ignorer sa
sortie - c'est déjà la raison pour laquelle `SC2016` est exclu de shellcheck ici.

## Décision

**Le champ `population` n'est pas confronté mécaniquement dans le cas général, et cela se déclare
plutôt que de se taire.** Deux dispositifs, dont aucun ne refuse :

1. **Le verdict dit ce qu'il a confronté.** `verifie_contrats_tiennent.py` imprime, à côté de son
   verdict, le nombre de populations confrontées et le nombre d'abstentions. C'est la leçon de `lus`
   (#5007) appliquée à un champ : un vert qui ne dit pas ce qu'il a jugé ne dit rien.

2. **Une loupe signale ce qui reste confrontable.**
   `loupe-5175-population-non-nommee.py` nomme les gardes dont un chemin parcouru manque à leur
   population déclarée. Elle rend `0` et laisse trier.

Et la cécité entre dans `HORS_CONFRONTATION`, où deux champs étaient déjà nommés. Un garde qui
promet plus qu'il ne tient emprunte la solidité de ses voisins.

## Ce que cela prouve, et ce que cela ne prouve pas

**Prouvé** : aucun chemin parcouru n'est passé sous silence, sur les treize gardes que l'évaluation
atteint. C'est ce qui aurait suffi à attraper #5176 sans artefact.

**Non prouvé** : que la phrase soit juste. `population` reste de la prose, et une prose peut nommer
les bons dossiers en décrivant mal ce qu'on y prend.

## Ce que cette décision ne tranche pas

**Le cliquet.** Poser un cliquet descendant sur les abstentions bornerait la dette. Il figerait un
nombre dont on ignore la part réductible : vingt-huit gardes construisent leur racine d'une façon que
l'évaluation ne suit pas, et six n'ont aucun corpus de fichiers - deux lisent la forge, deux passent
par `git`, deux ne parcourent rien. Ces six ne seront **jamais** confrontables, et un cliquet qui les
compte demanderait de descendre un chiffre qui a un plancher.

**Le vocabulaire fermé.** Les 41 abstentions portent **32 expressions distinctes**. Fermer un
vocabulaire de 32 entrées reviendrait à recopier le corpus dans une constante, qui vieillirait comme
les quatre chiffres de prose que ce chantier a trouvés faux.
