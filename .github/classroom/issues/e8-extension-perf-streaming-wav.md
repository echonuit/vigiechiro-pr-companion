# 🧩 [extension] Performance : réduire l'empreinte mémoire de l'import (avancé)

> Extension **optionnelle avancée** (R2.03 / perf). Touche le **service** d'import fourni.

## Objectif

Le brief O3 demande une lecture **par morceaux**, sans jamais charger un fichier entier en mémoire, et
une persistance **en flux**. Améliorez l'empreinte mémoire de l'import sur les nuits volumineuses.

## Deux pistes

1. **Lecture WAV en streaming** : éviter de charger chaque fichier d'un bloc ; traiter par *chunks*.
2. **Persistance en flux** : écrire les résultats de transformation au fil de l'eau plutôt que de tout
   accumuler avant la transaction.

## Critères d'acceptation

- [ ] Sur une nuit volumineuse (banc fourni), la **mémoire crête baisse** de façon mesurable, sans
      régression fonctionnelle (mêmes séquences produites).

## Definition of Done

- [ ] Mesure avant/après jointe ; tests d'import verts ; suite verte ; PR relue. Voir la DoD commune (`e0`).
