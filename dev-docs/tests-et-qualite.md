# Tests et qualité

La chaîne qualité tourne à **chaque push** (CI) et localement via `./mvnw`. Cette page en donne la vue
d'ensemble ; le détail vit dans
[**TESTING.md**](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/TESTING.md)
(source canonique).

## Les commandes

| Commande | Effet |
|---|---|
| `./mvnw test` | Tests unitaires et d'intégration |
| `./mvnw verify` | Build complet : tests + couverture + contrôles |
| `./mvnw -Pquality-gate verify` | Build + **PMD bloquant** + seuils de couverture (le « portail qualité ») |
| `./mvnw spotless:apply` | Formate le code (Palantir Java Format) |
| `./mvnw javafx:run` | Lance l'application |

## IHM testée en *headless* (sans X11 ni xvfb)

Les tests d'interface utilisent **TestFX** sur la **Headless Platform** de JavaFX : le rendu se fait
**en mémoire**, sans serveur d'affichage. C'est pourquoi la CI n'a besoin **ni de `xvfb` ni d'un
display**. Les drapeaux clés (posés par la config de test / les outils de capture) :

```
-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true
```

!!! danger "Lancer les tests avec le bon JDK"
    Utilisez un **JDK 25 standard** (`25.0.2-open` / Temurin), **pas** un JDK packagé FX (`fx-zulu`) :
    ce dernier masque les jars JavaFX de Maven et provoque un `NPE PlatformFactory` (la Headless
    Platform n'existe que dans le JavaFX des dépendances). C'est aussi pour cela que la CI utilise un
    JDK standard.

## La taxonomie des tests

- **Métier / MVVM** : JUnit 5 + AssertJ, Mockito pour isoler les dépendances ; ApprovalTests pour les
  sorties tabulaires (CSV Tadarida).
- **Vue (TestFX)** : charge un FXML, vérifie les bindings et les interactions (`fire`, sélection…).
- **Bout en bout (`fr.univ_amu.iut.e2e.*`)** : le vrai chrome + vrais services + base SQLite jetable,
  pour valider un parcours (navigation, rafraîchissement au retour…).
- **Architecture (ArchUnit)** : les 6 règles de
  [`ArchitectureTest`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/test/java/fr/univ_amu/iut/architecture/ArchitectureTest.java)
  font **échouer le build** si une frontière MVVM est franchie (cf. [Architecture](architecture.md)).

## Les outils qualité

| Outil | Rôle | Bloquant ? |
|---|---|---|
| **ArchUnit** | Frontières MVVM + absence de cycles | Oui (tests) |
| **Spotless** (Palantir) | Format du code, via un *hook* pre-commit silencieux | Oui (CI vérifie le format) |
| **PMD** | *Code smells* | Bloquant **sous `-Pquality-gate`** |
| **JaCoCo** | Couverture | Seuil bloquant **sous `-Pquality-gate`** |

## Ce qui bloque la CI

Deux workflows tournent sur chaque PR :

- **[maven.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml)**
  (« Java CI ») : `./mvnw -B verify` (compilation + tous les tests, ArchUnit inclus).
- **[lint.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/lint.yml)**
  (« Quality gate ») : `./mvnw -Pquality-gate verify` (PMD bloquant + seuils de couverture + Spotless).

Une PR doit avoir **les deux au vert** pour être mergée.

## Écrire un nouveau test : rappels

- Un test de vue part d'un **injecteur réel ou partiel** (Guice) et d'une **base jetable** (workspace
  temporaire + `MigrationSchema.migrer()`), comme les `*ViewTest` / `*E2ETest` existants.
- Pour une capture/aperçu déterministe, voir
  [Ajouter une fonctionnalité §7](ajouter-une-fonctionnalite.md#7-ajouter-un-apercu-capture-decran).
- **Deux pièges récurrents** sont détaillés dans
  [TESTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/TESTING.md)
  (fil JavaFX, mutations hors thread). À lire avant un premier test TestFX.
