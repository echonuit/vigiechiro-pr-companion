---
type: adr
title: "Le cycle décrit aussi le travail, pas seulement sa clôture"
status: stable
article: A26
chantier: "#3505"
decided_at: 2026-08-08
verification: humaine
verification_note: "une méthode de travail ne se teste pas ; le contrôle est la relecture du"
verified:
  - by: human:nedseb
    at: 2026-08-08
---

# Le cycle décrit aussi le travail, pas seulement sa clôture

## Contexte

Le cycle de vie d'un chantier faisait 507 lignes, dont l'essentiel décrivait la **clôture** : dix
passes, leur raison d'être, leurs contre-exemples. Ce qui se passe entre le commentaire de prise d'une
issue et sa pull request n'y tenait pas dix lignes.

Ce vide n'était pas neutre. Il était rempli par une consigne écrite ailleurs, et fausse.

## Ce que le dépôt enseignait

**`dev-docs/ajouter-une-fonctionnalite.md` numérotait « Tester » en étape 8 sur 8.** La page qui apprend
à écrire une fonctionnalité plaçait donc le test en dernier, et le mot « TDD » n'apparaissait nulle part
dans le dépôt.

Le dépôt tenait pourtant déjà la moitié de la règle, sous un autre nom : *un garde-fou de non-régression
se vérifie en le voyant rouge* ([ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)). Le
rouge du TDD est cette même exigence, déplacée **avant** le code, là où elle est gratuite. Après coup,
elle se paie : sur #3483, la règle ArchUnit qui interdit à un outil de capture de lire l'horloge a dû
être vérifiée en **réintroduisant le défaut à la main**, en relançant, puis en restaurant.

## Décision

Le cycle décrit désormais **trois moments**, et non plus un seul.

### 1. Pendant l'issue : une boucle, pas une étape

Rouge, vert, refactor est un **tour**, répété à chaque **petit pas** jusqu'à ce que le comportement soit
complet. Une issue en compte des dizaines. Un petit pas est le plus petit **fait observable** qu'on
puisse rendre rouge puis vert, pas la fonctionnalité.

Le signal que le pas était trop grand est simple et se lit sans discussion : **le rouge dure**. On
revient alors au dernier vert et on coupe en deux.

**Le REFACTOR est la troisième phase de chaque tour**, pas une étape de fin d'issue. C'est le point qui
demandait d'être écrit, parce que la seule mention du refactoring vivait en **passe 7**, c'est-à-dire au
moment où l'auteur a quitté le contexte. Deux échelles, qui ne se croisent pas :

| | REFACTOR du tour | Passe 7 |
| --- | --- | --- |
| Portée | le pas qu'on vient de faire | l'application entière |
| Filet | le test qu'on vient de rendre vert | la suite complète |
| Décision | seul | discutée |

Sans cette frontière, « refactorer souvent » entre en conflit avec la règle des petites PR et l'une des
deux cède.

### 2. Quand la boucle s'arrête : la mutation

PIT tourne **dès qu'un comportement est complet**, sur les classes pures que l'issue a livrées - et non
à la clôture du chantier, où le trou découvert porte sur du code livré depuis plusieurs pull requests.
La passe 6 devient une **vérification que ça a été fait**.

Précision qui évite le contresens inverse : **pas à chaque tour**. PIT est lent, la boucle doit rester
de l'ordre de la minute.

### 3. À la clôture de l'issue : deux textes qui se relisent à froid

Le **corps** d'une issue porte l'**état courant de la vérité** ; les **commentaires** portent le
**journal**. Tout commentaire qui change la lecture de l'issue est **suivi d'une édition du corps**.

C'est la généralisation d'une règle qui n'existait qu'au triage (« un recadrage laissé en commentaire
sous un corps périmé ne recadre rien »), là où elle ne couvrait que les issues qu'on **déplace** d'un
chantier à l'autre.

Et avant de fusionner : le corps de la pull request et celui de l'issue se lisent-ils dans six mois,
**sans** le fil ? Le titre de la pull request devient le sujet du commit de squash, et son corps est ce
qu'atteint quiconque remonte depuis `git log`.

## Les deux passes d'ADR, et pourquoi il en fallait deux

L'écriture des ADR vivait en **passe 3**. C'était chronologiquement impossible : les passes 4 à 9
**produisent** des décisions.

**Mesure.** Les cinq ADR du chantier #3151 - [3406](3406-une-nuit-porte-le-fuseau-de-son-site.md),
[3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md),
[3450](3450-une-propriete-de-fuseau-se-tient-en-rejouant-pas-en-relisant.md),
[3451](3451-un-invariant-tenu-par-la-base-se-double-d-un-refus.md),
[3483](3483-un-apercu-pose-ce-qu-il-ne-peut-pas-reproduire.md) - portent **toutes** la mention « suite
de ». Aucune n'est née là où le cycle la demandait : 3439 est sortie de la **revue visuelle** (passe 8),
3483 d'une trouvaille faite en retirant ce que 3439 masquait. Un cycle qui exige une chose impossible
obtient qu'on l'ignore.

Donc :

- **passe 0, en tête de clôture** : relire les ADR **existantes**, contre `origin/main`. Un chantier a
  le droit de **dépasser** une décision - #3442 a rendu faux ce que l'ADR 3406 assumait sur l'outre-mer -
  mais pas de le faire en silence, sinon deux règles opposées cohabitent et le prochain lecteur applique
  la première trouvée ;
- **passe 10, en queue** : écrire les ADR du chantier, en balayant les passes 0 à 9. Le **bilan** devient
  la passe 11, parce qu'il **renvoie** aux ADR et ne peut pas renvoyer à ce qui n'est pas écrit.

⚠️ Une décision **de ne pas faire** est une décision, et c'est celle qu'on oublie : elle ne laisse pas de
code derrière elle.

## ⚠️ Pourquoi les passes 1 à 9 ne changent PAS de numéro

C'est la contrainte qui a dicté la forme de la solution, et elle a été **mesurée avant** de choisir.

Le dépôt compte **42 citations** de passes numérotées, dont **35 dans `dev-docs/decisions/`** - des
documents que la règle déclare **immuables**. L'ADR 0014 cite « passe 2 » trois fois ; cinq ADR citent
« passe 7 » ; quatre citent « passe 8 ». Décaler la série les rendrait toutes fausses, sans que rien ne
rougisse, et sans qu'on ait le droit de les corriger.

Les deux passes ajoutées se placent donc **aux extrémités** : 0 en tête, 10 et 11 en queue. La casse
tombe à **4 citations**, toutes dans des documents **vivants** (`decisions/index.md`, `CONTRIBUTING.md`,
la page du cycle deux fois).

Les quatre ADR qui déclarent « Rédigée à la clôture (passe 3) » sont **datées** : elles disent vrai du
cycle en vigueur ce jour-là, et n'ont pas à changer. Une ADR immuable n'est pas une ADR éternellement
exacte sur son environnement ; c'est un enregistrement daté.

Le « 0 » n'est pas un pis-aller de numérotation : l'ouverture d'un chantier a **déjà** son étape 0, et
c'est déjà *relire l'existant avant d'agir*. La même figure aux deux bouts se lit sans explication.

## Deux ajouts venus du même chantier

**La documentation que le chantier PÉRIME.** La passe 3 disait « mettre à jour pour que la doc colle au
code », ce qui se lit comme *qu'ai-je à ajouter ?*. Le mode de panne est l'inverse : une page décrivant
fidèlement un mécanisme **remplacé** ne rougit nulle part et se lit comme vraie. Mesuré : #3439 a
remplacé les masques d'aperçus écrits à la main par des rectangles dérivés de la scène, et
`dev-docs/captures.md` a continué **une semaine** à décrire « seize » fichiers énumérés dans un script,
en qualifiant de « non élucidée » une instabilité que le même chantier venait d'élucider. L'instrument
est mécanique : partir des **fichiers touchés** et chercher **qui les cite**.

**Le « vu rouge » ne couvrait que les tests.** Il vaut à l'identique pour un **job d'intégration
continue** ou un **script**, là où le faux vert se voit le moins parce que personne ne relit un job qui
passe. Le job `fuseau-alternatif` de #3450 passait le fuseau par `-D` sur la ligne Maven, que les forks
surefire n'héritent pas : il aurait été vert en rejouant la suite dans le fuseau du runner.

## Ce que cette ADR apprend au-delà de son cas

**Une méthode écrite est appliquée telle qu'elle est écrite, y compris là où elle ne dit rien.** Le
cycle ne demandait pas d'écrire les tests en dernier ; il se taisait, et une numérotation d'anatomie
lue comme une chronologie a tenu lieu de règle. Un silence dans un document normatif n'est pas une
absence de consigne : c'est une consigne que personne n'a choisie.

Et une règle qu'on ne peut pas tenir ne se tient pas : elle se contourne, silencieusement, jusqu'à ce
qu'on mesure - ici, cinq ADR sur cinq écrites hors du moment prévu.
