# Contraintes techniques

L'application doit respecter les contraintes suivantes :

## Plate-forme et langage

- **Indépendance plate-forme** : l'application doit fonctionner sans modification sur Windows 10/11, Linux (Ubuntu/Debian récents) et macOS récent. Le choix Java 25 / JavaFX 25 induit mécaniquement une exigence de JDK/JRE compatible, ce qui peut écarter les machines plus anciennes encore courantes dans la communauté VigieChiro - **ce n'est pas un point que vous aurez à vérifier** dans le cadre de la SAE.
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

## Composants fournis par l'équipe pédagogique

Certaines briques techniques lourdes sont fournies pour vous permettre de vous concentrer sur la chaîne métier :

- **Vue audio (sonogramme + spectrogramme)** : composant JavaFX qui prend un fichier WAV en entrée et affiche le sonogramme (amplitude/temps) + le spectrogramme (fréquence/temps) avec **cursor de lecture synchronisé** et **boutons de zoom temps/fréquence**. Le calcul FFT et le rendu graphique sont à l'intérieur du composant — vous l'instanciez avec un chemin de fichier et vous écoutez ses évènements. Utilisé dans [M-Qualification](Analyse%20et%20conception/Maquettes/M-Qualification.md) et [M-Vision-Tadarida](Analyse%20et%20conception/Maquettes/M-Vision-Tadarida.md). Cela élimine la nécessité d'implémenter une FFT (`JTransforms`) et un rendu Canvas performant — vous vous concentrez sur l'intégration et la synchronisation avec le reste de l'application.

## Licence et publication

- **Licence open-source libérale** (Apache-2.0 ou MIT) pour permettre la réutilisation des composants par la communauté VigieChiro.
- Le code source est hébergé sur **GitHub** dans un dépôt créé via le lien GitHub Classroom communiqué au démarrage de la SAE.

## Données

- L'application travaille sur des **fichiers locaux** : un dossier de session importé doit pouvoir contenir plusieurs giga-octets de WAV sans dégrader notablement les performances de l'IHM. À titre indicatif, **une grosse nuit peut peser ~40 Go par enregistreur** ; le client cible peut déployer **jusqu'à 24 enregistreurs en parallèle** pendant 40 à 50 nuits sur une saison estivale (volumétrie cumulée de l'ordre de plusieurs To).
- Les **CSV d'observations** suivent le format produit par Tadarida : séparateur point-virgule, champs entre guillemets quand nécessaire, encodage UTF-8 (cf. exemples dans [`transformes/`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion-exemple-nuit/tree/main/transformes) du dépôt d'échantillon). Sur une séquence de 5 s ralentie ×10, Tadarida peut produire **plusieurs lignes** (une par espèce distincte identifiée), avec timing début/fin précis dans la séquence.
- Les **WAV bruts** sont au format PCM 16 bits, mono, 384 kHz. Les **WAV transformés** déposés sur Vigie-Chiro sont des séquences de 5 s **déjà ralenties ×10** (signal ramené dans la bande audible) : c'est ce fichier qui est lu en lecture normale dans l'IHM, pas un ralentissement appliqué à la volée.

