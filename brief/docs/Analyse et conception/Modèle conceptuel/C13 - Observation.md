# C13 - Observation

Une ligne du fichier de résultats. Une **séquence d'écoute peut générer plusieurs observations** : Tadarida produit 1 ligne par espèce distincte identifiée dans la séquence, avec son timing début/fin précis dans la séquence.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| séquence d'écoute source | référence | obligatoire | Lien vers la [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) correspondante. |
| temps début | décimal (s) | dans `[0, 5]` | Position dans la séquence. |
| temps fin | décimal (s) | dans `[0, 5]`, ≥ temps début | Position dans la séquence. |
| fréquence médiane | entier (**kHz**) | optionnel | Métrique Tadarida (colonne `median_freq_khz`). L'unité est le **kHz**, pas le Hz : les cris de chiroptères se lisent en dizaines de kHz (Pippip ~50, rhinolophes >100). Lue en Hz, la valeur n'aurait aucun sens (infrasons). |
| taxon Tadarida | référence | obligatoire | Code 6 lettres (source : Tadarida). |
| probabilité Tadarida | décimal | dans `[0, 1]` | **Pas une garantie** : un 99 % peut être faux, un 20 % peut être juste. |
| taxon autre Tadarida | référence | optionnel | 2e proposition Tadarida. |
| taxon observateur | référence | optionnel | Saisi par l'utilisateur en validation. |
| probabilité observateur | décimal | dans `[0, 1]`, optionnel | **N'est pas la confiance de l'observateur** : c'est la probabilité **Tadarida recopiée** à la validation, héritée du format `_Vu` (colonne `prob_observer`). La confiance déclarée par l'observateur est la **certitude** ci-dessous. |
| certitude observateur | énum | `SUR` \| `PROBABLE` \| `POSSIBLE` \| `null` (défaut) | Confiance **déclarée à la main** par l'observateur à la revue (colonne `observer_certainty`, #1139). Domaine calé sur la plateforme Vigie-Chiro ; **jamais** dérivée de la probabilité ci-dessus, vide tant qu'on ne l'a pas saisie. |
| commentaire utilisateur | texte | optionnel, ≤ 500 car. | « pic 39 kHz, morphologie atypique », etc. |
| marqué comme référence | booléen | par défaut `false` | Sélection bonus pour la bibliothèque de sons exportable (COULD). |
| mode de validation | énum | `manuel` \| `auto` \| `null` (défaut) | Trace comment le `taxon observateur` a été établi : saisie utilisateur explicite (`manuel`), propagation automatique par le mode inventaire (`auto`), ou pas encore validé (`null`). Cf. [R24](Règles%20métier.md#r24). |

## Règles applicables

- [R15](Règles%20métier.md#r15) - observation `validée` si `taxon observateur = taxon Tadarida` (la probabilité observateur n'entre pas dans le statut ; la confiance se déclare par la **certitude**).
- [R16](Règles%20métier.md#r16) - observation `corrigée` si `taxon observateur ≠ taxon Tadarida`.
- [R17](Règles%20métier.md#r17) - observation non touchée conserve les colonnes `tadarida_*` à l'export `_Vu.csv`.
- [R18](Règles%20métier.md#r18) - deux modes de validation : `inventaire` ou `activité` (+ variante future `inventaire pondéré`).
- [R24](Règles%20métier.md#r24) - chaque observation porte un `mode de validation` (`manuel` / `auto` / `null`) qui distingue ce qui a été vérifié à l'oreille de ce qui a été propagé.

## Voisins dans le modèle

- **Détectée dans** 1 [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md).
- **Classée comme** 1 [Taxon](C14%20-%20Taxon.md).
- **Agrégée par** 1 [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
