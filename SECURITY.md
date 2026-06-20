# Politique de sécurité

VigieChiro PR Companion est un dépôt **pédagogique** (SAÉ 2.01, BUT Informatique). Cette politique
est proportionnée à ce contexte : elle couvre le **signalement**, la **gestion des secrets**, la
**sensibilité des données** manipulées, et l'**hygiène de la chaîne logicielle**.

Voir aussi [CONTRIBUTING.md](CONTRIBUTING.md) (fonctionnement du dépôt) et [TESTING.md](TESTING.md)
(suite de tests).

---

## 1. Signaler une vulnérabilité

Si vous identifiez une faille (dans le code, un workflow, ou une fuite de données), **ne l'ouvrez
pas en issue publique** :

- écrivez en privé à **[sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr)** ;
- décrivez le problème, son impact et, si possible, les étapes pour le reproduire.

Le périmètre supporté est la branche **`solution`** (référence) et la version qui en dérive
(`main`). Les forks étudiants ne sont pas maintenus individuellement.

---

## 2. Secrets et identifiants

- **Aucun secret n'est versionné** : pas de clé d'API, de jeton, ni de mot de passe dans le dépôt.
  N'en committez jamais (y compris dans un fichier de test, une capture d'écran ou une base SQLite).
- Les workflows GitHub Actions s'exécutent au **moindre privilège** : `maven.yml` déclare
  `permissions: contents: read`. N'élargissez les permissions que lorsqu'un workflow en a réellement
  besoin (par exemple `contents: write` pour `generate-student.yml`, qui doit pousser `main`).
- Les jetons de synchronisation Classroom (`CLASSROOM_SYNC_DISPATCH_TOKEN`) sont des **secrets
  GitHub** (Fine-Grained PAT), jamais en clair dans les workflows.

---

## 3. Sensibilité des données (chiroptères)

C'est le point réellement spécifique à ce projet. Les données de capture acoustique de
**chauves-souris** sont **sensibles sur le plan écologique** : la localisation précise de gîtes ou
de points d'écoute d'**espèces protégées** ne doit pas être diffusée publiquement.

- **Ne committez jamais de données terrain réelles** (enregistrements, journaux de capteur, relevés)
  ni de **coordonnées GPS** de sites sensibles dans ce dépôt.
- Le jeu de données d'**exemple** fourni est **dé-PII** (les fichiers Kaleidoscope contenant des
  métadonnées identifiantes ont été retirés) et publié sur **Zenodo**
  (DOI [10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247)).
- La base locale **`vigiechiro.db`** (SQLite, fichier) est un artefact de travail : elle ne doit
  contenir que des données d'exemple ou de test, jamais de données personnelles d'observateur ou de
  localisation réelle d'espèce protégée. Elle est ignorée par git.

---

## 4. Surface applicative

- **Pas d'injection SQL** : les DAO utilisent des `PreparedStatement` (requêtes paramétrées), jamais
  de concaténation de chaînes dans le SQL.
- **Pas d'authentification réseau** : l'application est **locale** (base SQLite fichier). Elle ne
  s'expose sur aucun port et ne stocke aucun identifiant.
- **Composant `audio-view`** : consommé depuis **Maven Central** (non réimplémenté). Comme toute
  dépendance, il fait partie de la chaîne d'approvisionnement (cf. §5).

---

## 5. Hygiène de la chaîne logicielle

- **Dependabot** ([.github/dependabot.yml](.github/dependabot.yml)) propose mensuellement les mises à
  jour `maven` et `github-actions`. Les bumps sont revus avant merge.
- **JavaFX (`org.openjfx:*`) est exclu** de l'automatisation : impact fort (rendu, *Headless
  Platform*), mises à jour décidées à la main.
- **Accès natifs cadrés** : sous Java 25 (accès natif strict), seuls les modules qui en ont besoin
  sont autorisés (`--enable-native-access=javafx.graphics`, `--enable-native-access=org.xerial.sqlitejdbc`).
- **CI sur runner self-hosted** : les forks étudiants (`IUTInfoAix-S201-2026`) tournent sur un runner
  **self-hosted** Linux. N'y exposez jamais de secret sensible ; traitez le code des PR de forks
  comme **non fiable** (il s'exécute sur l'infrastructure).

---

## 6. Bonnes pratiques pour les contributeur·rices

- Vérifiez votre identité git avant de committer (`git config user.email` institutionnel).
- Ne désactivez pas les garde-fous (PMD, ArchUnit, garde d'intégrité) pour « faire passer » un build.
- En cas de doute sur la diffusion d'une donnée ou d'une coordonnée : abstenez-vous et demandez.
