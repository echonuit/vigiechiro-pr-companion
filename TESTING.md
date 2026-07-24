# Tester VigieChiro Companion

Ce document décrit la **suite de tests** : comment l'exécuter, ce qu'elle contient, et ce qui bloque
(ou non) l'intégration continue. Il complète [CONTRIBUTING.md](CONTRIBUTING.md) (workflow général) et
le [README (vue d'ensemble)](README.md#dev-qualite).

> 📖 Version structurée (mêmes infos, navigation + recherche) :
> [doc dev · Tests et qualité](https://companion-dev.echonuit.fr/tests-et-qualite/).

---

## 1. L'environnement de test

| Élément | Valeur | Pourquoi |
|---|---|---|
| JDK | **25 standard** (Temurin / `25.0.2-open`) | JavaFX 26 vient des dépendances Maven `org.openjfx`. |
| JavaFX | **26**, *Headless Platform* Gluon | Tests d'IHM headless **en mémoire**, sans serveur d'affichage. |
| Lanceur | **`./mvnw`** uniquement | Aucune installation de Maven. |

> **Attention au JDK local** : n'utilisez **pas** un JDK packagé avec JavaFX (type `fx-zulu`). Il
> embarque JavaFX 25, qui masque les jars Maven FX 26 sur le module-path et fait échouer le headless
> (`NPE com.sun.glass.ui.PlatformFactory.getPlatformFactory()` : la *Headless Platform* n'existe
> qu'en FX 26). La CI utilise un Temurin 25 standard, faites de même en local :
> `export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open`.

### Exécution headless (sans X11 ni xvfb)

Les tests TestFX tournent en mémoire grâce à la *Headless Platform*. La configuration vit dans le
bloc Surefire du [`pom.xml`](pom.xml) et n'a **rien à régler** :

```xml
<systemPropertyVariables>
    <glass.platform>Headless</glass.platform>   <!-- plateforme Glass logicielle -->
    <prism.order>sw</prism.order>               <!-- pipeline de rendu logiciel -->
    <java.awt.headless>true</java.awt.headless>
    <testfx.robot>glass</testfx.robot>          <!-- robot piloté par Glass -->
</systemPropertyVariables>
```

Le `argLine` complète avec les `--add-opens` / `--add-exports` requis par JavaFX sous Java 25 et
l'agent JaCoCo. On **ne met pas** `testfx.headless=true` : ce flag réactiverait l'ancien bootstrap
Monocle. Le headless vient de `glass.platform=Headless`, pas de TestFX.

---

## 2. Les commandes

| Commande | Effet |
|---|---|
| `./mvnw test` | **Toute** la suite de tests. |
| `./mvnw verify` | Build complet : tests + couverture + contrôles (PMD/JaCoCo **non** bloquants). |
| `./mvnw -Pquality-gate verify` | **Portail qualité** : PMD `failOnViolation` + seuils JaCoCo **bloquants**. |
| `./mvnw test -Dtest=SitesViewModelTest` | Une seule **classe** de test. |
| `./mvnw test -Dtest=SitesViewModelTest#chargeLesSites` | Une seule **méthode**. |
| `./mvnw -Pmutation test` | Tests de **mutation** PIT (lent, à la demande). |
| `./mvnw pmd:check` | Rapport PMD seul (rapide). |
| `./mvnw spotless:check` | Vérifie le formatage (sans modifier). |

---

## 3. La taxonomie des tests

Les tests vivent sous `src/test/java/fr/univ_amu/iut/`, en **miroir** des paquets de production.

### Tests métier et MVVM

| Catégorie | Emplacement | Ce qu'ils vérifient |
|---|---|---|
| Unitaires métier | `<feature>/model/`, `<feature>/dao/`, `commun/persistence/`, `commun/model/` | Entités, services, DAO (SQLite), migrations. Aucune dépendance JavaFX. |
| ViewModel | `<feature>/viewmodel/` | État observable et logique de présentation, sans composant graphique. |
| Intégration de vue (TestFX) | `<feature>/view/*VueIntegrationTest` | La vue FXML se lie au ViewModel et réagit (headless). |
| **Geste** (TestFX) | `<feature>/view/*ViewTest` | Le bouton est **cliqué**, et on vérifie son **effet** (#1405). |
| Parcours de bout en bout | `<feature>/e2e/Parcours*E2ETest` | Le scénario complet IHM → ViewModel → service → base. |

#### Tester un geste, pas un bouton

Un test qui vérifie qu'un bouton est **présent et actif** ne dit rien de ce qu'il fait. C'était
pourtant tout ce qu'on avait sur les actions **irréversibles** (purger, restaurer, supprimer,
réimporter), et pour une raison mécanique : un `showAndWait()` **fige** un test headless, donc le
clic était impossible.

Les dialogues d'une action sont désormais des **ports** remplaçables (`Confirmateur`, `Notificateur`,
`SelecteurFichier`, `DemandeurDeChoix` : cf. [patterns](dev-docs/patterns.md)). Un test de geste les
remplace par des
doubles, **déclenche** l'action, et vérifie **ce qui s'est passé** :

```java
controleur.confirmateur().definir(message -> { confirmations.add(message); return confirme; });
controleur.notificateur().definir((niveau, entete, message) -> annonces.add(entete));

robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

assertThat(sitesEnBase()).isEmpty();     // l'effet, pas « un mock a été appelé »
```

Trois exigences, dans l'ordre d'importance :

1. **Le refus.** Sur une action irréversible, « Annuler annule vraiment » est le test qui compte le
   plus - et c'est celui qui manquait partout.
2. **L'effet réel.** Quand la fixture le permet (vrai injecteur + vraie base), asserter que la ligne
   a **disparu de la base**, pas qu'un mock a reçu un appel.
3. **Le message de confirmation est un contenu.** Sur une cascade, c'est le seul avertissement que
   l'utilisateur recevra : vérifier qu'il annonce le gain, ce qui est conservé, et ce qui est perdu.
4. **Renoncer n'est pas abandonner.** Quand un dialogue offre plusieurs issues, l'une d'elles **détruit**
   souvent quelque chose et une autre **ne fait rien**. Les deux ferment le dialogue : un test doit les
   **distinguer**.

### Tests d'architecture (ArchUnit)

[`architecture/ArchitectureTest`](src/test/java/fr/univ_amu/iut/architecture/ArchitectureTest.java)
fait respecter les frontières MVVM. **Six règles** :

1. les paquets `model` ne dépendent pas de JavaFX ;
2. la persistance (`commun.persistence`, `model.dao`) ne dépend pas de JavaFX ;
3. la couche `viewmodel` ne dépend pas de `javafx.scene` / `javafx.fxml` / `javafx.stage`
   (`javafx.beans` reste autorisé) ;
4. la couche `view` ne touche jamais JDBC (`model.dao`, `java.sql`) ;
5. les slices `fr.univ_amu.iut.*` sont **sans cycle** (la racine de composition `commun.di` est
   exclue, elle connaît toutes les features par rôle) ;
6. une feature ne dépend pas du `view` ni du `viewmodel` d'une **autre** feature (le socle partagé
   `commun.view`, notamment le `Navigateur`, est l'exception assumée).

Casser une de ces règles fait **échouer le build** : c'est le garde-fou de l'architecture.

---

## 4. Ce qui bloque la CI

La source de vérité est [`.github/workflows/maven.yml`](.github/workflows/maven.yml) et
[`lint.yml`](.github/workflows/lint.yml).

| Workflow | Commande | Bloquant ? |
|---|---|---|
| Build + tests + couverture (`maven.yml`) | `./mvnw -B verify -Djacoco.haltOnFailure=true` | **Oui** |
| Formatage (`lint.yml`) | `./mvnw -B spotless:check` | **Oui** |
| Portail qualité PMD (`lint.yml`) | `./mvnw -B -Pquality-gate compile pmd:check` | **Oui** |

`lint.yml` vérifie aussi la **complétude des captures de référence**
([`check-captures.sh`](.github/assets/check-captures.sh)). Une PR doit passer **les deux** workflows.

---

## 5. Couverture et mutation

- **JaCoCo** mesure la couverture à chaque `verify`. Sous `-Pquality-gate`, les seuils deviennent
  **bloquants** au niveau `BUNDLE`. Leurs valeurs et la justification de chacune vivent dans le
  [`pom.xml`](pom.xml), **seule source** : les répéter ici les ferait diverger. En CI, ce seuil est
  rendu bloquant par **`maven.yml`** (`verify -Djacoco.haltOnFailure=true`), pas par `lint.yml` qui ne
  fait que le statique (Spotless, captures, PMD). Les outils
  (`**/outils/**` : capture d'écran, bancs de mesure) sont **exclus** du calcul (ils sont validés
  par exécution, pas par tests unitaires).
- **PIT** (profil `-Pmutation`) évalue la **qualité** des tests par mutation. Lent : à lancer à la
  demande, pas dans le cycle normal.

---

## 6. Écrire un nouveau test (rappels)

- **TestFX** : interroger les nœuds par `fx:id` (`lookup("#monId")`), piloter via le robot Glass,
  asserter avec AssertJ. Tester les actions par `bouton.fire()` plutôt que par un clic robot quand
  c'est possible (plus stable en headless).
- **ApprovalTests** : utilisé pour les sorties verbatim (CSV Tadarida `_Vu`). Le premier run produit
  un fichier `*.received`, à comparer puis approuver en `*.approved`.

### Trois pièges récurrents

- **`assertThat(path).endsWith(Path)` canonicalise** (`toRealPath`) et lève `NoSuchFileException`
  si le dossier n'existe pas (erreur sur runner neuf). Préférer le booléen **lexical**
  `java.nio.file.Path.endsWith` : `assertThat(p.endsWith(autre)).isTrue()`.
- **Mutation hors fil JavaFX** : un handler qui modifie l'IHM depuis un thread d'arrière-plan lève
  `Not on FX application thread`, souvent **avalée** (l'écran fige). Découper
  préparation (fil FX) / exécution (hors-thread) / `Platform.runLater` pour le retour, et tester via
  `bouton.fire()` en attendant l'état terminal.
- **`fire()` est un no-op sur un contrôle désactivé** : `Button.fire()` comme `Hyperlink.fire()`
  vérifient `isDisabled()` avant d'émettre. Un test qui « clique » un bouton grisé ne déclenche donc
  **rien** - et s'il attend une erreur métier, il échoue sans dire pourquoi. Le plus souvent c'est le
  **test** qui a tort : quand l'affordance (#789) a déjà **fermé** le geste, il n'y a plus de refus à
  annoncer, et c'est le **grisage** qu'il faut asserter. *On ne prévient pas après coup ce qu'on a
  déjà empêché.*
