---
type: adr
title: "Un garde de CI porte ses propres cas, et rien d'autre ne les porte"
status: stable
article: A2
chantier: "#3661, suites des lots 1 et 2 (#3664)"
decided_at: 2026-08-13
verification: certaine
enforced_by:
  - ".github/scripts/mesure_duree_portail.py"
verified:
  - by: machine:ci
    at: 2026-08-13
relations:
  amende: ["3560"]
---

# Un garde de CI porte ses propres cas, et rien d'autre ne les porte

## Contexte

Le dépôt tient depuis #2947 une discipline que **dix scripts sur onze** appliquent : chaque garde de
CI porte un `--auto-test` dans son propre fichier, et `lint.yml` les lance tous. La raison est écrite
dans `verifie-titre-pr.sh` :

> Ce script est lui-même un garde, et un garde qui cesse de détecter reste vert.

Les cas sont substantiels : onze pour le titre de PR, treize pour le secret winget, sept pour les
renvois de workflows, et plusieurs portent des **contrôles négatifs** - la règle doit aussi refuser de
refuser.

Le onzième script, `mesure-duree-portail.sh`, est arrivé avec [ADR 3560] **hors de cette
discipline** : ses cas vivaient dans `src/test/bats/scripts-ci.bats`, un fichier créé pour lui.

**La cause est un relevé faux, et il était de moi.** L'issue #3661 affirmait au départ que « onze
scripts rendent des jugements, un seul est éprouvé ». J'avais cherché les tests dans `src/test` et
dans les chemins cités par les workflows ; je ne pouvais pas y voir une discipline qui vit **à
l'intérieur** de chaque script. Un `grep` qui ne trouve rien est une hypothèse, jamais un constat.

## Décision

**Les cas d'un garde de CI vivent dans le garde.** `mesure-duree-portail.sh` porte désormais son
`--auto-test`, lancé par `lint.yml` à côté des dix autres ; `scripts-ci.bats` disparaît.

### Pourquoi ce n'est pas qu'une question de rangement

L'exception avait trois conséquences mesurables, toutes annulées ici :

- ses cas ne tournaient que dans le job `paquet`, qui **attend l'assemblage d'un fat-jar de 80 Mo**
  dont un test de shell n'a aucun besoin - les dix autres ne construisent rien ;
- elle a rendu **fausse** une phrase de `dev-docs/ci-cd-release.md` (« les tests bats passent sur ce
  seul artefact »), qu'il a fallu recadrer pour l'accueillir ;
- le compteur `<!--inv:tests-bats-->` comptait des cas qui ne sont pas des E2E de CLI, brouillant ce
  qu'il mesure.

Une seconde façon de faire ne coûte pas seulement sa duplication : elle **déplace ce qui l'entoure**
pour lui faire de la place, et chaque déplacement se relit ensuite comme une règle.

### Ce que l'injection devient

La couture de l'ADR 3560 **reste** : la série se pose par `SERIE_DUREES_FICHIER`. C'est elle qui rend
les cas possibles, quel que soit le harnais qui les porte - et c'est la moitié de la décision qui
valait, l'autre étant le choix du harnais, qui était faux.

### Ce qui a été écarté

**Garder `bats` et l'exécuter dans `lint.yml`.** Techniquement suffisant : cela réglait le fat-jar
inutile, pas la seconde façon de faire. Deux harnais pour onze scripts obligent, à chaque garde
suivant, à trancher lequel employer - et ce genre d'arbitrage se tranche par recopie du voisin le plus
proche, ce que l'ADR 3574 a déjà payé sur les effacements.

**Convertir les dix autres vers `bats`.** L'inverse, pour la même raison, avec dix fois le coût et
aucun gain : leur discipline actuelle fonctionne et est éprouvée.

## Conséquences

- `lint.yml` gagne une étape, `src/test/bats/` perd un fichier, et le compteur `bats` revient à 100.
- L'ADR 3560 annonçait `scripts-ci.bats` en vérification : ce fichier n'existe plus, et la garde
  `DocumentationAJourTest` l'a signalé. Sa ligne `**Vérification**` **est repointée** vers le script
  lui-même : une ADR est immuable dans son raisonnement, pas dans le pointeur vers le dispositif qui
  la tient - et laisser ce pointeur cassé serait précisément « un rapport qu'aucun script n'alimente »,
  ce que la garde reproche. C'est elle qui a rendu cette ADR nécessaire, plutôt qu'un pointeur corrigé
  en silence.
- **Deux constats restent ouverts**, et ils ne sont pas traités ici : `construit-appimage.sh` est le
  seul script sans aucun cas, et **quatre des dix auto-tests n'ont pas de contrôle négatif visible**.
  Un garde qui n'accepte jamais rien passe ses tests d'acceptation aussi bien qu'un garde juste.
