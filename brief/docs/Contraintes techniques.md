# Contraintes techniques

L'application doit respecter les contraintes suivantes :

## Plate-forme et langage

- **Indépendance plate-forme** : l'application doit fonctionner sans modification sur Windows 10/11, Linux (Ubuntu/Debian récents) et macOS récent.
- **Langage** : **Java 25** (LTS de référence pour le semestre).
- **Interface graphique** : **JavaFX 25**, écrite préférentiellement en **FXML** pour pouvoir être modifiée sans toucher au code des contrôleurs.
- **Persistance** : couche d'accès aux données réalisée avec **JDBC** sur **SQLite** (driver `org.xerial:sqlite-jdbc`). Une session = un fichier `.sqlite` portable, lisible par DB Browser for SQLite ou par n'importe quel client tiers.

## Outillage et cycle de vie

- Cycle de vie géré avec **Maven**, via le **Maven Wrapper** (`./mvnw` ou `mvnw.cmd`). L'utilisateur ne doit pas avoir besoin d'installer Maven globalement.
- **Tests automatisés** avec JUnit 5, AssertJ et TestFX (pour les tests d'IHM). La couverture doit être suffisante pour donner confiance dans les régressions.
- **Intégration continue** sur GitHub Actions : à chaque push, les tests sont exécutés et le résultat est visible sur la PR. Une CI rouge interdit la fusion.
- **Outil de qualité de code** : Spotless (formatage Google Java Format) configuré et déclenché en pre-commit (cf. conventions R2.02).

## Distribution et exécution

- L'application doit être **exécutable depuis la ligne de commande** : `./mvnw javafx:run` doit suffire à la lancer dans un environnement Java 25 + JavaFX 25.
- Un mode **CLI minimal** (sans IHM) pour réaliser les opérations purement techniques (import d'une session, export d'un CSV) est apprécié mais pas obligatoire.

## Licence et publication

- **Licence open-source libérale** (Apache-2.0 ou MIT) pour permettre la réutilisation des composants par la communauté VigieChiro.
- Le code source est hébergé sur **GitHub** dans un dépôt créé via le lien GitHub Classroom communiqué au démarrage de la SAE.

## Données

- L'application travaille sur des **fichiers locaux** : un dossier de session importé doit pouvoir contenir plusieurs giga-octets de WAV sans dégrader notablement les performances de l'IHM.
- Les **CSV d'observations** suivent le format produit par Tadarida : séparateur point-virgule, champs entre guillemets quand nécessaire, encodage UTF-8 (cf. exemples dans [`samples/kal/`](https://github.com/IUTInfoAix-S201/brief/tree/main/samples/kal)).
- Les **WAV** sont au format PCM 16 bits, mono, 384 kHz. La lecture audio doit appliquer un **ralentissement** (typiquement ×10 ou ×20) pour ramener le signal dans la bande audible.
