# C8 - Séquence d'écoute

Un fichier audio dérivé d'un enregistrement original par **expansion de temps ×10** suivie d'un **découpage régulier en séquences de 5 s**. C'est ce fichier qui est **audible par l'oreille humaine** (les ultrasons étant ramenés dans la bande audible) et c'est ce que l'utilisateur écoute dans l'application. C'est également ce fichier qui est déposé sur Vigie-Chiro et que Tadarida analysera.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | suffixe `_000`, `_001`… ajouté au nom de l'enregistrement original | Ex. `Car…_20260422_202623_000.wav`. |
| enregistrement original source | référence | obligatoire | Pour la traçabilité. |
| index dans le source | entier | ≥ 0 | Ordre de la séquence dans l'enregistrement original. |
| offset temporel dans le source | décimal (s) | calculé | Position de la séquence dans l'enregistrement original (avant ×10). |
| durée | décimal (s) | typiquement 5 s | La dernière séquence d'un enregistrement peut être plus courte. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `transformes/` de la capture. |
| inclus dans la sélection d'écoute | booléen | par défaut `false` | Mis à `true` si la séquence est sélectionnée pour la vérification d'enregistrement. |

## Règles applicables

- [R8](Règles%20métier.md#r8) - suffixe `_000`, `_001`… inséré entre nom de base et extension.
- [R10](Règles%20métier.md#r10) - durée 5 s, ralenti ×10 ; `ceil(2 × durée_source)` séquences.
- [R11](Règles%20métier.md#r11) - transformation déterministe (même entrée → même sortie au bit près).

## Voisins dans le modèle

- **Contenue dans** une [Capture](C6%20-%20Capture.md).
- **Dérivée d'**un [Enregistrement original](C7%20-%20Enregistrement%20original.md).
- **Portée par** 0..N [Sélections d'écoute](C11%20-%20Sélection%20d%27écoute.md).
- **Source de détection** pour 0..N [Observations](C13%20-%20Observation.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
