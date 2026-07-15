# Glossaire métier

Vocabulaire utilisateur posé dans le modèle conceptuel. Ces termes sont **ceux qui apparaissent dans l'IHM** ; on évite à dessein le jargon technique pour rester lisible par les utilisateurs cibles (cf. [O2 - Facilité d'apprentissage](../../Objectifs%20qualités/Objectifs%20qualités/O2.md)).

| Terme | Définition courte | Exemple / précision |
|---|---|---|
| **Site de suivi** | Unité géographique déclarée par l'utilisateur sur Vigie-Chiro web. Donne accès à un n° de carré et à un ensemble de points. | « Étang de la Tuilière », carré `040962`, points `A1`, `B2`, `C3`. |
| **Carré** | Code à 6 chiffres identifiant un site Vigie-Chiro. Les 2 premiers chiffres = département. | `040962` (carré 0962 du département 04). |
| **Point** | Code à 2 caractères (lettre + chiffre) identifiant un point d'écoute dans un site. | `A1`, `C2`, `Z4`. |
| **Passage** | Une nuit complète d'enregistrement sur un point d'un site, lors d'un n° de passage donné dans une année. | « Passage 2 du carré `640380` au point `Z1` en 2026 ». Anciennement appelé « session » dans les maquettes V1. |
| **Enregistreur** | Le matériel utilisé sur le terrain (Passive Recorder Teensy). Chaque enregistreur a un n° de série propre. | Enregistreur n° 1925492. |
| **Session d'enregistrement** | L'agrégat de données produit par un passage : enregistrements originaux, séquences d'écoute, journal du capteur, relevé climatique. | « La session d'enregistrement du 22/04/2026 sur le point Z1 ». |
| **Enregistrement original** | Fichier audio mono 16 bits 384 kHz produit par l'enregistreur, après copie protégée et renommage avec le préfixe `Car…-AAAA-PassN-YY-`. **Inaudible** sans transformation (signal ultrason). | `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`. |
| **Séquence d'écoute** | Fichier audio dérivé d'un enregistrement original, **découpé en tranches de 5 s réelles** puis **ralenti ×10** : une séquence porte 5 s d'enregistrement et dure 50 s à l'écoute. **Audible** par l'oreille humaine. C'est ce qui est déposé sur Vigie-Chiro et écouté dans l'application. | `Car…_20260422_202623_000.wav`, `…_001.wav`, etc. |
| **Vérification d'enregistrement** | Sound check global permettant à l'utilisateur de confirmer que la nuit est exploitable, avant le dépôt. Distinct de la validation taxonomique. | Marie écoute 15 séquences d'écoute réparties sur la nuit, ne détecte rien d'anormal, marque le passage `OK`. |
| **Verdict** | Jugement de qualité après vérification. **Par fichier son** écouté : `Bon` / `Mauvais` / `Inexploitable`. **Verdict final du passage** (dérivé, surchargeable) : `Non vérifié` / `OK` / `Utilisable` / `Inexploitable`. | Un passage `Inexploitable` ne peut pas être déposé. |
| **Sélection d'écoute** | Sous-ensemble de séquences d'écoute sélectionné automatiquement pour la vérification (méthode `RéparTemporel` par défaut). | 20 séquences prises uniformément entre l'heure de début et l'heure de fin de la nuit. |
| **Dépôt** | Ensemble des séquences d'écoute **d'un passage**, préparé et formaté selon les attentes du portail Vigie-Chiro, prêt à téléverser. Se fait **passage par passage** (jamais un regroupement de passages). | Sous-dossier `transformes/` de la session d'enregistrement (cf. [R22](Règles%20métier.md#r22)), à téléverser tel quel sur vigiechiro.herokuapp.com. |
| **Workspace** | Dossier racine sur le disque local de l'utilisateur où vivent toutes les sessions d'enregistrement, la base SQLite et les réglages. Configurable depuis les préférences (cf. [R21](Règles%20métier.md#r21)). | Défaut : `<Documents>/VigieChiro-Companion/`. Pour gros volumes, pointer un disque externe. |
| **Préfixe** | Chaîne `CarXXXXXX-AAAA-PassN-YY-` ajoutée en début de nom de fichier lors du renommage. | `Car640380-2026-Pass2-Z1-`. |
| **Tirets du 6** | Caractère `-` (U+002D HYPHEN-MINUS), à utiliser obligatoirement dans le préfixe (ni cadratin `-` ni demi-cadratin `-`). | Validation à la saisie. |
| **Expansion de temps** | Ralentissement temporel d'un facteur ×10, qui transpose les ultrasons (8-120 kHz) dans la bande audible (0,8-12 kHz) tout en allongeant leur durée. | 1 seconde d'enregistrement original devient 10 secondes de séquence d'écoute. |
| **Durée enregistrée** | Durée **réelle** captée par l'enregistreur sur un passage : la somme des durées des séquences d'écoute, comptées en secondes réelles (5 s par séquence pleine). À ne pas confondre avec la durée d'**écoute**, qui vaut ×10. | Une nuit de 3 614 séquences : 5 h 1 min enregistrées, mais environ 50 h à réécouter intégralement. |
| **Validation taxonomique** | Activité postérieure au retour Tadarida : revue espèce par espèce des observations classifiées, validation ou correction. | SHOULD du MVP. Cible étirable. |
| **Mode inventaire** | Variante de validation : on cherche la liste des espèces présentes, donc on arrête de valider une espèce une fois confirmée sur la nuit. | Karim sur un suivi rapide. |
| **Mode activité** | Variante de validation : on quantifie toutes les détections, donc toutes les observations doivent être passées en revue. | Samuel sur son protocole BACIP. |
| **Groupe taxonomique** | Niveau hiérarchique au-dessus du taxon (genre, famille, ordre) servant de filtre groupé. | `Myotis`, `Pipistrellus`, `Vespertilionidae`. |

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
