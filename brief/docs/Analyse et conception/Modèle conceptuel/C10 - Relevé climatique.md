# C10 - Relevé climatique

Le journal de température et d'hygrométrie produit par la sonde embarquée de l'enregistreur. Optionnel : la sonde peut être absente ou défaillante. Stocké physiquement sous le nom de fichier `*_THLog.csv` que produit le firmware Teensy.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire si présent | Fichier `PaRecPR<sn>_THLog.csv` à la racine de la session d'enregistrement (cf. [R22](Règles%20métier.md#r22)). Ex. `PaRecPR1925492_THLog.csv`. |
| mesures | série temporelle | une mesure toutes les 600 s (10 min) | Date, heure, température (°C), humidité (%). |

## Règles applicables

- [R20](Règles%20métier.md#r20) - relevé climatique optionnel ; l'absence est signalée dans l'onglet diagnostic.
- [R22](Règles%20métier.md#r22) - emplacement sur disque : à la racine du dossier de la session d'enregistrement.

## Voisins dans le modèle

- **Référencé par** une [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) (cardinalité 0..1).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
