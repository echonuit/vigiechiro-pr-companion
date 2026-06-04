# 🧩 [extension] Performance : campagne de mesure sur machine IUT

> Extension **optionnelle** (prolonge la vérification de performances). Complète l'issue « Mesurer les
> performances réelles ».

## Objectif

Valider les objectifs d'efficience **en conditions réelles** sur une **machine IUT** (premier
lancement du jour, JIT « froid »), et **consigner** les résultats.

## À mesurer

- [ ] **O5 — fluidité IHM à volume réel** : charger ~4000 observations (table de validation) et ~1000
      passages (table multisite) ; vérifier tri / filtre / sélection **fluides dans l'IHM**.
- [ ] **O3 — charge ≥ 20 Go** : import d'une grosse nuit via le banc fourni ; relever temps, débit,
      mémoire crête.
- [ ] **O3 — réactivité** : pendant un import dans l'appli, vérifier l'absence de gel IHM perceptible.

## Critères d'acceptation

- [ ] Un rapport daté (machine, mesures, comparaison aux cibles) est produit.

## Definition of Done

- [ ] Résultats consignés (`docs/benchmarks/` ou compte rendu) ; points chauds tracés en issues si une
      cible n'est pas tenue. Voir la DoD commune (`e0`).
