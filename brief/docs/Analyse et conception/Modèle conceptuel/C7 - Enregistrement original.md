# C7 - Enregistrement original

Un fichier audio sortant directement de l'enregistreur, après **copie protégée** et **renommage** par l'application avec le préfixe `CarXXXXXX-AAAA-PassN-YY-`. Ces fichiers sont conservés intacts et servent de référence ultime. Le PR captant en bande ultrasonore 8-120 kHz, **la grande majorité du contenu est inaudible à l'oreille humaine** ; certains fichiers peuvent toutefois contenir des sons audibles parasites (oiseaux nocturnes, insectes stridulants, vent, intempéries). Les enregistrements originaux ne sont pas écoutés directement dans l'application : seules les [séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md) ralenties ×10 sont exploitables pour la validation.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | format `Car<carre>-<annee>-<passage>-<point>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` | Ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `bruts/` de la session **si les originaux sont conservés** (cf. [R22](Règles%20métier.md#r22)) ; sinon le chemin **sur la carte de l'utilisateur**, qui vaut alors comme **provenance** et non comme emplacement : la carte sera démontée. Aucun parcours de récupération ne s'appuie sur ce chemin - l'identité d'un original se prouve par son empreinte. |
| durée | décimal (s) | typiquement 2-30 s | Déclenchée par seuil sur l'enregistreur. |
| échantillonnage | entier (Hz) | 384 000 | Mono 16 bits. |
| taille | entier (octets) | optionnelle | `size_bytes` : pré-contrôle rapide avant le hachage complet, pour la preuve d'identité à la réactivation. |
| empreinte SHA-256 | hex | optionnelle | Si l'on veut garantir l'intégrité bit-à-bit dans le temps. |

## Règles applicables

- [R6](Règles%20métier.md#r6) - préfixe `CarXXXXXX-AAAA-PassN-YY-` (tirets du 6).
- [R7](Règles%20métier.md#r7) - suffixe original de l'enregistreur conservé.
- [R9](Règles%20métier.md#r9) - copie protégée (aucune écriture sur la SD).
- [R22](Règles%20métier.md#r22) - emplacement sur disque : sous-dossier `bruts/` de la session, **quand les originaux sont conservés** (option de ré-analyse, absente par défaut).

## Voisins dans le modèle

- **Contenu dans** une [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md).
- **Découpé en** 1..N [Séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
