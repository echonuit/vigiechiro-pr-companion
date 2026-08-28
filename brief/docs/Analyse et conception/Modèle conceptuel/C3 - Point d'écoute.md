# C3 - Point d'écoute

Un point dans un site, identifié par un code court fourni par Vigie-Chiro. Le point peut être **choisi par l'utilisateur** au sein du carré (selon les contraintes du terrain), ou **tiré aléatoirement** par la plateforme Vigie-Chiro lorsque le protocole appliqué l'impose.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| code | texte | exactement 2 caractères : 1 lettre + 1 chiffre | Ex. `A1`, `C2`, `Z4`. |
| coordonnées GPS | décimal × 2 | optionnel ; si présentes, **dans l'emprise du carré** (R26) | Affichées sur la carte ; éditables au glisser, contraintes au carré. |
| descriptif | texte | optionnel, ≤ 500 car. | « En lisière de bois, au-dessus du chemin », etc. |
| synchronisé | booléen | par défaut `false` | `true` si le point a été **rapatrié** de la plateforme (grille STOC), `false` s'il a été **ajouté à la main** (colonne `synchronise`, #1738). Un point STOC rapatrié mais inutilisé est masqué par défaut. |
| commune | texte + code INSEE | dérivé, optionnel | Déterminée **automatiquement** des coordonnées GPS (API Géo) et mémorisée (table latérale `point_commune`, #2791) ; absente tant que non résolue (point sans GPS, hors ligne). Sert aux recherches et à l'export CSV ; département et région s'en dérivent. |

## Règles applicables

- [R2](Règles%20métier.md#r2) - format du code (lettre + chiffre).
- [R26](Règles%20métier.md#r26) - les coordonnées GPS tombent **dans l'emprise du carré** (carroyage national, 2 km) ; l'édition cartographique clampe le point à son carré.
- [R26bis](Règles%20métier.md#r26bis) - **sur une frontière**, les coordonnées satisfont R26 pour plusieurs carrés : le point tombe dans deux mailles, quatre à un coin, et le modèle ne tranche pas.
- [R27](Règles%20métier.md#r27) - un point **sans GPS** est situé au centre de son carré (position approchée, en éventail si plusieurs).
- [R28](Règles%20métier.md#r28) - un point qui **porte des passages** ne peut pas être supprimé.

## Voisins dans le modèle

- **Contenu dans** un [Site de suivi](C2%20-%20Site%20de%20suivi.md).
- **Fait l'objet de** 0..N [Passages](C5%20-%20Passage.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
