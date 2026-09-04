---
type: adr
title: "Un clip montre la version qu'on valide, et le dit"
status: stable
article: A5
chantier: "#4111, suite de #4103"
decided_at: 2026-08-21
verification: certaine
enforced_by:
  - ".github/scripts/verifie_conditions_de_job.py"
verified:
  - by: machine:ci
    at: 2026-08-21
relations:
  prolonge: ["4013"]
---

# Un clip montre la version qu'on valide, et le dit

## Contexte

Les clips de recette se tournaient **à la main**. `recette-filmee.yml` posait lui-même la condition de
son branchement :

> Manuel seulement, pour l'instant. Le branchement sur la publication viendra avec le harnais de
> recette (EPIC #3667) : brancher un dispositif avant d'avoir ce qu'il doit garder n'apporterait qu'un
> vert de plus.

Cette condition est levée : l'EPIC #3667 est clos, #4056 a publié les clips, et
`CorrespondanceRecetteTest` lit **11 sessions et 403 cas**.

Le défaut est celui de l'[ADR 4103](4103-un-canal-de-distribution-ne-depend-pas-d-un-geste.md), sur
un autre dispositif : une version publiée sans tournage laisse en ligne les clips d'une version
antérieure, et c'est un état parfaitement **vert**. Pire que pour un paquet : un clip **a toujours
l'air juste**, puisqu'il montre bien un produit qui fonctionne. Rien, en le regardant, ne dit de
quelle version il s'agit.

## Décision

**Le tournage part avec le train, après `publish`**, en parallèle du Flatpak. Le déclenchement manuel
reste, pour retourner un clip à la demande.

### 1. Pourquoi pas la veille, ni le matin, ni chaque commit

| Moment envisagé | Ce qu'il produirait |
|---|---|
| à chaque commit sur `main` | 5 min et 58 fichiers réécrits par fusion - une douzaine par jour - sur une pré-version **roulante** : on effacerait sans cesse des clips que personne n'a eu le temps de regarder |
| le matin | des clips de `main` à 6 h, c'est-à-dire d'un état qui n'existe nulle part ailleurs |
| la veille de la publication | un couplage **temporel** : le 2026-08-21, douze commits ont fusionné dans la journée. Les clips montreraient autre chose que ce qui est publié |

Les trois sont des **suppositions de date**. Après `publish`, ce que la recette regarde est ce que la
version publiée montre, **par construction**.

### 2. La pré-version dit ce qu'elle montre

Son corps annonçait « elle ne marque aucune version du produit ». C'était exact tant que le tournage
était manuel ; ça devient **faux** dès que le train l'appelle. Le corps se réécrit donc à chaque
tournage : la version filmée, ou le SHA court quand le tournage est manuel - auquel cas il le **dit**
plutôt que de laisser croire à une correspondance.

C'est la moitié qui compte. Automatiser sans nommer la version aurait produit des clips justes et
**indatables** : le défaut d'origine déplacé d'un cran, pas corrigé.

### 3. Le coût ne décide pas

Cinq minutes pour 58 clips, mesurées le 2026-08-20. C'est l'usage - jouer la recette d'une version -
qui décide du moment, pas la durée.

## Ce que la décision NE couvre pas

**`capture-vues.yml`**, qui produit les PNG d'aperçu.

**La première rédaction disait qu'il « pose exactement la même question ». C'est faux, et la mesure
l'a dit le jour même** : il n'est pas manuel. Il tourne sur `push: main` **et** sur chaque
`pull_request`, en cinq minutes, et publie ses aperçus par une PR auto-mergée. Le défaut que cette ADR
corrige - un dispositif qui dépend d'un geste que rien ne réclame - ne l'atteint donc pas.

Et son moment est **l'inverse** de celui retenu ici, à juste titre. Les deux ne se rangent pas de la
même façon :

| | Aperçus PNG | Clips |
|---|---|---|
| Où ils vivent | **committés dans le dépôt** | pièces jointes d'une pré-version **roulante**, hors git |
| Ce qui les date | l'historique git, par construction | rien, sauf à le leur faire dire |
| D'où le moment | à chaque commit, pour rester alignés sur le code | à la publication, pour montrer une **version** |

Ce n'est donc pas une incohérence entre deux dispositifs voisins : un artefact versionné avec le code
suit le code, un artefact publié à côté suit les versions.

## Conséquences

- Le train gagne un second `workflow_call`, en parallèle du premier : environ cinq minutes de plus.
- **Le chaînage ne s'observe qu'au premier train** : `release.yml` ne s'exécute pas sur les PR. Ce
  qui est vérifié avant fusion se limite au montage - conditions de job, butoirs, renvois, YAML.
- Un tournage manuel reste possible, et se distingue désormais d'un tournage de publication dans le
  corps de la pré-version.
