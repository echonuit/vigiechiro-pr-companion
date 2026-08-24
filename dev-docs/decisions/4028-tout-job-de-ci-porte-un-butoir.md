---
type: adr
title: "Tout job de CI porte un butoir"
status: stable
article: A12
chantier: "#4028, clôture du chantier des films et de la CI (#4013)"
decided_at: 2026-08-20
verification: certaine
enforced_by:
  - ".github/scripts/verifie-butoirs.sh"
verified:
  - by: machine:ci
    at: 2026-08-20
---

# Tout job de CI porte un butoir

## Contexte

Une PR est restée bloquée **4 h 15** sur l'étape « Aligner la police système » de `capture-vues.yml`.
Le miroir du runner ne répondait plus, apt basculait sur l'archive amont, et le job n'avait aucun
`timeout-minutes` : GitHub laisse courir **six heures**.

⚠️ L'étape était en `-qq`. Aucune ligne de log ne disait ce qui traînait.

Un butoir avait pourtant été posé sur `banc-filme` (#3883), avec le raisonnement exact : *« un garde
qui peut bloquer indéfiniment est pire qu'un garde qui échoue : il ne dit rien, et il retient tout le
monde »*. Il avait été apprécié **pour ce seul job**. Vingt-six autres n'en avaient aucun, dont ceux
qui publient.

## Décision

**1. Tout job porte un `timeout-minutes`.** Un job qui échoue apprend quelque chose ; un job qui pend
n'apprend rien **et** retient tout le monde.

**2. Les valeurs viennent d'une mesure**, pas d'une intuition : environ quatre fois le maximum observé
sur les quarante derniers runs réussis.

| Job | Maximum mesuré | Butoir |
|---|---|---|
| `paquet` | 13,3 min | 40 |
| `ordre-alternatif` | 12,9 min | 40 |
| `capturer` | 8,4 min | 30 |
| `lint` | 1,2 min | 15 |
| `titre` | 0,1 min | 10 |

**3. Un job qui DÉLÈGUE (`uses:`) en est dispensé** : c'est le workflow appelé qui porte le sien.

**4. La garde est la partie durable, pas les butoirs.** Vingt-six valeurs posées à la main se
seraient défaites au premier job ajouté. `verifie-butoirs.sh` refuse le vingt-septième qui en
manquerait.

## Conséquences

⚠️ **Un butoir rend la panne visible et bornée ; il ne la corrige pas.** Sitôt posé, il a montré que
trois étapes de trois workflows pendaient sur le même `apt-get update` - ce que personne ne voyait,
parce qu'elles portaient des noms parlant d'autre chose : « Aligner la police système », « Installer
de quoi afficher et filmer », « E2E CLI (bats) sur le fat-jar ». Lu de loin, le rouge accusait le code
de la PR. C'est ce qui a conduit à [ADR 4034](4034-les-paquets-passent-par-une-porte.md).

⚠️ **Un dépassement se présente en `cancelled`, pas en `failure`.** Un filtre de surveillance qui ne
regarde que les échecs ne verra jamais un job qui pend.

## Ce que cette garde ne dit PAS

Que le butoir soit **bien choisi**. Trop large, il ne protège de rien ; trop serré, il rend le rouge
illisible - `banc-filme` a dû passer de 12 à 25 minutes quand la mesure a montré que son outillage
pesait 91 Mo. Ces valeurs se révisent en mesurant de nouveau, pas en discutant.
