---
type: adr
title: "Une condition de job nomme l'état qu'elle attend"
status: stable
article: A15
chantier: "#4079, EPIC livraison (#2104)"
decided_at: 2026-08-21
verification: certaine
enforced_by:
  - ".github/scripts/verifie-conditions-de-job.sh"
verified:
  - by: machine:ci
    at: 2026-08-21
---

# Une condition de job nomme l'état qu'elle attend

## Contexte

Le train du mercredi 2026-08-19 a **réussi** et n'a **rien livré**. semantic-release a calculé la
version, committé `CHANGELOG.md`, créé le tag `v2.186.0` et la Release GitHub. Puis `installers` et
`publish` ont été **sautés** : aucun paquet construit, aucun asset téléversé, une Release restée en
brouillon. Le run est vert de bout en bout, et il a fallu huit jours pour que quelqu'un le remarque.

Les deux jobs étaient gardés par une expression qui paraissait juste :

```yaml
if: needs.release.outputs.tag != ''
```

La sortie `tag` valait pourtant bien `v2.186.0` : le journal du run porte `Nouveau tag : v2.186.0`
puis `Set output 'tag'`. La condition n'était pas fausse, elle n'a **jamais été évaluée**.

Dans GitHub Actions, la propagation du « sauté » est **transitive**, et une condition de job qui ne
porte aucune **fonction d'état** (`always()`, `cancelled()`, `success()`, `failure()`) est
implicitement enveloppée en `success() && (...)` - un `success()` qui porte sur **tout le graphe
amont**, pas sur le seul job nommé dans le `needs`.

Ce qui a armé le piège est une amélioration : #3770 a fait de la preuve des plateformes la condition
du train, en mettant deux gardes en `needs` du job `release`. L'une d'elles,
`contournement-declare`, ne tourne **que** sur un `workflow_dispatch` portant une raison écrite.
Sur le train programmé, elle est donc **toujours** sautée. Le job `release` s'en protégeait avec
`!cancelled()` ; ses descendants, écrits avant que cet amont n'existe, ne s'en protégeaient pas. Le
saut a traversé `release` et éteint la publication.

## Décision

**Toute condition d'un job appuyé sur un `needs` nomme explicitement l'état qu'elle attend.**

```yaml
if: >-
  ${{ !cancelled()
  && needs.release.result == 'success'
  && needs.release.outputs.tag != '' }}
```

Les deux morceaux comptent, et pour des raisons différentes :

- `!cancelled()` **reprend la main** sur la propagation du saut. C'est sa seule présence qui compte,
  pas ce qu'il teste.
- `needs.<job>.result == 'success'` **referme ce que le premier ouvre**. `!cancelled()` seul
  laisserait passer un ancêtre en **échec** : on aurait troqué une publication qui ne part jamais
  contre une publication qui part sur des bases rouges.

**Un job sans `if:` du tout n'est pas concerné.** Être sauté avec son ancêtre est alors le
comportement attendu, et l'exiger autrement refuserait une forme juste.

**La garde est la partie durable, pas les deux corrections.** Deux `if:` réécrits à la main se
seraient défaits au prochain `needs` ajouté en amont - c'est exactement ainsi que ce défaut est né.
`verifie-conditions-de-job.sh` refuse le troisième.

## Conséquences

⚠️ **Le mode de défaillance corrigé se présentait comme un succès.** C'est la même famille que les
ADR portant sur les gardes qui acceptent à tort : rien ne rougit, rien ne lève, et seul l'inventaire
des assets trahit que la chaîne s'est arrêtée au milieu. Un tableau de bord qui ne regarde que les
runs rouges ne l'aurait jamais montré.

⚠️ **Ajouter un `needs` en amont d'un job change le comportement de tout son aval**, y compris de
jobs qu'on ne touche pas et qu'on ne relit pas. #3770 était une bonne décision, correctement mise en
œuvre pour le job qu'elle visait ; le dégât s'est produit deux crans plus bas.

La numérotation garde un trou : la 2.186.0 n'a jamais été livrée, son brouillon a été supprimé et le
tag conservé pour que la chaîne reparte en 2.187.0 (#4083).

## Alternatives écartées

- **Faire tourner `contournement-declare` en toutes circonstances**, avec un corps vide hors
  contournement. Supprimerait l'ancêtre sautable, et détruirait ce que #3770 cherchait : un
  contournement qui **se voit** dans le graphe du run, à côté de la garde absente. Un job toujours
  vert ne signale plus rien.
- **Retirer le `needs` du job `release`.** Rendrait la publication indépendante de la preuve des
  plateformes, c'est-à-dire déferait la décision de #3770 pour réparer un effet de bord.
- **Se contenter de `always()`.** Publie même sur un `release` en échec. Le remède serait pire.
- **Ne rien garder et corriger les deux `if:`.** Écarté pour la raison donnée plus haut : le défaut
  se recrée tout seul au prochain amont ajouté, et il ne se voit pas.

## Ce que cette garde ne dit PAS

**Que la publication reparte.** La garde prouve qu'aucune condition de job n'est exposée à la
propagation du saut ; elle ne construit ni ne téléverse rien. Que la chaîne livre effectivement ne se
vérifie qu'au **prochain train**, ou sur un `workflow_dispatch` avec raison. Un vert de garde n'est
pas un artefact parti, et le confondre avec une preuve de livraison serait refaire, au niveau du
raisonnement, l'erreur que cette ADR corrige au niveau du YAML.
