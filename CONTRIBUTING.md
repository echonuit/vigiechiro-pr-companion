# Contribuer à VigieChiro PR Companion

Merci de votre intérêt ! Ce document explique comment proposer une évolution : mettre en place
l'environnement, respecter l'architecture, et soumettre une Pull Request.

Le détail des tests est dans [TESTING.md](TESTING.md) ; la politique de sécurité et de données dans
[SECURITY.md](SECURITY.md).

> 📖 **Documentation développeur** (architecture, navigation, persistance, injection, captures,
> CI/CD…, avec diagrammes) : **<https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/>**.
> Ce fichier reste le point d'entrée « contribution » ; la doc dev en est la version approfondie.

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

> 📖 En détail dans la doc dev :
> [Architecture](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/architecture/) ·
> [Navigation](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/navigation/) ·
> [Persistance](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/persistance/) ·
> [Injection](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/injection/) ·
> [Ajouter une fonctionnalité](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/ajouter-une-fonctionnalite/).

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

Le **type** du commit pilote la version publiée (cf. §7) : `feat:` → mineure, `fix:` → patch,
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

## 5. Cycle de vie d'un chantier

Un **chantier** est une évolution d'ampleur **EPIC**, répartie sur **plusieurs PR** (le §4 décrit
_une_ PR ; ici on décrit l'ensemble). Il **s'ouvre** par une analyse et **se clôt** par 10 passes.

**À l'ouverture** : cartographier l'existant (réutiliser les patterns en place plutôt que réinventer),
rédiger un plan, découper en **issues reliées à un EPIC**.

**À la clôture** (dans l'ordre) :

1. **Audit d'intégration** : vérifier que les évolutions de `main` survenues pendant le chantier n'ont
   rien laissé à rajouter (rebase, nouveaux points d'accroche à câbler, régressions).
2. **Cohérence CLI ↔ UI** : quand le chantier ajoute/change une **capacité métier**, la **CLI**
   (`fr.univ_amu.iut.cli`) doit exposer l'équivalent (même comportement) ; aligner si petit, sinon
   ouvrir une issue. « Sans objet » si le chantier est purement présentationnel.
3. **Doc développeur** (site `dev-docs/`) à jour.
4. **Doc utilisateur** (site `docs/`) + **captures** autant que nécessaire.
5. **Brief SAÉ** : répercuter dans le brief de la SAÉ 2.01 (dépôt `IUTInfoAix-S201/brief`) les
   évolutions qui changent ce qui est **attendu** ou **fourni** aux étudiants.
6. **Tests** : chaque usage couvert par des tests d'**intégration** (TestFX) et **E2E**.
7. **Harmonisation** : abstraire pour réduire **complexité** et **duplication** (Extract Class,
   patterns partagés).
8. **Revue visuelle** : **régénérer les captures** des écrans touchés et **les ouvrir**. Un geste testé
   n'est pas un écran regardé : un texte coupé, un glyphe absent ou une régression de style ne font
   rougir aucun test. C'est la passe précédente (CSS, socle) qui est la plus à même de casser un écran
   sans casser un test, d'où cette relecture **juste après** elle.
9. **Nouveaux chantiers** identifiés + **issues** créées.
10. **Bilan** : ce qui a été livré, dette restante, décisions.

> 📖 Raison d'être et mode opératoire de chaque passe, avec le **modèle de clôture** à coller dans
> l'EPIC : [doc dev · Cycle de vie d'un chantier](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/cycle-de-chantier/).

---

## 6. Intégration continue

Deux workflows se déclenchent à chaque push :

| Workflow | Rôle | Bloquant ? |
|---|---|---|
| [`maven.yml`](.github/workflows/maven.yml) | Build + tests headless **+ couverture** (`./mvnw verify -Djacoco.haltOnFailure=true`, seuils JaCoCo bloquants). | **Oui** |
| [`lint.yml`](.github/workflows/lint.yml) | Statique : **`spotless:check`** (formatage) + complétude des captures + **`-Pquality-gate compile pmd:check`** (PMD bloquant). | **Oui** |

Reproduire les contrôles **en local** (la CI les répartit sur les deux workflows) :

```bash
./mvnw -Pquality-gate compile pmd:check         # PMD bloquant (lint.yml)
./mvnw -B verify -Djacoco.haltOnFailure=true    # tests + couverture bloquante (maven.yml)
./mvnw spotless:check                           # formatage (lint.yml)
```

Les autres workflows : `capture-vues.yml` (régénère les aperçus de la doc), `docs.yml`
(construit/publie le site de documentation), `devcontainer-image.yml` (image GHCR pré-buildée).

> 📖 Carte complète des workflows et du portail qualité :
> [doc dev · CI/CD et release](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/ci-cd-release/).

---

## 7. Publier une version

Les **releases sont automatiques**, pilotées par les Conventional Commits (cf. §3). Sur la branche
`main`, à chaque ensemble de commits mergé :

- `feat:` déclenche une version **mineure**, `fix:` un **patch**, un `BREAKING CHANGE` une **majeure** ;
- **semantic-release** ([.releaserc.json](.releaserc.json)) calcule la version, crée le tag `vX.Y.Z`,
  la Release GitHub et met à jour le [CHANGELOG.md](CHANGELOG.md) ;
- le workflow [`release.yml`](.github/workflows/release.yml) construit alors les **installeurs natifs**
  (Linux `.deb`, macOS `.dmg` Apple Silicon, Windows `.msi`) via le profil `-Pinstaller`, et les
  attache à la Release (rendue publique seulement une fois **tous** les installeurs téléversés).

Vous n'avez donc **rien à taguer ni à versionner à la main** : merger des commits conventionnels suffit.

> **Activation.** Le workflow est **dormant** tant que la variable de dépôt `ENABLE_RELEASE` n'est pas
> à `true`. Première version : `v1.0.0`.

Construire un installeur **en local** (pour tester le packaging) :

```bash
./mvnw -Pinstaller -Djpackage.type=deb -DskipTests verify   # produit target/dist/
```

---

## 8. Dépendances

Les mises à jour sont gérées par **Dependabot** ([.github/dependabot.yml](.github/dependabot.yml)),
mensuellement, pour `maven` et `github-actions`. **JavaFX (`org.openjfx:*`) est volontairement
exclu** de l'automatisation : ses bumps ont un impact fort (rendu, Headless Platform, plugin
communautaire) et se décident à la main.

---

## 9. En cas de doute

- Un comportement du dépôt vous surprend ? Consultez d'abord les commentaires des fichiers cités
  ci-dessus : ils documentent les décisions (souvent le « pourquoi »).
- Une question : [sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr).
