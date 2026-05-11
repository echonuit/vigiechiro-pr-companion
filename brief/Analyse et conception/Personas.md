# Personas

Trois personas de référence représentent les utilisateurs cibles du *VigieChiro PR Companion*. Chaque persona est inspiré d'utilisateurs réels du programme VigieChiro, mais composé pour rendre lisibles des différences d'usage.

| | [Marie](Personas/Marie.md) | [Karim](Personas/Karim.md) | [Léa](Personas/Léa.md) |
|---|---|---|---|
| Âge | 58 | 32 | 24 |
| Profil | Naturaliste retraitée bénévole | Chargé d'études BE | Doctorante écologie |
| Volume | 2-3 carrés / saison | 5+ PR en parallèle | Centaines de sessions |
| Aisance numérique | Faible | Moyenne | Élevée |
| Priorité | Comprendre, ne pas se tromper | Productivité, traçabilité | Profondeur d'analyse |

> 🎯 La persona **principale** est **Marie** : si l'application convient à Marie, elle conviendra aux deux autres. Karim et Léa servent à éviter de tomber dans le piège « interface uniquement adaptée aux débutants », inutilisable au-delà de quelques sessions.

## Pourquoi ces trois-là ?

- **Marie** porte l'objectif qualité [O2 - Facilité d'apprentissage](../Objectifs%20qualités/Objectifs%20qualités/O2.md) et le scénario [SC1 - Onboarding](../Objectifs%20qualités/Scénario/SC1.md). Elle est la **mesure d'utilisabilité**.
- **Karim** porte les objectifs de **performance** ([O3](../Objectifs%20qualités/Objectifs%20qualités/O3.md), [O5](../Objectifs%20qualités/Objectifs%20qualités/O5.md)) et de **modularité** ([O6](../Objectifs%20qualités/Objectifs%20qualités/O6.md)) : il manipule plusieurs protocoles sur des chantiers commerciaux.
- **Léa** porte les objectifs d'**intégrité** ([O7](../Objectifs%20qualités/Objectifs%20qualités/O7.md)) et de **confidentialité** ([O8](../Objectifs%20qualités/Objectifs%20qualités/O8.md)). Son volume de données la rend particulièrement sensible aux pertes ou aux fuites.

## Les anti-personas

L'application n'est pas conçue pour :

- Un **utilisateur web** voulant consulter en ligne sans rien installer (l'app est desktop, mono-utilisateur, hors-ligne).
- Un **administrateur de base VigieChiro** souhaitant superviser la plateforme nationale (ce rôle est porté par la plateforme elle-même).
- Un **chercheur en deep learning** voulant entraîner ses propres classifieurs (Tadarida est en amont, hors périmètre).

Garder ces anti-personas en tête évite l'inflation fonctionnelle.
