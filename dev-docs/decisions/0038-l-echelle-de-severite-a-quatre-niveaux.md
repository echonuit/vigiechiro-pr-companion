# ADR 0038 — L'échelle de sévérité compte quatre niveaux, et son ordre de déclaration porte la sémantique

- **Statut** : Accepté — 2026-07-19
- **Chantier** : EPIC #1990 / sous-EPIC #2004 (#2045, #2050)
- **Vérification** : certaine — `DecisionsRespecteesTest#l_echelle_de_severite_a_quatre_niveaux_dans_l_ordre`
- **Applique** : [ADR 0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md) point 5, dont elle fournit le garde-fou.
- **Amendée le 2026-07-20** (#2159) : le contexte ci-dessous était écrit depuis **une** des deux échelles de sévérité de l'application. Voir « Ce que cette ADR ignorait ».

## Contexte

`RetourOperation.Severite` comptait trois niveaux : `SUCCES`, `INFO`, `ERREUR`.

Un **avertissement** n'y trouvait pas sa place. L'opération a abouti, mais quelque chose mérite l'attention : une nuit déjà importée qu'on réimporte quand même, un dossier mélangeant deux enregistreurs, un numéro de passage déjà pris. Ce n'est ni une information neutre, ni un échec.

On aurait pu croire que ces cas avaient été **rabattus** sur `ERREUR` ou `INFO` faute de mieux. La revue des adoptants (#2050) montre l'inverse, et c'est plus instructif : ils ont **quitté le type**.

Huit propriétés — neuf, une neuvième ayant été trouvée après coup — portaient leur sévérité sous forme de « ⚠ » écrit dans une chaîne libre, hors de `RetourOperation` :

```java
avertissement.set("⚠ Le passage n° " + numero + " existe déjà pour ce point en " + annee + …);
```

Une fois dehors, plus rien ne bornait leur forme. Trois d'entre elles y joignaient des **listes entières** ; une quatrième réduisait une collection à un booléen (« un ou plusieurs numéros sont déjà utilisés », alors que la boucle savait lesquels).

Le besoin était par ailleurs déjà inscrit **dans la feuille de style** avant de l'être dans le type : `importation.css` définissait `.insp-avertissement` en ambre à côté de `.insp-incoherence` en rouge. La distinction existait, elle ne pouvait simplement pas se dire.

## Ce que cette ADR ignorait

Le contexte ci-dessus dit qu'un niveau `AVERTISSEMENT` **manquait**. C'était vrai de
`RetourOperation.Severite`, et **faux de l'application** : `audit.model.SeveriteConstat` portait
`ERREUR`, `AVERTISSEMENT`, `INFO` depuis sa création.

Deux échelles coexistaient donc, l'une en `model`, l'autre en `viewmodel`, sans conversion, sans renvoi
documentaire, sans mention de l'une dans l'autre. Cette ADR a été écrite depuis un seul des deux angles,
faute d'avoir cherché l'autre — et son auteur venait pourtant de passer un chantier entier à traquer
exactement ce motif : **deux vocabulaires pour un concept**.

Découvert le lendemain, en préparant le lot 3 de #2036 : `VerdictCarre` devait porter sa sévérité, et le
choix fait pour lui décidait laquelle des deux échelles faisait autorité.

**Ce que la décision devient.** #2159 a posé `commun.model.Severite` comme échelle unique : les deux
précédentes s'y adossent, et plus aucun `model` ne cite `viewmodel`. Les quatre points ci-dessous
restent entièrement valables — ils portaient sur la **forme** de l'échelle (ses niveaux, son ordre, ses
glyphes), pas sur sa localisation.

**Ce que ça n'invalide pas.** Le mécanisme décrit — huit propriétés sorties du type faute de pouvoir s'y
ranger — est exact et documenté par #2050. Le niveau manquait bien *là où elles vivaient*.

**La leçon, qui vaut au-delà de ce cas.** Chercher ce qui manque à un type demande de chercher d'abord
s'il est seul de son espèce. Une analyse menée depuis un seul angle produit un constat vrai et une
conclusion incomplète — et rien, dans le constat, ne signale qu'il manque un angle.

## Décision

**1. `AVERTISSEMENT` s'insère entre `INFO` et `ERREUR`.** L'opération a abouti ; quelque chose mérite l'attention sans être un échec.

**2. L'ordre de déclaration porte la sévérité.** `CompteRendu.severite()` prend le maximum par `ordinal()` : la position des constantes détermine quel constat qualifie un compte rendu entier. Réordonner l'énumération changerait ce comportement **sans qu'aucun test existant ne rougisse** — `SeveriteTest` l'épingle donc explicitement.

**3. L'erreur cède le triangle et prend le cercle barré.** `fas-exclamation-triangle` est le glyphe usuel de l'avertissement. Deux niveaux distincts qui partagent une forme ne se distinguent plus quand la couleur manque, ce qui viderait de son sens la promesse de l'ADR 0035 point 5. `fas-times-circle` était déjà employé pour `ECHEC` dans `CelluleProgressionUnite` : le vocabulaire s'harmonise au lieu de se compliquer.

**4. Une seule table de glyphes.** `IconesSeverite` est la source unique, partagée par `BandeauRetour`, `VueCompteRendu` et `LibelleRetour`. Recopier la table aurait laissé trois surfaces libres de diverger — c'est le motif que l'ADR de #1974 a passé une PR à corriger sur les classes CSS, et qu'on a refait deux fois pendant ce chantier.

**5. La sévérité est refusée en tête de message.** Le constructeur compact de `RetourOperation` rejette un texte ouvrant par `⚠ ✓ ✗ ❌ ⛔ ✅ ❗`. C'est le garde-fou de l'ADR 0035 point 5 : sans lui, rien n'empêche un futur message de remettre le marqueur dans la chaîne, et le cycle recommence.

La garde ne mord qu'en **tête** : un glyphe au milieu d'une phrase illustre, il n'usurpe pas la sévérité. Et elle ne couvre **que** ce type — les propriétés `String` ad hoc restent libres, ce que sa Javadoc dit explicitement. Une garde qui laisserait croire qu'elle protège plus large serait pire que pas de garde.

Conformément à l'ADR 0035 point 6, elle ne concerne pas la CLI : un terminal ne rend pas de `FontIcon`, le caractère y est le seul moyen d'écrire un avertissement.

## Conséquences

L'icône d'erreur change dans toute l'application. C'est visible et assumé : le triangle disait « attention » là où il fallait dire « raté ».

Les neuf propriétés hors du type reviennent une par une (#2050). Quatre l'ont fait ; les autres sont soit des **états** — qui relèvent de l'ADR 0028 et non de ce type — soit sur un écran en refonte.

## Ce que la mise en œuvre a appris

Le niveau manquant ne s'est pas signalé par des messages mal classés, mais par une **fuite hors du système**. Quand un type ne sait pas exprimer un cas, le cas ne se plie pas au type : il en sort, et ce qui l'en empêchait cesse de s'appliquer. Chercher les niveaux manquants d'une énumération revient donc à chercher ce qui a **contourné** l'énumération, pas ce qui s'y est mal rangé.

Et — leçon ajoutée par l'amendement — la fuite ne va pas toujours vers des chaînes libres. Elle peut aller vers **une seconde énumération**, dans une autre couche, qui règle le même problème sans le savoir. Celle-là ne se trouve pas en cherchant des glyphes : elle se trouve en cherchant des **synonymes**.
