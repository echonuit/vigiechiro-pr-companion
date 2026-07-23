# C13 - Observation

Une détection dans une séquence d'écoute. Une **séquence peut en générer plusieurs** : Tadarida produit 1 ligne par espèce distincte identifiée, avec son timing début/fin précis. Sur une même détection, **trois avis peuvent coexister** : Tadarida **propose** (`taxon Tadarida`), l'observateur **corrige** (`taxon observateur` + certitude), et le validateur MNHN **tranche** (`taxon validateur`, en lecture seule) ; un **fil de discussion** peut s'y greffer. Une observation peut aussi être **créée à la main** sur une séquence que Tadarida n'a pas identifiée (elle n'a alors pas de `taxon Tadarida`).

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
| douteuse | booléen | par défaut `false` | Drapeau « à repasser » posé par l'observateur (`is_doubtful`, #160). Distinct de « pas encore vue » : la détection a été regardée, mais laissée en suspens. |
| taxon validateur | référence | optionnel | Taxon **tranché par l'expert MNHN** (`taxon_validator`, #1417). **Lecture seule** : le serveur refuse l'écriture depuis un jeton d'observateur. |
| certitude validateur | énum | `SUR` \| `PROBABLE` \| `POSSIBLE` \| `null` | Certitude déclarée par le validateur (`validator_certainty`), même domaine que la certitude observateur. Lecture seule. |
| identifiant donnée plateforme | texte | optionnel | `_id` de la donnée côté serveur (`vigiechiro_data_id`), acquis à l'ancrage. |
| indice observation plateforme | entier | optionnel | Position **brute** de l'observation dans le tableau `observations` du serveur (`vigiechiro_obs_index`) : c'est la **cible du PATCH** de correction. |

## Règles applicables

- [R15](Règles%20métier.md#r15) - observation `validée` si `taxon observateur = taxon Tadarida` (la probabilité observateur n'entre pas dans le statut ; la confiance se déclare par la **certitude**).
- [R16](Règles%20métier.md#r16) - observation `corrigée` si `taxon observateur ≠ taxon Tadarida`.
- [R17](Règles%20métier.md#r17) - observation non touchée conserve les colonnes `tadarida_*` à l'export `_Vu.csv`.
- [R18](Règles%20métier.md#r18) - deux modes de validation : `inventaire` ou `activité` (+ variante future `inventaire pondéré`).
- [R24](Règles%20métier.md#r24) - chaque observation porte un `mode de validation` (`manuel` / `auto` / `null`) qui distingue ce qui a été vérifié à l'oreille de ce qui a été propagé.

## Voisins dans le modèle

- **Détectée dans** 1 [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md).
- **Classée comme** 0..1 [Taxon](C14%20-%20Taxon.md) : le `taxon Tadarida` est **nullable** depuis l'observation manuelle (une détection créée à la main n'en a pas). L'observation porte en outre jusqu'à **trois** références de taxon (Tadarida, observateur, validateur).
- **Agrégée par** 0..1 [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md) : `results_id` est **nullable** ; une observation manuelle n'appartient à aucun jeu de résultats.
- **Discutée par** 0..N **Message** (table `observation_message`, #1417) : le fil d'échange avec le validateur MNHN, ordonné, chaque message portant l'auteur (identifiant plateforme) et sa date.

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
