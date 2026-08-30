---
name: clore-un-chantier
description: Use when a chantier or EPIC is finished and must be closed. Orchestrates the twelve closure passes in their mandatory order, states why the order is what it is, and delegates each pass to the skill that owns it.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Clore un chantier

## Loi d'airain

```
LES DOUZE PASSES S'EXÉCUTENT DANS L'ORDRE, ET LAISSENT LEUR TRACE
```

L'ordre n'est pas un rangement, c'est une chaîne de dépendances. Le rompre fait rater ce que la
passe suivante devait trouver.

**La trace n'est pas une formalité de fin.** Une clôture qui n'en laisse aucune est indiscernable
d'une clôture qui n'a pas eu lieu - pour le lecteur, pour le prochain chantier, et pour la mesure.
Le 28 août 2026, **43 des 64 EPIC clos** du dépôt n'en portaient aucune, dont un clos le jour même
par les quatorze passes, avec bilan et artefact. Rien ne l'avait réclamée (#4659).

## Annoncer

« J'utilise la compétence clore-un-chantier pour clore <l'EPIC>, passe par passe. »

## Les quatorze passes

| # | Passe | Ce qu'elle produit | Compétence |
|---|---|---|---|
| 0 | Relecture des ADR existantes | la liste des décisions que le chantier a contredites | `ecrire-une-adr` |
| 1 | Audit d'intégration | ce que `main` a changé pendant le chantier | `auditer-l-integration` |
| 2 | Cohérence CLI et IHM | la commande manquante, ou son absence justifiée | `confronter-les-deux-surfaces` |
| 3 | Doc développeur | `dev-docs/` recolle au code livré | `humaniser` |
| 4 | Doc utilisateur | `docs/` et ses captures | `revoir-les-ecrans`, `humaniser` |
| 5 | Brief projet | parcours, maquettes, modèle conceptuel | `humaniser` |
| 6 | Tests | chaque usage introduit est couvert | `tdd`, `mutation` |
| 6b | Recette préparée | chaque capacité ajoutée a sa case `Sxx-NN`, et la fixture pour la jouer | |
| 7 | Harmonisation | l'application entière, pas le delta | `harmoniser-l-application` |
| 8 | Revue visuelle | chaque **état** de chaque écran touché | `revoir-les-ecrans` |
| 9 | Suites consolidées | l'EPIC des suites relu et regroupé, les rattachements tranchés | `trier-les-issues` |
| 10 | Archivage OpenSpec | les delta specs fusionnées, **puis** le changement sous `archive/` | `openspec-archive-change` |
| 11 | ADR du chantier | une décision structurante, une ADR | `ecrire-une-adr` |
| 12 | Bilan | livré, dette restante, décisions et leur pourquoi, **et la trace collée dans l'EPIC** | `humaniser` |

Quatre passes ne produisent que de la prose, et c'est pourquoi elles nomment `humaniser` : ce
qu'elles écrivent est lu hors de l'échange qui l'a produit, donc l'article A31 s'y applique.

## Pourquoi cet ordre

- La **relecture des ADR** remet l'existant en tête avant qu'on touche à quoi que ce soit.
- L'**audit d'intégration** peut révéler du travail à faire **avant** de documenter.
- La **cohérence CLI** peut révéler une commande à ajouter, que les passes suivantes documenteront
  et testeront.
- L'**harmonisation** peut **casser un écran sans casser un test**, d'où la revue visuelle **juste
  après** elle.
- La **revue visuelle** peut faire émerger de nouveaux chantiers.
- Les **ADR** s'écrivent quand toutes les décisions sont prises.
- Le **bilan** vient en dernier parce qu'il renvoie à tout le reste.

## Rejouer une passe invalide celles qui la balayaient

Une clôture se refait parfois - une passe sautée, une passe faite de trop loin. **L'ordre vaut alors
une seconde fois** : une passe qui en balaye d'autres doit être rejouée **après** elles.

Vécu le 28 août 2026 sur le chantier #4573. Six passes ont été reprises, mais la **10** avait été
relancée avant que les 5 à 8 ne le soient. Elle a balayé des passes qui n'avaient pas encore eu lieu
dans leur forme finale, et n'a rien tiré ni de la 0 ni de la 7 - les deux sources que
[`ecrire-une-adr`](../ecrire-une-adr/SKILL.md) désigne pourtant en premier.

**Elle a rendu deux ADR sur quatre, et cela ressemblait à un succès.** C'est le piège : une passe qui
produit quelque chose paraît avoir abouti. Le contrôle n'est donc pas « la passe a-t-elle rendu
quelque chose », mais **énumérer ce que le chantier a décidé et vérifier que chaque décision a son
fichier**.

Les deux ADR manquantes étaient les plus faciles à défaire : un rembourrage qu'un lecteur retirerait
comme superflu, et deux constantes divergentes qu'il unifierait par souci de cohérence.

## Le delta que TOUTES les passes lisent

> `<sha d'ouverture>..origin/main`, **entier**. Jamais filtré sur les commits du chantier.

C'est l'erreur la plus fréquente de la clôture, et elle est invisible : filtrer donne un delta qui a
l'air juste, plus petit, et plus rapide à relire.

**Le code d'un chantier ne vit pas seul.** Il vit à côté de ce qui a été fusionné pendant qu'il
courait, et c'est cet ensemble-là qui part en production. Une passe qui ne lit que « mes commits »
juge un état qui n'a jamais existé.

Mesuré le 28 août 2026, à la clôture de #4671. La passe 1 y a trouvé **deux planchers périmés**, dont
un - celui de l'arbre de test - **posé par une autre session** pendant le chantier. Un delta filtré
n'aurait montré ni le plancher, ni le fait que mon propre travail l'avait fait monter sans le
verrouiller. Le défaut n'était ni dans mes commits ni dans les leurs : il était dans leur **rencontre**.

```bash
git log --oneline <sha-d-ouverture>..origin/main            # TOUS les commits, toutes sessions
git diff --stat <sha-d-ouverture>..origin/main              # et tout ce qu'ils touchent
```

Cela vaut pour les quatorze passes, pas seulement la 0 : la doc que la passe 3 relit peut avoir été
rendue fausse par un autre chantier, l'écran que la passe 8 regarde peut avoir été déplacé par lui.

## Aucune passe ne suppose

> Une passe se conclut sur une **mesure reproductible**, ou elle ne se conclut pas.

Reproductible veut dire : quelqu'un d'autre lance la même commande et obtient le même nombre. Une
lecture à l'œil, un souvenir, un « ça doit être ça » n'en sont pas - et une clôture est précisément
l'endroit où l'on croit savoir, puisqu'on vient d'écrire le code.

**Un résultat qui surprend se remesure avant d'être cru.** Quatre fois le même jour, la première
mesure était fausse :

| Ce que la mesure disait | Ce qu'elle valait |
|---|---|
| « aucune méthode n'est appelée » | un glob mangé par le shell ; refaite en Python, aucune n'était morte |
| « 0 test exécuté » | des rapports effacés par un `clean` enchaîné, pas un test manquant |
| « verdict=perte, un renvoi a disparu » | un fichier neuf **non indexé**, que `git ls-files` ne voyait pas |
| « cette issue portera le numéro suivant » | le numéro appartenait déjà à un autre chantier, et quatorze renvois pointaient chez lui |

Aucune de ces quatre n'aurait été rattrapée par un garde : toutes rendaient un résultat **plausible**.

## Passe 0 : la question qui décide

> Le chantier a-t-il **contredit** une décision existante, et si oui, l'a-t-il fait exprès ?

Un chantier a parfaitement le droit de dépasser une ADR. Ce qui n'est pas permis, c'est que le
dépassement soit **silencieux** : une ADR qu'on contredit sans le dire laisse deux règles opposées
dans le dépôt, et le prochain lecteur appliquera celle qu'il trouvera en premier.

**La lecture se fait contre `origin/main`**, pas contre la branche : d'autres chantiers ont pu
écrire des ADR pendant celui-ci, et le rebase de la passe 1 arriverait trop tard.

```bash
git log --oneline <sha-d-ouverture>..origin/main -- dev-docs/decisions/
```

**Et la question se pose à l'envers.** Parmi les ADR que le chantier respecte, certaines
régissent-elles du code **hors du delta**, qu'il faudrait aligner ? C'est ce qui a manqué à une
clôture où un garde de surface CLI avait été tenu à jour, mais pas le garde de comportement que la
**même décision** régit.

## Les suites d'une clôture se closent aussi

Les issues consolidées en passe 9 forment un chantier à part entière. Elles se closent par **les
mêmes quatorze passes**, appliquées à leur seul delta. Le dépôt l'a appris trois fois.

La passe 9 ne les **découvre** pas : chacune a été ouverte au moment où elle a été trouvée, et
rattachée à l'EPIC du chantier ou à celui des suites (#4562). Arriver en passe 9 avec une page
blanche est le signe que cette règle n'a pas été tenue, pas que le chantier n'a rien trouvé.

**Ce qu'on y trouve n'est plus une prédiction.** L'EPIC #4671 - les quatre suites de #4573 - a été
clos par les douze passes le 28 août 2026, sur un périmètre de quatre issues **déjà livrées et
fusionnées**. Les trois choses annoncées ci-dessus y étaient, et **trois passes ont produit du code
de production**, les 2, 7 et 8 :

| Ce qui était annoncé | Ce qui a été trouvé |
|---|---|
| une capacité livrée d'un seul côté | `ajouter-point --lat --lon` posait des coordonnées sans contrôler le carré, là où l'écran contrôlait depuis #733 |
| un état visuel sans capture | l'état divergent du contrôle, dont ce chantier avait changé **deux fois** ce qui s'affiche |
| une règle qu'aucune ADR ne porte | deux seuils d'indiscernabilité, écrivant deux fois la même justification |

Un périmètre étroit n'est donc pas une raison d'abréger : c'est ce qui rend la clôture rapide, pas ce
qui la rend inutile.

## La trace : ce que la passe 12 colle dans l'EPIC

Le bilan raconte ; **la case atteste**. Les deux sont nécessaires, et c'est la seconde qu'on oublie
parce qu'elle ne s'écrit pas, elle se coche.

Le modèle vit dans [`dev-docs/cycle-de-chantier.md`](../../../dev-docs/cycle-de-chantier.md), section
« Modèle de clôture (à coller dans l'EPIC) ». Il se colle **en commentaire sur l'EPIC**, cases cochées,
avant de le fermer. Son en-tête `## Clôture de chantier` est ce que le garde cherche : une clôture qui
l'omet ne se distingue d'aucune manière d'une clôture qui n'a pas eu lieu.

**Une passe non tenue se coche quand même, en le disant.** Une case laissée vide sans un mot laisse
croire à un oubli ; une case qui porte « sans objet : aucun écran touché » est une décision, et se
relit comme telle.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je documente d'abord, j'auditerai après » | L'audit peut révéler du code à écrire avant de documenter |
| « L'harmonisation n'a rien cassé, les tests passent » | Elle casse des écrans sans casser de test. C'est pourquoi la revue visuelle suit |
| « Cette ADR ne concerne pas mon delta » | Elle régit peut-être du code hors delta qu'il faut aligner |
| « Je relis les ADR de ma branche » | Contre `origin/main`, sinon vous manquez celles écrites pendant |
| « Les suites, on verra plus tard » | Elles se closent par les mêmes quatorze passes |
| « Le garde est sorti en 0, tout va bien » | Les cinq loupes signalent en rendant `0`. Passe 1 : on **lit** ce que les gardes écrivent |
| « Je refais la passe qui manquait » | Elle en invalide d'autres si elle les balayait. La 10 se rejoue **en dernier** |
| « La capture est produite, la passe 8 est faite » | Elle se **regarde**. Une image peut montrer le bon état et rester invraisemblable |
| « Cette trouvaille aura le numéro suivant » | Un numéro d'issue **supposé** est le numéro de quelqu'un d'autre. On l'ouvre, ou on cite l'EPIC |
| « Je relis le diff de mon chantier » | Le delta est `<ouverture>..origin/main` **entier**. Filtrer cache ce que la rencontre avec les autres sessions a produit |
| « C'est évident, je note et j'avance » | Une passe se conclut sur une mesure **reproductible**. Un résultat qui surprend se remesure avant d'être cru |
| « Le bilan est écrit, la clôture est faite » | Le bilan raconte, la **case** atteste. Sans elle, rien ne distingue votre clôture d'une clôture absente |
| « J'ai fermé la dernière issue, l'EPIC est clos » | Fermer les issues n'est pas clore le chantier. Les quatorze passes portent sur le **delta**, pas sur les tickets |
