# C8 - Séquence d'écoute

Un fichier audio dérivé d'un enregistrement original par **découpage régulier en tranches de 5 s réelles**, chaque tranche étant ensuite **ralentie ×10** (expansion de temps). C'est ce fichier qui est **audible par l'oreille humaine** (les ultrasons étant ramenés dans la bande audible) et c'est ce que l'utilisateur écoute dans l'application. C'est également ce fichier qui est déposé sur Vigie-Chiro et que Tadarida analysera.

!!! warning "5 s d'enregistrement, 50 s d'écoute"

    Le découpage porte sur le signal **brut**, pas sur le signal déjà ralenti. Une séquence contient donc **5 s d'enregistrement** et **dure 50 s à l'écoute** une fois ralentie ×10. C'est l'unité de découpage du pipeline Vigie-Chiro / Tadarida : les temps de l'`observations.csv` sont exprimés en **secondes réelles à l'intérieur de cette tranche de 5 s**.

    La **durée** stockée pour une séquence est la durée **réelle** (5 s), pas la durée d'écoute. C'est cette durée qui est cumulée pour donner la **durée enregistrée** d'un passage ([M-Passage](../Maquettes/M-Passage.md)) : une nuit de 3 614 séquences vaut 5 h 1 min enregistrées, et non 30 min.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | suffixe `_000`, `_001`… ajouté au nom de l'enregistrement original | Ex. `Car…_20260422_202623_000.wav`. |
| enregistrement original source | référence | obligatoire | Pour la traçabilité. |
| index dans le source | entier | ≥ 0 | Ordre de la séquence dans l'enregistrement original. |
| offset temporel dans le source | décimal (s) | calculé | Position de la séquence dans l'enregistrement original, en secondes **réelles** (avant ×10) : la séquence d'index `k` commence à `k × 5 s`. |
| durée | décimal (s) | typiquement 5 s | Durée **réelle** enregistrée (la séquence dure ×10 à l'écoute, soit 50 s). La dernière séquence d'un enregistrement peut être plus courte. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `transformes/` de la session d'enregistrement (cf. [R22](Règles%20métier.md#r22)). |
| inclus dans la sélection d'écoute | booléen | par défaut `false` | Mis à `true` si la séquence est sélectionnée pour la vérification d'enregistrement. |

## Règles applicables

- [R8](Règles%20métier.md#r8) - suffixe `_000`, `_001`… inséré entre nom de base et extension.
- [R10](Règles%20métier.md#r10) - tranches de 5 s réelles, ralenties ×10 ; `ceil(durée_source / 5)` séquences.
- [R11](Règles%20métier.md#r11) - transformation déterministe (même entrée → même sortie au bit près).
- [R22](Règles%20métier.md#r22) - emplacement sur disque : sous-dossier `transformes/` de la session d'enregistrement.

## Voisins dans le modèle

- **Contenue dans** une [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md).
- **Dérivée d'**un [Enregistrement original](C7%20-%20Enregistrement%20original.md).
- **Portée par** 0..N [Sélections d'écoute](C11%20-%20Sélection%20d%27écoute.md).
- **Source de détection** pour 0..N [Observations](C13%20-%20Observation.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
