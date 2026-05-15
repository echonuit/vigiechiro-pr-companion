# C4 - Enregistreur

Le matériel utilisé pour la nuit (Passive Recorder). L'application en mémorise l'identité pour suivre la santé du matériel dans le temps.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° de série | texte | unique, format libre | Lu dans le journal du capteur (`LogPR<n>.txt`). Ex. `1925492`. |
| modèle / version | texte | optionnel | Ex. `V1.01, T4.1` extrait du journal du capteur. |
| commentaire libre | texte | optionnel | Anomalies récurrentes, dates de remise en état, etc. |

## Voisins dans le modèle

- **A produit** 1..N [Passages](C5%20-%20Passage.md).
- Son identité est lue depuis le [Journal du capteur](C9%20-%20Journal%20du%20capteur.md) embarqué.

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
