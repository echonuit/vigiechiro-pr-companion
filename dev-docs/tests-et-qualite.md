# Tests et qualité

La chaîne qualité tourne à **chaque push** (CI) et localement via `./mvnw`. Cette page est la
référence structurée ; le repo-root garde un mémo
[**TESTING.md**](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/TESTING.md).

## Les commandes

| Commande | Effet |
|---|---|
| `./mvnw test` | **Toute** la suite de tests |
| `./mvnw test -Dtest=SitesViewModelTest` | Une seule **classe** de test |
| `./mvnw test -Dtest=SitesViewModelTest#chargeLesSites` | Une seule **méthode** |
| `./mvnw verify` | Build complet : tests + couverture + contrôles (PMD/JaCoCo **non** bloquants) |
| `./mvnw -Pquality-gate verify` | **Portail qualité** : PMD `failOnViolation` + seuils JaCoCo **bloquants** |
| `./mvnw -Pmutation test` | Tests de **mutation** PIT (lent, à la demande) |
| `./mvnw pmd:check` | Rapport PMD seul (rapide) |
| `./mvnw spotless:check` / `spotless:apply` | Vérifie / applique le formatage |
| `./mvnw javafx:run` | Lance l'application |

!!! tip "Quand lancer `clean` ?"
    Le build Maven est **incrémental** : `./mvnw verify` réutilise `target/`. Après certains
    changements, une classe périmée peut y subsister et provoquer une erreur **trompeuse** (à la
    compilation ou au packaging) que `./mvnw clean verify` fait disparaître. Réflexe : en cas
    d'erreur inexpliquée alors que le code semble correct, **relancer avec `clean`**. Cas typiques :

    - **suppression ou renommage** d'une classe/méthode : l'ancien `.class` reste dans `target/` ;
    - **changement de dépendances** (`pom.xml`).

    La CI part **toujours** d'un checkout propre : ce piège est purement **local**. Le cas du
    **packaging** est réglé depuis #1188 : le fat-jar est attaché sous le classifier `shaded`, le shade
    ne re-traite plus sa propre sortie (packaging **idempotent**, garde-fou en CI).

## IHM testée en *headless* (sans X11 ni xvfb)

Les tests **TestFX** tournent en mémoire grâce à la **Headless Platform** de JavaFX : aucun `xvfb`,
aucun display. La config vit dans le bloc Surefire du `pom.xml`, **rien à régler** :

```xml
<glass.platform>Headless</glass.platform>   <!-- plateforme Glass logicielle -->
<prism.order>sw</prism.order>               <!-- rendu logiciel -->
<java.awt.headless>true</java.awt.headless>
<testfx.robot>glass</testfx.robot>          <!-- robot piloté par Glass -->
```

On **ne met pas** `testfx.headless=true` : ce flag réactiverait l'ancien bootstrap Monocle. Le
headless vient de `glass.platform=Headless`, pas de TestFX. Le `argLine` ajoute les `--add-opens` /
`--add-exports` requis par JavaFX (accès aux internes `com.sun.javafx.*` pour TestFX) + l'agent JaCoCo
+ l'agent **Mockito** (voir ci-dessous).

!!! note "Préparation à Java 26+"
    Sous Java 25 tout passe, mais deux signaux annoncent la bascule 26 :

    - **Agent Mockito explicite** *(fait)* : Mockito 5 s'auto-attachait dynamiquement (« A Java agent
      has been loaded dynamically… will be disallowed by default in a future release »). On passe
      désormais `byte-buddy-agent` en `-javaagent` via `maven-dependency-plugin:properties`
      (`${net.bytebuddy:byte-buddy-agent:jar}` dans l'`argLine`) : plus d'auto-attachement, prêt pour
      le JDK 26.
    - **`sun.misc.Unsafe`** *(à surveiller)* : le warning vient de **Guava** (transitif), pas du code
      du projet. Il disparaîtra avec une montée de version de Guava ; rien à faire ici pour l'instant.

!!! danger "Lancer les tests avec le bon JDK"
    Utilisez un **JDK 25 standard** (`25.0.2-open` / Temurin), **pas** un JDK packagé FX (`fx-zulu`) :
    ce dernier embarque JavaFX 25, masque les jars Maven FX 26 et fait échouer le headless
    (`NPE com.sun.glass.ui.PlatformFactory.getPlatformFactory()` : la Headless Platform n'existe qu'en
    FX 26). Comme la CI :
    ```bash
    export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
    ```

## La taxonomie des tests

Les tests vivent sous `src/test/java/fr/univ_amu/iut/`, en **miroir** des paquets de production.

| Catégorie | Emplacement | Vérifie |
|---|---|---|
| Unitaires métier | `<feature>/model/`, `<feature>/dao/`, `commun/persistence/`, `commun/model/` | Entités, services, DAO, migrations. Sans JavaFX. |
| ViewModel | `<feature>/viewmodel/` | État observable + logique de présentation, sans composant graphique. |
| Intégration de vue (TestFX) | `<feature>/view/*VueIntegrationTest` | La vue FXML se lie au ViewModel et réagit (headless). |
| Bout en bout | `fr.univ_amu.iut.e2e.*`, `<feature>/e2e/Parcours*E2ETest` | Le scénario complet : IHM → ViewModel → service → base. |
| Architecture (ArchUnit) | `architecture/ArchitectureTest` | Les **6 règles** de frontière MVVM (cf. [Architecture](architecture.md)). |

Outils : **JUnit 5 + AssertJ + Mockito** ; **ApprovalTests** pour les sorties verbatim (CSV Tadarida
`_Vu` : le premier run produit un `*.received`, à approuver en `*.approved`).

## Les outils qualité

| Outil | Rôle | Bloquant ? |
|---|---|---|
| **ArchUnit** | Frontières MVVM + absence de cycles | Oui (tests) |
| **Spotless** (Palantir) | Format du code, via un *hook* pre-commit silencieux | Oui (`spotless:check` en CI) |
| **PMD** | *Code smells* | Bloquant **sous `-Pquality-gate`** |
| **JaCoCo** | Couverture | Seuils bloquants **sous `-Pquality-gate`** |
| **PIT** | Qualité des tests par **mutation** | Non (à la demande, `-Pmutation`) |

### Couverture et mutation

- **JaCoCo** : sous `-Pquality-gate`, seuils **bloquants** au niveau `BUNDLE` — **85 % de lignes** et
  **70 % de branches**. Les `**/outils/**` (capture d'écran, bancs de mesure) sont **exclus** : ils
  sont validés par exécution, pas par tests unitaires.
- **PIT** (`-Pmutation`) évalue si les tests **détectent** des mutations du code. Lent : à lancer à la
  demande, hors cycle normal.

## Ce qui bloque la CI

| Workflow | Commande | Bloquant ? |
|---|---|---|
| « Java CI » ([maven.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml)) — tests **+ couverture** | `./mvnw -B verify -Djacoco.haltOnFailure=true` | **Oui** |
| « Quality gate » ([lint.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/lint.yml)) — formatage | `./mvnw -B spotless:check` | **Oui** |
| « Quality gate » — portail PMD | `./mvnw -B -Pquality-gate compile pmd:check` | **Oui** |

`lint.yml` vérifie aussi la **complétude des captures** (cf. [Captures](captures.md)). Une PR doit
passer **les deux** workflows (cf. [CI/CD et release](ci-cd-release.md)).

Deux invariants sont en plus **verrouillés** : un test fige le **plan d'exécution** des requêtes O5
(l'index ne doit pas régresser, cf. [Performance et benchmarks](performance.md)), et les **garde-fous**
PMD / ArchUnit ne se désactivent **jamais** pour « faire passer » un build (cf.
[Sécurité et données sensibles](securite.md)).

## Écrire un nouveau test

- Un test de vue part d'un **injecteur** (réel ou partiel) Guice et d'une **base jetable** (workspace
  temporaire + `MigrationSchema.migrer()`), comme les `*VueIntegrationTest` / `*E2ETest` existants.
- **TestFX** : interroger les nœuds par `fx:id` (`lookup("#monId")`), piloter via le robot Glass,
  asserter avec AssertJ. Préférer **`bouton.fire()`** à un clic robot quand c'est possible (plus
  stable en headless).
- Pour une capture déterministe, voir
  [Ajouter une fonctionnalité §7](ajouter-une-fonctionnalite.md#7-ajouter-un-apercu-capture-decran).

### Deux pièges récurrents

!!! warning "`assertThat(path).endsWith(Path)` canonicalise"
    Cette forme appelle `toRealPath` et lève `NoSuchFileException` si le dossier n'existe pas (erreur
    sur runner neuf). Préférer le booléen **lexical** : `assertThat(p.endsWith(autre)).isTrue()`.

!!! warning "Mutation hors fil JavaFX"
    Un handler qui modifie l'IHM depuis un thread d'arrière-plan lève `Not on FX application thread`,
    souvent **avalée** (l'écran fige). Découper **préparation** (fil FX) / **exécution** (hors-thread)
    / retour sur le fil FX - c'est exactement le contrat du socle `ExecuteurTache` (#793, cf.
    [Patterns](patterns.md)), **synchrone en test** : avec lui, `bouton.fire()` rend l'état terminal
    observable au retour du clic, sans attente.
