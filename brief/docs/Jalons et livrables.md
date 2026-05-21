# Jalons et livrables

## Phases

La SAE 2.01 est une **SAE de développement**. La phase d'analyse et de conception qui précède habituellement le développement est **prise en charge par l'équipe pédagogique** et fournie aux groupes étudiants en début de SAE sous la forme du dossier [`Analyse et conception/`](Analyse%20et%20conception/index.md). Vous travaillez à partir de ce dossier comme s'il vous avait été remis par un client à l'issue d'une phase d'expression du besoin déjà menée.

Le projet est ainsi organisé en **2 phases** :

- **Phase 1** : Développement et validation
- **Phase 2** : Mise en service et soutenance finale

À la fin de chaque phase, les groupes d'étudiants devront produire un ensemble de livrables qui seront évalués par l'équipe pédagogique. Ces livraisons marquent les jalons du projet.

La SAE 2.01 est commune aux modules R2.02 et R2.03, tous deux assurés par le même enseignant. L'évaluation se répartit ainsi :

- Le code livré en **phase 1** est évalué simultanément pour **R2.02 (Développement IHM JavaFX)** et **R2.03 (Qualité de développement)** : la qualité du code, la testabilité, la lisibilité, l'hygiène Git pèsent autant que la complétude fonctionnelle.
- La **phase 2** (soutenance et démonstration) compte pour les deux modules ainsi que pour le portfolio.

> 📐 Le dossier d'analyse et de conception qui vous est fourni n'est **pas un cahier des charges figé**. Vous pouvez (et même devez) discuter ses partis pris avec l'équipe pédagogique si vous identifiez une incohérence ou une amélioration manifeste. La capacité à challenger une spécification fait partie des compétences évaluées.

## Livrables

### Phase 1 - Développement et validation

La phase 1 constitue le développement à proprement parler des fonctionnalités du périmètre fonctionnel défini dans le dossier [`Analyse et conception/Périmètre MVP.md`](Analyse%20et%20conception/Périmètre%20MVP.md), ainsi que la validation de chacune d'entre elles.

Le livrable de cette phase est un **dépôt Git** contenant l'ensemble du code source de l'application. On y retrouvera au minimum :

- Le **code métier** : ensemble des classes représentant les entités du domaine cartographiées dans [`Analyse et conception/Modèle conceptuel/`](Analyse%20et%20conception/Modèle%20conceptuel/index.md) (Site de suivi, Point d'écoute, Passage, Session d'enregistrement, Enregistrement original, Séquence d'écoute, Sélection d'écoute, etc.).
- La **couche d'accès aux données** réalisée avec JDBC sur SQLite (cf. [Contraintes techniques](Contraintes%20techniques.md) et [E0 Fondations de persistance](Analyse%20et%20conception/Story%20mapping/E0%20-%20Fondations%20de%20persistance.md)).
- Le **code de l'IHM** en JavaFX, préférentiellement en FXML, qui implémente la chaîne fil rouge ([P1](Analyse%20et%20conception/Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) à [P4](Analyse%20et%20conception/Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)) au minimum.
- La **couche d'import/transformation** : parsing du `LogPR*.txt` et du `THLog.csv`, copie protégée des WAV depuis la SD, renommage avec le préfixe `CarXXXXXX-AAAA-PassN-YY-`, transformation en séquences ralenties ×10 + chunks 5 s ([E2](Analyse%20et%20conception/Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md)).
- Si la cible étirable est livrée : import des CSV Tadarida et production du CSV `*_Vu.csv` ([E7](Analyse%20et%20conception/Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md)).
- Les **tests** (unitaires et d'IHM avec TestFX) avec un taux de couverture pertinent sur le code métier et les couches d'accès aux données.
- Un **README.md** clair expliquant comment installer, lancer et utiliser l'application.

Au-delà de la complétude fonctionnelle, **la qualité du code** sera prise en compte dans l'évaluation : lisibilité, structuration en couches, respect des principes vus en R2.03, présence et pertinence des tests, hygiène de l'historique Git.

### Phase 2 - Soutenance finale

La phase 2 est l'étape finale du projet. Vous y ferez la démonstration de votre réalisation et la synthèse du travail effectué.

Le livrable de cette phase est constitué de :

- Une **soutenance orale de 10 minutes** pendant laquelle chaque membre de l'équipe devra prendre la parole et sera interrogé sur les apprentissages critiques des modules R2.02 et R2.03.
- Un **diaporama** de synthèse du travail réalisé.
- Une **démonstration en direct** de votre application sur le jeu de données fourni.
