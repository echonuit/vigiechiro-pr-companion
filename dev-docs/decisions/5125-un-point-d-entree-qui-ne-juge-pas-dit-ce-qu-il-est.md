---
type: adr
title: "Un point d'entrée qui ne juge pas dit ce qu'il est, pas « sans objet »"
status: stable
article: A3
chantier: "#5125 (sous-chantier #5117)"
decided_at: 2026-09-02
verification: certaine
enforced_by:
  - "scripts/adr/verifie_contrats_tiennent.py"
verified:
  - by: machine:suspects
    at: 2026-09-02
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-02
---

# Un point d'entrée qui ne juge pas dit ce qu'il est, pas « sans objet »

## Contexte

Le format `--contrat` livré par #5009 demande six champs, dont `dispositif`. Quatre valeurs
suffisaient tant que les porteurs étaient trois : `cliquet` borne ce qu'on tolère, `plancher` garde
ce qu'on possède, `loupe` observe sans bloquer, `invariant` refuse sans marge.

Le sous-chantier #5117 a porté le corpus de `scripts/adr` à **41 contrats sur 41 points d'entrée**,
et huit d'entre eux ne rentraient dans aucune des quatre. Trois n'ont même pas de verdict à rendre :

| point d'entrée | ce qu'il fait |
|---|---|
| `rapport.py` | il **agrège** les verdicts des autres, et ne refuse jamais |
| `resserre_cliquets.py` | il **écrit** dans les ADR : un générateur |
| `verifie_scripts.py` | il éprouve les **gardes** eux-mêmes, et refuse sur une propriété de tout ou rien |

`imprime_contrat` accepte « (sans objet) » pour un champ sans réponse, et c'était la voie facile.

## Décision

**Un point d'entrée qui ne rend pas de verdict déclare quand même ce qu'il est.** Le vocabulaire
passe à sept valeurs : les quatre qui disent *comment* il juge, et trois qui disent qu'il ne juge
pas et *ce qu'il fait à la place*.

Le vocabulaire est **clos et déclaré à un seul endroit**, la constante `DISPOSITIFS` de
`verifie_contrats_tiennent.py`. Un mot hors de l'ensemble fait rougir.

## Pourquoi, et non « (sans objet) »

**Un champ qui se tait ne se distingue pas d'un oubli.** C'est déjà l'argument d'`imprime_contrat`
pour refuser un contrat incomplet : une ligne absente ne dit rien, quand `seuil: (sans objet)` dit
quelque chose. Le même raisonnement vaut un cran plus loin : « (sans objet) » sur trois points
d'entrée différents les rendrait indiscernables, alors qu'un agrégateur, un générateur et un harnais
ne se lisent, ne se lancent et ne se corrigent pas de la même façon.

**L'ADR 2748 le demande déjà pour les dispositifs de CI** : un dispositif qui peut ne rien vérifier
le dit. Cette décision-ci l'étend à ce qu'un point d'entrée déclare de lui-même.

**Et le lot 4 de #5102 en aura besoin.** `scripts/methode` porte plusieurs générateurs ; l'EPIC
l'annonçait avant que ce lot ne le rencontre. Un mot posé ici sert là-bas.

## Conséquences

**Un mot neuf se justifie là où il se pose.** Le fichier qui l'emploie le premier écrit en commentaire
pourquoi aucun mot existant ne convenait. `verifie_scripts.py` porte ainsi la raison de `harnais` :
il refuse, mais sur la couverture du dispositif plutôt que sur le dépôt, et ce n'est donc pas un
`invariant` au sens des six autres.

**Le champ reste hors confrontation avec l'inférence.** `contrats-des-gardes.py` devine le dispositif
au nom de l'aide appelée ; comparer les deux rendrait un désaccord là où il n'y a qu'une convention
de nommage. C'est le **vocabulaire** qui est vérifié, pas l'accord avec la devinette.

**Élargir le vocabulaire reste possible, et coûte une ligne.** Ce qui n'est plus possible est de
l'élargir sans le dire : le garde refuse, et l'ajout se lit dans le diff de cette constante.

## Ce qui a été écarté

**« (sans objet) » pour les trois.** Passe le garde, n'apprend rien, et rend trois natures
différentes identiques à la lecture.

**Un vocabulaire ouvert, sans contrôle.** Il aurait laissé chaque contrat inventer son mot. La
mesure d'ouverture de #5117 avait justement trouvé trois idiomes pour la même chose, et
`contrats-des-gardes.py` en avait rendu trois comptes différents et tous faux (#5046).
