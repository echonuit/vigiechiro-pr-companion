# ADR 2753 - Une règle absente et assumée vaut mieux qu'une règle contournée

- **Statut** : Accepté - 2026-08-06
- **Chantier** : #2753, lot #2725 du chantier #2720
- **Vérification** : humaine - aucune règle technique n'est posée, c'est précisément l'objet de la
  décision ; la case du gabarit de PR se lit, elle ne se mesure pas

## Contexte

Ce dépôt a **un mainteneur**. Mesuré sur les quarante dernières PR : 25 fusionnées par une personne,
15 par un bot. `CODEOWNERS` se réduit à `* @nedseb`.

⚠️ Et une mesure a recadré le constat d'origine. L'issue supposait que les chemins sensibles
« fusionnent avec la même revue que le reste ». En réalité :

```
$ gh api repos/echonuit/vigiechiro-pr-companion/branches/main/protection
Branch not protected (HTTP 404)
```

**Aucune** protection. Ni revue requise, ni check requis. La CI est verte au moment des fusions parce
qu'on l'attend, pas parce qu'une règle l'impose.

## Décision

### 1. On n'installe aucune protection de branche, et on l'écrit

Une seconde approbation humaine **n'existe pas** à effectif constant. Poser une règle qui exige un
approbateur revient à la contourner soi-même chaque semaine - et une règle qu'on contourne apprend
qu'on peut contourner les règles.

Rendre les checks obligatoires aurait été moins absurde, mais aurait coûté en friction quotidienne
pour un risque faible chez un mainteneur qui attend déjà le vert. La décision est donc de **ne rien
poser** et de **le documenter**, dans [Reprendre le dépôt](../reprendre-le-depot.md) comme ici : un
repreneur doit savoir qu'il n'y a pas de garantie de fusion, et que **c'est la CI qui tient ce dépôt,
pas une règle**.

### 2. Les chemins sensibles portent une case, pas un garde

`db/migration/**`, `ServiceSauvegarde` et `.github/workflows/**` sont les endroits où une erreur ne se
rattrape pas : une migration jouée, une sauvegarde perdue, un workflow qui ne s'exercera qu'une fois
par semaine. Le gabarit de PR y consacre une section - avec « sans objet » comme réponse valide.

Elle ne bloque rien, et ce n'est pas un demi-remède : elle se lit **au moment où l'on agit**, ce qu'une
protection de branche ne fait pas.

⚠️ **Écarté : un garde automatique** qui aurait exigé une preuve dans le corps de la PR. Il aurait été
plus dur à contourner, mais serait devenu un **dispositif de plus à surveiller**, avec son autotest et
sa non-vacuité - exactement ce contre quoi
[l'ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md) met en garde. À un mainteneur, le
coût de surveillance dépasse le gain.

### 3. Le bus factor se traite par l'écrit, pas par l'organisation

On ne peut pas créer un second mainteneur ; on peut écrire ce qu'il faut savoir pour reprendre.
[Reprendre le dépôt](../reprendre-le-depot.md) liste les secrets et leur usage, les accès extérieurs,
et les **gestes manuels récurrents** - le seul contenu que ni le code ni la CI ne révèlent.

## Conséquences

- **assumé** : une PR peut être fusionnée sur rouge, ou sans relecture. Rien ne l'empêche
  techniquement, et l'écrire vaut mieux que de le laisser croire impossible ;
- la case des chemins sensibles **vieillira** si personne ne la coche honnêtement. C'est le risque de
  tout dispositif déclaratif, et il est préféré au risque d'un garde qu'on cesserait de lire ;
- si le projet gagne un second mainteneur, **cette ADR est la première à rouvrir** : son unique
  prémisse est l'effectif.

### Ce que cette décision ne dit pas

Elle ne dit pas que la protection de branche est inutile en général. Elle dit qu'à **un** mainteneur,
la friction est immédiate et le bénéfice hypothétique. C'est un arbitrage d'effectif, pas de principe.
