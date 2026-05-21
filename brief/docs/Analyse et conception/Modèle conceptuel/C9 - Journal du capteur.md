# C9 - Journal du capteur

Le journal technique de l'enregistreur pour la nuit, lu et structuré par l'application. Stocké physiquement sous le nom de fichier `LogPR<n>.txt` que produit le firmware Teensy.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire | Fichier `LogPR<n>.txt` à la racine de la session d'enregistrement (cf. [R22](Règles%20métier.md#r22)). |
| évènements parsés | liste | structurée | Démarrage, paramètres, batterie, mises en veille, alarmes, anomalies. |
| anomalies détectées | liste | dérivée | Réveils non programmés, erreurs SD, redémarrages, batterie critique. |

> **À noter** : ce journal est circulaire (place limitée sur l'enregistreur), il efface de l'information au fur et à mesure quand la SD sature. L'ordre exact d'éviction reste à confirmer auprès du concepteur du firmware.

## Règles applicables

- [R19](Règles%20métier.md#r19) - journal circulaire, l'application exploite ce qui est présent sans tenter de reconstituer les pertes.
- [R22](Règles%20métier.md#r22) - emplacement sur disque : à la racine du dossier de la session d'enregistrement.

## Voisins dans le modèle

- **Référencé par** une [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md).
- Fournit l'identité de l'[Enregistreur](C4%20-%20Enregistreur.md) (n° de série, modèle).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
