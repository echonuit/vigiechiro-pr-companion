---
type: adr
title: "Un point de passage garantit l'ENSEMBLE, jamais la présence d'un de ses membres"
status: stable
article: A16
chantier: "#3978 et #3985, passes 0 et 7 de la clôture des suites des finitions de recette (#3424)"
decided_at: 2026-08-18
verification: certaine
enforced_by:
  - "HabillageTest#poser_garantit_le_trio_meme_si_base_est_deja_la"
verified:
  - by: machine:ci
    at: 2026-08-18
relations:
  prolonge: ["3374"]
---

# Un point de passage garantit l'ENSEMBLE, jamais la présence d'un de ses membres

## Contexte

L'[ADR 3374](3374-une-fenetre-porte-son-habillage-ou-elle-n-est-pas-le-produit.md) a posé un point de
passage unique, `Habillage`, qui donne à toute fenêtre le **trio** du chrome : `palette.css`,
`base.css`, `design.css`. Elle a écarté explicitement le remède par déclaration dans chaque FXML :

> Ajouter `@base.css` aux dix FXML aurait marché, et **se serait défait au onzième**.

`poser` vérifiait pourtant **une seule** feuille pour décider si le travail était fait :

```java
if (surLaRacine.contains(base) || surLaScene.contains(base)) {
    return;
}
```

Vrai de `MainView.fxml`, qui déclare les trois. **Faux d'`EcranReglages.fxml`**, seul FXML du dépôt à
déclarer `palette + base` sans `design`. Mesuré :

| Racine | `design.css` après `poser` |
|---|---|
| déclare `palette` + `base` | **absente** |
| nue | présente |

## Ce que le défaut a coûté, et pourquoi il est instructif

**Il a produit un diagnostic faux, et un correctif qui contredisait l'ADR 3374 en silence.**

Le symptôme visible était un bouton primaire rendu en gris dans la galerie. On en a conclu à un défaut
du **produit**, et on a ajouté `@design.css` au FXML - la route que 3374 écarte.

C'était faux : `NavigationReglages.ouvrir()` **empile** la vue dans la zone centrale de `MainView`, dont
elle hérite les feuilles. **L'utilisateur a toujours vu le produit.** Seule la capture, qui monte
l'écran **seul**, rendait des contrôles nus - le cas même que l'ADR 3374 existe pour fermer.

La leçon n'est donc pas « il manquait une feuille » mais : **un aperçu ne prouve rien du produit tant
qu'on n'a pas vérifié comment l'écran naît en production.**

## Décision

**1. Un point de passage garantit ce qu'il promet en entier.** `poser` vérifie et complète le trio,
quelle que soit la forme d'entrée : liste vide, `palette` seule, `palette + base`, ou `design` seule.
La présence d'un membre ne vaut jamais preuve de l'ensemble.

**2. La déclaration dans le FXML reste écartée.** Celle ajoutée à `EcranReglages.fxml` a été retirée,
et le cliquet qui l'accompagnait avec elle : il exigeait des vues ce que l'ADR 3374 leur refuse. Un
garde qui impose l'inverse de la règle est pire qu'aucun garde.

**3. Le garde vit sur le point de passage, pas sur ses appelants.** C'est ce qui distingue ce
correctif du précédent : vérifier que 24 FXML déclarent une feuille, c'est garder la copie ; vérifier
que `poser` rend le trio, c'est garder la règle.

## Ce que la forme du défaut apprend, et qui se généralise

C'est la **troisième** occurrence du même motif sur cette classe, chaque fois d'un cran plus fin :

| Occurrence | Ce qui a été vérifié | Ce qui ne l'a pas été |
|---|---|---|
| Amendement de l'ADR 3374 | l'**ordre** des feuilles | la **liste** : deux posées sur trois |
| #3978 | la **présence** de `base` | l'**ensemble** dont elle fait partie |
| #3985 | que le trio soit **posé** | qu'il ne le soit pas **deux fois** |

Et le garde d'idempotence existant n'a attrapé **aucune** des deux dernières : il rejoue `poser` sur
une scène **déjà habillée**, quand les deux défauts naissent au **premier** appel, sur des formes
d'entrée que personne n'avait inventoriées. Un garde peut couvrir la bonne propriété sur les mauvaises
entrées, et son vert ne dit alors rien.

## Conséquences

- Les **sept** outils de capture qui posaient leurs feuilles à la main, en **cinq** combinaisons, sont
  devenus redondants et ont été retirés (#3992). Les dix-neuf aperçus qu'ils produisent sont
  **identiques au pixel près** avant et après : la preuve que `poser` faisait déjà tout le travail.
- L'une de ces combinaisons - `design` seule - **causait** le doublon de #3985.
- `patterns.md` disait « chargée par tous les FXML ». La phrase désignait le mauvais mécanisme ; elle
  dit désormais que la feuille arrive par `Habillage`.

## Alternatives écartées

- **Garder la déclaration dans le FXML et écrire l'ADR du dépassement.** Elle marchait. Mais la cause
  restait entière, et le prochain FXML déclarant `palette + base` serait retombé dedans sans que rien
  ne le dise.
- **Faire vérifier aux appelants qu'ils ont bien le trio.** C'est déplacer la règle chez ceux qui
  l'oublient. Le point de passage existe précisément pour qu'ils n'aient pas à y penser.
