# C7 - Enregistrement original

Un fichier audio sortant directement de l'enregistreur, après **copie protégée** et **renommage** par l'application avec le préfixe `CarXXXXXX-AAAA-PassN-YY-`. Ces fichiers sont conservés intacts et servent de référence ultime, mais ne sont jamais écoutés directement (ils sont en ultrason donc inaudibles).

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | format `Car<carre>-<annee>-<passage>-<point>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` | Ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `bruts/` de la capture. |
| durée | décimal (s) | typiquement 2-30 s | Déclenchée par seuil sur l'enregistreur. |
| échantillonnage | entier (Hz) | 384 000 | Mono 16 bits. |
| empreinte SHA-256 | hex | optionnelle | Si l'on veut garantir l'intégrité bit-à-bit dans le temps. |

## Règles applicables

- [R6](Règles%20métier.md#r6) - préfixe `CarXXXXXX-AAAA-PassN-YY-` (tirets du 6).
- [R7](Règles%20métier.md#r7) - suffixe original de l'enregistreur conservé.
- [R9](Règles%20métier.md#r9) - copie protégée (aucune écriture sur la SD).

## Voisins dans le modèle

- **Contenu dans** une [Capture](C6%20-%20Capture.md).
- **Découpé en** 1..N [Séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
