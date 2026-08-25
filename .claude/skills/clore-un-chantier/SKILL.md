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
LES DOUZE PASSES S'EXÉCUTENT DANS L'ORDRE
```

L'ordre n'est pas un rangement, c'est une chaîne de dépendances. Le rompre fait rater ce que la
passe suivante devait trouver.

## Annoncer

« J'utilise la compétence clore-un-chantier pour clore <l'EPIC>, passe par passe. »

## Les douze passes

| # | Passe | Ce qu'elle produit | Compétence |
|---|---|---|---|
| 0 | Relecture des ADR existantes | la liste des décisions que le chantier a contredites | `ecrire-une-adr` |
| 1 | Audit d'intégration | ce que `main` a changé pendant le chantier | |
| 2 | Cohérence CLI et IHM | la commande manquante, ou son absence justifiée | |
| 3 | Doc développeur | `dev-docs/` recolle au code livré | `humaniseur` |
| 4 | Doc utilisateur | `docs/` et ses captures | `revue-visuelle`, `humaniseur` |
| 5 | Brief projet | parcours, maquettes, modèle conceptuel | `humaniseur` |
| 6 | Tests | chaque usage introduit est couvert | `tdd`, `mutation` |
| 7 | Harmonisation | l'application entière, pas le delta | |
| 8 | Revue visuelle | chaque **état** de chaque écran touché | `revue-visuelle` |
| 9 | Nouveaux chantiers | les issues cadrées pendant que le contexte est frais | `triage` |
| 10 | ADR du chantier | une décision structurante, une ADR | `ecrire-une-adr` |
| 11 | Bilan | livré, dette restante, décisions et leur pourquoi | `humaniseur` |

Quatre passes ne produisent que de la prose, et c'est pourquoi elles nomment `humaniseur` : ce
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

Les issues nées en passe 9 forment un chantier à part entière. Elles se closent par **les mêmes
douze passes**, appliquées à leur seul delta. Le dépôt l'a appris trois fois.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je documente d'abord, j'auditerai après » | L'audit peut révéler du code à écrire avant de documenter |
| « L'harmonisation n'a rien cassé, les tests passent » | Elle casse des écrans sans casser de test. C'est pourquoi la revue visuelle suit |
| « Cette ADR ne concerne pas mon delta » | Elle régit peut-être du code hors delta qu'il faut aligner |
| « Je relis les ADR de ma branche » | Contre `origin/main`, sinon vous manquez celles écrites pendant |
| « Les suites, on verra plus tard » | Elles se closent par les mêmes douze passes |
