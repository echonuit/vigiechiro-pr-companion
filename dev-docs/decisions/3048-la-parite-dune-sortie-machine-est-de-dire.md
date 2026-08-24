---
type: adr
title: "La parité d'une sortie machine est de **dire**, pas de retirer (précise 0014)"
status: stable
article: A19
chantier: "#3048, suites de la clôture #3018"
decided_at: 2026-07-31
verification: certaine
enforced_by:
  - "ExportSyntheseCsvTest#referentiel_indisponible_conserve_les_colonnes"
verified:
  - by: machine:ci
    at: 2026-07-31
---

# La parité d'une sortie machine est de **dire**, pas de retirer (précise 0014)

## Contexte

L'[ADR 0014](0014-parite-cli-ihm.md) pose que toute capacité métier est offerte des deux côtés. Elle ne
dit pas ce que « pareil » veut dire quand un état **dégradé** survient, et l'intuition qu'on en tire est
fausse.

Le cas qui l'a montré : quand le référentiel d'activité est indisponible, **l'écran retire** les colonnes
« Activité » et « Seuils retenus », masque le sélecteur de milieu, efface la mise en garde et la citation,
et l'assume - « créditer une source qu'on n'a pas pu charger n'aiderait personne ».

Les deux sorties machine, elles, affirmaient sans condition « Comparé au référentiel : région … · Été »
et recopiaient la citation. Elles décrivaient une comparaison qui n'avait pas eu lieu.

La correction évidente semblait être : *faire pareil que l'écran*. Elle est mauvaise.

## Ce que « faire pareil » aurait cassé

Le CSV porte déjà, en commentaire, la raison :

> Un nom de colonne exportée est un **CONTRAT** avec les scripts qui relisent le fichier.

Retirer la colonne « Activité » d'un CSV décale toutes les suivantes. Un script qui lit la colonne 6 lit
alors autre chose **sans le savoir** : on casse exactement ce que le retrait prétendait protéger. La même
chose vaut pour un champ JSON dont un consommateur teste la présence.

Un écran et un fichier n'ont pas le même lecteur. L'un est **lu** par quelqu'un qui voit tout d'un coup et
comprend qu'une colonne a disparu ; l'autre est **parcouru** par un programme qui compte les colonnes.

## Décision

Quand une surface doit rendre compte d'un état dégradé :

1. **l'IHM peut retirer** - un écran se relit entièrement à chaque affichage, une colonne en moins se voit ;
2. **une sortie machine ne retire pas, elle dit** - la structure reste stable, et un champ, une ligne
   d'en-tête ou un drapeau **nomme** l'état ;
3. ce que la sortie n'a pas pu établir, elle **cesse de l'affirmer** : pas de contexte de comparaison
   inventé, pas de citation d'une source non chargée. Se taire n'est pas retirer une colonne ;
4. le message dit aussi **ce qui reste bon**. Ici : les comptages sont une mesure, la classe n'en était
   qu'une lecture ; perdre la seconde ne doit pas jeter le doute sur la première.

Concrètement : le JSON gagne `"disponible"` et omet `avertissement`/`source` ; le CSV remplace ses trois
lignes de contexte par une seule, et **garde ses treize colonnes**.

## Conséquences

- La parité de la passe 2 se lit désormais en deux temps : la **capacité** est offerte des deux côtés
  (ADR 0014), la **restitution d'un état dégradé** suit la nature de la surface.
- Un consommateur peut enfin distinguer **« cette espèce n'est pas au référentiel »** de **« il n'y a pas
  de référentiel »**. Les deux situations produisaient `couvertParLeReferentiel: false`, et elles appellent
  des conduites opposées : corriger une identification, ou réparer une installation.
- Le nombre de colonnes du CSV reste épinglé par un test (`COLONNES = 13`), qui devient le garde-fou de
  cette ADR autant que celui du format.

## Alternatives écartées

- **Retirer les colonnes comme l'écran.** Rupture de contrat silencieuse, décrite ci-dessus.
- **Ne rien changer.** C'était l'état de départ : la sortie affirmait une comparaison qui n'a pas eu lieu.
  Un fichier qui ment sur sa provenance est pire qu'un fichier incomplet, parce qu'il ne se signale pas.
- **Laisser les cellules vides sans rien dire.** Dans un CSV une cellule vide se lit comme une donnée
  manquante - ce qui est vrai ligne à ligne, mais ne dit pas que **rien** n'a pu être comparé. La tête du
  fichier est le seul endroit où cette information tient.
