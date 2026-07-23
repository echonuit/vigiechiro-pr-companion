# C2 - Site de suivi

Un site est un **carreau de 2 km × 2 km** issu d'un **carroyage de référence couvrant tout le territoire national métropolitain** (maille standardisée du programme Vigie-Chiro). Il est créé hors application sur <https://vigiechiro.herokuapp.com/>, qui fournit le numéro de **carré** et la liste des **codes points** que l'utilisateur déclare ensuite dans l'app.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° carré | texte | exactement 6 chiffres | Les 2 premiers chiffres = département. Ex. `040962` (département 04 = Alpes-de-Haute-Provence). **Leading zero obligatoire** pour les départements 1 à 9. |
| nom convivial | texte | optionnel, ≤ 100 car. | Pour aider Marie à reconnaître son site (ex. « Étang de la Tuilière »). |
| protocole | énum | `PointFixeStandard` \| `PointFixeRecherche` | **`PointFixeStandard`** : suit le protocole VigieChiro à la lettre (2 passages annuels dans les fenêtres juin-juillet et août-septembre, cf. [R3](Règles%20métier.md#r3) / [R4](Règles%20métier.md#r4)). **`PointFixeRecherche`** : utilise les mêmes réglages techniques d'acquisition mais avec dates et fréquences personnalisées (cas des campagnes de recherche, par ex. la thèse Samuel) - [R3](Règles%20métier.md#r3) et [R4](Règles%20métier.md#r4) sont alors muettes. Architecture extensible (Pédestre, Routier… plus tard). |
| commentaire libre | texte | optionnel | Contexte écologique, descriptif paysager. |
| date de création | date | obligatoire | Date locale (importante pour les anniversaires de passage). |

## Règles applicables

- [R1](Règles%20métier.md#r1) - format du n° de carré (6 chiffres, leading zero départements 1-9).
- [R25](Règles%20métier.md#r25) - le n° de carré est **unique par utilisateur** (pas deux fois le même carré).
- [R26](Règles%20métier.md#r26) - le carré matérialise une **maille 2 km** du carroyage national : son emprise situe ses points sur la carte.
- [R28](Règles%20métier.md#r28) - un site qui **porte des passages** ne peut pas être supprimé.

## Voisins dans le modèle

- **Possédé par** un [Utilisateur](C1%20-%20Utilisateur.md).
- **Contient** ≥ 1 [Point d'écoute](C3%20-%20Point%20d%27écoute.md).
- **Ancré par** 0..1 **Lien VigieChiro** (table `vigiechiro_link`, entité `site`) : l'`_id` du site sur la plateforme, et son état **verrouillé** (`verrouille`) qui conditionne la possibilité de déposer sous ce site.

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
