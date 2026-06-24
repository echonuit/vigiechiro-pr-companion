# Contribuer à VigieChiro PR Companion

Merci de votre intérêt ! Ce document explique comment proposer une évolution : mettre en place
l'environnement, respecter l'architecture, et soumettre une Pull Request.

Le détail des tests est dans [TESTING.md](TESTING.md) ; la politique de sécurité et de données dans
[SECURITY.md](SECURITY.md).

---

## 1. Mettre en place l'environnement

Tout passe par le **Maven Wrapper** `./mvnw` (aucune installation de Maven). Le JDK doit être un
**JDK 25 standard** (Temurin / `25.0.2-open`), **pas** un JDK packagé avec JavaFX : JavaFX 26 vient
des dépendances Maven, et la *Headless Platform* est purement logicielle (cf. [TESTING.md](TESTING.md)).

```bash
git clone https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
./mvnw verify        # premier appel : télécharge Maven + dépendances, puis tout est en cache
```

Au **premier `./mvnw`**, le plugin `git-build-hook` configure silencieusement
`core.hooksPath=.githooks`, ce qui active le hook **pre-commit** ([.githooks/pre-commit](.githooks/pre-commit)).
Ce hook **formate les `.java` stagés** avec **Spotless** (Palantir Java Format) avant chaque commit.

---

## 2. L'architecture : package-by-feature + MVVM

L'architecture est **package-by-feature + MVVM** (cf. [README §3](README.md#3-architecture--package-by-feature--mvvm)).
Chaque feature vit dans `src/main/java/fr/univ_amu/iut/<feature>/` et se découpe en `model/`,
`viewmodel/`, `view/`, `di/`.

Points d'attention :

- **Emplacement des `.fxml` / `.css`** : **à côté** de leur controller, dans
  `src/main/java/.../view/` (et non dans `src/main/resources`). Le `pom.xml` copie les fichiers
  non-Java de `src/main/java` dans `target/classes` au même chemin de paquetage. Les ressources
  **partagées** (migrations `db/migration`, thème) restent, elles, dans `src/main/resources`.
- **Câblage Guice** : la feature publie ses services / ViewModels via son module dans `di/` ;
  les controllers FXML sont injectés via la `controllerFactory`.
- **Respect des frontières MVVM** : elles sont **vérifiées par ArchUnit** (model sans JavaFX,
  viewmodel sans `javafx.scene/fxml/stage`, view sans JDBC, pas de dépendance vers le `view` /
  `viewmodel` d'une autre feature). Voir la liste complète dans [TESTING.md](TESTING.md).
- **`sites` est la feature de référence** : c'est le modèle à reproduire (ViewModel pur lié à une
  vue FXML).

Toute évolution de comportement doit être accompagnée de **tests** (de bout en bout quand c'est une
feature : IHM → ViewModel → service → base).

---

## 3. Conventions de code et de commit

**Code** :

- formatage **Spotless / Palantir Java Format** (le hook s'en charge ; sinon `./mvnw spotless:apply`) ;
- doc-comments **Markdown** `///` (JEP 467), pas de `/** */` HTML ;
- **noms de classes en français**, sans accents dans les identifiants (`Navigateur`, `Passage`,
  `EtapeNavigation`...) ;
- **pas de tiret cadratin** dans la doc et les commentaires : tiret simple ou deux-points.

**Commits** : [Conventional Commits](https://www.conventionalcommits.org/fr/) **en français**, le
scope étant le nom de la feature ou du domaine :

```
feat(passage): écran pivot d'une nuit (statut + navigation)
fix(importation): import hors fil JavaFX gelait l'écran
test(qualification): test d'acceptation du verdict de qualité
docs(readme): renvois vers CONTRIBUTING/TESTING/SECURITY
chore(deps): bump assertj 3.27.7
```

- **petits commits** logiques (un par préoccupation), message axé sur le **pourquoi** ;
- toujours **créer** un nouveau commit plutôt qu'amender.

Le **type** du commit pilote la version publiée (cf. §6) : `feat:` → mineure, `fix:` → patch,
`BREAKING CHANGE:` → majeure. Les autres types (`docs`, `chore`, `test`, `refactor`...) ne déclenchent
pas de release.

---

## 4. Workflow de contribution

```bash
# 1. Forkez le dépôt, puis :
git clone https://github.com/<vous>/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
git checkout -b feat/<feature>

# 2. ... code + tests ...
./mvnw verify                      # 0 échec, 0 skip indésirable
git commit ...                     # le hook formate le code

# 3. Poussez et ouvrez une PR vers la branche par défaut
git push -u origin feat/<feature>
gh pr create --fill
```

- La PR cible la **branche par défaut** du dépôt (`gh pr create --fill` la sélectionne
  automatiquement). `@nedseb` est ajouté en reviewer automatiquement ([CODEOWNERS](.github/CODEOWNERS)).
- Privilégier des **PR petites et séquentielles** (par exemple : ViewModel + tests, puis vue
  principale, puis vue secondaire) plutôt qu'une feature entière en un seul gros diff.
- Merger quand la CI est verte.
- Avant tout commit, vérifiez l'identité git : `git config user.email`.

---

## 5. Intégration continue

Deux workflows se déclenchent à chaque push :

| Workflow | Rôle | Bloquant ? |
|---|---|---|
| [`maven.yml`](.github/workflows/maven.yml) | Build + tests headless (`verify -DexcludedGroups=conformite`), puis une passe de conformité **non bloquante** et `spotless:check` (mesure). | **Oui** (tests hors conformité) |
| [`lint.yml`](.github/workflows/lint.yml) | Cohérence doc, complétude des captures, tests Bats, puis **`-Pquality-gate verify`** (PMD + seuils JaCoCo bloquants). | **Oui** |

Reproduire le portail qualité **en local** :

```bash
./mvnw -Pquality-gate verify       # PMD + couverture bloquants
./mvnw spotless:check              # formatage
```

Les autres workflows : `capture-vues.yml` (régénère les aperçus de la doc), `docs.yml`
(construit/publie le site de documentation), `devcontainer-image.yml` (image GHCR pré-buildée).

---

## 6. Publier une version

Les **releases sont automatiques**, pilotées par les Conventional Commits (cf. §3). Sur la branche
`main`, à chaque ensemble de commits mergé :

- `feat:` déclenche une version **mineure**, `fix:` un **patch**, un `BREAKING CHANGE` une **majeure** ;
- **semantic-release** ([.releaserc.json](.releaserc.json)) calcule la version, crée le tag `vX.Y.Z`,
  la Release GitHub et met à jour le [CHANGELOG.md](CHANGELOG.md) ;
- le workflow [`release.yml`](.github/workflows/release.yml) construit alors les **installeurs natifs**
  (Linux `.deb`, macOS `.dmg` arm64 et Intel, Windows `.msi`) via le profil `-Pinstaller`, et les
  attache à la Release (rendue publique seulement une fois **tous** les installeurs téléversés).

Vous n'avez donc **rien à taguer ni à versionner à la main** : merger des commits conventionnels suffit.

> **Activation.** Le workflow est **dormant** tant que la variable de dépôt `ENABLE_RELEASE` n'est pas
> à `true`. Première version : `v1.0.0`.

Construire un installeur **en local** (pour tester le packaging) :

```bash
./mvnw -Pinstaller -Djpackage.type=deb -DskipTests verify   # produit target/dist/
```

---

## 7. Dépendances

Les mises à jour sont gérées par **Dependabot** ([.github/dependabot.yml](.github/dependabot.yml)),
mensuellement, pour `maven` et `github-actions`. **JavaFX (`org.openjfx:*`) est volontairement
exclu** de l'automatisation : ses bumps ont un impact fort (rendu, Headless Platform, plugin
communautaire) et se décident à la main.

---

## 8. En cas de doute

- Un comportement du dépôt vous surprend ? Consultez d'abord les commentaires des fichiers cités
  ci-dessus : ils documentent les décisions (souvent le « pourquoi »).
- Une question : [sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr).
