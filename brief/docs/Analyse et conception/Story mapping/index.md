# Story mapping

!!! note "Genèse pédagogique - au passé"
    Cette page documente le **découpage prévu** lors du cadrage de la SAE 2.01 (épopées, stories,
    estimations, arbitrage MoSCoW). Elle est conservée **pour mémoire**. L'application a finalement été
    menée **au-delà de ce périmètre** : toutes les épopées E1 à E8 ont été livrées (et complétées par
    des écrans non prévus, comme la recherche globale et l'inventaire « Espèces & observations »). Les
    écrans réels sont décrits, au présent, dans les [Maquettes](../Maquettes/index.md) et les
    [Parcours utilisateurs](../Parcours%20utilisateurs/index.md).

Le travail des [parcours utilisateurs](../Parcours%20utilisateurs/index.md) avait été décomposé en
**9 épopées**, chacune contenant **2 à 8 stories** [INVEST](https://fr.wikipedia.org/wiki/INVEST_(g%C3%A9nie_logiciel))
(Independent, Negotiable, Valuable, Estimable, Small, Testable).

Chaque story était :

- identifiée par un code (`E1.S2` = épopée 1, story 2),
- rattachée à un ou plusieurs parcours (et à des **maquettes cibles**),
- assortie de **critères d'acceptation** explicites,
- **estimée en complexité** sur une échelle d'étoiles (voir ci-dessous),
- **rattachée à un niveau MoSCoW** (MUST / SHOULD / COULD).

## Échelle de complexité

| Étoiles | Niveau | Effort indicatif |
|---|---|---|
| ★ | Trivial | ~1-2 h |
| ★★ | Simple | ½ journée |
| ★★★ | Moyen | 1 journée |
| ★★★★ | Significatif | 2 journées |
| ★★★★★ | Lourd | 3+ journées (à recouper si possible) |

Ces estimations sont **indicatives**. Vous les réviserez en équipe au début de la phase de développement (planning poker).

## Vue d'ensemble des épopées

| Épopée | Titre | Parcours principal | MoSCoW | Stories | Complexité totale |
|---|---|---|---|---|---|
| [E0](E0%20-%20Fondations%20de%20persistance.md) | 🗄️ Fondations de persistance | (transverse, sert tout) | ✅ MUST socle (S1-S5) + 🟠 SHOULD (S6-S7) + ⚪ COULD (S8) | 8 | ★★★★★ ★★★★★ ★★★★★ ★★★★★ ★ (21 ★) |
| [E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md) | 🌐 Gérer ses sites et points de suivi | [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) | ✅ MUST | 5 | ★★★★★ ★★★★★ (10 ★) |
| [E2](E2%20-%20Importer%20et%20transformer%20une%20nuit.md) | 📥 Importer et transformer une nuit | [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md) | ✅ MUST (S1-S6) + 🟠 SHOULD (S8) | 8 | ★★★★★ ★★★★★ ★★★★★ ★★★★★ ★★★ (23 ★) |
| [E3](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md) | 🎧 Vérifier la qualité d'enregistrement | [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md) | ✅ MUST (S1-S5) + 🟠 SHOULD (S6) | 6 | ★★★★★ ★★★★★ ★ (11 ★) |
| [E4](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md) | 📦 Préparer et tracer le dépôt VigieChiro | [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md) | ✅ MUST (S1-S3) + 🟠 SHOULD (S4) | 4 | ★★★★★ ★★★★ (9 ★) |
| [E5](E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md) | 🗂 Naviguer dans le volume multi-sites | [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md) | 🟠 SHOULD (S1-S2) + ⚪ COULD (S3-S5) | 5 | ★★★★★ ★★★★★ ★★★★ (14 ★) |
| [E6](E6%20-%20Diagnostiquer%20le%20matériel.md) | 🩺 Diagnostiquer le matériel | [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md) | 🟠 SHOULD (S1-S2) + ⚪ COULD (S3-S5) | 5 | ★★★★★ ★★★★★ ★★★ (13 ★) |
| [E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md) | ✅ Valider les résultats Tadarida | [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md) | 🟠 SHOULD (S1-S5, S7) + ⚪ COULD (S6) | 7 | ★★★★★ ★★★★★ ★★★★★ ★★★★★ ★★ (22 ★) |
| [E8](E8%20-%20Productivité%20avancée%20Tadarida.md) | 🚀 Productivité avancée Tadarida | [P9](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md), [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md) | ⚪ COULD (au mieux) | 2 | ★★★★★ ★★ (7 ★) |

## Convention des liens vers maquettes

Les **maquettes** sont en cours de refonte (étape ultérieure du dossier). Chaque story référence une ou plusieurs **maquettes cibles** par leur identifiant prévisionnel :

| ID | Écran |
|---|---|
| M-Sites | Vue mes sites de suivi (liste + ajout) |
| M-Site-detail | Détail d'un site (carré, points, passages enregistrés) |
| M-Import | Modale d'import enrichie (sélection site + point + passage) |
| M-Passage | Détail d'un passage (méta + qualification + diagnostic en onglet) |
| M-Qualification | Vue vérification d'enregistrement par échantillonnage |
| M-Lot | Préparation du dépôt |
| M-MultiSite | Tableau croisé Site × Passage |
| M-SonsValidation | Vue de validation taxonomique (cible étirée) |

Ces noms deviendront des **liens cliquables** quand les maquettes correspondantes seront produites.
