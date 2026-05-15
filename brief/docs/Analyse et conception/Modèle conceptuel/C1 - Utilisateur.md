# C1 - Utilisateur

L'utilisateur unique de l'application. Mono-utilisateur, hors-ligne, pas de compte ni de mot de passe.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| identifiant local | UUID | unique, généré à l'install | Sert uniquement à associer les sites en base, jamais affiché |
| nom affiché | texte | optionnel, ≤ 60 car. | Cosmétique, repris dans la barre de titre |

## Voisins dans le modèle

- **Possède** ≥ 1 [Site de suivi](C2%20-%20Site%20de%20suivi.md) (cf. [Cardinalités](Cardinalités.md)).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
