---
type: adr
title: "Une javadoc contracte, elle ne raconte pas : le code dit quoi, l'ADR dit pourquoi"
status: stable
article: A30
chantier: "#4359 (l'article de la javadoc, détaché de #4342 par la règle de numérotation)"
decided_at: 2026-08-24
verification: probable
enforced_by:
  - "scripts/adr/4359-javadoc-narratif.py"
ratchet: 3641
verified:
  - by: machine:suspects
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Une javadoc contracte, elle ne raconte pas : le code dit quoi, l'ADR dit pourquoi

## Contexte

Le code de production porte **26 814 lignes de prose en javadoc pour 77 891 lignes de code**. Les
étiquettes de contrat - `@param`, `@return`, `@throws` - n'en font que **7 %**. Le reste est du
récit.

La lecture des blocs les plus longs donne la même chose chaque fois : un bloc de 89 lignes racontant
l'implémentation qu'il avait remplacée, un autre de 49 lignes pour une **interface vide** qui
recopiait une ADR du corpus.

## Le défaut

Ce récit est l'histoire d'un dépôt qui n'existe plus. Le coût n'est pas la place : **le lecteur qui
cherche ce que fait une classe traverse d'abord ce qu'elle a été**, sans savoir quelle phrase est
encore vraie. Une javadoc dont une moitié est périmée se saute, et le contrat se perd avec le
récit.

Trois natures se mélangent, et seule la lecture les sépare :

- **ce qui est caduc** - un défaut corrigé, une implémentation retirée, un choix qu'on ne referait
  plus. `git log` le garde pour qui le cherche ;
- **une décision recopiée** - le corpus la porte déjà, et deux exemplaires dérivent. Cela se
  **cite**, ce que l'article A5 demande de tout inventaire ;
- **ce que le code dit déjà** - un commentaire qui paraphrase la ligne suivante. Si la ligne n'est
  pas claire, c'est elle qu'il faut réécrire.

## Décision

Trois endroits, un rôle chacun.

**Le code dit ce qu'il fait.** Un nom qui dit son intention, une fonction qui tient dans un écran,
un type qui rend l'erreur impossible. Un commentaire qui explique un nom est un nom à changer.

**La javadoc dit le contrat** : ce qu'on doit fournir, ce qu'on obtient, ce qui peut être nul, ce
qui est garanti. Elle s'adresse à qui appelle, pas à qui a écrit.

**L'ADR dit pourquoi**, et la javadoc la **cite** au lieu de la redire.

Le seuil est de **8 lignes de prose** par bloc, et le compte est en **lignes au-delà** : un bloc de
huit lignes ou moins ne coûte rien, une classe difficile méritant un paragraphe. Le grain de la
ligne a été choisi après coup, au grain du **bloc** un bloc de 50 lignes réécrit en 22 ne bougeant
pas le compte, ce qui poussait à couper du **contrat** pour passer sous le seuil. Le cliquet descend
par tranches, chaque bloc **lu**.

## Conséquences

**Le code de production seulement.** Un garde doit dire ce qu'il vérifie - c'est l'article A2, et sa
javadoc est sa déclaration. Compter les tests demanderait au dépôt de renoncer à une règle pour en
tenir une autre.

**Le niveau est `probable`.** La longueur se compte ; ce qu'il faut couper ne se décide pas
mécaniquement. Un record de trente champs n'est pas un suspect : les étiquettes de contrat sont
exclues, avec leurs suites, et deux cas du banc le tiennent.

**Cent trente-six blocs lus**, 2 936 lignes de prose devenues 1 769, cliquet à **2 532**. Une ADR y
était recopiée **quatre fois dans le même bloc**. Le registre attendu est écrit dans
`CONTRIBUTING.md` : il n'existait nulle part.

**Un second garde, déterministe.** Une ligne de javadoc répétée juste après elle-même est une coupe
ratée. Le défaut est arrivé quinze fois d'un coup, et il a passé la compilation, spotless et les
tests : le lecteur voyait la phrase deux fois, aucun garde ne la voyait. Il a une seconde forme,
trouvée trois fois ensuite : la ligne d'avant n'est pas identique, elle est le début tronqué de la
suivante. `verifie_javadoc_sans_doublon.py` refuse les deux, sur tout le code Java du dépôt.

**Et tout n'est pas du récit.** `ParserCsvTadarida` documente un format et un mapping de colonnes,
`TransformationAudio` l'arithmétique du découpage. Ces blocs dépassent le seuil et le méritent : le
cliquet ne descendra pas à zéro, il compte une dette, pas une faute.

## Alternatives écartées

- **Compter toutes les lignes de commentaire.** Un record bien documenté deviendrait le pire
  suspect du dépôt.
- **Le pourcentage de commentaire par fichier.** Meilleur pour **choisir quoi lire** : il désigne
  les petites interfaces où le commentaire a mangé le code - `SuitLaRevision` portait 36 lignes pour
  4 de code. Mauvais comme cliquet : le dénominateur étant le code, un port d'une méthode plafonne
  haut quoi qu'on fasse, et le cliquet pousserait à couper du contrat là où il compte le plus.
- **Compter les blocs trop longs plutôt que leurs lignes.** Essayé : le compte ne bougeait pas d'un
  bloc de 50 lignes réécrit en 22.
- **Une passe mécanique de suppression.** Le motif est trivial à trouver, le remède ne l'est pas.
- **Interdire la prose en javadoc.** Une classe difficile mérite un paragraphe.
