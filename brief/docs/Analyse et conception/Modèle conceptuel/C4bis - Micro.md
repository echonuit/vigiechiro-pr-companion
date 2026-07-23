# C4bis - Micro

Le **micro** monté sur le Passive Recorder est l'élément clé de la **qualité acoustique** d'une session d'enregistrement. Plusieurs modèles coexistent au sein de la communauté Vigie-Chiro, avec des caractéristiques sensiblement différentes (bande passante, sensibilité, directivité). Tracer cette information permet de **comparer la qualité des enregistrements entre PR** ou avant/après un changement de matériel.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| modèle / référence | texte | obligatoire | Ex. `Knowles FG-23329`, `SPU0410LR5H-QB`, ou modèle custom. |
| bande passante | texte | optionnel | Plage utile, ex. `8-150 kHz`. |
| sensibilité | texte | optionnel | Caractéristique constructeur, ex. `-42 dBV/Pa @ 1 kHz`. |
| date de mise en service | date | optionnel | Suit la vie matérielle du micro (utile pour repérer une dérive après remplacement). |
| date de retrait | date | optionnel | Fin de service du micro (`decommissioned_at`), typiquement après un remplacement. |
| actif | booléen | par défaut `true` | Micro actuellement monté (`is_active`). La table **historise** les micros : un seul `actif` par enregistreur, les précédents sont conservés avec leur date de retrait. |
| commentaire libre | texte | optionnel | Provenance, intervention, défaut connu. |

## Voisins dans le modèle

- **Monté sur** un [Enregistreur](C4%20-%20Enregistreur.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
