# C3 - Point d'écoute

Un point dans un site, identifié par un code court fourni par Vigie-Chiro.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| code | texte | exactement 2 caractères : 1 lettre + 1 chiffre | Ex. `A1`, `C2`, `Z4`. |
| coordonnées GPS | décimal × 2 | optionnel | Utile pour le calcul astronomique (lever/coucher soleil) en COULD. |
| descriptif | texte | optionnel, ≤ 500 car. | « En lisière de bois, au-dessus du chemin », etc. |

## Règles applicables

- [R2](Règles%20métier.md#r2) - format du code (lettre + chiffre).

## Voisins dans le modèle

- **Contenu dans** un [Site de suivi](C2%20-%20Site%20de%20suivi.md).
- **Fait l'objet de** 0..N [Passages](C5%20-%20Passage.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
