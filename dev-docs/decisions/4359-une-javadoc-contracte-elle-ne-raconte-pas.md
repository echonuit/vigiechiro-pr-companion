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
ratchet: 1533
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

**Le seuil dépend de ce que le bloc surmonte**, et le compte est en **lignes au-delà**. Un seuil
unique mesurait mal : c'est dans la javadoc de classe que le pourquoi a sa place, et un seuil taillé
pour une méthode l'y comptait comme de la dette.

| nature | blocs | médiane | 9ᵉ décile | seuil |
|---|---:|---:|---:|---:|
| type (classe, record, enum, interface) | 1 411 | 7 | 14 | **15** |
| méthode | 4 809 | 2 | 5 | **8** |
| champ | 836 | 2 | 3 | **8** |
| constante d'enum | 127 | 1 | 3 | **8** |
| autre | 38 | 2 | 4 | **8** |

Chaque seuil est posé **au-dessus du 9ᵉ décile de sa nature** : il laisse passer le régime normal du
dépôt et ne signale que ce qui en sort. Un seuil de 8 sur les types signalait **495** blocs ici,
contre **104** au seuil de 15 : quatre blocs sur cinq relevaient du régime normal d'une classe.

Ces seuils viennent de la ligne d'origine. Ils sont repris parce que la distribution mesurée **ici**
les confirme - 9ᵉ décile à 14 pour les types, à 5 pour les méthodes - et non parce qu'ils y étaient.
Deux corpus indépendants tombent au même endroit.

Le grain de la **ligne** a été choisi après coup : au grain du bloc, un bloc de 50 lignes réécrit en
22 ne bougeait pas le compte, ce qui poussait à couper du **contrat** pour passer sous le seuil. Le
cliquet descend par tranches, chaque bloc **lu**.

## Conséquences

**Les deux arbres Java**, production et tests. Le cliquet s'est d'abord borné à la production, au
motif qu'« un garde doit dire ce qu'il vérifie, c'est l'article A2, et sa javadoc est cette
déclaration ».

**Ce motif attribuait à A2 ce qu'il ne dit pas.** A2 énonce qu'un garde est vu rouge sur sa propre
mutation, et sa seule clause d'exclusion est étroite : « un détecteur textuel s'exclut de son propre
corpus » - le garde des cadratins ne compte pas les cadratins de son source. Rien n'y exempte la
javadoc d'un garde de la discipline de longueur, et rien n'y parle du code de test. **A2 n'est donc
pas en cause, son usage l'était.**

Un commentaire de classe de test se lit comme un autre et vieillit pareil. L'exclure du compte
revenait à dire qu'il n'a pas à être juste, et la maîtrise de la dette vaut pour les tests comme
pour le reste. Les distributions des deux arbres sont d'ailleurs les mêmes - 9ᵉ décile à 14 pour les
types, à 5 pour les méthodes - donc les mêmes seuils, sans réglage propre aux tests.

Les gardes écrits en Java, `ContrasteAATest`, `DoublonsFeuillesDeStyleTest`,
`DocumentationAJourTest`, gardent leur déclaration entière : c'est du **contrat**, et le registre
`4359-blocs-relus.tsv` est fait pour l'inscrire une fois lue. Une exception qui **sort du corpus**
est un angle mort ; une exception **inscrite** reste comptée et se relit.

**Le niveau est `probable`.** La longueur se compte ; ce qu'il faut couper ne se décide pas
mécaniquement. Un record de trente champs n'est pas un suspect : les étiquettes de contrat sont
exclues, avec leurs suites, et deux cas du banc le tiennent.

**Le cliquet a ouvert à 3 641**, sur 713 blocs dans 572 fichiers. Il vaut **<!--inv:cliquet-javadoc-->1 533<!--/inv-->** sur 669 blocs
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
