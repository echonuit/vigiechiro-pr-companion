# Personas

Trois personas de référence représentent les utilisateurs cibles du *VigieChiro Companion*. Marie et Karim sont composés à partir d'utilisateurs réels du programme VigieChiro pour rendre lisibles des différences d'usage. **Samuel correspond au client réel du produit** (Samuel Busson, CEREMA).

| | [Marie](Marie.md) | [Karim](Karim.md) | [Samuel](Samuel.md) ⭐ |
|---|---|---|---|
| Profil | Naturaliste retraitée bénévole | Chargé d'études en BE | Chercheur écologue (CEREMA) |
| Volume typique | 2-3 carrés VigieChiro / saison | 5+ PR en parallèle sur chantiers | 24 enregistreurs en parallèle × 40-50 nuits / saison estivale |
| Aisance numérique | Faible | Moyenne | Élevée |
| Priorité | Comprendre, ne pas se tromper | Productivité, traçabilité | Reproductibilité scientifique |

> 🎯 La persona **principale** pour les choix d'IHM est **Marie** : si l'application convient à Marie, elle conviendra aux deux autres. Karim et Samuel servent à éviter de tomber dans le piège « interface uniquement adaptée aux débutants », inutilisable au-delà de quelques sessions.
>
> ⭐ **Samuel est en plus le commanditaire réel** : il exprime le besoin, et son avis qualitatif sur l'application fait autorité. Sa première campagne (sur AudioMoth) a généré 560 000+ contacts qu'il a dû avaler avec des scripts ad hoc. Ses prochaines campagnes seront sur PR Teensy - sans outil propre, le travail manuel est ingérable.

## Pourquoi ces trois-là ?

- **Marie** porte l'objectif qualité [O2 - Facilité d'apprentissage](../../Objectifs%20qualités/Objectifs%20qualités/O2.md) et le scénario [SC1 - Onboarding](../../Objectifs%20qualités/Scénario/SC1.md). Elle est la **mesure d'utilisabilité**.
- **Karim** porte les objectifs de **performance** ([O3](../../Objectifs%20qualités/Objectifs%20qualités/O3.md), [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)) et de **modularité** ([O6](../../Objectifs%20qualités/Objectifs%20qualités/O6.md)) : il manipule plusieurs protocoles sur des chantiers commerciaux.
- **Samuel** porte les objectifs d'**intégrité** ([O7](../../Objectifs%20qualités/Objectifs%20qualités/O7.md)) et de **confidentialité** ([O8](../../Objectifs%20qualités/Objectifs%20qualités/O8.md)). Le volume de son protocole expérimental le rend particulièrement sensible aux pertes ou aux fuites, et la rigueur méthodologique attendue d'une thèse impose une trace complète des décisions de validation.

## Les anti-personas

L'application n'est pas conçue pour :

- Un **utilisateur web** voulant consulter en ligne sans rien installer (l'app est une application desktop mono-utilisateur, à installer localement ; elle dialogue avec la plateforme Vigie-Chiro pour le dépôt et la validation).
- Un **administrateur de base VigieChiro** souhaitant superviser la plateforme nationale (ce rôle est porté par la plateforme elle-même).
- Un **chercheur en deep learning** voulant entraîner ses propres classifieurs (Tadarida est en amont, hors périmètre).

Garder ces anti-personas en tête évite l'inflation fonctionnelle.
