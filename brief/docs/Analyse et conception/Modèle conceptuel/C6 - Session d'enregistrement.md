# C6 - Session d'enregistrement

L'agrégat de données produit par un passage : tous les enregistrements originaux, toutes les séquences d'écoute dérivées, le journal du capteur et (si présent) le relevé climatique. Le mot « session d'enregistrement » désigne dans l'IHM ce que l'utilisateur a ramené du terrain pour une nuit donnée.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin racine | texte | obligatoire | Sous-dossier du workspace nommé selon le préfixe [R6](Règles%20métier.md#r6), structure imposée par [R22](Règles%20métier.md#r22). |
| volume total enregistrements originaux | entier (octets) | calculé | Indicatif (peut atteindre ~40 Go pour une grosse nuit). |
| volume total séquences d'écoute | entier (octets) | calculé | Typiquement légèrement supérieur aux enregistrements originaux (×10 en durée mais re-échantillonné). |

> **Colonnes retirées.** Le schéma a porté `archived_at` (V24) et `originals_purged_at` (V25), destinées à tracer un archivage et une purge des bruts. Ces gestes ont été **retirés** (ADR 0048 : « l'application observe, elle ne possède pas ») : n'étant plus ni lues ni écrites, les deux colonnes ont été **supprimées du schéma** par `V31` (#2429).

## Règles applicables

- [R9](Règles%20métier.md#r9) - copie protégée à l'import (aucune écriture sur la SD).
- [R21](Règles%20métier.md#r21) - une session d'enregistrement vit dans le workspace utilisateur (configurable, défaut `<Documents>/VigieChiro-Companion/`).
- [R22](Règles%20métier.md#r22) - structure d'une session d'enregistrement : nom du dossier = préfixe, sous-dossier `transformes/` (et `bruts/` **seulement si les originaux sont conservés**), journal et relevé à la racine.

## Voisins dans le modèle

- **Produite par** un [Passage](C5%20-%20Passage.md).
- **Contient** 1..N [Enregistrements originaux](C7%20-%20Enregistrement%20original.md).
- **Contient** 1..N [Séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md).
- **Référence** 1 [Journal du capteur](C9%20-%20Journal%20du%20capteur.md).
- **Référence** 0..1 [Relevé climatique](C10%20-%20Relevé%20climatique.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
