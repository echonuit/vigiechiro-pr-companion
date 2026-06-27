# Documentation développeur

Bienvenue dans la doc **technique** de VigieChiro PR Companion. Elle s'adresse à qui veut **lire,
modifier ou étendre le code** (humain comme assistant IA).

!!! tip "Vous cherchez plutôt à *utiliser* l'application ?"
    Voir la **[documentation utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/)**
    (prise en main, écrans, parcours). Le **besoin métier** d'origine est dans le
    **[brief](https://iutinfoaix-s201.github.io/brief/)**.

## La pile en bref

| Élément | Choix |
|---|---|
| Langage / runtime | **Java 25** (JDK standard, pas un JDK packagé FX) |
| IHM | **JavaFX 26** (dépendances Maven), *Headless Platform* pour les tests |
| Persistance | **SQLite** (fichier local), **DAO** en `PreparedStatement` (pas d'ORM) |
| Injection de dépendances | **Guice 7** |
| Build | **Maven Wrapper** `./mvnw` (aucune install de Maven) |
| Tests | **JUnit 5**, **AssertJ**, **Mockito**, **TestFX** (headless), **ApprovalTests** |
| Qualité | **PMD**, **Spotless** (Palantir), **JaCoCo**, **ArchUnit** |
| Licence | **GPL v3** |

## Le dépôt en un écran

```
vigiechiro-pr-companion/
├── src/main/java/fr/univ_amu/iut/   ← le code (architecture paquet-par-fonctionnalité, cf. Architecture)
├── src/main/resources/db/migration/ ← migrations SQLite versionnées (V0x__*.sql)
├── src/test/java/                   ← tests (métier, MVVM, TestFX, ArchUnit, e2e)
├── docs/ + mkdocs.yml               ← documentation UTILISATEUR (site séparé)
├── dev-docs/ + mkdocs-dev.yml       ← CETTE documentation développeur (site /dev/)
├── .github/workflows/               ← CI : maven, lint (qualité), docs, captures, release
├── .github/assets/                  ← aperçus PNG des écrans + harnais de capture
├── pom.xml                          ← build + profils (quality-gate, installer jpackage)
├── CONTRIBUTING.md · TESTING.md · SECURITY.md
└── README.md                        ← présentation produit + « Sous le capot »
```

## Démarrer en 30 secondes

Prérequis : un **JDK 25 standard** (Temurin / `25.0.2-open`). Tout passe par `./mvnw`.

```bash
git clone https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
./mvnw verify      # compile + tests + contrôles qualité (doit afficher BUILD SUCCESS)
./mvnw javafx:run  # lance l'application
```

!!! warning "Piège classique : `NPE PlatformFactory` en headless"
    Vous utilisez un JDK **packagé avec JavaFX** (type `fx-zulu`). Prenez un **JDK 25 standard** :
    JavaFX vient des dépendances Maven, pas du JDK. Détails dans
    [Tests et qualité](tests-et-qualite.md).

## Par où continuer

<div class="grid cards" markdown>

-   :material-sitemap: **[Architecture](architecture.md)**

    Paquet-par-fonctionnalité + MVVM, le socle `commun`, et les règles d'architecture vérifiées
    automatiquement (ArchUnit).

-   :material-plus-box: **[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md)**

    Le tutoriel pas à pas : du modèle à l'écran, en passant par la navigation inter-feature.

-   :material-test-tube: **[Tests et qualité](tests-et-qualite.md)**

    TestFX headless, taxonomie, ArchUnit, PMD/Spotless/JaCoCo, et ce qui bloque la CI.

-   :material-source-pull: **Contribuer**

    Le flux fork → branche → PR et les conventions de commit :
    [CONTRIBUTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md).

</div>

!!! info "Documents de référence au repo-root"
    [CONTRIBUTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)
    · [TESTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/TESTING.md)
    · [SECURITY.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/SECURITY.md)
    · [note de performance](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/docs/benchmarks/README.md).
    Ces pages restent la **source canonique** ; cette doc les met en perspective et les approfondit.
