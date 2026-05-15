# C6 - Capture

L'agrégat de données produit par un passage : tous les enregistrements originaux, toutes les séquences d'écoute dérivées, le journal du capteur et (si présent) le relevé climatique. Le mot « capture » désigne dans l'IHM ce que l'utilisateur a ramené du terrain pour une nuit donnée.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin racine | texte | obligatoire | Dossier sur le disque local de l'utilisateur. |
| volume total enregistrements originaux | entier (octets) | calculé | Indicatif (peut atteindre ~40 Go pour une grosse nuit). |
| volume total séquences d'écoute | entier (octets) | calculé | Typiquement légèrement supérieur aux enregistrements originaux (×10 en durée mais re-échantillonné). |

## Règles applicables

- [R9](Règles%20métier.md#r9) - copie protégée à l'import (aucune écriture sur la SD).

## Voisins dans le modèle

- **Produite par** un [Passage](C5%20-%20Passage.md).
- **Contient** 1..N [Enregistrements originaux](C7%20-%20Enregistrement%20original.md).
- **Contient** 1..N [Séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md).
- **Référence** 1 [Journal du capteur](C9%20-%20Journal%20du%20capteur.md).
- **Référence** 0..1 [Relevé climatique](C10%20-%20Relevé%20climatique.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
