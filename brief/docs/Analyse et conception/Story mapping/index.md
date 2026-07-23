# Story mapping

Les [parcours utilisateurs](../Parcours%20utilisateurs/index.md) sont décomposés en **10 épopées**,
chacune contenant **2 à 8 stories**. La plupart des stories sont livrées, et le produit a été complété
par des écrans non prévus initialement (recherche globale, inventaire « Espèces & observations »).
**Certaines stories restent cependant des cibles non livrées ou partiellement livrées** et portent une
note en tête de fiche (non livrées : E1.S5, E5.S1, E5.S4, E5.S5, E6.S4, E6.S6, E8.S1 ; partielles :
E6.S2, E6.S5, E8.S2). Les écrans
réels sont décrits dans les [Maquettes](../Maquettes/index.md) et les
[Parcours utilisateurs](../Parcours%20utilisateurs/index.md) ; cette page en donne la **décomposition
fonctionnelle**, feature par feature.

Chaque story est :

- identifiée par un code (`E1.S2` = épopée 1, story 2),
- rattachée à un ou plusieurs parcours (et à des **maquettes cibles**),
- assortie de **critères d'acceptation** explicites.

## Vue d'ensemble des épopées

| Épopée | Titre | Parcours principal | Stories |
|---|---|---|---|
| [E0](E0%20-%20Fondations%20de%20persistance.md) | 🗄️ Fondations de persistance | (transverse, sert tout) | 11 |
| [E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md) | 🌐 Gérer ses sites et points de suivi | [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) | 5 |
| [E2](E2%20-%20Importer%20et%20transformer%20une%20nuit.md) | 📥 Importer et transformer une nuit | [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md) | 10 |
| [E3](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md) | 🎧 Vérifier la qualité d'enregistrement | [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md) | 8 |
| [E4](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md) | 📦 Préparer et tracer le dépôt VigieChiro | [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md) | 6 |
| [E5](E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md) | 🗂 Naviguer dans le volume multi-sites | [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md) | 6 |
| [E6](E6%20-%20Diagnostiquer%20le%20matériel.md) | 🩺 Diagnostiquer le matériel | [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md) | 6 |
| [E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md) | ✅ Valider les résultats Tadarida | [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md) | 9 |
| [E8](E8%20-%20Productivité%20avancée%20Tadarida.md) | 🚀 Productivité avancée Tadarida | [P9](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md), [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md), [P11](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20espèces%20détectées.md) | 3 |
| [E9](E9%20-%20Intégration%20plateforme%20VigieChiro.md) | ☁️ Intégration plateforme VigieChiro | [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), [P12](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md) | 5 |

## Capacités transverses livrées (sans story dédiée)

Deux capacités sont **livrées** mais ne se décrivent pas comme un parcours utilisateur ; elles sont mentionnées ici plutôt que découpées en stories :

- **Annonce de mise à jour applicative** : un bandeau signale, au démarrage, qu'une version plus récente est publiée (« La version X est disponible, vous utilisez la Y ») avec un lien vers cette version. L'annonce **reste silencieuse** tant qu'elle n'est pas sûre (version locale inconnue en développement, amont injoignable, locale déjà à jour).
- **CLI (43 sous-commandes)** : l'application expose une ligne de commande `vigiechiro <sous-commande>` couvrant l'équivalent de l'IHM (import, qualification, validation, dépôt, traitement serveur, publication, sauvegarde, audit, diagnostic, reconstruction...), pour le scripting et l'automatisation sur gros volume. La **parité CLI ↔ IHM** est une contrainte de conception.

## Convention des liens vers maquettes

Les **maquettes** sont produites (16, dans [Maquettes](../Maquettes/index.md)). Chaque story référence une ou plusieurs **maquettes cibles** par leur identifiant :

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
