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
  - "scripts/adr/4359-blocs-relus.py"
loupe:
  - "scripts/adr/loupe-4359-javadoc-vieillie.py"
ratchet: 3094
inv_key: cliquet-javadoc
verified:
  - by: machine:suspects
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Une javadoc contracte, elle ne raconte pas : le code dit quoi, l'ADR dit pourquoi

## Contexte

Le code de production porte **33 410 lignes de prose en javadoc pour 78 496 lignes de code**. Les
étiquettes de contrat - `@param`, `@return`, `@throws` - n'en font que **5,7 %**. Le reste est du
récit.

La lecture des blocs les plus longs donne la même chose chaque fois : un bloc de 86 lignes qui
raconte écran par écran ce que l'outil rend, un autre de 49 lignes pour une **interface vide**, qui
recopie une ADR du corpus.

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

**Le cliquet a ouvert à 3 641**, sur 713 blocs dans 572 fichiers. Il vaut **<!--inv:cliquet-javadoc-->3 094<!--/inv-->** sur 669 blocs
dans 545 fichiers après trois tranches (#4397, #4401, #4424). Le registre attendu est
écrit dans `CONTRIBUTING.md` : il n'existait nulle part.

**Un bloc relu et gardé volontairement s'inscrit, et son inscription se périme** (#4414). Cette
décision dit plus haut que le cliquet ne descendra pas à zéro. Rien ne distinguait pourtant un bloc
qu'on avait ouvert et décidé de garder d'un bloc qu'on n'avait jamais lu : chaque tranche rouvrait
les deux. `scripts/adr/4359-blocs-relus.tsv` mémorise la lecture, et
`scripts/adr/4359-blocs-relus.py` la tient honnête.

**Le registre ne fait pas baisser le cliquet, et c'est la décision.** La dette reste annoncée en
entier. Il dit « ce bloc a été lu », pas « cette dette n'existe plus » : confondre les deux
transformerait une **mémoire de revue** en **desserrement**, et ferait descendre le compteur sans
qu'une seule ligne ait été corrigée.

**Un bloc s'identifie par l'empreinte de son texte.** Trois autres formes ont été confrontées, et
chacune produit un faux vert : une entrée `Fichier.java:120` se fait détourner par une insertion en
amont, qui donne l'exemption à un autre bloc ; une entrée par nom de membre est héritée par une
surcharge que personne n'a relue ; une marque dans le bloc voyage par copier-coller sans que la
demande de fusion ne touche le garde, donc sans le signal central que l'ADR
[4339](4339-un-arbre-repris-d-un-outil-amont-se-declare.md) exige. L'empreinte est insensible aux
deux premiers et invalidée par toute édition - une prose réécrite n'a pas été relue sous sa forme
actuelle. Mesuré avant d'être retenu : **680 blocs sous cliquet, 680 empreintes distinctes, aucune
collision**. Une réindentation, elle, ne l'invalide pas : les marges sont retirées avant le calcul.

**Ce que le registre ne voit pas**, et qui recoupe la loupe ci-dessous : un bloc devenu faux parce
que le **code** a changé sous lui garde son empreinte. Les deux dispositifs se lisent ensemble.

**Une loupe dit où regarder avant de couper** (#4415). Contracter un bloc **déjà faux** ne le
corrige pas : il le préserve, en plus court et donc plus crédible. `loupe-4359-javadoc-vieillie.py`
liste les blocs sous cliquet dont le **code** a bougé après eux - 116 des 680 au jour de sa pose.

Le signal est grossier, et c'est assumé : un commit qui n'a touché qu'une accolade fait remonter la
date sans rien invalider. Elle ne bloque donc rien et ne porte pas de cliquet. Sa vraie limite est
ailleurs, et elle compte : un bloc **faux dès le premier jour** porte la même date que son code, et
plus de la moitié du corpus est dans ce cas. La loupe hiérarchise une revue, elle ne la remplace pas.

Ce qui l'a motivée est mesuré. Sur soixante fichiers porteurs de dette, 27 % avaient vu leur code
bouger après leur javadoc. Et la première tranche de douze fichiers ouverts en a trouvé **deux**
qui mentaient - l'un annonçant quatre onglets là où le code en rend six, l'autre décrivant le
comportement d'avant sa propre correction. Tous deux attrapés **par accident**, parce qu'ils se
contredisaient eux-mêmes ; un bloc faux et cohérent serait passé.

**Un second garde reste à écrire.** Une ligne de javadoc répétée juste après elle-même est une coupe
ratée, et elle passe la compilation, spotless et les tests : le lecteur voit la phrase deux fois,
aucun garde ne la voit. Elle a une seconde forme, plus discrète : la ligne d'avant n'est pas
identique, elle est le début tronqué de la suivante. Rien ne refuse ni l'une ni l'autre ici. Le
dépôt jumeau a rencontré les deux en résorbant sa dette (#4334), ce qui dit quand ce garde devient
nécessaire : au moment où les coupes commencent, pas avant.

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

## La jurisprudence du cliquet

Le cliquet de cette décision suit deux ADR antérieures.
[2867](2867-une-dette-se-tient-par-un-cliquet.md) pose qu'une dette se tient par un compteur qui ne
remonte pas, plutôt que par un nettoyage qu'on remet.
[2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) ajoute que sa valeur d'ouverture se mesure, et
que le resserrer est un geste distinct de le poser.

[3540](3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md) dit la limite : un compteur qui
ne monte pas prouve que rien ne s'ajoute, pas que la règle est comprise. C'est pourquoi le garde rend
des suspects et non des fautes.
