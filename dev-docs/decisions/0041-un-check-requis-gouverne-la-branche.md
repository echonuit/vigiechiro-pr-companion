# ADR 0041 — Un check requis ne gouverne pas les PR, il gouverne la branche

- **Statut** : Accepté — 2026-07-20
- **Chantier** : EPIC #2104 / lot 1 (#2106)
- **Complète** : [ADR 0040](0040-le-sujet-de-commit-est-une-syntaxe.md), en disant **jusqu'où** le contrôle qu'elle institue doit aller.

## Contexte

L'[ADR 0040](0040-le-sujet-de-commit-est-une-syntaxe.md) institue un contrôle du titre de PR, après
que la publication du dépôt se soit arrêtée deux jours en silence. La question restée ouverte était
son **degré** : informatif, ou bloquant ?

Il a été rendu **bloquant** (ruleset de dépôt `titre-de-pr-conforme`, check requis `titre`). En une
heure, il a cassé **deux** automatismes - les deux chemins par lesquels ce dépôt écrit sur `main` :

| Chemin | Symptôme |
|---|---|
| PR d'aperçus (`capture-vues.yml`) | `BLOCKED`, **aucun check rapporté** |
| Push du CHANGELOG (`semantic-release`) | `GH013 … Required status check "titre" is expected`, push rejeté |

La cause commune est la même règle de GitHub : **aucun workflow n'est déclenché par un événement
produit avec le `GITHUB_TOKEN`** (sans quoi une action pourrait se relancer indéfiniment). Le check
requis ne peut donc jamais rapporter sur ce que produit l'automatisation - et un check requis muet
**bloque pour toujours**.

Le second cas est le plus instructif, à deux titres. D'abord parce qu'il **a arrêté la publication**,
c'est-à-dire exactement ce que le chantier venait de réparer : trois releases ont échoué d'affilée.
Ensuite parce qu'il montre que la portée d'un check requis dépasse les pull requests : un **push
direct** est soumis aux mêmes règles.

Le premier cas a d'ailleurs été corrigé sans que le second soit vu, alors qu'ils relèvent du même
mécanisme sous deux formes. Corriger le chemin qu'on a sous les yeux ne dit rien des autres.

La dérogation qu'on attendrait est fermée : ajouter `github-actions` aux contournements d'un ruleset
**de dépôt** échoue en `422` - `Actor GitHub Actions integration must be part of the ruleset source or
owner organization`. Seul un ruleset **d'organisation** l'accepterait, au prix d'une configuration qui
déborde le dépôt.

## Décision

**Le contrôle du titre reste informatif.** Il rougit sur un titre non conforme, et cette information
suffit : c'est ainsi qu'il a attrapé la PR #2122 le jour même de sa mise en place, dont le titre a été
corrigé dans la minute.

Deux raisons, pas une.

**1. Le bénéfice du blocage était faible.** Le dépôt a un mainteneur unique, qui dispose de toute
façon du contournement administrateur : le blocage n'empêchait donc personne de fusionner en rouge, il
ne faisait que déplacer le geste.

**2. Le coût était réel et mesuré.** Deux automatismes cassés, dont la chaîne de publication, en une
heure.

**Avant de rendre un check obligatoire, inventorier tous les chemins d'écriture vers `main`** - PR
humaines, PR de bot, pushes directs d'automatismes - et se demander, pour chacun, **comment le check y
rapportera**. Un chemin sans réponse est un blocage permanent.

## Conséquences

**Ce qu'on garde.** Le signal, à l'endroit et au moment utiles. Et le filet de l'ADR 0040 : le parser
élargi de `.releaserc.json` fait qu'un titre non conforme fusionné par mégarde **publie quand même**,
au lieu de re-figer la version.

**Ce qu'on perd.** Rien n'empêche techniquement de fusionner une PR au titre rouge. C'est assumé :
l'information est visible, et l'ignorer est un choix, non un accident silencieux - ce qui était le
défaut d'origine.

**Ce qui reste, et qu'on aurait pu retirer.** `capture-vues.yml` publie lui-même son check `titre`.
Ce mécanisme est né du besoin de débloquer, disparu depuis ; il est conservé parce qu'il se justifie
seul : sans lui, une PR d'aperçus ne serait validée par **rien**.

**Si le blocage redevient souhaitable**, la voie est un ruleset **d'organisation** restreint à ce
dépôt, avec l'application GitHub Actions en contournement - qui couvrirait les deux chemins d'un coup.
C'est une modification de configuration d'organisation, à décider comme telle.
