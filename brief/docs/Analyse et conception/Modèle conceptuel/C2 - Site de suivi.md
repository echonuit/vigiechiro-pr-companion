# C2 - Site de suivi

Un site est créé hors application sur <https://vigiechiro.herokuapp.com/>. Il fournit le numéro de **carré** et la liste des **codes points** que l'utilisateur déclare ensuite dans l'app.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° carré | texte | exactement 6 chiffres | Les 2 premiers chiffres = département. Ex. `040962` (département 04 = Alpes-de-Haute-Provence). **Leading zero obligatoire** pour les départements 1 à 9. |
| nom convivial | texte | optionnel, ≤ 100 car. | Pour aider Marie à reconnaître son site (ex. « Étang de la Tuilière »). |
| protocole | énum | `PointFixe` (seul supporté MVP) | Architecture extensible (Pédestre, Routier… plus tard). |
| commentaire libre | texte | optionnel | Contexte écologique, descriptif paysager. |
| date de création | date | obligatoire | Date locale (importante pour les anniversaires de passage). |

## Règles applicables

- [R1](Règles%20métier.md#r1) - format du n° de carré (6 chiffres, leading zero départements 1-9).

## Voisins dans le modèle

- **Possédé par** un [Utilisateur](C1%20-%20Utilisateur.md).
- **Contient** ≥ 1 [Point d'écoute](C3%20-%20Point%20d%27écoute.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
