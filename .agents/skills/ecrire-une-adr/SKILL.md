---
name: ecrire-une-adr
description: Use at closure pass 0 to re-read the existing decisions and find what the chantier contradicted, and at pass 11 to write what it decided. One decision, one ADR, and every ADR declares how it is verified.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Écrire une ADR

## Loi d'airain

```
UNE ADR DÉCLARE COMMENT ELLE EST VÉRIFIÉE
```

Une décision dont rien ne dit comment on saurait qu'elle est respectée n'est pas une décision, c'est
une intention. Un garde fait rougir la CI si la mention manque, ou si le test nommé n'existe pas.

## Annoncer

« J'utilise la compétence ecrire-une-adr pour <la décision>. »

## Qu'est-ce qui mérite une ADR

Une **décision structurante** : un choix d'architecture ou de domaine qu'un développeur futur
pourrait raisonnablement remettre en cause faute d'en connaître les raisons. Une par décision,
immuable, expliquant le **pourquoi**.

**Une décision de ne pas faire est une décision.** « On garde la dépendance aux tuiles
OpenStreetMap », « on n'ajoute aucune protection de branche », « ce territoire reste hors de la
table de dérivation ». Ce sont des ADR, et **ce sont celles qu'on oublie**, parce qu'elles ne
laissent pas de code derrière elles.

## Une par décision, immuable, et numérotée comme son issue

**Le numéro de l'ADR est celui de l'issue** qui a porté la décision. Cela évite d'inventer une
numérotation parallèle, et rend le chemin de la décision au travail qui l'a produite lisible dans les
deux sens.

Une par décision : deux décisions dans un fichier se renversent ensemble le jour où l'une seule
change.

## Les trois niveaux de vérification

| Niveau | Ce qu'il exige | Ce qu'il nomme |
|---|---|---|
| `certaine` | un test ou script **déterministe** | son identifiant exact |
| `probable` | un script de suspects | le script **et son cliquet** |
| `humaine` | le motif de non-mécanisation | et, au besoin, une loupe |

Le niveau se déclare en en-tête, au même titre que le statut et le chantier. Un niveau `certaine`
sans référence nommée est refusé.

La règle vient de l'[ADR 2465](../../../dev-docs/decisions/2465-une-adr-declare-comment-elle-est-verifiee.md),
et se pose dans les champs de l'en-tête OKF :

| Champ | Ce qu'il porte |
|---|---|
| `verification:` | `certaine`, `probable` ou `humaine` |
| `enforced_by:` | le test ou le script qui **refuse**, pour `certaine` et `probable` |
| `ratchet:` | le cliquet, que `probable` exige en plus |
| `loupe:` | pour `humaine`, à la place d'un applicateur |

**Une vérification `humaine` déclare une loupe, jamais un `enforced_by:`.** Y nommer un script
prétendrait qu'il refuse, quand il ne fait que relever, et un garde qui promet plus qu'il ne tient
emprunte la solidité de ses voisins.

## Deux moments, et le second n'est pas le premier

Cette compétence sert **deux passes** de la clôture, et elles ne demandent pas le même geste.

| Passe | Le geste |
|---|---|
| **0** · en tête de clôture | **relire** l'existant, et chercher ce que le chantier a contredit |
| **11** · après la 10 | **écrire** ce que le chantier a décidé |

Les deux sont les deux bouts d'une même conversation avec le corpus : ce que la passe 0 trouve
d'un dépassement délibéré, la passe 11 l'écrit des deux côtés. C'est pourquoi elles vivent ici
ensemble, mais leurs étapes sont distinctes et ce qui suit les sépare.

## Passe 0 : la fonction de garde de la relecture

```
0. METTRE A JOUR le graphe du depot : graphify update .
1. RELIRE    la PROMESSE de l EPIC, et la confronter a ce qui a atterri.
             Un « non » suspend la cloture : voir plus bas.
2. LISTER    les ADR apparues PENDANT le chantier, contre origin/main.
3. POSER     la question qui decide : le chantier a-t-il CONTREDIT une decision
             existante, et si oui l a-t-il fait expres ?
4. POSER     la meme question A L ENVERS : parmi les ADR que le chantier
             RESPECTE, certaines regissent-elles du code HORS du delta qu il
             faudrait aligner ?
5. ECRIRE    tout depassement delibere. Il deviendra une ADR en passe 11.
```

### La promesse, et ce qu'on fait d'un « non »

> L'EPIC promettait ceci. Est-ce livré ? Ce qui ne l'est pas, l'a-t-on **décidé**, ou **oublié** ?

Le cycle demande partout ce qui a été **livré**, et nulle part si ce qui était **promis** l'a été. Le
bilan de la passe 12 liste ; il ne confronte pas. Une promesse à moitié tenue produit donc un bilan
juste et une clôture fausse.

**La promesse se lit sous la forme qu'elle a prise.** Le « fini quand » de chaque lot est la
meilleure, quand il est là. Il ne l'est pas souvent : sur 70 EPIC clos, **3** en portent un. À
défaut, on confronte le corps de l'EPIC, sa section « ce que porte cet EPIC », sa liste de lots.

**Un « non » suspend la clôture**, et il n'y a que deux issues :

- **livrer** ce qui manque, la clôture reprenant ensuite ;
- **décider de ne pas livrer**, ce qui est une décision de ne pas faire, donc une ADR de la passe 11,
  et ce que le lot devient se dit.

Ce qui n'est pas permis est la troisième voie, celle qu'on prend par défaut : clore en laissant la
promesse à moitié tenue sans que personne ne l'ait décidé.

**Vécu à la clôture de #4925**, le jour où la question a été écrite. L'EPIC promettait trois lots,
deux étaient livrés, le troisième n'avait jamais été découpé. Trois autres dispositifs le
déclaraient prêt : les compétences en place, le tableau complet, cinq gardes verts. Le lot 3 est
sorti en EPIC #4937, par décision.

### Le graphe d'abord, parce que la passe s'en sert

```bash
graphify update .
```

La passe 0 cherche ce qui est apparu pendant le chantier. Un graphe périmé oriente cette recherche à
côté, et il vieillit sans le dire.

Mesuré le 30 août 2026 : le graphe datait de vingt heures et rendait « Passe 9 · identification des
nouveaux chantiers » à la ligne 843, un titre et un emplacement que la passe 9 n'avait plus depuis le
matin même. La règle générale vit dans le `CLAUDE.md` du dépôt parent et n'avait pas été tenue de la
journée.

Il est en tête de la **première** passe, ce qui le rend acquis pour les treize suivantes sans le
répéter. Les passes 3 et 7 interrogent le graphe elles aussi, et lisent alors un état à jour.

### La lecture se fait contre `origin/main`, jamais contre la branche

```bash
git log --oneline <sha-d-ouverture>..origin/main -- dev-docs/decisions/
```

D'autres chantiers ont pu écrire des ADR pendant celui-ci. Le rebase de la passe 1 arriverait trop
tard pour les découvrir, et ce sont justement les plus susceptibles d'avoir été ignorées : elles
n'existaient pas quand le plan a été écrit.

### Un dépassement est permis, un dépassement silencieux ne l'est pas

Un chantier a le droit de dépasser une ADR : #3442 a rendu faux ce que l'ADR 3406 assumait sur
l'outre-mer, et c'était le progrès attendu.

Ce qui n'est pas permis, c'est que le dépassement soit **tu**. Une ADR qu'on contredit sans le dire
laisse deux règles opposées dans le dépôt, et le prochain lecteur appliquera celle qu'il trouvera en
premier.

### La question à l'envers, celle qu'on ne pose pas

Parmi les ADR que le chantier **respecte**, certaines régissent-elles du code **hors du delta** qu'il
faudrait aligner ?

C'est ce qui a manqué à une clôture où `cli-surface.bats`, le garde de surface, avait été tenu à
jour, mais pas `cli.bats`, le garde de comportement que la **même** décision régit. Le chantier ne
contredisait rien ; il avait laissé une moitié derrière lui.

Cette question a produit du travail à trois clôtures du 30 août 2026 : un appel de garde jamais
éprouvé, six commandes citées sans avoir été lancées, et quatre autres le lendemain. Elle n'est pas
décorative.

## Passe 11 : la fonction de garde de l'écriture

```
1. BALAYER    les passes 0 a 10 et poser pour chacune la meme question :
              une decision a-t-elle ete prise ici, qu un lecteur futur pourrait
              defaire faute d en connaitre la raison ?
2. ECRIRE     une ADR par decision. Le contexte, la decision, ses consequences.
3. DECLARER   le niveau de verification et ce qui le tient.
4. CHAINER    si le chantier a depasse une ADR existante, l ecrire des deux cotes.
5. RELIRE     a la grille de la competence humaniser avant de commettre. Une ADR est de la prose
              visible au sens de l article A31, et c est ici qu elle s ecrit.
```

## Pourquoi en passe 11, et pas en passe 3

Parce que **les passes 4 à 9 produisent des décisions**, et qu'on ne peut pas écrire en début de
clôture ce qu'on n'a pas encore décidé.

Le constat est mesuré, pas supposé : les cinq ADR d'un même chantier portent **toutes** la mention
« suite de », c'est-à-dire qu'aucune n'est née à l'endroit où le cycle les demandait. L'une est
sortie de la **revue visuelle**, une autre d'une trouvaille faite en retirant ce que la première
masquait.

**Un cycle qui exige une chose impossible obtient qu'on l'ignore.**

Les cinq ADR du chantier #3151, les 3406, 3439, 3450, 3451 et 3483, portent **toutes** la mention
« suite de » : aucune n'est née à l'endroit où le cycle les demandait. L'ADR 3439 est sortie de la
revue visuelle, et l'ADR 3483 d'une trouvaille faite en retirant ce que 3439 masquait.

**Un cycle qui exige une chose impossible obtient qu'on l'ignore.**

## Les trois sources qui reviennent

- la **passe 0**, quand le chantier a délibérément **dépassé** une ADR existante : le dépassement
  s'écrit, sinon deux règles opposées cohabitent dans le dépôt ;
- la **passe 7**, où un refactoring de conceptualisation tranche presque toujours quelque chose ;
- la **passe 8**, où l'on découvre ce qu'aucun test ne dit.

## Ce qu'il faut lancer, plutôt que de le découvrir en CI

Une ADR neuve doit atteindre son lecteur, et deux dispositifs le vérifient, à des moments différents.

```bash
python3 scripts/adr/verifie_okf.py            # refuse, neuf formes dont l'atteignabilité
python3 scripts/methode/matrice-constitution.py --verifie   # dit si la matrice est périmée
python3 scripts/methode/matrice-constitution.py             # la régénère
```

`verifie_okf.py` refuse une ADR **absente de `dev-docs/decisions/index.md` ou de `mkdocs-dev.yml`**,
sous le motif « atteignabilité ». Ce n'est pas théorique : les ADR 4649 et 4829, écrites le 30 août
2026, ont été refusées tant qu'elles ne figuraient pas aux deux endroits.

La **matrice de la constitution** n'est pas dans ce garde. Elle se régénère, et son contrôle le dit
plutôt qu'il ne le corrige.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « On a décidé de ne rien faire, donc pas d'ADR » | C'est la catégorie qu'on oublie le plus |
| « Vérification : certaine » sans rien nommer | Refusé. `certaine` nomme un test déterministe |
| « J'écris les ADR au début de la clôture » | Les passes 4 à 9 produisent des décisions |
| « Cette ADR contredit l'ancienne, tant pis » | Deux règles opposées cohabitent, et le lecteur prend la première trouvée |
| « La décision est évidente » | Évidente aujourd'hui pour vous. C'est exactement le cas que l'ADR sert |
