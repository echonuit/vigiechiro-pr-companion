# Calendrier de travail

## Calendrier 2026 confirmé

| Jalon | Date | Détail |
|---|---|---|
| Présentation du projet | **vendredi 22/05/2026** | Lancement officiel. À partir de cette date les équipes connaissent le brief. |
| Amorçage en parallèle d'autres modules | 22/05 → 31/05 | **6 jours ouvrés**. Activités légères : lecture du brief, formation des équipes, premières estimations, **assemblage du PR de l'équipe** si pièces reçues à temps, première nuit d'enregistrement test. |
| Démarrage du travail exclusif sur la SAE | **lundi 01/06/2026** | Plus aucun autre module en parallèle. |
| Sprint 1 (développement intensif) | 01/06 → 09/06 | **7 jours ouvrés**. Cible : chaîne fil rouge MUST ([P1](Analyse%20et%20conception/Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) → [P4](Analyse%20et%20conception/Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)). |
| Sprint 2 (dev + finition + soutenance) | 10/06 → 17/06 | **6 jours ouvrés**. Finition MUST + SHOULD opportunistes (E5/E6/E7 selon vélocité) + préparation soutenance. |
| Code freeze + diaporama | **jeudi 18/06/2026 matin** | Livraison code source et diaporama de soutenance. |
| Test individuel | 18/06 après-midi | Test individuel évaluant les apprentissages R2.02 / R2.03 (modalités précisées en cours). |
| Soutenance orale + démo | **18/06 après le test individuel** | Démonstration de l'application sur le jeu de données fourni, devant l'équipe pédagogique et **Samuel Busson** (client réel). |

## Rendus

- **Code source** : poussé sur GitHub via le lien Classroom communiqué au démarrage du projet. La branche `main` doit être stable et la CI verte au moment du code freeze.
- **Diaporama de soutenance** : déposé sur l'espace AmeTICE de la SAE 2.01 avant le 18/06 matin.

## Conséquences sur le périmètre

13 jours ouvrés de développement exclusif, c'est **court** au regard de l'ambition du MVP. Lisez attentivement le [Périmètre MVP](Analyse%20et%20conception/Périmètre%20MVP.md) et notamment la section « Évolutions du périmètre » : la chaîne fil rouge est une **cible idéale exigeante**, pas une obligation absolue. Une démo convaincante de bout-en-bout (même si certaines étapes sont simulées) + un plan d'action explicite sur ce qui reste comptent autant qu'un MUST totalement livré.

## Encadrement

- Permanences enseignantes pendant les créneaux SAE inscrits à l'emploi du temps de la phase exclusive (01/06 → 17/06).
- Les groupes peuvent solliciter un point de revue intermédiaire à tout moment via le canal de communication officiel de la promotion.
- En dehors des séances de SAE, les enseignants ne s'engagent pas à répondre dans la journée. Anticipez vos questions, surtout pour les points techniques bloquants.

## Risque calendrier à anticiper

- **Pièces détachées du PR** : la construction du PR de chaque équipe pendant l'amorçage (22/05 → 31/05) suppose que les composants soient arrivés à temps. Si l'arrivage est en retard, l'assemblage est repoussé sans impact bloquant sur le développement (le [jeu de données fourni](index.md#donnees-dexemple-fournies) permet de travailler indépendamment).
- **Téléchargement du full dataset** : la nuit complète est archivée sur Zenodo (DOI [10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247), lien permanent). Récupérez-la **dès le démarrage** pour pouvoir tester sur les volumes réels.
