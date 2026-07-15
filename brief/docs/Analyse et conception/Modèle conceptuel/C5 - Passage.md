# C5 - Passage

L'unité métier centrale : une nuit complète d'enregistrement sur un point d'un site, avec un enregistreur, lors d'un n° de passage donné dans une année.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° de passage | entier | typiquement 1 ou 2 | Le protocole impose deux passages annuels (cf. [R3](Règles%20métier.md#r3)). |
| année | entier | 4 chiffres | Ex. 2026. |
| date d'enregistrement | date | obligatoire | Date du **soir** où l'enregistrement démarre. |
| heure de début | heure | obligatoire | Lue du journal du capteur. |
| heure de fin | heure | obligatoire | Lue du journal du capteur. |
| paramètres d'acquisition | structure | extraits du journal du capteur | Fe, FL, FPH, S.R., gain, bande de fréquence, durée enregistrement, seuil SD. Sérialisés tels quels. |
| statut d'avancement | énum | `Importé` / `Transformé` / `Vérifié` / `Prêt à déposer` / `Déposé` | Progression de la chaîne pré-VigieChiro. |
| verdict final de vérification | énum | `Non vérifié` / `OK` / `Utilisable` / `Inexploitable` | **Dérivé** des verdicts par fichier son de la [sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md), **surchargeable** à la main. Un passage `Inexploitable` ne peut pas être déposé ([R14](Règles%20métier.md#r14)). |
| commentaire de session | texte | optionnel, ≤ 2000 car. | Météo, intervention humaine, anomalie matérielle, etc. |
| données météo structurées | structure | optionnelles | T° début/fin nuit, couverture nuageuse, vent. À aligner sur les champs Vigie-Chiro pour faciliter le dépôt. |
| date de dépôt sur Vigie-Chiro | datetime | optionnelle | Tracée au dépôt. |

> **Note importante** : ce que les anciennes maquettes appelaient « session » est désormais nommé **passage** pour rester cohérent avec le vocabulaire Vigie-Chiro.

## Règles applicables

- [R3](Règles%20métier.md#r3) - fenêtres temporelles des passages 1 et 2 (alerte sans bloquer).
- [R4](Règles%20métier.md#r4) - intervalle ≥ 1 mois entre les deux passages d'un site.
- [R5](Règles%20métier.md#r5) - unicité du quadruplet `(Site, Point, Année, n° de passage)`.
- [R14](Règles%20métier.md#r14) - un passage `Inexploitable` ne peut pas être déposé.

## Voisins dans le modèle

- **Sur** un [Point d'écoute](C3%20-%20Point%20d%27écoute.md).
- **Produit par** un [Enregistreur](C4%20-%20Enregistreur.md).
- **Produit** exactement 1 [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md).
- **À vérifier par** 0..1 [Sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md).
- **Annoté par** 0..1 [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
