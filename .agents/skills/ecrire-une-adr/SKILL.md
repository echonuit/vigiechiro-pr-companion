---
name: ecrire-une-adr
description: Use at closure pass 10, once every decision is taken, and whenever a chantier deliberately overrides an existing decision. One decision, one ADR, and every ADR declares how it is verified.
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

## Fonction de garde

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
