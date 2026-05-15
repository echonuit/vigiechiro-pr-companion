# C13 - Observation

Une ligne du fichier de résultats. Une **séquence d'écoute peut générer plusieurs observations** : Tadarida produit 1 ligne par espèce distincte identifiée dans la séquence, avec son timing début/fin précis dans la séquence.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| séquence d'écoute source | référence | obligatoire | Lien vers la [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) correspondante. |
| temps début | décimal (s) | dans `[0, 5]` | Position dans la séquence. |
| temps fin | décimal (s) | dans `[0, 5]`, ≥ temps début | Position dans la séquence. |
| fréquence médiane | entier (Hz) | obligatoire | Métrique Tadarida. |
| taxon Tadarida | référence | obligatoire | Code 6 lettres (source : Tadarida). |
| probabilité Tadarida | décimal | dans `[0, 1]` | **Pas une garantie** : un 99 % peut être faux, un 20 % peut être juste. |
| taxon autre Tadarida | référence | optionnel | 2e proposition Tadarida. |
| taxon observateur | référence | optionnel | Saisi par l'utilisateur en validation. |
| probabilité observateur | décimal | dans `[0, 1]`, optionnel | Niveau de confiance utilisateur. |
| commentaire utilisateur | texte | optionnel, ≤ 500 car. | « pic 39 kHz, morphologie atypique », etc. |
| marqué comme référence | booléen | par défaut `false` | Sélection bonus pour la bibliothèque de sons exportable (COULD). |

## Règles applicables

- [R15](Règles%20métier.md#r15) - observation `validée` si `taxon observateur = taxon Tadarida` et `probabilité observateur` renseignée.
- [R16](Règles%20métier.md#r16) - observation `corrigée` si `taxon observateur ≠ taxon Tadarida`.
- [R17](Règles%20métier.md#r17) - observation non touchée conserve les colonnes `tadarida_*` à l'export `_Vu.csv`.
- [R18](Règles%20métier.md#r18) - deux modes de validation : `inventaire` ou `activité`.

## Voisins dans le modèle

- **Détectée dans** 1 [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md).
- **Classée comme** 1 [Taxon](C14%20-%20Taxon.md).
- **Agrégée par** 1 [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
