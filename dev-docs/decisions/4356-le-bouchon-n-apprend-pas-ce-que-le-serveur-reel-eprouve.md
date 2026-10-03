---
type: adr
title: "Le bouchon n'apprend pas ce que le serveur réel éprouve à chaque demande"
status: stable
article: A4
chantier: "#4356, clôture de l'EPIC #5643 (chantier #5640)"
decided_at: 2026-10-03
verification: humaine
verification_note: "une decision de ne pas faire ne laisse aucun code a garder : rien ne peut refuser un comportement ajoute au bouchon sans juger s il cite une sonde live, ce que seule la relecture fait"
enforced_by: []
verified:
  - by: humain
    at: 2026-10-03
relations:
  prolonge: ["4142", "5641-les-tests-connectes-ont-deux-cibles"]
generated:
  by: "process:assistance-par-agents"
---

# Le bouchon n'apprend pas ce que le serveur réel éprouve à chaque demande

## Le contexte

`src/test/bats/stub_vigiechiro.py` sert les tests de la ligne de commande. #4356 relevait qu'il ne
connaît aucun des pièges que le client absorbe : `max_results > 100` rejeté en 422, dates en RFC 1123,
`numero` refusé à l'écriture, `401` contre `403` contre injoignable. L'éprouver demandait un tournage
connecté, donc un jeton frais par tir, et l'issue proposait d'enseigner ces pièges au bouchon, chaque
comportement citant la sonde live qui l'a établi.

Depuis le chantier #5643, le contrat live joue **en entier sur la plateforme de test** à chaque
demande, contre le vrai code serveur de l'API épinglée : 42 tests, aucun sauté.

## La décision

**Le bouchon n'apprend pas ces pièges.** Chacun a sa sonde sur la plateforme de test :

| Piège | Sonde |
|---|---|
| `max_results > 100` en 422 | `ContratApiVigieChiroLiveTest#refus_serveur_est_un_refuse_explicite` |
| dates en RFC 1123 | les allers-retours de `AllerRetourParticipationLiveTest` |
| `numero` refusé à l'écriture | `ContratApiVigieChiroLiveTest`, « PROBE #4444 » |
| `401`, `403`, injoignable | `ContratApiVigieChiroLiveTest#un_jeton_inconnu_est_un_refus_401`, les refus `403` de la carte des lectures |

Un bouchon répond ce que nous croyons que la plateforme répond, et c'est l'argument de
l'[ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md) : il ne cesse pas d'être vrai parce
que le bouchon grossit. Un serveur réel épinglé ne croit rien. Enrichir le bouchon créerait une seconde
source de vérité sur le contrat, qui dériverait.

**La règle de #4356 reste** : tout comportement ajouté au bouchon cite la sonde live qui l'a établi. Ce
chantier n'en ajoute aucun, et le piège `If-Match` n'en appelle aucun, la plateforme rendant `200` avec
ou sans cet en-tête (#4523).

## Pourquoi `humaine`

Rien ne peut refuser un comportement ajouté au bouchon sans lire s'il cite sa sonde, et une décision de
ne pas faire ne laisse aucun code à garder.

## Ce qui a été écarté

**Enrichir le bouchon**, la proposition d'origine de #4356, pour la raison dite plus haut. Le bouchon
garde son rôle, servir les tests de la ligne de commande sans réseau, et `cli-reseau.bats` continue de
l'employer.
